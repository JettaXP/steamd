package com.jettaxp.steamd.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

data class Release(val version: Long, val tag: String, val notes: String, val url: String, val size: Long)

object Updater {
    private const val LATEST = "https://api.github.com/repos/JettaXP/steamd/releases/latest"

    fun currentVersion(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "steamd")
    }

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val c = open(LATEST).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode !in 200..299) throw IllegalStateException("GitHub: HTTP ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = j.getString("tag_name")
            val version = tag.removePrefix("v").toLongOrNull() ?: return@withContext null
            val assets = j.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return@withContext null
            Release(version, tag, j.optString("body"), apk.getString("browser_download_url"), apk.getLong("size"))
        } finally {
            c.disconnect()
        }
    }

    /**
     * Downloads the release APK with the system DownloadManager (handles redirects, retries and slow
     * networks) into the app's own external files, reporting progress from 0 to 1.
     */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "update")
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "steamd-${release.tag}.apk")
        val request = DownloadManager.Request(Uri.parse(release.url))
            .setTitle("steamd ${release.tag}")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))
        val id = dm.enqueue(request)
        try {
            while (true) {
                coroutineContext.ensureActive()
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if (!c.moveToFirst()) throw IllegalStateException("download disappeared")
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        .takeIf { it > 0 } ?: release.size
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            if (release.size > 0 && file.length() != release.size) throw IllegalStateException("incomplete download")
                            return file
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            throw IllegalStateException("download failed (${reasonText(reason)})")
                        }
                    }
                }
                delay(300)
            }
        } catch (e: Exception) {
            dm.remove(id)
            file.delete()
            throw e
        }
    }

    private fun reasonText(reason: Int) = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "cannot resume"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "no storage"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file exists"
        DownloadManager.ERROR_FILE_ERROR -> "file error"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "network error"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough space"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "unexpected HTTP response"
        else -> if (reason in 100..599) "HTTP $reason" else "code $reason"
    }
}
