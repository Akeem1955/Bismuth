package com.bismuth.app.engine

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "BismuthModelDownloadMgr"

sealed class DownloadState {
    data object Idle : DownloadState()
    data object Checking : DownloadState()
    data class Downloading(
        val progressPercent: Int,
        val receivedBytes: Long,
        val totalBytes: Long,
    ) : DownloadState()
    data object Ready : DownloadState()
    data class Failed(val message: String) : DownloadState()
}

class ModelDownloadManager(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    suspend fun ensureModelReady(modelConfig: BismuthModelConfig) {
        _downloadState.value = DownloadState.Checking

        if (modelConfig.isDownloaded(context)) {
            _downloadState.value = DownloadState.Ready
            return
        }

        if (tryImportFromDownloads(modelConfig)) {
            _downloadState.value = DownloadState.Ready
            return
        }

        startDownload(modelConfig)
    }

    private suspend fun tryImportFromDownloads(modelConfig: BismuthModelConfig): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val sourceFile = File(downloadsDir, modelConfig.modelFile)
                if (!sourceFile.exists()) return@withContext false

                val targetFile = File(modelConfig.getLocalPath(context))
                targetFile.parentFile?.mkdirs()

                _downloadState.value = DownloadState.Downloading(
                    progressPercent = 25,
                    receivedBytes = 0,
                    totalBytes = sourceFile.length(),
                )
                sourceFile.copyTo(targetFile, overwrite = true)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Could not import from Downloads: ${e.message}")
                false
            }
        }

    suspend fun importCustomModelUri(uri: Uri, modelConfig: BismuthModelConfig): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val targetFile = File(modelConfig.getLocalPath(context))
                targetFile.parentFile?.mkdirs()

                _downloadState.value = DownloadState.Downloading(50, 0, 0)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                _downloadState.value = DownloadState.Ready
                true
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Failed("Import failed: ${e.message}")
                false
            }
        }

    private suspend fun startDownload(modelConfig: BismuthModelConfig) {
        val inputDataBuilder = Data.Builder()
            .putString(DownloadConsts.KEY_MODEL_NAME, modelConfig.displayName)
            .putString(DownloadConsts.KEY_MODEL_URL, modelConfig.downloadUrl)
            .putString(DownloadConsts.KEY_MODEL_COMMIT_HASH, modelConfig.commitHash)
            .putString(DownloadConsts.KEY_MODEL_DOWNLOAD_MODEL_DIR, modelConfig.normalizedName)
            .putString(DownloadConsts.KEY_MODEL_DOWNLOAD_FILE_NAME, modelConfig.modelFile)
            .putLong(DownloadConsts.KEY_MODEL_TOTAL_BYTES, modelConfig.sizeInBytes)

        val downloadWorkRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(inputDataBuilder.build())
            .addTag("bismuth_model_download")
            .build()

        val workerId = downloadWorkRequest.id

        workManager.enqueueUniqueWork(
            "bismuth_model_${modelConfig.normalizedName}",
            ExistingWorkPolicy.REPLACE,
            downloadWorkRequest,
        )

        workManager.getWorkInfoByIdFlow(workerId).collect { workInfo ->
            if (workInfo != null) {
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED -> {
                        _downloadState.value = DownloadState.Downloading(0, 0, modelConfig.sizeInBytes)
                    }
                    WorkInfo.State.RUNNING -> {
                        val receivedBytes = workInfo.progress.getLong(
                            DownloadConsts.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, 0L
                        )
                        val progress = if (modelConfig.sizeInBytes > 0) {
                            (receivedBytes * 100 / modelConfig.sizeInBytes).toInt()
                        } else 0
                        _downloadState.value = DownloadState.Downloading(
                            progressPercent = progress,
                            receivedBytes = receivedBytes,
                            totalBytes = modelConfig.sizeInBytes,
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _downloadState.value = DownloadState.Ready
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        val errorMessage = workInfo.outputData.getString(
                            DownloadConsts.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE
                        ) ?: "Download failed"
                        _downloadState.value = DownloadState.Failed(errorMessage)
                    }
                    else -> {}
                }
            }
        }
    }

    fun cancelDownload() {
        workManager.cancelAllWorkByTag("bismuth_model_download")
        _downloadState.value = DownloadState.Idle
    }
}
