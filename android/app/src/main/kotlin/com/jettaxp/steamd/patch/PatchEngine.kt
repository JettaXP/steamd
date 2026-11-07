package com.jettaxp.steamd.patch

import android.content.Context
import com.jettaxp.steamd.data.Palettes
import com.jettaxp.steamd.data.Settings
import com.jettaxp.steamd.data.SteamApp
import com.jettaxp.steamd.data.SteamInfo
import com.jettaxp.steamd.install.Installer
import com.jettaxp.steamd.patcher.ApkPatcher
import com.jettaxp.steamd.patcher.ApkSigning
import com.jettaxp.steamd.patcher.SigningKey
import com.jettaxp.steamd.patcher.SteamThemePatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class SteamStatus { NotInstalled, Official, Patched, Other }

enum class Step { Copy, Patch, Sign, Uninstall, Install }

class PatchEngine(private val context: Context) {
    private val app = context.applicationContext
    private val settings = Settings(app)
    private val keyFile = File(app.filesDir, "signing/steamd.p12")
    private val originals = File(app.filesDir, "original")
    private val work = File(app.cacheDir, "patch")

    fun key(): SigningKey = synchronized(KEY_LOCK) {
        cachedKey ?: SigningKey.loadOrCreate(keyFile, PASSWORD).also { cachedKey = it }
    }

    fun ourCert(): String = SteamApp.sha256(key().certificate.encoded)

    fun exportKey(out: java.io.OutputStream) {
        key()
        keyFile.inputStream().use { it.copyTo(out) }
    }

    fun importKey(bytes: ByteArray): String {
        val tmp = File(app.cacheDir, "import.p12")
        try {
            tmp.writeBytes(bytes)
            val imported = SigningKey.load(tmp, PASSWORD)
            synchronized(KEY_LOCK) {
                keyFile.parentFile?.mkdirs()
                tmp.copyTo(keyFile, overwrite = true)
                cachedKey = imported
            }
            return SteamApp.sha256(imported.certificate.encoded)
        } finally {
            tmp.delete()
        }
    }

    fun status(steam: SteamInfo?): SteamStatus {
        val official = storedOfficialCert()
        return when {
            steam == null -> SteamStatus.NotInstalled
            steam.certSha256 == ourCert() -> SteamStatus.Patched
            official == null || steam.certSha256 == official -> SteamStatus.Official
            else -> SteamStatus.Other
        }
    }

    fun originalsBytes(): Long = originals.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearOriginals() { originals.deleteRecursively() }

    fun originalsFor(steam: SteamInfo): List<File>? {
        val dir = File(originals, steam.versionCode.toString())
        val base = File(dir, "base.apk")
        if (!base.exists()) return null
        return listOf(base) + dir.listFiles().orEmpty().filter { it.name != "base.apk" }.sortedBy { it.name }
    }

    fun currentOptions(): SteamThemePatch.Options =
        Palettes.patchOptions(app, settings.colorSource, settings.storeTheme).copy(cssUrl = settings.cssUrl)

    fun fingerprint(options: SteamThemePatch.Options): String =
        listOf(options.palette.values.joinToString(","), options.primary, options.cssUrl, options.storeTheme, options.radius)
            .joinToString("|")

    suspend fun run(steam: SteamInfo, silent: Boolean, onStep: (Step) -> Unit = {}) = MUTEX.withLock {
        val status = status(steam)
        val needsUninstall = status != SteamStatus.Patched
        if (silent && needsUninstall) throw IllegalStateException("silent update needs the patched app installed")
        onStep(Step.Copy)
        val sources = withContext(Dispatchers.IO) { saveOriginals(steam, status) }
        onStep(Step.Patch)
        val options = currentOptions()
        val unsigned = withContext(Dispatchers.IO) {
            work.deleteRecursively(); work.mkdirs()
            File(work, "base-unsigned.apk").also { ApkPatcher.patch(sources.first(), it, options) }
        }
        onStep(Step.Sign)
        val signed = withContext(Dispatchers.IO) {
            val k = key()
            val list = mutableListOf(File(work, "base.apk").also { ApkSigning.sign(unsigned, it, k) })
            unsigned.delete()
            sources.drop(1).forEach { split -> list += File(work, split.name).also { ApkSigning.sign(split, it, k) } }
            list
        }
        try {
            if (needsUninstall) {
                onStep(Step.Uninstall)
                val r = Installer.uninstall(app, SteamApp.PACKAGE)
                if (!r.success) throw IllegalStateException(r.message ?: "uninstall cancelled")
            }
            onStep(Step.Install)
            val r = Installer.install(app, signed, silent)
            if (!r.success) throw IllegalStateException(r.message ?: "install cancelled")
            settings.appliedFingerprint = fingerprint(options)
        } finally {
            withContext(Dispatchers.IO) { work.deleteRecursively() }
        }
    }

    private fun saveOriginals(steam: SteamInfo, status: SteamStatus): List<File> {
        originalsFor(steam)?.let { return it }
        if (status == SteamStatus.Patched) throw IllegalStateException("original APKs are not saved")
        originals.deleteRecursively()
        val dir = File(originals, steam.versionCode.toString()).apply { mkdirs() }
        val copies = steam.allApks.mapIndexed { i, apk ->
            File(dir, if (i == 0) "base.apk" else apk.name).also { apk.copyTo(it, overwrite = true) }
        }
        steam.certSha256?.let { File(originals, "official.cert").writeText(it) }
        return copies
    }

    private fun storedOfficialCert(): String? = File(originals, "official.cert").takeIf { it.exists() }?.readText()

    companion object {
        private val PASSWORD = "steamd".toCharArray()
        private val KEY_LOCK = Any()
        private val MUTEX = Mutex()
        @Volatile private var cachedKey: SigningKey? = null
    }
}
