// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.mplex

sealed class Frame(private val streamId: MplexStreamId, val type: Int) {
    val initiator: Boolean get() = streamId.initiator
    val id: Long get() = streamId.id

    internal class NewStreamFrame(id: Long, val name: String) : Frame(MplexStreamId(true, id), NewStreamTag)

    internal class MessageFrame(streamId: MplexStreamId, val data: ByteArray, type: Int) : Frame(streamId, type) {
        constructor(streamId: MplexStreamId, data: ByteArray) : this(streamId, data, if (streamId.initiator) MessageInitiatorTag else MessageReceiverTag)

        override fun equals(other: Any?): Boolean {
            if (other === this) {
                return true
            }
            if (other !is MessageFrame) {
                return super.equals(other)
            }
            if (!super.equals(other)) return false
            if (!data.contentEquals(other.data)) return false
            return true
        }

        override fun hashCode(): Int {
            return super.hashCode() xor data.contentHashCode()
        }
    }

    internal class CloseFrame(streamId: MplexStreamId, type: Int) : Frame(streamId, type) {
        constructor(streamId: MplexStreamId) : this(streamId, if (streamId.initiator) CloseInitiatorTag else CloseReceiverTag)
    }

    internal class ResetFrame(streamId: MplexStreamId, type: Int) : Frame(streamId, type) {
        constructor(streamId: MplexStreamId) : this(streamId, if (streamId.initiator) ResetInitiatorTag else ResetReceiverTag)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Frame) {
            return super.equals(other)
        }
        if (type != other.type) return false
        if (streamId != other.streamId) return false
        return true
    }

    override fun hashCode(): Int {
        return streamId.hashCode()
    }

    companion object {
        val Empty = ByteArray(0)

        const val NewStreamTag = 0
        const val MessageReceiverTag = 1
        const val MessageInitiatorTag = 2
        const val CloseReceiverTag = 3
        const val CloseInitiatorTag = 4
        const val ResetReceiverTag = 5
        const val ResetInitiatorTag = 6
    }
}
