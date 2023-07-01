// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.secp256k1

import org.apache.kerby.asn1.type.Asn1Integer
import org.apache.kerby.asn1.type.Asn1Sequence
import org.erwinkok.libp2p.crypto.math.BigInt
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.util.Tuple
import org.erwinkok.util.Tuple2
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min

class Secp256k1Signature(val r: ModNScalar, val s: ModNScalar) {
    // Serialize returns the ECDSA signature in the Distinguished Encoding Rules
    // (DER) format per section 10 of [ISO/IEC 8825-1] and such that the S component
    // of the signature is less than or equal to the half order of the group.
    //
    // Note that the serialized bytes returned do not include the appended hash type
    // used in Decred signature scripts.
    fun serialize(): ByteArray {
        // The format of a DER encoded signature is as follows:
        //
        // 0x30 <total length> 0x02 <length of R> <R> 0x02 <length of S> <S>
        //   - 0x30 is the ASN.1 identifier for a sequence.
        //   - Total length is 1 byte and specifies length of all remaining data.
        //   - 0x02 is the ASN.1 identifier that specifies an integer follows.
        //   - Length of R is 1 byte and specifies how many bytes R occupies.
        //   - R is the arbitrary length big-endian encoded number which
        //     represents the R value of the signature.  DER encoding dictates
        //     that the value must be encoded using the minimum possible number
        //     of bytes.  This implies the first byte can only be null if the
        //     highest bit of the next byte is set in order to prevent it from
        //     being interpreted as a negative number.
        //   - 0x02 is once again the ASN.1 integer identifier.
        //   - Length of S is 1 byte and specifies how many bytes S occupies.
        //   - S is the arbitrary length big-endian encoded number which
        //     represents the S value of the signature.  The encoding rules are
        //     identical as those for R.

        // Ensure the S component of the signature is less than or equal to the half
        // order of the group because both S and its negation are valid signatures
        // modulo the order, so this forces a consistent choice to reduce signature
        // malleability.
        val sigS = if (s.isOverHalfOrder()) {
            -s
        } else {
            s
        }

        // Total length of returned signature is 1 byte for each magic and length
        // (6 total), plus lengths of R and S.
        val sequence = Asn1Sequence()
        sequence.addItem(Asn1Integer(BigInt.fromBytes(r.bytes())))
        sequence.addItem(Asn1Integer(BigInt.fromBytes(sigS.bytes())))
        return sequence.encode()
    }

    // Verify returns whether or not the signature is valid for the provided hash
    // and secp256k1 public key.
    fun verify(pubKey: Secp256k1PublicKey, hash: ByteArray): Boolean {
        // The algorithm for verifying an ECDSA signature is given as algorithm 4.30
        // in [GECC].
        //
        // The following is a paraphrased version for reference:
        //
        // G = curve generator
        // N = curve order
        // Q = public key
        // m = message
        // R, S = signature
        //
        // 1. Fail if R and S are not in [1, N-1]
        // 2. e = H(m)
        // 3. w = S^-1 mod N
        // 4. u1 = e * w mod N
        //    u2 = R * w mod N
        // 5. X = u1G + u2Q
        // 6. Fail if X is the point at infinity
        // 7. x = X.x mod N (X.x is the x coordinate of X)
        // 8. Verified if x == R
        //
        // However, since all group operations are done internally in Jacobian
        // projective space, the algorithm is modified slightly here in order to
        // avoid an expensive inversion back into affine coordinates at step 7.
        // Credits to Greg Maxwell for originally suggesting this optimization.
        //
        // Ordinarily, step 7 involves converting the x coordinate to affine by
        // calculating x = x / z^2 (mod P) and then calculating the remainder as
        // x = x (mod N).  Then step 8 compares it to R.
        //
        // Note that since R is the x coordinate mod N from a random point that was
        // originally mod P, and the cofactor of the secp256k1 curve is 1, there are
        // only two possible x coordinates that the original random point could have
        // been to produce R: x, where x < N, and x+N, where x+N < P.
        //
        // This implies that the signature is valid if either:
        // a) R == X.x / X.z^2 (mod P)
        //    => R * X.z^2 == X.x (mod P)
        // --or--
        // b) R + N < P && R + N == X.x / X.z^2 (mod P)
        //    => R + N < P && (R + N) * X.z^2 == X.x (mod P)
        //
        // Therefore the following modified algorithm is used:
        //
        // 1. Fail if R and S are not in [1, N-1]
        // 2. e = H(m)
        // 3. w = S^-1 mod N
        // 4. u1 = e * w mod N
        //    u2 = R * w mod N
        // 5. X = u1G + u2Q
        // 6. Fail if X is the point at infinity
        // 7. z = (X.z)^2 mod P (X.z is the z coordinate of X)
        // 8. Verified if R * z == X.x (mod P)
        // 9. Fail if R + N >= P
        // 10. Verified if (R + N) * z == X.x (mod P)

        // Step 1.
        //
        // Fail if R and S are not in [1, N-1].
        if (r.isZero || s.isZero) {
            return false
        }

        // Step 2.
        //
        // e = H(m)
        val e = ModNScalar.setByteSlice(hash)

        // Step 3.
        //
        // w = S^-1 mod N
        val w = s.inverse()

        // Step 4.
        //
        // u1 = e * w mod N
        // u2 = R * w mod N
        val u1 = e * w
        val u2 = r * w

        // Step 5.
        //
        // X = u1G + u2Q
        val q = pubKey.asJacobian()
        val u1G = Secp256k1Curve.scalarBaseMultNonConst(u1)
        val u2Q = Secp256k1Curve.scalarMultNonConst(u2, q)
        val x = Secp256k1Curve.addNonConst(u1G, u2Q)

        // Step 6.
        //
        // Fail if X is the point at infinity
        if ((x.x.isZero && x.y.isZero) || x.z.isZero) {
            return false
        }

        // Step 7.
        //
        // z = (X.z)^2 mod P (X.z is the z coordinate of X)
        val z = x.z.square()

        // Step 8.
        //
        // Verified if R * z == X.x (mod P)
        val sigRModP = modNScalarToField(r)
        val result = (sigRModP * z).normalize()
        if (result == x.x) {
            return true
        }

        // Step 9.
        //
        // Fail if R + N >= P
        if (sigRModP.isGtOrEqPrimeMinusOrder()) {
            return false
        }

        // Step 10.
        //
        // Verified if (R + N) * z == X.x (mod P)
        val verified = ((sigRModP + orderAsFieldVal) * z).normalize()
        return verified == x.x
    }

    override fun hashCode(): Int {
        var result = r.hashCode()
        result = 31 * result + s.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is Secp256k1Signature) {
            return super.equals(other)
        }
        return r == other.r &&
            s == other.s
    }

    companion object {
        // asn1SequenceID is the ASN.1 identifier for a sequence and is used when
        // parsing and serializing signatures encoded with the Distinguished
        // Encoding Rules (DER) format per section 10 of [ISO/IEC 8825-1].
        private const val asn1SequenceID = 0x30

        // asn1IntegerID is the ASN.1 identifier for an integer and is used when
        // parsing and serializing signatures encoded with the Distinguished
        // Encoding Rules (DER) format per section 10 of [ISO/IEC 8825-1].
        private const val asn1IntegerID = 0x02

        // minSigLen is the minimum length of a DER encoded signature and is
        // when both R and S are 1 byte each.
        //
        // 0x30 + <1-byte> + 0x02 + 0x01 + <byte> + 0x2 + 0x01 + <byte>
        private const val minSigLen = 8

        // maxSigLen is the maximum length of a DER encoded signature and is
        // when both R and S are 33 bytes each.  It is 33 bytes because a
        // 256-bit integer requires 32 bytes and an additional leading null byte
        // might be required if the high bit is set in the value.
        //
        // 0x30 + <1-byte> + 0x02 + 0x21 + <33 bytes> + 0x2 + 0x21 + <33 bytes>
        private const val maxSigLen = 72

        // sequenceOffset is the byte offset within the signature of the
        // expected ASN.1 sequence identifier.
        private const val sequenceOffset = 0

        // dataLenOffset is the byte offset within the signature of the expected
        // total length of all remaining data in the signature.
        private const val dataLenOffset = 1

        // rTypeOffset is the byte offset within the signature of the ASN.1
        // identifier for R and is expected to indicate an ASN.1 integer.
        private const val rTypeOffset = 2

        // rLenOffset is the byte offset within the signature of the length of
        // R.
        private const val rLenOffset = 3

        // rOffset is the byte offset within the signature of R.
        private const val rOffset = 4

        // compactSigSize is the size of a compact signature.  It consists of a
        // compact signature recovery code byte followed by the R and S components
        // serialized as 32-byte big-endian values. 1+32*2 = 65.
        // for the R and S components. 1+32+32=65.
        private const val compactSigSize = 65

        // compactSigMagicOffset is a value used when creating the compact signature
        // recovery code inherited from Bitcoin and has no meaning, but has been
        // retained for compatibility.  For historical purposes, it was originally
        // picked to avoid a binary representation that would allow compact
        // signatures to be mistaken for other components.
        private const val compactSigMagicOffset = 27.toByte()

        // compactSigCompPubKey is a value used when creating the compact signature
        // recovery code to indicate the original public key was compressed.
        private const val compactSigCompPubKey = 4.toByte()

        // pubKeyRecoveryCodeOddnessBit specifies the bit that indicates the oddess
        // of the Y coordinate of the random point calculated when creating a
        // signature.
        private const val pubKeyRecoveryCodeOddnessBit = (1 shl 0).toByte()

        // pubKeyRecoveryCodeOverflowBit specifies the bit that indicates the X
        // coordinate of the random point calculated when creating a signature was
        // >= N, where N is the order of the group.
        private const val pubKeyRecoveryCodeOverflowBit = (1 shl 1).toByte()

        private const val minValidCode = compactSigMagicOffset
        private const val maxValidCode = compactSigMagicOffset + compactSigCompPubKey + 3

        private val orderAsFieldVal = FieldVal.setByteSlice(BigInt.toBytes(Secp256k1Curve.n))

        // signRFC6979 generates a deterministic ECDSA signature according to RFC 6979
        // and BIP 62 and returns it along with an additional public key recovery code
        // for efficiently recovering the public key from the signature.
        fun signRFC6979(privKey: Secp256k1PrivateKey, hash: ByteArray): Tuple2<Secp256k1Signature, UByte> {
            // The algorithm for producing an ECDSA signature is given as algorithm 4.29
            // in [GECC].
            //
            // The following is a paraphrased version for reference:
            //
            // G = curve generator
            // N = curve order
            // d = private key
            // m = message
            // r, s = signature
            //
            // 1. Select random nonce k in [1, N-1]
            // 2. Compute kG
            // 3. r = kG.x mod N (kG.x is the x coordinate of the point kG)
            //    Repeat from step 1 if r = 0
            // 4. e = H(m)
            // 5. s = k^-1(e + dr) mod N
            //    Repeat from step 1 if s = 0
            // 6. Return (r,s)
            //
            // This is slightly modified here to conform to RFC6979 and BIP 62 as
            // follows:
            //
            // A. Instead of selecting a random nonce in step 1, use RFC6979 to generate
            //    a deterministic nonce in [1, N-1] parameterized by the private key,
            //    message being signed, and an iteration count for the repeat cases
            // B. Negate s calculated in step 5 if it is > N/2
            //    This is done because both s and its negation are valid signatures
            //    modulo the curve order N, so it forces a consistent choice to reduce
            //    signature malleability

            val privKeyScalar = privKey.key
            val privKeyBytes = ByteArray(32)
            privKeyScalar.putBytes(privKeyBytes, 0)
            var iteration = 0
            while (true) {
                // Step 1 with modification A.
                //
                // Generate a deterministic nonce in [1, N-1] parameterized by the
                // private key, message being signed, and iteration count.
                val k = Nonce.nonceRFC6979(privKeyBytes, hash, byteArrayOf(), byteArrayOf(), iteration)

                // Step 2.
                //
                // Compute kG
                //
                // Note that the point must be in affine coordinates.
                val kG = Secp256k1Curve.scalarBaseMultNonConst(k).toAffine()

                // Step 3.
                //
                // r = kG.x mod N
                // Repeat from step 1 if r = 0
                val (rn, overflow) = fieldToModNScalar(kG.x)
                if (!rn.isZero) {
                    // Since the secp256k1 curve has a cofactor of 1, when recovering a
                    // public key from an ECDSA signature over it, there are four possible
                    // candidates corresponding to the following cases:
                    //
                    // 1) The X coord of the random point is < N and its Y coord even
                    // 2) The X coord of the random point is < N and its Y coord is odd
                    // 3) The X coord of the random point is >= N and its Y coord is even
                    // 4) The X coord of the random point is >= N and its Y coord is odd
                    //
                    // Rather than forcing the recovery procedure to check all possible
                    // cases, this creates a recovery code that uniquely identifies which of
                    // the cases apply by making use of 2 bits.  Bit 0 identifies the
                    // oddness case and Bit 1 identifies the overflow case (aka when the X
                    // coord >= N).
                    //
                    // It is also worth noting that making use of Hasse's theorem shows
                    // there are around log_2((p-n)/p) ~= -127.65 ~= 1 in 2^127 points where
                    // the X coordinate is >= N.  It is not possible to calculate these
                    // points since that would require breaking the ECDLP, but, in practice
                    // this strongly implies with extremely high probability that there are
                    // only a few actual points for which this case is true.
                    var pubKeyRecoveryCode = (overflow shl 1).toUByte() or (kG.y.isOddBit).toUByte()

                    // Step 4.
                    //
                    // e = H(m)
                    //
                    // Note that this actually sets e = H(m) mod N which is correct since
                    // it is only used in step 5 which itself is mod N.
                    val e = ModNScalar.setByteSlice(hash)

                    // Step 5 with modification B.
                    //
                    // s = k^-1(e + dr) mod N
                    // Repeat from step 1 if s = 0
                    // s = -s if s > N/2
                    val kInv = k.inverse()
                    var sn = ((privKeyScalar * rn) + e) * kInv
                    if (!sn.isZero) {
                        if (sn.isOverHalfOrder()) {
                            sn = -sn

                            // Negating s corresponds to the random point that would have been
                            // generated by -k (mod N), which necessarily has the opposite
                            // oddness since N is prime, thus flip the pubkey recovery code
                            // oddness bit accordingly.
                            pubKeyRecoveryCode = pubKeyRecoveryCode xor 0x01u
                        }

                        // Step 6.
                        //
                        // Return (r,s)
                        return Tuple(Secp256k1Signature(rn, sn), pubKeyRecoveryCode)
                    }
                }
                iteration++
            }
        }

        // ParseDERSignature parses a signature in the Distinguished Encoding Rules
        // (DER) format per section 10 of [ISO/IEC 8825-1] and enforces the following
        // additional restrictions specific to secp256k1:
        //
        // - The R and S values must be in the valid range for secp256k1 scalars:
        //   - Negative values are rejected
        //   - Zero is rejected
        //   - Values greater than or equal to the secp256k1 group order are rejected
        fun parseDERSignature(sig: ByteArray): Result<Secp256k1Signature> {
            // The format of a DER encoded signature for secp256k1 is as follows:
            //
            // 0x30 <total length> 0x02 <length of R> <R> 0x02 <length of S> <S>
            //   - 0x30 is the ASN.1 identifier for a sequence
            //   - Total length is 1 byte and specifies length of all remaining data
            //   - 0x02 is the ASN.1 identifier that specifies an integer follows
            //   - Length of R is 1 byte and specifies how many bytes R occupies
            //   - R is the arbitrary length big-endian encoded number which
            //     represents the R value of the signature.  DER encoding dictates
            //     that the value must be encoded using the minimum possible number
            //     of bytes.  This implies the first byte can only be null if the
            //     highest bit of the next byte is set in order to prevent it from
            //     being interpreted as a negative number.
            //   - 0x02 is once again the ASN.1 integer identifier
            //   - Length of S is 1 byte and specifies how many bytes S occupies
            //   - S is the arbitrary length big-endian encoded number which
            //     represents the S value of the signature.  The encoding rules are
            //     identical as those for R.
            //
            // NOTE: The DER specification supports specifying lengths that can occupy
            // more than 1 byte, however, since this is specific to secp256k1
            // signatures, all lengths will be a single byte.
            // The signature must adhere to the minimum and maximum allowed length.
            val sigLen = sig.size
            if (sigLen < minSigLen) {
                return Err("malformed signature: too short: $sigLen < $minSigLen")
            }
            if (sigLen > maxSigLen) {
                return Err("malformed signature: too long: $sigLen > $maxSigLen")
            }
            // The signature must start with the ASN.1 sequence identifier.
            if (sig[sequenceOffset] != asn1SequenceID.toByte()) {
                return Err("malformed signature: format has wrong type: ${sig[sequenceOffset]}")
            }

            // The signature must indicate the correct amount of data for all elements
            // related to R and S.
            if (sig[dataLenOffset] != (sigLen - 2).toByte()) {
                return Err("malformed signature: bad length: ${sig[dataLenOffset]} != ${sigLen - 2}")
            }

            // Calculate the offsets of the elements related to S and ensure S is inside
            // the signature.
            //
            // rLen specifies the length of the big-endian encoded number which
            // represents the R value of the signature.
            //
            // sTypeOffset is the offset of the ASN.1 identifier for S and, like its R
            // counterpart, is expected to indicate an ASN.1 integer.
            //
            // sLenOffset and sOffset are the byte offsets within the signature of the
            // length of S and S itself, respectively.
            var rLen = sig[rLenOffset].toInt()
            val sTypeOffset = rOffset + rLen
            val sLenOffset = sTypeOffset + 1
            if (sTypeOffset >= sigLen) {
                return Err("malformed signature: S type indicator missing")
            }
            if (sLenOffset >= sigLen) {
                return Err("malformed signature: S length missing")
            }
            // The lengths of R and S must match the overall length of the signature.
            //
            // sLen specifies the length of the big-endian encoded number which
            // represents the S value of the signature.
            val sOffset = sLenOffset + 1
            var sLen = sig[sLenOffset].toInt()
            if (sOffset + sLen != sigLen) {
                return Err("malformed signature: invalid S length")
            }
            // R elements must be ASN.1 integers.
            if (sig[rTypeOffset] != asn1IntegerID.toByte()) {
                return Err("malformed signature: R integer marker: ${sig[rTypeOffset]} != $asn1IntegerID")
            }

            // Zero-length integers are not allowed for R.
            if (rLen == 0) {
                return Err("malformed signature: R length is zero")
            }

            // R must not be negative.
            if ((sig[rOffset] and 0x80.toByte()) != 0.toByte()) {
                return Err("malformed signature: R is negative")
            }

            // Null bytes at the start of R are not allowed, unless R would otherwise be
            // interpreted as a negative number.
            if (rLen > 1 && sig[rOffset] == 0x00.toByte() && (sig[rOffset + 1] and 0x80.toByte()) == 0.toByte()) {
                return Err("malformed signature: R value has too much padding")
            }

            // S elements must be ASN.1 integers.
            if (sig[sTypeOffset] != asn1IntegerID.toByte()) {
                return Err("malformed signature: S integer marker: ${sig[sTypeOffset]} != $asn1IntegerID")
            }

            // Zero-length integers are not allowed for S.
            if (sLen == 0) {
                return Err("malformed signature: S length is zero")
            }

            // S must not be negative.
            if ((sig[sOffset] and 0x80.toByte()) != 0.toByte()) {
                return Err("malformed signature: S is negative")
            }

            // Null bytes at the start of S are not allowed, unless S would otherwise be
            // interpreted as a negative number.
            if (sLen > 1 && sig[sOffset] == 0x00.toByte() && (sig[sOffset + 1] and 0x80.toByte()) == 0.toByte()) {
                return Err("malformed signature: S value has too much padding")
            }
            // The signature is validly encoded per DER at this point, however, enforce
            // additional restrictions to ensure R and S are in the range [1, N-1] since
            // valid ECDSA signatures are required to be in that range per spec.
            //
            // Also note that while the overflow checks are required to make use of the
            // specialized mod N scalar type, rejecting zero here is not strictly
            // required because it is also checked when verifying the signature, but
            // there really isn't a good reason not to fail early here on signatures
            // that do not conform to the ECDSA spec.

            // Strip leading zeroes from R.
            var rOff = rOffset
            while (rLen > 0 && sig[rOff] == 0x00.toByte()) {
                rOff++
                rLen--
            }
            if (rLen > 32) {
                return Err("invalid signature: R is larger than 256 bits")
            }
            val rBytes = ByteArray(32)
            System.arraycopy(sig, rOff, rBytes, max(0, 32 - rLen), min(32, rLen))
            var r = ModNScalar.setBytesUnchecked(rBytes)
            val rOverflows = r.overflows
            r = r.reduce256(rOverflows)
            if (rOverflows != 0u) {
                return Err("invalid signature: R >= group order")
            }
            if (r.isZero) {
                return Err("invalid signature: R is 0")
            }

            // Strip leading zeroes from S.
            var sOff = sOffset
            while (sLen > 0 && sig[sOff] == 0x00.toByte()) {
                sOff++
                sLen--
            }
            if (sLen > 32) {
                return Err("invalid signature: S is larger than 256 bits")
            }
            val sBytes = ByteArray(32)
            System.arraycopy(sig, sOff, sBytes, max(0, 32 - sLen), min(32, sLen))
            var s = ModNScalar.setBytesUnchecked(sBytes)
            val sOverflows = s.overflows
            s = s.reduce256(sOverflows)
            if (sOverflows != 0u) {
                return Err("invalid signature: S >= group order")
            }
            if (s.isZero) {
                return Err("invalid signature: S is 0")
            }
            return Ok(Secp256k1Signature(r, s))
        }

        // SignCompact produces a compact ECDSA signature over the secp256k1 curve for
        // the provided hash (which should be the result of hashing a larger message)
        // using the given private key.  The isCompressedKey parameter specifies if the
        // produced signature should reference a compressed public key or not.
        //
        // Compact signature format:
        // <1-byte compact sig recovery code><32-byte R><32-byte S>
        //
        // The compact sig recovery code is the value 27 + public key recovery code + 4
        // if the compact signature was created with a compressed public key.
        fun signCompact(key: Secp256k1PrivateKey, hash: ByteArray, isCompressedKey: Boolean): ByteArray {
            // Create the signature and associated pubkey recovery code and calculate
            // the compact signature recovery code.
            val (sig, pubKeyRecoveryCode) = signRFC6979(key, hash)
            var compactSigRecoveryCode = (compactSigMagicOffset + pubKeyRecoveryCode.toInt()).toByte()
            if (isCompressedKey) {
                compactSigRecoveryCode = (compactSigRecoveryCode + compactSigCompPubKey).toByte()
            }

            // Output <compactSigRecoveryCode><32-byte R><32-byte S>.
            val b = ByteArray(compactSigSize)
            b[0] = compactSigRecoveryCode
            sig.r.putBytes(b, 1)
            sig.s.putBytes(b, 33)
            return b
        }

        // RecoverCompact attempts to recover the secp256k1 public key from the provided
        // compact signature and message hash.  It first verifies the signature, and, if
        // the signature matches then the recovered public key will be returned as well
        // as a boolean indicating whether or not the original key was compressed.
        fun recoverCompact(signature: ByteArray, hash: ByteArray): Result<Tuple2<Secp256k1PublicKey, Boolean>> {
            // The following is very loosely based on the information and algorithm that
            // describes recovering a public key from and ECDSA signature in section
            // 4.1.6 of [SEC1].
            //
            // Given the following parameters:
            //
            // G = curve generator
            // N = group order
            // P = field prime
            // Q = public key
            // m = message
            // e = hash of the message
            // r, s = signature
            // X = random point used when creating signature whose x coordinate is r
            //
            // The equation to recover a public key candidate from an ECDSA signature
            // is:
            // Q = r^-1(sX - eG).
            //
            // This can be verified by plugging it in for Q in the sig verification
            // equation:
            // X = s^-1(eG + rQ) (mod N)
            //  => s^-1(eG + r(r^-1(sX - eG))) (mod N)
            //  => s^-1(eG + sX - eG) (mod N)
            //  => s^-1(sX) (mod N)
            //  => X (mod N)
            //
            // However, note that since r is the x coordinate mod N from a random point
            // that was originally mod P, and the cofactor of the secp256k1 curve is 1,
            // there are four possible points that the original random point could have
            // been to produce r: (r,y), (r,-y), (r+N,y), and (r+N,-y).  At least 2 of
            // those points will successfully verify, and all 4 will successfully verify
            // when the original x coordinate was in the range [N+1, P-1], but in any
            // case, only one of them corresponds to the original private key used.
            //
            // The method described by section 4.1.6 of [SEC1] to determine which one is
            // the correct one involves calculating each possibility as a candidate
            // public key and comparing the candidate to the authentic public key.  It
            // also hints that is is possible to generate the signature in a such a
            // way that only one of the candidate public keys is viable.
            //
            // A more efficient approach that is specific to the secp256k1 curve is used
            // here instead which is to produce a "pubkey recovery code" when signing
            // that uniquely identifies which of the 4 possibilities is correct for the
            // original random point and using that to recover the pubkey directly as
            // follows:
            //
            // 1. Fail if r and s are not in [1, N-1]
            // 2. Convert r to integer mod P
            // 3. If pubkey recovery code overflow bit is set:
            //    3.1 Fail if r + N >= P
            //    3.2 r = r + N (mod P)
            // 4. y = +sqrt(r^3 + 7) (mod P)
            //    4.1 Fail if y does not exist
            //    4.2 y = -y if needed to match pubkey recovery code oddness bit
            // 5. X = (r, y)
            // 6. e = H(m) mod N
            // 7. w = r^-1 mod N
            // 8. u1 = -(e * w) mod N
            //    u2 = s * w mod N
            // 9. Q = u1G + u2X
            // 10. Fail if Q is the point at infinity

            // A compact signature consists of a recovery byte followed by the R and
            // S components serialized as 32-byte big-endian values.
            if (signature.size != compactSigSize) {
                return Err("invalid compact signature size")
            }

            // Parse and validate the compact signature recovery code.
            var sigRecoveryCode = signature[0]
            if (sigRecoveryCode < minValidCode || sigRecoveryCode > maxValidCode) {
                return Err("invalid compact signature recovery code")
            }
            sigRecoveryCode = (sigRecoveryCode - compactSigMagicOffset).toByte()
            val wasCompressed = (sigRecoveryCode and compactSigCompPubKey) != 0.toByte()
            val pubKeyRecoveryCode = sigRecoveryCode and 3

            // Step 1.
            //
            // Parse and validate the R and S signature components.
            //
            // Fail if r and s are not in [1, N-1].
            val rBytes = ByteArray(32)
            System.arraycopy(signature, 1, rBytes, 0, 32)
            var r = ModNScalar.setBytesUnchecked(rBytes)
            val rOverflows = r.overflows
            r = r.reduce256(rOverflows)
            if (r.isZero) {
                return Err("signature R is 0")
            }

            val sBytes = ByteArray(32)
            System.arraycopy(signature, 33, sBytes, 0, 32)
            var s = ModNScalar.setBytesUnchecked(sBytes)
            val sOverflows = s.overflows
            s = s.reduce256(sOverflows)
            if (s.isZero) {
                return Err("signature S is 0")
            }

            // Step 2.
            //
            // Convert r to integer mod P.
            var fieldR = modNScalarToField(r)

            // Step 3.
            //
            // If pubkey recovery code overflow bit is set:
            if ((pubKeyRecoveryCode and pubKeyRecoveryCodeOverflowBit) != 0.toByte()) {
                // Step 3.1.
                //
                // Fail if r + N >= P
                //
                // Either the signature or the recovery code must be invalid if the
                // recovery code overflow bit is set and adding N to the R component
                // would exceed the field prime since R originally came from the X
                // coordinate of a random point on the curve.
                if (fieldR.isGtOrEqPrimeMinusOrder()) {
                    return Err("signature R + N >= P")
                }

                // Step 3.2.
                //
                // r = r + N (mod P)
                fieldR = fieldR + orderAsFieldVal
            }

            // Step 4.
            //
            // y = +sqrt(r^3 + 7) (mod P)
            // Fail if y does not exist.
            // y = -y if needed to match pubkey recovery code oddness bit
            //
            // The signature must be invalid if the calculation fails because the X
            // coord originally came from a random point on the curve which means there
            // must be a Y coord that satisfies the equation for a valid signature.
            val oddY = (pubKeyRecoveryCode and pubKeyRecoveryCodeOddnessBit) != 0.toByte()
            val (y, valid) = Secp256k1Curve.decompressY(fieldR, oddY)
            if (!valid) {
                return Err("signature is not for a valid curve point")
            }

            // Step 5.
            //
            // X = (r, y)
            val x = JacobianPoint(fieldR, y, FieldVal.One)

            // Step 6.
            //
            // e = H(m) mod N
            val e = ModNScalar.setByteSlice(hash)

            // Step 7.
            //
            // w = r^-1 mod N
            val w = r.inverse()

            // Step 8.
            //
            // u1 = -(e * w) mod N
            // u2 = s * w mod N
            val u1 = -(e * w)
            val u2 = s * w

            // Step 9.
            //
            // Q = u1G + u2X
            val u1G = Secp256k1Curve.scalarBaseMultNonConst(u1)
            val u2X = Secp256k1Curve.scalarMultNonConst(u2, x)
            val Q = Secp256k1Curve.addNonConst(u1G, u2X)

            // Step 10.
            //
            // Fail if Q is the point at infinity.
            //
            // Either the signature or the pubkey recovery code must be invalid if the
            // recovered pubkey is the point at infinity.
            if ((Q.x.isZero && Q.y.isZero) || Q.z.isZero) {
                return Err("recovered pubkey is the point at infinity")
            }

            // Notice that the public key is in affine coordinates.
            val pubKey = Secp256k1PublicKey(Q.toAffine())
            return Ok(Tuple(pubKey, wasCompressed))
        }

        // fieldToModNScalar converts a field value to scalar modulo the group order and
        // returns the scalar along with either 1 if it was reduced (aka it overflowed)
        // or 0 otherwise.
        //
        // Note that a bool is not used here because it is not possible in Go to convert
        // from a bool to numeric value in constant time and many constant-time
        // operations require a numeric value.
        private fun fieldToModNScalar(v: FieldVal): Tuple2<ModNScalar, UInt> {
            val buf = ByteArray(32)
            v.putBytes(buf, 0)
            val s = ModNScalar.setBytesUnchecked(buf)
            val overflow = s.overflows
            val sr = s.reduce256(overflow)
            return Tuple(sr, overflow)
        }

        // modNScalarToField converts a scalar modulo the group order to a field value.
        private fun modNScalarToField(v: ModNScalar): FieldVal {
            val buf = ByteArray(32)
            v.putBytes(buf, 0)
            return FieldVal.setBytes(buf)
        }
    }
}
