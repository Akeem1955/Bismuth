package com.bismuth.app.ui.animation

import androidx.annotation.RawRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Production-ready Jetpack Compose wrapper for Lottie vector micro-interactions & FX.
 */
@Composable
fun LottieEffectView(
    modifier: Modifier = Modifier,
    @RawRes resId: Int? = null,
    assetName: String? = null,
    url: String? = null,
    isPlaying: Boolean = true,
    iterations: Int = LottieConstants.IterateForever,
    speed: Float = 1.0f
) {
    val spec = when {
        resId != null -> LottieCompositionSpec.RawRes(resId)
        assetName != null -> LottieCompositionSpec.Asset(assetName)
        url != null -> LottieCompositionSpec.Url(url)
        else -> null
    }

    if (spec == null) return

    val composition by rememberLottieComposition(spec)
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = isPlaying,
        iterations = iterations,
        speed = speed
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
    )
}

/**
 * Full celebratory particle burst overlay triggered on quiz mastery / step completion.
 */
@Composable
fun CelebrationBurstOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LottieEffectView(
                assetName = "celebrate.json",
                modifier = Modifier.size(280.dp),
                iterations = 1,
                speed = 1.2f
            )
        }
    }
}

/**
 * Glowing pulsating AI neural core indicator for on-device reasoning and inference.
 */
@Composable
fun AIThinkingOrb(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    LottieEffectView(
        assetName = "ai_thinking.json",
        modifier = modifier.size(size),
        iterations = LottieConstants.IterateForever,
        speed = 1.0f
    )
}

/**
 * Dynamic soundwave pulse equalizer for microphone recording and voice playback.
 */
@Composable
fun AudioPulseVisualizer(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    LottieEffectView(
        assetName = "audio_pulse.json",
        modifier = modifier.size(size),
        iterations = LottieConstants.IterateForever,
        speed = 1.1f
    )
}

/**
 * Futuristic laser scanning beam for PDF document analysis and meal photo scanning.
 */
@Composable
fun ScannerBeamView(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    LottieEffectView(
        assetName = "scanner_beam.json",
        modifier = modifier.size(size),
        iterations = LottieConstants.IterateForever,
        speed = 1.2f
    )
}
