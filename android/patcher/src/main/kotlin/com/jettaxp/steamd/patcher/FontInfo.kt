package com.jettaxp.steamd.patcher

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads family name, weight and italic flag from a TrueType/OpenType font. */
data class FontInfo(val family: String, val weight: Int, val italic: Boolean) {
    companion object {
        fun read(bytes: ByteArray): FontInfo {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
            val numTables = b.getShort(4).toInt() and 0xFFFF
            val tables = HashMap<String, Int>()
            for (k in 0 until numTables) {
                val r = 12 + 16 * k
                tables[String(bytes, r, 4, Charsets.US_ASCII)] = b.getInt(r + 8)
            }
            val weight = tables["OS/2"]?.let { b.getShort(it + 4).toInt() and 0xFFFF } ?: 400
            val fsSelection = tables["OS/2"]?.let { b.getShort(it + 62).toInt() and 0xFFFF } ?: 0
            val macStyle = tables["head"]?.let { b.getShort(it + 44).toInt() and 0xFFFF } ?: 0
            val italic = (fsSelection and 1) != 0 || (macStyle and 2) != 0
            return FontInfo(tables["name"]?.let { readFamily(b, bytes, it) } ?: "", weight, italic)
        }

        private fun readFamily(b: ByteBuffer, bytes: ByteArray, name: Int): String? {
            val count = b.getShort(name + 2).toInt() and 0xFFFF
            val strings = name + (b.getShort(name + 4).toInt() and 0xFFFF)
            var best: String? = null
            for (k in 0 until count) {
                val r = name + 6 + 12 * k
                val platform = b.getShort(r).toInt() and 0xFFFF
                val nameId = b.getShort(r + 6).toInt() and 0xFFFF
                val len = b.getShort(r + 8).toInt() and 0xFFFF
                val off = b.getShort(r + 10).toInt() and 0xFFFF
                if (nameId != 16 && nameId != 1) continue
                val s = if (platform == 3 || platform == 0) String(bytes, strings + off, len, Charsets.UTF_16BE)
                        else String(bytes, strings + off, len, Charsets.ISO_8859_1)
                if (nameId == 16) return s
                if (best == null) best = s
            }
            return best
        }
    }
}
