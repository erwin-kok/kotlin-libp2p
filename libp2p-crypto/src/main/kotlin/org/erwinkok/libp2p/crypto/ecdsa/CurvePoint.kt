// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import java.math.BigInteger

data class CurvePoint(val x: BigInteger, val y: BigInteger) {
    override fun hashCode(): Int {
        return x.hashCode() xor y.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is CurvePoint) {
            return super.equals(other)
        }
        return x == other.x && y == other.y
    }
}
