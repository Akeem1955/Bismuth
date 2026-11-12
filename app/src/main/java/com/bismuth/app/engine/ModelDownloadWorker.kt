package com.bismuth.app.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "BismuthDownloadWorker"
private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "bismuth_download_channel"
private var channelCreated = false

class ModelDownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val notificationId: Int = params.id.hashCode()

    init {
        if (!channelCreated) {
            val channel = NotificationChannel(
                FOREGROUND_NOTIFICATION_CHANNEL_ID,
                "Bismuth Model Download",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Bismuth AI model download progress" }
            notificationManager.createNotificationChannel(channel)
            channelCreated = true
        }
    }

    override suspend fun doWork(): Result {
        val fileUrl = inputData.getString(DownloadConsts.KEY_MODEL_URL)
        val modelName = inputData.getString(DownloadConsts.KEY_MODEL_NAME) ?: "Model"
        val version = inputData.getString(DownloadConsts.KEY_MODEL_COMMIT_HASH) ?: "main"
        val fileName = inputData.getString(DownloadConsts.KEY_MODEL_DOWNLOAD_FILE_NAME)
        val modelDir = inputData.getString(DownloadConsts.KEY_MODEL_DOWNLOAD_MODEL_DIR) ?: ""
        val totalBytes = inputData.getLong(DownloadConsts.KEY_MODEL_TOTAL_BYTES, 0L)

        return withContext(Dispatchers.IO) {
            if (fileUrl == null || fileName == null) {
                Result.failure()
            } else {
                try {
                    trySetForeground(progress = 0, modelName = modelName)

                    val outputDir = File(
                        applicationContext.getExternalFilesDir(null),
                        listOf(modelDir, version).joinToString(separator = File.separator),
                    )
                    if (!outputDir.exists()) outputDir.mkdirs()

                    val outputTmpFile = File(
                        applicationContext.getExternalFilesDir(null),
                        listOf(modelDir, version, "$fileName.${DownloadConsts.TMP_FILE_EXT}")
                            .joinToString(separator = File.separator),
                    )

                    val existingBytes = outputTmpFile.length()
                    val headers = if (existingBytes > 0) {
                        mapOf(
                            "Range" to "bytes=$existingBytes-",
                            "Accept-Encoding" to "identity"
                        )
                    } else {
                        emptyMap()
                    }
                    val activeConnection = openFollowingRedirects(fileUrl = fileUrl, headers = headers)

                    val responseCode = activeConnection.responseCode
                    var downloadedBytes = 0L
                    val isPartialResume = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                        val contentRange = activeConnection.getHeaderField("Content-Range")
                        if (contentRange != null) {
                            val startByte = contentRange.substringAfter("bytes ")
                                .split("/")[0].split("-")[0].toLongOrNull() ?: 0L
                            downloadedBytes += startByte
                            true
                        } else {
                            false
                        }
                    } else if (responseCode == HttpURLConnection.HTTP_OK) {
                        false
                    } else if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                        activeConnection.disconnect()
                        throw IOException("Access denied (HTTP $responseCode). Gated models can be imported locally via 'Import Local .litertlm'.")
                    } else {
                        activeConnection.disconnect()
                        throw IOException("HTTP error code: $responseCode")
                    }

                    val headerTotal = activeConnection.contentLengthLong.let { if (it > 0) it + downloadedBytes else 0L }
                    val effectiveTotal = if (totalBytes > 0) totalBytes else headerTotal

                    try {
                        activeConnection.inputStream.use { inputStream ->
                            FileOutputStream(outputTmpFile, isPartialResume).use { outputStream ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var bytesRead: Int
                                var lastProgressTs = 0L

                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead

                                    val curTs = System.currentTimeMillis()
                                    if (curTs - lastProgressTs > 200) {
                                        setProgress(
                                            Data.Builder()
                                                .putLong(DownloadConsts.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, downloadedBytes)
                                                .putLong(DownloadConsts.KEY_MODEL_TOTAL_BYTES, effectiveTotal)
                                                .build()
                                        )
                                        val progress = if (effectiveTotal > 0) {
                                            ((downloadedBytes * 100L) / effectiveTotal).toInt().coerceIn(0, 100)
                                        } else 0
                                        trySetForeground(progress = progress, modelName = modelName)
                                        lastProgressTs = curTs
                                    }
                                }
                                outputStream.flush()
                            }
                        }
                    } finally {
                        activeConnection.disconnect()
                    }

                    val originalFile = File(
                        outputTmpFile.absolutePath.replace(".${DownloadConsts.TMP_FILE_EXT}", "")
                    )
                    if (originalFile.exists()) originalFile.delete()
                    val renamed = outputTmpFile.renameTo(originalFile)
                    if (!renamed) {
                        outputTmpFile.delete()
                        throw IOException("Failed to finalize model download")
                    }
                    Result.success()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Download failed: ${e.message}", e)
                    Result.failure(
                        Data.Builder()
                            .putString(
                                DownloadConsts.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE,
                                e.message ?: "Download failed"
                            )
                            .build()
                    )
                }
            }
        }
    }

    private suspend fun trySetForeground(progress: Int, modelName: String?) {
        try {
            setForeground(createForegroundInfo(progress = progress, modelName = modelName))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Avoid crashing when Android 12+ background restrictions reject FGS promotion.
            Log.w(TAG, "Unable to promote download worker to foreground: ${e.message}")
        }
    }

    private fun openFollowingRedirects(
        fileUrl: String,
        headers: Map<String, String> = emptyMap(),
        maxRedirects: Int = 6
    ): HttpURLConnection {
        var currentUrl = fileUrl
        repeat(maxRedirects + 1) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 30_000
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            connection.connect()
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect without Location header (HTTP $code)")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
            } else {
                return connection
            }
        }
        throw IOException("Too many redirects while downloading model")
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0)
    }

    private fun createForegroundInfo(progress: Int, modelName: String? = null): ForegroundInfo {
        val title = if (modelName != null) "Downloading \"$modelName\"" else "Downloading model"
        val content = "Download in progress: $progress%"

        val notification = NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }
}
