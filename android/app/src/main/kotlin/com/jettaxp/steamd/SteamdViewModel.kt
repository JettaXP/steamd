package com.jettaxp.steamd

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jettaxp.steamd.data.AppLanguage
import com.jettaxp.steamd.data.AppTheme
import com.jettaxp.steamd.data.ColorSource
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SteamStatus { NotInstalled, Official, Patched, Other }

sealed interface Compat {
    data object Checking : Compat
    data class Supported(val hermesVersion: Int) : Compat
    data class Unsupported(val reason: String) : Compat
}

enum class Step { Copy, Patch, Sign, Uninstall, Install }

sealed interface PatchFlow {
    data object Idle : PatchFlow
    data object Confirm : PatchFlow
    data class Running(val step: Step, val needsUninstall: Boolean) : PatchFlow
    data object Done : PatchFlow
    data class Failed(val message: String) : PatchFlow
}

data class UiState(
    val steam: SteamInfo? = null,
    val status: SteamStatus = SteamStatus.NotInstalled,
    val compat: Compat = Compat.Checking,
    val colorSource: ColorSource = ColorSource.Default,
    val storeTheme: Boolean = true,
    val flow: PatchFlow = PatchFlow.Idle,
    val keyFingerprint: String = "",
    val cssUrl: String = SteamThemePatch.DEFAULT_CSS_URL,
    val appTheme: AppTheme = AppTheme.System,
    val appDynamicColor: Boolean = true,
    val language: AppLanguage = AppLanguage.System,
    val originalsBytes: Long = 0,
)

class SteamdViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val keyFile = File(app.filesDir, "signing/steamd.p12")
    private val originals = File(app.filesDir, "original")
    private val work = File(app.cacheDir, "patch")

    private val _state = MutableStateFlow(UiState(
        colorSource = settings.colorSource, storeTheme = settings.storeTheme, cssUrl = settings.cssUrl,
        appTheme = settings.appTheme, appDynamicColor = settings.appDynamicColor, language = Settings.language,
    ))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var key: SigningKey? = null

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val k = withContext(Dispatchers.IO) { key ?: SigningKey.loadOrCreate(keyFile, PASSWORD).also { key = it } }
        val ourCert = SteamApp.sha256(k.certificate.encoded)
        val steam = withContext(Dispatchers.IO) { SteamApp.find(ctx) }
        val status = when {
            steam == null -> SteamStatus.NotInstalled
            steam.certSha256 == ourCert -> SteamStatus.Patched
            steam.certSha256 == storedOfficialCert() || storedOfficialCert() == null -> SteamStatus.Official
            else -> SteamStatus.Other
        }
        val saved = withContext(Dispatchers.IO) { originals.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
        _state.update {
            it.copy(steam = steam, status = status, compat = Compat.Checking, keyFingerprint = ourCert.take(16),
                originalsBytes = saved, language = Settings.language)
        }
        if (steam != null) {
            val compat = withContext(Dispatchers.IO) { probe(steam, status) }
            _state.update { it.copy(compat = compat) }
        }
    }

    private fun probe(steam: SteamInfo, status: SteamStatus): Compat = runCatching {
        val base = if (status == SteamStatus.Patched) originalsFor(steam)?.first()
            ?: return Compat.Unsupported("original APKs of this version are not saved")
            else steam.baseApk
        val r = SteamThemePatch.apply(ApkPatcher.readBundle(base), SteamThemePatch.Options())
        if (r.colorsReplaced == 0 && !r.storeTheme) Compat.Unsupported("theme not found in this version")
        else Compat.Supported(r.hermesVersion)
    }.getOrElse { Compat.Unsupported(it.message ?: it.javaClass.simpleName) }

    fun setColorSource(source: ColorSource) {
        settings.colorSource = source
        _state.update { it.copy(colorSource = source) }
    }

    fun setStoreTheme(on: Boolean) {
        settings.storeTheme = on
        _state.update { it.copy(storeTheme = on) }
    }

    fun setCssUrl(url: String): Boolean {
        val u = url.trim().ifEmpty { SteamThemePatch.DEFAULT_CSS_URL }
        if (!Settings.CSS_URL.matches(u)) return false
        settings.cssUrl = u
        _state.update { it.copy(cssUrl = u) }
        return true
    }

    fun setAppTheme(theme: AppTheme) {
        settings.appTheme = theme
        _state.update { it.copy(appTheme = theme) }
    }

    fun setAppDynamicColor(on: Boolean) {
        settings.appDynamicColor = on
        _state.update { it.copy(appDynamicColor = on) }
    }

    fun setLanguage(language: AppLanguage) {
        _state.update { it.copy(language = language) }
        Settings.language = language
    }

    fun clearOriginals() = viewModelScope.launch {
        withContext(Dispatchers.IO) { originals.deleteRecursively() }
        refresh()
    }

    fun startPatch() = _state.update { it.copy(flow = PatchFlow.Confirm) }
    fun dismissFlow() = _state.update { it.copy(flow = PatchFlow.Idle) }.also { refresh() }

    fun runPatch() = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val s = _state.value
        val steam = s.steam ?: return@launch
        val needsUninstall = s.status != SteamStatus.Patched
        fun step(st: Step) = _state.update { it.copy(flow = PatchFlow.Running(st, needsUninstall)) }
        try {
            step(Step.Copy)
            val sources = withContext(Dispatchers.IO) { saveOriginals(steam) }
            step(Step.Patch)
            val options = Palettes.patchOptions(ctx, s.colorSource, s.storeTheme).copy(cssUrl = s.cssUrl)
            val out = withContext(Dispatchers.IO) {
                work.deleteRecursively(); work.mkdirs()
                val unsigned = File(work, "base-unsigned.apk")
                ApkPatcher.patch(sources.first(), unsigned, options)
                unsigned
            }
            step(Step.Sign)
            val signed = withContext(Dispatchers.IO) {
                val k = key ?: SigningKey.loadOrCreate(keyFile, PASSWORD).also { key = it }
                val list = mutableListOf<File>()
                File(work, "base.apk").also { ApkSigning.sign(out, it, k); list += it }
                out.delete()
                sources.drop(1).forEach { split -> File(work, split.name).also { ApkSigning.sign(split, it, k); list += it } }
                list
            }
            if (needsUninstall) {
                step(Step.Uninstall)
                val r = Installer.uninstall(ctx, SteamApp.PACKAGE)
                if (!r.success) throw IllegalStateException(r.message ?: "uninstall cancelled")
            }
            step(Step.Install)
            val r = Installer.install(ctx, signed)
            if (!r.success) throw IllegalStateException(r.message ?: "install cancelled")
            withContext(Dispatchers.IO) { work.deleteRecursively() }
            _state.update { it.copy(flow = PatchFlow.Done) }
        } catch (e: Exception) {
            _state.update { it.copy(flow = PatchFlow.Failed(e.message ?: e.javaClass.simpleName)) }
        }
    }

    private fun saveOriginals(steam: SteamInfo): List<File> {
        originalsFor(steam)?.let { return it }
        if (_state.value.status == SteamStatus.Patched) throw IllegalStateException("original APKs are not saved")
        originals.deleteRecursively()
        val dir = File(originals, steam.versionCode.toString()).apply { mkdirs() }
        val copies = steam.allApks.mapIndexed { i, apk ->
            File(dir, if (i == 0) "base.apk" else apk.name).also { apk.copyTo(it, overwrite = true) }
        }
        steam.certSha256?.let { File(originals, "official.cert").writeText(it) }
        return copies
    }

    private fun originalsFor(steam: SteamInfo): List<File>? {
        val dir = File(originals, steam.versionCode.toString())
        val base = File(dir, "base.apk")
        if (!base.exists()) return null
        return listOf(base) + (dir.listFiles().orEmpty().filter { it.name != "base.apk" }.sortedBy { it.name })
    }

    private fun storedOfficialCert(): String? = File(originals, "official.cert").takeIf { it.exists() }?.readText()

    companion object {
        private val PASSWORD = "steamd".toCharArray()
    }
}
