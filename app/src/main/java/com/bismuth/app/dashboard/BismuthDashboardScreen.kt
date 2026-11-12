package com.bismuth.app.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismuth.app.BismuthDestination
import com.bismuth.app.engine.BismuthModelConfig
import com.bismuth.app.engine.DeviceHardwareProfile
import com.bismuth.app.profile.LessonProgressStore
import com.bismuth.app.profile.StudentProfilePrefs
import com.bismuth.app.skills.SkillsCatalogRoot
import com.bismuth.app.skills.SubjectCatalogCoordinator
import com.bismuth.app.skills.SubjectSkillCatalog
import com.bismuth.app.skills.TopicCatalogEntry
import com.bismuth.app.ui.animation.bouncyClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BismuthDashboardScreen(
    hardwareProfile: DeviceHardwareProfile,
    selectedModel: BismuthModelConfig,
    onOpenDrawer: () -> Unit,
    onNavigateToDestination: (BismuthDestination) -> Unit,
    onSelectTopicToLearn: (TopicCatalogEntry) -> Unit,
    onQuickLearnTopic: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var profile by remember { mutableStateOf(StudentProfilePrefs.getProfile(context)) }
    val streakDays = remember(profile) { LessonProgressStore.getStreakDays(context) }
    val completedLessonsCount = remember(profile) { LessonProgressStore.getTotalCompletedLessons(context) }
    val isModelDownloaded = remember(selectedModel.modelId, context) {
        selectedModel.isDownloaded(context)
    }

    var catalog by remember { mutableStateOf<SkillsCatalogRoot?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }

    fun findPreferredSubject(root: SkillsCatalogRoot, preferred: String): SubjectCatalogCoordinator? {
        return root.subjects.firstOrNull {
            it.title.equals(preferred, ignoreCase = true) ||
                it.title.contains(preferred, ignoreCase = true) ||
                preferred.contains(it.title, ignoreCase = true)
        } ?: root.subjects.firstOrNull()
    }

    LaunchedEffect(Unit) {
        catalog = SubjectSkillCatalog.loadCatalog(context)
    }

    val matchedSubject = remember(catalog, profile.primarySubject) {
        catalog?.let { findPreferredSubject(it, profile.primarySubject) }
    }

    val recommendedTopic = remember(matchedSubject, completedLessonsCount) {
        val topics = matchedSubject?.chapters?.flatMap { it.topics }.orEmpty()
        topics.firstOrNull { !LessonProgressStore.isTopicCompleted(context, it.topicId) }
            ?: topics.firstOrNull()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Bismuth AI",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .bouncyClickable { showProfileDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile & Language",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (profile.preferredLanguage == "Nigerian Pidgin") {
                                    "${profile.name} • Pidgin"
                                } else {
                                    "${profile.name} • EN"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Gradient Welcome & Readiness Hero Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.26f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Welcome back, ${profile.name}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${streakDays}d Streak",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Offline AI tutor, PDF textbook quizzer & lecture notes — built for low-data and shared phones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isModelDownloaded) Color(0xFF16A34A).copy(alpha = 0.14f)
                                else Color(0xFFD97706).copy(alpha = 0.16f),
                                modifier = Modifier.bouncyClickable {
                                    onNavigateToDestination(BismuthDestination.MODELS)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isModelDownloaded) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isModelDownloaded) Color(0xFF15803D) else Color(0xFFB45309),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isModelDownloaded) "AI Ready: ${selectedModel.displayName}"
                                        else "Pick an AI Brain ->",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isModelDownloaded) Color(0xFF15803D) else Color(0xFFB45309),
                                        maxLines = 1
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.bouncyClickable { showProfileDialog = true }
                            ) {
                                Text(
                                    text = "Language: ${profile.preferredLanguage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Primary Gateway Grid: All 6 Core Features in One Clean View
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "All Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureGateTile(
                        title = "Quick Learn",
                        subtitle = "Ask any topic for instant guide & quiz",
                        icon = Icons.Default.AutoAwesome,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToDestination(BismuthDestination.QUICK_LEARN) },
                        modifier = Modifier.weight(1f)
                    )
                    FeatureGateTile(
                        title = "Whiteboard",
                        subtitle = "Visual ${profile.primarySubject} lessons",
                        icon = Icons.Default.School,
                        accentColor = Color(0xFF4F46E5),
                        onClick = { onNavigateToDestination(BismuthDestination.TEACH) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureGateTile(
                        title = "PDF & Quiz",
                        subtitle = "Summarize textbooks & practice exams",
                        icon = Icons.Default.Description,
                        accentColor = Color(0xFF0F766E),
                        onClick = { onNavigateToDestination(BismuthDestination.DOCS) },
                        modifier = Modifier.weight(1f)
                    )
                    FeatureGateTile(
                        title = "Scribe",
                        subtitle = if (selectedModel.supportsAudio) {
                            "Record class lectures to notes"
                        } else {
                            "Pick a Voice AI to unlock"
                        },
                        icon = Icons.Default.Mic,
                        accentColor = Color(0xFF7C3AED),
                        onClick = { onNavigateToDestination(BismuthDestination.SCRIBE) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FeatureGateTile(
                        title = "Meal & Diet",
                        subtitle = "Log meals, calories & macros",
                        icon = Icons.Default.HealthAndSafety,
                        accentColor = Color(0xFF15803D),
                        onClick = { onNavigateToDestination(BismuthDestination.HEALTH) },
                        modifier = Modifier.weight(1f)
                    )
                    FeatureGateTile(
                        title = "AI Brains",
                        subtitle = if (isModelDownloaded) {
                            "${hardwareProfile.totalRamGb} GB RAM • Switch offline AI"
                        } else {
                            "Pick an offline AI for your phone"
                        },
                        icon = Icons.Default.Memory,
                        accentColor = Color(0xFFB45309),
                        onClick = { onNavigateToDestination(BismuthDestination.MODELS) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Compact Resume Lesson Strip
            if (recommendedTopic != null && matchedSubject != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClickable { onSelectTopicToLearn(recommendedTopic) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "UP NEXT IN ${matchedSubject.title.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = recommendedTopic.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Start",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    // Profile & Study Language Bottom Sheet
    if (showProfileDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var editName by remember(profile) { mutableStateOf(profile.name) }
        var editLevel by remember(profile) { mutableStateOf(profile.academicLevel) }
        var editSubject by remember(profile) { mutableStateOf(profile.primarySubject) }
        var editStyle by remember(profile) { mutableStateOf(profile.learningStyle) }
        var editLanguage by remember(profile) { mutableStateOf(profile.preferredLanguage) }

        ModalBottomSheet(
            onDismissRequest = { showProfileDialog = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Student Profile & Study Language",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Student Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Preferred AI Study Language",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudentProfilePrefs.STUDY_LANGUAGES.forEach { lang ->
                            FilterChip(
                                selected = editLanguage == lang,
                                onClick = { editLanguage = lang },
                                label = {
                                    Text(
                                        text = if (lang == "Nigerian Pidgin") "Pidgin (NG)" else lang,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Academic Level", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudentProfilePrefs.ACADEMIC_LEVELS.forEach { lvl ->
                            FilterChip(
                                selected = editLevel == lvl,
                                onClick = { editLevel = lvl },
                                label = { Text(lvl, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Primary Subject Focus", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudentProfilePrefs.PRIMARY_SUBJECTS.forEach { subj ->
                            FilterChip(
                                selected = editSubject == subj,
                                onClick = { editSubject = subj },
                                label = { Text(subj, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Teaching Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudentProfilePrefs.LEARNING_STYLES.forEach { st ->
                            FilterChip(
                                selected = editStyle == st,
                                onClick = { editStyle = st },
                                label = { Text(st, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        StudentProfilePrefs.saveProfile(
                            context = context,
                            name = editName.trim().ifBlank { "Scholar" },
                            academicLevel = editLevel,
                            primarySubject = editSubject,
                            learningStyle = editStyle,
                            preferredLanguage = editLanguage
                        )
                        val updated = StudentProfilePrefs.getProfile(context)
                        profile = updated
                        showProfileDialog = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Save Study Profile", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FeatureGateTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.bouncyClickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.24f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
