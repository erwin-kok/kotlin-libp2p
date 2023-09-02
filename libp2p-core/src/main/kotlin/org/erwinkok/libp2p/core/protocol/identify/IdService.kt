// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

@file:OptIn(DelicateCoroutinesApi::class)

package org.erwinkok.libp2p.core.protocol.identify

import com.google.protobuf.ByteString
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.erwinkok.libp2p.core.base.AwaitableClosable
import org.erwinkok.libp2p.core.event.EvtLocalAddressesUpdated
import org.erwinkok.libp2p.core.event.EvtLocalProtocolsUpdated
import org.erwinkok.libp2p.core.event.EvtPeerIdentificationCompleted
import org.erwinkok.libp2p.core.event.EvtPeerIdentificationFailed
import org.erwinkok.libp2p.core.event.EvtPeerProtocolsUpdated
import org.erwinkok.libp2p.core.host.Host
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.host.RemoteIdentity
import org.erwinkok.libp2p.core.identify.pb.DbIdentify
import org.erwinkok.libp2p.core.network.Connectedness
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.network.Network
import org.erwinkok.libp2p.core.network.NetworkConnection
import org.erwinkok.libp2p.core.network.Stream
import org.erwinkok.libp2p.core.network.Subscriber
import org.erwinkok.libp2p.core.network.address.AddressUtil
import org.erwinkok.libp2p.core.network.address.IpUtil
import org.erwinkok.libp2p.core.network.readUnsignedVarInt
import org.erwinkok.libp2p.core.network.writeUnsignedVarInt
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.ConnectedAddrTTL
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.RecentlyConnectedAddrTTL
import org.erwinkok.libp2p.core.peerstore.Peerstore.Companion.TempAddrTTL
import org.erwinkok.libp2p.core.record.Envelope
import org.erwinkok.libp2p.core.record.PeerRecord
import org.erwinkok.libp2p.core.resourcemanager.ResourceScope.Companion.ReservationPriorityAlways
import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.multiformat.multistream.MultistreamMuxer
import org.erwinkok.multiformat.multistream.ProtocolId
import org.erwinkok.result.Err
import org.erwinkok.result.Errors
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOr
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import org.erwinkok.result.mapBoth
import org.erwinkok.result.onFailure
import org.erwinkok.result.onSuccess
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class IdService(
    private val scope: CoroutineScope,
    private val host: Host,
    private val userAgent: String = DefaultUserAgent,
    private val disableSignedPeerRecord: Boolean = false,
) : AwaitableClosable, Subscriber {
    private val _context = Job(scope.coroutineContext[Job])
    private val triggerPushes = Channel<Unit>(RENDEZVOUS)
    private val setupCompleted = Semaphore(1, 1)
    private val currentSnapshotLock = ReentrantLock()
    private var currentSnapshot = IdentifySnapshot(0L, setOf(), listOf(), null)
    private var metricsTracer: MetricsTracer? = null
    private val connectionsMutex = Mutex()
    private val connections = mutableMapOf<NetworkConnection, ConnectionEntry>()
    private val addressMutex = Mutex()
    private val observedAddressManager = ObservedAddressManager(scope, host)

    override val jobContext: Job get() = _context

    suspend fun start() {
        host.network.subscribe(this)
        host.setStreamHandler(Id) { stream ->
            handleIdentifyRequest(stream)
        }
        host.setStreamHandler(IdPush) { stream ->
            handlePush(stream)
        }
        updateSnapshot()
        setupCompleted.release()
        host.eventBus.subscribe<EvtLocalAddressesUpdated>(this, scope) {
            if (updateSnapshot()) {
                metricsTracer?.triggeredPushes(it)
                triggerPushes.trySend(Unit)
            }
        }
        host.eventBus.subscribe<EvtLocalProtocolsUpdated>(this, scope) {
            if (updateSnapshot()) {
                metricsTracer?.triggeredPushes(it)
                triggerPushes.trySend(Unit)
            }
        }
        scope.launch(_context + CoroutineName("id-service-push")) {
            try {
                while (!triggerPushes.isClosedForReceive) {
                    triggerPushes.receive()
                    sendPushes()
                }
            } catch (e: CancellationException) {
                // Do nothing
            }
        }
    }

    fun ownObservedAddresses(): List<InetMultiaddress> {
        return observedAddressManager.addresses()
    }

    fun observedAddressesFor(local: InetMultiaddress): List<InetMultiaddress> {
        return observedAddressManager.addressesFor(local)
    }

    suspend fun identifyConnection(connection: NetworkConnection) {
        identifyWait(connection).join()
    }

    suspend fun identifyWait(connection: NetworkConnection): Job {
        var result: Job
        connectionsMutex.withLock {
            val entry = connections[connection]
            if (entry == null) {
                if (connection.isClosed) {
                    logger.debug { "connection $connection not found in identify service" }
                    val job = Job()
                    job.complete()
                    return job
                } else {
                    addConnectionWithLock(connection)
                }
            } else {
                val job = entry.job
                if (job != null) {
                    return job
                }
            }

            result = scope.launch(_context + CoroutineName("id-service-identify-wait-$connection"), start = CoroutineStart.LAZY) {
                doIdentifyConnection(connection)
                    .onSuccess {
                        host.eventBus.publish(EvtPeerIdentificationCompleted(connection.remoteIdentity.peerId))
                    }
                    .onFailure {
                        host.eventBus.publish(EvtPeerIdentificationFailed(connection.remoteIdentity.peerId, it))
                    }
            }
            connections[connection] = ConnectionEntry(result)
        }
        result.start()
        return result
    }

    override fun close() {
        triggerPushes.cancel()
        host.eventBus.unsubscribe(this)
        observedAddressManager.close()
        _context.complete()
    }

    override fun connected(network: Network, connection: NetworkConnection) {
        runBlocking {
            connectionsMutex.withLock {
                addConnectionWithLock(connection)
            }
        }
    }

    override fun disconnected(network: Network, connection: NetworkConnection) {
        runBlocking {
            connectionsMutex.withLock {
                connections.remove(connection)
            }
            if (host.network.connectedness(connection.remoteIdentity.peerId) != Connectedness.Connected) {
                // Last disconnect.
                // Undo the setting of addresses to ConnectedAddrTTL we did
                addressMutex.withLock {
                    host.peerstore.updateAddresses(connection.remoteIdentity.peerId, ConnectedAddrTTL, RecentlyConnectedAddrTTL)
                }
            }
        }
    }

    private suspend fun sendPushes() {
        val conns = connectionsMutex.withLock {
            this.connections.entries.filter { it.value.pushSupport == PushSupport.IdentifyPushSupported || it.value.pushSupport == PushSupport.IdentifyPushSupportUnknown }.map { it.key }
        }
        val semaphore = Semaphore(maxPushConcurrency)
        val jobs = mutableListOf<Job>()
        for (connection in conns) {
            val entry = connectionsMutex.withLock {
                this.connections[connection]
            }
            if (entry != null) {
                val snapshot = currentSnapshotLock.withLock { currentSnapshot }
                if (entry.sequence >= snapshot.sequence) {
                    logger.debug { "already sent this snapshot ${snapshot.sequence} to peer ${connection.remoteIdentity}" }
                } else {
                    jobs.add(
                        scope.launch(_context + CoroutineName("send-push-${connection.remoteIdentity}")) {
                            semaphore.acquire()
                            withTimeoutOrNull(5.seconds) {
                                host.newStream(connection.remoteIdentity.peerId, Id)
                                    .onSuccess {
                                        sendIdentifyResponse(it, true)
                                    }
                            }
                            semaphore.release()
                        },
                    )
                }
            }
        }
        jobs.joinAll()
    }

    private suspend fun addConnectionWithLock(connection: NetworkConnection) {
        if (!connections.containsKey(connection)) {
            setupCompleted.acquire()
            connections[connection] = ConnectionEntry()
            setupCompleted.release()
        }
    }

    private suspend fun doIdentifyConnection(connection: NetworkConnection): Result<Unit> {
        val stream = connection.newStream("identify")
            .getOrElse { e ->
                logger.debug { "error opening identify stream: ${errorMessage(e)}" }
                return Err(e)
            }
        stream.setProtocol(Id)
        MultistreamMuxer.selectProtoOrFail(Id, stream)
            .onFailure { e ->
                logger.info { "failed negotiate identify protocol with peer ${connection.remoteIdentity.peerId}: ${e.message}" }
                stream.reset()
                return Err(e)
            }
        return handleIdentifyResponse(stream, false)
    }

    private suspend fun handlePush(stream: Stream) {
        handleIdentifyResponse(stream, true)
    }

    internal suspend fun handleIdentifyRequest(stream: Stream) {
        sendIdentifyResponse(stream, false)
    }

    private suspend fun sendIdentifyResponse(stream: Stream, isPush: Boolean): Result<Unit> {
        stream.streamScope.setService(ServiceName)
            .onFailure {
                val message = "error attaching stream to identify service: ${errorMessage(it)}"
                logger.warn { message }
                stream.reset()
                return Err(message)
            }

        val snapshot = currentSnapshotLock.withLock { currentSnapshot }

        logger.debug { "sending snapshot, sequence=${snapshot.sequence}, protocols=[${snapshot.protocols.joinToString(", ")}], addresses=[${snapshot.addresses.joinToString(", ")}]" }

        val messageBuilder = createBaseIdentifyResponse(stream, snapshot)
        val record = getSignedRecord(snapshot)
        if (record != null) {
            messageBuilder.signedPeerRecord = ByteString.copyFrom(record)
        }

        logger.debug { "$Id sending message to ${stream.connection.remoteIdentity} ${stream.connection.remoteAddress}" }

        val message = messageBuilder.build()
        writeChunkedIdentifyMessage(stream, message)

        metricsTracer?.identifySent(isPush, message.protocolsCount, message.listenAddrsCount)

        connectionsMutex.withLock {
            val entry = connections[stream.connection]
            if (entry != null) {
                entry.sequence = snapshot.sequence
            }
        }
        stream.close()
        return Ok(Unit)
    }

    private suspend fun handleIdentifyResponse(stream: Stream, isPush: Boolean): Result<Unit> {
        stream.streamScope.setService(ServiceName)
            .onFailure {
                logger.warn { "error attaching stream to identify service: ${errorMessage(it)}" }
                stream.reset()
                return Err(it)
            }
        stream.streamScope.reserveMemory(SignedIdSize, ReservationPriorityAlways)
            .onFailure {
                logger.warn { "error reserving memory for identify stream: ${errorMessage(it)}" }
                stream.reset()
                return Err(it)
            }

        val identify = readMessagesFromStreamWithTimeout(stream, StreamReadTimeout)
            .getOrElse {
                logger.warn { "error reading identify message: ${errorMessage(it)}" }
                stream.reset()
                return Err(it)
            }
        logger.info { "${stream.protocol()} received message from ${stream.connection.remoteIdentity}" }

        consumeMessage(identify, stream.connection, isPush)

        metricsTracer?.identifyReceived(isPush, identify.protocolsCount, identify.listenAddrsCount)

        connectionsMutex.withLock {
            val entry = connections[stream.connection]
            if (entry != null) {
                host.peerstore.supportsProtocols(stream.connection.remoteIdentity.peerId, setOf(IdPush))
                    .onSuccess {
                        val identifyPushSupported = if (it.isNotEmpty()) {
                            PushSupport.IdentifyPushSupported
                        } else {
                            PushSupport.IdentifyPushUnsupported
                        }
                        connections[stream.connection] = ConnectionEntry(entry.job, identifyPushSupported, entry.sequence)
                        metricsTracer?.connectionPushSupport(identifyPushSupported)
                    }
                    .onFailure {
                        connections[stream.connection] = ConnectionEntry(entry.job, PushSupport.IdentifyPushUnsupported, entry.sequence)
                        metricsTracer?.connectionPushSupport(PushSupport.IdentifyPushUnsupported)
                    }
            }
        }

        stream.streamScope.releaseMemory(SignedIdSize)
        stream.close()
        return Ok(Unit)
    }

    private suspend fun readMessagesFromStreamWithTimeout(stream: Stream, timeout: Duration): Result<DbIdentify.Identify> {
        val identify = withTimeoutOrNull(timeout) {
            readMessagesFromStream(stream)
        }
        return identify ?: Err("Timout occurred while reading identify message")
    }

    private suspend fun readMessagesFromStream(stream: Stream): Result<DbIdentify.Identify> {
        val builder = DbIdentify.Identify.newBuilder()
        for (i in 0 until MaxMessages) {
            val size = stream.input.readUnsignedVarInt()
                .map { it.toLong() }
                .getOrElse {
                    if (it == Errors.EndOfStream) {
                        return Ok(builder.build())
                    }
                    return Err(it)
                }
            if (size > SignedIdSize) {
                return Err("Packet size $size exceeds $SignedIdSize")
            }
            val bytes = ByteArray(size.toInt())
            stream.input.readFully(bytes)
            builder.mergeFrom(bytes)
        }
        return Err("Too many messages in identify")
    }

    private suspend fun updateSnapshot(): Boolean {
        val addresses = host.addresses()
        val protocols = host.multistreamMuxer.protocols()
        val record = if (!disableSignedPeerRecord) {
            host.peerstore.getPeerRecord(host.id)
        } else {
            null
        }
        currentSnapshotLock.withLock {
            val snapshot = IdentifySnapshot(currentSnapshot.sequence + 1, protocols.toSet(), addresses.toList(), record)
            if (currentSnapshot == snapshot) {
                return false
            }
            currentSnapshot = snapshot
            logger.debug { "updating $snapshot" }
            return true
        }
    }

    private suspend fun writeChunkedIdentifyMessage(stream: Stream, identify: DbIdentify.Identify) {
        val bytes = identify.toByteArray()
        if (!identify.hasSignedPeerRecord() && bytes.size <= LegacyIdSize) {
            stream.output.writeUnsignedVarInt(bytes.size)
            stream.output.writeFully(bytes)
            stream.output.flush()
            return
        }
        val message1 = DbIdentify.Identify.newBuilder()
            .setProtocolVersion(identify.protocolVersion)
            .setAgentVersion(identify.agentVersion)
            .setPublicKey(identify.publicKey)
            .addAllListenAddrs(identify.listenAddrsList)
            .setObservedAddr(identify.observedAddr)
            .addAllProtocols(identify.protocolsList)
            .build()
        val bytes1 = message1.toByteArray()
        stream.output.writeUnsignedVarInt(bytes1.size)
        stream.output.writeFully(bytes1)
        val message2 = DbIdentify.Identify.newBuilder()
            .setSignedPeerRecord(identify.signedPeerRecord)
            .build()
        val bytes2 = message2.toByteArray()
        stream.output.writeUnsignedVarInt(bytes2.size)
        stream.output.writeFully(bytes2)
        stream.output.flush()
    }

    private suspend fun createBaseIdentifyResponse(stream: Stream, snapshot: IdentifySnapshot): DbIdentify.Identify.Builder {
        val identifyBuilder = DbIdentify.Identify.newBuilder()

        val connection = stream.connection
        val remoteAddress = connection.remoteAddress
        val localAddress = connection.localAddress

        // set protocols this node is currently handling
        identifyBuilder.addAllProtocols(snapshot.protocols.map { it.id })

        // observed address so other side is informed of their
        // "public" address, at least in relation to us.
        identifyBuilder.observedAddr = ByteString.copyFrom(remoteAddress.bytes)

        // populate unsigned addresses.
        // peers that do not yet support signed addresses will need this.
        // Note: LocalMultiaddr is sometimes 0.0.0.0
        val viaLoopback = IpUtil.isIpLoopback(localAddress) || IpUtil.isIpLoopback(remoteAddress)
        val listenAddresses =
            snapshot
                .addresses
                .filter { a -> viaLoopback || !IpUtil.isIpLoopback(a) }
                .map { i -> ByteString.copyFrom(i.bytes) }
        identifyBuilder.addAllListenAddrs(listenAddresses)

        // set our public key
        val localIdentity = host.peerstore.localIdentity(host.id)

        // check if we even have a public key.
        if (localIdentity == null) {
            logger.error { "did not have own public key in Peerstore" }
            // if neither of the key is present it is safe to assume that we are using an insecure transport.
        } else {
            // public key is present. Safe to proceed.
            CryptoUtil.marshalPublicKey(localIdentity.publicKey)
                .onSuccess {
                    identifyBuilder.publicKey = ByteString.copyFrom(it)
                }
                .onFailure {
                    logger.error { "Could not convert key to bytes" }
                }
        }

        // set protocol versions
        identifyBuilder.protocolVersion = LibP2PVersion
        identifyBuilder.agentVersion = userAgent
        return identifyBuilder
    }

    private fun getSignedRecord(snapshot: IdentifySnapshot): ByteArray? {
        val r = snapshot.record
        if (disableSignedPeerRecord || r == null) {
            return null
        }
        return r.marshal()
            .getOrElse {
                logger.error { "failed to marshal signed record: ${errorMessage(it)}" }
                return null
            }
    }

    private fun diff(a: Set<ProtocolId>, b: Set<ProtocolId>): Tuple2<Set<ProtocolId>, Set<ProtocolId>> {
        val added = mutableSetOf<ProtocolId>()
        val removed = mutableSetOf<ProtocolId>()
        for (x in b) {
            var found = false
            for (y in a) {
                if (x == y) {
                    found = true
                    break
                }
            }
            if (!found) {
                added.add(x)
            }
        }
        for (x in a) {
            var found = false
            for (y in b) {
                if (x == y) {
                    found = true
                    break
                }
            }
            if (!found) {
                removed.add(x)
            }
        }
        return Tuple(added, removed)
    }

    private suspend fun consumeMessage(identify: DbIdentify.Identify, connection: NetworkConnection, isPush: Boolean) {
        val remotePeerId = connection.remoteIdentity.peerId
        val supported = host.peerstore.getProtocols(remotePeerId).getOr(setOf())
        val identifyProtocols = identify.protocolsList.map { ProtocolId.of(it) }.toSet()
        val (added, removed) = diff(supported, identifyProtocols)
        host.peerstore.setProtocols(remotePeerId, identifyProtocols)
        if (isPush) {
            host.eventBus.publish(EvtPeerProtocolsUpdated(remotePeerId, added, removed))
        }
        if (identify.hasObservedAddr()) {
            consumeObservedAddress(identify.observedAddr.toByteArray(), connection)
        }

        val listenAddresses = mutableListOf<InetMultiaddress>()
        identify.listenAddrsList.forEach {
            InetMultiaddress.fromBytes(it.toByteArray())
                .onSuccess { address ->
                    listenAddresses.add(address)
                }
                .onFailure {
                    logger.warn { "$Id Could not parse multiaddress from $remotePeerId: ${errorMessage(it)} " }
                }
        }
        val signedPeerRecord = signedPeerRecordFromMessage(identify)

        addressMutex.withLock {
            // Downgrade connected and recently connected addrs to a temporary TTL.
            val ttl = if (host.network.connectedness(remotePeerId) == Connectedness.Connected) {
                ConnectedAddrTTL
            } else {
                RecentlyConnectedAddrTTL
            }
            host.peerstore.updateAddresses(remotePeerId, RecentlyConnectedAddrTTL, TempAddrTTL)
            host.peerstore.updateAddresses(remotePeerId, ConnectedAddrTTL, TempAddrTTL)
            val addresses = if (signedPeerRecord != null) {
                consumeSignedPeerRecord(remotePeerId, signedPeerRecord)
                    .mapBoth(
                        { it },
                        {
                            logger.debug { "failed to consume signed peer record: ${errorMessage(it)}" }
                            listenAddresses
                        },
                    )
            } else {
                listenAddresses
            }
            host.peerstore.addAddresses(remotePeerId, filterAddresses(addresses, connection.remoteAddress), ttl)

            // Finally, expire all temporary addrs.
            host.peerstore.updateAddresses(remotePeerId, TempAddrTTL, ZERO)
        }

        logger.debug { "${connection.localIdentity} received listen addrs for ${connection.remoteIdentity}: ${listenAddresses.joinToString(", ")}" }

        if (identify.hasProtocolVersion()) {
            host.peerstore.put(remotePeerId, "ProtocolVersion", identify.protocolVersion)
        }
        if (identify.hasAgentVersion()) {
            host.peerstore.put(remotePeerId, "AgentVersion", identify.agentVersion)
        }

        // get the key from the other side. we may not have it (no-auth transport)
        if (identify.hasPublicKey()) {
            consumeReceivedPublicKey(connection, identify.publicKey.toByteArray())
        }
    }

    private fun consumeSignedPeerRecord(peerId: PeerId, signedPeerRecord: Envelope): Result<List<InetMultiaddress>> {
        val id = PeerId.fromPublicKey(signedPeerRecord.publicKey)
            .getOrElse {
                return Err("failed to derive PeerId: ${errorMessage(it)}")
            }
        if (id != peerId) {
            return Err("received signed peer record envelope for unexpected PeerId. expected $peerId, got $id")
        }
        val record = signedPeerRecord.record()
            .getOrElse {
                return Err("failed to derive record: ${errorMessage(it)}")
            }
        val peerRecord = record as? PeerRecord ?: return Err("not a peer record")
        if (peerRecord.peerId != peerId) {
            return Err("received signed peer record for unexpected PeerId. expected $peerId, got ${peerRecord.peerId}")
        }
        return Ok(peerRecord.addresses)
    }

    private suspend fun consumeReceivedPublicKey(connection: NetworkConnection, publicKeyBytes: ByteArray) {
        val remoteIdentity = connection.remoteIdentity
        val publicKey = CryptoUtil.unmarshalPublicKey(publicKeyBytes)
            .getOrElse {
                logger.warn { "cannot unmarshal key from remote peer: $remoteIdentity, ${errorMessage(it)}" }
                return
            }
        val remoteIdentityFromPublicKey = RemoteIdentity.fromPublicKey(publicKey)
            .getOrElse {
                logger.debug { "cannot get remote identity from key of remote peer: $remoteIdentity" }
                return
            }
        if (remoteIdentityFromPublicKey != remoteIdentity) {
            logger.error { "received key for remote peer $remoteIdentity mismatch: $remoteIdentityFromPublicKey" }
            return
        }
        val currentRemoteIdentity = host.peerstore.remoteIdentity(remoteIdentity.peerId)
        if (currentRemoteIdentity == null) {
            host.peerstore.addRemoteIdentity(remoteIdentityFromPublicKey)
                .onFailure {
                    logger.error { "could not add remote identity for $remoteIdentity to peerstore: ${errorMessage(it)}" }
                }
            return
        }

        // ok, we have a remote identity, we should verify they match.
        if (currentRemoteIdentity == remoteIdentityFromPublicKey) {
            return
        }

        val localIdentity = connection.localIdentity
        logger.error { "[$localIdentity] identify got a different identity for: $remoteIdentity" }

        val remotePeerId = PeerId.fromPublicKey(publicKey)
            .getOrElse {
                logger.error { "cannot get PeerId from local key of remote peer: $remoteIdentity, ${errorMessage(it)}" }
                return
            }
        if (remotePeerId != currentRemoteIdentity.peerId) {
            logger.error { "local key for remote peer $remoteIdentity yields different PeerId: $remotePeerId" }
        } else {
            logger.error { "local key and received key for $remoteIdentity do not match, but match PeerId" }
        }
    }

    private fun consumeObservedAddress(observedAddress: ByteArray, connection: NetworkConnection) {
        if (observedAddress.isNotEmpty()) {
            InetMultiaddress.fromBytes(observedAddress)
                .onSuccess {
                    observedAddressManager.record(connection, it)
                }
                .onFailure {
                    logger.debug { "error parsing received observed address for ${connection.remoteIdentity}: ${errorMessage(it)}" }
                }
        }
    }

    private fun signedPeerRecordFromMessage(message: DbIdentify.Identify): Envelope? {
        if (!message.hasSignedPeerRecord()) {
            return null
        }
        val bytes = message.signedPeerRecord.toByteArray()
        if (bytes.isEmpty()) {
            return null
        }
        val consumeEnvelope = Envelope.consumeEnvelope(bytes, PeerRecord.PeerRecordEnvelopeDomain)
            .getOrElse {
                logger.error { "error getting peer record from Identify message: ${errorMessage(it)}" }
                return null
            }
        return consumeEnvelope.envelope
    }

    private fun filterAddresses(addresses: List<InetMultiaddress>, remoteAddress: InetMultiaddress): List<InetMultiaddress> {
        if (IpUtil.isIpLoopback(remoteAddress)) {
            return addresses
        }
        if (IpUtil.isPrivateAddress(remoteAddress)) {
            return AddressUtil.filterAddresses(addresses) {
                !IpUtil.isIpLoopback(it)
            }
        }
        return AddressUtil.filterAddresses(addresses) {
            IpUtil.isPublicAddress(it)
        }
    }

    companion object {
        private val Id = ProtocolId.of("/ipfs/id/1.0.0")
        private val IdPush = ProtocolId.of("/ipfs/id/push/1.0.0")
        private val StreamReadTimeout = 60.seconds
        private const val LibP2PVersion = "ipfs/0.1.0"
        private const val ServiceName = "libp2p.identify"
        private const val DefaultUserAgent = "erwinkok.org/libp2p"
        private const val LegacyIdSize = 2 * 1024
        private const val SignedIdSize = 8 * 1024
        private const val MaxMessages = 10
        private const val maxPushConcurrency = 32

        fun hasConsistentTransport(a: InetMultiaddress, green: List<InetMultiaddress>): Boolean {
            return green.any { it.networkProtocol == a.networkProtocol }
        }
    }
}
