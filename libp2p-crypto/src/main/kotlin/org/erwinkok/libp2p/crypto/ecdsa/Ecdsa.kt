// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.erwinkok.libp2p.crypto.KeyPair
import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse
import java.math.BigInteger
import java.security.SecureRandom

object Ecdsa {
    private const val OID_NAMED_CURVE_P244 = "1.3.132.0.33"
    private const val OID_NAMED_CURVE_P256 = "1.2.840.10045.3.1.7"
    private const val OID_NAMED_CURVE_P384 = "1.3.132.0.34"
    private const val OID_NAMED_CURVE_P521 = "1.3.132.0.35"

    // GenerateECDSAKeyPair generates a new ecdsa private and public key
    fun generateKeyPair(random: SecureRandom = SecureRandom()): Result<KeyPair> {
        val privateKey = generatePrivateKey(Curve.p256, random)
        return Ok(KeyPair(privateKey, privateKey.publicKey))
    }

    fun generatePrivateKey(curve: Curve, random: SecureRandom): EcdsaPrivateKey {
        val k = randFieldElement(curve, random)
        val cp = curve.scalarBaseMult(BigInt.toBytes(k))
        val pub = EcdsaPublicKey(curve, cp)
        return EcdsaPrivateKey(pub, k)
    }

    fun marshalPrivateKey(key: EcdsaPrivateKey): Result<ByteArray> {
        val oid = oidFromNamedCurve(key.ecdsaPublicKey.curve) ?: return Err("unknown elliptic curve")
        val privateKey = BigInt.toBytes(key.d, (key.ecdsaPublicKey.curve.n.bitLength() + 7) / 8)
        val asn1PrivateKey = Asn1EcdsaPrivateKey(1, privateKey, oid, Curve.marshal(key.ecdsaPublicKey.curve, key.ecdsaPublicKey.cp))
        return asn1PrivateKey.marshal()
    }

    fun marshalPublicKey(pub: EcdsaPublicKey): Result<ByteArray> {
        return Asn1EcdsaPublicKey.marshal(pub)
    }

    // UnmarshalECDSAPrivateKey returns a private key from x509 bytes
    fun unmarshalPrivateKey(data: ByteArray): Result<EcdsaPrivateKey> {
        val (version, privateKey, namedCurveOID) = Asn1EcdsaPrivateKey.unmarshal(data)
            .getOrElse { return Err(it) }
        if (version != 1) {
            return Err("unknown EC private key version: $version")
        }
        val curve = namedCurveFromOid(namedCurveOID) ?: return Err("unknown elliptic curve: $namedCurveOID")
        val k = BigInt.fromBytes(privateKey)
        val curveOrder = curve.n
        if (k >= curveOrder) {
            return Err("invalid elliptic curve private key value")
        }
        val dstLength = (curveOrder.bitLength() + 7) / 8
        val dst = resizeKey(privateKey, dstLength)
        val cp = curve.scalarBaseMult(dst)
        return Ok(EcdsaPrivateKey(EcdsaPublicKey(curve, cp), k))
    }

    private fun resizeKey(src: ByteArray, dstLength: Int): ByteArray {
        val srcLength = src.size
        val dst: ByteArray
        if (srcLength == dstLength) {
            dst = src
        } else if (srcLength < dstLength) {
            dst = ByteArray(dstLength)
            System.arraycopy(src, 0, dst, dstLength - srcLength, srcLength)
        } else {
            dst = ByteArray(dstLength)
            val srcPos = srcLength - dstLength
            for (i in 0 until srcPos) {
                require(src[i].toInt() == 0) { "Resize truncates array values" }
            }
            System.arraycopy(src, srcPos, dst, 0, dstLength)
        }
        return dst
    }

    // UnmarshalECDSAPublicKey returns the public key from x509 bytes
    fun unmarshalPublicKey(data: ByteArray): Result<EcdsaPublicKey> {
        return Asn1EcdsaPublicKey.unmarshal(data)
    }

    fun sign(priv: EcdsaPrivateKey, hash: ByteArray, secureRandom: SecureRandom): Result<EcdsaSignature> {
        secureRandom.nextInt()
        val entropy = ByteArray(32)
        secureRandom.nextBytes(entropy)

        // Initialize an SHA-512 hash context; digest ...
        val curve = priv.ecdsaPublicKey.curve
        val n = curve.n
        if (n.signum() == 0) {
            return Err("Zero parameter")
        }
        var r: BigInteger? = null
        var s: BigInteger? = null
        var foundS = false
        while (!foundS) {
            var kInv: BigInteger? = null
            var foundR = false
            while (!foundR) {
                val k = randFieldElement(curve, secureRandom)
                kInv = fermatInverse(k, n)
                val (x) = curve.scalarBaseMult(BigInt.toBytes(k))
                r = x.mod(n)
                foundR = r.signum() != 0
            }
            val e = hashToInt(hash, curve)
            s = priv.d.multiply(r).add(e).multiply(kInv).mod(n)
            foundS = s.signum() != 0
        }
        val nr = r ?: return Err("Could not find r")
        val ns = s ?: return Err("Could not find s")
        return Ok(EcdsaSignature(nr, ns))
    }

    // Verify verifies the signature in r, s of hash using the public key, pub. Its
    // return value records whether the signature is valid.
    fun verify(pub: EcdsaPublicKey, hash: ByteArray, signature: EcdsaSignature): Result<Boolean> {
        // See [NSA] 3.4.2
        val curve = pub.curve
        val n = curve.n
        if (signature.r.signum() <= 0 || signature.s.signum() <= 0) {
            return Ok(false)
        }
        if (signature.r >= n || signature.s >= n) {
            return Ok(false)
        }
        val e = hashToInt(hash, curve)
        val w = signature.s.modInverse(n)
        val u1 = e.multiply(w).mod(n)
        val u2 = signature.r.multiply(w).mod(n)

        // Check if implements S1*g + S2*p
        val cp1 = curve.scalarBaseMult(BigInt.toBytes(u1))
        val cp2 = curve.scalarMult(pub.cp, BigInt.toBytes(u2))
        val (x, y) = curve.addPoint(cp1, cp2)
        return if (x.signum() == 0 && y.signum() == 0) {
            Ok(false)
        } else {
            Ok(x.mod(n).compareTo(signature.r) == 0)
        }
    }

    // hashToInt converts a hash value to an integer. There is some disagreement
    // about how this is done. [NSA] suggests that this is done in the obvious
    // manner, but [SECG] truncates the hash to the bit-length of the curve order
    // first. We follow [SECG] because that's what OpenSSL does. Additionally,
    // OpenSSL right shifts excess bits from the number if the hash is too large
    // and we mirror that too.
    private fun hashToInt(hash: ByteArray, curve: Curve): BigInteger {
        val orderBits = curve.n.bitLength()
        val orderBytes = (orderBits + 7) / 8
        val copiedHash = if (hash.size > orderBytes) {
            hash.copyOf(orderBytes)
        } else {
            hash
        }
        var ret = BigInt.fromBytes(copiedHash)
        val excess = copiedHash.size * 8 - orderBits
        if (excess > 0) {
            ret = ret.shiftRight(excess)
        }
        return ret
    }

    // fermatInverse calculates the inverse of k in GF(P) using Fermat's method.
    // This has better constant-time properties than Euclid's method (implemented
    // in math/big.Int.ModInverse) although math/big itself isn't strictly
    // constant-time so it's not perfect.
    private fun fermatInverse(k: BigInteger, n: BigInteger): BigInteger {
        val nMinus2 = n.subtract(BigInteger.TWO)
        return k.modPow(nMinus2, n)
    }

    // randFieldElement returns a random element of the field underlying the given
    // curve using the procedure given in [NSA] A.2.1.
    private fun randFieldElement(curve: Curve, secureRandom: SecureRandom): BigInteger {
        val b = ByteArray(curve.bitSize / 8 + 8)
        secureRandom.nextBytes(b)
        var k = BigInt.fromBytes(b)
        val n = curve.n.subtract(BigInteger.ONE)
        k = k.mod(n)
        return k.add(BigInteger.ONE)
    }

    fun namedCurveFromOid(oid: String): Curve? {
        return when (oid) {
            OID_NAMED_CURVE_P244 -> Curve.p244
            OID_NAMED_CURVE_P256 -> Curve.p256
            OID_NAMED_CURVE_P384 -> Curve.p384
            OID_NAMED_CURVE_P521 -> Curve.p521
            else -> null
        }
    }

    fun oidFromNamedCurve(curve: Curve): String? {
        return when (curve) {
            Curve.p244 -> OID_NAMED_CURVE_P244
            Curve.p256 -> OID_NAMED_CURVE_P256
            Curve.p384 -> OID_NAMED_CURVE_P384
            Curve.p521 -> OID_NAMED_CURVE_P521
            else -> null
        }
    }
}
