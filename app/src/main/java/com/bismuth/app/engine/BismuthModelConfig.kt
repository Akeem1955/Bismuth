package com.bismuth.app.engine

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import java.io.File
import kotlin.math.ceil

enum class ModelFamily(val displayName: String, val badgeColorHex: Long) {
    GEMMA("Gemma", 0xFF3A5282L),
    QWEN("Qwen", 0xFF6D28D9L),
    DEEPSEEK("DeepSeek", 0xFF0F766EL),
    PHI("Phi", 0xFFB45309L),
}

enum class ModelTier(val label: String, val badgeText: String) {
    HIGH_END("Flagship Tier (8GB+ RAM)", "Deep Reasoning / Multimodal"),
    MID_HIGH("Balanced Tier (6GB+ RAM)", "Multimodal + Standard Quiz"),
    LOW_END("Efficiency Tier (4GB+ RAM)", "Fast On-Device Tutoring"),
    ULTRA_LOW("Ultra-Compact Tier (2GB+ RAM)", "Text-Only + Fast Core Quiz");

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
    val family: ModelFamily = ModelFamily.GEMMA,
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

        val QWEN_2_5_1_5B = BismuthModelConfig(
            modelId = "litert-community/Qwen2.5-1.5B-Instruct",
            modelFile = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            commitHash = "main",
            sizeInBytes = 1_597_922_856L,
            displayName = "Qwen 2.5 (1.5B Instruct)",
            subtitle = "Alibaba Qwen family • Fast structured math, code & step-by-step tutoring",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            minRamGb = 4,
            tier = ModelTier.LOW_END,
            supportsAudio = false,
            supportsImage = false,
            family = ModelFamily.QWEN,
        )

        val DEEPSEEK_R1_QWEN_1_5B = BismuthModelConfig(
            modelId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            modelFile = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
            commitHash = "main",
            sizeInBytes = 1_625_493_432L,
            displayName = "DeepSeek-R1 Distill Qwen (1.5B)",
            subtitle = "Qwen architecture • Chain-of-thought reasoning distilled for physics & proofs",
            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            minRamGb = 4,
            tier = ModelTier.LOW_END,
            supportsAudio = false,
            supportsImage = false,
            family = ModelFamily.DEEPSEEK,
        )

        val PHI_4_MINI = BismuthModelConfig(
            modelId = "litert-community/Phi-4-mini-instruct",
            modelFile = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            commitHash = "main",
            sizeInBytes = 3_910_050_199L,
            displayName = "Phi-4 Mini Instruct",
            subtitle = "Microsoft Phi family • High-accuracy logical & mathematical reasoning",
            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            minRamGb = 8,
            tier = ModelTier.HIGH_END,
            supportsAudio = false,
            supportsImage = false,
            family = ModelFamily.PHI,
        )

        val ALL = listOf(
            GEMMA_4_E4B,
            GEMMA_4_E2B,
            QWEN_2_5_1_5B,
            DEEPSEEK_R1_QWEN_1_5B,
            PHI_4_MINI,
            GEMMA_3N_E2B,
            GEMMA_3N_LITE,
        )
    }
}

object HardwareCapabilityEvaluator {

    fun inspectDevice(context: Context): DeviceHardwareProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalBytes = memInfo.totalMem
        val totalGiB = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val totalRamGb = when {
            totalGiB >= 20.0 -> 24
            totalGiB >= 14.0 -> 16
            totalGiB >= 10.0 -> 12
            totalGiB >= 6.4 -> 8
            totalGiB >= 4.6 -> 6
            totalGiB >= 3.1 -> 4
            totalGiB >= 2.2 -> 3
            else -> 2
        }
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

    fun customConfigForFilename(filename: String): BismuthModelConfig {
        val lower = filename.lowercase()
        val isGemma4 = lower.contains("gemma-4") || lower.contains("e4b")
        val hasAudio = isGemma4 || lower.contains("3n-e2b") || (lower.contains("3n") && !lower.contains("lite"))
        val hasImage = isGemma4 || lower.contains("e2b-it")
        val family = when {
            "deepseek" in lower -> ModelFamily.DEEPSEEK
            "qwen" in lower -> ModelFamily.QWEN
            "phi" in lower -> ModelFamily.PHI
            else -> ModelFamily.GEMMA
        }
        return BismuthModelConfig(
            modelId = "custom:$filename",
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
            family = family,
        )
    }

    fun getSelectedConfig(context: Context): BismuthModelConfig {
        val id = getSelectedModelId(context)
        if (id.startsWith("custom:")) {
            return customConfigForFilename(id.substringAfter("custom:"))
        }
        return BismuthModelConfig.ALL.firstOrNull { it.modelId == id }
            ?: BismuthModelConfig.ALL.first {
                it.modelId == HardwareCapabilityEvaluator.inspectDevice(context).recommendedModelId
            }
    }
}
