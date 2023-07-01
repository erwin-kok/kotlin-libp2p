// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

data class JacobianPoint(val x: FieldVal, val y: FieldVal, val z: FieldVal) {
    // ToAffine reduces the Z value of the existing point to 1 effectively
    // making it an affine coordinate in constant time.  The point will be
    // normalized.
    fun toAffine(): Jacobian2dPoint {
        // Inversions are expensive and both point addition and point doubling
        // are faster when working with points that have a z value of one.  So,
        // if the point needs to be converted to affine, go ahead and normalize
        // the point itself at the same time as the calculation is the same.
        val zInv = z.inverse() // zInv = Z^-1
        val tempZ = zInv.square() // tempZ = Z^-2
        return Jacobian2dPoint((x * tempZ).normalize(), (y * tempZ * zInv).normalize())
    }

    override fun hashCode(): Int {
        return x.hashCode() xor y.hashCode() xor z.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is JacobianPoint) {
            return super.equals(other)
        }
        return x == other.x && y == other.y && z == other.z
    }

    companion object {
        val Zero: JacobianPoint = JacobianPoint(FieldVal.Zero, FieldVal.Zero, FieldVal.Zero)

        fun fromHex(x: String, y: String, z: String): JacobianPoint {
            return JacobianPoint(FieldVal.fromHex(x), FieldVal.fromHex(y), FieldVal.fromHex(z))
        }
    }
}
