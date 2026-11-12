package com.bismuth.app.teaching

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismuth.app.ui.animation.bouncyClickable
import com.bismuth.app.engine.BismuthModelConfig
import com.bismuth.app.profile.LessonProgressStore
import com.bismuth.app.profile.StudentProfilePrefs
import com.bismuth.app.skills.ChapterCatalogNode
import com.bismuth.app.skills.CuratedResource
import com.bismuth.app.skills.SkillsCatalogRoot
import com.bismuth.app.skills.SubjectCatalogCoordinator
import com.bismuth.app.skills.SubjectSkillCatalog
import com.bismuth.app.skills.TopicCatalogEntry
import com.bismuth.app.skills.TopicSkillBundle
import com.bismuth.app.ui.animation.AIThinkingOrb
import com.bismuth.app.ui.animation.AudioPulseVisualizer
import com.bismuth.app.ui.animation.CelebrationBurstOverlay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private data class HitTarget(
    val obj: BoardObject,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private enum class LessonVisualStageMode(val label: String) {
    WHITEBOARD("Board"),
    STEP_DIAGRAM("Diagram"),
    WALKTHROUGH_VIDEO("Video"),
    FORMULA_GUIDE("Formulas"),
}

enum class SocraticTeachingScreenStage {
    FULL_SCREEN_BOARD,
    FULL_SCREEN_QUIZ,
    LESSON_MASTERED
}

private enum class QuizCheckState {
    UNCHECKED,
    CORRECT,
    INCORRECT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveWhiteboardScreen(
    initialTopic: TopicCatalogEntry? = null,
    selectedModel: BismuthModelConfig? = null,
    onNavigateBack: () -> Unit,
    onQuickLearnTopic: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }

    val studentProfile = remember { StudentProfilePrefs.getProfile(context) }

    // Catalog & Active Topic State
    var catalog by remember { mutableStateOf<SkillsCatalogRoot?>(null) }
    var selectedSubject by remember { mutableStateOf<SubjectCatalogCoordinator?>(null) }
    var selectedChapter by remember { mutableStateOf<ChapterCatalogNode?>(null) }
    var selectedTopicEntry by remember(initialTopic?.topicId) { mutableStateOf<TopicCatalogEntry?>(initialTopic) }
    var activeBundle by remember(initialTopic?.topicId) { mutableStateOf<TopicSkillBundle?>(null) }
    var isLoadingBundle by remember { mutableStateOf(false) }
    var isDownloadingPack by remember { mutableStateOf(false) }
    var openedFromCatalog by remember(initialTopic?.topicId) { mutableStateOf(initialTopic == null) }

    // Full-screen mode switcher: Whiteboard vs. Quiz (not split!)
    var screenStage by remember(initialTopic?.topicId) { mutableStateOf(SocraticTeachingScreenStage.FULL_SCREEN_BOARD) }

    // Bottom Sheets & Dialogs
    var showSkillInspectorSheet by remember { mutableStateOf(false) }
    var inspectedNode by remember { mutableStateOf<BoardObject?>(null) }
    val nodeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val inspectorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Load initial catalog & sync active topic when initialTopic changes
    LaunchedEffect(initialTopic?.topicId) {
        val cat = catalog ?: SubjectSkillCatalog.loadCatalog(context)
        catalog = cat
        selectedTopicEntry = initialTopic
        if (initialTopic != null) {
            selectedSubject = cat.subjects.find { s ->
                s.chapters.any { c -> c.topics.any { t -> t.topicId == initialTopic.topicId } }
            } ?: cat.subjects.firstOrNull()
            selectedChapter = selectedSubject?.chapters?.find { c ->
                c.topics.any { t -> t.topicId == initialTopic.topicId }
            } ?: selectedSubject?.chapters?.firstOrNull()
        }
    }

    LaunchedEffect(selectedTopicEntry?.topicId) {
        val entry = selectedTopicEntry
        if (entry == null) {
            activeBundle = null
            isLoadingBundle = false
            return@LaunchedEffect
        }
        val cat = catalog ?: SubjectSkillCatalog.loadCatalog(context).also { catalog = it }
        selectedSubject = cat.subjects.find { s ->
            s.chapters.any { c -> c.topics.any { t -> t.topicId == entry.topicId } }
        } ?: selectedSubject ?: cat.subjects.firstOrNull()
        selectedChapter = selectedSubject?.chapters?.find { c ->
            c.topics.any { t -> t.topicId == entry.topicId }
        } ?: selectedChapter ?: selectedSubject?.chapters?.firstOrNull()

        isLoadingBundle = true
        activeBundle = SubjectSkillCatalog.loadTopicBundleOffline(context, entry)
        isLoadingBundle = false
    }

    val scene = activeBundle?.whiteboardScene

    // Socratic Stepper State
    var currentStepIndex by remember(activeBundle?.topicId) { mutableIntStateOf(0) }
    val totalSteps = (scene?.steps?.size ?: 3).coerceAtLeast(1)
    val currentStep = scene?.steps?.getOrElse(currentStepIndex) { scene.steps.firstOrNull() }
    var stageMode by remember(activeBundle?.topicId) { mutableStateOf(LessonVisualStageMode.WHITEBOARD) }

    // Dynamic AI-Generated Quiz State
    var activeDynamicQuiz by remember(activeBundle?.topicId, currentStepIndex) { mutableStateOf<DynamicStepQuiz?>(null) }
    var isLoadingQuiz by remember(activeBundle?.topicId, currentStepIndex) { mutableStateOf(false) }
    var selectedQuizOption by remember(activeBundle?.topicId, currentStepIndex) { mutableStateOf<Int?>(null) }
    var quizCheckState by remember(activeBundle?.topicId, currentStepIndex) { mutableStateOf(QuizCheckState.UNCHECKED) }

    // Fetch or generate dynamic quiz for this step
    LaunchedEffect(activeBundle?.topicId, currentStepIndex) {
        val bundle = activeBundle ?: return@LaunchedEffect
        val step = currentStep ?: return@LaunchedEffect
        isLoadingQuiz = true
        activeDynamicQuiz = DynamicQuizOrchestrator.generateStepQuiz(
            context = context,
            bundle = bundle,
            step = step,
            profile = studentProfile,
            selectedModel = selectedModel
        )
        isLoadingQuiz = false
    }

    // Canvas Pan & Zoom State
    var zoomScale by remember(activeBundle?.topicId) { mutableFloatStateOf(1.0f) }
    var panOffset by remember(activeBundle?.topicId) { mutableStateOf(Offset.Zero) }

    // TTS & Tutor Voice Engine (prefers natural high-quality English voice when installed)
    var isTtsSpeaking by remember { mutableStateOf(false) }
    var tutorState by remember { mutableStateOf(TutorState.IDLE) }
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
                runCatching {
                    val bestVoice = engine?.voices
                        ?.filter { it.locale?.language == "en" && !it.isNetworkConnectionRequired }
                        ?.sortedWith(
                            compareByDescending<Voice> { it.locale?.country == "NG" }
                                .thenByDescending { it.quality }
                                .thenByDescending { it.name.contains("neural", ignoreCase = true) || it.name.contains("local", ignoreCase = true) }
                        )
                        ?.firstOrNull()
                    if (bestVoice != null) {
                        engine?.voice = bestVoice
                    }
                    engine?.setSpeechRate(0.97f)
                }
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isTtsSpeaking = true
                        tutorState = TutorState.SPEAKING
                    }

                    override fun onDone(utteranceId: String?) {
                        isTtsSpeaking = false
                        tutorState = if (quizCheckState == QuizCheckState.CORRECT) TutorState.CELEBRATING else TutorState.IDLE
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isTtsSpeaking = false
                        tutorState = TutorState.IDLE
                    }
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

    fun speakNarration(text: String) {
        ttsEngine?.let { engine ->
            if (engine.isSpeaking) {
                engine.stop()
                isTtsSpeaking = false
                tutorState = TutorState.IDLE
            } else {
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bismuth_tts_speech")
            }
        }
    }

    fun handleLessonBack() {
        ttsEngine?.stop()
        isTtsSpeaking = false
        tutorState = TutorState.IDLE
        if (openedFromCatalog) {
            selectedTopicEntry = null
            activeBundle = null
            screenStage = SocraticTeachingScreenStage.FULL_SCREEN_BOARD
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = selectedTopicEntry != null) {
        if (screenStage == SocraticTeachingScreenStage.FULL_SCREEN_QUIZ) {
            screenStage = SocraticTeachingScreenStage.FULL_SCREEN_BOARD
        } else {
            handleLessonBack()
        }
    }

    BackHandler(enabled = selectedTopicEntry == null) {
        onNavigateBack()
    }

    // Auto-narrate on step change when a lesson bundle is active in Whiteboard view
    LaunchedEffect(activeBundle?.topicId, currentStepIndex, screenStage) {
        if (selectedTopicEntry != null && activeBundle != null && screenStage == SocraticTeachingScreenStage.FULL_SCREEN_BOARD) {
            val narration = currentStep?.narration
            if (!narration.isNullOrBlank()) {
                tutorState = TutorState.SPEAKING
                speakNarration(narration)
            }
        }
    }

    val currentCatalog = catalog
    if (selectedTopicEntry == null) {
        if (currentCatalog != null) {
            TeachCourseCatalogScreen(
                catalog = currentCatalog,
                onCatalogUpdated = { catalog = it },
                onSelectTopic = { chosenTopic ->
                    openedFromCatalog = true
                    screenStage = SocraticTeachingScreenStage.FULL_SCREEN_BOARD
                    selectedTopicEntry = chosenTopic
                },
                onNavigateBack = onNavigateBack,
                onQuickLearnTopic = onQuickLearnTopic,
                modifier = modifier
            )
        } else {
            Box(
                modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AnimatedContent(
            targetState = screenStage,
            transitionSpec = {
                if (targetState == SocraticTeachingScreenStage.FULL_SCREEN_QUIZ) {
                    (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "screenStageTransition"
        ) { stage ->
            when (stage) {
                SocraticTeachingScreenStage.FULL_SCREEN_BOARD -> {
                    // ─────────────────────────────────────────────────────────────
                    // STAGE 1: FULL-SCREEN SOCRATIC WHITEBOARD & INTERACTIVE MODEL
                    // ─────────────────────────────────────────────────────────────
                    val subjectAccent = subjectBrandColor(selectedSubject?.subjectId.orEmpty())
                    val entrySnapshot = selectedTopicEntry

                    fun triggerPackDownload() {
                        val target = entrySnapshot ?: return
                        if (isDownloadingPack) return
                        scope.launch {
                            isDownloadingPack = true
                            val res = SubjectSkillCatalog.downloadAndInstallTopicFromGcs(context, target)
                            isDownloadingPack = false
                            res.onSuccess { downloaded ->
                                activeBundle = downloaded
                                snackbarHostState.showSnackbar("Saved ${target.title} offline")
                            }.onFailure { err ->
                                snackbarHostState.showSnackbar("Download failed: ${err.message ?: "Check internet"}")
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // Top Navigation Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { handleLessonBack() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (openedFromCatalog) "Back to Courses" else "Back to Dashboard",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = selectedTopicEntry?.title ?: "Socratic Whiteboard",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${selectedSubject?.iconEmoji.orEmpty()} ${selectedSubject?.title ?: "STEM"} • Step ${currentStepIndex + 1}/$totalSteps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subjectAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        ttsEngine?.stop()
                                        openedFromCatalog = true
                                        selectedTopicEntry = null
                                        activeBundle = null
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = "Browse Subjects & Modules",
                                        tint = subjectAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (onQuickLearnTopic != null && entrySnapshot != null) {
                                    IconButton(
                                        onClick = { onQuickLearnTopic(entrySnapshot.title) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Quick Learn this topic",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { showSkillInspectorSheet = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = "Inspect SKILL.md",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        currentStepIndex = 0
                                        selectedQuizOption = null
                                        quizCheckState = QuizCheckState.UNCHECKED
                                        zoomScale = 1.0f
                                        panOffset = Offset.Zero
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "Restart Lesson",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (activeBundle == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingBundle) {
                                    CircularProgressIndicator()
                                } else if (entrySnapshot != null) {
                                    Card(
                                        shape = RoundedCornerShape(22.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                        border = BorderStroke(1.dp, subjectAccent.copy(alpha = 0.45f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(22.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                tint = subjectAccent,
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Text(
                                                text = entrySnapshot.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Download this ${(entrySnapshot.packageSizeBytes / 1024).coerceAtLeast(1)} KB multimedia module pack (diagrams, walkthrough video, board & quiz) to study offline.",
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { triggerPackDownload() },
                                                enabled = !isDownloadingPack,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (isDownloadingPack) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Downloading Multimedia Pack…")
                                                } else {
                                                    Text("Download & Start Module")
                                                }
                                            }
                                            if (onQuickLearnTopic != null) {
                                                OutlinedButton(
                                                    onClick = { onQuickLearnTopic(entrySnapshot.title) },
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Learn via Quick AI Instead")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                        val bundleLoaded = activeBundle!!

                        // Segmented Progress Bar + Step Selector
                        BrilliantSegmentedProgressBar(
                            currentStepIndex = currentStepIndex,
                            totalSteps = totalSteps,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Socratic Voice Speech Bubble
                        currentStep?.let { step ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, subjectAccent.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (isTtsSpeaking) {
                                        AudioPulseVisualizer(size = 26.dp)
                                    } else {
                                        AIThinkingOrb(size = 26.dp)
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = step.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = subjectAccent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = step.narration,
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = 17.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    IconButton(
                                        onClick = { speakNarration(step.narration) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Listen",
                                            tint = subjectAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Dedicated Clean Multimedia Mode Switcher Bar (Zero overlap with canvas media pills)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LessonVisualStageMode.entries.forEach { mode ->
                                val isSelected = stageMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) subjectAccent else Color.Transparent)
                                        .clickable { stageMode = mode }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF061618) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Full-Screen Interactive Visual Canvas Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    RoundedCornerShape(20.dp)
                                )
                        ) {
                            when (stageMode) {
                                LessonVisualStageMode.STEP_DIAGRAM -> {
                                    OfflineStepDiagramView(
                                        topicId = bundleLoaded.topicId,
                                        bundledInApk = entrySnapshot?.bundledInApk == true,
                                        step = currentStep ?: scene!!.steps.first(),
                                        onRequestDownloadPack = { triggerPackDownload() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                LessonVisualStageMode.WALKTHROUGH_VIDEO -> {
                                    OfflineWalkthroughVideoPlayer(
                                        topicId = bundleLoaded.topicId,
                                        bundledInApk = entrySnapshot?.bundledInApk == true,
                                        currentStep = currentStep ?: scene!!.steps.first(),
                                        totalSteps = totalSteps,
                                        onStepSelectedFromVideo = { currentStepIndex = it },
                                        onNarrateStep = { speakNarration(it) },
                                        onRequestDownloadPack = { triggerPackDownload() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                LessonVisualStageMode.FORMULA_GUIDE -> {
                                    SkillFormulaAndObjectivesPanel(
                                        bundle = bundleLoaded,
                                        accentColor = subjectAccent,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                LessonVisualStageMode.WHITEBOARD -> {
                                    val visibleObjects = remember(currentStepIndex, scene) {
                                        scene?.visibleObjectsForStep(currentStepIndex).orEmpty()
                                    }
                                    val visibleArrows = remember(currentStepIndex, scene) {
                                        scene?.visibleArrowsForStep(currentStepIndex).orEmpty()
                                    }
                                    val focusIds = remember(currentStepIndex, scene) {
                                        currentStep?.focusObjectIds.orEmpty().toSet()
                                    }

                                    val focusPulseTransition = rememberInfiniteTransition(label = "nodeFocusPulse")
                                    val focusPulseAlpha by focusPulseTransition.animateFloat(
                                        initialValue = 0.25f,
                                        targetValue = 0.85f,
                                        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                                        label = "focusAlpha"
                                    )

                                    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
                                    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                                    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
                                    val onSurface = MaterialTheme.colorScheme.onSurface
                                    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                                    val outline = MaterialTheme.colorScheme.outline
                                    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
                                    val pillBgColor = MaterialTheme.colorScheme.surfaceContainerHighest

                                    val hitTargets = remember { mutableListOf<HitTarget>() }

                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Topic-specific animated interactive lab across ALL 10 modules
                                        if (currentStep != null) {
                                            TopicInteractiveSimulationCard(
                                                topicId = bundleLoaded.topicId,
                                                stepNumber = currentStep.stepNumber,
                                                stepTitle = currentStep.title,
                                                accentColor = subjectAccent,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        // Whiteboard Canvas
                                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                            Canvas(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .pointerInput(currentStepIndex, scene?.sceneId) {
                                                        detectTransformGestures { _, pan, zoom, _ ->
                                                            zoomScale = (zoomScale * zoom).coerceIn(0.7f, 2.5f)
                                                            panOffset += pan
                                                        }
                                                    }
                                                    .pointerInput(currentStepIndex, scene?.sceneId, zoomScale, panOffset) {
                                                        detectTapGestures { tapOffset ->
                                                            val hit = hitTargets.lastOrNull { target ->
                                                                tapOffset.x in target.left..target.right &&
                                                                        tapOffset.y in target.top..target.bottom
                                                            }
                                                            if (hit != null) {
                                                                inspectedNode = hit.obj
                                                                speakNarration(hit.obj.explanation.ifBlank { hit.obj.label })
                                                            }
                                                        }
                                                    }
                                            ) {
                                                hitTargets.clear()
                                                drawWhiteboardGrid(gridColor = outlineVariant.copy(alpha = 0.15f))

                                                val minX = visibleObjects.minOfOrNull { it.position.x } ?: 0f
                                                val maxX = visibleObjects.maxOfOrNull { it.position.x + it.size.width } ?: 500f
                                                val minY = visibleObjects.minOfOrNull { it.position.y } ?: 0f
                                                val maxY = visibleObjects.maxOfOrNull { it.position.y + it.size.height } ?: 400f
                                                val sceneW = (maxX - minX).coerceAtLeast(240f)
                                                val sceneH = (maxY - minY).coerceAtLeast(180f)

                                                val availableW = size.width - 28.dp.toPx()
                                                val availableH = size.height - 28.dp.toPx()
                                                val autoFitScale = minOf(availableW / sceneW, availableH / sceneH).coerceIn(0.7f, 2.0f)
                                                val fitZoom = zoomScale * autoFitScale
                                                val centerOffsetX = (size.width - sceneW * fitZoom) / 2f - minX * fitZoom + panOffset.x
                                                val centerOffsetY = (size.height - sceneH * fitZoom) / 2f - minY * fitZoom + panOffset.y

                                                val nodeCenters = mutableMapOf<String, Offset>()
                                                visibleObjects.forEach { obj ->
                                                    val nodeX = obj.position.x * fitZoom + centerOffsetX
                                                    val nodeY = obj.position.y * fitZoom + centerOffsetY
                                                    val nodeWidth = obj.size.width * fitZoom
                                                    val nodeHeight = obj.size.height * fitZoom
                                                    nodeCenters[obj.objectId] = Offset(nodeX + nodeWidth / 2f, nodeY + nodeHeight / 2f)
                                                }

                                                // Draw arrows UNDERNEATH cards + edge labels
                                                visibleArrows.forEach { arrow ->
                                                    val fromCenter = nodeCenters[arrow.fromObjectId]
                                                    val toCenter = nodeCenters[arrow.toObjectId]
                                                    if (fromCenter != null && toCenter != null) {
                                                        drawArrow(
                                                            from = fromCenter,
                                                            to = toCenter,
                                                            color = subjectAccent.copy(alpha = 0.85f),
                                                            strokeWidth = 2.4f * fitZoom,
                                                            zoom = fitZoom
                                                        )
                                                        if (arrow.label.isNotBlank()) {
                                                            val midX = (fromCenter.x + toCenter.x) / 2f
                                                            val midY = (fromCenter.y + toCenter.y) / 2f
                                                            val edgeTextPaint = TextPaint().apply {
                                                                color = onSurface.toArgb()
                                                                textSize = (9.5f * density.density * fitZoom).coerceIn(8f, 22f)
                                                                isAntiAlias = true
                                                            }
                                                            val textW = edgeTextPaint.measureText(arrow.label) + 16f
                                                            val pillH = edgeTextPaint.textSize + 10f
                                                            drawRoundRect(
                                                                color = pillBgColor.copy(alpha = 0.94f),
                                                                topLeft = Offset(midX - textW / 2f, midY - pillH / 2f),
                                                                size = Size(textW, pillH),
                                                                cornerRadius = CornerRadius(pillH / 2f, pillH / 2f)
                                                            )
                                                            drawContext.canvas.nativeCanvas.drawText(
                                                                arrow.label,
                                                                midX - (textW - 16f) / 2f,
                                                                midY + edgeTextPaint.textSize * 0.35f,
                                                                edgeTextPaint
                                                            )
                                                        }
                                                    }
                                                }

                                                // Draw concept blocks on top with wrapped text & animated focus glow
                                                visibleObjects.forEach { obj ->
                                                    val isFocused = obj.objectId in focusIds
                                                    val nodeX = obj.position.x * fitZoom + centerOffsetX
                                                    val nodeY = obj.position.y * fitZoom + centerOffsetY
                                                    val nodeWidth = obj.size.width * fitZoom
                                                    val nodeHeight = obj.size.height * fitZoom

                                                    val cornerRad = CornerRadius(16f * fitZoom, 16f * fitZoom)
                                                    val boxTopLeft = Offset(nodeX, nodeY)
                                                    val boxSize = Size(nodeWidth, nodeHeight)

                                                    hitTargets.add(
                                                        HitTarget(
                                                            obj = obj,
                                                            left = nodeX,
                                                            top = nodeY,
                                                            right = nodeX + nodeWidth,
                                                            bottom = nodeY + nodeHeight
                                                        )
                                                    )

                                                    if (isFocused) {
                                                        drawRoundRect(
                                                            color = subjectAccent.copy(alpha = 0.25f * focusPulseAlpha),
                                                            topLeft = Offset(nodeX - 4f * fitZoom, nodeY - 4f * fitZoom),
                                                            size = Size(nodeWidth + 8f * fitZoom, nodeHeight + 8f * fitZoom),
                                                            cornerRadius = CornerRadius(20f * fitZoom, 20f * fitZoom)
                                                        )
                                                    }

                                                    drawRoundRect(
                                                        color = if (isFocused) primaryContainer else surfaceContainerHigh,
                                                        topLeft = boxTopLeft,
                                                        size = boxSize,
                                                        cornerRadius = cornerRad
                                                    )

                                                    drawRoundRect(
                                                        color = if (isFocused) subjectAccent else outlineVariant.copy(alpha = 0.7f),
                                                        topLeft = boxTopLeft,
                                                        size = boxSize,
                                                        cornerRadius = cornerRad,
                                                        style = Stroke(width = if (isFocused) 2.5f * fitZoom else 1.2f * fitZoom)
                                                    )

                                                    drawContext.canvas.nativeCanvas.apply {
                                                        val padX = 11f * fitZoom
                                                        val availTextWidth = (nodeWidth - padX * 2f).toInt().coerceAtLeast(36)

                                                        val titlePaint = TextPaint().apply {
                                                            color = if (isFocused) onPrimaryContainer.toArgb() else onSurface.toArgb()
                                                            textSize = (11.5f * density.density * fitZoom).coerceIn(10f, 30f)
                                                            isFakeBoldText = true
                                                            isAntiAlias = true
                                                        }
                                                        val formulaPaint = TextPaint().apply {
                                                            color = if (isFocused) subjectAccent.toArgb() else onSurface.toArgb()
                                                            textSize = (10.5f * density.density * fitZoom).coerceIn(9f, 26f)
                                                            typeface = android.graphics.Typeface.MONOSPACE
                                                            isFakeBoldText = true
                                                            isAntiAlias = true
                                                        }
                                                        val subPaint = TextPaint().apply {
                                                            color = if (isFocused) onPrimaryContainer.copy(alpha = 0.8f).toArgb() else onSurfaceVariant.toArgb()
                                                            textSize = (9f * density.density * fitZoom).coerceIn(8f, 22f)
                                                            isAntiAlias = true
                                                        }

                                                        save()
                                                        clipRect(nodeX, nodeY, nodeX + nodeWidth, nodeY + nodeHeight)
                                                        var cursorY = nodeY + (7f * fitZoom)

                                                        val titleLayout = StaticLayout.Builder
                                                            .obtain(obj.label, 0, obj.label.length, titlePaint, availTextWidth)
                                                            .setMaxLines(2)
                                                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                                            .build()
                                                        save()
                                                        translate(nodeX + padX, cursorY)
                                                        titleLayout.draw(this)
                                                        restore()
                                                        cursorY += titleLayout.height + (3f * fitZoom)

                                                        if (obj.formula.isNotBlank() && cursorY < nodeY + nodeHeight - 8f) {
                                                            val formulaLayout = StaticLayout.Builder
                                                                .obtain(obj.formula, 0, obj.formula.length, formulaPaint, availTextWidth)
                                                                .setMaxLines(2)
                                                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                                                .build()
                                                            save()
                                                            translate(nodeX + padX, cursorY)
                                                            formulaLayout.draw(this)
                                                            restore()
                                                            cursorY += formulaLayout.height + (2f * fitZoom)
                                                        }

                                                        if (obj.subLabel.isNotBlank() && cursorY < nodeY + nodeHeight - 6f) {
                                                            val subLayout = StaticLayout.Builder
                                                                .obtain(obj.subLabel, 0, obj.subLabel.length, subPaint, availTextWidth)
                                                                .setMaxLines(2)
                                                                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                                                                .build()
                                                            save()
                                                            translate(nodeX + padX, cursorY)
                                                            subLayout.draw(this)
                                                            restore()
                                                        }
                                                        restore()
                                                    }
                                                }
                                            }

                                            // Helpful hint badge
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = "Tap any card to inspect • Drag or pinch",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    zoomScale = 1.0f
                                                    panOffset = Offset.Zero
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(6.dp)
                                                    .size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CenterFocusStrong,
                                                    contentDescription = "Reset Canvas",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Full Screen Bottom Action Bar to Switch to Quiz Stage
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding(),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Understood Step ${currentStepIndex + 1}?",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Take Skill Checkpoint",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Button(
                                    onClick = {
                                        screenStage = SocraticTeachingScreenStage.FULL_SCREEN_QUIZ
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.height(46.dp)
                                ) {
                                    Text("TEST UNDERSTANDING", fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        }
                    }
                }

                SocraticTeachingScreenStage.FULL_SCREEN_QUIZ -> {
                    // ─────────────────────────────────────────────────────────────
                    // STAGE 2: FULL-SCREEN DYNAMIC SOCRATIC CHECKPOINT QUIZ
                    // ─────────────────────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // Top Nav Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    screenStage = SocraticTeachingScreenStage.FULL_SCREEN_BOARD
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Visual Board")
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (activeDynamicQuiz?.isAiGenerated == true)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (activeDynamicQuiz?.isAiGenerated == true) "AI-Driven Checkpoint" else "Skill Invariant Checkpoint",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Segmented Progress Bar
                        BrilliantSegmentedProgressBar(
                            currentStepIndex = currentStepIndex,
                            totalSteps = totalSteps,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quiz Content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Step ${currentStepIndex + 1} Checkpoint",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            if (isLoadingQuiz) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                        Text(
                                            text = "Synthesizing dynamic Socratic challenge…",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                val quiz = activeDynamicQuiz
                                if (quiz != null) {
                                    Text(
                                        text = quiz.question,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 28.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        quiz.options.forEachIndexed { optIndex, optionText ->
                                            val isSelected = selectedQuizOption == optIndex

                                            val containerColor by animateColorAsState(
                                                targetValue = when {
                                                    quizCheckState == QuizCheckState.CORRECT && isSelected ->
                                                        Color(0xFF00C853).copy(alpha = 0.12f)
                                                    quizCheckState == QuizCheckState.INCORRECT && isSelected ->
                                                        Color(0xFFFF5252).copy(alpha = 0.12f)
                                                    isSelected ->
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                                    else ->
                                                        MaterialTheme.colorScheme.surfaceContainerLow
                                                },
                                                label = "quiz_opt_bg_$optIndex"
                                            )

                                            val borderColor by animateColorAsState(
                                                targetValue = when {
                                                    quizCheckState == QuizCheckState.CORRECT && isSelected ->
                                                        Color(0xFF00C853)
                                                    quizCheckState == QuizCheckState.INCORRECT && isSelected ->
                                                        Color(0xFFFF5252)
                                                    isSelected ->
                                                        MaterialTheme.colorScheme.primary
                                                    else ->
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                                },
                                                label = "quiz_opt_border_$optIndex"
                                            )

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .bouncyClickable {
                                                        selectedQuizOption = optIndex
                                                        if (quizCheckState == QuizCheckState.INCORRECT) {
                                                            quizCheckState = QuizCheckState.UNCHECKED
                                                        }
                                                    },
                                                shape = RoundedCornerShape(18.dp),
                                                color = containerColor,
                                                border = BorderStroke(if (isSelected) 2.dp else 1.2.dp, borderColor)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(
                                                                if (isSelected) borderColor.copy(alpha = 0.2f)
                                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                                            )
                                                            .border(
                                                                1.dp,
                                                                if (isSelected) borderColor else MaterialTheme.colorScheme.outlineVariant,
                                                                RoundedCornerShape(8.dp)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = "${('A' + optIndex)}",
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 15.sp,
                                                            color = if (isSelected) borderColor else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }

                                                    Text(
                                                        text = optionText,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.weight(1f)
                                                    )

                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = when (quizCheckState) {
                                                                QuizCheckState.CORRECT -> Icons.Default.CheckCircle
                                                                QuizCheckState.INCORRECT -> Icons.Default.Close
                                                                QuizCheckState.UNCHECKED -> Icons.Default.Check
                                                            },
                                                            contentDescription = null,
                                                            tint = borderColor,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Pinned Duolingo Action Tray
                        val stepMisconceptionHint = activeDynamicQuiz?.misconceptionRationale?.ifBlank {
                            currentStep?.tutorTip?.ifBlank { "Review Step ${currentStepIndex + 1} intuition and try another option." }
                        } ?: "Review Step ${currentStepIndex + 1} intuition and try another option."

                        DuolingoBottomActionTray(
                            checkState = quizCheckState,
                            isOptionSelected = selectedQuizOption != null,
                            explanation = activeDynamicQuiz?.explanation ?: "",
                            incorrectHint = stepMisconceptionHint,
                            isFinalStep = currentStepIndex >= totalSteps - 1,
                            onCheck = {
                                val quiz = activeDynamicQuiz ?: return@DuolingoBottomActionTray
                                val isCorrect = selectedQuizOption == quiz.correctIndex
                                if (isCorrect) {
                                    quizCheckState = QuizCheckState.CORRECT
                                    tutorState = TutorState.CELEBRATING
                                    speakNarration("Correct! ${quiz.explanation}")
                                } else {
                                    quizCheckState = QuizCheckState.INCORRECT
                                    tutorState = TutorState.HINT
                                    speakNarration("Not quite. $stepMisconceptionHint")
                                }
                            },
                            onContinue = {
                                LessonProgressStore.recordStepCompletion(
                                    context = context,
                                    topicId = activeBundle?.topicId ?: selectedTopicEntry?.topicId.orEmpty(),
                                    completedStepIndex = currentStepIndex,
                                    totalSteps = totalSteps
                                )
                                if (currentStepIndex < totalSteps - 1) {
                                    currentStepIndex++
                                    selectedQuizOption = null
                                    quizCheckState = QuizCheckState.UNCHECKED
                                    screenStage = SocraticTeachingScreenStage.FULL_SCREEN_BOARD
                                } else {
                                    screenStage = SocraticTeachingScreenStage.LESSON_MASTERED
                                }
                            },
                            onTryAgain = {
                                quizCheckState = QuizCheckState.UNCHECKED
                            }
                        )
                    }
                }

                SocraticTeachingScreenStage.LESSON_MASTERED -> {
                    // ─────────────────────────────────────────────────────────────
                    // STAGE 3: FULL CELEBRATION MASTERY CARD
                    // ─────────────────────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            border = BorderStroke(2.dp, Color(0xFF00C853))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00C853).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Completed",
                                        tint = Color(0xFF00C853),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Text(
                                    text = "Concept Mastered!",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "Brilliant job, ${studentProfile.name}! You've demonstrated full mastery across all $totalSteps steps of ${selectedTopicEntry?.title ?: "this module"}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = { handleLessonBack() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (openedFromCatalog) "BACK TO ALL COURSES" else "RETURN TO LEARNING DASHBOARD"
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        currentStepIndex = 0
                                        selectedQuizOption = null
                                        quizCheckState = QuizCheckState.UNCHECKED
                                        screenStage = SocraticTeachingScreenStage.FULL_SCREEN_BOARD
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Replay This Socratic Lesson")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Lottie Celebration Burst Overlay
        CelebrationBurstOverlay(
            visible = tutorState == TutorState.CELEBRATING || screenStage == SocraticTeachingScreenStage.LESSON_MASTERED,
            modifier = Modifier.fillMaxSize()
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // ─────────────────────────────────────────────────────────────
    // Node Inspector Bottom Sheet
    // ─────────────────────────────────────────────────────────────
    inspectedNode?.let { node ->
        ModalBottomSheet(
            onDismissRequest = { inspectedNode = null },
            sheetState = nodeSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = node.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (node.subLabel.isNotBlank()) {
                            Text(
                                text = node.subLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { inspectedNode = null }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (node.formula.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = node.formula,
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Text(
                    text = node.explanation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Button(
                    onClick = { speakNarration(node.explanation) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Hear Tutor Explanation")
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // SKILL.md & Context Inspector Sheet
    // ─────────────────────────────────────────────────────────────
    val bundleForInspector = activeBundle
    val subjForInspector = selectedSubject
    val chapForInspector = selectedChapter
    val stepForInspector = currentStep

    if (showSkillInspectorSheet && bundleForInspector != null && subjForInspector != null && chapForInspector != null && stepForInspector != null) {
        ModalBottomSheet(
            onDismissRequest = { showSkillInspectorSheet = false },
            sheetState = inspectorSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "3-Layer Skill Package & Step Context",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Module ID: ${bundleForInspector.topicId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Step-Scoped Prompt (Step ${stepForInspector.stepNumber})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = SubjectSkillCatalog.buildStepScopedPrompt(
                                subject = subjForInspector,
                                chapter = chapForInspector,
                                bundle = bundleForInspector,
                                step = stepForInspector,
                                studentQuestion = "Explain the invariant for this step."
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (bundleForInspector.skillMarkdown.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "SKILL.md Orchestrator Rules",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = bundleForInspector.skillMarkdown,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun BrilliantSegmentedProgressBar(
    currentStepIndex: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isCurrentOrCompleted = i <= currentStepIndex
            val progressTarget = if (isCurrentOrCompleted) 1f else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = progressTarget,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                label = "seg_progress_$i"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (i == currentStepIndex) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                )
            }
        }
    }
}

@Composable
private fun DuolingoBottomActionTray(
    checkState: QuizCheckState,
    isOptionSelected: Boolean,
    explanation: String,
    incorrectHint: String,
    isFinalStep: Boolean,
    onCheck: () -> Unit,
    onContinue: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = when (checkState) {
            QuizCheckState.CORRECT -> Color(0xFF00C853).copy(alpha = 0.12f)
            QuizCheckState.INCORRECT -> Color(0xFFFF5252).copy(alpha = 0.12f)
            QuizCheckState.UNCHECKED -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = 8.dp,
        border = BorderStroke(
            1.dp,
            when (checkState) {
                QuizCheckState.CORRECT -> Color(0xFF00C853).copy(alpha = 0.5f)
                QuizCheckState.INCORRECT -> Color(0xFFFF5252).copy(alpha = 0.5f)
                QuizCheckState.UNCHECKED -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = checkState != QuizCheckState.UNCHECKED,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (checkState == QuizCheckState.CORRECT) Icons.Default.CheckCircle
                        else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (checkState == QuizCheckState.CORRECT) Color(0xFF00C853) else Color(0xFFFF5252),
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = if (checkState == QuizCheckState.CORRECT) "Brilliant! You got it right."
                            else "Not quite yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (checkState == QuizCheckState.CORRECT) Color(0xFF00C853) else Color(0xFFFF5252)
                        )
                        if (explanation.isNotBlank() && checkState == QuizCheckState.CORRECT) {
                            Text(
                                text = explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else if (checkState == QuizCheckState.INCORRECT) {
                            Text(
                                text = incorrectHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            when (checkState) {
                QuizCheckState.UNCHECKED -> {
                    Button(
                        onClick = onCheck,
                        enabled = isOptionSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Text(
                            text = "CHECK ANSWER",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                QuizCheckState.CORRECT -> {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00C853)
                        )
                    ) {
                        Text(
                            text = if (isFinalStep) "COMPLETE LESSON ➔" else "CONTINUE TO NEXT STEP ➔",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
                QuizCheckState.INCORRECT -> {
                    Button(
                        onClick = onTryAgain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5252)
                        )
                    ) {
                        Text(
                            text = "TRY AGAIN",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawWhiteboardGrid(gridColor: Color) {
    val step = 32.dp.toPx()
    val width = size.width
    val height = size.height

    var x = 0f
    while (x < width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 0.8f
        )
        x += step
    }

    var y = 0f
    while (y < height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 0.8f
        )
        y += step
    }
}

private fun DrawScope.drawArrow(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    zoom: Float
) {
    drawLine(
        color = color,
        start = from,
        end = to,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    val angle = atan2(to.y - from.y, to.x - from.x)
    val headLen = 14f * zoom
    val arrowAngle = Math.PI / 6.0

    val p1 = Offset(
        x = (to.x - headLen * cos(angle - arrowAngle)).toFloat(),
        y = (to.y - headLen * sin(angle - arrowAngle)).toFloat()
    )
    val p2 = Offset(
        x = (to.x - headLen * cos(angle + arrowAngle)).toFloat(),
        y = (to.y - headLen * sin(angle + arrowAngle)).toFloat()
    )

    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path = path, color = color)
}

@Composable
private fun SkillFormulaAndObjectivesPanel(
    bundle: TopicSkillBundle,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val formulaResources: List<CuratedResource> = remember(bundle.topicId) {
        bundle.resources.filter { it.type == "formula_sheet" }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        formulaResources.forEach { sheet ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = sheet.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (sheet.summary.isNotBlank()) {
                        Text(
                            text = sheet.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = sheet.bodyText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        if (bundle.instructions.learningObjectives.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Module Learning Objectives",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    bundle.instructions.learningObjectives.forEach { obj ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00C853),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = obj,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (bundle.instructions.commonMisconceptions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Common Pitfalls to Avoid",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF8A65)
                    )
                    bundle.instructions.commonMisconceptions.forEach { pitfall ->
                        Text(
                            text = "• $pitfall",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
