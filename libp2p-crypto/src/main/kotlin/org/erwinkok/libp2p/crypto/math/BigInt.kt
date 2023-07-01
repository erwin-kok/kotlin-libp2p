// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.math

import java.math.BigInteger
import java.util.Arrays

object BigInt {
    fun fromDecimal(s: String): BigInteger {
        return BigInteger(s, 10)
    }

    fun fromHex(s: String): BigInteger {
        return BigInteger(s, 16)
    }

    fun fromBytes(buf: ByteArray): BigInteger {
        return BigInteger(1, buf)
    }

    fun fromBytes(buf: ByteArray, off: Int, length: Int): BigInteger {
        var mag = buf
        if (off != 0 || length != buf.size) {
            mag = ByteArray(length)
            System.arraycopy(buf, off, mag, 0, length)
        }
        return BigInteger(1, mag)
    }

    fun toBytes(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        if (bytes[0].toInt() == 0 && bytes.size != 1) {
            val tmp = ByteArray(bytes.size - 1)
            System.arraycopy(bytes, 1, tmp, 0, tmp.size)
            return tmp
        }
        return bytes
    }

    fun toBytes(value: BigInteger, length: Int): ByteArray {
        val bytes = value.toByteArray()
        if (bytes.size == length) {
            return bytes
        }
        val start = if (bytes[0].toInt() == 0 && bytes.size != 1) 1 else 0
        val count = bytes.size - start
        require(count <= length) { "standard length exceeded for value" }
        val tmp = ByteArray(length)
        System.arraycopy(bytes, start, tmp, tmp.size - count, count)
        return tmp
    }

    fun toBytes(value: BigInteger, buf: ByteArray, off: Int, len: Int) {
        val bytes = value.toByteArray()
        if (bytes.size == len) {
            System.arraycopy(bytes, 0, buf, off, len)
            return
        }
        val start = if (bytes[0].toInt() == 0 && bytes.size != 1) 1 else 0
        val count = bytes.size - start
        require(count <= len) { "standard length exceeded for value" }
        val padLen = len - count
        Arrays.fill(buf, off, off + padLen, 0x00.toByte())
        System.arraycopy(bytes, start, buf, off + padLen, count)
    }
}
