// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.testplans.ping

import com.beust.klaxon.JsonObject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.erwinkok.libp2p.core.datastore.MapDatastore
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.LocalIdentity
import org.erwinkok.libp2p.core.host.builder.HostBuilder
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.ProviderAddrTTL
import org.erwinkok.libp2p.core.protocol.ping.PingService
import org.erwinkok.libp2p.core.record.AddressInfo
import org.erwinkok.libp2p.muxer.mplex.mplex
import org.erwinkok.libp2p.security.noise.noise
import org.erwinkok.libp2p.transport.tcp.tcp
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import kotlin.system.exitProcess
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

fun main() {
    runBlocking {
        val transport = System.getenv("transport") ?: "tcp"
        val security = System.getenv("security") ?: "noise"
        val muxer = System.getenv("muxer") ?: "mplex"
        val isDialerString = System.getenv("is_dialer") ?: "true"
        val ip = System.getenv("ip") ?: "0.0.0.0"
        val redisAddr = System.getenv("redis_addr") ?: "redis:6379"
        val testTimeoutSeconds = System.getenv("test_timeout_seconds")

        val testTimeout = if (testTimeoutSeconds != null) {
            Integer.parseInt(testTimeoutSeconds).seconds
        } else {
            3.minutes
        }

        val isDialer = isDialerString == "true"

        val hostBuilder = HostBuilder()

        when (transport) {
            "tcp" -> {
                hostBuilder.transports { tcp() }
                hostBuilder.swarm {
                    listenAddresses {
                        multiAddress("/ip4/$ip/tcp/0")
                    }
                }
            }

            else -> {
                logger.error { "Unsupported transport: $transport" }
                exitProcess(1)
            }
        }

        when (security) {
            "noise" -> hostBuilder.securityTransport { noise() }
            else -> {
                logger.error { "Unsupported security: $security" }
                exitProcess(1)
            }
        }

        when (muxer) {
            "mplex" -> hostBuilder.muxers { mplex() }
            else -> {
                logger.error { "Unsupported muxer: $muxer" }
                exitProcess(1)
            }
        }

        val scope = CoroutineScope(SupervisorJob() + exceptionHandler + Dispatchers.Default)

        val localIdentity = LocalIdentity.random()
            .getOrElse {
                logger.error { "Could not create random identity" }
                exitProcess(1)
            }

        hostBuilder.identity(localIdentity)
        hostBuilder.datastore(MapDatastore(scope))

        val host = hostBuilder.build(scope)
            .getOrElse {
                logger.error { "The following errors occurred while creating the host: ${errorMessage(it)}" }
                exitProcess(1)
            }

        val addresses = host
            .addresses()
            .map { it.withPeerId(localIdentity.peerId) }
        logger.info { "Local addresses the Host listens on: ${addresses.joinToString()} " }

        var result = true
        createRedisClient(redisAddr, testTimeout)
            .onFailure {
                logger.error { "Error: ${errorMessage(it)}" }
            }
            .onSuccess { redisClient ->
                result = if (isDialer) {
                    dialer(scope, host, redisClient, testTimeout)
                } else {
                    listener(scope, host, redisClient, testTimeout)
                }
                redisClient.close()
            }

        host.close()
        host.awaitClosed()

        val error = if (result) 1 else 0
        exitProcess(error)
    }
}

private suspend fun listener(scope: CoroutineScope, host: Host, redisClient: Jedis, testTimeout: Duration): Boolean {
    logger.info { "Configured as listener..." }
    val pingService = PingService(scope, host)
    val hostAddress = host.addresses().firstOrNull()
    if (hostAddress == null) {
        logger.error { "Failed to get listen address" }
        return true
    }
    val address = hostAddress.withPeerId(host.id).toString()
    redisClient.rpush("listenerAddr", address)
    delay(testTimeout)
    pingService.close()
    return true
}

private suspend fun dialer(scope: CoroutineScope, host: Host, redisClient: Jedis, testTimeout: Duration): Boolean {
    logger.info { "Configured as dialer..." }
    val listenerAddr = redisClient.blpop(testTimeout.inWholeSeconds.toInt(), "listenerAddr")
    if (listenerAddr == null) {
        logger.error { "Failed to wait for listener to be ready" }
        return true
    }
    if (listenerAddr.isEmpty()) {
        logger.error { "Didn't receive any address from listener" }
        return true
    }
    val address = listenerAddr[1]
    val peerAddress = InetMultiaddress.fromString(address)
        .getOrElse {
            logger.error { "Could not parse Multiaddress: $address" }
            return true
        }
    logger.info { "Other peer multiaddress is: $peerAddress" }

    val peerId = peerAddress.peerId
        .getOrElse {
            logger.error { "Failed to get PeerId from $peerAddress" }
            return true
        }

    host.peerstore.addAddress(peerId, peerAddress, ProviderAddrTTL)
    host.peerstore.addProtocols(peerId, setOf(ProtocolId.of("/ipfs/ping/1.0.0")))

    var pingResult: PingService.PingResult? = null
    val handshakePlusOneRTT = measureNanoTime {
        host.connect(AddressInfo.fromPeerId(peerId))
            .getOrElse {
                logger.error { "Could not connect to host ${errorMessage(it)}" }
                return true
            }
        val pingService = PingService(scope, host)
        val flow = pingService.ping(peerId, testTimeout)
        pingResult = withTimeoutOrNull(testTimeout) { flow.firstOrNull() }
        pingService.close()
    }

    if (pingResult == null) {
        logger.error { "Error occurred while receiving from peer" }
        return true
    }

    val error = pingResult?.error
    if (error != null) {
        logger.error { "Error occurred while receiving from peer: ${errorMessage(error)}" }
        return true
    }

    val pingRTTMilllis = pingResult?.rtt ?: 0L

    val testResult = JsonObject()
    testResult["handshakePlusOneRTTMillis"] = handshakePlusOneRTT / 1000000.0
    testResult["pingRTTMilllis"] = pingRTTMilllis / 1000000.0
    println(testResult.toJsonString())

    return false
}

private fun createRedisClient(redisAddr: String, timeout: Duration): Result<Jedis> {
    logger.info { "Connecting to Redis address: $redisAddr" }
    val waitMillis = timeout.inWholeMilliseconds.toInt()
    val config = DefaultJedisClientConfig.builder()
        .timeoutMillis(waitMillis)
        .database(0)
        .build()
    val jedis = Jedis(HostAndPort.from(redisAddr), config)
    return if (jedis.ping("Waiting") != "Waiting") {
        Err("Error waiting for Redis")
    } else {
        Ok(jedis)
    }
}

private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
    val cause = throwable.cause ?: throwable
    val message = cause.message ?: cause.toString()
    logger.error { "Error occurred: $message" }
}
