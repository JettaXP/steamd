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
    private val bundleFile = System.getenv("STEAMD_BUNDLE")?.let(::File)?.takeIf { it.exists() }

    @Test
    fun matchesPythonPatcherByteForByte() {
        assumeTrue("STEAMD_BUNDLE not set", bundleFile != null)
        val expected = System.getenv("STEAMD_EXPECTED")?.let(::File)?.takeIf { it.exists() }
        assumeTrue("STEAMD_EXPECTED not set", expected != null)
        val r = SteamThemePatch.apply(bundleFile!!.readBytes(), SteamThemePatch.Options())
        assertEquals(12, r.colorsReplaced)
        assertTrue(r.storeTheme)
        assertArrayEquals(expected!!.readBytes(), r.bundle)
    }

    @Test
    fun patchedBundleParsesAndStringsPointToNewValues() {
        assumeTrue(bundleFile != null)
        val r = SteamThemePatch.apply(bundleFile!!.readBytes(), SteamThemePatch.Options())
        val hb = HermesBundle(r.bundle)
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
        assertEquals(12, report.colorsReplaced)
        val key = SigningKey.loadOrCreate(File(dir, "key.p12"), "pw".toCharArray())
        val signed = File(dir, "signed.apk")
        ApkSigning.sign(unsigned, signed, key, minSdk = 26)

        val bytes = signed.readBytes()
        assertTrue("APK Signing Block missing", String(bytes, Charsets.ISO_8859_1).contains("APK Sig Block 42"))
        ZipFile(signed).use { z ->
            assertEquals(null, z.getEntry("META-INF/CERT.RSA"))
            assertTrue(z.getEntry("META-INF/STEAMD.RSA") != null)
            HermesBundle(z.getInputStream(z.getEntry("assets/index.android.bundle")).readBytes())
        }

        assertEquals(key.certificate, SigningKey.load(File(dir, "key.p12"), "pw".toCharArray()).certificate)
        dir.deleteRecursively()
    }

    @Test
    fun patchesAndSignsRealApk() {
        assumeTrue(bundleFile != null)
        val real = System.getenv("STEAMD_REAL_APK")?.let(::File)?.takeIf { it.exists() }
        assumeTrue("STEAMD_REAL_APK not set", real != null)
        val dir = Files.createTempDirectory("steamd-real").toFile()
        val base = File(dir, "base.apk")

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
        assertEquals(12, ApkPatcher.patch(base, unsigned, SteamThemePatch.Options()).colorsReplaced)
        val signed = File(dir, "signed.apk")
        ApkSigning.sign(unsigned, signed, SigningKey.generate())
        val v = ApkVerifier.Builder(signed).build().verify()
        assertTrue(v.errors.toString() + v.warnings.toString(), v.isVerified)
        assertTrue(v.isVerifiedUsingV2Scheme)
        System.getenv("STEAMD_KEEP")?.let { signed.copyTo(File(it), overwrite = true) }
        dir.deleteRecursively()
    }

    @Test
    fun readsGoogleSansStyles() {
        val dir = File("../app/src/main/assets/fonts")
        assumeTrue(dir.exists())
        val styles = dir.listFiles { f -> f.name.endsWith(".ttf") }!!.associate { f ->
            val i = FontInfo.read(f.readBytes())
            f.name to (i.weight to i.italic)
        }
        assertEquals(400 to false, styles["GoogleSans-Regular.ttf"])
        assertEquals(500 to false, styles["GoogleSans-Medium.ttf"])
        assertEquals(700 to false, styles["GoogleSans-Bold.ttf"])
        assertEquals(400 to true, styles["GoogleSans-RegularItalic.ttf"])
        assertEquals(700 to true, styles["GoogleSans-BoldItalic.ttf"])
        assertTrue(FontInfo.read(File(dir, "GoogleSans-Regular.ttf").readBytes()).family == "Google Sans")
    }
}
