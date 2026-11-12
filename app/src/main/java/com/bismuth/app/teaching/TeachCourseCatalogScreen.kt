package com.bismuth.app.teaching

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismuth.app.profile.LessonProgressStore
import com.bismuth.app.skills.ChapterCatalogNode
import com.bismuth.app.skills.SkillsCatalogRoot
import com.bismuth.app.skills.SubjectCatalogCoordinator
import com.bismuth.app.skills.SubjectSkillCatalog
import com.bismuth.app.skills.TopicCatalogEntry
import com.bismuth.app.skills.TopicSkillBundle
import com.bismuth.app.ui.animation.bouncyClickable
import com.bismuth.app.ui.animation.reactiveSpringScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rich, Coursera-inspired Subject Course & Lesson Module Hub for Bismuth Teach.
 * Displays animated subject hero cards synced with chapter syllabi, all 10 skill-backed
 * modules, multimedia capability pills, offline pack status, and 1-tap cloud downloads.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TeachCourseCatalogScreen(
    catalog: SkillsCatalogRoot,
    onCatalogUpdated: (SkillsCatalogRoot) -> Unit,
    onSelectTopic: (TopicCatalogEntry) -> Unit,
    onNavigateBack: () -> Unit,
    onQuickLearnTopic: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val subjects = catalog.subjects
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { subjects.size.coerceAtLeast(1) })
    var showAllModulesView by remember { mutableStateOf(false) }
    var isSyncingCatalog by remember { mutableStateOf(false) }

    // Per-topic download & availability states
    val offlineStatusMap = remember { mutableStateMapOf<String, Boolean>() }
    val downloadingTopicIds = remember { mutableStateMapOf<String, Boolean>() }
    var isBatchDownloading by remember { mutableStateOf(false) }
    var batchProgressLabel by remember { mutableStateOf("") }

    // Refresh offline status whenever catalog updates
    LaunchedEffect(catalog) {
        catalog.subjects.forEach { subj ->
            subj.chapters.forEach { chap ->
                chap.topics.forEach { topic ->
                    offlineStatusMap[topic.topicId] = SubjectSkillCatalog.isTopicAvailableOffline(context, topic)
                }
            }
        }
    }

    // Skill preview modal sheet
    var previewTarget by remember { mutableStateOf<Triple<SubjectCatalogCoordinator, ChapterCatalogNode, TopicCatalogEntry>?>(null) }
    var previewBundle by remember { mutableStateOf<TopicSkillBundle?>(null) }
    val previewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(previewTarget) {
        val target = previewTarget ?: run {
            previewBundle = null
            return@LaunchedEffect
        }
        previewBundle = SubjectSkillCatalog.loadTopicBundleOffline(context, target.third)
    }

    val allTopicTriples = remember(catalog) {
        catalog.subjects.flatMap { subj ->
            subj.chapters.flatMap { chap ->
                chap.topics.map { topic -> Triple(subj, chap, topic) }
            }
        }
    }
    val offlineCount = allTopicTriples.count { offlineStatusMap[it.third.topicId] == true }
    val completedCount = allTopicTriples.count { LessonProgressStore.isTopicCompleted(context, it.third.topicId) }
    val missingTopics = allTopicTriples.map { it.third }.filter { offlineStatusMap[it.topicId] != true }

    val syncSpinTransition = rememberInfiniteTransition(label = "catalogSync")
    val syncRotation by syncSpinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "syncAngle"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bismuth Academy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${subjects.size} Subjects • $offlineCount/${allTopicTriples.size} Offline • $completedCount Done",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        enabled = !isSyncingCatalog,
                        onClick = {
                            scope.launch {
                                isSyncingCatalog = true
                                val res = SubjectSkillCatalog.syncCatalogFromGcs(context)
                                isSyncingCatalog = false
                                res.onSuccess { updated ->
                                    onCatalogUpdated(updated)
                                    snackbarHostState.showSnackbar("Synced ${updated.totalSubjects} subjects (${updated.totalTopics} modules)")
                                }.onFailure { err ->
                                    snackbarHostState.showSnackbar("Sync skipped (offline): ${err.message ?: "No network"}")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Refresh Course Catalog",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(22.dp)
                                .then(if (isSyncingCatalog) Modifier.rotate(syncRotation) else Modifier)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Offline Pack Readiness / One-Tap Download All Bar
            item {
                val missingMb = "%.1f".format(missingTopics.sumOf { it.packageSizeBytes } / (1024.0 * 1024.0))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (missingTopics.isEmpty())
                        Color(0xFF00C853).copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (missingTopics.isEmpty()) Color(0xFF00C853).copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (missingTopics.isEmpty()) Icons.Default.OfflinePin else Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = if (missingTopics.isEmpty()) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = when {
                                        isBatchDownloading -> batchProgressLabel
                                        missingTopics.isEmpty() -> "All ${allTopicTriples.size} Modules Saved Offline"
                                        else -> "${missingTopics.size} Cloud Skill Packs ($missingMb MB)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (missingTopics.isEmpty())
                                        "Every whiteboard, diagram, video & quiz works with zero data"
                                    else "Includes 3 HD diagrams, MP4 walkthrough & interactive quiz per module",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }

                        if (missingTopics.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.bouncyClickable(enabled = !isBatchDownloading) {
                                    scope.launch {
                                        isBatchDownloading = true
                                        var saved = 0
                                        var failed = 0
                                        missingTopics.forEachIndexed { idx, entry ->
                                            batchProgressLabel = "Saving ${idx + 1}/${missingTopics.size}: ${entry.title}"
                                            downloadingTopicIds[entry.topicId] = true
                                            val result = SubjectSkillCatalog.downloadAndInstallTopicFromGcs(context, entry)
                                            downloadingTopicIds[entry.topicId] = false
                                            if (result.isSuccess) {
                                                offlineStatusMap[entry.topicId] = true
                                                saved++
                                            } else {
                                                failed++
                                            }
                                        }
                                        isBatchDownloading = false
                                        snackbarHostState.showSnackbar(
                                            if (failed == 0) "Downloaded all $saved modules offline!"
                                            else "Saved $saved modules ($failed failed — check internet)"
                                        )
                                    }
                                }
                            ) {
                                if (isBatchDownloading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                            .size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(
                                        text = "Get All",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Quick Subject Navigation Filter Pills + "All 10" Toggle
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(subjects) { index, subj ->
                        val isSelected = !showAllModulesView && pagerState.currentPage == index
                        val accent = subjectBrandColor(subj.subjectId)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                showAllModulesView = false
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            label = {
                                Text(
                                    text = "${subj.iconEmoji} ${subj.title}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = accent
                            )
                        )
                    }

                    item {
                        FilterChip(
                            selected = showAllModulesView,
                            onClick = { showAllModulesView = !showAllModulesView },
                            label = {
                                Text(
                                    text = "All ${allTopicTriples.size} Modules",
                                    fontWeight = if (showAllModulesView) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }

            // 3. Coursera-Style Subject Course Carousel (Swipeable Hero Cards)
            if (subjects.isNotEmpty() && !showAllModulesView) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HorizontalPager(
                            state = pagerState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            pageSpacing = 12.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) { pageIndex ->
                            val subj = subjects[pageIndex]
                            val subjTopics = subj.chapters.flatMap { it.topics }
                            val subjOffline = subjTopics.count { offlineStatusMap[it.topicId] == true }
                            val subjCompleted = subjTopics.count { LessonProgressStore.isTopicCompleted(context, it.topicId) }

                            CourseraSubjectHeroCard(
                                subject = subj,
                                courseIndex = pageIndex + 1,
                                totalCourses = subjects.size,
                                offlineCount = subjOffline,
                                completedCount = subjCompleted,
                                totalModules = subjTopics.size,
                                isActivePage = pagerState.currentPage == pageIndex,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(pageIndex) }
                                }
                            )
                        }

                        // Carousel Page Indicator Dots
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            subjects.forEachIndexed { idx, subj ->
                                val isCurrent = pagerState.currentPage == idx
                                val accent = subjectBrandColor(subj.subjectId)
                                val dotWidth by animateFloatAsState(
                                    targetValue = if (isCurrent) 24f else 7f,
                                    animationSpec = tween(250),
                                    label = "dotW_$idx"
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .width(dotWidth.dp)
                                        .height(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrent) accent else MaterialTheme.colorScheme.outlineVariant)
                                        .clickable {
                                            scope.launch { pagerState.animateScrollToPage(idx) }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Syllabus & Animated Lesson Modules List
            if (!showAllModulesView && subjects.isNotEmpty()) {
                val activeSubject = subjects[pagerState.currentPage.coerceIn(0, subjects.lastIndex)]
                val activeAccent = subjectBrandColor(activeSubject.subjectId)

                item(key = "syllabus_${activeSubject.subjectId}") {
                    AnimatedContent(
                        targetState = activeSubject,
                        transitionSpec = {
                            (fadeIn(tween(260)) + slideInVertically(tween(280)) { it / 14 }) togetherWith fadeOut(tween(150))
                        },
                        label = "subjectSyllabus"
                    ) { subject ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            subject.chapters.forEach { chapter ->
                                ChapterSyllabusBanner(
                                    subjectTitle = subject.title,
                                    chapter = chapter,
                                    accentColor = activeAccent
                                )

                                chapter.topics.forEachIndexed { modIndex, topic ->
                                    StaggeredModuleReveal(index = modIndex) {
                                        CourseModuleCard(
                                            moduleNumber = modIndex + 1,
                                            topic = topic,
                                            accentColor = activeAccent,
                                            isOffline = offlineStatusMap[topic.topicId] == true,
                                            isDownloading = downloadingTopicIds[topic.topicId] == true,
                                            completedSteps = LessonProgressStore.getCompletedStepsForTopic(context, topic.topicId),
                                            isCompleted = LessonProgressStore.isTopicCompleted(context, topic.topicId),
                                            onStartOrDownload = {
                                                if (offlineStatusMap[topic.topicId] == true) {
                                                    onSelectTopic(topic)
                                                } else {
                                                    scope.launch {
                                                        downloadingTopicIds[topic.topicId] = true
                                                        val res = SubjectSkillCatalog.downloadAndInstallTopicFromGcs(context, topic)
                                                        downloadingTopicIds[topic.topicId] = false
                                                        res.onSuccess {
                                                            offlineStatusMap[topic.topicId] = true
                                                            onSelectTopic(topic)
                                                        }.onFailure { err ->
                                                            snackbarHostState.showSnackbar("Download failed: ${err.message ?: "Check internet"} — or try Quick AI")
                                                        }
                                                    }
                                                }
                                            },
                                            onInspectSkill = {
                                                previewTarget = Triple(subject, chapter, topic)
                                            },
                                            onQuickLearn = onQuickLearnTopic?.let { cb -> { cb(topic.title) } }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // "All 10 Modules" Master Syllabus Overview
                subjects.forEach { subj ->
                    val subjAccent = subjectBrandColor(subj.subjectId)
                    item(key = "header_${subj.subjectId}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = subj.iconEmoji, fontSize = 18.sp)
                            Text(
                                text = subj.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = subjAccent.copy(alpha = 0.16f)
                            ) {
                                val count = subj.chapters.sumOf { it.topics.size }
                                Text(
                                    text = "$count Modules",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = subjAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    subj.chapters.forEach { chap ->
                        itemsIndexed(
                            items = chap.topics,
                            key = { _, t -> t.topicId }
                        ) { modIdx, topic ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                CourseModuleCard(
                                    moduleNumber = modIdx + 1,
                                    topic = topic,
                                    accentColor = subjAccent,
                                    isOffline = offlineStatusMap[topic.topicId] == true,
                                    isDownloading = downloadingTopicIds[topic.topicId] == true,
                                    completedSteps = LessonProgressStore.getCompletedStepsForTopic(context, topic.topicId),
                                    isCompleted = LessonProgressStore.isTopicCompleted(context, topic.topicId),
                                    onStartOrDownload = {
                                        if (offlineStatusMap[topic.topicId] == true) {
                                            onSelectTopic(topic)
                                        } else {
                                            scope.launch {
                                                downloadingTopicIds[topic.topicId] = true
                                                val res = SubjectSkillCatalog.downloadAndInstallTopicFromGcs(context, topic)
                                                downloadingTopicIds[topic.topicId] = false
                                                res.onSuccess {
                                                    offlineStatusMap[topic.topicId] = true
                                                    onSelectTopic(topic)
                                                }.onFailure { err ->
                                                    snackbarHostState.showSnackbar("Download failed: ${err.message ?: "Check internet"}")
                                                }
                                            }
                                        }
                                    },
                                    onInspectSkill = {
                                        previewTarget = Triple(subj, chap, topic)
                                    },
                                    onQuickLearn = onQuickLearnTopic?.let { cb -> { cb(topic.title) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Skill Package & Multimedia Inspector Sheet
    val targetInfo = previewTarget
    if (targetInfo != null) {
        val (subj, chap, topic) = targetInfo
        val accent = subjectBrandColor(subj.subjectId)
        val bundle = previewBundle
        ModalBottomSheet(
            onDismissRequest = { previewTarget = null },
            sheetState = previewSheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${subj.iconEmoji} ${subj.title} • SKILL ORCHESTRATION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Text(
                            text = topic.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { previewTarget = null }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Coordinator Philosophy
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Pedagogical Philosophy",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                        Text(
                            text = bundle?.instructions?.socraticSystemPrompt ?: subj.coordinatorPhilosophy,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Learning Objectives
                val objectives = bundle?.instructions?.learningObjectives?.ifEmpty { chap.chapterObjectives } ?: chap.chapterObjectives
                if (objectives.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Learning Objectives",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        objectives.forEach { obj ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00C853),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = obj,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Curated Media Manifest Verification
                if (topic.mediaFiles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Verified Skill Multimedia (${topic.mediaFiles.size} files)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        topic.mediaFiles.forEach { mf ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = mf.relativePath,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${(mf.sizeBytes / 1024).coerceAtLeast(1)} KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accent
                                    )
                                }
                            }
                        }
                    }
                }

                if (bundle != null && bundle.skillMarkdown.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "SKILL.md Specification",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = bundle.skillMarkdown.trim(),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CourseraSubjectHeroCard(
    subject: SubjectCatalogCoordinator,
    courseIndex: Int,
    totalCourses: Int,
    offlineCount: Int,
    completedCount: Int,
    totalModules: Int,
    isActivePage: Boolean,
    onClick: () -> Unit
) {
    val accent = subjectBrandColor(subject.subjectId)
    var showPhilosophy by remember(subject.subjectId) { mutableStateOf(false) }

    val targetProgress = if (totalModules > 0) completedCount.toFloat() / totalModules.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "subjProgress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .reactiveSpringScale(isActive = isActivePage, activeScale = 1.0f, inactiveScale = 0.96f)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = if (isActivePage) 1.5.dp else 1.dp,
            color = accent.copy(alpha = if (isActivePage) 0.7f else 0.3f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .padding(18.dp)
                .animateContentSize()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accent.copy(alpha = 0.24f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = subject.iconEmoji, fontSize = 22.sp)
                        }
                        Column {
                            Text(
                                text = "COURSE $courseIndex OF $totalCourses",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = accent,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = subject.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                    ) {
                        Text(
                            text = "$offlineCount/$totalModules Ready",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Text(
                    text = subject.tagline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                // Coordinator Philosophy toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPhilosophy = !showPhilosophy }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (showPhilosophy) "Teaching Strategy" else "Tap to read teaching approach",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (showPhilosophy) "Hide" else "View",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (showPhilosophy) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = subject.coordinatorPhilosophy,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Course Completion Progress Bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Course Progress",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$completedCount of $totalModules completed",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterSyllabusBanner(
    subjectTitle: String,
    chapter: ChapterCatalogNode,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${subjectTitle.uppercase()} SYLLABUS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                letterSpacing = 0.6.sp
            )
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = chapter.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseModuleCard(
    moduleNumber: Int,
    topic: TopicCatalogEntry,
    accentColor: Color,
    isOffline: Boolean,
    isDownloading: Boolean,
    completedSteps: Int,
    isCompleted: Boolean,
    onStartOrDownload: () -> Unit,
    onInspectSkill: () -> Unit,
    onQuickLearn: (() -> Unit)?
) {
    val sizeKb = (topic.packageSizeBytes / 1024).coerceAtLeast(1)
    val cardBorderColor by animateColorAsState(
        targetValue = when {
            isCompleted -> Color(0xFF00C853).copy(alpha = 0.6f)
            isOffline -> accentColor.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        },
        label = "modBorder"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.2.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "MODULE $moduleNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isCompleted) {
                        StatusBadgeChip(
                            icon = Icons.Default.CheckCircle,
                            label = "Mastered",
                            color = Color(0xFF00E676)
                        )
                    } else if (completedSteps > 0) {
                        StatusBadgeChip(
                            icon = Icons.Default.Verified,
                            label = "Step $completedSteps/3",
                            color = Color(0xFFFFCA28)
                        )
                    } else if (isOffline) {
                        StatusBadgeChip(
                            icon = Icons.Default.CloudDone,
                            label = if (topic.bundledInApk) "Pre-Bundled ($sizeKb KB)" else "Saved ($sizeKb KB)",
                            color = Color(0xFF00E676)
                        )
                    } else {
                        StatusBadgeChip(
                            icon = Icons.Default.CloudDownload,
                            label = "Pack • $sizeKb KB",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Title & Summary
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = topic.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            // Multimedia Capability Row confirming Skill Orchestration
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MediaFeatureTag(icon = Icons.Default.Draw, text = "Interactive Lab")
                MediaFeatureTag(icon = Icons.Default.Image, text = "3 Diagrams")
                MediaFeatureTag(icon = Icons.Default.PlayCircleOutline, text = "12s Video")
                MediaFeatureTag(icon = Icons.Default.Quiz, text = "AI Quiz")
            }

            // Bottom Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onStartOrDownload,
                    enabled = !isDownloading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOffline) accentColor else MaterialTheme.colorScheme.primary,
                        contentColor = if (isOffline) Color(0xFF061618) else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    val btnContentColor = if (isOffline) Color(0xFF061618) else MaterialTheme.colorScheme.onPrimary
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = btnContentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Saving Pack…",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = btnContentColor
                        )
                    } else {
                        Icon(
                            imageVector = if (isOffline) Icons.Default.PlayArrow else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = btnContentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                !isOffline -> "Get & Start ($sizeKb KB)"
                                isCompleted -> "Review Lesson"
                                completedSteps > 0 -> "Continue Lesson"
                                else -> "Start Lesson"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = btnContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onInspectSkill,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Inspect Skill Package",
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                if (onQuickLearn != null && !isOffline) {
                    OutlinedButton(
                        onClick = onQuickLearn,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Quick Learn AI",
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaggeredModuleReveal(
    index: Int,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(index * 70L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 4 }
    ) {
        content()
    }
}

@Composable
private fun StatusBadgeChip(icon: ImageVector, label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun MediaFeatureTag(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun subjectBrandColor(subjectId: String): Color = when (subjectId.lowercase()) {
    "mathematics" -> Color(0xFF26C6DA) // Bright Cyan Teal
    "physics" -> Color(0xFF7986CB)     // Luminous Indigo
    "chemistry" -> Color(0xFF4DB6AC)   // Lab Emerald
    "biology" -> Color(0xFF66BB6A)     // Botanical Green
    "english" -> Color(0xFFBA68C8)     // Literature Plum
    else -> Color(0xFF26C6DA)
}
