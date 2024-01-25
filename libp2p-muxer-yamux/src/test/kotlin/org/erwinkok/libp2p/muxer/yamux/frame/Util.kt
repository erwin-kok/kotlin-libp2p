// Copyright (c) 2024 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.muxer.yamux.frame

import io.ktor.utils.io.ByteChannel
import org.erwinkok.result.expectNoErrors
import org.junit.jupiter.api.Assertions.assertInstanceOf

internal suspend inline fun <reified T : Frame> Frame.loopFrame(): T {
    val byteChannel = ByteChannel(false)
    byteChannel.writeMplexFrame(this)
    byteChannel.flush()
    val frame = byteChannel.readMplexFrame().expectNoErrors()
    assertInstanceOf(T::class.java, frame)
    return frame as T
}
