// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux

import org.erwinkok.libp2p.muxer.yamux.YamuxConst.initialStreamWindow
import org.erwinkok.libp2p.muxer.yamux.YamuxConst.maxStreamWindow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.seconds

data class YamuxConfig(
    // AcceptBacklog is used to limit how many streams may be
    // waiting an accept.
    val acceptBacklog: Int = 256,

    // PingBacklog is used to limit how many ping acks we can queue.
    val pingBacklog: Int = 32,

    // EnableKeepalive is used to do a period keep alive
    // messages using a ping.
    val enableKeepAlive: Boolean = true,

    // KeepAliveInterval is how often to perform the keep alive
    val keepAliveInterval: Duration = 30.seconds,

    // MeasureRTTInterval is how often to re-measure the round trip time
    val measureRTTInterval: Duration = 30.seconds,

    // ConnectionWriteTimeout is meant to be a "safety valve" timeout after
    // we which will suspect a problem with the underlying connection and
    // close it. This is only applied to writes, where's there's generally
    // an expectation that things will move along quickly.
    val connectionWriteTimeout: Duration = 10.seconds,

    // MaxIncomingStreams is maximum number of concurrent incoming streams
    // that we accept. If the peer tries to open more streams, those will be
    // reset immediately.
    val maxIncomingStreams: UInt = 1000u,

    // InitialStreamWindowSize is used to control the initial
    // window size that we allow for a stream.
    val initialStreamWindowSize: UInt = initialStreamWindow,

    // MaxStreamWindowSize is used to control the maximum
    // window size that we allow for a stream.
    val maxStreamWindowSize: UInt = maxStreamWindow,

    // WriteCoalesceDelay is the maximum amount of time we'll delay
    // coalescing a packet before sending it. This should be on the order of
    // micro-milliseconds.
    val writeCoalesceDelay: Duration = 100.microseconds,

    // MaxMessageSize is the maximum size of a message that we'll send on a
    // stream. This ensures that a single stream doesn't hog a connection.
    val maxMessageSize: UInt = 64u * 1024u,
)