// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.network.address

import inet.ipaddr.IPAddress
import inet.ipaddr.IPAddressString
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse

object IpUtil {
    private val privateCIDR4 = listOf(
        // localhost
        "127.0.0.0/8", // private networks
        "10.0.0.0/8",
        "100.64.0.0/10",
        "172.16.0.0/12",
        "192.168.0.0/16", // link local
        "169.254.0.0/16",
    )
    private val privateCIDR6 = listOf(
        // localhost
        "::1/128", // ULA reserved
        "fc00::/7", // link local
        "fe80::/10",
    )
    private val unroutableCIDR4 = listOf(
        "0.0.0.0/8",
        "192.0.0.0/26",
        "192.0.2.0/24",
        "192.88.99.0/24",
        "198.18.0.0/15",
        "198.51.100.0/24",
        "203.0.113.0/24",
        "224.0.0.0/4",
        "240.0.0.0/4",
        "255.255.255.255/32",
    )
    val unroutableCIDR6 = listOf(
        "ff00::/8",
    )
    private var Private4 = parseCidr(privateCIDR4)
    private var Private6 = parseCidr(privateCIDR6)
    private var Unroutable4 = parseCidr(unroutableCIDR4)
    private var Unroutable6 = parseCidr(unroutableCIDR6)

    val IP4Loopback = InetMultiaddress.fromString("/ip4/127.0.0.1").getOrElse { error("Could not create Multiaddress: ${errorMessage(it)}") }
    val IP6Loopback = InetMultiaddress.fromString("/ip6/::1").getOrElse { error("Could not create Multiaddress: ${errorMessage(it)}") }
    val IP4MappedIP6Loopback = InetMultiaddress.fromString("/ip6/::ffff:127.0.0.1").getOrElse { error("Could not create Multiaddress: ${errorMessage(it)}") }

    val IP4Unspecified = InetMultiaddress.fromString("/ip4/0.0.0.0").getOrElse { error("Could not create Multiaddress: ${errorMessage(it)}") }
    val IP6Unspecified = InetMultiaddress.fromString("/ip6/::").getOrElse { error("Could not create Multiaddress: ${errorMessage(it)}") }

    fun isThinWaist(address: InetMultiaddress): Boolean {
        return address.hostName?.address?.isIPAddress ?: false
    }

    fun isIpLoopback(address: InetMultiaddress): Boolean {
        return address.hostName?.address?.isLoopback ?: false
    }

    fun isIp6LinkLocal(address: InetMultiaddress): Boolean {
        val ip = address.hostName?.address
        return ip != null && ip.isIPv6 && ip.isLinkLocal
    }

    fun isIpUnspecified(address: InetMultiaddress): Boolean {
        return address.hostName?.address?.isUnspecified ?: false
    }

    fun isPublicAddress(address: InetMultiaddress): Boolean {
        val ip = address.hostName?.address ?: return false
        if (ip.isIPv4) {
            return !inAddrRange(ip, Private4) && !inAddrRange(ip, Unroutable4)
        } else if (ip.isIPv6) {
            return !inAddrRange(ip, Private6) && !inAddrRange(ip, Unroutable6)
        }
        return false
    }

    fun isPrivateAddress(address: InetMultiaddress): Boolean {
        val ip = address.hostName?.address ?: return false
        if (ip.isIPv4) {
            return inAddrRange(ip, Private4)
        } else if (ip.isIPv6) {
            return inAddrRange(ip, Private6)
        }
        return false
    }

    fun unique(addresses: List<InetMultiaddress>): List<InetMultiaddress> {
        return addresses.toSet().toList()
    }

    private fun parseCidr(cidrs: List<String>): List<IPAddress> {
        return cidrs.map { IPAddressString(it).address.toPrefixBlock() }
    }

    private fun inAddrRange(ip: IPAddress, ipnets: List<IPAddress>): Boolean {
        for (ipnet in ipnets) {
            if (ipnet.contains(ip)) {
                return true
            }
        }
        return false
    }
}
