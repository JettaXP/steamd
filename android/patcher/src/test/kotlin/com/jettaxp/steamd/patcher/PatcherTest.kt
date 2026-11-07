package com.jettaxp.steamd.patcher

import com.android.apksig.ApkVerifier
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

    /** End to end on a real APK (with a real manifest): STEAMD_REAL_APK=/path/to/any.apk */
    @Test
    fun patchesAndSignsRealApk() {
        assumeTrue(bundleFile != null)
        val real = System.getenv("STEAMD_REAL_APK")?.let(::File)?.takeIf { it.exists() }
        assumeTrue("STEAMD_REAL_APK not set", real != null)
        val dir = Files.createTempDirectory("steamd-real").toFile()
        val base = File(dir, "base.apk")
        // real APK + Steam's bundle
        ZipFile(real!!).use { zin ->
            ZipOutputStream(base.outputStream()).use { z ->
                for (e in zin.entries()) {
                    if (e.name == ApkPatcher.BUNDLE_ENTRY) continue
                    val data = zin.getInputStream(e).readBytes()
                    z.putNextEntry(ZipEntry(e.name).apply {
                        if (e.method == ZipEntry.STORED) {
                            method = ZipEntry.STORED; size = data.size.toLong(); compressedSize = data.size.toLong()
                            crc = CRC32().apply { update(data) }.value
                        }
                    }); z.write(data); z.closeEntry()
                }
                z.putNextEntry(ZipEntry(ApkPatcher.BUNDLE_ENTRY)); z.write(bundleFile!!.readBytes()); z.closeEntry()
            }
        }
        val unsigned = File(dir, "unsigned.apk")
        assertEquals(11, ApkPatcher.patch(base, unsigned, SteamThemePatch.Options()).colorsReplaced)
        val signed = File(dir, "signed.apk")
        ApkSigning.sign(unsigned, signed, SigningKey.generate())
        val v = ApkVerifier.Builder(signed).build().verify()
        assertTrue(v.errors.toString() + v.warnings.toString(), v.isVerified)
        assertTrue(v.isVerifiedUsingV2Scheme)
        System.getenv("STEAMD_KEEP")?.let { signed.copyTo(File(it), overwrite = true) }
        dir.deleteRecursively()
    }
}
