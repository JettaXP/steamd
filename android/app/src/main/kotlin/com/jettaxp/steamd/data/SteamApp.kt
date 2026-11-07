package com.jettaxp.steamd.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** What we know about the Steam app installed on this device. */
data class SteamInfo(
    val versionName: String,
    val versionCode: Long,
    val baseApk: File,
    val splitApks: List<File>,
    val certSha256: String?,
) {
    val allApks: List<File> get() = listOf(baseApk) + splitApks
}

object SteamApp {
    const val PACKAGE = "com.valvesoftware.android.steam.community"

    fun find(context: Context): SteamInfo? {
        val pm = context.packageManager
        val info: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(signingFlag().toLong()))
            } else {
                @Suppress("DEPRECATION") pm.getPackageInfo(PACKAGE, signingFlag())
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val app = info.applicationInfo ?: return null
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        return SteamInfo(
            versionName = info.versionName ?: "?",
            versionCode = code,
            baseApk = File(app.sourceDir),
            splitApks = app.splitSourceDirs?.map(::File).orEmpty(),
            certSha256 = certSha256(info),
        )
    }

    @Suppress("DEPRECATION")
    private fun signingFlag() =
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun certSha256(info: PackageInfo): String? {
        val sig = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }?.lastOrNull()
        } else {
            info.signatures?.firstOrNull()
        } ?: return null
        return sha256(sig.toByteArray())
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun launch(context: Context) {
        context.packageManager.getLaunchIntentForPackage(PACKAGE)?.let { context.startActivity(it) }
    }
}
