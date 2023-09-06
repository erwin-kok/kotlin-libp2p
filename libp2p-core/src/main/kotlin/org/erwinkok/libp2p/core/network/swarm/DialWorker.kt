// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.core.network.swarm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.Direction
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.connectiongater.ConnectionGater
import org.erwinkok.libp2p.core.network.transport.TransportConnection
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.flatMap
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import org.erwinkok.result.toErrorIf
import java.time.Instant

private val logger = KotlinLogging.logger {}

internal class DialWorker(
    scope: CoroutineScope,
    private val peerId: PeerId,
    private val networkPeer: NetworkPeer,
    private val swarmDialer: SwarmDialer,
    swarmConfig: SwarmConfig,
) : AwaitableClosable {
    private val _context = SupervisorJob(scope.coroutineContext[Job])
    private val requestChannel = Channel<DialRequest>(16)
    private val responseChannel = Channel<DialResponse>(16)
    private val requests = mutableListOf<DialRequest>()
    private val trackedDials = mutableMapOf<InetMultiaddress, AddressDial>()
    private val queue = TimedPriorityQueue<AddressDial>(scope, 32)
    private val dialError = DialError(peerId)
    private val connectionGater: ConnectionGater? = swarmConfig.connectionGater
    private val dialTimeout = swarmConfig.dialTimeout
    private val maxRetries = swarmConfig.maxRetries
    private val backoffBase = swarmConfig.backoffBase
    private val backoffCoefficient = swarmConfig.backoffCoefficient

    override val jobContext: Job get() = _context

    val isCompleted: Boolean
        get() = requests.isEmpty()

    init {
        scope.launch(_context + CoroutineName("swarm-dialer-$peerId")) {
            try {
                while (!requestChannel.isClosedForReceive) {
                    select {
                        requestChannel.onReceive {
                            handleRequest(it)
                        }
                        queue.onReceive {
                            doDial(it)
                        }
                        responseChannel.onReceive {
                            handleResponse(it)
                        }
                    }
                }
            } catch (e: CancellationException) {
                // Do nothing...
            } catch (e: ClosedReceiveChannelException) {
                // Do nothing...
            } catch (e: Exception) {
                logger.warn { "Unexpected error occurred in swarm-dialer-$peerId: ${errorMessage(e)}" }
                throw e
            }
        }
    }

    suspend fun dial(): Result<SwarmConnection> {
        val dialRequest = DialRequest()
        requestChannel.send(dialRequest)
        return dialRequest.getResponse()
    }

    override fun close() {
        requestChannel.close()
        responseChannel.close()
        queue.close()
        _context.complete()
    }

    private suspend fun handleRequest(dialRequest: DialRequest) {
        requests.add(dialRequest)
        val bestConnection = networkPeer.bestConnectionToPeer()
        if (bestConnection != null) {
            responseChannel.send(DialResponse(Ok(bestConnection)))
            return
        }
        val addresses = swarmDialer.addressesForDial(peerId)
            .getOrElse {
                responseChannel.send(DialResponse(Err(it)))
                return
            }
        if (addresses.isEmpty()) {
            responseChannel.send(DialResponse(Err("Peer $peerId does not have any addresses defined")))
            return
        }
        val addressesRanking = DialRanker.defaultDialRanker(addresses)
        for (addressDelay in addressesRanking) {
            val addressDial = trackedDials[addressDelay.address]
            if (addressDial != null) {
                val connection = addressDial.connection
                if (connection != null) {
                    // dial to this address was successful, complete the request
                    responseChannel.send(DialResponse(Ok(connection)))
                }
            } else {
                // Not yet dialing...
                val now = Instant.now()
                val scheduleTime = now.plusMillis(addressDelay.delay.inWholeMilliseconds)
                val newAddressDial = AddressDial(addressDelay.address, 0, now, scheduleTime)
                trackedDials[addressDelay.address] = newAddressDial
                queue.queueElement(newAddressDial)
            }
        }
    }

    private suspend fun handleResponse(dialResponse: DialResponse) {
        requests.forEach { it.setResponse(dialResponse.result) }
        requests.clear()
    }

    private suspend fun doDial(addressDial: AddressDial) {
        val bestConnection = networkPeer.bestConnectionToPeer()
        if (bestConnection != null) {
            responseChannel.send(DialResponse(Ok(bestConnection)))
            return
        }
        val connection = withTimeoutOrNull(dialTimeout) {
            dialAddress(peerId, addressDial.address)
                .toErrorIf(
                    { transportConnection ->
                        connectionGater != null && !connectionGater.interceptUpgraded(transportConnection)
                    },
                    { transportConnection ->
                        transportConnection.close()
                        Swarm.ErrGaterDisallowedConnection
                    },
                )
                .flatMap {
                    networkPeer.addConnection(it, Direction.DirOutbound)
                }
        }
        if (connection == null) {
            connectFailure(addressDial, SwarmDialer.ErrDialTimeout)
        } else {
            connection
                .onSuccess { networkConnection ->
                    addressDial.connection = networkConnection
                    responseChannel.send(DialResponse(Ok(networkConnection)))
                    trackedDials.remove(addressDial.address)
                }.onFailure {
                    connectFailure(addressDial, it)
                }
        }
    }

    private suspend fun connectFailure(addressDial: AddressDial, error: Error) {
        val address = addressDial.address
        val retries = addressDial.retries + 1
        if (retries >= maxRetries) {
            logger.info { "Could not connect to Peer $peerId on address $address (${errorMessage(error)}). Giving up." }
            responseChannel.send(DialResponse(Err(dialError.combine())))
            trackedDials.remove(addressDial.address)
        } else {
            val backoffTime = backoffBase + backoffCoefficient * addressDial.retries * addressDial.retries
            logger.info { "Could not connect to Peer $peerId on address $address (${errorMessage(error)}). Retry $retries in $backoffTime" }
            if (error != SwarmDialer.ErrDialTimeout) {
                dialError.recordError(addressDial.address, error)
            }
            val newTime = addressDial.scheduleTime.plusMillis(backoffTime.inWholeMilliseconds)
            queue.queueElement(AddressDial(address, retries, addressDial.createdAt, newTime))
        }
    }

    private suspend fun dialAddress(peerId: PeerId, remoteAddress: InetMultiaddress): Result<TransportConnection> {
        return swarmDialer.swarmTransport.transportForDialing(remoteAddress)
            .flatMap { transport ->
                transport.dial(peerId, remoteAddress)
                    .toErrorIf(
                        { it.remoteIdentity.peerId != peerId },
                        {
                            it.close()
                            Error("Transport $transport dialed to ${it.remoteIdentity.peerId} instead of $peerId")
                        },
                    )
            }
    }
}
