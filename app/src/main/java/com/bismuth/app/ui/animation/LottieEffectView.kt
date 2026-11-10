package com.bismuth.app.ui.animation

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
