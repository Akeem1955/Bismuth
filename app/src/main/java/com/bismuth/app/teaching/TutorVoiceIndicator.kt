package com.bismuth.app.teaching

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bismuth.app.ui.animation.AIThinkingOrb
import com.bismuth.app.ui.animation.AudioPulseVisualizer

enum class TutorState(val label: String) {
    IDLE("Tutor Ready"),
    SPEAKING("Tutor Speaking"),
    THINKING("Analyzing Step"),
    CELEBRATING("Concept Mastered!"),
    HINT("Reviewing Concept")
}

/**
 * Clean, native Material 3 / Google Edge UI audio and state indicator for the offline AI tutor.
 * Real-time waveform reactive to TextToSpeech playback and step interactions.
 */
@Composable
fun TutorVoiceIndicator(
    state: TutorState,
    isSpeaking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (state) {
        TutorState.CELEBRATING -> MaterialTheme.colorScheme.primaryContainer
        TutorState.HINT -> MaterialTheme.colorScheme.tertiaryContainer
        TutorState.SPEAKING -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = when (state) {
        TutorState.CELEBRATING -> MaterialTheme.colorScheme.onPrimaryContainer
        TutorState.HINT -> MaterialTheme.colorScheme.onTertiaryContainer
        TutorState.SPEAKING -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
        tonalElevation = 3.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                state == TutorState.CELEBRATING -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Correct",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                state == TutorState.THINKING -> {
                    AIThinkingOrb(size = 20.dp)
                }
                isSpeaking || state == TutorState.SPEAKING -> {
                    AudioPulseVisualizer(size = 20.dp)
                }
                state == TutorState.HINT -> {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "Hint",
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                else -> {
                    VoiceWaveformVisualizer(
                        isActive = false,
                        barColor = contentColor,
                        modifier = Modifier.size(width = 24.dp, height = 16.dp)
                    )
                }
            }

            Text(
                text = state.label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )

            if (!isSpeaking && state == TutorState.IDLE) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Hear narration",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun VoiceWaveformVisualizer(
    isActive: Boolean,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")

    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, delayMillis = 80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val phase4 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(410, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )

    Canvas(modifier = modifier) {
        val totalBars = 4
        val barWidth = size.width / (totalBars * 2f)
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)

        val heights = if (isActive) {
            listOf(phase1, phase2, phase3, phase4)
        } else {
            listOf(0.25f, 0.4f, 0.3f, 0.2f)
        }

        heights.forEachIndexed { i, ratio ->
            val barHeight = size.height * ratio
            val x = i * (barWidth * 2f) + (barWidth / 2f)
            val y = (size.height - barHeight) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = corner
            )
        }
    }
}
