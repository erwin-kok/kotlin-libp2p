package org.erwinkok.libp2p.muxer.mplex

import io.ktor.util.moveTo
import io.ktor.util.moveToByteArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

class FrameReader {
    enum class State {
        HEADER,
        LENGTH,
        BODY,
        FINISHED,
    }

    private val state = AtomicReference(State.HEADER)

    private var tag: Int = 0
    private var id: Long = 0
    private var length: Int = 0
    private var body: ByteArray? = null

    // VarInt
    private var value = 0uL
    private var scale = 0
    private var index = 0

    // Body
    private var remaining: Int = 0
    private var buffer = ByteBuffer.allocate(1024)

    fun frame(bb: ByteBuffer): Frame? {
        require(bb.order() == ByteOrder.BIG_ENDIAN) { "Buffer order should be BIG_ENDIAN but it is ${bb.order()}" }
        while (bb.remaining() > 0 && handleStep(bb)) {
            // Keep on processing
        }
        return readFrame()
    }

    private fun handleStep(bb: ByteBuffer) = when (state.get()) {
        State.HEADER -> parseHeader(bb)
        State.LENGTH -> parseLength(bb)
        State.BODY -> parseBody(bb)
        else -> false
    }

    private fun parseHeader(bb: ByteBuffer): Boolean {
        return readUnsignedVarInt(bb) { header ->
            this.tag = (header and 0x7uL).toInt()
            this.id = (header shr 3).toLong()
            state.set(State.LENGTH)
        }
    }

    private fun parseLength(bb: ByteBuffer): Boolean {
        return readUnsignedVarInt(bb) { length ->
            this.length = length.toInt()
            if (this.length <= 0) {
                this.body = null
                state.set(State.FINISHED)
            } else {
                prepareBodyParse()
                state.set(State.BODY)
            }
        }
    }

    private fun prepareBodyParse() {
        this.remaining = length
        if (buffer.capacity() < length) {
            buffer = ByteBuffer.allocate(length)
        }
        buffer.clear()
    }

    private fun parseBody(bb: ByteBuffer): Boolean {
        remaining -= bb.moveTo(buffer, remaining)
        if (remaining <= 0) {
            buffer.flip()
            this.body = buffer.moveToByteArray()
            state.set(State.FINISHED)
            return false
        }
        return true
    }

    private fun readUnsignedVarInt(bb: ByteBuffer, result: (ULong) -> Unit): Boolean {
        if (bb.remaining() < 1) {
            return false
        }
        val uByte = bb.get().toUByte()
        if ((index == 8 && uByte >= 0x80u) || index >= 9) {
            // this is the 9th and last byte we're willing to read, but it
            // signals there's more (1 in MSB).
            // or this is the >= 10th byte, and for some reason we're still here.
            throw ProtocolViolationException("varints larger than uint63 are not supported")
        }
        if (uByte < 0x80u) {
            if (uByte == 0u.toUByte() && scale > 0) {
                throw ProtocolViolationException("varint not minimally encoded")
            }
            value = value or (uByte.toULong() shl scale)
            result(value)
            value = 0uL
            scale = 0
            index = 0
            return true
        }
        value = value or ((uByte and 0x7fu).toULong() shl scale)
        scale += 7
        index++
        return true
    }

    private fun readFrame(): Frame? {
        if (state.get() != State.FINISHED) {
            return null
        }
        state.set(State.HEADER)
        return when (tag) {
            Frame.NewStreamTag -> {
                val b = body
                val name = if (b != null) String(b) else ""
                Frame.NewStreamFrame(id, name)
            }

            Frame.MessageReceiverTag -> {
                Frame.MessageFrame(MplexStreamId(false, id), body ?: Frame.Empty, Frame.MessageReceiverTag)
            }

            Frame.MessageInitiatorTag -> {
                Frame.MessageFrame(MplexStreamId(true, id), body ?: Frame.Empty, Frame.MessageInitiatorTag)
            }

            Frame.CloseReceiverTag -> {
                if (length != 0) {
                    throw ProtocolViolationException("CloseFrame should not carry data")
                }
                return Frame.CloseFrame(MplexStreamId(false, id), Frame.CloseReceiverTag)
            }

            Frame.CloseInitiatorTag -> {
                if (length != 0) {
                    throw ProtocolViolationException("CloseFrame should not carry data")
                }
                Frame.CloseFrame(MplexStreamId(true, id), Frame.CloseInitiatorTag)
            }

            Frame.ResetReceiverTag -> {
                if (length != 0) {
                    throw ProtocolViolationException("ResetFrame should not carry data")
                }
                Frame.ResetFrame(MplexStreamId(false, id), Frame.ResetReceiverTag)
            }

            Frame.ResetInitiatorTag -> {
                if (length != 0) {
                    throw ProtocolViolationException("ResetFrame should not carry data")
                }
                Frame.ResetFrame(MplexStreamId(true, id), Frame.ResetInitiatorTag)
            }

            else -> throw ProtocolViolationException("Unknown Mplex tag type '$tag'")
        }
    }
}
