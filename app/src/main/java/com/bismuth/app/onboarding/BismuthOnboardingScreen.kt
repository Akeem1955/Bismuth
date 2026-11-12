package com.bismuth.app.onboarding

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismuth.app.R
import com.bismuth.app.engine.BismuthModelConfig
import com.bismuth.app.engine.DeviceHardwareProfile
import com.bismuth.app.profile.StudentProfilePrefs
import com.bismuth.app.ui.animation.AIThinkingOrb
import com.bismuth.app.ui.animation.CelebrationBurstOverlay
import com.bismuth.app.ui.animation.SpringSpecs
import com.bismuth.app.ui.animation.bouncyClickable
import com.bismuth.app.ui.animation.reactiveSpringScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TOTAL_ONBOARDING_STEPS = 5

private data class OptionMeta(
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color
)

/**
 * High-engagement, spring-animated 5-step onboarding studio for Bismuth.
 */
@Composable
fun BismuthOnboardingScreen(
    hardwareProfile: DeviceHardwareProfile,
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(0) }
    var studentName by remember { mutableStateOf("") }
    var selectedLevel by remember {
        mutableStateOf(
            StudentProfilePrefs.ACADEMIC_LEVELS.getOrElse(2) {
                StudentProfilePrefs.ACADEMIC_LEVELS.first()
            }
        )
    }
    var selectedSubject by remember { mutableStateOf(StudentProfilePrefs.PRIMARY_SUBJECTS.first()) }
    var selectedStyle by remember { mutableStateOf(StudentProfilePrefs.LEARNING_STYLES.first()) }
    var selectedLanguage by remember { mutableStateOf(StudentProfilePrefs.STUDY_LANGUAGES.first()) }
    var showCelebration by remember { mutableStateOf(false) }
    var isLaunching by remember { mutableStateOf(false) }

    val stepAccent by animateColorAsState(
        targetValue = when (currentStep) {
            0 -> Color(0xFF006874)
            1 -> Color(0xFF6D28D9)
            2 -> Color(0xFF0F766E)
            3 -> Color(0xFF2563EB)
            else -> Color(0xFFD97706)
        },
        animationSpec = tween(durationMillis = 480),
        label = "onboardingStepAccent"
    )

    LaunchedEffect(currentStep) {
        if (currentStep == TOTAL_ONBOARDING_STEPS - 1) {
            showCelebration = true
            delay(1_800L)
            if (!isLaunching) {
                showCelebration = false
            }
        }
    }

    fun persistProfile() {
        StudentProfilePrefs.saveProfile(
            context = context,
            name = studentName.trim().ifBlank { "Scholar" },
            academicLevel = selectedLevel,
            primarySubject = selectedSubject,
            learningStyle = selectedStyle,
            preferredLanguage = selectedLanguage
        )
    }

    fun launchWithCelebration() {
        if (isLaunching) return
        isLaunching = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        persistProfile()
        showCelebration = true
        scope.launch {
            delay(520L)
            onOnboardingComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        OnboardingAmbientCanvas(
            accentColor = stepAccent,
            currentStep = currentStep,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            OnboardingTopBar(
                currentStep = currentStep,
                totalSteps = TOTAL_ONBOARDING_STEPS,
                accentColor = stepAccent,
                onBack = {
                    if (currentStep > 0) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentStep--
                    }
                },
                onSelectStep = { target ->
                    if (target < currentStep) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentStep = target
                    }
                },
                onSkip = {
                    persistProfile()
                    onOnboardingComplete()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val goingForward = targetState >= initialState
                        val enterSlide = slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = 0.80f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { width -> if (goingForward) width / 2 else -width / 2 }
                        val exitSlide = slideOutHorizontally(
                            animationSpec = tween(220)
                        ) { width -> if (goingForward) -width / 3 else width / 3 }

                        (enterSlide + fadeIn(tween(240)) + scaleIn(initialScale = 0.94f, animationSpec = SpringSpecs.TactileBounce))
                            .togetherWith(exitSlide + fadeOut(tween(180)) + scaleOut(targetScale = 0.96f))
                            .using(SizeTransform(clip = false))
                    },
                    label = "onboardingStepContent"
                ) { step ->
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(vertical = 8.dp)
                    ) {
                        when (step) {
                            0 -> OnboardingWelcomeStep(
                                hardwareProfile = hardwareProfile,
                                accentColor = stepAccent
                            )
                            1 -> OnboardingNameStep(
                                studentName = studentName,
                                accentColor = stepAccent,
                                onNameChange = { studentName = it },
                                onNextStep = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentStep = 2
                                }
                            )
                            2 -> OnboardingLevelStep(
                                selectedLevel = selectedLevel,
                                onSelectLevel = { level ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedLevel = level
                                }
                            )
                            3 -> OnboardingSubjectStep(
                                selectedSubject = selectedSubject,
                                onSelectSubject = { subject ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedSubject = subject
                                }
                            )
                            else -> OnboardingStyleLaunchStep(
                                studentName = studentName,
                                selectedLevel = selectedLevel,
                                selectedSubject = selectedSubject,
                                selectedStyle = selectedStyle,
                                selectedLanguage = selectedLanguage,
                                hardwareProfile = hardwareProfile,
                                onSelectStyle = { style ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedStyle = style
                                },
                                onSelectLanguage = { lang ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedLanguage = lang
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TactilePrimaryCtaButton(
                label = when (currentStep) {
                    0 -> "Let's Personalize"
                    1 -> if (studentName.isBlank()) "Continue as Scholar" else "Nice to Meet You!"
                    2 -> "Lock Academic Stage"
                    3 -> "Choose Teaching Vibe"
                    else -> if (isLaunching) "Launching Studio..." else "Launch Bismuth"
                },
                accentColor = stepAccent,
                enabled = !isLaunching,
                isFinal = currentStep == TOTAL_ONBOARDING_STEPS - 1,
                onClick = {
                    if (currentStep < TOTAL_ONBOARDING_STEPS - 1) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentStep++
                    } else {
                        launchWithCelebration()
                    }
                }
            )
        }

        CelebrationBurstOverlay(
            visible = showCelebration,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun OnboardingAmbientCanvas(
    accentColor: Color,
    currentStep: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambientMotion")
    val driftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "driftPhase"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPulse"
    )

    val secondaryTint = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val shiftX = cos(driftPhase + currentStep * 0.7f) * (w * 0.08f)
        val shiftY = sin(driftPhase + currentStep * 0.5f) * (h * 0.05f)

        val topGlowCenter = Offset(w * 0.82f + shiftX, h * 0.16f + shiftY)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = 0.18f), Color.Transparent),
                center = topGlowCenter,
                radius = w * 0.65f * pulse
            ),
            radius = w * 0.65f * pulse,
            center = topGlowCenter
        )

        val bottomGlowCenter = Offset(w * 0.18f - shiftX, h * 0.82f - shiftY)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryTint.copy(alpha = 0.14f), Color.Transparent),
                center = bottomGlowCenter,
                radius = w * 0.58f
            ),
            radius = w * 0.58f,
            center = bottomGlowCenter
        )

        for (i in 0 until 6) {
            val angle = driftPhase + i * 1.05f
            val px = w * (0.14f + i * 0.15f) + cos(angle) * 26f
            val py = h * (0.20f + ((i * 19) % 55) / 100f) + sin(angle * 1.3f) * 22f
            drawCircle(
                color = accentColor.copy(alpha = 0.14f + (i % 3) * 0.04f),
                radius = (4f + (i % 3) * 2.5f).dp.toPx(),
                center = Offset(px, py)
            )
        }
    }
}

@Composable
private fun OnboardingTopBar(
    currentStep: Int,
    totalSteps: Int,
    accentColor: Color,
    onBack: () -> Unit,
    onSelectStep: (Int) -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.CenterStart) {
            androidx.compose.animation.AnimatedVisibility(
                visible = currentStep > 0,
                enter = fadeIn() + scaleIn(initialScale = 0.7f, animationSpec = SpringSpecs.TactileBounce),
                exit = fadeOut() + scaleOut(targetScale = 0.7f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.bouncyClickable(onClick = onBack)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous step",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp).size(20.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (idx in 0 until totalSteps) {
                val isCurrent = idx == currentStep
                val isDone = idx < currentStep
                val pillWidth by animateDpAsState(
                    targetValue = if (isCurrent) 34.dp else 10.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "stepPillWidth"
                )
                val pillColor by animateColorAsState(
                    targetValue = when {
                        isCurrent -> accentColor
                        isDone -> accentColor.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    },
                    animationSpec = tween(250),
                    label = "stepPillColor"
                )
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(pillWidth)
                        .clip(CircleShape)
                        .background(pillColor)
                        .clickable(enabled = isDone) { onSelectStep(idx) }
                )
            }
        }

        Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onSkip) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingWelcomeStep(
    hardwareProfile: DeviceHardwareProfile,
    accentColor: Color
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var mascotTaps by remember { mutableIntStateOf(0) }
    var voiceStage by remember { mutableIntStateOf(0) }
    var isVoicePlaying by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val voiceClips = remember {
        listOf(
            R.raw.bismuth_kame_stage_1,
            R.raw.bismuth_kame_stage_2,
            R.raw.bismuth_kame_stage_3
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                activePlayer?.stop()
                activePlayer?.release()
            }
            activePlayer = null
        }
    }

    fun triggerPowerUpVoice() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        mascotTaps++
        val clipRes = voiceClips[voiceStage % voiceClips.size]
        voiceStage++

        runCatching {
            activePlayer?.stop()
            activePlayer?.release()
        }
        activePlayer = null

        val player = runCatching { MediaPlayer.create(context, clipRes) }.getOrNull()
        if (player != null) {
            isVoicePlaying = true
            player.setOnCompletionListener { finished ->
                isVoicePlaying = false
                runCatching { finished.release() }
                if (activePlayer === finished) {
                    activePlayer = null
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                isVoicePlaying = false
                runCatching { failed.release() }
                if (activePlayer === failed) {
                    activePlayer = null
                }
                true
            }
            activePlayer = player
            runCatching { player.start() }.onFailure {
                isVoicePlaying = false
                runCatching { player.release() }
                activePlayer = null
            }
        }
    }

    val haloBoost by animateFloatAsState(
        targetValue = if (isVoicePlaying) 1.32f else 1.15f,
        animationSpec = SpringSpecs.GentleHarmonic,
        label = "haloEnergyBoost"
    )

    val tapSpin by animateFloatAsState(
        targetValue = mascotTaps * 360f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "mascotTapSpin"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "heroOrbit")
    val orbitDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitDeg"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heroFloatY"
    )

    val recommendedName = remember(hardwareProfile) {
        BismuthModelConfig.ALL.firstOrNull { it.modelId == hardwareProfile.recommendedModelId }?.displayName
            ?: "Qwen / Gemma Edge Engine"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(210.dp)
                .bouncyClickable(pressScale = 0.88f) { triggerPowerUpVoice() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension * 0.44f
                val innerRadius = size.minDimension * 0.34f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = if (isVoicePlaying) 0.38f else 0.24f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = outerRadius * haloBoost
                    ),
                    radius = outerRadius * haloBoost,
                    center = center
                )

                rotate(orbitDeg, pivot = center) {
                    drawArc(
                        color = accentColor.copy(alpha = 0.40f),
                        startAngle = 0f,
                        sweepAngle = 220f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                        size = androidx.compose.ui.geometry.Size(outerRadius * 2, outerRadius * 2)
                    )
                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = 6.dp.toPx(),
                        center = Offset(center.x + outerRadius, center.y)
                    )
                }

                rotate(-orbitDeg * 1.4f, pivot = center) {
                    drawArc(
                        color = Color(0xFF6D28D9).copy(alpha = 0.35f),
                        startAngle = 45f,
                        sweepAngle = 190f,
                        useCenter = false,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                        size = androidx.compose.ui.geometry.Size(innerRadius * 2, innerRadius * 2)
                    )
                    drawCircle(
                        color = accentColor,
                        radius = 4.5.dp.toPx(),
                        center = Offset(center.x, center.y - innerRadius)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 10.dp,
                border = BorderStroke(2.dp, accentColor.copy(alpha = 0.35f)),
                modifier = Modifier
                    .size(116.dp)
                    .graphicsLayer {
                        translationY = floatY.dp.toPx()
                        rotationZ = tapSpin
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_bismuth_logo),
                    contentDescription = "Bismuth Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .clip(CircleShape)
                )
            }
        }

        StaggeredEntrance(visibleKey = "welcome", index = 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isVoicePlaying) accentColor.copy(alpha = 0.22f) else accentColor.copy(alpha = 0.14f),
                    border = if (isVoicePlaying) BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)) else null,
                    modifier = Modifier
                        .reactiveSpringScale(isActive = isVoicePlaying, activeScale = 1.05f)
                        .bouncyClickable { triggerPowerUpVoice() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isVoicePlaying) Icons.Default.GraphicEq else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Play energy encouragement voice",
                            tint = accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isVoicePlaying) "POWERING UP SETUP..." else "TAP ATOM • CHARGE YOUR SETUP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Turn tough STEM\ninto intuition.",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    lineHeight = 38.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Interactive Socratic whiteboards, PDF quizzes, voice scribe, and multi-family edge models running right on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        StaggeredEntrance(visibleKey = "welcome", index = 1) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeaturePillChip("Visual Whiteboard", Icons.Default.Functions, Color(0xFF006874))
                FeaturePillChip("Gemma • Qwen • Phi", Icons.Default.Memory, Color(0xFF6D28D9))
                FeaturePillChip("Voice & Vision", Icons.Default.GraphicEq, Color(0xFF0F766E))
            }
        }

        StaggeredEntrance(visibleKey = "welcome", index = 2) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accentColor.copy(alpha = 0.14f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.padding(10.dp).size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${hardwareProfile.totalRamGb} GB RAM • ${hardwareProfile.cpuCores} Cores Detected",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Best fit: $recommendedName (plus Qwen & DeepSeek options in Models)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturePillChip(label: String, icon: ImageVector, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingNameStep(
    studentName: String,
    accentColor: Color,
    onNameChange: (String) -> Unit,
    onNextStep: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val quickNames = remember { listOf("Newton", "Curie", "Euler", "Lovelace", "Explorer") }

    val companionMessage = when {
        studentName.isBlank() -> "Hey! I'm Bismuth — your offline STEM sidekick. What should I call you?"
        studentName.trim().length < 3 -> "Ooh, starting with '${studentName.trim()}'... keep going!"
        else -> "Awesome to meet you, ${studentName.trim()}! Let's tune lessons around how you think."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        StaggeredEntrance(visibleKey = "name_step", index = 0) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = accentColor.copy(alpha = 0.10f),
                border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.size(62.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            AIThinkingOrb(size = 56.dp)
                        }
                    }
                    AnimatedContent(
                        targetState = companionMessage,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f))
                                .togetherWith(fadeOut(tween(140)))
                        },
                        label = "companionBubble",
                        modifier = Modifier.weight(1f)
                    ) { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        StaggeredEntrance(visibleKey = "name_step", index = 1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Who's in the lab today?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your tutor uses this during live interactive whiteboard walkthroughs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        StaggeredEntrance(visibleKey = "name_step", index = 2) {
            OutlinedTextField(
                value = studentName,
                onValueChange = onNameChange,
                label = { Text("First name or nickname") },
                placeholder = { Text("e.g. Maya") },
                singleLine = true,
                trailingIcon = {
                    if (studentName.isNotEmpty()) {
                        IconButton(onClick = { onNameChange("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear name")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onNextStep()
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    focusedLabelColor = accentColor
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        StaggeredEntrance(visibleKey = "name_step", index = 3) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Or tap a playful callsign:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickNames.forEach { alias ->
                        val active = studentName.equals(alias, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) accentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (active) 1.5.dp else 1.dp,
                                color = if (active) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier
                                .reactiveSpringScale(isActive = active, activeScale = 1.06f)
                                .bouncyClickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onNameChange(alias)
                                }
                        ) {
                            Text(
                                text = alias,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) accentColor else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingLevelStep(
    selectedLevel: String,
    onSelectLevel: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StaggeredEntrance(visibleKey = "level_step", index = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Pick your academic level",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "We calibrate problem depth, equations, and Socratic hints automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        StudentProfilePrefs.ACADEMIC_LEVELS.forEachIndexed { index, level ->
            val meta = resolveLevelMeta(level, index)
            StaggeredEntrance(visibleKey = "level_step", index = index + 1) {
                PlayfulSelectableCard(
                    title = level,
                    subtitle = meta.subtitle,
                    icon = meta.icon,
                    accentColor = meta.accent,
                    isSelected = selectedLevel == level,
                    onClick = { onSelectLevel(level) }
                )
            }
        }
    }
}

@Composable
private fun OnboardingSubjectStep(
    selectedSubject: String,
    onSelectSubject: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StaggeredEntrance(visibleKey = "subject_step", index = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Choose your main arena",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Everything stays unlocked — this pins your favorite studio up front.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        StudentProfilePrefs.PRIMARY_SUBJECTS.forEachIndexed { index, subject ->
            val meta = resolveSubjectMeta(subject, index)
            StaggeredEntrance(visibleKey = "subject_step", index = index + 1) {
                PlayfulSelectableCard(
                    title = subject,
                    subtitle = meta.subtitle,
                    icon = meta.icon,
                    accentColor = meta.accent,
                    isSelected = selectedSubject == subject,
                    onClick = { onSelectSubject(subject) }
                )
            }
        }
    }
}

@Composable
private fun OnboardingStyleLaunchStep(
    studentName: String,
    selectedLevel: String,
    selectedSubject: String,
    selectedStyle: String,
    selectedLanguage: String,
    hardwareProfile: DeviceHardwareProfile,
    onSelectStyle: (String) -> Unit,
    onSelectLanguage: (String) -> Unit
) {
    val cleanName = studentName.trim().ifBlank { "Scholar" }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StaggeredEntrance(visibleKey = "style_step", index = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "How should Bismuth teach?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Pick the explanation style that makes ideas click fastest for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        StudentProfilePrefs.LEARNING_STYLES.forEachIndexed { index, style ->
            val meta = resolveStyleMeta(style, index)
            StaggeredEntrance(visibleKey = "style_step", index = index + 1) {
                PlayfulSelectableCard(
                    title = style,
                    subtitle = meta.subtitle,
                    icon = meta.icon,
                    accentColor = meta.accent,
                    isSelected = selectedStyle == style,
                    onClick = { onSelectStyle(style) }
                )
            }
        }

        StaggeredEntrance(visibleKey = "style_step", index = StudentProfilePrefs.LEARNING_STYLES.size + 1) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Preferred AI Study Language",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StudentProfilePrefs.STUDY_LANGUAGES.forEach { lang ->
                        val isLangSelected = selectedLanguage == lang
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isLangSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = if (isLangSelected) 1.5.dp else 1.dp,
                                color = if (isLangSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .bouncyClickable { onSelectLanguage(lang) }
                        ) {
                            Text(
                                text = if (lang == "Nigerian Pidgin") "Pidgin (NG)" else lang,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isLangSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isLangSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }

        StaggeredEntrance(visibleKey = "style_step", index = StudentProfilePrefs.LEARNING_STYLES.size + 2) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(10.dp).size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ready for Liftoff, $cleanName!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$selectedLevel • $selectedSubject • $selectedLanguage • ${hardwareProfile.totalRamGb} GB Edge Ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaggeredEntrance(
    visibleKey: Any,
    index: Int,
    content: @Composable () -> Unit
) {
    val progress = remember(visibleKey, index) { Animatable(0f) }

    LaunchedEffect(visibleKey, index) {
        delay(index * 55L)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = SpringSpecs.ScaleEntrance
        )
    }

    Box(
        modifier = Modifier.graphicsLayer {
            val v = progress.value.coerceIn(0f, 1.08f)
            alpha = v.coerceIn(0f, 1f)
            translationY = (1f - v) * 28.dp.toPx()
            val s = 0.92f + 0.08f * v
            scaleX = s
            scaleY = s
        }
    ) {
        content()
    }
}

@Composable
private fun PlayfulSelectableCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(220),
        label = "cardBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        animationSpec = tween(220),
        label = "cardBorder"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .reactiveSpringScale(isActive = isSelected, activeScale = 1.02f, inactiveScale = 1f)
            .bouncyClickable(pressScale = 0.96f, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (isSelected) accentColor.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
                .matchParentSize()
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = containerColor,
            border = BorderStroke(width = if (isSelected) 2.dp else 1.dp, color = borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor.copy(alpha = if (isSelected) 0.22f else 0.12f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn(animationSpec = spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TactilePrimaryCtaButton(
    label: String,
    accentColor: Color,
    enabled: Boolean,
    isFinal: Boolean,
    onClick: () -> Unit
) {
    val arrowTransition = rememberInfiniteTransition(label = "ctaArrowNudge")
    val arrowNudge by arrowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowNudge"
    )

    val lipColor = Color(
        red = (accentColor.red * 0.68f).coerceIn(0f, 1f),
        green = (accentColor.green * 0.68f).coerceIn(0f, 1f),
        blue = (accentColor.blue * 0.68f).coerceIn(0f, 1f),
        alpha = 1f
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(enabled = enabled, pressScale = 0.96f, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(lipColor)
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accentColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.95f))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "ctaLabel"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = if (isFinal) Icons.Default.RocketLaunch else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { translationX = arrowNudge.dp.toPx() }
                )
            }
        }
    }
}

private fun resolveLevelMeta(level: String, index: Int): OptionMeta {
    val lower = level.lowercase()
    return when {
        "middle" in lower || index == 0 -> OptionMeta(
            subtitle = "Visual intuition, playful puzzles & clear foundations",
            icon = Icons.Default.Explore,
            accent = Color(0xFF0F766E)
        )
        "high" in lower || index == 1 -> OptionMeta(
            subtitle = "AP STEM prep, algebra-to-calculus bridges & lab mastery",
            icon = Icons.Default.School,
            accent = Color(0xFF006874)
        )
        "undergrad" in lower || "college" in lower || index == 2 -> OptionMeta(
            subtitle = "Rigorous derivations, vector calculus & quantitative proofs",
            icon = Icons.Default.Calculate,
            accent = Color(0xFF2563EB)
        )
        else -> OptionMeta(
            subtitle = "First-principles depth, research-grade edge synthesis",
            icon = Icons.Default.Psychology,
            accent = Color(0xFF6D28D9)
        )
    }
}

private fun resolveSubjectMeta(subject: String, index: Int): OptionMeta {
    val lower = subject.lowercase()
    return when {
        "math" in lower || "calc" in lower -> OptionMeta(
            subtitle = "Interactive balance scales, equations & step proofs",
            icon = Icons.Default.Functions,
            accent = Color(0xFF006874)
        )
        "phys" in lower -> OptionMeta(
            subtitle = "Force vectors, free-body motion & kinetic intuition",
            icon = Icons.Default.Bolt,
            accent = Color(0xFFD97706)
        )
        "chem" in lower || "bio" in lower -> OptionMeta(
            subtitle = "Reaction mechanisms, stoichiometry & molecular diagrams",
            icon = Icons.Default.Science,
            accent = Color(0xFF0F766E)
        )
        "comp" in lower || "code" in lower || "cs" in lower -> OptionMeta(
            subtitle = "Algorithms, complexity & structured problem solving",
            icon = Icons.Default.Code,
            accent = Color(0xFF6D28D9)
        )
        else -> OptionMeta(
            subtitle = "Focused offline tutorials tailored to your syllabus",
            icon = if (index % 2 == 0) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Explore,
            accent = Color(0xFF2563EB)
        )
    }
}

private fun resolveStyleMeta(style: String, index: Int): OptionMeta {
    val lower = style.lowercase()
    return when {
        "visual" in lower || index == 0 -> OptionMeta(
            subtitle = "Drawings, live balance widgets & Socratic nudges first",
            icon = Icons.Default.Visibility,
            accent = Color(0xFF006874)
        )
        "analog" in lower || index == 1 -> OptionMeta(
            subtitle = "Everyday real-world intuition before formal symbols",
            icon = Icons.Default.AutoAwesome,
            accent = Color(0xFF6D28D9)
        )
        else -> OptionMeta(
            subtitle = "Clean axioms, step-by-step formal logic & zero fluff",
            icon = Icons.Default.Psychology,
            accent = Color(0xFFD97706)
        )
    }
}
