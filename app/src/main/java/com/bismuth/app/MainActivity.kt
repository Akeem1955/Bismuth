package com.bismuth.app

import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismuth.app.dashboard.BismuthDashboardScreen
import com.bismuth.app.document.DocumentFeatureScreen
import com.bismuth.app.engine.BismuthModelConfig
import com.bismuth.app.engine.DeviceHardwareProfile
import com.bismuth.app.engine.DownloadState
import com.bismuth.app.engine.HardwareCapabilityEvaluator
import com.bismuth.app.engine.ModelDownloadManager
import com.bismuth.app.engine.ModelFamily
import com.bismuth.app.engine.ModelPrefs
import com.bismuth.app.health.HealthFeatureScreen
import com.bismuth.app.onboarding.BismuthOnboardingScreen
import com.bismuth.app.profile.StudentProfilePrefs
import com.bismuth.app.scribe.ScribeFeatureScreen
import com.bismuth.app.skills.TopicCatalogEntry
import com.bismuth.app.teaching.InteractiveWhiteboardScreen
import com.bismuth.app.ui.theme.BismuthTheme
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

enum class BismuthDestination(
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val badge: String
) {
    HOME("Home", Icons.Default.Home, "Learning Dashboard", ""),
    QUICK_LEARN("Quick Learn", Icons.Default.AutoAwesome, "Instant Topic Tutor", "Skill AI"),
    TEACH("Teach", Icons.Default.School, "Socratic Whiteboard", "Visual Lessons"),
    DOCS("Docs", Icons.Default.Description, "Quiz & Study Studio", "PDF Ingestion"),
    SCRIBE("Scribe", Icons.Default.Mic, "Lecture Scribe", "16kHz Voice"),
    HEALTH("Health", Icons.Default.HealthAndSafety, "Nutrition & Health", "Meal Tracker"),
    MODELS("AI Brains", Icons.Default.Memory, "Pick Your AI", "Offline AI")
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
    val scope = rememberCoroutineScope()
    val hardwareProfile = remember { HardwareCapabilityEvaluator.inspectDevice(context) }
    var selectedModel by remember {
        mutableStateOf(ModelPrefs.getSelectedConfig(context))
    }

    var isConfigured by remember { mutableStateOf(StudentProfilePrefs.isConfigured(context)) }
    var currentDestination by remember { mutableStateOf(BismuthDestination.HOME) }
    var previousDestination by remember { mutableStateOf(BismuthDestination.HOME) }
    var activeTopicForLesson by remember { mutableStateOf<TopicCatalogEntry?>(null) }
    var activeQuickTopicQuery by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    fun navigateTo(dest: BismuthDestination) {
        if (currentDestination != BismuthDestination.MODELS) {
            previousDestination = currentDestination
        }
        currentDestination = dest
        scope.launch { drawerState.close() }
    }

    fun selectModel(config: BismuthModelConfig) {
        ModelPrefs.setSelectedModelId(context, config.modelId)
        selectedModel = config
    }

    BackHandler(enabled = drawerState.isOpen || (currentDestination != BismuthDestination.HOME && currentDestination != BismuthDestination.TEACH)) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (currentDestination == BismuthDestination.MODELS) {
            currentDestination = previousDestination
        } else {
            currentDestination = BismuthDestination.HOME
        }
    }

    // First Launch: Dedicated Onboarding Setup Screen
    if (!isConfigured) {
        BismuthOnboardingScreen(
            hardwareProfile = hardwareProfile,
            onOnboardingComplete = {
                isConfigured = true
                currentDestination = BismuthDestination.HOME
            },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // Post-Onboarding: Sliding Navigation Drawer + Full App Navigation
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                drawerTonalElevation = 6.dp,
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            ) {
                BismuthNavDrawerContent(
                    hardwareProfile = hardwareProfile,
                    selectedModel = selectedModel,
                    currentDestination = currentDestination,
                    onSelectDestination = { dest ->
                        if (dest == BismuthDestination.TEACH) {
                            activeTopicForLesson = null
                        }
                        navigateTo(dest)
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (currentDestination != BismuthDestination.HOME &&
                    currentDestination != BismuthDestination.TEACH &&
                    currentDestination != BismuthDestination.QUICK_LEARN
                ) {
                    BismuthEdgeTopBar(
                        hardwareProfile = hardwareProfile,
                        selectedModel = selectedModel,
                        currentDestination = currentDestination,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateBack = { currentDestination = previousDestination },
                        onOpenModels = { navigateTo(BismuthDestination.MODELS) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (currentDestination != BismuthDestination.TEACH) {
                            Modifier.pointerInput(currentDestination) {
                                val slop = viewConfiguration.touchSlop
                                val openThresholdPx = 68.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    var prevPos = down.position
                                    var triggered = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed || change.isConsumed) break
                                        val delta = change.position - prevPos
                                        prevPos = change.position
                                        totalDx += delta.x
                                        totalDy += delta.y
                                        if (!triggered &&
                                            totalDx > openThresholdPx &&
                                            totalDx > slop &&
                                            totalDx > kotlin.math.abs(totalDy) * 1.45f
                                        ) {
                                            triggered = true
                                            scope.launch { drawerState.open() }
                                        }
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        if (currentDestination != BismuthDestination.HOME &&
                            currentDestination != BismuthDestination.TEACH &&
                            currentDestination != BismuthDestination.QUICK_LEARN
                        ) {
                            Modifier.padding(innerPadding)
                        } else {
                            Modifier
                        }
                    )
            ) {
                when (currentDestination) {
                    BismuthDestination.HOME -> BismuthDashboardScreen(
                        hardwareProfile = hardwareProfile,
                        selectedModel = selectedModel,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToDestination = { dest ->
                            if (dest == BismuthDestination.TEACH) {
                                activeTopicForLesson = null
                            }
                            navigateTo(dest)
                        },
                        onSelectTopicToLearn = { topic ->
                            activeTopicForLesson = topic
                            navigateTo(BismuthDestination.TEACH)
                        },
                        onQuickLearnTopic = { query ->
                            activeQuickTopicQuery = query
                            navigateTo(BismuthDestination.QUICK_LEARN)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    BismuthDestination.QUICK_LEARN -> com.bismuth.app.teaching.QuickLearnTopicScreen(
                        initialTopicQuery = activeQuickTopicQuery,
                        selectedModel = selectedModel,
                        onNavigateToModels = { navigateTo(BismuthDestination.MODELS) },
                        onNavigateBack = { navigateTo(BismuthDestination.HOME) },
                        modifier = Modifier.fillMaxSize()
                    )
                    BismuthDestination.TEACH -> InteractiveWhiteboardScreen(
                        initialTopic = activeTopicForLesson,
                        selectedModel = selectedModel,
                        onNavigateBack = { navigateTo(BismuthDestination.HOME) },
                        onQuickLearnTopic = { query ->
                            activeQuickTopicQuery = query
                            navigateTo(BismuthDestination.QUICK_LEARN)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    BismuthDestination.DOCS -> DocumentFeatureScreen(
                        selectedModel = selectedModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    BismuthDestination.SCRIBE -> ScribeFeatureScreen(
                        selectedModel = selectedModel,
                        onRequestSwitchToAudioModel = {
                            navigateTo(BismuthDestination.MODELS)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    BismuthDestination.HEALTH -> HealthFeatureScreen(
                        selectedModel = selectedModel,
                        onRequestSwitchToVisionModel = {
                            navigateTo(BismuthDestination.MODELS)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    BismuthDestination.MODELS -> com.bismuth.app.engine.AiBrainSelectorScreen(
                        hardwareProfile = hardwareProfile,
                        selectedModel = selectedModel,
                        onSelectModel = { selectModel(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun BismuthNavDrawerContent(
    hardwareProfile: DeviceHardwareProfile,
    selectedModel: BismuthModelConfig,
    currentDestination: BismuthDestination,
    onSelectDestination: (BismuthDestination) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "drawerPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "drawerDotAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Drawer Header: Sleek Bismuth Brand & Sparkle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_bismuth_logo),
                    contentDescription = "Bismuth AI",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Bismuth",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "EDGE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "On-Device AI Studio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Hardware summary pill inside drawer
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Device: ${hardwareProfile.totalRamGb}GB RAM • ${hardwareProfile.cpuCores} Cores",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = dotAlpha))
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "TUTOR SUITE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            // Primary Educational Modules
            val mainFeatures = listOf(
                BismuthDestination.HOME,
                BismuthDestination.QUICK_LEARN,
                BismuthDestination.TEACH,
                BismuthDestination.DOCS,
                BismuthDestination.SCRIBE,
                BismuthDestination.HEALTH
            )

            mainFeatures.forEach { dest ->
                val isSelected = dest == currentDestination
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = dest.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.title,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    badge = {
                        if (dest.badge.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = dest.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    selected = isSelected,
                    onClick = { onSelectDestination(dest) },
                    shape = RoundedCornerShape(16.dp),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedContainerColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "INFERENCE ENGINE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )

            val isModelsSelected = currentDestination == BismuthDestination.MODELS
            NavigationDrawerItem(
                label = {
                    Text(
                        text = BismuthDestination.MODELS.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isModelsSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                icon = {
                    Icon(
                        imageVector = BismuthDestination.MODELS.icon,
                        contentDescription = "Models",
                        modifier = Modifier.size(22.dp)
                    )
                },
                badge = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "Pick AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                },
                selected = isModelsSelected,
                onClick = { onSelectDestination(BismuthDestination.MODELS) },
                shape = RoundedCornerShape(16.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    unselectedContainerColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun BismuthEdgeTopBar(
    hardwareProfile: DeviceHardwareProfile,
    selectedModel: BismuthModelConfig,
    currentDestination: BismuthDestination,
    onOpenDrawer: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenModels: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (currentDestination == BismuthDestination.MODELS) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                } else {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Navigation Drawer",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (currentDestination == BismuthDestination.HOME) "Bismuth AI" else currentDestination.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (currentDestination == BismuthDestination.HOME) "Offline AI Studio" else currentDestination.badge,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .widthIn(max = 148.dp)
                    .clickable { onOpenModels() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = dotAlpha))
                    )
                    Text(
                        text = selectedModel.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
    var selectedFamily by remember { mutableStateOf<ModelFamily?>(null) }
    var deletedTick by remember { mutableIntStateOf(0) }
    var customImportMessage by remember { mutableStateOf<String?>(null) }

    val customModelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val resolvedName = try {
                var name: String? = null
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = cursor.getString(idx)
                    }
                }
                name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "custom_model.litertlm"
            } catch (_: Exception) {
                "custom_model.litertlm"
            }

            if (!resolvedName.endsWith(".litertlm", ignoreCase = true)) {
                customImportMessage = "Please choose a .litertlm model file (got $resolvedName)."
                return@rememberLauncherForActivityResult
            }

            val customConfig = com.bismuth.app.engine.ModelPrefs.customConfigForFilename(resolvedName)
            scope.launch {
                activeDownloadModelId = customConfig.modelId
                customImportMessage = "Copying $resolvedName to Bismuth offline storage…"
                val imported = downloadManager.importCustomModelUri(uri, customConfig)
                if (imported) {
                    onSelectModel(customConfig)
                    deletedTick++
                    customImportMessage = "Imported and activated $resolvedName for offline inference."
                } else {
                    customImportMessage = "Could not import $resolvedName. Ensure storage space is available."
                }
            }
        }
    }

    val filteredModels = remember(selectedFamily) {
        if (selectedFamily == null) BismuthModelConfig.ALL
        else BismuthModelConfig.ALL.filter { it.family == selectedFamily }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Hardware-Aware Model Selection",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Bismuth inspects your phone's RAM and processor, recommends the optimal on-device AI model (Gemma, Qwen, DeepSeek, Phi), disables variants that exceed device memory, and gates multimodal Audio/Vision capabilities.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Phone Hardware Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "• Total Physical RAM: ${hardwareProfile.totalRamGb} GB (Available: ${hardwareProfile.availableRamMb} MB)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• CPU Cores: ${hardwareProfile.cpuCores}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• Recommended Architecture: ${
                        BismuthModelConfig.ALL.firstOrNull { it.modelId == hardwareProfile.recommendedModelId }?.displayName ?: hardwareProfile.recommendedModelId
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Offline Local .litertlm Sideload Card (Zero-Bandwidth Sharing)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Offline Local Model Import (.litertlm)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Received a .litertlm file via Bluetooth, Xender, or USB? Import it from phone storage with zero internet required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selectedModel.modelId.startsWith("custom_")) {
                    Text(
                        text = "Active Custom Model: ${selectedModel.displayName} (${selectedModel.modelFile})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D)
                    )
                }
                customImportMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = { customModelPicker.launch(arrayOf("*/*")) },
                    enabled = downloadState !is DownloadState.Downloading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Local .litertlm", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFamily == null,
                onClick = { selectedFamily = null },
                label = { Text("All (${BismuthModelConfig.ALL.size})", fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            ModelFamily.entries.forEach { family ->
                val count = BismuthModelConfig.ALL.count { it.family == family }
                val familyAccent = Color(family.badgeColorHex)
                FilterChip(
                    selected = selectedFamily == family,
                    onClick = { selectedFamily = if (selectedFamily == family) null else family },
                    label = { Text("${family.displayName} ($count)", fontWeight = FontWeight.SemiBold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = familyAccent.copy(alpha = 0.16f),
                        selectedLabelColor = familyAccent
                    )
                )
            }
        }

        filteredModels.forEach { config ->
            val isSupported = HardwareCapabilityEvaluator.isModelSupportedOnDevice(config, hardwareProfile)
            val isSelected = config.modelId == selectedModel.modelId
            val isDownloaded = remember(config.modelId, deletedTick, downloadState) {
                config.isDownloaded(context)
            }
            val dlState = if (activeDownloadModelId == config.modelId) downloadState else DownloadState.Idle
            val familyAccent = Color(config.family.badgeColorHex)
            val sizeText = if (config.sizeInBytes > 0L) {
                String.format(Locale.US, "%.2f GB", config.sizeInBytes / (1024.0 * 1024.0 * 1024.0))
            } else {
                "Local"
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = familyAccent.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = config.family.displayName.uppercase(Locale.US),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = familyAccent,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = sizeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = config.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (!isSupported) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Gated",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Needs ${config.minRamGb}GB RAM",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = config.tier.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = config.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CapabilityBadge("Whiteboard & Docs", true)
                        CapabilityBadge("Audio Scribe", config.supportsAudio)
                        CapabilityBadge("Vision Photo", config.supportsImage)
                    }

                    if (!isSupported) {
                        Text(
                            text = "Requires at least ${config.minRamGb} GB RAM (Device has ${hardwareProfile.totalRamGb} GB). Disabled to prevent memory crashes.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (activeDownloadModelId == config.modelId) {
                        when (val st = downloadState) {
                            is DownloadState.Downloading -> {
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { st.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            is DownloadState.Failed -> {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = st.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            else -> {}
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectModel(config) },
                            enabled = isSupported,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (isSelected) "Active Model" else "Select Model",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isSupported) {
                            if (!isDownloaded) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            activeDownloadModelId = config.modelId
                                            downloadManager.ensureModelReady(config)
                                        }
                                    },
                                    enabled = downloadState !is DownloadState.Downloading,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Download", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            File(config.getLocalPath(context)).delete()
                                            deletedTick++
                                            customImportMessage = "Deleted local weights for ${config.displayName}."
                                        } catch (_: Exception) {}
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", style = MaterialTheme.typography.labelMedium)
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
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh
        else MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Text(
            text = if (enabled) "✓ $label" else "✕ $label",
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}
