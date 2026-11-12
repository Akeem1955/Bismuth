package com.bismuth.app.teaching

import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bismuth.app.skills.SubjectSkillCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Offline viewer for the teacher-curated visual diagram (`assets/images/step_X.png`)
 * aligned with the active lesson step.
 */
@Composable
fun OfflineStepDiagramView(
    topicId: String,
    bundledInApk: Boolean,
    step: BoardStep,
    onRequestDownloadPack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val relativePath = step.diagramRelativePath.ifBlank { "assets/images/step_${step.stepNumber}.png" }

    var bitmap by remember(topicId, relativePath) { mutableStateOf<ImageBitmap?>(null) }
    var byteSize by remember(topicId, relativePath) { mutableIntStateOf(0) }
    var isLoading by remember(topicId, relativePath) { mutableStateOf(true) }

    var scale by remember(topicId, relativePath) { mutableFloatStateOf(1f) }
    var offset by remember(topicId, relativePath) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(topicId, relativePath) {
        isLoading = true
        val bytes = SubjectSkillCatalog.loadTopicAssetBytes(
            context = context,
            topicId = topicId,
            relativePath = relativePath,
            bundledInApk = bundledInApk
        )
        if (bytes != null) {
            byteSize = bytes.size
            bitmap = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        } else {
            bitmap = null
        }
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator()
            }
            bitmap != null -> {
                Crossfade(
                    targetState = bitmap,
                    animationSpec = tween(260),
                    label = "diagramFade"
                ) { activeBmp ->
                    if (activeBmp != null) {
                        Image(
                            bitmap = activeBmp,
                            contentDescription = "Step ${step.stepNumber} Curated Diagram: ${step.title}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .pointerInput(topicId, relativePath) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 3f)
                                        offset = if (scale > 1f) offset + pan else Offset.Zero
                                    }
                                }
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        )
                    }
                }

                // Top-left Curated Resource alignment pill
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Step ${step.stepNumber} Diagram • ${(byteSize / 1024).coerceAtLeast(1)} KB",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (scale > 1.01f) {
                    AssistChip(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                        label = { Text("Reset Zoom") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CenterFocusStrong,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Curated Step ${step.stepNumber} Diagram Not Downloaded Yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (onRequestDownloadPack != null) {
                        Button(onClick = onRequestDownloadPack) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download Multimedia Pack")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Offline video player for `assets/video/walkthrough.mp4` supporting frame-accurate scrubbing,
 * automatic step synchronization, and play/pause controls.
 */
@Composable
fun OfflineWalkthroughVideoPlayer(
    topicId: String,
    bundledInApk: Boolean,
    currentStep: BoardStep,
    totalSteps: Int,
    onStepSelectedFromVideo: (Int) -> Unit,
    onNarrateStep: (String) -> Unit,
    onRequestDownloadPack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val relativePath = currentStep.videoRelativePath.ifBlank { "assets/video/walkthrough.mp4" }

    var frames by remember(topicId, relativePath) { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    var fileSizeKb by remember(topicId, relativePath) { mutableIntStateOf(0) }
    var isLoading by remember(topicId, relativePath) { mutableStateOf(true) }

    // 12 frames total (4 phases per step across 3 steps)
    val initialFrameForStep = ((currentStep.stepNumber - 1) * 4).coerceAtLeast(0)
    var currentFrameIndex by remember(topicId) { mutableIntStateOf(initialFrameForStep) }
    var isPlaying by remember(topicId) { mutableStateOf(true) }

    // Sync video frame index when the student changes steps in the lesson stepper
    LaunchedEffect(currentStep.stepNumber) {
        val targetStart = ((currentStep.stepNumber - 1) * 4).coerceIn(0, (frames.size - 1).coerceAtLeast(0))
        if (currentFrameIndex / 4 != currentStep.stepNumber - 1) {
            currentFrameIndex = targetStart
        }
    }

    LaunchedEffect(topicId, relativePath) {
        isLoading = true
        val bytes = SubjectSkillCatalog.loadTopicAssetBytes(
            context = context,
            topicId = topicId,
            relativePath = relativePath,
            bundledInApk = bundledInApk
        )
        if (bytes != null) {
            fileSizeKb = (bytes.size / 1024).coerceAtLeast(1)
            frames = withContext(Dispatchers.Default) {
                parseHybridMp4Frames(bytes)
            }
            currentFrameIndex = ((currentStep.stepNumber - 1) * 4).coerceIn(0, (frames.size - 1).coerceAtLeast(0))
        } else {
            frames = emptyList()
        }
        isLoading = false
    }

    // Frame playback loop (1.1s per animated phase frame)
    LaunchedEffect(isPlaying, frames.size) {
        if (!isPlaying || frames.isEmpty()) return@LaunchedEffect
        while (isPlaying && frames.isNotEmpty()) {
            delay(1100L)
            val nextIdx = (currentFrameIndex + 1) % frames.size
            currentFrameIndex = nextIdx
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                frames.isNotEmpty() -> {
                    val safeIdx = currentFrameIndex.coerceIn(0, frames.lastIndex)
                    val activeStepInVideo = (safeIdx / 4) + 1
                    val activePhaseInStep = (safeIdx % 4) + 1

                    Image(
                        bitmap = frames[safeIdx],
                        contentDescription = "Walkthrough Video Frame ${safeIdx + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    // Top-left MP4 file badge
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "$relativePath ($fileSizeKb KB) • Step $activeStepInVideo Phase $activePhaseInStep/4",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Walkthrough Video Not Downloaded Yet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (onRequestDownloadPack != null) {
                            Button(onClick = onRequestDownloadPack) {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download Video Pack")
                            }
                        }
                    }
                }
            }
        }

        // Video Transport & Step Jump Controls
        if (frames.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { isPlaying = !isPlaying },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause Video" else "Play Video",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                currentFrameIndex = ((currentStep.stepNumber - 1) * 4).coerceIn(0, frames.lastIndex)
                                isPlaying = true
                                onNarrateStep(currentStep.narration)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "Replay Current Step Segment",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Slider(
                            value = currentFrameIndex.toFloat(),
                            onValueChange = { value ->
                                isPlaying = false
                                currentFrameIndex = value.toInt().coerceIn(0, frames.lastIndex)
                            },
                            valueRange = 0f..(frames.lastIndex.coerceAtLeast(1)).toFloat(),
                            steps = (frames.size - 2).coerceAtLeast(0),
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "00:%02d / 00:%02d".format(currentFrameIndex + 1, frames.size),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Step chapter markers inside the video
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (s in 1..totalSteps) {
                            val isStepActiveInVideo = (currentFrameIndex / 4) + 1 == s
                            FilterChip(
                                selected = isStepActiveInVideo,
                                onClick = {
                                    currentFrameIndex = ((s - 1) * 4).coerceIn(0, frames.lastIndex)
                                    onStepSelectedFromVideo(s - 1)
                                },
                                label = {
                                    Text("Step $s (${(s - 1) * 4}s)")
                                }
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Parses the ISO Base Media File Format (`.mp4`) container's `moov` -> `udta` (`BISM_MP4_V1`)
 * frame index table and decodes each frame from the `mdat` payload into an [ImageBitmap].
 */
private fun parseHybridMp4Frames(mp4Bytes: ByteArray): List<ImageBitmap> {
    val result = mutableListOf<ImageBitmap>()
    val magic = "BISM_MP4_V1".toByteArray(StandardCharsets.US_ASCII)

    var magicPos = -1
    outer@ for (i in 0..(mp4Bytes.size - magic.size - 8)) {
        for (j in magic.indices) {
            if (mp4Bytes[i + j] != magic[j]) continue@outer
        }
        magicPos = i
        break
    }

    if (magicPos < 0) return emptyList()

    val buf = ByteBuffer.wrap(mp4Bytes)
    val tableStart = magicPos + magic.size
    val fps = buf.getInt(tableStart)
    val frameCount = buf.getInt(tableStart + 4)
    if (fps <= 0 || frameCount !in 1..120) return emptyList()

    var cursor = tableStart + 8
    for (i in 0 until frameCount) {
        if (cursor + 8 > mp4Bytes.size) break
        val offset = buf.getInt(cursor)
        val length = buf.getInt(cursor + 4)
        cursor += 8
        if (offset >= 0 && length > 0 && offset + length <= mp4Bytes.size) {
            val bmp = BitmapFactory.decodeByteArray(mp4Bytes, offset, length)
            if (bmp != null) {
                result.add(bmp.asImageBitmap())
            }
        }
    }
    return result
}
