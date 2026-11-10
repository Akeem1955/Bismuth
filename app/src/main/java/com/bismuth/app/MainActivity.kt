package com.bismuth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismuth.app.document.DocumentFeatureScreen
import com.bismuth.app.engine.BismuthModelConfig
import com.bismuth.app.engine.DeviceHardwareProfile
import com.bismuth.app.engine.DownloadState
import com.bismuth.app.engine.HardwareCapabilityEvaluator
import com.bismuth.app.engine.ModelDownloadManager
import com.bismuth.app.engine.ModelPrefs
import com.bismuth.app.health.HealthFeatureScreen
import com.bismuth.app.scribe.ScribeFeatureScreen
import com.bismuth.app.teaching.InteractiveWhiteboardScreen
import com.bismuth.app.ui.theme.BismuthTheme
import kotlinx.coroutines.launch
import java.io.File

enum class BismuthDestination(
    val title: String,
    val icon: ImageVector
) {
    TEACH("Teach", Icons.Default.School),
    DOCS("Docs", Icons.Default.Description),
    SCRIBE("Scribe", Icons.Default.Mic),
    HEALTH("Health", Icons.Default.HealthAndSafety),
    MODELS("Models", Icons.Default.Memory)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BismuthTheme {
                BismuthAppRoot()
            }
        }
    }
}

@Composable
fun BismuthAppRoot() {
    val context = LocalContext.current
    val hardwareProfile = remember { HardwareCapabilityEvaluator.inspectDevice(context) }
    var selectedModel by remember {
        mutableStateOf(ModelPrefs.getSelectedConfig(context))
    }
    var currentDestination by remember { mutableStateOf(BismuthDestination.TEACH) }

    fun selectModel(config: BismuthModelConfig) {
        ModelPrefs.setSelectedModelId(context, config.modelId)
        selectedModel = config
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0F1D),
        topBar = {
            BismuthTopStatusBar(
                hardwareProfile = hardwareProfile,
                selectedModel = selectedModel,
                onOpenModels = { currentDestination = BismuthDestination.MODELS }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                BismuthDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination == dest,
                        onClick = { currentDestination = dest },
                        icon = {
                            Icon(
                                imageVector = dest.icon,
                                contentDescription = dest.title
                            )
                        },
                        label = {
                            Text(
                                text = dest.title,
                                fontSize = 11.sp,
                                fontWeight = if (currentDestination == dest) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF38BDF8),
                            selectedTextColor = Color(0xFF38BDF8),
                            indicatorColor = Color(0xFF1E293B),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentDestination) {
                BismuthDestination.TEACH -> InteractiveWhiteboardScreen(
                    selectedModel = selectedModel,
                    modifier = Modifier.fillMaxSize()
                )
                BismuthDestination.DOCS -> DocumentFeatureScreen(
                    selectedModel = selectedModel,
                    modifier = Modifier.fillMaxSize()
                )
                BismuthDestination.SCRIBE -> ScribeFeatureScreen(
                    selectedModel = selectedModel,
                    onRequestSwitchToAudioModel = {
                        currentDestination = BismuthDestination.MODELS
                    },
                    modifier = Modifier.fillMaxSize()
                )
                BismuthDestination.HEALTH -> HealthFeatureScreen(
                    selectedModel = selectedModel,
                    onRequestSwitchToVisionModel = {
                        currentDestination = BismuthDestination.MODELS
                    },
                    modifier = Modifier.fillMaxSize()
                )
                BismuthDestination.MODELS -> HardwareModelSelectorScreen(
                    hardwareProfile = hardwareProfile,
                    selectedModel = selectedModel,
                    onSelectModel = { selectModel(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun BismuthTopStatusBar(
    hardwareProfile: DeviceHardwareProfile,
    selectedModel: BismuthModelConfig,
    onOpenModels: () -> Unit
) {
    Surface(
        color = Color(0xFF0F172A),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_bismuth_logo),
                    contentDescription = "Bismuth Crystal Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "BISMUTH • Zero-Bandwidth AI Tutor",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "RAM: ${hardwareProfile.totalRamGb} GB • ${hardwareProfile.cpuCores} Cores • ${selectedModel.displayName}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                onClick = onOpenModels,
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E293B),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = buildString {
                            append("Text")
                            if (selectedModel.supportsImage) append(" • Vision")
                            if (selectedModel.supportsAudio) append(" • Audio")
                        },
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareModelSelectorScreen(
    hardwareProfile: DeviceHardwareProfile,
    selectedModel: BismuthModelConfig,
    onSelectModel: (BismuthModelConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { ModelDownloadManager(context) }
    val downloadState by downloadManager.downloadState.collectAsState()
    var activeDownloadModelId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Hardware-Aware Model Selection & Capability Gating",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Bismuth inspects your phone's RAM and processor, recommends the optimal on-device Gemma model, disables variants that exceed your device memory, and unlocks Audio/Vision features based on model capability.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        val recommendedModel = remember(hardwareProfile) {
            BismuthModelConfig.ALL.firstOrNull { it.modelId == hardwareProfile.recommendedModelId }
                ?: BismuthModelConfig.GEMMA_3N_LITE
        }

        // Device Hardware Diagnostics Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Detected Phone Hardware Specification",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• Total Physical RAM: ${hardwareProfile.totalRamGb} GB (Available: ${hardwareProfile.availableRamMb} MB)",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Text(
                    text = "• CPU Cores: ${hardwareProfile.cpuCores}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
                Text(
                    text = "• Recommended Model for This Device: ${recommendedModel.displayName}",
                    color = Color(0xFF34D399),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }

        // Model Variant Cards
        BismuthModelConfig.ALL.forEach { config ->
            val isSupported = HardwareCapabilityEvaluator.isModelSupportedOnDevice(config, hardwareProfile)
            val isRecommended = config.modelId == hardwareProfile.recommendedModelId
            val isSelected = config.modelId == selectedModel.modelId
            val isDownloaded = config.isDownloaded(context)

            val borderColor = when {
                !isSupported -> Color(0xFF475569)
                isSelected -> Color(0xFF38BDF8)
                isRecommended -> Color(0xFF10B981)
                else -> Color(0xFF1E293B)
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSupported) Color(0xFF131C2E) else Color(0xFF0F1420)
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isSelected || isRecommended) 1.5.dp else 1.dp,
                    color = borderColor
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = config.displayName,
                                    color = if (isSupported) Color.White else Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                if (isRecommended) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF064E3B)
                                    ) {
                                        Text(
                                            text = "Recommended for Your Phone",
                                            color = Color(0xFF34D399),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                if (!isSupported) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF3B1822)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = Color(0xFFF43F5E),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Disabled (Requires ${config.minRamGb}GB+ RAM)",
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "${config.tier.label} • Min RAM: ${config.minRamGb} GB",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = config.subtitle,
                        color = if (isSupported) Color(0xFFCBD5E1) else Color(0xFF64748B),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Capability Badges
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CapabilityBadge("Whiteboard & Docs", true)
                        CapabilityBadge(
                            "Inline Quiz: ${if (config.tier.isHighCapability) "Analytical" else "Foundational"}",
                            true
                        )
                        CapabilityBadge("Food Photo Vision", config.supportsImage)
                        CapabilityBadge("Lecture Audio Scribe", config.supportsAudio)
                    }

                    if (!isSupported) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "This model requires at least ${config.minRamGb} GB RAM, and your phone has ${hardwareProfile.totalRamGb} GB RAM. Disabled to prevent out-of-memory crashes.",
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp
                        )
                    }

                    if (activeDownloadModelId == config.modelId) {
                        when (val st = downloadState) {
                            is DownloadState.Downloading -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { st.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = "Downloading ${config.modelFile}: ${st.progressPercent}%",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp
                                )
                            }
                            is DownloadState.Failed -> {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = st.message,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp
                                )
                            }
                            else -> {}
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectModel(config) },
                            enabled = isSupported,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF10B981) else Color(0xFF0284C7)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isSelected) "Active Model" else "Select Model",
                                fontSize = 12.sp
                            )
                        }

                        if (isSupported) {
                            if (!isDownloaded) {
                                OutlinedButton(
                                    onClick = {
                                        activeDownloadModelId = config.modelId
                                        scope.launch {
                                            downloadManager.ensureModelReady(config)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download Weights", fontSize = 11.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            File(config.getLocalPath(context)).delete()
                                        } catch (_: Exception) {
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete Weights", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityBadge(label: String, enabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (enabled) Color(0xFF1E293B) else Color(0xFF0F172A)
    ) {
        Text(
            text = if (enabled) "✓ $label" else "✕ $label",
            color = if (enabled) Color(0xFF38BDF8) else Color(0xFF64748B),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}
