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
        if (engine.fingerprint(engine.currentOptions()) == settings.appliedFingerprint) return Result.success()
        return try {
            engine.run(steam, silent = true)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC = "auto-repatch"
        private const val NOW = "auto-repatch-now"

        fun shouldRun(settings: Settings) =
            Build.VERSION.SDK_INT >= 31 && settings.autoRepatch && settings.colorSource == ColorSource.Wallpaper

        private val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!shouldRun(Settings(context))) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoRepatchWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
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
