// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.erwinkok.libp2p.crypto.CryptoUtil
import org.erwinkok.libp2p.crypto.PublicKey
import org.erwinkok.libp2p.crypto.pb.Crypto
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.getOrElse

// References:
//   [SEC1] Elliptic Curve Cryptography
//     https://www.secg.org/sec1-v2.pdf
//
//   [SEC2] Recommended Elliptic Curve Domain Parameters
//     https://www.secg.org/sec2-v2.pdf
//
//   [ANSI X9.62-1998] Public Key Cryptography For The Financial Services
//     Industry: The Elliptic Curve Digital Signature Algorithm (ECDSA)
class Secp256k1PublicKey(val key: Jacobian2dPoint) : PublicKey(Crypto.KeyType.Secp256k1) {

    // SerializeUncompressed serializes a public key in the 65-byte uncompressed
    // format.
    fun serializeUncompressed(): ByteArray {
        // 0x04 || 32-byte x coordinate || 32-byte y coordinate
        val b = ByteArray(PubKeyBytesLenUncompressed)
        b[0] = PubKeyFormatUncompressed
        key.x.putBytes(b, 1)
        key.y.putBytes(b, 33)
        return b
    }

    // SerializeCompressed serializes a public key in the 33-byte compressed format.
    fun serializeCompressed(): ByteArray {
        // Choose the format byte depending on the oddness of the Y coordinate.
        val format = if (key.y.isOdd) {
            PubKeyFormatCompressedOdd
        } else {
            PubKeyFormatCompressedEven
        }

        // 0x02 or 0x03 || 32-byte x coordinate
        val b = ByteArray(PubKeyBytesLenCompressed)
        b[0] = format
        key.x.putBytes(b, 1)
        return b
    }

    // AsJacobian converts the public key into a Jacobian point with Z=1 and stores
    // the result in the provided result param.  This allows the public key to be
    // treated a Jacobian point in the secp256k1 group in calculations.
    fun asJacobian(): JacobianPoint {
        return JacobianPoint(
            key.x,
            key.y,
            FieldVal.One,
        )
    }

    // IsOnCurve returns whether or not the public key represents a point on the
    // secp256k1 curve.
    fun isOnCurve(): Boolean {
        return Secp256k1Curve.isOnCurve(key.x, key.y)
    }

    override fun hashCode(): Int {
        var result = key.x.hashCode()
        result = 31 * result + key.y.hashCode()
        return result
    }

    override fun verify(data: ByteArray, signature: ByteArray): Result<Boolean> {
        val sig = Secp256k1Signature.parseDERSignature(signature)
            .getOrElse { return Err(it) }
        val hash = CryptoUtil.sha256Digest.digest(data)
        return Ok(sig.verify(this, hash))
    }

    override fun raw(): Result<ByteArray> {
        return Ok(serializeCompressed())
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Secp256k1PublicKey) {
            return super.equals(other)
        }
        return key.x == other.key.x &&
            key.y == other.key.y
    }

    override fun toString(): String {
        return "(${key.x}, ${key.y})"
    }

    companion object {
        // PubKeyBytesLenCompressed is the number of bytes of a serialized
        // compressed public key.
        const val PubKeyBytesLenCompressed = 33

        // PubKeyBytesLenUncompressed is the number of bytes of a serialized
        // uncompressed public key.
        const val PubKeyBytesLenUncompressed = 65

        // PubKeyFormatCompressedEven is the identifier prefix byte for a public key
        // whose Y coordinate is even when serialized in the compressed format per
        // section 2.3.4 of [SEC1](https://secg.org/sec1-v2.pdf#subsubsection.2.3.4).
        const val PubKeyFormatCompressedEven = 0x02.toByte()

        // PubKeyFormatCompressedOdd is the identifier prefix byte for a public key
        // whose Y coordinate is odd when serialized in the compressed format per
        // section 2.3.4 of [SEC1](https://secg.org/sec1-v2.pdf#subsubsection.2.3.4).
        const val PubKeyFormatCompressedOdd = 0x03.toByte()

        // PubKeyFormatUncompressed is the identifier prefix byte for a public key
        // when serialized according in the uncompressed format per section 2.3.3 of
        // [SEC1](https://secg.org/sec1-v2.pdf#subsubsection.2.3.3).
        const val PubKeyFormatUncompressed = 0x04.toByte()

        // PubKeyFormatHybridEven is the identifier prefix byte for a public key
        // whose Y coordinate is even when serialized according to the hybrid format
        // per section 4.3.6 of [ANSI X9.62-1998].
        //
        // NOTE: This format makes little sense in practice an therefore this
        // package will not produce public keys serialized in this format.  However,
        // it will parse them since they exist in the wild.
        const val PubKeyFormatHybridEven = 0x06.toByte()

        // PubKeyFormatHybridOdd is the identifier prefix byte for a public key
        // whose Y coordingate is odd when serialized according to the hybrid format
        // per section 4.3.6 of [ANSI X9.62-1998].
        //
        // NOTE: This format makes little sense in practice an therefore this
        // package will not produce public keys serialized in this format.  However,
        // it will parse them since they exist in the wild.
        internal const val PubKeyFormatHybridOdd = 0x07.toByte()

        // ParsePubKey parses a secp256k1 public key encoded according to the format
        // specified by ANSI X9.62-1998, which means it is also compatible with the
        // SEC (Standards for Efficient Cryptography) specification which is a subset of
        // the former.  In other words, it supports the uncompressed, compressed, and
        // hybrid formats as follows:
        //
        // Compressed:
        //   <format byte = 0x02/0x03><32-byte X coordinate>
        // Uncompressed:
        //   <format byte = 0x04><32-byte X coordinate><32-byte Y coordinate>
        // Hybrid:
        //   <format byte = 0x05/0x06><32-byte X coordinate><32-byte Y coordinate>
        //
        // NOTE: The hybrid format makes little sense in practice an therefore this
        // package will not produce public keys serialized in this format.  However,
        // this function will properly parse them since they exist in the wild.
        fun parsePubKey(serialized: ByteArray): Result<Secp256k1PublicKey> {
            when (serialized.size) {
                PubKeyBytesLenUncompressed -> {
                    // Reject unsupported public key formats for the given length.
                    val format = serialized[0]
                    if (
                        (format != PubKeyFormatUncompressed) &&
                        (format != PubKeyFormatHybridEven) &&
                        (format != PubKeyFormatHybridOdd)
                    ) {
                        return Err("invalid public key: unsupported format: $format")
                    }

                    // Parse the x and y coordinates while ensuring that they are in the
                    // allowed range.

                    val x = FieldVal.setByteSlice(serialized.copyOfRange(1, 33))
                    if (x.overflows) {
                        return Err("invalid public key: x >= field prime")
                    }
                    val y = FieldVal.setByteSlice(serialized.copyOfRange(33, 65))
                    if (y.overflows) {
                        return Err("invalid public key: y >= field prime")
                    }

                    // Ensure the oddness of the y coordinate matches the specified format
                    // for hybrid public keys.
                    if ((format == PubKeyFormatHybridEven) || (format == PubKeyFormatHybridOdd)) {
                        val wantOddY = format == PubKeyFormatHybridOdd
                        if (y.isOdd != wantOddY) {
                            return Err("invalid public key: y oddness does not match specified value of $wantOddY")
                        }
                    }

                    // Reject public keys that are not on the secp256k1 curve.
                    if (!Secp256k1Curve.isOnCurve(x, y)) {
                        return Err("invalid public key: [$x,$y] not on secp256k1 curve")
                    }
                    return Ok(Secp256k1PublicKey(Jacobian2dPoint(x, y)))
                }

                PubKeyBytesLenCompressed -> {
                    // Reject unsupported public key formats for the given length.
                    val format = serialized[0]
                    if (
                        (format != PubKeyFormatCompressedEven) &&
                        (format != PubKeyFormatCompressedOdd)
                    ) {
                        return Err("invalid public key: unsupported format: $format")
                    }

                    // Parse the x coordinate while ensuring that it is in the allowed
                    // range.
                    val x = FieldVal.setByteSlice(serialized.copyOfRange(1, 33))
                    if (x.overflows) {
                        return Err("invalid public key: x >= field prime")
                    }

                    // Attempt to calculate the y coordinate for the given x coordinate such
                    // that the result pair is a point on the secp256k1 curve and the
                    // solution with desired oddness is chosen.
                    val wantOddY = format == PubKeyFormatCompressedOdd
                    val (y, valid) = Secp256k1Curve.decompressY(x, wantOddY)
                    if (!valid) {
                        return Err("invalid public key: x coordinate $x is not on the secp256k1 curve")
                    }
                    return Ok(Secp256k1PublicKey(Jacobian2dPoint(x, y.normalize())))
                }

                else -> return Err("malformed public key: invalid length: ${serialized.size}")
            }
        }
    }
}
