// Copyright (c) 2022-2023 Erwin Kok. BSD-3-Clause license. See LICENSE file for more details.
package org.erwinkok.libp2p.crypto.ed25519

class ProjCached {
    val yplusx: Element
    val yminusx: Element
    val z: Element
    val t2d: Element

    private constructor(yplusx: Element, yminusx: Element, z: Element, t2d: Element) {
        this.yplusx = yplusx
        this.yminusx = yminusx
        this.z = z
        this.t2d = t2d
    }

    constructor(p: ProjCached) : this(p.yplusx, p.yminusx, p.z, p.t2d)
    constructor(p: Point) : this(p.y + p.x, p.y - p.x, p.z, p.t * Element.d2)

    // Negates if cond == 1 and leaves it unchanged if cond == 0.
    fun negateConditionally(cond: Boolean): ProjCached {
        val (yplusx, yminusx) = Element.swap(yplusx, yminusx, cond)
        val t2d = Element.select(-t2d, t2d, cond)
        return ProjCached(yplusx, yminusx, z, t2d)
    }

    override fun hashCode(): Int {
        var result = yplusx.hashCode()
        result = 31 * result + yminusx.hashCode()
        result = 31 * result + z.hashCode()
        result = 31 * result + t2d.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }
        if (other !is ProjCached) {
            return super.equals(other)
        }
        return yplusx == other.yplusx &&
            yminusx == other.yminusx &&
            z == other.z &&
            t2d == other.t2d
    }

    companion object {
        val Zero = ProjCached(Element.One, Element.One, Element.One, Element.Zero)

        // Select sets v to a if cond == 1 and to b if cond == 0.
        fun select(a: ProjCached, b: ProjCached, cond: Boolean): ProjCached {
            val yplusx = Element.select(a.yplusx, b.yplusx, cond)
            val yminusx = Element.select(a.yminusx, b.yminusx, cond)
            val z = Element.select(a.z, b.z, cond)
            val t2d = Element.select(a.t2d, b.t2d, cond)
            return ProjCached(yplusx, yminusx, z, t2d)
        }
    }
}
