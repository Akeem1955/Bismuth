package com.bismuth.app.teaching

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismuth.app.engine.AgentResponse
import com.bismuth.app.engine.BismuthEngine
import com.bismuth.app.engine.BismuthModelConfig
import com.bismuth.app.profile.StudentProfile
import com.bismuth.app.profile.StudentProfilePrefs
import com.bismuth.app.ui.animation.AIThinkingOrb
import com.bismuth.app.ui.animation.AudioPulseVisualizer
import com.bismuth.app.ui.animation.bouncyClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class QuickLearnFocusMode(val label: String, val directive: String) {
    INTUITION("Simple Analogy", "Prioritize crystal-clear everyday intuition before formulas."),
    WORKED("Worked Steps", "Focus on showing every mathematical or logical step in detail."),
    EXAM("Exam Drill", "Focus on exam formulas, edge cases, and trick misconceptions.")
}

data class QuickLearnQuizItem(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class QuickLearnLessonOutput(
    val title: String,
    val intuition: String,
    val corePrinciple: String,
    val workedExample: String,
    val pitfall: String,
    val quizzes: List<QuickLearnQuizItem>,
    val rawFallbackText: String
)

data class QuickLearnFollowUp(
    val question: String,
    val answer: String
)

object QuickLearnSkillLoader {
    private const val SKILL_ASSET_PATH = "skills/quick_learn/SKILL.md"

    suspend fun loadSkillMarkdown(context: Context): String = withContext(Dispatchers.IO) {
        try {
            context.assets.open(SKILL_ASSET_PATH).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            "You are Bismuth, an offline STEM tutor. Explain topics dynamically with [TITLE], [INTUITION], [CORE_PRINCIPLE], [WORKED_EXAMPLE], [PITFALL], and 2 check questions."
        }
    }
}

object QuickLearnParser {
    private val thinkBlockRegex = Regex("""<think>[\s\S]*?</think>""", RegexOption.IGNORE_CASE)
    private val markerRegex = Regex("""(?m)^\s*\[([A-Z0-9_]+)]\s*(.*)$""")

    fun sanitizeModelStream(raw: String): String {
        return raw.replace(thinkBlockRegex, "").trim()
    }

    fun parse(rawOutput: String, fallbackTopic: String): QuickLearnLessonOutput {
        val clean = sanitizeModelStream(rawOutput)
        val sections = mutableMapOf<String, StringBuilder>()
        var currentTag: String? = null

        clean.lines().forEach { line ->
            val match = markerRegex.matchEntire(line)
            if (match != null) {
                currentTag = match.groupValues[1].uppercase(Locale.US)
                val firstPart = match.groupValues[2].trim()
                val buf = sections.getOrPut(currentTag!!) { StringBuilder() }
                if (firstPart.isNotEmpty()) {
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(firstPart)
                }
            } else if (currentTag != null) {
                val buf = sections.getOrPut(currentTag!!) { StringBuilder() }
                if (buf.isNotEmpty() || line.isNotBlank()) {
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(line.trimEnd())
                }
            }
        }

        fun tagText(key: String): String = sections[key]?.toString()?.trim().orEmpty()

        val quizzes = buildList {
            for (idx in 1..2) {
                val q = tagText("QUIZ_${idx}_Q")
                val opts = listOf(
                    tagText("QUIZ_${idx}_A"),
                    tagText("QUIZ_${idx}_B"),
                    tagText("QUIZ_${idx}_C"),
                    tagText("QUIZ_${idx}_D")
                ).filter { it.isNotBlank() }
                val ansChar = tagText("QUIZ_${idx}_ANS").firstOrNull()?.uppercaseChar() ?: 'A'
                val correctIndex = (ansChar - 'A').coerceIn(0, (opts.size - 1).coerceAtLeast(0))
                val why = tagText("QUIZ_${idx}_WHY").ifBlank { "Verify against the core principle above." }
                if (q.isNotBlank() && opts.size >= 2) {
                    add(QuickLearnQuizItem(q, opts, correctIndex, why))
                }
            }
        }

        val title = tagText("TITLE").ifBlank { fallbackTopic }
        val intuition = tagText("INTUITION")
        val corePrinciple = tagText("CORE_PRINCIPLE")
        val workedExample = tagText("WORKED_EXAMPLE")
        val pitfall = tagText("PITFALL")

        return QuickLearnLessonOutput(
            title = title,
            intuition = intuition,
            corePrinciple = corePrinciple,
            workedExample = workedExample,
            pitfall = pitfall,
            quizzes = quizzes,
            rawFallbackText = clean
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickLearnTopicScreen(
    initialTopicQuery: String = "",
    selectedModel: BismuthModelConfig,
    onNavigateToModels: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var profile by remember { mutableStateOf(StudentProfilePrefs.getProfile(context)) }
    var activeLanguage by remember { mutableStateOf(profile.preferredLanguage) }
    var topicInput by remember(initialTopicQuery) { mutableStateOf(initialTopicQuery) }
    var focusMode by remember { mutableStateOf(QuickLearnFocusMode.INTUITION) }
    var skillMarkdown by remember { mutableStateOf("") }
    var showSkillSheet by remember { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var parsedLesson by remember { mutableStateOf<QuickLearnLessonOutput?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isModelDownloaded by remember(selectedModel.modelId) { mutableStateOf(selectedModel.isDownloaded(context)) }

    val selectedQuizAnswers = remember { mutableStateMapOf<Int, Int>() }
    var followUpInput by remember { mutableStateOf("") }
    var isAnsweringFollowUp by remember { mutableStateOf(false) }
    val followUps = remember { mutableStateListOf<QuickLearnFollowUp>() }

    // TTS Setup
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ngLocale = Locale.forLanguageTag("en-NG")
                val res = engine?.setLanguage(ngLocale)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine?.language = Locale.US
                }
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isSpeaking = true }
                    override fun onDone(utteranceId: String?) { isSpeaking = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { isSpeaking = false }
                })
                ttsEngine = engine
            }
        }
        onDispose {
            engine?.stop()
            engine?.shutdown()
            ttsEngine = null
        }
    }

    fun speakText(text: String) {
        val engine = ttsEngine ?: return
        if (engine.isSpeaking) {
            engine.stop()
            isSpeaking = false
        } else {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bismuth_quick_learn_tts")
        }
    }

    fun runQuickLearn(query: String, activeProfile: StudentProfile, lang: String, mode: QuickLearnFocusMode) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty() || isGenerating) return
        focusManager.clearFocus()
        isModelDownloaded = selectedModel.isDownloaded(context)

        if (!isModelDownloaded) {
            errorMessage = "${selectedModel.displayName} is not saved on your phone yet. Open AI Brains to pick and save an offline AI."
            return
        }

        scope.launch {
            isGenerating = true
            errorMessage = null
            streamingText = ""
            parsedLesson = null
            selectedQuizAnswers.clear()
            followUps.clear()

            val loadedSkill = skillMarkdown.ifBlank {
                QuickLearnSkillLoader.loadSkillMarkdown(context).also { skillMarkdown = it }
            }

            val systemPrompt = """
                $loadedSkill

                [Active Student Context]
                Student Name: ${activeProfile.name.ifBlank { "Scholar" }}
                [Academic Level]: ${activeProfile.academicLevel}
                [Learning Style]: ${activeProfile.learningStyle}
                [Focus Mode]: ${mode.label} — ${mode.directive}
                [Language Directive]: ${StudentProfilePrefs.languageInstruction(lang)}
            """.trimIndent()

            val userPrompt = "Teach me this topic clearly: \"$cleanQuery\". Follow every [TAG] in the skill specification."

            val resultBuffer = StringBuilder()
            var streamError: String? = null

            withContext(Dispatchers.IO) {
                val ready = BismuthEngine.initializeIfNeeded(
                    context = context,
                    config = selectedModel,
                    systemPrompt = systemPrompt
                )
                if (!ready) {
                    streamError = "Could not start ${selectedModel.displayName}. Please check your saved AI in AI Brains."
                    return@withContext
                }
                BismuthEngine.resetConversation(systemPrompt = systemPrompt)
                BismuthEngine.sendAgentMessage(userPrompt).collect { resp ->
                    when (resp) {
                        is AgentResponse.Token -> {
                            resultBuffer.append(resp.text)
                            val snapshot = QuickLearnParser.sanitizeModelStream(resultBuffer.toString())
                            withContext(Dispatchers.Main) {
                                streamingText = snapshot
                            }
                        }
                        is AgentResponse.Error -> {
                            streamError = resp.message
                        }
                        else -> {}
                    }
                }
            }

            if (streamError != null && resultBuffer.isEmpty()) {
                errorMessage = streamError
            } else if (resultBuffer.isNotBlank()) {
                parsedLesson = QuickLearnParser.parse(resultBuffer.toString(), cleanQuery)
            } else {
                errorMessage = "The model returned an empty response. Try phrasing the topic slightly more specifically."
            }
            isGenerating = false
        }
    }

    LaunchedEffect(Unit) {
        profile = StudentProfilePrefs.getProfile(context)
        activeLanguage = profile.preferredLanguage
        skillMarkdown = QuickLearnSkillLoader.loadSkillMarkdown(context)
        isModelDownloaded = selectedModel.isDownloaded(context)

        if (initialTopicQuery.isNotBlank() && isModelDownloaded) {
            runQuickLearn(initialTopicQuery, profile, activeLanguage, focusMode)
        }
    }

    val starterTopics = remember(profile.primarySubject) {
        when {
            "phys" in profile.primarySubject.lowercase() -> listOf("Ohm's Law", "Projectile Motion", "Kinetic Energy", "Doppler Effect")
            "chem" in profile.primarySubject.lowercase() -> listOf("Molarity & Moles", "Covalent vs Ionic Bonds", "pH Scale", "Ideal Gas Law")
            "bio" in profile.primarySubject.lowercase() -> listOf("Photosynthesis", "Mitosis vs Meiosis", "ATP Respiration", "Osmosis")
            "comp" in profile.primarySubject.lowercase() -> listOf("Big-O Notation", "Recursion", "Binary Search", "Hash Tables")
            else -> listOf("Quadratic Formula", "Pythagorean Theorem", "Derivatives Intuition", "Probability Bayes Rule")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column {
                    Text(
                        text = "Quick Learn Any Topic",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${selectedModel.displayName} • Offline Skill AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = { showSkillSheet = true }) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "Inspect Quick Learn SKILL.md",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isModelDownloaded) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Model Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Download or import ${selectedModel.displayName} once to explain any topic with zero internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        OutlinedButton(onClick = onNavigateToModels) {
                            Text("Models")
                        }
                    }
                }
            }

            // Topic Query Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = topicInput,
                        onValueChange = { topicInput = it },
                        label = { Text("What do you want to understand?") },
                        placeholder = { Text("e.g. Quadratic Formula, Osmosis, Ohm's Law") },
                        singleLine = true,
                        trailingIcon = {
                            if (topicInput.isNotEmpty()) {
                                IconButton(onClick = { topicInput = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { runQuickLearn(topicInput, profile, activeLanguage, focusMode) }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Language & Focus Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudentProfilePrefs.STUDY_LANGUAGES.forEach { lang ->
                            FilterChip(
                                selected = activeLanguage == lang,
                                onClick = { activeLanguage = lang },
                                label = { Text(lang, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        QuickLearnFocusMode.entries.forEach { mode ->
                            FilterChip(
                                selected = focusMode == mode,
                                onClick = { focusMode = mode },
                                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Button(
                        onClick = { runQuickLearn(topicInput, profile, activeLanguage, focusMode) },
                        enabled = topicInput.isNotBlank() && !isGenerating,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Thinking on Device...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Explain Topic Offline", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (parsedLesson == null && !isGenerating) {
                        Text(
                            text = "Quick Starters:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            starterTopics.forEach { sample ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.bouncyClickable {
                                        topicInput = sample
                                        runQuickLearn(sample, profile, activeLanguage, focusMode)
                                    }
                                ) {
                                    Text(
                                        text = sample,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Live streaming preview before final structured cards render
            if (isGenerating) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AIThinkingOrb(size = 34.dp)
                            Column {
                                Text(
                                    text = "Synthesizing live in $activeLanguage...",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Guided by quick_learn/SKILL.md",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (streamingText.isNotBlank()) {
                            Text(
                                text = streamingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Structured Dynamic Output
            parsedLesson?.let { lesson ->
                // Header + Speak Action
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lesson.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${activeLanguage} • ${focusMode.label} • ${profile.academicLevel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.bouncyClickable {
                                val narration = listOf(lesson.title, lesson.intuition, lesson.corePrinciple, lesson.workedExample)
                                    .filter { it.isNotBlank() }
                                    .joinToString(". ")
                                    .ifBlank { lesson.rawFallbackText }
                                speakText(narration)
                            }
                        ) {
                            Box(modifier = Modifier.padding(10.dp)) {
                                if (isSpeaking) {
                                    AudioPulseVisualizer(size = 20.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Read lesson aloud",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                val hasStructuredSections = lesson.intuition.isNotBlank() ||
                    lesson.corePrinciple.isNotBlank() ||
                    lesson.workedExample.isNotBlank()

                if (hasStructuredSections) {
                    if (lesson.intuition.isNotBlank()) {
                        QuickLearnSectionCard(
                            title = "Everyday Intuition",
                            body = lesson.intuition,
                            icon = Icons.Default.Lightbulb,
                            accent = Color(0xFF006874)
                        )
                    }
                    if (lesson.corePrinciple.isNotBlank()) {
                        QuickLearnSectionCard(
                            title = "Core Formula / Principle",
                            body = lesson.corePrinciple,
                            icon = Icons.Default.Bolt,
                            accent = Color(0xFF2563EB)
                        )
                    }
                    if (lesson.workedExample.isNotBlank()) {
                        QuickLearnSectionCard(
                            title = "Step-by-Step Worked Example",
                            body = lesson.workedExample,
                            icon = Icons.Default.Calculate,
                            accent = Color(0xFF0F766E)
                        )
                    }
                    if (lesson.pitfall.isNotBlank()) {
                        QuickLearnSectionCard(
                            title = "Common Mistake to Watch",
                            body = lesson.pitfall,
                            icon = Icons.Default.Warning,
                            accent = Color(0xFFD97706)
                        )
                    }
                } else {
                    QuickLearnSectionCard(
                        title = "AI Explanation",
                        body = lesson.rawFallbackText,
                        icon = Icons.Default.AutoAwesome,
                        accent = MaterialTheme.colorScheme.primary
                    )
                }

                if (lesson.quizzes.isNotEmpty()) {
                    Text(
                        text = "Quick Check Questions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    lesson.quizzes.forEachIndexed { qIdx, quiz ->
                        val chosen = selectedQuizAnswers[qIdx]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Quiz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Check ${qIdx + 1}: ${quiz.question}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                quiz.options.forEachIndexed { optIdx, optText ->
                                    val isChosen = chosen == optIdx
                                    val isRight = optIdx == quiz.correctIndex
                                    val bg = when {
                                        chosen == null -> MaterialTheme.colorScheme.surface
                                        isRight -> Color(0xFF00C853).copy(alpha = 0.16f)
                                        isChosen && !isRight -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    val borderCol = when {
                                        chosen == null -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        isRight -> Color(0xFF00C853)
                                        isChosen && !isRight -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = bg,
                                        border = BorderStroke(if (isChosen || (chosen != null && isRight)) 1.5.dp else 1.dp, borderCol),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .bouncyClickable(enabled = chosen == null) {
                                                selectedQuizAnswers[qIdx] = optIdx
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${'A' + optIdx}) $optText",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (chosen != null && isRight) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "Correct", tint = Color(0xFF00C853), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = chosen != null) {
                                    Text(
                                        text = quiz.explanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // Follow-up Doubt Clarification
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Ask a Follow-Up Question",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = followUpInput,
                                onValueChange = { followUpInput = it },
                                placeholder = { Text("Why did that step happen?", style = MaterialTheme.typography.bodySmall) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            IconButton(
                                onClick = {
                                    val q = followUpInput.trim()
                                    if (q.isEmpty() || isAnsweringFollowUp) return@IconButton
                                    followUpInput = ""
                                    focusManager.clearFocus()
                                    scope.launch {
                                        isAnsweringFollowUp = true
                                        val reply = BismuthEngine.generateTextOrNull(
                                            context = context,
                                            prompt = "Topic: ${lesson.title}. Student question: \"$q\". ${StudentProfilePrefs.languageInstruction(activeLanguage)} Keep answer short, clear, and accurate.",
                                            config = selectedModel,
                                            systemPrompt = skillMarkdown
                                        )
                                        val cleanReply = QuickLearnParser.sanitizeModelStream(reply.orEmpty())
                                        if (cleanReply.isNotBlank()) {
                                            followUps.add(QuickLearnFollowUp(q, cleanReply))
                                        }
                                        isAnsweringFollowUp = false
                                    }
                                },
                                enabled = !isAnsweringFollowUp && followUpInput.isNotBlank()
                            ) {
                                if (isAnsweringFollowUp) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send question", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        followUps.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("You: ${item.question}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(item.answer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showSkillSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSkillSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Quick Learn Skill (assets/skills/quick_learn/SKILL.md)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = skillMarkdown,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun QuickLearnSectionCard(
    title: String,
    body: String,
    icon: ImageVector,
    accent: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.14f)) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.padding(7.dp).size(18.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
