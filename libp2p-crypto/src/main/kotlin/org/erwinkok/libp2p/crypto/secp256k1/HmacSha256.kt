// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import java.lang.Integer.min
import java.security.MessageDigest
import java.util.Arrays
import kotlin.experimental.xor

private const val Sha256BlockSize = 64

class HmacSha256(key: ByteArray) {
    // The blocksize of SHA256 in bytes.
    private val inner = MessageDigest.getInstance("SHA-256")
    private val outer = MessageDigest.getInstance("SHA-256")
    private val ipad = ByteArray(Sha256BlockSize)
    private val opad = ByteArray(Sha256BlockSize)

    init {
        initKey(key)
    }

    // Write adds data to the running hash.
    fun write(p: ByteArray) {
        inner.update(p)
    }

    // Write adds data to the running hash.
    fun write(p: ByteArray, offset: Int, len: Int) {
        inner.update(p, offset, len)
    }

    // ResetKey resets the HMAC-SHA256 to its initial state and then initializes it
    // with the provided key.  It is equivalent to creating a new instance with the
    // provided key without allocating more memory.
    fun resetKey(key: ByteArray) {
        inner.reset()
        outer.reset()
        Arrays.fill(ipad, 0)
        Arrays.fill(opad, 0)
        initKey(key)
    }

    // Resets the HMAC-SHA256 to its initial state using the current key.
    fun reset() {
        inner.reset()
        inner.update(ipad)
    }

    // Sum returns the hash of the written data.
    fun sum(): ByteArray {
        outer.reset()
        outer.update(opad)
        outer.update(inner.digest())
        return outer.digest()
    }

    // initKey initializes the HMAC-SHA256 instance to the provided key.
    private fun initKey(key: ByteArray) {
        // Hash the key if it is too large.
        var mkey = key
        if (key.size > Sha256BlockSize) {
            outer.update(key)
            mkey = outer.digest()
        }
        System.arraycopy(mkey, 0, ipad, 0, min(mkey.size, Sha256BlockSize))
        System.arraycopy(mkey, 0, opad, 0, min(mkey.size, Sha256BlockSize))
        for (i in ipad.indices) {
            ipad[i] = ipad[i] xor 0x36
        }
        for (i in opad.indices) {
            opad[i] = opad[i] xor 0x5c
        }
        inner.update(ipad)
    }
}
