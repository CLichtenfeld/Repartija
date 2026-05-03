package com.example.repartija.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.repartija.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import kotlin.math.max

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseNotes: String
)

class UpdateManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)

    private val _updateAvailable = MutableStateFlow<UpdateInfo?>(null)
    val updateAvailable: StateFlow<UpdateInfo?> = _updateAvailable.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    suspend fun checkForUpdates(force: Boolean = false) {
        val lastCheck = prefs.getLong("last_check_time", 0)
        val now = System.currentTimeMillis()
        val twentyFourHours = 24 * 60 * 60 * 1000L
        
        if (!force && (now - lastCheck) < twentyFourHours) {
            return
        }

        prefs.edit().putLong("last_check_time", now).apply()

        withContext(Dispatchers.IO) {
            try {
                val response = URL("https://api.github.com/repos/CLichtenfeld/Repartija/releases/latest").readText()
                val json = JSONObject(response)
                
                var latestVersion = json.getString("tag_name")
                if (latestVersion.startsWith("v")) {
                    latestVersion = latestVersion.substring(1)
                }

                val currentVersion = BuildConfig.VERSION_NAME

                if (isNewerVersion(currentVersion, latestVersion)) {
                    val assets = json.getJSONArray("assets")
                    var downloadUrl = ""
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.getString("name").endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }

                    if (downloadUrl.isNotEmpty()) {
                        val releaseNotes = json.optString("body", "Nueva versión disponible.")
                        _updateAvailable.value = UpdateInfo(latestVersion, downloadUrl, releaseNotes)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        
        val length = max(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun downloadAndInstall(updateInfo: UpdateInfo) {
        val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
            .setTitle("Actualización de Repartija")
            .setDescription("Descargando versión ${updateInfo.version}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Repartija_update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        _downloadProgress.value = 0f

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    _downloadProgress.value = 1f
                    context.unregisterReceiver(this)
                    installApk(downloadId, downloadManager)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        // Track progress
        Thread {
            var downloading = true
            while (downloading) {
                val q = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(q)
                if (cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1) {
                        val bytesDownloaded = cursor.getInt(bytesDownloadedIndex)
                        val bytesTotal = cursor.getInt(bytesTotalIndex)
                        
                        if (bytesTotal > 0) {
                            val progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                            _downloadProgress.value = progress
                        }
                    }
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex != -1) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            downloading = false
                        }
                    }
                }
                cursor.close()
                Thread.sleep(500)
            }
        }.start()
    }

    private fun installApk(downloadId: Long, downloadManager: DownloadManager) {
        val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: return

        val install = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        context.startActivity(install)
    }
}
