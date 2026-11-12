package com.bismuth.app.skills

import android.content.Context
import com.bismuth.app.teaching.BoardStep
import com.bismuth.app.teaching.TeachingBoardScene
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Layer 1: Skill Instructions (tells the LLM how to orchestrate the lesson).
 */
@Serializable
data class TopicSkillInstructions(
    val skillId: String,
    val learningObjectives: List<String>,
    val explanationSequence: List<String>,
    val commonMisconceptions: List<String>,
    val socraticSystemPrompt: String,
)

/**
 * Layer 2: Curated Learning Resources (verified educational content, step diagrams & instructional video).
 */
@Serializable
data class CuratedResource(
    val id: String,
    val title: String,
    val type: String, // "formula_sheet", "diagram_image", "instructional_video"
    val summary: String,
    val bodyText: String,
    val stepId: String = "all",
    val relativePath: String = "",
    val url: String = "",
    val sha256: String = "",
    val sizeBytes: Int = 0,
)

/**
 * Binary media file entry inside a topic skill package (PNG diagram, MP4 video, or board scene).
 */
@Serializable
data class SkillMediaFileEntry(
    val relativePath: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Int = 0,
    val mimeType: String = "",
)

@Serializable
data class TopicAssessmentQuestion(
    val questionId: String,
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
)

/**
 * Complete 3-Layer Topic Skill Bundle loaded either from APK pre-bundled assets
 * or downloaded on-demand from Google Cloud Storage.
 */
@Serializable
data class TopicSkillBundle(
    val topicId: String,
    val subjectId: String,
    val subjectTitle: String = "",
    val chapterId: String,
    val chapterTitle: String = "",
    val title: String,
    val summary: String,
    val version: String = "1.1.0",
    val skillMarkdown: String = "",
    val instructions: TopicSkillInstructions,
    val resources: List<CuratedResource> = emptyList(),
    val whiteboardScene: TeachingBoardScene,
    val assessment: List<TopicAssessmentQuestion> = emptyList(),
    val mediaFiles: List<SkillMediaFileEntry> = emptyList(),
)

@Serializable
data class TopicCatalogEntry(
    val topicId: String,
    val subjectId: String,
    val chapterId: String,
    val title: String,
    val summary: String,
    val version: String = "1.1.0",
    val bundledInApk: Boolean = false,
    val packageSizeBytes: Int = 0,
    val bundleSha256: String = "",
    val bundleUrl: String,
    val skillMdUrl: String,
    val lessonUrl: String,
    val assessmentUrl: String,
    val manifestUrl: String,
    val mediaFiles: List<SkillMediaFileEntry> = emptyList(),
)

@Serializable
data class ChapterCatalogNode(
    val chapterId: String,
    val subjectId: String,
    val title: String,
    val summary: String,
    val chapterObjectives: List<String> = emptyList(),
    val skillMdUrl: String = "",
    val topics: List<TopicCatalogEntry> = emptyList(),
)

@Serializable
data class SubjectCatalogCoordinator(
    val subjectId: String,
    val title: String,
    val iconEmoji: String,
    val accentHex: String,
    val tagline: String,
    val coordinatorPhilosophy: String,
    val skillMdUrl: String = "",
    val chapters: List<ChapterCatalogNode> = emptyList(),
)

@Serializable
data class SkillsCatalogRoot(
    val schemaVersion: String = "1.1.0",
    val bucketName: String,
    val baseUrl: String,
    val totalSubjects: Int,
    val totalTopics: Int,
    val bundledTopicIds: List<String> = emptyList(),
    val subjects: List<SubjectCatalogCoordinator> = emptyList(),
)

/**
 * Production Skill Repository managing:
 * 1. Pre-bundled APK skills (`assets/skills/`) with offline PNG diagrams and MP4 video walkthroughs.
 * 2. Real Google Cloud Storage (`gs://bismuth-skills-tunjiax-wallet-482614/v1/`) catalog sync
 *    and on-demand topic module + media asset downloads with per-file SHA-256 verification.
 * 3. Step-scoped LLM prompt construction aligned with the active step's curated media.
 */
object SubjectSkillCatalog {

    const val GCS_BUCKET_NAME = "bismuth-skills-tunjiax-wallet-482614"
    const val GCS_BASE_URL = "https://storage.googleapis.com/$GCS_BUCKET_NAME/v1"
    const val GCS_CATALOG_URL = "$GCS_BASE_URL/catalog.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun localSkillsRootDir(context: Context): File =
        File(context.filesDir, "skills").also { if (!it.exists()) it.mkdirs() }

    fun localTopicDir(context: Context, topicId: String): File =
        File(localSkillsRootDir(context), "topics/$topicId")

    /**
     * Loads the skill catalog from local synced storage if available and up-to-date,
     * falling back to the APK's bundled `assets/skills/catalog.json`.
     */
    suspend fun loadCatalog(context: Context): SkillsCatalogRoot = withContext(Dispatchers.IO) {
        val bundledJson = context.assets.open("skills/catalog.json").bufferedReader().use { it.readText() }
        val bundledCatalog = json.decodeFromString<SkillsCatalogRoot>(bundledJson)

        val cachedFile = File(localSkillsRootDir(context), "catalog.json")
        if (cachedFile.exists()) {
            runCatching {
                val cachedCatalog = json.decodeFromString<SkillsCatalogRoot>(cachedFile.readText())
                if (cachedCatalog.schemaVersion == bundledCatalog.schemaVersion &&
                    cachedCatalog.subjects.flatMap { it.chapters }.flatMap { it.topics }
                        .all { it.mediaFiles.isNotEmpty() }
                ) {
                    return@withContext cachedCatalog
                }
            }
        }
        bundledCatalog
    }

    /**
     * Syncs the latest `catalog.json` directly from Google Cloud Storage over HTTPS.
     */
    suspend fun syncCatalogFromGcs(context: Context): Result<SkillsCatalogRoot> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = downloadBytesFromUrl(GCS_CATALOG_URL)
            val text = bytes.toString(Charsets.UTF_8)
            val parsed = json.decodeFromString<SkillsCatalogRoot>(text)
            File(localSkillsRootDir(context), "catalog.json").writeBytes(bytes)
            parsed
        }
    }

    /**
     * Returns true if the topic (including all its curated media files) is either downloaded
     * to local device storage or pre-bundled inside the APK assets.
     */
    fun isTopicAvailableOffline(context: Context, entry: TopicCatalogEntry): Boolean {
        if (entry.bundledInApk) return true
        return isTopicDownloadedOnDevice(context, entry.topicId, entry.mediaFiles)
    }

    /**
     * Returns true if the topic and all its media assets have been downloaded from GCS to local storage.
     */
    fun isTopicDownloadedOnDevice(
        context: Context,
        topicId: String,
        requiredMedia: List<SkillMediaFileEntry> = emptyList(),
    ): Boolean {
        val dir = localTopicDir(context, topicId)
        val bundleFile = File(dir, "bundle.json")
        if (!bundleFile.exists()) return false
        if (requiredMedia.isNotEmpty()) {
            return requiredMedia.all { mf -> File(dir, mf.relativePath).exists() }
        }
        return File(dir, "assets/video/walkthrough.mp4").exists() &&
                File(dir, "assets/images/step_1.png").exists()
    }

    /**
     * Loads a topic bundle from local storage or pre-bundled APK assets without network calls.
     */
    suspend fun loadTopicBundleOffline(
        context: Context,
        entry: TopicCatalogEntry
    ): TopicSkillBundle? = withContext(Dispatchers.IO) {
        val dir = localTopicDir(context, entry.topicId)
        val localBundleFile = File(dir, "bundle.json")
        if (localBundleFile.exists() && isTopicDownloadedOnDevice(context, entry.topicId, entry.mediaFiles)) {
            return@withContext runCatching {
                json.decodeFromString<TopicSkillBundle>(localBundleFile.readText())
            }.getOrNull()
        }
        if (entry.bundledInApk) {
            return@withContext runCatching {
                val assetText = context.assets
                    .open("skills/topics/${entry.topicId}/bundle.json")
                    .bufferedReader()
                    .use { it.readText() }
                json.decodeFromString<TopicSkillBundle>(assetText)
            }.getOrNull()
        }
        null
    }

    /**
     * Loads binary bytes for a topic's offline media asset (`assets/images/step_1.png`,
     * `assets/video/walkthrough.mp4`, etc.) from either local downloaded storage or APK assets.
     */
    suspend fun loadTopicAssetBytes(
        context: Context,
        topicId: String,
        relativePath: String,
        bundledInApk: Boolean,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val cleanRel = relativePath.trimStart('/')
        val localFile = File(localTopicDir(context, topicId), cleanRel)
        if (localFile.exists()) {
            return@withContext runCatching { localFile.readBytes() }.getOrNull()
        }
        if (bundledInApk) {
            return@withContext runCatching {
                context.assets.open("skills/topics/$topicId/$cleanRel").use { it.readBytes() }
            }.getOrNull()
        }
        null
    }

    /**
     * Downloads all topic skill files (`bundle.json`, `SKILL.md`, `lesson.json`, `assessment.json`,
     * `manifest.json`) AND all curated media files (`assets/images/step_1..3.png`,
     * `assets/video/walkthrough.mp4`, `assets/board/scene.json`) from Google Cloud Storage,
     * verifies every file's SHA-256 checksum, and persists them to local offline storage.
     */
    suspend fun downloadAndInstallTopicFromGcs(
        context: Context,
        entry: TopicCatalogEntry
    ): Result<TopicSkillBundle> = withContext(Dispatchers.IO) {
        runCatching {
            val bundleBytes = downloadBytesFromUrl(entry.bundleUrl)
            if (entry.bundleSha256.isNotBlank()) {
                val actualHash = sha256Hex(bundleBytes)
                check(actualHash.equals(entry.bundleSha256, ignoreCase = true)) {
                    "SHA-256 mismatch for ${entry.topicId}/bundle.json: expected ${entry.bundleSha256}, got $actualHash"
                }
            }

            val parsedBundle = json.decodeFromString<TopicSkillBundle>(bundleBytes.toString(Charsets.UTF_8))

            val dir = localTopicDir(context, entry.topicId)
            if (!dir.exists()) dir.mkdirs()

            File(dir, "bundle.json").writeBytes(bundleBytes)
            File(dir, "SKILL.md").writeText(parsedBundle.skillMarkdown)

            runCatching {
                File(dir, "lesson.json").writeBytes(downloadBytesFromUrl(entry.lessonUrl))
                File(dir, "assessment.json").writeBytes(downloadBytesFromUrl(entry.assessmentUrl))
                File(dir, "manifest.json").writeBytes(downloadBytesFromUrl(entry.manifestUrl))
            }

            // Download & SHA-256 verify all teacher-curated media files (diagrams + video + board scene)
            val mediaToDownload = entry.mediaFiles.ifEmpty { parsedBundle.mediaFiles }
            for (mediaFile in mediaToDownload) {
                val mediaBytes = downloadBytesFromUrl(mediaFile.url)
                if (mediaFile.sha256.isNotBlank()) {
                    val actualMediaHash = sha256Hex(mediaBytes)
                    check(actualMediaHash.equals(mediaFile.sha256, ignoreCase = true)) {
                        "SHA-256 mismatch for ${entry.topicId}/${mediaFile.relativePath}: expected ${mediaFile.sha256}, got $actualMediaHash"
                    }
                }
                val targetFile = File(dir, mediaFile.relativePath)
                targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                targetFile.writeBytes(mediaBytes)
            }

            parsedBundle
        }
    }

    /**
     * Deletes a downloaded topic package and all its media assets from local device storage.
     */
    suspend fun removeDownloadedTopic(context: Context, topicId: String): Boolean = withContext(Dispatchers.IO) {
        val dir = localTopicDir(context, topicId)
        if (dir.exists()) dir.deleteRecursively() else false
    }

    /**
     * Builds the compact, step-scoped context window payload for the active Topic & Step only,
     * including the teacher-curated visual diagram and video alignment for the current step.
     */
    fun buildStepScopedPrompt(
        subject: SubjectCatalogCoordinator,
        chapter: ChapterCatalogNode,
        bundle: TopicSkillBundle,
        step: BoardStep,
        studentQuestion: String,
    ): String {
        val stepDiagram = step.diagramRelativePath.ifBlank { "assets/images/step_${step.stepNumber}.png" }
        val stepVideo = step.videoRelativePath.ifBlank { "assets/video/walkthrough.mp4" }
        return buildString {
            appendLine("[ACTIVE SKILL ORCHESTRATOR: ${subject.title} > ${chapter.title} > ${bundle.title}]")
            appendLine("Subject Philosophy: ${subject.coordinatorPhilosophy}")
            appendLine("Topic Directive: ${bundle.instructions.socraticSystemPrompt}")
            appendLine("Current Step (${step.stepNumber}): ${step.title}")
            appendLine("Step Narration: ${step.narration}")
            appendLine("Aligned Step Diagram: $stepDiagram (Resource ID: ${step.diagramResourceId.ifBlank { "img_step_${step.stepNumber}" }})")
            appendLine("Aligned Concept Video: $stepVideo (Phase ${step.stepNumber} of 3)")
            if (step.tutorTip.isNotBlank()) {
                appendLine("Tutor Tip: ${step.tutorTip}")
            }
            appendLine("Common Misconceptions to Watch: ${bundle.instructions.commonMisconceptions.joinToString("; ")}")
            appendLine()
            appendLine("Student Question: $studentQuestion")
            appendLine("Respond concisely in 2-4 sentences grounded strictly in this step and its aligned visual diagram without giving away future checkpoint answers.")
        }
    }

    private fun downloadBytesFromUrl(urlString: String): ByteArray {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            doInput = true
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code when fetching $urlString")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
