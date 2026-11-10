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
                    setForeground(createForegroundInfo(progress = 0, modelName = modelName))

                    val url = URL(fileUrl)
                    val connection = url.openConnection() as HttpURLConnection

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

                    var downloadedBytes = 0L
                    val existingBytes = outputTmpFile.length()
                    if (existingBytes > 0) {
                        connection.setRequestProperty("Range", "bytes=$existingBytes-")
                        connection.setRequestProperty("Accept-Encoding", "identity")
                    }

                    connection.connect()

                    if (connection.responseCode == HttpURLConnection.HTTP_OK ||
                        connection.responseCode == HttpURLConnection.HTTP_PARTIAL
                    ) {
                        val contentRange = connection.getHeaderField("Content-Range")
                        if (contentRange != null) {
                            val startByte = contentRange.substringAfter("bytes ")
                                .split("/")[0].split("-")[0].toLong()
                            downloadedBytes += startByte
                        }
                    } else {
                        throw IOException("HTTP error code: ${connection.responseCode}")
                    }

                    val inputStream = connection.inputStream
                    val outputStream = FileOutputStream(outputTmpFile, true)

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
                                    .build()
                            )
                            val progress = if (totalBytes > 0) {
                                (downloadedBytes * 100 / totalBytes).toInt()
                            } else 0
                            setForeground(createForegroundInfo(progress = progress, modelName = modelName))
                            lastProgressTs = curTs
                        }
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

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
                } catch (e: IOException) {
                    Log.e(TAG, "Download failed: ${e.message}", e)
                    Result.failure(
                        Data.Builder()
                            .putString(DownloadConsts.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE, e.message)
                            .build()
                    )
                }
            }
        }
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
