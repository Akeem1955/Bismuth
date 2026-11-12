package com.bismuth.app.engine

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

private const val TAG = "BismuthEngine"
private const val AGENT_MAX_TOKENS = 8_192
private const val AGENT_TOP_K = 64
private const val AGENT_TOP_P = 0.95
private const val AGENT_TEMPERATURE = 0.7

sealed class AgentResponse {
    data class Token(val text: String) : AgentResponse()
    data object Done : AgentResponse()
    data class ToolCallRequest(val message: Message) : AgentResponse()
    data class Error(val message: String) : AgentResponse()
}

/**
 * LiteRT-LM inference engine wrapper for Bismuth.
 * Supports Gemma 4 (E4B/E2B multimodal vision + audio) and Gemma 3n (audio + text / lite text).
 */
object BismuthEngine {

    init {
        @OptIn(ExperimentalApi::class)
        ExperimentalFlags.enableConversationConstrainedDecoding = false
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeModelPath: String? = null
    @Volatile
    var isInitialized: Boolean = false
        private set

    suspend fun initializeIfNeeded(
        context: Context,
        config: BismuthModelConfig = ModelPrefs.getSelectedConfig(context),
        systemPrompt: String = "You are Bismuth, an offline AI tutor and academic study companion.",
    ): Boolean = withContext(Dispatchers.IO) {
        if (!config.isDownloaded(context)) {
            Log.d(TAG, "Model file not yet downloaded at ${config.getLocalPath(context)}")
            return@withContext false
        }
        val path = config.getLocalPath(context)
        if (isInitialized && activeModelPath == path && conversation != null) {
            return@withContext true
        }
        return@withContext initialize(
            context = context,
            modelPath = path,
            systemPrompt = systemPrompt,
            supportsAudio = config.supportsAudio,
            supportsImage = config.supportsImage,
        )
    }

    suspend fun initialize(
        context: Context,
        modelPath: String,
        systemPrompt: String,
        tools: List<ToolProvider> = emptyList(),
        supportsAudio: Boolean = false,
        supportsImage: Boolean = false,
        maxNumTokens: Int = AGENT_MAX_TOKENS,
        constrainedDecoding: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            close()
            @OptIn(ExperimentalApi::class)
            run { ExperimentalFlags.enableConversationConstrainedDecoding = constrainedDecoding }

            var candidateGpuEngine: Engine? = null
            val (newEngine, activeBackend) = try {
                val gpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.GPU(),
                    visionBackend = if (supportsImage) Backend.GPU() else null,
                    audioBackend = if (supportsAudio) Backend.CPU() else null,
                    maxNumTokens = maxNumTokens,
                    cacheDir = context.cacheDir.path,
                )
                Engine(gpuConfig).also {
                    candidateGpuEngine = it
                    it.initialize()
                } to "GPU"
            } catch (gpuError: Exception) {
                runCatching { candidateGpuEngine?.close() }
                Log.w(TAG, "GPU init failed for $modelPath (${gpuError.message}); falling back to CPU backend.")
                val cpuConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),
                    visionBackend = if (supportsImage) Backend.CPU() else null,
                    audioBackend = if (supportsAudio) Backend.CPU() else null,
                    maxNumTokens = maxNumTokens,
                    cacheDir = context.cacheDir.path,
                )
                Engine(cpuConfig).also { it.initialize() } to "CPU"
            }

            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = AGENT_TOP_K,
                        topP = AGENT_TOP_P,
                        temperature = AGENT_TEMPERATURE,
                    ),
                    systemInstruction = PromptFormatting.buildSystemInstruction(systemPrompt),
                    tools = tools,
                    automaticToolCalling = false,
                )
            )

            engine = newEngine
            conversation = newConversation
            activeModelPath = modelPath
            isInitialized = true
            Log.d(TAG, "BismuthEngine initialized ($activeBackend) with $modelPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Engine initialization failed: ${e.message}", e)
            isInitialized = false
            false
        }
    }

    fun resetConversation(
        systemPrompt: String,
        tools: List<ToolProvider> = emptyList(),
        constrainedDecoding: Boolean = false,
    ) {
        val currentEngine = engine ?: return
        try {
            conversation?.close()
        } catch (_: Exception) {}

        @OptIn(ExperimentalApi::class)
        run { ExperimentalFlags.enableConversationConstrainedDecoding = constrainedDecoding }

        conversation = currentEngine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(
                    topK = AGENT_TOP_K,
                    topP = AGENT_TOP_P,
                    temperature = AGENT_TEMPERATURE,
                ),
                systemInstruction = PromptFormatting.buildSystemInstruction(systemPrompt),
                tools = tools,
                automaticToolCalling = false,
            )
        )
    }

    fun sendAgentMessage(
        text: String,
        imagePngBytes: List<ByteArray> = emptyList(),
        audioBytes: List<ByteArray> = emptyList(),
    ): Flow<AgentResponse> {
        return sendMessageAsync(PromptFormatting.buildUserContents(text, imagePngBytes, audioBytes))
    }

    fun sendMessageAsync(contents: Contents): Flow<AgentResponse> = callbackFlow {
        val conv = conversation
        if (conv == null || !isInitialized) {
            trySend(AgentResponse.Error("Engine not initialized"))
            close()
            return@callbackFlow
        }

        conv.sendMessageAsync(
            contents,
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    val text = message.toString()
                    if (message.toolCalls.isNotEmpty()) {
                        trySend(AgentResponse.ToolCallRequest(message))
                    } else {
                        trySend(AgentResponse.Token(text))
                    }
                }

                override fun onDone() {
                    trySend(AgentResponse.Done)
                    close()
                }

                override fun onError(throwable: Throwable) {
                    val msg = if (throwable is CancellationException) {
                        "Cancelled"
                    } else {
                        throwable.message ?: "Inference error"
                    }
                    trySend(AgentResponse.Error(msg))
                    close()
                }
            }
        )

        awaitClose {}
    }

    /**
     * Convenience suspend helper that runs a prompt through LiteRT-LM if the model is downloaded
     * and initialized, or returns null so the caller can use its deterministic offline fallback.
     */
    suspend fun generateTextOrNull(
        context: Context,
        prompt: String,
        config: BismuthModelConfig = ModelPrefs.getSelectedConfig(context),
        systemPrompt: String = "You are Bismuth, a concise and accurate academic AI tutor.",
        imagePngBytes: List<ByteArray> = emptyList(),
        audioBytes: List<ByteArray> = emptyList(),
    ): String? = withContext(Dispatchers.IO) {
        if (!initializeIfNeeded(context, config = config, systemPrompt = systemPrompt)) {
            return@withContext null
        }
        resetConversation(systemPrompt = systemPrompt)
        val sb = StringBuilder()
        var hadError = false
        sendAgentMessage(prompt, imagePngBytes, audioBytes).collect { resp ->
            when (resp) {
                is AgentResponse.Token -> sb.append(resp.text)
                is AgentResponse.Error -> hadError = true
                else -> {}
            }
        }
        val out = sb.toString().trim()
        if (hadError || out.isBlank()) null else out
    }

    fun close() {
        try { conversation?.close() } catch (_: Exception) {}
        try { engine?.close() } catch (_: Exception) {}
        conversation = null
        engine = null
        activeModelPath = null
        isInitialized = false
    }
}
