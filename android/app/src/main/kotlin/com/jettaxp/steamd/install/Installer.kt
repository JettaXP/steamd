package com.jettaxp.steamd.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import com.jettaxp.steamd.data.SteamApp
import java.io.File

data class InstallerResult(val action: String, val success: Boolean, val message: String?, val pending: Boolean = false)

object Installer {
    private val events = MutableSharedFlow<InstallerResult>(extraBufferCapacity = 4)
    val results: SharedFlow<InstallerResult> = events.asSharedFlow()

    internal fun emit(r: InstallerResult) { events.tryEmit(r) }

    @Volatile internal var background = false

    suspend fun uninstall(context: Context, packageName: String): InstallerResult {
        context.packageManager.packageInstaller.uninstall(packageName, sender(context, ACTION_UNINSTALL))
        return results.first { it.action == ACTION_UNINSTALL }
    }

    suspend fun install(context: Context, apks: List<File>, silent: Boolean = false): InstallerResult {
        background = silent
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(
                if (silent) PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED
                else PackageInstaller.SessionParams.USER_ACTION_REQUIRED,
            )
        }
        params.setAppPackageName(SteamApp.PACKAGE)
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            for (apk in apks) {
                session.openWrite(apk.name, 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
            session.commit(sender(context, ACTION_INSTALL))
        }
        return results.first { it.action == ACTION_INSTALL }
    }

    /** Installs a newer steamd over itself; Android shows its own update dialog. */
    suspend fun installSelf(context: Context, apk: File): InstallerResult {
        background = false
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        params.setAppPackageName(context.packageName)
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(sender(context, ACTION_SELF_UPDATE))
        }
        return results.first { it.action == ACTION_SELF_UPDATE }
    }

    private fun sender(context: Context, action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, InstallReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    ).intentSender

    const val ACTION_INSTALL = "com.jettaxp.steamd.INSTALL"
    const val ACTION_UNINSTALL = "com.jettaxp.steamd.UNINSTALL"
    const val ACTION_SELF_UPDATE = "com.jettaxp.steamd.SELF_UPDATE"
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                              else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    Installer.emit(InstallerResult(action, false, "no confirmation intent"))
                } else if (Installer.background && action == Installer.ACTION_INSTALL) {
                    Notifier.showConfirm(context, confirm)
                    Installer.emit(InstallerResult(action, false, "waiting for confirmation", pending = true))
                } else {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            else -> {
                val ok = status == PackageInstaller.STATUS_SUCCESS
                if (ok && action == Installer.ACTION_INSTALL) {
                    val settings = com.jettaxp.steamd.data.Settings(context)
                    settings.pendingFingerprint?.let { settings.appliedFingerprint = it; settings.pendingFingerprint = null }
                    Notifier.cancel(context)
                }
                Installer.emit(InstallerResult(action, ok, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
            }
        }
    }
}
