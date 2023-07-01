// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ecdsa

import org.apache.kerby.asn1.Asn1
import org.apache.kerby.asn1.UniversalTag
import org.apache.kerby.asn1.type.Asn1Integer
import org.apache.kerby.asn1.type.Asn1Sequence
import org.erwinkok.result.Err
import org.erwinkok.result.Ok
import org.erwinkok.result.Result
import org.erwinkok.result.errorMessage
import java.io.IOException
import java.math.BigInteger

data class Asn1Signature(val r: BigInteger, val s: BigInteger) {
    fun marshal(): Result<ByteArray> {
        return try {
            val sequence = Asn1Sequence()
            sequence.addItem(Asn1Integer(r))
            sequence.addItem(Asn1Integer(s))
            Ok(sequence.encode())
        } catch (e: IOException) {
            Err("Could not marshal ASN1 Signature: ${errorMessage(e)}")
        }
    }

    companion object {
        fun unmarshal(data: ByteArray): Result<Asn1Signature> {
            if (data.isEmpty()) {
                return Err("Could not unmarshal ASN1 Signature: empty data")
            }
            return try {
                val type1 = Asn1.decode(data)
                if (type1.tag().universalTag() != UniversalTag.SEQUENCE) {
                    return Err("Expected ASN1 Sequence, but was: ${type1.tag().universalTag().toStr()}")
                }
                val sequence = type1 as Asn1Sequence
                val value = sequence.value
                if (value.size != 2) {
                    return Err("invalid length in ASN1 sequence: ${value.size}")
                }

                // R
                val type2 = value[0]
                if (type2.tag().universalTag() != UniversalTag.INTEGER || type2 !is Asn1Integer) {
                    return Err("Expected ASN1 Integer, but was: ${type2.tag().universalTag().toStr()}")
                }
                val r = type2.value ?: return Err("Could not unmarshal ASN1 Signature: r == null")

                // S
                val type3 = value[1]
                if (type3.tag().universalTag() != UniversalTag.INTEGER || type3 !is Asn1Integer) {
                    return Err("Expected ASN1 Integer, but was: ${type3.tag().universalTag().toStr()}")
                }
                val s = type3.value ?: return Err("Could not unmarshal ASN1 Signature: s == null")
                Ok(Asn1Signature(r, s))
            } catch (e: ClassCastException) {
                Err("Could not unmarshal ASN1 Signature: ${errorMessage(e)}")
            } catch (e: IOException) {
                Err("Could not unmarshal ASN1 Signature: ${errorMessage(e)}")
            } catch (e: IllegalArgumentException) {
                Err("Could not unmarshal ASN1 Signature: ${errorMessage(e)}")
            }
        }
    }
}
