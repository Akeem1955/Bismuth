package com.bismuth.app.scribe

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bismuth.app.ui.animation.AIThinkingOrb
import com.bismuth.app.ui.animation.AudioPulseVisualizer
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bismuth.app.engine.BismuthEngine
import com.bismuth.app.engine.BismuthModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

data class LectureChunkSlot(
    val chunkIndex: Int,
    val label: String,
    val durationSec: Int,
    val status: String,
    val transcript: String,
    val wavBytes: ByteArray? = null
)

data class AcademicScribeStudyTemplate(
    val keyTakeawaysAndSummary: List<String>,
    val inDepthDetailsAndConcepts: List<Pair<String, String>>,
    val essentialStudyNotes: List<String>
)

data class LectureFollowUpExchange(
    val question: String,
    val answer: String
)

object PcmWavEncoder {
    const val SAMPLE_RATE = 16000
    const val MAX_LECTURE_SECONDS = 30 * 60
    const val CHUNK_SECONDS = 10 * 60

    fun wrapPcmInWav(pcmData: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(chunkSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        val out = ByteArrayOutputStream(44 + dataSize)
        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }
}

@Composable
fun ScribeFeatureScreen(
    selectedModel: BismuthModelConfig,
    onRequestSwitchToAudioModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
    }

    var isRecording by remember { mutableStateOf(false) }
    var elapsedLectureSeconds by remember { mutableIntStateOf(0) }
    var activeChunkNumber by remember { mutableIntStateOf(1) }
    var isProcessingScribe by remember { mutableStateOf(false) }
    var scribeErrorMessage by remember { mutableStateOf<String?>(null) }
    var peakMicAmplitude by remember { mutableFloatStateOf(0f) }
    val profileLanguage = remember {
        com.bismuth.app.profile.StudentProfilePrefs.getProfile(context).preferredLanguage
    }
    var selectedLanguage by remember { mutableStateOf(profileLanguage) }

    val chunkSlots = remember {
        mutableStateListOf(
            LectureChunkSlot(1, "Chunk 1 (00:00 – 10:00)", 0, "Ready", ""),
            LectureChunkSlot(2, "Chunk 2 (10:00 – 20:00)", 0, "Ready", ""),
            LectureChunkSlot(3, "Chunk 3 (20:00 – 30:00)", 0, "Ready", "")
        )
    }

    var fullTranscript by remember { mutableStateOf("") }
    var structuredScribeOutput by remember { mutableStateOf<AcademicScribeStudyTemplate?>(null) }

    var followUpQuestion by remember { mutableStateOf("") }
    var isAskingFollowUp by remember { mutableStateOf(false) }
    val followUpHistory = remember { mutableStateListOf<LectureFollowUpExchange>() }

    val pcmStream = remember { ByteArrayOutputStream() }
    var audioRecordInstance by remember { mutableStateOf<AudioRecord?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                audioRecordInstance?.stop()
                audioRecordInstance?.release()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun transcribeAndSummarizeChunks(latestWav: ByteArray?, currentChunkIdx: Int) {
        isProcessingScribe = true
        scribeErrorMessage = null
        val slotIdx = (currentChunkIdx - 1).coerceIn(0, 2)
        chunkSlots[slotIdx] = chunkSlots[slotIdx].copy(status = "Transcribing")

        if (!selectedModel.supportsAudio) {
            chunkSlots[slotIdx] = chunkSlots[slotIdx].copy(
                status = "Audio Unsupported",
                transcript = "",
                wavBytes = latestWav
            )
            scribeErrorMessage = "${selectedModel.displayName} does not support audio transcription. Please switch to an Audio-capable model (e.g. Gemma 3n or Gemma 4)."
            isProcessingScribe = false
            return
        }

        val transcribedText = if (latestWav != null && latestWav.size > 44) {
            val transcribePrompt = if (selectedLanguage == "English") {
                "Transcribe this academic lecture audio segment accurately verbatim:"
            } else {
                "Transcribe this academic lecture audio segment accurately in $selectedLanguage:"
            }
            BismuthEngine.generateTextOrNull(
                context = context,
                prompt = transcribePrompt,
                config = selectedModel,
                audioBytes = listOf(latestWav)
            )
        } else null

        val resolvedChunkTranscript = transcribedText?.trim().orEmpty()

        if (resolvedChunkTranscript.isNotBlank()) {
            chunkSlots[slotIdx] = chunkSlots[slotIdx].copy(
                status = "Completed",
                transcript = resolvedChunkTranscript,
                wavBytes = latestWav
            )
        } else {
            chunkSlots[slotIdx] = chunkSlots[slotIdx].copy(
                status = "No Speech Detected",
                transcript = "",
                wavBytes = latestWav
            )
        }

        fullTranscript = chunkSlots
            .filter { it.transcript.isNotBlank() }
            .joinToString("\n\n") { "[${it.label}]\n${it.transcript}" }

        if (fullTranscript.isNotBlank()) {
            val summaryPrompt = """
                You are an expert academic tutor. Analyze this verbatim lecture transcript and format it strictly into the 3-part Academic Scribe Study Template in $selectedLanguage:
                
                [1. KEY TAKEAWAYS & HIGH-LEVEL SUMMARY]
                - Bullet point summary of core principles covered.
                
                [2. IN-DEPTH DETAILS & CONCEPT BREAKDOWNS]
                Concept Name: Detailed explanation, equations, and mechanisms.
                
                [3. ESSENTIAL STUDY NOTES]
                - Critical exam reminders, pitfalls to avoid, and formula relationships.
                
                Lecture Transcript:
                $fullTranscript
            """.trimIndent()

            val llmStructured = BismuthEngine.generateTextOrNull(
                context = context,
                prompt = summaryPrompt,
                config = selectedModel
            )

            structuredScribeOutput = parseAcademicScribeTemplate(llmStructured, fullTranscript)
        } else {
            structuredScribeOutput = null
        }
        isProcessingScribe = false
    }

    @SuppressLint("MissingPermission")
    fun startRecordingLecture() {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        scribeErrorMessage = null
        pcmStream.reset()
        elapsedLectureSeconds = 0
        activeChunkNumber = 1
        peakMicAmplitude = 0f
        chunkSlots[0] = chunkSlots[0].copy(status = "Recording", durationSec = 0, transcript = "")
        chunkSlots[1] = chunkSlots[1].copy(status = "Ready", durationSec = 0, transcript = "")
        chunkSlots[2] = chunkSlots[2].copy(status = "Ready", durationSec = 0, transcript = "")
        fullTranscript = ""
        structuredScribeOutput = null
        followUpHistory.clear()
        isRecording = true

        scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                PcmWavEncoder.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val recorder = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    PcmWavEncoder.SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
            } catch (_: Exception) {
                null
            }
            audioRecordInstance = recorder
            val readBuf = ByteArray(2048)
            try {
                if (recorder?.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    while (isRecording) {
                        val read = recorder.read(readBuf, 0, readBuf.size)
                        if (read > 0) {
                            var maxSample = 0
                            for (i in 0 until read - 1 step 2) {
                                val sample = (readBuf[i + 1].toInt() shl 8) or (readBuf[i].toInt() and 0xFF)
                                val absVal = abs(sample)
                                if (absVal > maxSample) maxSample = absVal
                            }
                            peakMicAmplitude = (maxSample / 32768f).coerceIn(0f, 1f)

                            synchronized(pcmStream) {
                                if (pcmStream.size() < 16000 * 2 * 60) {
                                    pcmStream.write(readBuf, 0, read)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stopRecordingAndProcess() {
        isRecording = false
        peakMicAmplitude = 0f
        val rawBytes = synchronized(pcmStream) { pcmStream.toByteArray() }
        val wavBytes = if (rawBytes.isNotEmpty()) PcmWavEncoder.wrapPcmInWav(rawBytes) else null
        scope.launch {
            transcribeAndSummarizeChunks(wavBytes, activeChunkNumber)
        }
    }

    // Auto-rotate and immediately transcribe completed 10-minute chunks in the background while recording next
    LaunchedEffect(isRecording) {
        while (isRecording) {
            delay(1000)
            elapsedLectureSeconds++
            val currentChunkIdx = ((elapsedLectureSeconds - 1) / PcmWavEncoder.CHUNK_SECONDS) + 1
            val secInCurrentChunk = elapsedLectureSeconds % PcmWavEncoder.CHUNK_SECONDS

            if (currentChunkIdx <= 3) {
                val idx = currentChunkIdx - 1
                chunkSlots[idx] = chunkSlots[idx].copy(
                    durationSec = if (secInCurrentChunk == 0) PcmWavEncoder.CHUNK_SECONDS else secInCurrentChunk,
                    status = "Recording"
                )
            }

            if (elapsedLectureSeconds % PcmWavEncoder.CHUNK_SECONDS == 0 && elapsedLectureSeconds < PcmWavEncoder.MAX_LECTURE_SECONDS) {
                val finishedChunk = elapsedLectureSeconds / PcmWavEncoder.CHUNK_SECONDS
                val rawChunkPcm = synchronized(pcmStream) {
                    val b = pcmStream.toByteArray()
                    pcmStream.reset()
                    b
                }
                val chunkWav = if (rawChunkPcm.isNotEmpty()) PcmWavEncoder.wrapPcmInWav(rawChunkPcm) else null
                activeChunkNumber = (finishedChunk + 1).coerceAtMost(3)
                scope.launch {
                    transcribeAndSummarizeChunks(chunkWav, finishedChunk)
                }
            }

            if (elapsedLectureSeconds >= PcmWavEncoder.MAX_LECTURE_SECONDS) {
                stopRecordingAndProcess()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Lecture Transcription & Academic Scribe",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Record in-person lectures (up to 30 mins in three 10-minute auto-chunks), transcribe verbatim offline, and format into the 3-part Academic Scribe Study Template.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Language Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Language:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf("English", "Nigerian Pidgin", "French", "Spanish", "Yoruba", "Hausa", "Igbo").forEach { lang ->
                FilterChip(
                    selected = selectedLanguage == lang,
                    onClick = { selectedLanguage = lang },
                    label = { Text(lang, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        if (!selectedModel.supportsAudio) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedModel.displayName} is Text-Only",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Audio transcription requires a model with Audio Input capability. Switch to Gemma 3n or Gemma 4 in Models.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onRequestSwitchToAudioModel) {
                        Text("Switch", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Live Audio Recorder Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Live Lecture Audio (16 kHz Mono PCM)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Elapsed: ${formatMmSs(elapsedLectureSeconds)} / 30:00 Max (10-min chunks)",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = {
                            if (isRecording) {
                                stopRecordingAndProcess()
                            } else {
                                startRecordingLecture()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRecording) "Stop" else "Record",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Audio waveform visualizer when recording
                if (isRecording) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AudioPulseVisualizer(modifier = Modifier.size(28.dp))
                        val baseAmp = peakMicAmplitude.coerceIn(0.1f, 1f)
                        val infTransition = rememberInfiniteTransition(label = "audio_bars")
                        for (i in 0 until 16) {
                            val animMultiplier by infTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(200 + (i * 35), easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "bar_$i"
                            )
                            val barHeight = (baseAmp * animMultiplier * 24).coerceIn(4f, 26f).dp
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.error)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunkSlots.forEach { slot ->
                        val slotContainer = when (slot.status) {
                            "Recording" -> MaterialTheme.colorScheme.errorContainer
                            "Transcribing" -> MaterialTheme.colorScheme.tertiaryContainer
                            "Completed" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val slotContentColor = when (slot.status) {
                            "Recording" -> MaterialTheme.colorScheme.onErrorContainer
                            "Transcribing" -> MaterialTheme.colorScheme.onTertiaryContainer
                            "Completed" -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = slotContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Chunk ${slot.chunkIndex}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = slotContentColor
                                )
                                Text(
                                    text = "${ (slot.chunkIndex - 1) * 10 }–${ slot.chunkIndex * 10 }m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = slotContentColor.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = slot.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = slotContentColor
                                )
                            }
                        }
                    }
                }
            }
        }

        if (scribeErrorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = scribeErrorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (isProcessingScribe) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AIThinkingOrb(modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Synthesizing Academic Scribe Notes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Transcribing verbatim & structuring 3-part study template in $selectedLanguage with ${selectedModel.displayName}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (fullTranscript.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Verbatim Lecture Transcription",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Lecture Transcript", fullTranscript))
                                Toast.makeText(context, "Transcript copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Transcript",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = fullTranscript,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Structured Academic Scribe Study Template
        structuredScribeOutput?.let { scribe ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Academic Scribe Study Template",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                val text = buildString {
                                    appendLine("=== Academic Scribe Notes ===")
                                    appendLine("\n1. Key Takeaways & Summary:")
                                    scribe.keyTakeawaysAndSummary.forEach { appendLine("• $it") }
                                    appendLine("\n2. In-Depth Concepts:")
                                    scribe.inDepthDetailsAndConcepts.forEach { appendLine("${it.first}: ${it.second}") }
                                    appendLine("\n3. Essential Study Notes:")
                                    scribe.essentialStudyNotes.forEach { appendLine("• $it") }
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Study Notes", text))
                                Toast.makeText(context, "Study notes copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Study Notes",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 1. Key Takeaways & High-Level Summary
                    if (scribe.keyTakeawaysAndSummary.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "1. Key Takeaways & High-Level Summary",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                scribe.keyTakeawaysAndSummary.forEach { bullet ->
                                    Text(
                                        text = "• $bullet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. In-Depth Details & Concept Breakdowns
                    if (scribe.inDepthDetailsAndConcepts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "2. In-Depth Details & Concept Breakdowns",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                scribe.inDepthDetailsAndConcepts.forEach { (concept, detail) ->
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text(
                                            text = concept,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Essential Study Notes
                    if (scribe.essentialStudyNotes.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "3. Essential Study Notes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                scribe.essentialStudyNotes.forEach { note ->
                                    Text(
                                        text = "• $note",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Grounded Follow-Up Q&A
        if (fullTranscript.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Follow-Up Lecture Q&A",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ask questions strictly answered by the transcribed lecture audio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = followUpQuestion,
                            onValueChange = { followUpQuestion = it },
                            placeholder = {
                                Text("Ask a question about the recorded lecture...", style = MaterialTheme.typography.bodySmall)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val q = followUpQuestion.trim()
                                if (q.isEmpty() || isAskingFollowUp) return@IconButton
                                scope.launch {
                                    isAskingFollowUp = true
                                    val prompt = """
                                        You are an academic teaching assistant. Answer the student's question strictly and accurately using the transcribed lecture below in $selectedLanguage.
                                        If the information is not in the transcript, state that clearly.
                                        
                                        Transcribed Lecture:
                                        $fullTranscript
                                        
                                        Student Question: $q
                                    """.trimIndent()
                                    val llmAnswer = BismuthEngine.generateTextOrNull(
                                        context = context,
                                        prompt = prompt,
                                        config = selectedModel
                                    ) ?: extractTranscriptAnswerFallback(q, fullTranscript)

                                    followUpHistory.add(0, LectureFollowUpExchange(q, llmAnswer))
                                    followUpQuestion = ""
                                    isAskingFollowUp = false
                                }
                            },
                            enabled = !isAskingFollowUp && followUpQuestion.isNotBlank()
                        ) {
                            if (isAskingFollowUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Ask Lecture Follow-up",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    followUpHistory.forEach { item ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Q: ${item.question}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.answer,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d", m, s)
}

private fun parseAcademicScribeTemplate(
    llmOutput: String?,
    transcript: String
): AcademicScribeStudyTemplate {
    if (!llmOutput.isNullOrBlank()) {
        val takeaways = mutableListOf<String>()
        val concepts = mutableListOf<Pair<String, String>>()
        val notes = mutableListOf<String>()

        var currentSection = 0
        for (line in llmOutput.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val lower = trimmed.lowercase()
            when {
                "takeaway" in lower || "high-level" in lower || "[1." in lower -> {
                    currentSection = 1
                    continue
                }
                "in-depth" in lower || "concept" in lower || "[2." in lower -> {
                    currentSection = 2
                    continue
                }
                "essential" in lower || "study notes" in lower || "[3." in lower -> {
                    currentSection = 3
                    continue
                }
            }

            val cleanLine = trimmed.removePrefix("-").removePrefix("•").removePrefix("*").trim()
            if (cleanLine.isBlank()) continue

            when (currentSection) {
                1 -> takeaways.add(cleanLine)
                2 -> {
                    if (cleanLine.contains(":")) {
                        val parts = cleanLine.split(":", limit = 2)
                        concepts.add(parts[0].trim() to parts[1].trim())
                    } else {
                        concepts.add("Concept" to cleanLine)
                    }
                }
                3 -> notes.add(cleanLine)
                else -> takeaways.add(cleanLine)
            }
        }

        if (takeaways.isNotEmpty() || concepts.isNotEmpty() || notes.isNotEmpty()) {
            return AcademicScribeStudyTemplate(
                keyTakeawaysAndSummary = takeaways.ifEmpty { listOf("Lecture transcribed successfully.") },
                inDepthDetailsAndConcepts = concepts,
                essentialStudyNotes = notes
            )
        }
    }

    val nonBlankLines = transcript.lines().map { it.trim() }.filter { it.length > 20 }
    val takeaways = nonBlankLines.take(3)
    val concepts = nonBlankLines.drop(3).take(3).mapIndexed { idx, s ->
        "Core Segment ${idx + 1}" to s
    }
    val notes = nonBlankLines.takeLast(2)

    return AcademicScribeStudyTemplate(
        keyTakeawaysAndSummary = takeaways.ifEmpty { listOf("Audio transcribed verbatim from live lecture.") },
        inDepthDetailsAndConcepts = concepts,
        essentialStudyNotes = notes
    )
}

private fun extractTranscriptAnswerFallback(question: String, transcript: String): String {
    val qWords = question.lowercase().split(" ").filter { it.length > 3 }
    val matchingLines = transcript.lines().filter { line ->
        val lineLower = line.lowercase()
        qWords.any { word -> lineLower.contains(word) }
    }
    return if (matchingLines.isNotEmpty()) {
        "Relevant lecture segment:\n" + matchingLines.take(3).joinToString("\n") { "• $it" }
    } else {
        "The question could not be directly matched to the recorded lecture transcript. Please verify the terms or consult the full transcription above."
    }
}
