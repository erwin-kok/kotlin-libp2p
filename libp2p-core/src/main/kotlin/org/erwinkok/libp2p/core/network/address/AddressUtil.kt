// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.address

import inet.ipaddr.HostName
import io.github.oshai.kotlinlogging.KotlinLogging
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

    fun resolveUnspecifiedAddress(resolve: InetMultiaddress, interfaceAddresses: List<InetMultiaddress>): Result<List<InetMultiaddress>> {
        if (!IpUtil.isIpUnspecified(resolve)) {
            return Ok(listOf(resolve))
        }
        val hostName = resolve.hostName ?: return Err("")
        val isIPv4 = hostName.asAddress()?.isIPv4 ?: true
        val result = interfaceAddresses
            .mapNotNull { it.hostName?.asAddress() }
            .filter {
                if (isIPv4) {
                    it.isIPv4
                } else {
                    it.isIPv6
                }
            }
            .map { resolve.withHostName(HostName(it, hostName.port)) }
        return if (result.isEmpty()) {
            Err("failed to resolve: $resolve")
        } else {
            Ok(result)
        }
    }

    fun resolveUnspecifiedAddresses(unspecifiedAddresses: List<InetMultiaddress>, _interfaceAddresses: List<InetMultiaddress>? = null): Result<List<InetMultiaddress>> {
        val interfaceAddresses = if (_interfaceAddresses.isNullOrEmpty()) {
            interfaceAddresses()
                .getOrElse { return Err(it) }
        } else {
            _interfaceAddresses
        }
        val result = mutableListOf<InetMultiaddress>()
        for (address in unspecifiedAddresses) {
            resolveUnspecifiedAddress(address, interfaceAddresses)
                .onSuccess {
                    result.addAll(it)
                }
        }
        return if (result.isEmpty()) {
            Err("failed to specify addresses: $unspecifiedAddresses")
        } else {
            Ok(result)
        }
    }

    fun interfaceAddresses(): Result<List<InetMultiaddress>> {
        val interfaceMultiaddresses = NetworkInterface.interfaceMultiaddresses()
            .getOrElse { return Err(it) }
        return Ok(interfaceMultiaddresses.filter { !IpUtil.isIp6LinkLocal(it) })
    }
}
