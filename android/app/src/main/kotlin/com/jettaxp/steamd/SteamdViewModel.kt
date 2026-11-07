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
import com.jettaxp.steamd.patch.AutoRepatchWorker
import com.jettaxp.steamd.patch.PatchEngine
import com.jettaxp.steamd.patch.SteamStatus
import com.jettaxp.steamd.patch.Step
import com.jettaxp.steamd.patcher.ApkPatcher
import com.jettaxp.steamd.patcher.SteamThemePatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Compat {
    data object Checking : Compat
    data class Supported(val hermesVersion: Int) : Compat
    data class Unsupported(val reason: String) : Compat
    data object ForeignPatch : Compat
}

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
    val autoRepatch: Boolean = true,
)

class SteamdViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val engine = PatchEngine(app)

    private val _state = MutableStateFlow(UiState(
        colorSource = settings.colorSource, storeTheme = settings.storeTheme, cssUrl = settings.cssUrl,
        appTheme = settings.appTheme, appDynamicColor = settings.appDynamicColor, language = Settings.language,
        autoRepatch = settings.autoRepatch,
    ))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        AutoRepatchWorker.schedule(app)
    }

    fun refresh() = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val (steam, status, cert, saved) = withContext(Dispatchers.IO) {
            val steam = SteamApp.find(ctx)
            Quad(steam, engine.status(steam), engine.ourCert(), engine.originalsBytes())
        }
        _state.update {
            it.copy(steam = steam, status = status, compat = Compat.Checking, keyFingerprint = cert.take(16),
                originalsBytes = saved, language = Settings.language)
        }
        if (steam != null) {
            val compat = withContext(Dispatchers.IO) { probe(steam, status) }
            _state.update { it.copy(compat = compat) }
        }
        if (status == SteamStatus.Patched) AutoRepatchWorker.checkNow(ctx)
    }

    private data class Quad(val steam: SteamInfo?, val status: SteamStatus, val cert: String, val saved: Long)

    private fun probe(steam: SteamInfo, status: SteamStatus): Compat = runCatching {
        val base = if (status == SteamStatus.Patched) engine.originalsFor(steam)?.first()
            ?: return Compat.Unsupported("original APKs of this version are not saved")
            else steam.baseApk
        val bundle = ApkPatcher.readBundle(base)
        if (status != SteamStatus.Patched && containsAscii(bundle, "--sd-p")) return Compat.ForeignPatch
        val r = SteamThemePatch.apply(bundle, SteamThemePatch.Options())
        if (r.colorsReplaced == 0 && !r.storeTheme) Compat.Unsupported("theme not found in this version")
        else Compat.Supported(r.hermesVersion)
    }.getOrElse { Compat.Unsupported(it.message ?: it.javaClass.simpleName) }

    private fun containsAscii(data: ByteArray, text: String): Boolean {
        val n = text.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..data.size - n.size) {
            for (k in n.indices) if (data[i + k] != n[k]) continue@outer
            return true
        }
        return false
    }

    fun setColorSource(source: ColorSource) {
        settings.colorSource = source
        _state.update { it.copy(colorSource = source) }
        AutoRepatchWorker.schedule(getApplication())
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

    fun setAutoRepatch(on: Boolean) {
        settings.autoRepatch = on
        _state.update { it.copy(autoRepatch = on) }
        AutoRepatchWorker.schedule(getApplication())
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

    fun exportKey(uri: android.net.Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val ok = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { engine.exportKey(it) } }.isSuccess
        }
        toast(if (ok) R.string.key_exported else R.string.key_error)
    }

    fun importKey(uri: android.net.Uri) = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                require(bytes.size < 64 * 1024)
                engine.importKey(bytes)
            }.isSuccess
        }
        toast(if (ok) R.string.key_imported else R.string.key_error)
        refresh()
    }

    private fun toast(res: Int) =
        android.widget.Toast.makeText(getApplication(), res, android.widget.Toast.LENGTH_LONG).show()

    fun clearOriginals() = viewModelScope.launch {
        withContext(Dispatchers.IO) { engine.clearOriginals() }
        refresh()
    }

    fun startPatch() = _state.update { it.copy(flow = PatchFlow.Confirm) }
    fun dismissFlow() = _state.update { it.copy(flow = PatchFlow.Idle) }.also { refresh() }

    fun runPatch() = viewModelScope.launch {
        val s = _state.value
        val steam = s.steam ?: return@launch
        val needsUninstall = s.status != SteamStatus.Patched
        try {
            engine.run(steam, silent = false) { st -> _state.update { it.copy(flow = PatchFlow.Running(st, needsUninstall)) } }
            _state.update { it.copy(flow = PatchFlow.Done) }
        } catch (e: Exception) {
            _state.update { it.copy(flow = PatchFlow.Failed(e.message ?: e.javaClass.simpleName)) }
        }
    }
}
