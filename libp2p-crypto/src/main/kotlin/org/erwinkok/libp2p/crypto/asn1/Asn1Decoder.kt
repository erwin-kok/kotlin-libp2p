// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.asn1

import org.apache.kerby.asn1.Asn1
import org.apache.kerby.asn1.Tag
import org.apache.kerby.asn1.UniversalTag
import org.apache.kerby.asn1.type.Asn1BitString
import org.apache.kerby.asn1.type.Asn1Constructed
import org.apache.kerby.asn1.type.Asn1Integer
import org.apache.kerby.asn1.type.Asn1ObjectIdentifier
import org.apache.kerby.asn1.type.Asn1OctetString
import org.apache.kerby.asn1.type.Asn1Sequence
import org.apache.kerby.asn1.type.Asn1Type
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import java.math.BigInteger

class Asn1Decoder private constructor(private val list: List<Asn1Type>) {
    var index = 0
        private set

    val size: Int
        get() = list.size

    val nextInt: Result<Int>
        get() {
            val type = list[index++]
            if (type.tag().universalTag() != UniversalTag.INTEGER) {
                return Err("Expected ASN1 Integer, but was: ${type.tag().universalTag().toStr()}")
            }
            return Ok((type as Asn1Integer).value.toInt())
        }

    val nextBigInt: Result<BigInteger>
        get() {
            val type = list[index++]
            if (type.tag().universalTag() != UniversalTag.INTEGER) {
                return Err(("Expected ASN1 Integer, but was: ${type.tag().universalTag().toStr()}"))
            }
            return Ok((type as Asn1Integer).value)
        }

    val nextOctetString: Result<ByteArray>
        get() {
            val type = list[index++]
            if (type.tag().universalTag() != UniversalTag.OCTET_STRING) {
                return Err(("Expected ASN1 OctetString, but was: ${type.tag().universalTag().toStr()}"))
            }
            return Ok((type as Asn1OctetString).value)
        }

    val nextBitString: Result<ByteArray>
        get() {
            val type = list[index++]
            if (type.tag().universalTag() != UniversalTag.BIT_STRING) {
                return Err(("Expected ASN1 BitString, but was: ${type.tag().universalTag().toStr()}"))
            }
            return Ok((type as Asn1BitString).value)
        }

    val nextObjectIdentifier: Result<String>
        get() {
            val type = list[index++]
            if (type.tag().universalTag() != UniversalTag.OBJECT_IDENTIFIER) {
                return Err(("Expected ASN1 ObjectIdentifier, but was: ${type.tag().universalTag().toStr()}"))
            }
            return Ok((type as Asn1ObjectIdentifier).value)
        }

    val nextSequence: Result<Asn1Decoder>
        get() {
            val type = list[index++]
            if (type.tag().universalTag() != UniversalTag.SEQUENCE) {
                return Err(("Expected ASN1 Sequence, but was: " + type.tag().universalTag().toStr()))
            }
            return Ok(Asn1Decoder((type as Asn1Sequence).value))
        }

    fun getNextDecoderWithTag(tag: Tag): Result<Asn1Decoder> {
        val type = list[index++] as? Asn1Constructed ?: return Err(("Expected Asn1Constructed."))
        if (type.tag() != tag) {
            return Err(("Unexpected tag encountered in Asn1Constructed: ${type.tag()}"))
        }
        return Ok(Asn1Decoder(type.value))
    }

    companion object {
        fun fromBytes(data: ByteArray): Result<Asn1Decoder> {
            val type = Asn1.decode(data)
            if (type.tag().universalTag() != UniversalTag.SEQUENCE) {
                return Err(("Expected ASN1 Sequence, but was: ${type.tag().universalTag().toStr()}"))
            }
            val sequence = type as Asn1Sequence
            val value = sequence.value
            return Ok(Asn1Decoder(value))
        }
    }
}
