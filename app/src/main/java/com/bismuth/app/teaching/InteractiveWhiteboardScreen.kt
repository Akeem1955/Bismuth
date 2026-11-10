package com.bismuth.app.teaching

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismuth.app.engine.BismuthEngine
import com.bismuth.app.engine.BismuthModelConfig
import com.bismuth.app.skills.ChapterSkillNode
import com.bismuth.app.skills.SkillInstallPrefs
import com.bismuth.app.skills.SubjectSkillCatalog
import com.bismuth.app.skills.SubjectSkillCoordinator
import com.bismuth.app.skills.TopicSkillPackage
import com.bismuth.app.ui.animation.LottieEffectView
import com.bismuth.app.ui.animation.RiveCharacterView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private data class CanvasHitTarget(
    val obj: BoardObject,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun parseColorHex(hex: String, fallback: Color = Color(0xFF38BDF8)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun InteractiveWhiteboardScreen(
    selectedModel: BismuthModelConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val subjects = remember { SubjectSkillCatalog.ALL_SUBJECTS }
    var activeSubject by remember { mutableStateOf(subjects.first()) }
    var activeChapter by remember(activeSubject) { mutableStateOf(activeSubject.chapters.first()) }
    var activeTopic by remember(activeChapter) { mutableStateOf(activeChapter.topics.first()) }

    val installedState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            subjects.forEach { subj ->
                subj.chapters.forEach { ch ->
                    ch.topics.forEach { topic ->
                        put(topic.topicId, SkillInstallPrefs.isTopicInstalled(context, topic))
                    }
                }
            }
        }
    }
    var downloadingTopicId by remember { mutableStateOf<String?>(null) }

    val boardScene = activeTopic.whiteboardScene
    var currentStepIndex by remember(activeTopic) { mutableIntStateOf(0) }
    val currentStep = boardScene.steps.getOrElse(currentStepIndex) { boardScene.steps.first() }

    // Canvas pan & zoom
    var scale by remember(activeTopic) { mutableFloatStateOf(1f) }
    var offset by remember(activeTopic) { mutableStateOf(Offset.Zero) }

    // Selected node for tap-to-explain popup
    var tappedObject by remember(currentStepIndex, activeTopic) { mutableStateOf<BoardObject?>(null) }

    // Inline step quiz state (gated before advancing to next step)
    var showStepQuizModal by remember(currentStepIndex, activeTopic) { mutableStateOf(false) }
    var selectedQuizOptionIndex by remember(currentStepIndex, activeTopic) { mutableIntStateOf(-1) }
    var quizSubmitted by remember(currentStepIndex, activeTopic) { mutableStateOf(false) }
    val completedSteps = remember(activeTopic) { mutableStateMapOf<Int, Boolean>() }

    // Rive character emotion & Lottie celebration trigger
    var tutorEmotion by remember(currentStepIndex, activeTopic) {
        mutableStateOf(TutorRiveEmotion.IDLE)
    }
    var showCelebrationBurst by remember { mutableStateOf(false) }

    // Skill Inspector tab (0 = Live Whiteboard, 1 = Skill Architecture & Resources)
    var viewTab by remember { mutableIntStateOf(0) }

    // Follow-up tutor Q&A grounded in current step skill
    var studentQuestion by remember { mutableStateOf("") }
    var tutorReply by remember(currentStepIndex, activeTopic) { mutableStateOf<String?>(null) }
    var isAskingTutor by remember { mutableStateOf(false) }

    // Offline Android TTS
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.US
                ttsReady = true
            }
        }
        ttsEngine = instance
        onDispose {
            instance?.stop()
            instance?.shutdown()
        }
    }

    LaunchedEffect(showCelebrationBurst) {
        if (showCelebrationBurst) {
            delay(2200)
            showCelebrationBurst = false
        }
    }

    fun speakOut(text: String) {
        if (ttsReady) {
            tutorEmotion = TutorRiveEmotion.EXPLAINING
            ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bismuth_whiteboard_tts")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .padding(12.dp)
    ) {
        // 1. Subject Coordinator Bar (Mathematics, Physics, Chemistry, Biology, English)
        SubjectSelectorBar(
            subjects = subjects,
            selectedSubject = activeSubject,
            onSelectSubject = { subj ->
                activeSubject = subj
                activeChapter = subj.chapters.first()
                activeTopic = activeChapter.topics.first()
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. Chapter & Topic Skill Selector + GCS Package Status
        ChapterAndTopicBar(
            subject = activeSubject,
            selectedChapter = activeChapter,
            selectedTopic = activeTopic,
            isInstalled = installedState[activeTopic.topicId] == true,
            isDownloading = downloadingTopicId == activeTopic.topicId,
            viewTab = viewTab,
            onSelectChapter = { ch ->
                activeChapter = ch
                activeTopic = ch.topics.first()
            },
            onSelectTopic = { topic ->
                activeTopic = topic
            },
            onToggleInstallGcsSkill = {
                val id = activeTopic.topicId
                val currentlyInstalled = installedState[id] == true
                if (!currentlyInstalled) {
                    scope.launch {
                        downloadingTopicId = id
                        delay(750)
                        SkillInstallPrefs.setTopicInstalled(context, id, true)
                        installedState[id] = true
                        downloadingTopicId = null
                    }
                } else {
                    SkillInstallPrefs.setTopicInstalled(context, id, false)
                    installedState[id] = false
                }
            },
            onChangeViewTab = { viewTab = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (viewTab == 1) {
            SkillThreeLayerInspector(
                subject = activeSubject,
                chapter = activeChapter,
                topic = activeTopic,
                currentStep = currentStep,
                modifier = Modifier.weight(1f)
            )
        } else {
            // 3. Interactive Whiteboard Canvas + Tap-on-Node Popup + Inline Quiz + Rive Dock
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Interactive Canvas Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(315.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0F172A), Color(0xFF131D35))
                            )
                        )
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                ) {
                    val visibleObjects = remember(currentStepIndex, activeTopic) {
                        boardScene.visibleObjectsForStep(currentStepIndex)
                    }
                    val visibleArrows = remember(currentStepIndex, activeTopic) {
                        boardScene.visibleArrowsForStep(currentStepIndex)
                    }
                    val currentStepObjectIds = remember(currentStepIndex, activeTopic) {
                        currentStep.focusObjectIds.toSet()
                    }
                    val hitTargets = remember { mutableListOf<CanvasHitTarget>() }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentStepIndex, activeTopic) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(0.75f, 2.5f)
                                    offset += pan
                                }
                            }
                            .pointerInput(currentStepIndex, activeTopic, scale, offset) {
                                detectTapGestures { tapOffset ->
                                    val hit = hitTargets.lastOrNull { target ->
                                        tapOffset.x in target.left..target.right &&
                                            tapOffset.y in target.top..target.bottom
                                    }
                                    if (hit != null) {
                                        tappedObject = hit.obj
                                        tutorEmotion = TutorRiveEmotion.EXPLAINING
                                    } else {
                                        tappedObject = null
                                    }
                                }
                            }
                    ) {
                        drawWhiteboardGrid()
                        hitTargets.clear()

                        val objectBounds = mutableMapOf<String, CanvasHitTarget>()
                        visibleObjects.forEachIndexed { idx, obj ->
                            val isHighlighted = obj.objectId in currentStepObjectIds || tappedObject?.objectId == obj.objectId
                            val bounds = drawBoardNode(
                                obj = obj,
                                index = idx,
                                totalCount = visibleObjects.size,
                                scale = scale,
                                pan = offset,
                                isHighlighted = isHighlighted,
                                isSelected = tappedObject?.objectId == obj.objectId
                            )
                            hitTargets.add(bounds)
                            objectBounds[obj.objectId] = bounds
                        }

                        visibleArrows.forEach { arrow ->
                            val from = objectBounds[arrow.fromObjectId]
                            val to = objectBounds[arrow.toObjectId]
                            if (from != null && to != null) {
                                drawConnectorArrow(from, to, arrow)
                            }
                        }
                    }

                    // Top-left badge: Step indicator & Tap-a-node instruction
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC0F172A))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Step ${currentStepIndex + 1}/${boardScene.steps.size} • Tap any node for detailed explanation",
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Top-right reset zoom button
                    IconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                            tappedObject = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xAA1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Canvas Zoom",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Lottie Micro-Celebration Overlay when student passes inline step quiz
                    if (showCelebrationBurst) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(180.dp)
                        ) {
                            LottieEffectView(
                                assetName = "celebrate.json",
                                iterations = 1,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Pop-up explanation card when user taps a diagram node
                    tappedObject?.let { selectedNode ->
                        NodeExplanationPopup(
                            node = selectedNode,
                            onSpeak = {
                                speakOut("${selectedNode.label}. ${selectedNode.explanation}")
                            },
                            onDismiss = { tappedObject = null },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp)
                        )
                    }
                }

                // 4. Docked Rive Character + Step Narration + Step Navigation Controls
                RiveTutorDockCard(
                    step = currentStep,
                    stepIndex = currentStepIndex,
                    totalSteps = boardScene.steps.size,
                    tutorEmotion = tutorEmotion,
                    isStepQuizCompleted = completedSteps[currentStepIndex] == true,
                    onSpeakStep = { speakOut(currentStep.narration) },
                    onPreviousStep = {
                        if (currentStepIndex > 0) {
                            currentStepIndex--
                        }
                    },
                    onNextOrQuizStep = {
                        val quiz = currentStep.inlineQuiz
                        if (quiz != null && completedSteps[currentStepIndex] != true) {
                            showStepQuizModal = true
                            tutorEmotion = TutorRiveEmotion.THINKING
                        } else if (currentStepIndex < boardScene.steps.lastIndex) {
                            currentStepIndex++
                        }
                    }
                )

                // 5. Inline Step Quiz Card (Gated before advancing, adapted to Model Tier)
                AnimatedVisibility(visible = showStepQuizModal && currentStep.inlineQuiz != null) {
                    currentStep.inlineQuiz?.let { inlineQuiz ->
                        val isHighTier = selectedModel.tier.isHighCapability
                        InlineStepQuizCard(
                            quiz = inlineQuiz,
                            modelDisplayName = selectedModel.displayName,
                            isHighCapabilityTier = isHighTier,
                            selectedOptionIndex = selectedQuizOptionIndex,
                            isSubmitted = quizSubmitted,
                            onSelectOption = { idx ->
                                selectedQuizOptionIndex = idx
                                quizSubmitted = false
                            },
                            onSubmitAnswer = {
                                val targetCorrect = if (isHighTier) {
                                    inlineQuiz.advancedCorrectIndex
                                } else {
                                    inlineQuiz.basicCorrectIndex
                                }
                                if (selectedQuizOptionIndex >= 0) {
                                    quizSubmitted = true
                                    if (selectedQuizOptionIndex == targetCorrect) {
                                        completedSteps[currentStepIndex] = true
                                        tutorEmotion = TutorRiveEmotion.CELEBRATING
                                        showCelebrationBurst = true
                                    } else {
                                        tutorEmotion = TutorRiveEmotion.ENCOURAGING
                                    }
                                }
                            },
                            onContinueToNextStep = {
                                showStepQuizModal = false
                                if (currentStepIndex < boardScene.steps.lastIndex) {
                                    currentStepIndex++
                                }
                            }
                        )
                    }
                }

                // 6. Ask AI Tutor About This Step (Step-Scoped Prompting)
                AskStepTutorBar(
                    question = studentQuestion,
                    onQuestionChange = { studentQuestion = it },
                    isAsking = isAskingTutor,
                    tutorReply = tutorReply,
                    onAsk = {
                        val q = studentQuestion.trim()
                        if (q.isEmpty() || isAskingTutor) return@AskStepTutorBar
                        scope.launch {
                            isAskingTutor = true
                            tutorEmotion = TutorRiveEmotion.THINKING
                            val scopedPrompt = SubjectSkillCatalog.buildStepScopedPrompt(
                                subject = activeSubject,
                                chapter = activeChapter,
                                topic = activeTopic,
                                step = currentStep,
                                studentQuestion = q
                            )
                            val modelAnswer = BismuthEngine.generateTextOrNull(
                                context = context,
                                prompt = scopedPrompt,
                                config = selectedModel
                            )
                            tutorReply = modelAnswer ?: buildDeterministicStepAnswer(
                                topic = activeTopic,
                                step = currentStep,
                                question = q
                            )
                            tutorEmotion = TutorRiveEmotion.EXPLAINING
                            isAskingTutor = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SubjectSelectorBar(
    subjects: List<SubjectSkillCoordinator>,
    selectedSubject: SubjectSkillCoordinator,
    onSelectSubject: (SubjectSkillCoordinator) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        subjects.forEach { subject ->
            val selected = subject.subjectId == selectedSubject.subjectId
            val accent = parseColorHex(subject.accentHex)
            Surface(
                onClick = { onSelectSubject(subject) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) accent.copy(alpha = 0.22f) else Color(0xFF151F32),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) accent else Color(0xFF26334D)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${subject.iconEmoji} ${subject.title}",
                        color = if (selected) Color.White else Color(0xFFCBD5E1),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = subject.tagline,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterAndTopicBar(
    subject: SubjectSkillCoordinator,
    selectedChapter: ChapterSkillNode,
    selectedTopic: TopicSkillPackage,
    isInstalled: Boolean,
    isDownloading: Boolean,
    viewTab: Int,
    onSelectChapter: (ChapterSkillNode) -> Unit,
    onSelectTopic: (TopicSkillPackage) -> Unit,
    onToggleInstallGcsSkill: () -> Unit,
    onChangeViewTab: (Int) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chapters:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                subject.chapters.forEach { chapter ->
                    FilterChip(
                        selected = chapter.chapterId == selectedChapter.chapterId,
                        onClick = { onSelectChapter(chapter) },
                        label = { Text(chapter.title, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF1E3A8A),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Topic Skill:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                selectedChapter.topics.forEach { topic ->
                    FilterChip(
                        selected = topic.topicId == selectedTopic.topicId,
                        onClick = { onSelectTopic(topic) },
                        label = { Text(topic.title, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF0284C7),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // GCS Offline Skill Package Status
                Surface(
                    onClick = onToggleInstallGcsSkill,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isInstalled) Color(0xFF064E3B) else Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF38BDF8)
                            )
                        } else {
                            Icon(
                                imageVector = if (isInstalled) Icons.Default.CloudDone else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (isInstalled) Color(0xFF34D399) else Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDownloading) {
                                "Syncing GCS Skill..."
                            } else if (isInstalled) {
                                "Installed Offline (${selectedTopic.packageSizeKb} KB)"
                            } else {
                                "Download Skill (${selectedTopic.packageSizeKb} KB)"
                            },
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }

                // Switch between Interactive Board and 3-Layer Skill Package Architecture
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = viewTab == 0,
                        onClick = { onChangeViewTab(0) },
                        label = { Text("Whiteboard", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = viewTab == 1,
                        onClick = { onChangeViewTab(1) },
                        label = { Text("Skill Package (3 Layers)", fontSize = 11.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeExplanationPopup(
    node: BoardObject,
    onSpeak: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nodeAccent = parseColorHex(node.style.strokeColor)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF21E293B)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, nodeAccent)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(nodeAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = node.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (node.subLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${node.subLabel})",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }
                Row {
                    IconButton(onClick = onSpeak, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Speak Node Explanation",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Node Explanation",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = node.explanation,
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            if (node.keyFormulaOrRule.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A)
                ) {
                    Text(
                        text = "Key Rule / Formula: ${node.keyFormulaOrRule}",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RiveTutorDockCard(
    step: BoardStep,
    stepIndex: Int,
    totalSteps: Int,
    tutorEmotion: TutorRiveEmotion,
    isStepQuizCompleted: Boolean,
    onSpeakStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onNextOrQuizStep: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rive_tutor_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val emotionAccent = when (tutorEmotion) {
        TutorRiveEmotion.IDLE -> Color(0xFF38BDF8)
        TutorRiveEmotion.EXPLAINING -> Color(0xFF22C55E)
        TutorRiveEmotion.THINKING -> Color(0xFFF59E0B)
        TutorRiveEmotion.CELEBRATING -> Color(0xFFA855F7)
        TutorRiveEmotion.ENCOURAGING -> Color(0xFFEC4899)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = androidx.compose.foundation.BorderStroke(1.dp, emotionAccent.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Docked Rive Character Avatar Box
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F172A))
                        .border(2.dp, emotionAccent, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    RiveCharacterView(
                        modifier = Modifier.fillMaxSize(),
                        stateMachineName = "TutorStateMachine",
                        booleanInputs = mapOf(
                            "isSpeaking" to (tutorEmotion == TutorRiveEmotion.EXPLAINING),
                            "isThinking" to (tutorEmotion == TutorRiveEmotion.THINKING),
                            "isCelebrating" to (tutorEmotion == TutorRiveEmotion.CELEBRATING)
                        )
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size((26 * pulseScale).dp)
                                .clip(CircleShape)
                                .background(emotionAccent.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Docked Rive AI Tutor",
                                tint = emotionAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = tutorEmotion.label,
                            color = emotionAccent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = step.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onSpeakStep, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Narrate Step",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = step.narration,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPreviousStep,
                    enabled = stepIndex > 0
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Prev Step", fontSize = 12.sp)
                }

                if (isStepQuizCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Checkpoint Passed",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = onNextOrQuizStep,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (step.inlineQuiz != null && !isStepQuizCompleted) {
                            Color(0xFF7C3AED)
                        } else {
                            Color(0xFF0284C7)
                        }
                    )
                ) {
                    val needsQuiz = step.inlineQuiz != null && !isStepQuizCompleted
                    Icon(
                        imageVector = if (needsQuiz) Icons.Default.Quiz else Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            needsQuiz -> "Step Check Quiz"
                            stepIndex < totalSteps - 1 -> "Next Step"
                            else -> "Lesson Complete"
                        },
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineStepQuizCard(
    quiz: StepInlineQuiz,
    modelDisplayName: String,
    isHighCapabilityTier: Boolean,
    selectedOptionIndex: Int,
    isSubmitted: Boolean,
    onSelectOption: (Int) -> Unit,
    onSubmitAnswer: () -> Unit,
    onContinueToNextStep: () -> Unit
) {
    val prompt = if (isHighCapabilityTier) quiz.advancedQuestion else quiz.basicQuestion
    val options = if (isHighCapabilityTier) quiz.advancedOptions else quiz.basicOptions
    val correctIndex = if (isHighCapabilityTier) quiz.advancedCorrectIndex else quiz.basicCorrectIndex
    val explanation = if (isHighCapabilityTier) quiz.advancedExplanation else quiz.basicExplanation
    val isCorrect = isSubmitted && selectedOptionIndex == correctIndex

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181830)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B5CF6)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inline Step Checkpoint",
                    color = Color(0xFFC4B5FD),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isHighCapabilityTier) Color(0xFF312E81) else Color(0xFF1E293B)
                ) {
                    Text(
                        text = if (isHighCapabilityTier) {
                            "Analytical Mode ($modelDisplayName)"
                        } else {
                            "Foundational Mode ($modelDisplayName)"
                        },
                        color = Color(0xFF93C5FD),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = prompt,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            options.forEachIndexed { idx, option ->
                val isSelected = selectedOptionIndex == idx
                val borderColor = when {
                    isSubmitted && idx == correctIndex -> Color(0xFF10B981)
                    isSubmitted && isSelected && idx != correctIndex -> Color(0xFFEF4444)
                    isSelected -> Color(0xFF38BDF8)
                    else -> Color(0xFF334155)
                }
                Surface(
                    onClick = { onSelectOption(idx) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Text(
                        text = "${('A' + idx)}. $option",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (isSubmitted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isCorrect) {
                        "Correct! $explanation"
                    } else {
                        "Not quite: $explanation"
                    },
                    color = if (isCorrect) Color(0xFF34D399) else Color(0xFFFCA5A5),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isCorrect) {
                    Button(
                        onClick = onSubmitAnswer,
                        enabled = selectedOptionIndex >= 0
                    ) {
                        Text("Check Answer", fontSize = 12.sp)
                    }
                } else {
                    Button(
                        onClick = onContinueToNextStep,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Continue to Next Step", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AskStepTutorBar(
    question: String,
    onQuestionChange: (String) -> Unit,
    isAsking: Boolean,
    tutorReply: String?,
    onAsk: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Ask AI Tutor About This Step (Grounded in Topic Skill)",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = onQuestionChange,
                    placeholder = {
                        Text(
                            "e.g., Why does this step work that way?",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    },
                    singleLine = true,
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
                    onClick = onAsk,
                    enabled = !isAsking && question.isNotBlank(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0284C7))
                ) {
                    if (isAsking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Ask Step Tutor",
                            tint = Color.White
                        )
                    }
                }
            }

            if (!tutorReply.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tutorReply,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillThreeLayerInspector(
    subject: SubjectSkillCoordinator,
    chapter: ChapterSkillNode,
    topic: TopicSkillPackage,
    currentStep: BoardStep,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Subject Skill Coordinator • ${subject.title}",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = subject.coordinatorPhilosophy,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "GCS Package Path: ${topic.gcsObjectPath}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Layer 1 • Skill Instructions (How to Teach)",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Learning Objectives:",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    topic.instructions.learningObjectives.forEach { obj ->
                        Text("• $obj", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Common Misconceptions to Address:",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    topic.instructions.commonMisconceptions.forEach { misc ->
                        Text("• $misc", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Layer 2 • Curated Learning Resources (Offline Knowledge)",
                        color = Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    topic.resources.forEach { res ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(res.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(res.summary, color = Color(0xFF93C5FD), fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(res.bodyText, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Layer 3 • Interactive Components & Step-Scoped Prompt Preview",
                        color = Color(0xFFC084FC),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val previewPrompt = SubjectSkillCatalog.buildStepScopedPrompt(
                        subject = subject,
                        chapter = chapter,
                        topic = topic,
                        step = currentStep,
                        studentQuestion = "Explain the core principle of this step."
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = previewPrompt,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawWhiteboardGrid() {
    val gridSpacing = 28.dp.toPx()
    val gridColor = Color(0xFF1E293B).copy(alpha = 0.55f)
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += gridSpacing
    }
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += gridSpacing
    }
}

private fun DrawScope.drawBoardNode(
    obj: BoardObject,
    index: Int,
    totalCount: Int,
    scale: Float,
    pan: Offset,
    isHighlighted: Boolean,
    isSelected: Boolean
): CanvasHitTarget {
    // Normalize virtual 720x520 board coordinates onto the canvas viewport
    val normX = (obj.position.x / 700f).coerceIn(0.08f, 0.72f)
    val normY = (obj.position.y / 520f).coerceIn(0.12f, 0.78f)
    val boxWidth = (158.dp.toPx() * scale).coerceIn(110f, 300f)
    val boxHeight = (62.dp.toPx() * scale).coerceIn(48f, 125f)

    val centerX = ((normX * (size.width - boxWidth)) + boxWidth / 2f) * scale + pan.x
    val centerY = ((normY * (size.height - boxHeight)) + boxHeight / 2f) * scale + pan.y

    val left = centerX - boxWidth / 2f
    val top = centerY - boxHeight / 2f
    val right = centerX + boxWidth / 2f
    val bottom = centerY + boxHeight / 2f

    val nodeColor = parseColorHex(obj.style.strokeColor)
    val fillBrush = Brush.verticalGradient(
        colors = listOf(
            nodeColor.copy(alpha = if (isSelected) 0.38f else 0.22f),
            Color(0xFF0F172A).copy(alpha = 0.94f)
        ),
        startY = top,
        endY = bottom
    )

    drawRoundRect(
        brush = fillBrush,
        topLeft = Offset(left, top),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
    )

    drawRoundRect(
        color = if (isSelected) Color.White else nodeColor,
        topLeft = Offset(left, top),
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
        style = Stroke(width = if (isSelected) 3.dp.toPx() else if (isHighlighted) 2.dp.toPx() else 1.2.dp.toPx())
    )

    drawContext.canvas.nativeCanvas.apply {
        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = (12.5f * scale).sp.toPx()
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val subPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(186, 230, 253)
            textSize = (10.5f * scale).sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        val labelText = if (obj.label.length > 22) obj.label.take(21) + "…" else obj.label
        drawText(labelText, centerX, centerY - (4.dp.toPx() * scale), titlePaint)

        if (obj.subLabel.isNotBlank()) {
            val subText = if (obj.subLabel.length > 24) obj.subLabel.take(23) + "…" else obj.subLabel
            drawText(subText, centerX, centerY + (13.dp.toPx() * scale), subPaint)
        }
    }

    return CanvasHitTarget(obj = obj, left = left, top = top, right = right, bottom = bottom)
}

private fun DrawScope.drawConnectorArrow(
    from: CanvasHitTarget,
    to: CanvasHitTarget,
    arrow: BoardArrow
) {
    val start = Offset((from.left + from.right) / 2f, (from.top + from.bottom) / 2f)
    val end = Offset((to.left + to.right) / 2f, (to.top + to.bottom) / 2f)

    val angle = atan2(end.y - start.y, end.x - start.x)
    val startPad = 34.dp.toPx()
    val endPad = 38.dp.toPx()

    val clippedStart = Offset(
        start.x + cos(angle) * startPad,
        start.y + sin(angle) * startPad
    )
    val clippedEnd = Offset(
        end.x - cos(angle) * endPad,
        end.y - sin(angle) * endPad
    )

    val arrowColor = parseColorHex(arrow.colorHex)
    drawLine(
        color = arrowColor,
        start = clippedStart,
        end = clippedEnd,
        strokeWidth = 2.2.dp.toPx(),
        cap = StrokeCap.Round
    )

    val headLength = 10.dp.toPx()
    val headAngle = Math.toRadians(28.0).toFloat()
    val path = Path().apply {
        moveTo(clippedEnd.x, clippedEnd.y)
        lineTo(
            clippedEnd.x - headLength * cos(angle - headAngle),
            clippedEnd.y - headLength * sin(angle - headAngle)
        )
        lineTo(
            clippedEnd.x - headLength * cos(angle + headAngle),
            clippedEnd.y - headLength * sin(angle + headAngle)
        )
        close()
    }
    drawPath(path = path, color = arrowColor)

    if (arrow.label.isNotBlank()) {
        val midX = (clippedStart.x + clippedEnd.x) / 2f
        val midY = (clippedStart.y + clippedEnd.y) / 2f - 6.dp.toPx()
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.rgb(226, 232, 240)
                textSize = 10.sp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText(arrow.label, midX, midY, paint)
        }
    }
}

private fun buildDeterministicStepAnswer(
    topic: TopicSkillPackage,
    step: BoardStep,
    question: String
): String {
    val focusedNodes = topic.whiteboardScene.objects.filter { it.objectId in step.focusObjectIds }
    val nodeHighlights = focusedNodes.joinToString("; ") { obj ->
        "${obj.label}: ${obj.explanation} (${obj.keyFormulaOrRule})"
    }
    return "Step Grounded Answer (${topic.title} — ${step.title}):\n" +
        "• Core Principle: ${step.narration}\n" +
        "• Diagram Breakdown: $nodeHighlights\n" +
        "• Direct Takeaway for \"$question\": ${topic.resources.firstOrNull()?.summary ?: topic.summary}"
}
