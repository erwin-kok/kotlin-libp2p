// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import org.erwinkok.libp2p.muxer.yamux.YamuxConst.errInvalidFrameType
import org.erwinkok.libp2p.muxer.yamux.YamuxConst.errInvalidGoAwayType
import org.erwinkok.result.Err
import org.erwinkok.result.Error
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import kotlin.experimental.and
import kotlin.experimental.or

object YamuxConst {
    val errInvalidVersion = Error("Invalid protocol version")
    val errInvalidFrameType = Error("Invalid frame type")
    val errInvalidGoAwayType = Error("Invalid GoAway type")
    val errSessionShutdown = Error("Session shutdown")
    val errStreamsExhausted = Error("streams exhausted")
    val errDuplicateStream = Error("duplicate stream initiated")
    val errRecvWindowExceeded = Error("recv window exceeded")
    val errTimeout = Error("Timeout occurred")
    val errStreamClosed = Error("stream closed")
    val errUnexpectedFlag = Error("unexpected flag")
    val errRemoteGoAway = Error("remote end is not accepting connections")
    val errStreamReset = Error("stream reset")
    val errConnectionWriteTimeout = Error("connection write timeout")
    val errKeepAliveTimeout = Error("keepalive timeout")

    const val protoVersion = 0.toByte()

    // initialStreamWindow is the initial stream window size.
    // It's not an implementation choice, the value defined in the specification.
    const val initialStreamWindow: Int = 256 * 1024
    const val maxStreamWindow: Int = 16 * 1024 * 1024
}

enum class FrameType(val code: Byte) {
    TypeData(0), // Data is used for data frames. They are followed by length bytes worth of payload.
    TypeWindowUpdate(1), // WindowUpdate is used to change the window of a given stream. The length indicates the delta update to the window.
    TypePing(2), // Ping is sent as a keep-alive or to measure the RTT. The StreamID and Length value are echoed back in the response.
    TypeGoAway(3) // GoAway is sent to terminate a session. The StreamID should be 0 and the length is an error code.
    ;

    companion object {
        private val intToTypeMap = entries.associateBy { it.code }

        fun fromInt(value: Byte): Result<FrameType> {
            val type = intToTypeMap[value] ?: return Err(errInvalidFrameType)
            return Ok(type)
        }
    }
}

enum class GoAwayType(val code: Int) {
    GoAwayNormal(0), // goAwayNormal is sent on a normal termination
    GoAwayProtoError(1), // goAwayProtoErr sent on a protocol error
    GoAwayInternalError(2), // goAwayInternalErr sent on an internal error
    ;

    companion object {
        private val intToTypeMap = GoAwayType.entries.associateBy { it.code }

        fun fromInt(value: Int): Result<GoAwayType> {
            val type = intToTypeMap[value] ?: return Err(errInvalidGoAwayType)
            return Ok(type)
        }
    }
}

enum class Flag(val code: Short) {
    flagSyn(1), // SYN is sent to signal a new stream. May be sent with a data payload
    flagAck(2), // ACK is sent to acknowledge a new stream. May be sent with a data payload
    flagFin(4), // FIN is sent to half-close the given stream. May be sent with a data payload.
    flagRst(8), // RST is used to hard close a given stream.
}

class Flags(val code: Short = 0) {
    fun hasFlag(flag: Flag): Boolean {
        return ((code and flag.code) == flag.code)
    }

    companion object {
        private val intToTypeMap = GoAwayType.entries.associateBy { it.code }

        fun fromShort(value: Short): Result<Flags> {
            return Ok(Flags(value))
        }

        fun of(vararg values: Flag): Flags {
            val code = values.fold(0.toShort()) { code, flag -> code or flag.code }
            return Flags(code)
        }
    }
}
