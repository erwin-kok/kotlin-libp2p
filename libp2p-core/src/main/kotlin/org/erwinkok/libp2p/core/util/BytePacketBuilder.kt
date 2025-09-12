// Copyright (c) 2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.

package org.erwinkok.libp2p.core.util

import io.ktor.utils.io.core.BytePacketBuilder
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.EOFException
import org.erwinkok.multiformat.util.UVarInt
import org.erwinkok.result.Err
import org.erwinkok.result.Errors
import org.erwinkok.result.Ok
import org.erwinkok.result.Result

fun ByteReadPacket.readUnsignedVarInt(): Result<ULong> {
    return UVarInt.readUnsignedVarInt {
        try {
            Ok(readByte().toUByte())
        } catch (_: EOFException) {
            Err(Errors.EndOfStream)
        }
    }
}

fun BytePacketBuilder.writeUnsignedVarInt(x: Int): Result<Int> {
    return writeUnsignedVarInt(x.toULong())
}

fun BytePacketBuilder.writeUnsignedVarInt(x: ULong): Result<Int> {
    return UVarInt.writeUnsignedVarInt(x) {
        try {
            this.writeByte(it)
            Ok(Unit)
        } catch (_: Exception) {
            Err(Errors.EndOfStream)
        }
    }
}
