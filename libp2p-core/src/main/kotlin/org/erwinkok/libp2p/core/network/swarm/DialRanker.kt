// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import org.erwinkok.libp2p.core.network.AddressDelay
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkProtocol
import org.erwinkok.libp2p.core.network.address.IpUtil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.milliseconds

internal object DialRanker {
    // duration by which TCP dials are delayed relative to the last QUIC dial
    val PublicTCPDelay = 250.milliseconds
    val PrivateTCPDelay = 30.milliseconds

    // duration by which QUIC dials are delayed relative to previous QUIC dial
    val PublicQUICDelay = 250.milliseconds
    val PrivateQUICDelay = 30.milliseconds

    // RelayDelay is the duration by which relay dials are delayed relative to direct addresses
    val RelayDelay = 500.milliseconds

    fun defaultDialRanker(addresses: List<InetMultiaddress>): List<AddressDelay> {
        val (relayAddresses, addresses1) = addresses.partition { it.isRelay }
        val (privateAddresses, addresses2) = addresses1.partition { IpUtil.isPrivateAddress(it) }
        val (publicAddresses, addresses3) = addresses2.partition { it.isIpv4 || it.isIpv6 }
        val relayOffset = if (publicAddresses.isNotEmpty()) {
            RelayDelay
        } else {
            ZERO
        }
        val result = mutableListOf<AddressDelay>()
        result.addAll(addresses3.map { AddressDelay(it, ZERO) })
        result.addAll(getAddressDelay(privateAddresses, PrivateTCPDelay, PrivateQUICDelay, ZERO))
        result.addAll(getAddressDelay(publicAddresses, PublicTCPDelay, PublicQUICDelay, ZERO))
        result.addAll(getAddressDelay(relayAddresses, PublicTCPDelay, PublicQUICDelay, relayOffset))
        return result
    }

    fun noDelayDialRanker(addresses: List<InetMultiaddress>): List<AddressDelay> {
        return getAddressDelay(addresses, ZERO, ZERO, ZERO)
    }

    private fun getAddressDelay(addresses: List<InetMultiaddress>, tcpDelay: Duration, quicDelay: Duration, offset: Duration): List<AddressDelay> {
        val sortedAddresses = addresses.sortedBy { score(it) }.toMutableList()
        var happyEyeballs = false
        if (sortedAddresses.isNotEmpty() && isQuicAddress(sortedAddresses[0]) && sortedAddresses[0].isIpv6) {
            for (i in 1 until sortedAddresses.size) {
                if (isQuicAddress(sortedAddresses[i]) && sortedAddresses[i].isIpv4) {
                    if (i > 1) {
                        sortedAddresses.swap(1, i)
                    }
                    happyEyeballs = true
                    break
                }
            }
        }
        val result = mutableListOf<AddressDelay>()
        var totalTCPDelay = ZERO
        for ((i, address) in sortedAddresses.withIndex()) {
            var delay = ZERO
            if (isQuicAddress(address)) {
                // For QUIC addresses we dial an IPv6 address, then after quicDelay an IPv4
                // address, then after quicDelay we dial rest of the addresses.
                if (i == 1) {
                    delay = quicDelay
                } else if (i > 1 && happyEyeballs) {
                    delay = quicDelay.times(2)
                } else if (i > 1) {
                    delay = quicDelay
                }
                totalTCPDelay = delay + tcpDelay
            } else if (address.networkProtocol == NetworkProtocol.TCP) {
                delay = totalTCPDelay
            }
            result.add(AddressDelay(address, offset + delay))
        }
        return result
    }

    private fun score(address: InetMultiaddress): Int {
        var ip4Weight = 0
        if (address.isIpv4) {
            ip4Weight = 1 shl 18
        }
        val port = address.hostName?.port ?: 0
        if (address.networkProtocol == NetworkProtocol.WEBTRANSPORT) {
            return ip4Weight + (1 shl 19) + port
        }
        if (address.networkProtocol == NetworkProtocol.QUIC) {
            return ip4Weight + port + (1 shl 17)
        }
        if (address.networkProtocol == NetworkProtocol.QUIC_V1) {
            return ip4Weight + port
        }
        if (address.networkProtocol == NetworkProtocol.TCP) {
            return ip4Weight + port + (1 shl 20)
        }
        return (1 shl 30)
    }

    private fun MutableList<InetMultiaddress>.swap(a: Int, b: Int) {
        val tmp = this[a]
        this[a] = this[b]
        this[b] = tmp
    }

    private fun isQuicAddress(address: InetMultiaddress): Boolean {
        return address.networkProtocol == NetworkProtocol.QUIC || address.networkProtocol == NetworkProtocol.QUIC_V1 || address.networkProtocol == NetworkProtocol.WEBTRANSPORT
    }
}
