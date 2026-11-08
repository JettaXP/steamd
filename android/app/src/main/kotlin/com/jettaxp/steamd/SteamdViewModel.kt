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

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: com.jettaxp.steamd.update.Release) : UpdateState
    data class Downloading(val release: com.jettaxp.steamd.update.Release, val progress: Float) : UpdateState
    data class Ready(val release: com.jettaxp.steamd.update.Release, val file: java.io.File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

sealed interface PatchFlow {
    data object Idle : PatchFlow
    data class Confirm(val update: Boolean) : PatchFlow
    data class Running(val step: Step, val needsUninstall: Boolean) : PatchFlow
    data class Done(val update: Boolean) : PatchFlow
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
    val autoStatus: String? = null,
    val googleSans: Boolean = true,
    val materialIcons: Boolean = true,
    val tabs: List<String> = com.jettaxp.steamd.patcher.SteamThemePatch.DEFAULT_TABS,
    val update: UpdateState = UpdateState.Idle,
    val versionCode: Long = 0,
)

class SteamdViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val engine = PatchEngine(app)

    private val _state = MutableStateFlow(UiState(
        colorSource = settings.colorSource, storeTheme = settings.storeTheme, cssUrl = settings.cssUrl,
        appTheme = settings.appTheme, appDynamicColor = settings.appDynamicColor, language = Settings.language,
        autoRepatch = settings.autoRepatch, googleSans = settings.googleSans, materialIcons = settings.materialIcons, tabs = settings.tabs,
    ))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
        AutoRepatchWorker.schedule(app)
        checkUpdate()
    }

    fun refresh() = viewModelScope.launch {
        val ctx = getApplication<Application>()
        val (steam, status, cert, saved) = withContext(Dispatchers.IO) {
            val steam = SteamApp.find(ctx)
            Quad(steam, engine.status(steam), engine.ourCert(), engine.originalsBytes())
        }
        _state.update {
            it.copy(steam = steam, status = status, compat = Compat.Checking, keyFingerprint = cert.take(16),
                originalsBytes = saved, language = Settings.language, autoStatus = settings.lastAuto)
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

    fun checkUpdate() = viewModelScope.launch {
        val ctx = getApplication<Application>()
        _state.update { it.copy(update = UpdateState.Checking, versionCode = com.jettaxp.steamd.update.Updater.currentVersion(ctx)) }
        val next = runCatching { com.jettaxp.steamd.update.Updater.latest() }
        _state.update { s ->
            s.copy(update = next.fold(
                onSuccess = { r -> if (r != null && r.version > s.versionCode) UpdateState.Available(r) else UpdateState.UpToDate },
                onFailure = { UpdateState.Failed(it.message ?: it.javaClass.simpleName) },
            ))
        }
    }

    fun downloadUpdate() = viewModelScope.launch {
        val release = (_state.value.update as? UpdateState.Available)?.release ?: return@launch
        _state.update { it.copy(update = UpdateState.Downloading(release, 0f)) }
        try {
            val file = withContext(Dispatchers.IO) {
                com.jettaxp.steamd.update.Updater.download(getApplication(), release) { p ->
                    _state.update { s -> if (s.update is UpdateState.Downloading) s.copy(update = UpdateState.Downloading(release, p)) else s }
                }
            }
            _state.update { it.copy(update = UpdateState.Ready(release, file)) }
        } catch (e: Exception) {
            _state.update { it.copy(update = UpdateState.Failed(e.message ?: e.javaClass.simpleName)) }
        }
    }

    fun installUpdate() = viewModelScope.launch {
        val ready = _state.value.update as? UpdateState.Ready ?: return@launch
        val r = runCatching { com.jettaxp.steamd.install.Installer.installSelf(getApplication(), ready.file) }
        r.exceptionOrNull()?.let { e -> _state.update { it.copy(update = UpdateState.Failed(e.message ?: e.javaClass.simpleName)) } }
        r.getOrNull()?.takeIf { !it.success }?.let { res -> _state.update { it.copy(update = UpdateState.Failed(res.message ?: "install cancelled")) } }
    }

    fun setTabs(tabs: List<String>) {
        settings.tabs = tabs
        _state.update { it.copy(tabs = settings.tabs) }
    }

    fun setMaterialIcons(on: Boolean) {
        settings.materialIcons = on
        _state.update { it.copy(materialIcons = on) }
    }

    fun setGoogleSans(on: Boolean) {
        settings.googleSans = on
        _state.update { it.copy(googleSans = on) }
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

    fun startPatch() = _state.update { it.copy(flow = PatchFlow.Confirm(update = it.status == SteamStatus.Patched)) }
    fun dismissFlow() = _state.update { it.copy(flow = PatchFlow.Idle) }.also { refresh() }

    fun runPatch() = viewModelScope.launch {
        val s = _state.value
        val steam = s.steam ?: return@launch
        val needsUninstall = s.status != SteamStatus.Patched
        try {
            engine.run(steam, silent = false) { st -> _state.update { it.copy(flow = PatchFlow.Running(st, needsUninstall)) } }
            _state.update { it.copy(flow = PatchFlow.Done(update = !needsUninstall)) }
        } catch (e: Exception) {
            _state.update { it.copy(flow = PatchFlow.Failed(e.message ?: e.javaClass.simpleName)) }
        }
    }
}
