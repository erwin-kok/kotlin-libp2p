// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.apache.kerby.asn1.Tag
import org.apache.kerby.asn1.type.Asn1BitString
import org.apache.kerby.asn1.type.Asn1Constructed
import org.apache.kerby.asn1.type.Asn1Integer
import org.apache.kerby.asn1.type.Asn1ObjectIdentifier
import org.apache.kerby.asn1.type.Asn1OctetString
import org.apache.kerby.asn1.type.Asn1Sequence
import org.erwinkok.libp2p.crypto.asn1.Asn1Decoder
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import org.erwinkok.result.getOrElse
import java.io.IOException

data class Asn1EcdsaPrivateKey(val version: Int, val privateKey: ByteArray, var namedCurveOID: String, val publicKey: ByteArray) {
    fun marshal(): Result<ByteArray> {
        return try {
            val sequence = Asn1Sequence()
            sequence.addItem(Asn1Integer(1L))
            sequence.addItem(Asn1OctetString(privateKey))
            val constructed1 = Asn1Constructed(Tag(0xA0))
            constructed1.addItem(Asn1ObjectIdentifier(namedCurveOID))
            sequence.addItem(constructed1)
            val constructed2 = Asn1Constructed(Tag(0xA1))
            constructed2.addItem(Asn1BitString(publicKey))
            sequence.addItem(constructed2)
            Ok(sequence.encode())
        } catch (e: IOException) {
            Err("Could not marshal ASN1 PrivateKey: ${errorMessage(e)}")
        }
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + privateKey.contentHashCode()
        result = 31 * result + namedCurveOID.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is Asn1EcdsaPrivateKey) {
            return super.equals(other)
        }
        return version == other.version &&
            privateKey.contentEquals(other.privateKey) &&
            namedCurveOID == other.namedCurveOID &&
            publicKey.contentEquals(other.publicKey)
    }

    companion object {
        fun unmarshal(data: ByteArray): Result<Asn1EcdsaPrivateKey> {
            return try {
                val decoder = Asn1Decoder.fromBytes(data)
                    .getOrElse { return Err(it) }
                val version = decoder.nextInt
                    .getOrElse { return Err(it) }
                val privateKey = decoder.nextOctetString
                    .getOrElse { return Err(it) }

                // NamedCurveOID
                val decoder1 = decoder.getNextDecoderWithTag(Tag(0xa0))
                    .getOrElse { return Err(it) }
                if (decoder1.size != 1) {
                    return Err("invalid length in ASN1 NamedCurveOID sequence: ${decoder1.size}")
                }
                val namedCurveOID = decoder1.nextObjectIdentifier
                    .getOrElse { return Err(it) }

                // PublicKey
                val decoder2 = decoder.getNextDecoderWithTag(Tag(0xa1))
                    .getOrElse { return Err(it) }
                if (decoder2.size != 1) {
                    return Err("invalid length in ASN1 PublicKey sequence: ${decoder2.size}")
                }
                val publicKey = decoder2.nextBitString
                    .getOrElse { return Err(it) }
                Ok(Asn1EcdsaPrivateKey(version, privateKey, namedCurveOID, publicKey))
            } catch (e: ClassCastException) {
                Err("Could not unmarshal ASN1 PrivateKey: ${errorMessage(e)}")
            } catch (e: IOException) {
                Err("Could not unmarshal ASN1 PrivateKey: ${errorMessage(e)}")
            }
        }
    }
}
