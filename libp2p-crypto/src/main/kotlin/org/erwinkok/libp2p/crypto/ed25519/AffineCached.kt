// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

class AffineCached {
    val yplusx: Element
    val yminusx: Element
    val t2d: Element

    private constructor(yplusx: Element, yminusx: Element, t2d: Element) {
        this.yplusx = yplusx
        this.yminusx = yminusx
        this.t2d = t2d
    }

    constructor(p: AffineCached) : this(p.yplusx, p.yminusx, p.t2d)

    constructor(p: Point) {
        val zInv = p.z.invert()
        this.yplusx = (p.y + p.x) * zInv
        this.yminusx = (p.y - p.x) * zInv
        this.t2d = (p.t * Element.d2) * zInv
    }

    // Negates if cond == 1 and leaves it unchanged if cond == 0.
    fun negateConditionally(cond: Boolean): AffineCached {
        val (yplusx, yminusx) = Element.swap(yplusx, yminusx, cond)
        val t2d = Element.select(-t2d, t2d, cond)
        return AffineCached(yplusx, yminusx, t2d)
    }

    override fun hashCode(): Int {
        var result = yplusx.hashCode()
        result = 31 * result + yminusx.hashCode()
        result = 31 * result + t2d.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is AffineCached) {
            return super.equals(other)
        }
        return yplusx == other.yplusx &&
            yminusx == other.yminusx &&
            t2d == other.t2d
    }

    companion object {
        val Zero = AffineCached(Element.One, Element.One, Element.Zero)

        // Select sets v to a if cond == 1 and to b if cond == 0.
        fun select(a: AffineCached, b: AffineCached, cond: Boolean): AffineCached {
            val yplusx = Element.select(a.yplusx, b.yplusx, cond)
            val yminusx = Element.select(a.yminusx, b.yminusx, cond)
            val t2d = Element.select(a.t2d, b.t2d, cond)
            return AffineCached(yplusx, yminusx, t2d)
        }
    }
}
