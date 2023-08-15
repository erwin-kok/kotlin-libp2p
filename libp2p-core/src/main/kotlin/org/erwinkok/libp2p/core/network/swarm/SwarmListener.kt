// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.core.network.swarm

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.address.NetworkInterface.resolveUnspecifiedAddresses
import org.erwinkok.libp2p.core.network.swarm.Swarm.Companion.ErrSwarmClosed
import org.erwinkok.result.CombinedError
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

class SwarmListener(
    private val scope: CoroutineScope,
    private val swarm: Swarm,
    private val swarmTransport: SwarmTransport,
) : AwaitableClosable {
    private val _context = Job(scope.coroutineContext[Job])
    private val listenersLock = ReentrantLock()
    private val listeners = mutableMapOf<InetMultiaddress, Job>()
    private val interfaceListenAddresses = mutableListOf<InetMultiaddress>()
    private var interfaceListenAddressesExpiration: Instant? = null

    override val jobContext: Job get() = _context

    fun addListener(address: InetMultiaddress): Result<Unit> {
        return addListeners(listOf(address))
    }

    fun addListeners(addresses: List<InetMultiaddress>): Result<Unit> {
        val errors = CombinedError()
        for (address in addresses) {
            listenersLock.withLock {
                if (listeners.containsKey(address)) {
                    errors.recordError(ErrAlreadyListening)
                } else {
                    startListener(address)
                        .onFailure {
                            errors.recordError(it)
                        }
                }
            }
        }
        if (errors.hasErrors && addresses.isNotEmpty()) {
            return Err(errors.error("While adding listeners, the following errors occurred: "))
        }
        return Ok(Unit)
    }

    fun removeListener(address: InetMultiaddress): Result<Unit> {
        listenersLock.withLock {
            if (!listeners.containsKey(address)) {
                return Err(ErrNotListening)
            }
            listeners.remove(address)?.cancel()
            swarm.notifyAll { subscriber -> subscriber.listenClose(swarm, address) }
        }
        return Ok(Unit)
    }

    fun listenAddresses(): List<InetMultiaddress> {
        listenersLock.withLock {
            return listeners.keys.toList()
        }
    }

    fun interfaceListenAddresses(): Result<List<InetMultiaddress>> {
        listenersLock.withLock {
            val expiration = interfaceListenAddressesExpiration
            val expired = expiration == null || Instant.now().isAfter(expiration)
            if (!expired) {
                return Ok(interfaceListenAddresses)
            }
            val listenAddresses = listeners.keys.toList()
            val newInterfaceListenAddresses = if (listenAddresses.isNotEmpty()) {
                resolveUnspecifiedAddresses(listenAddresses)
                    .getOrElse { return Err(it) }
                    .toMutableList()
            } else {
                listOf()
            }
            interfaceListenAddresses.clear()
            interfaceListenAddresses.addAll(newInterfaceListenAddresses)
            interfaceListenAddressesExpiration = Instant.now().plusMillis(interfaceAddressesCacheDuration.inWholeMilliseconds)
        }
        return Ok(interfaceListenAddresses)
    }

    override fun close() {
        listenersLock.withLock {
            listeners.forEach { it.value.cancel() }
            listeners.clear()
        }
        _context.complete()
    }

    private fun startListener(address: InetMultiaddress): Result<Unit> {
        val listener = swarmTransport.transportForListening(address)
            .flatMap { transport -> transport.listen(address) }
            .getOrElse { return Err(it) }
        logger.info { "Starting swarm-listener on: ${listener.transportAddress}..." }
        val job = scope.launch(_context + CoroutineName("swarm-listener-$address")) {
            while (_context.isActive) {
                listener.accept()
                    .onSuccess { transportConnection ->
                        logger.info { "swarm-listener-$address accepted connection: $transportConnection" }
                        swarm.addConnection(transportConnection, Direction.DirInbound)
                            .onFailure {
                                if (it != ErrSwarmClosed) {
                                    logger.warn { "swarm-listener-$address could not add connection: ${errorMessage(it)}" }
                                }
                            }
                    }
                    .onFailure {
                        logger.warn { "swarm-listener-$address could not accept connection: ${errorMessage(it)}" }
                    }
            }
        }
        listeners[listener.transportAddress] = job
        swarm.notifyAll { subscriber -> subscriber.listen(swarm, listener.transportAddress) }
        return Ok(Unit)
    }

    companion object {
        private val ErrAlreadyListening = Error("already listening on provided address")
        private val ErrNotListening = Error("not listening on provided address")
        private val interfaceAddressesCacheDuration = 1.minutes
    }
}
