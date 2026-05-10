package com.aviator.predictor.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadProgress(
    val percent: Int        = 0,
    val isComplete: Boolean = false,
    val isFailed: Boolean   = false,
    val localFile: File?    = null
)

object UpdateInstaller {

    /**
     * Enqueues the APK download via [DownloadManager].
     * Saves into the app's own external files dir (no storage permission needed,
     * and FileProvider can always serve it via <external-files-path>).
     */
    fun startDownload(context: Context, downloadUrl: String, fileName: String): Long {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            // Ensure destination directory exists
            val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            destDir?.mkdirs()

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Aviator Predictor Update")
                setDescription("Downloading $fileName…")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                // App-private external dir — no READ/WRITE_EXTERNAL_STORAGE permission needed
                // FileProvider serves this via <external-files-path name="downloads" path="Downloads/"/>
                setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
                )
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }

            dm.enqueue(request)
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Polls [DownloadManager] and emits [DownloadProgress] until done or failed.
     */
    suspend fun pollProgress(
        context: Context,
        downloadId: Long,
        fileName: String,
        onProgress: (DownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        while (true) {
            val query  = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(query)

            if (cursor == null || !cursor.moveToFirst()) {
                onProgress(DownloadProgress(isFailed = true))
                break
            }

            val statusCol     = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val totalCol      = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val downloadedCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val localUriCol   = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

            val status     = cursor.getInt(statusCol)
            val total      = cursor.getLong(totalCol)
            val downloaded = cursor.getLong(downloadedCol)
            val localUri   = cursor.getString(localUriCol)
            cursor.close()

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val file = resolveFile(context, localUri, fileName)
                    if (file != null) {
                        onProgress(DownloadProgress(percent = 100, isComplete = true, localFile = file))
                    } else {
                        onProgress(DownloadProgress(isFailed = true))
                    }
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    onProgress(DownloadProgress(isFailed = true))
                    break
                }
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_PENDING -> {
                    onProgress(DownloadProgress(percent = 0))
                }
                DownloadManager.STATUS_RUNNING -> {
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    onProgress(DownloadProgress(percent = percent))
                }
            }

            delay(600)
        }
    }

    /**
     * Resolves the APK [File] after download completes.
     * Tries multiple strategies so MIUI path mangling doesn't break things.
     */
    private fun resolveFile(context: Context, localUri: String?, fileName: String): File? {
        // Strategy 1: parse the URI the DownloadManager gave us
        if (!localUri.isNullOrBlank()) {
            try {
                val path = Uri.parse(localUri).path
                if (!path.isNullOrBlank()) {
                    val f = File(path)
                    if (f.exists() && f.length() > 0) return f
                }
            } catch (_: Exception) {}
        }

        // Strategy 2: canonical app-private external Downloads path
        val appPrivate = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (appPrivate.exists() && appPrivate.length() > 0) return appPrivate

        // Strategy 3: public Downloads (fallback for older DownloadManager behaviour)
        val publicDownloads = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (publicDownloads.exists() && publicDownloads.length() > 0) return publicDownloads

        return null
    }

    /**
     * Cancels an in-progress download.
     */
    fun cancelDownload(context: Context, downloadId: Long) {
        runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(downloadId)
        }
    }

    /**
     * Returns true if the app can install unknown packages.
     */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true

    /**
     * Opens the "Install unknown apps" settings page for this app.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data  = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }
    }

    /**
     * Launches the system package installer for [apkFile].
     *
     * On MIUI the FileProvider URI must be granted explicitly — this method
     * uses FLAG_GRANT_READ_URI_PERMISSION and also queries all possible
     * installer packages to grant them read access before firing the Intent.
     */
    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val uri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } catch (e: Exception) {
            // Last-ditch: plain file URI (works on very old devices)
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }

        // Grant read permission to every app that could handle this intent
        // (covers MIUI's separate installer package)
        try {
            val resolvedActivities = context.packageManager
                .queryIntentActivities(intent, 0)
            for (info in resolvedActivities) {
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (_: Exception) {}

        context.startActivity(intent)
    }

    /**
     * Finds a previously downloaded APK. Checks app-private dir first,
     * then public Downloads as fallback.
     */
    fun findDownloadedApk(context: Context, fileName: String): File? {
        val appPrivate = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (appPrivate.exists() && appPrivate.length() > 0) return appPrivate

        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        if (publicDir.exists() && publicDir.length() > 0) return publicDir

        return null
    }

    /**
     * Deletes old Aviator APKs to free space.
     */
    fun cleanOldApks(context: Context) {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.listFiles { f -> f.name.startsWith("AviatorPredictor") && f.name.endsWith(".apk") }
            ?.forEach { it.delete() }
    }
}
