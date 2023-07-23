// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.address

import inet.ipaddr.HostName
import mu.KotlinLogging
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkProtocol
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.combine
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onSuccess
import java.net.NetworkInterface
import java.net.SocketException
import java.util.stream.Collectors

private val logger = KotlinLogging.logger {}

object NetworkInterface {
    fun interfaceMultiaddresses(): Result<List<InetMultiaddress>> {
        return try {
            return Ok(
                NetworkInterface.networkInterfaces()
                    .flatMap { it.inetAddresses() }
                    .collect(Collectors.toList())
                    .map { InetMultiaddress.fromHostNameAndProtocol(HostName(it), NetworkProtocol.UNKNOWN) }
                    .combine()
                    .getOrElse { return Err(it) },
            )
        } catch (e: SocketException) {
            logger.error { "Could not determine networkInterfaces" }
            Err("Could not determine networkInterfaces")
        }
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
            Err("Could not resolve specified addresses")
        } else {
            Ok(result)
        }
    }

    fun resolveUnspecifiedAddresses(unspecifiedAddresses: List<InetMultiaddress>, _interfaceAddresses: List<InetMultiaddress>? = null): Result<List<InetMultiaddress>> {
        val interfaceAddresses = if (_interfaceAddresses.isNullOrEmpty()) {
            interfaceMultiaddresses()
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
            Err("Could not resolve specified addresses")
        } else {
            Ok(IpUtil.unique(result))
        }
    }
}
