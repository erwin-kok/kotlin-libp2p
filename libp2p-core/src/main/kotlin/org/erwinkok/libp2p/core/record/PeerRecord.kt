// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.record

import com.google.protobuf.ByteString
import com.google.protobuf.InvalidProtocolBufferException
import org.erwinkok.libp2p.core.host.PeerId
import org.erwinkok.libp2p.core.network.InetMultiaddress
import org.erwinkok.libp2p.core.record.pb.DbPeerRecord
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import org.erwinkok.result.map
import java.time.Instant

class PeerRecord private constructor(var peerId: PeerId, var addresses: List<InetMultiaddress>, var seq: Long) : Record {
    override fun marshalRecord(): Result<ByteArray> {
        return Ok(toProtoBuf().toByteArray())
    }

    private fun toProtoBuf(): DbPeerRecord.PeerRecord {
        return DbPeerRecord.PeerRecord.newBuilder()
            .setPeerId(ByteString.copyFrom(peerId.idBytes()))
            .addAllAddresses(addressesToProtobuf(addresses.toList()))
            .setSeq(seq)
            .build()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is PeerRecord) {
            return super.equals(other)
        }
        if (seq != other.seq) {
            return false
        }
        if (peerId != other.peerId) {
            return false
        }
        return if (addresses.size != other.addresses.size) {
            false
        } else {
            addresses.containsAll(other.addresses) &&
                other.addresses.containsAll(addresses)
        }
    }

    override fun hashCode(): Int {
        return peerId.hashCode() xor addresses.hashCode() xor seq.toInt()
    }

    companion object PeerRecordType : RecordType<PeerRecord> {
        const val PeerRecordEnvelopeDomain = "libp2p-peer-record"
        val PeerRecordEnvelopePayloadType = byteArrayOf(0x03, 0x01) // Multicodec.LIBP2P_PEER_RECORD;

        init {
            RecordRegistry.registerType(PeerRecordType)
        }

        override val domain: String
            get() = PeerRecordEnvelopeDomain

        override val codec: ByteArray
            get() = PeerRecordEnvelopePayloadType

        fun fromAddressInfo(addressInfo: AddressInfo): PeerRecord {
            return PeerRecord(addressInfo.peerId, addressInfo.addresses, timestampSeq())
        }

        fun fromProtoBuf(peerRecord: DbPeerRecord.PeerRecord): Result<PeerRecord> {
            return PeerId.fromBytes(peerRecord.peerId.toByteArray())
                .map {
                    PeerRecord(
                        it,
                        addressesFromProtobuf(peerRecord.addressesList),
                        peerRecord.seq,
                    )
                }
        }

        fun fromPeerIdAndAddresses(peerId: PeerId, addresses: List<InetMultiaddress>): Result<PeerRecord> {
            return Ok(PeerRecord(peerId, addresses, timestampSeq()))
        }

        override fun unmarshalRecord(data: ByteArray): Result<PeerRecord> {
            try {
                val peerRecord = DbPeerRecord.PeerRecord.parseFrom(data)
                val peerId = PeerId.fromBytes(peerRecord.peerId.toByteArray()).getOrElse { return Err("Could not generate PeerId from bytes") }
                val addresses = addressesFromProtobuf(peerRecord.addressesList)
                val seq = peerRecord.seq
                return Ok(PeerRecord(peerId, addresses, seq))
            } catch (e: InvalidProtocolBufferException) {
                return Err("Could not parse payload: ${errorMessage(e)}")
            }
        }

        private fun timestampSeq(): Long {
            return Instant.now().toEpochMilli()
        }

        private fun addressesFromProtobuf(addressesList: List<DbPeerRecord.PeerRecord.AddressInfo>): List<InetMultiaddress> {
            return addressesList.mapNotNull { a -> InetMultiaddress.fromBytes(a.multiaddr.toByteArray()).getOrElse { null } }
        }

        private fun addressesToProtobuf(addresses: List<InetMultiaddress>): List<DbPeerRecord.PeerRecord.AddressInfo> {
            return addresses.map { address -> DbPeerRecord.PeerRecord.AddressInfo.newBuilder().setMultiaddr(ByteString.copyFrom(address.bytes)).build() }
        }
    }
}
