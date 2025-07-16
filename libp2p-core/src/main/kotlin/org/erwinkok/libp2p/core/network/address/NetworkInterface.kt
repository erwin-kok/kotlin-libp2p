// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.address

import inet.ipaddr.HostName
import io.github.oshai.kotlinlogging.KotlinLogging
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkProtocol
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.combine
import org.erwinkok.result.getOrElse
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
        } catch (_: SocketException) {
            logger.error { "Could not determine networkInterfaces" }
            Err("Could not determine networkInterfaces")
        }
    }
}
