package com.jettaxp.steamd.patcher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class PatcherTest {
    // Steam's bundle is not part of the repository: tests that need it are skipped without it.
    private val bundleFile = System.getenv("STEAMD_BUNDLE")?.let(::File)?.takeIf { it.exists() }

    @Test
    fun matchesPythonPatcherByteForByte() {
        assumeTrue("STEAMD_BUNDLE not set", bundleFile != null)
        val expected = System.getenv("STEAMD_EXPECTED")?.let(::File)?.takeIf { it.exists() }
        assumeTrue("STEAMD_EXPECTED not set", expected != null)
        val r = SteamThemePatch.apply(bundleFile!!.readBytes(), SteamThemePatch.Options())
        assertEquals(11, r.colorsReplaced)
        assertTrue(r.storeTheme)
        assertArrayEquals(expected!!.readBytes(), r.bundle)
    }

    @Test
    fun patchedBundleParsesAndStringsPointToNewValues() {
        assumeTrue(bundleFile != null)
        val r = SteamThemePatch.apply(bundleFile!!.readBytes(), SteamThemePatch.Options())
        val hb = HermesBundle(r.bundle)  // validates header and SHA-1 footer
        assertEquals(96, hb.version)
        assertEquals(emptyList<Int>(), hb.findExact("#1A9FFF"))
        assertEquals(2, hb.findExact("#8AB4F8").size)
    }

    @Test
    fun secondPassFindsNothingToPatch() {
        assumeTrue(bundleFile != null)
        val once = SteamThemePatch.apply(bundleFile!!.readBytes(), SteamThemePatch.Options()).bundle
        val twice = SteamThemePatch.apply(once, SteamThemePatch.Options(storeTheme = false))
        assertEquals(0, twice.colorsReplaced)
    }

    @Test
    fun rebuildsAndSignsApk() {
        assumeTrue(bundleFile != null)
        val dir = Files.createTempDirectory("steamd").toFile()
        val base = File(dir, "base.apk")
        ZipOutputStream(base.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("assets/index.android.bundle")); z.write(bundleFile!!.readBytes()); z.closeEntry()
            val arsc = ByteArray(1000) { it.toByte() }
            z.putNextEntry(ZipEntry("resources.arsc").apply {
                method = ZipEntry.STORED; size = 1000; compressedSize = 1000
                crc = CRC32().apply { update(arsc) }.value
            }); z.write(arsc); z.closeEntry()
            z.putNextEntry(ZipEntry("META-INF/CERT.RSA")); z.write(byteArrayOf(1)); z.closeEntry()
        }
        val unsigned = File(dir, "unsigned.apk")
        val report = ApkPatcher.patch(base, unsigned, SteamThemePatch.Options())
        assertEquals(11, report.colorsReplaced)
        val key = SigningKey.loadOrCreate(File(dir, "key.p12"), "pw".toCharArray())
        val signed = File(dir, "signed.apk")
        ApkSigning.sign(unsigned, signed, key, minSdk = 26)
        // Full verification needs a real AndroidManifest.xml (checked with apksigner on a real APK);
        // here: v2/v3 signing block and a fresh v1 signature are present, the old one is gone.
        val bytes = signed.readBytes()
        assertTrue("APK Signing Block missing", String(bytes, Charsets.ISO_8859_1).contains("APK Sig Block 42"))
        ZipFile(signed).use { z ->
            assertEquals(null, z.getEntry("META-INF/CERT.RSA"))
            assertTrue(z.getEntry("META-INF/STEAMD.RSA") != null)
            HermesBundle(z.getInputStream(z.getEntry("assets/index.android.bundle")).readBytes())
        }
        // Same key file -> same certificate (needed for updates of the patched app)
        assertEquals(key.certificate, SigningKey.load(File(dir, "key.p12"), "pw".toCharArray()).certificate)
        dir.deleteRecursively()
    }
}
