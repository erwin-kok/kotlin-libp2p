// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.address

import inet.ipaddr.HostName
import mu.KotlinLogging
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onSuccess

private val logger = KotlinLogging.logger {}

object AddressUtil {
    fun filterAddresses(addresses: List<InetMultiaddress>, filter: (InetMultiaddress) -> Boolean): List<InetMultiaddress> {
        return filterAddresses(addresses, listOf(filter))
    }

    fun filterAddresses(addresses: List<InetMultiaddress>, filters: List<(InetMultiaddress) -> Boolean>): List<InetMultiaddress> {
        val result = mutableListOf<InetMultiaddress>()
        for (address in addresses) {
            var good = true
            for (filter in filters) {
                good = good && filter(address)
            }
            if (good) {
                result.add(address)
            }
        }
        return result
    }

    fun addressOverNonLocalIp(address: InetMultiaddress): Boolean {
        return !IpUtil.isIp6LinkLocal(address)
    }

    fun resolveUnspecifiedAddress(resolve: InetMultiaddress, interfaceAddresses: List<InetMultiaddress>): Result<MutableSet<InetMultiaddress>> {
        if (!IpUtil.isIpUnspecified(resolve)) {
            return Ok(mutableSetOf(resolve))
        }
        val result = mutableSetOf<InetMultiaddress>()
        val filteredInterfaceAddresses = interfaceAddresses.filter {
            val resolveAddress = resolve.hostName?.address
            val interfaceAddress = it.hostName?.address
            resolveAddress != null &&
                interfaceAddress != null &&
                ((resolveAddress.isIPv4 && interfaceAddress.isIPv4) || (resolveAddress.isIPv6 && interfaceAddress.isIPv6))
        }
        for (interfaceAddress in filteredInterfaceAddresses) {
            val address = interfaceAddress.hostName?.address
            if (address != null) {
                val port = resolve.hostName?.port
                val resolvedAddress = if (port != null) {
                    resolve.withHostName(HostName(address, port))
                } else {
                    resolve.withHostName(HostName(address))
                }
                if (!IpUtil.isIpUnspecified(resolvedAddress)) {
                    result.add(resolvedAddress)
                }
            }
        }
        if (result.isEmpty()) {
            return Err("failed to resolve: $resolve")
        }
        return Ok(result)
    }

    fun resolveUnspecifiedAddresses(unspecifiedAddresses: List<InetMultiaddress>, ifaceAddresses: List<InetMultiaddress>?): Result<MutableSet<InetMultiaddress>> {
        val ifaceAddrs = if (ifaceAddresses.isNullOrEmpty()) {
            interfaceAddresses()
                .getOrElse { return Err(it) }
        } else {
            ifaceAddresses
        }
        val result = mutableSetOf<InetMultiaddress>()
        for (unspecifiedAddress in unspecifiedAddresses) {
            resolveUnspecifiedAddress(unspecifiedAddress, ifaceAddrs)
                .onSuccess { result.addAll(it) }
        }
        if (result.isEmpty()) {
            return Err("failed to specify addresses: $unspecifiedAddresses")
        }
        logger.debug { "resolveUnspecifiedAddresses: $unspecifiedAddresses, $ifaceAddrs, $result" }
        return Ok(result)
    }

    fun interfaceAddresses(): Result<List<InetMultiaddress>> {
        val interfaceMultiaddresses = NetworkInterface.interfaceMultiaddresses()
            .getOrElse { return Err(it) }
        return Ok(interfaceMultiaddresses.filter { addressOverNonLocalIp(it) })
    }

    fun addressInList(address: InetMultiaddress, list: List<InetMultiaddress>): Boolean {
        return list.any { it == address }
    }

    fun addressIsShareableOnWan(address: InetMultiaddress): Boolean {
        return if (IpUtil.isIpLoopback(address) || IpUtil.isIp6LinkLocal(address) || IpUtil.isIpUnspecified(address)) {
            false
        } else {
            IpUtil.isThinWaist(address)
        }
    }
}
