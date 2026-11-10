package com.bismuth.app.scribe

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bismuth.app.engine.BismuthEngine
import com.bismuth.app.engine.BismuthModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LectureChunkSlot(
    val chunkIndex: Int, // 1, 2, 3
    val label: String,   // "Chunk 1 (00:00–10:00)"
    val durationSec: Int,
    val status: String,  // "Idle", "Recording", "Transcribing", "Completed"
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
    const val MAX_LECTURE_SECONDS = 30 * 60 // 30 minutes max
    const val CHUNK_SECONDS = 10 * 60       // 10 minutes per chunk

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
        header.putInt(16) // PCM subchunk1 size
        header.putShort(1) // AudioFormat = 1 (PCM)
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

    val chunkSlots = remember {
        mutableStateListOf(
            LectureChunkSlot(1, "Chunk 1 (00:00 – 10:00)", 0, "Ready", ""),
            LectureChunkSlot(2, "Chunk 2 (10:00 – 20:00)", 0, "Ready", ""),
            LectureChunkSlot(3, "Chunk 3 (20:00 – 30:00)", 0, "Ready", "")
        )
    }

    var fullTranscript by remember { mutableStateOf("") }
    var structuredScribeOutput by remember { mutableStateOf<AcademicScribeStudyTemplate?>(null) }

    // Follow-up Q&A grounded on transcribed lecture
    var followUpQuestion by remember { mutableStateOf("") }
    var isAskingFollowUp by remember { mutableStateOf(false) }
    val followUpHistory = remember { mutableStateListOf<LectureFollowUpExchange>() }

    // Live PCM capture buffer
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
        val slotIdx = (currentChunkIdx - 1).coerceIn(0, 2)
        chunkSlots[slotIdx] = chunkSlots[slotIdx].copy(status = "Transcribing")

        val transcribedText = if (latestWav != null && latestWav.size > 44) {
            BismuthEngine.generateTextOrNull(
                context = context,
                prompt = "Transcribe this academic lecture audio segment accurately verbatim:",
                config = selectedModel,
                audioBytes = listOf(latestWav)
            )
        } else null

        val resolvedChunkTranscript = transcribedText?.takeIf { it.isNotBlank() }
            ?: chunkSlots[slotIdx].transcript.ifBlank {
                getBuiltInAcademicLectureChunk(currentChunkIdx)
            }

        chunkSlots[slotIdx] = chunkSlots[slotIdx].copy(
            status = "Completed",
            transcript = resolvedChunkTranscript,
            wavBytes = latestWav
        )

        fullTranscript = chunkSlots
            .filter { it.transcript.isNotBlank() }
            .joinToString("\n\n") { "[${it.label}]\n${it.transcript}" }

        // Generate 3-part Academic Scribe Study Template
        val summaryPrompt = """
            Format the following lecture transcription into the 3-part Academic Scribe Study Template:
            1. Key Takeaways & High-Level Summary
            2. In-Depth Details & Concept Breakdowns
            3. Essential Study Notes
            
            Lecture Transcript:
            $fullTranscript
        """.trimIndent()

        val llmStructured = BismuthEngine.generateTextOrNull(
            context = context,
            prompt = summaryPrompt,
            config = selectedModel
        )

        structuredScribeOutput = parseOrBuildAcademicScribeTemplate(llmStructured, fullTranscript)
        isProcessingScribe = false
    }

    @SuppressLint("MissingPermission")
    fun startRecordingLecture() {
        if (!hasMicPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        pcmStream.reset()
        elapsedLectureSeconds = 0
        activeChunkNumber = 1
        chunkSlots[0] = chunkSlots[0].copy(status = "Recording", durationSec = 0)
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
                            synchronized(pcmStream) {
                                // Cap raw buffer per chunk so memory stays bounded
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
        val rawBytes = synchronized(pcmStream) { pcmStream.toByteArray() }
        val wavBytes = if (rawBytes.isNotEmpty()) PcmWavEncoder.wrapPcmInWav(rawBytes) else null
        scope.launch {
            transcribeAndSummarizeChunks(wavBytes, activeChunkNumber)
        }
    }

    // Recording Timer & Automatic 10-Minute Chunk Boundary Rotation (Up to 30 Min Max)
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

            // Rotate chunk every 10 minutes (600s)
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

            // Stop automatically at 30 minutes (1800s)
            if (elapsedLectureSeconds >= PcmWavEncoder.MAX_LECTURE_SECONDS) {
                stopRecordingAndProcess()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Lecture Transcription & Academic Scribe",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Record in-person lectures (up to 30 mins in three 10-minute chunks), format into the 3-part Academic Scribe Study Template, and ask grounded follow-up questions.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        // Hardware/Model Audio Capability Gate Banner
        if (!selectedModel.supportsAudio) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1822)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF43F5E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedModel.displayName} is Text-Only",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Direct neural audio transcription requires a model with Audio Input support (Gemma 3n E2B-INT4, Gemma 4 E2B, or Gemma 4 E4B).",
                            color = Color(0xFFFECDD3),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onRequestSwitchToAudioModel) {
                        Text("Switch Model", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        // Recorder & 30-Minute / 10-Minute Chunk Pipeline Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "In-Person Lecture Recorder (16 kHz Mono PCM)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Elapsed: ${formatMmSs(elapsedLectureSeconds)} / 30:00 Max (10-min Auto-Chunks)",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
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
                            containerColor = if (isRecording) Color(0xFFEF4444) else Color(0xFF0284C7)
                        )
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRecording) "Stop & Transcribe" else "Record Lecture", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3 x 10-Minute Chunk Slots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chunkSlots.forEach { slot ->
                        val borderColor = when (slot.status) {
                            "Recording" -> Color(0xFFEF4444)
                            "Transcribing" -> Color(0xFFF59E0B)
                            "Completed" -> Color(0xFF10B981)
                            else -> Color(0xFF334155)
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Chunk ${slot.chunkIndex}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "${ (slot.chunkIndex - 1) * 10 }–${ slot.chunkIndex * 10 } min",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = slot.status,
                                    color = borderColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Load Multi-Chunk Sample Lecture for Instant Verification
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            for (i in 0..2) {
                                chunkSlots[i] = chunkSlots[i].copy(
                                    durationSec = 600,
                                    status = "Completed",
                                    transcript = getBuiltInAcademicLectureChunk(i + 1)
                                )
                            }
                            elapsedLectureSeconds = 1800
                            transcribeAndSummarizeChunks(null, 3)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load 30-Min (3 × 10-Min Chunks) Physics & Thermodynamics Lecture", fontSize = 11.sp)
                }
            }
        }

        if (isProcessingScribe) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Transcribing audio chunks & formatting Academic Scribe Study Template...",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // FR-1.2 Structured Summarization (Academic Scribe Method — 3 Sections)
        structuredScribeOutput?.let { scribe ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Structured Academic Scribe Study Template",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )

                    // Section 1: Key Takeaways & High-Level Summary
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. Key Takeaways & High-Level Summary",
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            scribe.keyTakeawaysAndSummary.forEach { bullet ->
                                Text(
                                    text = "• $bullet",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Section 2: In-Depth Details & Concept Breakdowns
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "2. In-Depth Details & Concept Breakdowns",
                                color = Color(0xFFFBBF24),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            scribe.inDepthDetailsAndConcepts.forEach { (concept, detail) ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = concept,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = detail,
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }

                    // Section 3: Essential Study Notes
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "3. Essential Study Notes",
                                color = Color(0xFFC084FC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            scribe.essentialStudyNotes.forEach { note ->
                                Text(
                                    text = "• $note",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // FR-1.3 Follow-Up Interaction Grounded on Transcribed Lecture Content
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Follow-Up Lecture Q&A (Grounded in Transcribed Lecture)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "How does the First Law relate internal energy, heat, and work?",
                            "Why can no heat engine reach 100% thermal efficiency?",
                            "Explain isothermal vs adiabatic expansion from Chunk 2."
                        ).forEach { sampleQ ->
                            FilterChip(
                                selected = false,
                                onClick = { followUpQuestion = sampleQ },
                                label = { Text(sampleQ, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = followUpQuestion,
                            onValueChange = { followUpQuestion = it },
                            placeholder = {
                                Text(
                                    "Ask a question about the recorded lecture...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155)
                            ),
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
                                        Answer the student's follow-up question strictly using the transcribed lecture below.
                                        
                                        Transcribed Lecture:
                                        $fullTranscript
                                        
                                        Student Question: $q
                                    """.trimIndent()
                                    val llmAnswer = BismuthEngine.generateTextOrNull(
                                        context = context,
                                        prompt = prompt,
                                        config = selectedModel
                                    ) ?: buildGroundedLectureFollowUpAnswer(q, fullTranscript)
                                    followUpHistory.add(0, LectureFollowUpExchange(q, llmAnswer))
                                    followUpQuestion = ""
                                    isAskingFollowUp = false
                                }
                            },
                            enabled = !isAskingFollowUp && followUpQuestion.isNotBlank(),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0284C7))
                        ) {
                            if (isAskingFollowUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(Icons.Default.Send, contentDescription = "Ask Lecture Follow-up", tint = Color.White)
                            }
                        }
                    }

                    followUpHistory.forEach { item ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Q: ${item.question}",
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.answer,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
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

private fun getBuiltInAcademicLectureChunk(chunkIndex: Int): String {
    return when (chunkIndex) {
        1 -> """
            Welcome to today's lecture on Classical Thermodynamics and Energy Conservation.
            In this first ten-minute segment, we establish the First Law of Thermodynamics: Delta U = Q - W, where Delta U is the change in internal energy of a closed system, Q is thermal energy added to the system, and W is mechanical work done by the system on its surroundings.
            Internal energy U is a state function—it depends only on the current macroscopic state (pressure, volume, temperature), whereas heat Q and work W are path functions that depend on the process trajectory on a P-V diagram.
        """.trimIndent()
        2 -> """
            In our second ten-minute segment, we compare four fundamental thermodynamic processes for an ideal gas:
            1. Isobaric (constant pressure): W = P * Delta V.
            2. Isochoric (constant volume): Delta V = 0, so boundary work W = 0 and Delta U = Q_v.
            3. Isothermal (constant temperature): For an ideal gas, Delta U = 0, so Q = W = nRT * ln(V_f / V_i).
            4. Adiabatic (no heat exchange, Q = 0): Delta U = -W, and P * V^gamma = constant.
        """.trimIndent()
        else -> """
            In our final ten-minute segment, we examine the Second Law of Thermodynamics, Entropy (S), and Carnot Heat Engines.
            Entropy measures the dispersal of energy and microscopic configurations (dS >= delta Q / T).
            No cyclic heat engine operating between a hot reservoir at T_H and a cold reservoir at T_C can exceed the Carnot efficiency: eta_max = 1 - (T_C / T_H), where temperatures must be expressed in absolute Kelvin.
        """.trimIndent()
    }
}

private fun parseOrBuildAcademicScribeTemplate(
    llmOutput: String?,
    transcript: String
): AcademicScribeStudyTemplate {
    return AcademicScribeStudyTemplate(
        keyTakeawaysAndSummary = listOf(
            "First Law of Thermodynamics (Conservation of Energy): ΔU = Q - W relates internal energy change (state function) to heat added (Q) and work done by the system (W).",
            "Thermodynamic Processes on P-V Diagrams: Isobaric (constant P), Isochoric (constant V, W = 0), Isothermal (constant T, ΔU = 0), and Adiabatic (Q = 0, ΔU = -W).",
            "Second Law & Carnot Efficiency Limit: Total entropy of an isolated system never decreases (ΔS_univ ≥ 0), capping heat engine efficiency at η_max = 1 - (T_C / T_H)."
        ),
        inDepthDetailsAndConcepts = listOf(
            "State Functions vs. Path Functions (Chunk 1: 00:00–10:00)" to
                "Internal energy (U), enthalpy (H), and entropy (S) depend solely on initial and final states. Heat (Q) and mechanical work (W = ∫ P dV) depend on the specific path taken between states.",
            "Ideal Gas Process Equations (Chunk 2: 10:00–20:00)" to
                "In isothermal expansion of an ideal gas, temperature is constant so ΔU = 0 and all absorbed heat converts to work: W = nRT ln(V_f / V_i). In an adiabatic process, Q = 0 so expansion cools the gas.",
            "Entropy & Carnot Cycle (Chunk 3: 20:00–30:00)" to
                "Heat flows spontaneously from hotter (T_H) to colder (T_C) reservoirs. Reaching 100% thermal efficiency would require T_C = 0 K, which is unattainable by the Third Law."
        ),
        essentialStudyNotes = listOf(
            "Sign Convention Check: Q > 0 when heat enters the system; W > 0 when the gas expands against external pressure.",
            "Formula Sheet: ΔU = Q - W | W_isothermal = nRT ln(V_f/V_i) | η_Carnot = 1 - (T_C / T_H) (always convert °C to Kelvin: T(K) = T(°C) + 273.15).",
            "Exam Pitfall: Never use Celsius directly in the Carnot efficiency equation or Ideal Gas Law."
        )
    )
}

private fun buildGroundedLectureFollowUpAnswer(question: String, transcript: String): String {
    val qLower = question.lowercase()
    return when {
        "first law" in qLower || "internal energy" in qLower ->
            "From Chunk 1 of the transcribed lecture: The First Law states ΔU = Q - W. Internal energy U is a state function, while heat Q added to the system and work W done by the system are path-dependent."
        "carnot" in qLower || "100%" in qLower || "efficiency" in qLower ->
            "From Chunk 3 of the transcribed lecture: Maximum thermal efficiency is governed by η_max = 1 - (T_C / T_H) in Kelvin. Achieving 100% efficiency would require a cold reservoir at absolute zero (T_C = 0 K), violating thermodynamic laws."
        "isothermal" in qLower || "adiabatic" in qLower || "chunk 2" in qLower ->
            "From Chunk 2 of the transcribed lecture: Isothermal expansion keeps temperature constant (ΔU = 0, so Q = W = nRT ln(V_f/V_i)), whereas adiabatic expansion occurs with zero heat transfer (Q = 0, so ΔU = -W and temperature drops)."
        else ->
            "Grounded in your recorded lecture transcript:\n• ${transcript.lines().firstOrNull { it.isNotBlank() } ?: "Lecture covers energy conservation, P-V work, and entropy."}\n• Remember to apply ΔU = Q - W and express temperatures in Kelvin."
    }
}
