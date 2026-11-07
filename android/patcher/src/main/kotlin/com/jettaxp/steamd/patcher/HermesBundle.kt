package com.jettaxp.steamd.patcher

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Minimal reader/writer for the string table of a Hermes bytecode bundle.
 *
 * Only the string table is touched: entries can be repointed to other bytes in
 * the string storage. Bytecode, offsets and the file size stay the same, and the
 * SHA-1 footer is recomputed by [finish].
 *
 * Layout (bytecode v84-v96): 128-byte header, function headers (16 bytes each),
 * string kinds (4 bytes each), identifier hashes (4 bytes each),
 * small string table (4 bytes each), overflow string table (8 bytes each), string storage.
 */
class HermesBundle(original: ByteArray) {
    val data: ByteArray = original.copyOf()
    // Lookups always read the unmodified bytes, so earlier writes do not affect later searches.
    private val pristine: ByteArray = original
    private val buf: ByteBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    val version: Int
    val stringCount: Int
    private val smallTableOffset: Int
    private val overflowTableOffset: Int
    private val storageOffset: Int

    // Per-string view of the ORIGINAL table (used for lookups and overlap checks).
    private val offsets: IntArray
    private val lengths: IntArray      // in bytes
    private val utf16: BooleanArray
    private val overflow: BooleanArray

    // Overlap index: strings sorted by offset, with the two largest end positions so far.
    private val sorted: IntArray
    private val sortedOffsets: IntArray
    private val maxEnd1: IntArray
    private val maxEndIdx1: IntArray
    private val maxEnd2: IntArray

    init {
        if (data.size < 128 || buf.getLong(0) != MAGIC) throw PatchException("Not a Hermes bytecode bundle")
        version = buf.getInt(8)
        if (version !in SUPPORTED_VERSIONS) throw PatchException("Unsupported Hermes bytecode version $version")
        if (buf.getInt(32) != data.size) throw PatchException("Bundle size does not match its header")
        if (!sha1(data, data.size - 20).contentEquals(data.copyOfRange(data.size - 20, data.size))) {
            throw PatchException("SHA-1 footer mismatch: the bundle is already modified or damaged")
        }
        val functionCount = buf.getInt(40)
        val stringKindCount = buf.getInt(44)
        val identifierCount = buf.getInt(48)
        stringCount = buf.getInt(52)
        val overflowCount = buf.getInt(56)
        val storageSize = buf.getInt(60)

        var p = HEADER_SIZE
        p = align(p + functionCount * 16)
        p = align(p + stringKindCount * 4)
        p = align(p + identifierCount * 4)
        smallTableOffset = p
        overflowTableOffset = align(smallTableOffset + stringCount * 4)
        storageOffset = align(overflowTableOffset + overflowCount * 8)
        if (storageOffset + storageSize > data.size) throw PatchException("Corrupt string table")

        offsets = IntArray(stringCount)
        lengths = IntArray(stringCount)
        utf16 = BooleanArray(stringCount)
        overflow = BooleanArray(stringCount)
        for (i in 0 until stringCount) {
            val v = buf.getInt(smallTableOffset + 4 * i)
            val isUtf16 = (v and 1) != 0
            var off = (v ushr 1) and 0x7FFFFF
            var len = (v ushr 24) and 0xFF
            if (len == 0xFF) {
                overflow[i] = true
                val q = overflowTableOffset + 8 * off
                off = buf.getInt(q)
                len = buf.getInt(q + 4)
            }
            utf16[i] = isUtf16
            offsets[i] = off
            lengths[i] = if (isUtf16) len * 2 else len
        }

        sorted = (0 until stringCount).sortedBy { offsets[it] }.toIntArray()
        sortedOffsets = IntArray(stringCount) { offsets[sorted[it]] }
        maxEnd1 = IntArray(stringCount); maxEndIdx1 = IntArray(stringCount); maxEnd2 = IntArray(stringCount)
        var e1 = Int.MIN_VALUE; var i1 = -1; var e2 = Int.MIN_VALUE
        for (k in 0 until stringCount) {
            val j = sorted[k]
            val end = offsets[j] + lengths[j]
            if (end > e1) { e2 = e1; e1 = end; i1 = j } else if (end > e2) { e2 = end }
            maxEnd1[k] = e1; maxEndIdx1[k] = i1; maxEnd2[k] = e2
        }
    }

    fun isAscii(i: Int) = !utf16[i]
    fun isOverflow(i: Int) = overflow[i]
    fun byteLength(i: Int) = lengths[i]

    /** Latin-1 value of string [i] in the original bundle (null for UTF-16 strings). */
    fun string(i: Int): String? =
        if (utf16[i]) null else String(pristine, storageOffset + offsets[i], lengths[i], Charsets.ISO_8859_1)

    private fun originalEquals(i: Int, value: ByteArray): Boolean {
        if (utf16[i] || lengths[i] != value.size) return false
        val base = storageOffset + offsets[i]
        for (k in value.indices) if (pristine[base + k] != value[k]) return false
        return true
    }

    private fun originalStartsWith(i: Int, prefix: ByteArray): Boolean {
        if (utf16[i] || lengths[i] < prefix.size) return false
        val base = storageOffset + offsets[i]
        for (k in prefix.indices) if (pristine[base + k] != prefix[k]) return false
        return true
    }

    fun findExact(value: String): List<Int> {
        val b = value.toByteArray(Charsets.ISO_8859_1)
        return (0 until stringCount).filter { originalEquals(it, b) }
    }

    fun findStartingWith(prefix: String): List<Int> {
        val b = prefix.toByteArray(Charsets.ISO_8859_1)
        return (0 until stringCount).filter { originalStartsWith(it, b) }
    }

    /** True if any string other than [i] uses bytes in [a, b) of the storage. */
    fun isShared(i: Int, a: Int, b: Int): Boolean {
        var lo = 0; var hi = stringCount
        while (lo < hi) { val m = (lo + hi) ushr 1; if (sortedOffsets[m] < b) lo = m + 1 else hi = m }
        val k = lo - 1
        if (k < 0) return false
        val best = if (maxEndIdx1[k] != i) maxEnd1[k] else maxEnd2[k]
        return best > a
    }

    /** First string (in table order) that starts with one of [prefixes], is at least [need] bytes and shares no storage. */
    fun findDonor(prefixes: List<String>, need: Int): Int? {
        for (prefix in prefixes) {
            val pb = prefix.toByteArray(Charsets.ISO_8859_1)
            for (i in 0 until stringCount) {
                if (lengths[i] >= need && originalStartsWith(i, pb) && !isShared(i, offsets[i], offsets[i] + lengths[i])) return i
            }
        }
        return null
    }

    fun storageOffsetOf(i: Int) = offsets[i]

    fun writeStorage(offset: Int, bytes: ByteArray) {
        System.arraycopy(bytes, 0, data, storageOffset + offset, bytes.size)
    }

    /** Point string [i] at storage bytes [offset, offset + length). */
    fun repoint(i: Int, offset: Int, length: Int) {
        val t = smallTableOffset + 4 * i
        val v = buf.getInt(t)
        if (((v ushr 24) and 0xFF) != 0xFF) {
            if (length >= 0xFF || offset > 0x7FFFFF) throw PatchException("String #$i: value does not fit a small entry")
            buf.putInt(t, (v and 1) or (offset shl 1) or (length shl 24))
        } else {
            val q = overflowTableOffset + 8 * ((v ushr 1) and 0x7FFFFF)
            buf.putInt(q, offset)
            buf.putInt(q + 4, length)
        }
    }

    /** Recomputes the SHA-1 footer and returns the patched bundle. */
    fun finish(): ByteArray {
        System.arraycopy(sha1(data, data.size - 20), 0, data, data.size - 20, 20)
        return data
    }

    companion object {
        const val MAGIC = 0x1F1903C103BC1FC6L
        const val HEADER_SIZE = 128
        val SUPPORTED_VERSIONS = 84..96

        private fun align(p: Int) = (p + 3) and 3.inv()

        fun sha1(bytes: ByteArray, length: Int): ByteArray =
            MessageDigest.getInstance("SHA-1").apply { update(bytes, 0, length) }.digest()
    }
}

class PatchException(message: String) : Exception(message)
