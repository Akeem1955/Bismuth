package com.bismuth.app.ui.animation

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Loop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Production-ready Jetpack Compose wrapper for interactive Rive character animations.
 * Drives real-time state machine transitions via declarative Compose state.
 */
@Composable
fun RiveCharacterView(
    modifier: Modifier = Modifier,
    @RawRes resId: Int? = null,
    bytes: ByteArray? = null,
    url: String? = null,
    stateMachineName: String? = null,
    artboardName: String? = null,
    fit: Fit = Fit.CONTAIN,
    alignment: Alignment = Alignment.CENTER,
    loop: Loop = Loop.AUTO,
    autoplay: Boolean = true,
    booleanInputs: Map<String, Boolean> = emptyMap(),
    numberInputs: Map<String, Float> = emptyMap(),
    onInit: (RiveAnimationView) -> Unit = {}
) {
    var loadedBytes by remember(bytes) { mutableStateOf(bytes) }

    LaunchedEffect(url) {
        if (url != null && loadedBytes == null) {
            val fetched = withContext(Dispatchers.IO) {
                try {
                    URL(url).openStream().use { it.readBytes() }
                } catch (_: Exception) {
                    null
                }
            }
            loadedBytes = fetched
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RiveAnimationView(context).apply {
                if (resId != null) {
                    setRiveResource(
                        resId = resId,
                        artboardName = artboardName,
                        stateMachineName = stateMachineName,
                        autoplay = autoplay,
                        fit = fit,
                        alignment = alignment,
                        loop = loop
                    )
                } else if (loadedBytes != null) {
                    setRiveBytes(
                        bytes = loadedBytes!!,
                        artboardName = artboardName,
                        stateMachineName = stateMachineName,
                        autoplay = autoplay,
                        fit = fit,
                        alignment = alignment,
                        loop = loop
                    )
                }
                onInit(this)
            }
        },
        update = { view ->
            if (resId == null && loadedBytes != null) {
                try {
                    view.setRiveBytes(
                        bytes = loadedBytes!!,
                        artboardName = artboardName,
                        stateMachineName = stateMachineName,
                        autoplay = autoplay,
                        fit = fit,
                        alignment = alignment,
                        loop = loop
                    )
                } catch (_: Exception) {
                    // Ignore re-init conflicts if already active
                }
            }

            // Synchronize state machine inputs on Compose recomposition
            stateMachineName?.let { smName ->
                booleanInputs.forEach { (inputName, value) ->
                    try {
                        view.setBooleanState(smName, inputName, value)
                    } catch (_: Exception) {
                        // Input may not exist in current artboard/state machine
                    }
                }
                numberInputs.forEach { (inputName, value) ->
                    try {
                        view.setNumberState(smName, inputName, value)
                    } catch (_: Exception) {
                        // Input may not exist in current artboard/state machine
                    }
                }
            }
        }
    )
}
