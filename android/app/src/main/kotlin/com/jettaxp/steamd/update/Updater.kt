package com.jettaxp.steamd.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
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
        connectTimeout = 15_000
        readTimeout = 30_000
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

    /** Downloads the release APK into the cache, reporting progress from 0 to 1. */
    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "update").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "steamd-${release.tag}.apk")
        val c = open(release.url)
        try {
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.size
            var done = 0L
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (release.size > 0 && file.length() != release.size) throw IllegalStateException("incomplete download")
            file
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }
}
