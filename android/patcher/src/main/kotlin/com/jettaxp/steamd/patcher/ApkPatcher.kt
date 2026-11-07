package com.jettaxp.steamd.patcher

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Rewrites base.apk: patches the Hermes bundle, optionally swaps fonts, drops the old
 * signature. The output is unsigned and unaligned: pass it to [ApkSigning.sign].
 */
object ApkPatcher {
    const val BUNDLE_ENTRY = "assets/index.android.bundle"

    class FontReplacement(val weight: Int, val italic: Boolean, val bytes: ByteArray)

    data class Report(val hermesVersion: Int, val colorsReplaced: Int, val storeTheme: Boolean,
                      val fontsReplaced: Int, val log: List<String>)

    /** Reads the bundle from an APK without changing anything. */
    fun readBundle(apk: File): ByteArray = ZipFile(apk).use { z ->
        val e = z.getEntry(BUNDLE_ENTRY) ?: throw PatchException("No Hermes bundle in ${apk.name}")
        z.getInputStream(e).use { it.readBytes() }
    }

    fun patch(
        baseApk: File,
        out: File,
        options: SteamThemePatch.Options,
        fonts: List<FontReplacement> = emptyList(),
        progress: (String) -> Unit = {},
    ): Report {
        var result: SteamThemePatch.Result? = null
        var fontsReplaced = 0
        val log = mutableListOf<String>()
        ZipFile(baseApk).use { zin ->
            ZipOutputStream(out.outputStream().buffered()).use { zout ->
                for (entry in zin.entries()) {
                    val name = entry.name
                    if (isOldSignature(name)) continue
                    var data = zin.getInputStream(entry).use { it.readBytes() }
                    if (name == BUNDLE_ENTRY) {
                        progress("bundle")
                        result = SteamThemePatch.apply(data, options).also { data = it.bundle; log += it.log }
                    } else if (fonts.isNotEmpty() && name.endsWith(".ttf", ignoreCase = true)) {
                        val info = runCatching { FontInfo.read(data) }.getOrNull()
                        if (info != null && info.family.contains("motiva", ignoreCase = true)) {
                            val r = fonts.filter { it.italic == info.italic }.ifEmpty { fonts }
                                .minBy { kotlin.math.abs(it.weight - info.weight) }
                            data = r.bytes
                            fontsReplaced++
                            log += "font $name: ${info.family} ${info.weight}${if (info.italic) " italic" else ""}"
                        }
                    }
                    zout.putNextEntry(copyEntry(entry, data))
                    zout.write(data)
                    zout.closeEntry()
                }
            }
        }
        val r = result ?: throw PatchException("No Hermes bundle in ${baseApk.name}")
        return Report(r.hermesVersion, r.colorsReplaced, r.storeTheme, fontsReplaced, log)
    }

    private fun isOldSignature(name: String): Boolean {
        if (!name.startsWith("META-INF/")) return false
        val n = name.substringAfterLast('/').uppercase()
        return n == "MANIFEST.MF" || n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".EC") || n.endsWith(".DSA")
    }

    private fun copyEntry(src: ZipEntry, data: ByteArray): ZipEntry = ZipEntry(src.name).apply {
        time = src.time
        if (src.method == ZipEntry.STORED) {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            crc = CRC32().apply { update(data) }.value
        } else {
            method = ZipEntry.DEFLATED
        }
    }
}
