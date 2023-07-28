// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import io.ktor.utils.io.core.Closeable
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.NetworkProtocol
import org.erwinkok.libp2p.core.network.transport.Transport
import org.erwinkok.multiformat.multiaddress.Protocol
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

class SwarmTransport : Closeable {
    private val transportsLock = ReentrantLock()
    private val transports = mutableMapOf<Protocol, Transport>()

    fun addTransport(transport: Transport): Result<Unit> {
        val protocols = transport.protocols
        if (protocols.isEmpty()) {
            return Err("transport '$transport' does not not handle any protocol")
        }
        transportsLock.withLock {
            val registered = protocols.filter { transports.containsKey(it) }.map { protocolName(it) }
            if (registered.isNotEmpty()) {
                return Err("transports already registered for protocol(s): " + registered.joinToString())
            }
            for (protocol in protocols) {
                transports[protocol] = transport
            }
        }
        return Ok(Unit)
    }

    fun transportForDialing(address: InetMultiaddress): Result<Transport> {
        if (address.networkProtocol == NetworkProtocol.UNKNOWN) {
            return Err("Address $address has an unknown transport")
        }
        transportsLock.withLock {
            if (transports.isEmpty()) {
                return Err("Swarm has no transports configured")
            }
            val transport = transports.filter { it.value.canDial(address) }.firstNotNullOfOrNull { it.value }
            return if (transport != null) {
                Ok(transport)
            } else {
                Err("No Transport found for address $address")
            }
        }
    }

    fun transportForListening(address: InetMultiaddress): Result<Transport> {
        if (address.networkProtocol == NetworkProtocol.UNKNOWN) {
            return Err("Address $address has an unknown transport")
        }
        transportsLock.withLock {
            if (transports.isEmpty()) {
                return Err("Swarm has no transports configured")
            }
            val transport = transports.filter { it.value.canDial(address) }.firstNotNullOfOrNull { it.value }
            return if (transport != null) {
                Ok(transport)
            } else {
                Err("No Transport found for address $address")
            }
        }
    }

    override fun close() {
        transportsLock.withLock {
            transports.forEach { (_, transport) -> transport.close() }
            transports.clear()
        }
    }

    private fun protocolName(protocol: Protocol): String {
        return protocol.codec.typeName.ifBlank { "unknown (${protocol.codec.code})" }
    }
}
