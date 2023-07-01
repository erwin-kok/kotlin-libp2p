// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

data class Jacobian2dPoint(val x: FieldVal, val y: FieldVal) {
    override fun hashCode(): Int {
        return x.hashCode() xor y.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Jacobian2dPoint) {
            return super.equals(other)
        }
        return x == other.x && y == other.y
    }

    companion object {
        val Zero: Jacobian2dPoint = Jacobian2dPoint(FieldVal.Zero, FieldVal.Zero)

        fun fromHex(x: String, y: String): Jacobian2dPoint {
            return Jacobian2dPoint(FieldVal.fromHex(x), FieldVal.fromHex(y))
        }
    }
}
