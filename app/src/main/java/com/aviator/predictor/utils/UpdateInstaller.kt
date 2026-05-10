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
    val percent: Int           = 0,
    val downloadedBytes: Long  = 0L,
    val totalBytes: Long       = 0L,
    val isComplete: Boolean    = false,
    val isFailed: Boolean      = false,
    val localFile: File?       = null
)

object UpdateInstaller {

    fun startDownload(context: Context, downloadUrl: String, fileName: String): Long {
        return try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val destDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            destDir?.mkdirs()

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Aviator Predictor Update")
                setDescription("Downloading $fileName…")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
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
                        onProgress(DownloadProgress(
                            percent       = 100,
                            downloadedBytes = total,
                            totalBytes    = total,
                            isComplete    = true,
                            localFile     = file
                        ))
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
                    onProgress(DownloadProgress(
                        percent         = 0,
                        downloadedBytes = 0L,
                        totalBytes      = total.coerceAtLeast(0L)
                    ))
                }
                DownloadManager.STATUS_RUNNING -> {
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    onProgress(DownloadProgress(
                        percent         = percent,
                        downloadedBytes = downloaded,
                        totalBytes      = total.coerceAtLeast(0L)
                    ))
                }
            }

            delay(600)
        }
    }

    private fun resolveFile(context: Context, localUri: String?, fileName: String): File? {
        if (!localUri.isNullOrBlank()) {
            try {
                val path = Uri.parse(localUri).path
                if (!path.isNullOrBlank()) {
                    val f = File(path)
                    if (f.exists() && f.length() > 0) return f
                }
            } catch (_: Exception) {}
        }

        val appPrivate = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (appPrivate.exists() && appPrivate.length() > 0) return appPrivate

        val publicDownloads = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName
        )
        if (publicDownloads.exists() && publicDownloads.length() > 0) return publicDownloads

        val internalFilesDownloads = File(File(context.filesDir, "Downloads"), fileName)
        if (internalFilesDownloads.exists() && internalFilesDownloads.length() > 0) return internalFilesDownloads

        val internalFiles = File(context.filesDir, fileName)
        if (internalFiles.exists() && internalFiles.length() > 0) return internalFiles

        val cacheDownloads = File(File(context.cacheDir, "Downloads"), fileName)
        if (cacheDownloads.exists() && cacheDownloads.length() > 0) return cacheDownloads

        val cacheFile = File(context.cacheDir, fileName)
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile

        try {
            context.getExternalFilesDirs(null).filterNotNull().forEach { dir ->
                val found = findRecursive(dir, fileName)
                if (found != null) return found
            }
        } catch (_: Exception) {}

        return null
    }

    private fun findRecursive(root: File, fileName: String, depth: Int = 0): File? {
        if (depth > 3) return null
        root.listFiles()?.forEach { f ->
            if (f.isFile && f.name == fileName && f.length() > 0) return f
            if (f.isDirectory) {
                val found = findRecursive(f, fileName, depth + 1)
                if (found != null) return found
            }
        }
        return null
    }

    fun cancelDownload(context: Context, downloadId: Long) {
        runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(downloadId)
        }
    }

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            context.packageManager.canRequestPackageInstalls()
        else true

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

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        } catch (e: IllegalArgumentException) {
            val fallbackFile = copyToInternalFiles(context, apkFile)
            if (fallbackFile != null) {
                try {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fallbackFile)
                } catch (_: Exception) {
                    @Suppress("DEPRECATION") Uri.fromFile(fallbackFile)
                }
            } else {
                @Suppress("DEPRECATION") Uri.fromFile(apkFile)
            }
        } catch (e: Exception) {
            @Suppress("DEPRECATION") Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }

        try {
            val resolvedActivities = context.packageManager.queryIntentActivities(intent, 0)
            for (info in resolvedActivities) {
                context.grantUriPermission(
                    info.activityInfo.packageName, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } catch (_: Exception) {}

        context.startActivity(intent)
    }

    private fun copyToInternalFiles(context: Context, src: File): File? {
        return try {
            val destDir = File(context.filesDir, "Downloads").also { it.mkdirs() }
            val dest    = File(destDir, src.name)
            src.copyTo(dest, overwrite = true)
            dest
        } catch (_: Exception) {
            null
        }
    }

    fun findDownloadedApk(context: Context, fileName: String): File? {
        val appPrivate = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (appPrivate.exists() && appPrivate.length() > 0) return appPrivate

        val internalDownloads = File(File(context.filesDir, "Downloads"), fileName)
        if (internalDownloads.exists() && internalDownloads.length() > 0) return internalDownloads

        val internalRoot = File(context.filesDir, fileName)
        if (internalRoot.exists() && internalRoot.length() > 0) return internalRoot

        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName
        )
        if (publicDir.exists() && publicDir.length() > 0) return publicDir

        return null
    }

    fun cleanOldApks(context: Context) {
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.listFiles { f -> f.name.startsWith("AviatorPredictor") && f.name.endsWith(".apk") }
            ?.forEach { it.delete() }

        File(context.filesDir, "Downloads")
            .listFiles { f -> f.name.startsWith("AviatorPredictor") && f.name.endsWith(".apk") }
            ?.forEach { it.delete() }
    }
}
