package com.bismuth.app.engine

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import java.io.File
import kotlin.math.ceil

enum class ModelTier(val label: String, val badgeText: String) {
    HIGH_END("Gemma 4 Multimodal (High-End)", "Vision + Audio + Deep Quiz"),
    MID_HIGH("Gemma 4 Compact (Mid-High)", "Vision + Audio + Standard Quiz"),
    LOW_END("Gemma 3n Audio+Text (Low-End)", "Audio + Text + Core Quiz"),
    ULTRA_LOW("Gemma 3n Lite (Basic Phone)", "Text-Only + Fast Core Quiz");

    val isHighCapability: Boolean
        get() = this == HIGH_END || this == MID_HIGH
}

data class DeviceHardwareProfile(
    val totalRamBytes: Long,
    val totalRamGb: Int,
    val availableRamMb: Long,
    val cpuCores: Int,
    val recommendedModelId: String,
)

data class BismuthModelConfig(
    val modelId: String,
    val modelFile: String,
    val commitHash: String,
    val sizeInBytes: Long,
    val displayName: String,
    val subtitle: String,
    val downloadUrl: String,
    val minRamGb: Int,
    val tier: ModelTier,
    val supportsAudio: Boolean,
    val supportsImage: Boolean,
) {
    val normalizedName: String = Regex("[^a-zA-Z0-9]").replace(modelId, "_")

    fun getLocalPath(context: Context): String {
        val baseDir = context.getExternalFilesDir(null)?.absolutePath ?: ""
        return listOf(baseDir, normalizedName, commitHash, modelFile)
            .joinToString(File.separator)
    }

    fun isDownloaded(context: Context): Boolean {
        return File(getLocalPath(context)).exists()
    }

    companion object {
        val GEMMA_4_E4B = BismuthModelConfig(
            modelId = "litert-community/gemma-4-E4B-it-litert-lm",
            modelFile = "gemma-4-E4B-it.litertlm",
            commitHash = "9695417f248178c63a9f318c6e0c56cb917cb837",
            sizeInBytes = 3_654_467_584L,
            displayName = "Gemma 4 (E4B Multimodal)",
            subtitle = "Full Vision + Audio Scribe + Multi-Step Reasoning (8GB+ RAM)",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/9695417f248178c63a9f318c6e0c56cb917cb837/gemma-4-E4B-it.litertlm?download=true",
            minRamGb = 8,
            tier = ModelTier.HIGH_END,
            supportsAudio = true,
            supportsImage = true,
        )

        val GEMMA_4_E2B = BismuthModelConfig(
            modelId = "litert-community/gemma-4-E2B-it-litert-lm",
            modelFile = "gemma-4-E2B-it.litertlm",
            commitHash = "7fa1d78473894f7e736a21d920c3aa80f950c0db",
            sizeInBytes = 2_583_085_056L,
            displayName = "Gemma 4 (E2B Multimodal)",
            subtitle = "Balanced Vision + Audio Scribe + Interactive Teaching (6GB+ RAM)",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/7fa1d78473894f7e736a21d920c3aa80f950c0db/gemma-4-E2B-it.litertlm?download=true",
            minRamGb = 6,
            tier = ModelTier.MID_HIGH,
            supportsAudio = true,
            supportsImage = true,
        )

        val GEMMA_3N_E2B = BismuthModelConfig(
            modelId = "litert-community/gemma-3n-E2B-it-int4",
            modelFile = "gemma-3n-E2B-it-int4.litertlm",
            commitHash = "main",
            sizeInBytes = 1_610_612_736L,
            displayName = "Gemma 3n (E2B Audio + Text)",
            subtitle = "Optimized for Low-Mid Phones: Audio Lecture Scribe + Teaching + Docs (4GB+ RAM)",
            downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm?download=true",
            minRamGb = 4,
            tier = ModelTier.LOW_END,
            supportsAudio = true,
            supportsImage = false,
        )

        val GEMMA_3N_LITE = BismuthModelConfig(
            modelId = "litert-community/gemma-3n-lite-int4",
            modelFile = "gemma-3n-lite-int4.litertlm",
            commitHash = "main",
            sizeInBytes = 943_718_400L,
            displayName = "Gemma 3n Lite (Ultra-Low Spec)",
            subtitle = "Base Default: Document Quiz + Interactive Teaching Whiteboard (2GB+ RAM)",
            downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-lite-int4.litertlm?download=true",
            minRamGb = 2,
            tier = ModelTier.ULTRA_LOW,
            supportsAudio = false,
            supportsImage = false,
        )

        val ALL = listOf(GEMMA_4_E4B, GEMMA_4_E2B, GEMMA_3N_E2B, GEMMA_3N_LITE)
    }
}

object HardwareCapabilityEvaluator {

    fun inspectDevice(context: Context): DeviceHardwareProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalBytes = memInfo.totalMem
        val totalRamGb = ceil(totalBytes / (1024.0 * 1024.0 * 1024.0)).toInt().coerceAtLeast(2)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val cores = Runtime.getRuntime().availableProcessors()

        val recommended = when {
            totalRamGb >= 8 -> BismuthModelConfig.GEMMA_4_E4B.modelId
            totalRamGb >= 6 -> BismuthModelConfig.GEMMA_4_E2B.modelId
            totalRamGb >= 4 -> BismuthModelConfig.GEMMA_3N_E2B.modelId
            else -> BismuthModelConfig.GEMMA_3N_LITE.modelId
        }

        return DeviceHardwareProfile(
            totalRamBytes = totalBytes,
            totalRamGb = totalRamGb,
            availableRamMb = availMb,
            cpuCores = cores,
            recommendedModelId = recommended,
        )
    }

    /**
     * Returns true if the phone hardware can run this model without OOM.
     * Models exceeding the phone's RAM tier are disabled in the selector UI.
     */
    fun isModelSupportedOnDevice(config: BismuthModelConfig, hardware: DeviceHardwareProfile): Boolean {
        return hardware.totalRamGb >= config.minRamGb
    }
}

object ModelPrefs {
    private const val PREFS_NAME = "bismuth_model_prefs"
    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"

    fun setSelectedModelId(context: Context, modelId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_MODEL_ID, modelId)
            .apply()
    }

    fun getSelectedModelId(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_MODEL_ID, null)
        if (saved != null) return saved

        val hw = HardwareCapabilityEvaluator.inspectDevice(context)
        return hw.recommendedModelId
    }

    fun getSelectedConfig(context: Context): BismuthModelConfig {
        val id = getSelectedModelId(context)
        if (id.startsWith("custom:")) {
            val filename = id.substringAfter("custom:")
            val lower = filename.lowercase()
            val isGemma4 = lower.contains("gemma-4") || lower.contains("e4b")
            val hasAudio = isGemma4 || lower.contains("3n") || lower.contains("e2b")
            val hasImage = isGemma4 || lower.contains("e2b")
            return BismuthModelConfig(
                modelId = id,
                modelFile = filename,
                commitHash = "local",
                sizeInBytes = 0L,
                displayName = "Custom: $filename",
                subtitle = "Imported local .litertlm model",
                downloadUrl = "",
                minRamGb = 2,
                tier = if (hasImage) ModelTier.HIGH_END else if (hasAudio) ModelTier.LOW_END else ModelTier.ULTRA_LOW,
                supportsAudio = hasAudio,
                supportsImage = hasImage,
            )
        }
        return BismuthModelConfig.ALL.firstOrNull { it.modelId == id }
            ?: BismuthModelConfig.ALL.first {
                it.modelId == HardwareCapabilityEvaluator.inspectDevice(context).recommendedModelId
            }
    }
}
