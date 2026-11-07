package com.jettaxp.steamd.patch

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jettaxp.steamd.data.ColorSource
import com.jettaxp.steamd.data.Settings
import com.jettaxp.steamd.data.SteamApp
import java.util.concurrent.TimeUnit

class AutoRepatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        if (!shouldRun(settings)) return Result.success()
        val engine = PatchEngine(applicationContext)
        val steam = SteamApp.find(applicationContext) ?: return Result.success()
        if (engine.status(steam) != SteamStatus.Patched || engine.originalsFor(steam) == null) return Result.success()
        val fp = engine.fingerprint(engine.currentOptions())
        val now = System.currentTimeMillis()
        if (fp == settings.appliedFingerprint) { settings.lastAuto = "same|$now"; return Result.success() }
        if (fp == settings.pendingFingerprint) { settings.lastAuto = "pending|$now"; return Result.success() }
        return try {
            val applied = kotlinx.coroutines.withTimeout(5 * 60_000L) { engine.run(steam, silent = true) }
            settings.lastAuto = (if (applied) "applied" else "pending") + "|$now"
            Result.success()
        } catch (e: Exception) {
            settings.lastAuto = "error:${e.message ?: e.javaClass.simpleName}|$now"
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        } finally {
            com.jettaxp.steamd.install.Installer.background = false
        }
    }

    companion object {
        private const val PERIODIC = "auto-repatch"
        private const val NOW = "auto-repatch-now"

        fun shouldRun(settings: Settings) =
            Build.VERSION.SDK_INT >= 31 && settings.autoRepatch && settings.colorSource == ColorSource.Wallpaper

        private val constraints = Constraints.Builder().setRequiresStorageNotLow(true).build()

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!shouldRun(Settings(context))) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoRepatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun checkNow(context: Context) {
            if (!shouldRun(Settings(context))) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoRepatchWorker>().setConstraints(constraints).build(),
            )
        }
    }
}
