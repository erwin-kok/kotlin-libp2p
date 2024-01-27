// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import org.erwinkok.result.Error

object YamuxConst {
    val errInvalidVersion = Error("Invalid protocol version")
    val errInvalidMsgType = Error("Invalid message type")
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

    const val typeData = 0.toByte() // Data is used for data frames. They are followed by length bytes worth of payload.
    const val typeWindowUpdate = 1.toByte() // WindowUpdate is used to change the window of a given stream. The length indicates the delta update to the window.
    const val typePing = 2.toByte() // Ping is sent as a keep-alive or to measure the RTT. The StreamID and Length value are echoed back in the response.
    const val typeGoAway = 3.toByte() // GoAway is sent to terminate a session. The StreamID should be 0 and the length is an error code.

    const val flagSyn = 1.toShort() // SYN is sent to signal a new stream. May be sent with a data payload
    const val flagAck = 2.toShort() // ACK is sent to acknowledge a new stream. May be sent with a data payload
    const val flagFin = 4.toShort() // FIN is sent to half-close the given stream. May be sent with a data payload.
    const val flagRst = 8.toShort() // RST is used to hard close a given stream.

    // initialStreamWindow is the initial stream window size.
    // It's not an implementation choice, the value defined in the specification.
    const val initialStreamWindow: Int = 256 * 1024
    const val maxStreamWindow: Int = 16 * 1024 * 1024

    // goAwayNormal is sent on a normal termination
    const val goAwayNormal = 0

    // goAwayProtoErr sent on a protocol error
    const val goAwayProtoErr = 1

    // goAwayInternalErr sent on an internal error
    const val goAwayInternalErr = 2
}
