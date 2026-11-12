package com.bismuth.app.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Standard spring physics specifications for tactile, high-engagement STEM UI.
 */
object SpringSpecs {
    val TactileBounce = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val ScaleEntrance = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    val GentleHarmonic = spring<Float>(
        dampingRatio = 0.42f,
        stiffness = 110f
    )
}

/**
 * Applies a Duolingo / Brilliant-style tactile spring press effect to any clickable component.
 * Upon touch down, compresses to [pressScale]; upon release, springs back with realistic bounce and fires [onClick].
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    pressScale: Float = 0.95f,
    onClick: () -> Unit
): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    scope.launch {
                        scale.animateTo(
                            targetValue = pressScale,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                    val released = tryAwaitRelease()
                    scope.launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = SpringSpecs.TactileBounce
                        )
                    }
                    if (released) {
                        onClick()
                    }
                }
            )
        }
}

/**
 * Reactive spring scale modifier driven by external selection or focus state.
 */
@Composable
fun Modifier.reactiveSpringScale(
    isActive: Boolean,
    activeScale: Float = 1.02f,
    inactiveScale: Float = 1.0f
): Modifier {
    val animatedScale by animateFloatAsState(
        targetValue = if (isActive) activeScale else inactiveScale,
        animationSpec = SpringSpecs.TactileBounce,
        label = "reactiveSpringScale"
    )
    return this.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
    }
}
