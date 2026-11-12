package com.bismuth.app.document

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bismuth.app.ui.animation.CelebrationBurstOverlay
import com.bismuth.app.ui.animation.ScannerBeamView
import androidx.compose.ui.unit.sp
import com.bismuth.app.engine.BismuthEngine
import com.bismuth.app.engine.BismuthModelConfig
import com.bismuth.app.ui.animation.LottieEffectView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DocumentQuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class ChapterBoundary(
    val chapterNumber: Int,
    val title: String,
    val startPage: Int,
    val endPage: Int,
    val content: String
)

data class LoadedDocumentSource(
    val id: String,
    val fileName: String,
    val totalPages: Int,
    val chapters: List<ChapterBoundary>,
    val pageTexts: Map<Int, String>,
    val isAppInternalStorage: Boolean = false
)

enum class QuizDifficulty(val label: String) {
    FOUNDATIONAL("Foundational"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced")
}

enum class RangeSelectionMode(val label: String) {
    CHAPTER_RANGE("Chapter Range"),
    PAGE_RANGE("Selected Pages")
}

data class ParsedVoiceDocCommand(
    val matchedDocQuery: String?,
    val mode: RangeSelectionMode,
    val startChapter: Int?,
    val endChapter: Int?,
    val pageCountOrEnd: Int?,
    val questionCount: Int?,
    val difficulty: QuizDifficulty?,
    val action: String
)

object PersistentDocumentStore {
    private fun getDocDir(context: Context): File {
        val dir = File(context.filesDir, "imported_documents")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun importAndPersistPdf(context: Context, uri: Uri): LoadedDocumentSource? = withContext(Dispatchers.IO) {
        val rawName = PdfDocumentExtractor.resolveUriFileName(context, uri) ?: "Document_${System.currentTimeMillis()}.pdf"
        val cleanName = rawName.replace(Regex("""[^a-zA-Z0-9._\-]"""), "_")
        val targetFile = File(getDocDir(context), cleanName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            PdfDocumentExtractor.extractPdfFile(context, targetFile, rawName, isInternal = true)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadPersistedDocuments(context: Context): List<LoadedDocumentSource> = withContext(Dispatchers.IO) {
        val dir = getDocDir(context)
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".pdf", ignoreCase = true) } ?: emptyList()
        files.mapNotNull { PdfDocumentExtractor.extractPdfFile(context, it, it.name, isInternal = true) }
    }

    suspend fun deleteDocument(context: Context, doc: LoadedDocumentSource): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(doc.id)
            if (file.exists() && file.parentFile?.absolutePath == getDocDir(context).absolutePath) {
                file.delete()
            } else false
        } catch (_: Exception) {
            false
        }
    }
}

object PdfDocumentExtractor {
    private val chapterRegex = Regex(
        """(?im)^\s*(?:chapter|unit|module|section)\s+(\d+)\b[:\s\-]*(.*)$"""
    )

    suspend fun scanDevicePdfFiles(): List<File> = withContext(Dispatchers.IO) {
        val roots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        )
        val found = mutableListOf<File>()
        roots.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.name.endsWith(".pdf", ignoreCase = true)) {
                        found.add(f)
                    }
                }
            }
        }
        found.take(15)
    }

    private fun extractWithPdfBox(
        context: Context,
        file: File,
        displayName: String,
        isInternal: Boolean
    ): LoadedDocumentSource? {
        return try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { document ->
                val totalPages = document.numberOfPages.coerceAtLeast(1)
                val maxInspectPages = totalPages.coerceAtMost(60)
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper().apply {
                    sortByPosition = true
                }
                val pageTexts = mutableMapOf<Int, String>()
                var nonBlankPages = 0
                for (pageNum in 1..maxInspectPages) {
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum
                    val extracted = try {
                        stripper.getText(document).trim()
                    } catch (_: Throwable) {
                        ""
                    }
                    if (extracted.isNotBlank()) {
                        nonBlankPages++
                        pageTexts[pageNum] = extracted
                    } else {
                        pageTexts[pageNum] = "Page $pageNum of $displayName (no selectable text extracted)."
                    }
                }
                if (nonBlankPages == 0 && Build.VERSION.SDK_INT >= 35) {
                    return null
                }
                val detectedChapters = detectChaptersFromPages(pageTexts, totalPages)
                LoadedDocumentSource(
                    id = file.absolutePath,
                    fileName = displayName,
                    totalPages = totalPages,
                    chapters = detectedChapters,
                    pageTexts = pageTexts,
                    isAppInternalStorage = isInternal
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun extractPdfFile(
        context: Context,
        file: File,
        displayName: String = file.name,
        isInternal: Boolean = false
    ): LoadedDocumentSource? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null

        val pdfBoxResult = extractWithPdfBox(context, file, displayName, isInternal)
        if (pdfBoxResult != null) {
            return@withContext pdfBoxResult
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount.coerceAtLeast(1)
            val maxInspectPages = totalPages.coerceAtMost(60)
            val pageTexts = mutableMapOf<Int, String>()

            for (i in 0 until maxInspectPages) {
                val pageNum = i + 1
                val page = renderer.openPage(i)
                val extracted = try {
                    if (Build.VERSION.SDK_INT >= 35) {
                        page.textContents.joinToString("\n") { it.text }.trim()
                    } else {
                        ""
                    }
                } catch (_: Throwable) {
                    ""
                } finally {
                    page.close()
                }
                pageTexts[pageNum] = if (extracted.isNotBlank()) {
                    extracted
                } else {
                    "Page $pageNum of $displayName (no selectable text extracted)."
                }
            }

            val detectedChapters = detectChaptersFromPages(pageTexts, totalPages)
            LoadedDocumentSource(
                id = file.absolutePath,
                fileName = displayName,
                totalPages = totalPages,
                chapters = detectedChapters,
                pageTexts = pageTexts,
                isAppInternalStorage = isInternal
            )
        } catch (_: Exception) {
            null
        } finally {
            try {
                renderer?.close()
            } catch (_: Exception) {
            }
            try {
                pfd?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun detectChaptersFromPages(
        pageTexts: Map<Int, String>,
        totalPages: Int
    ): List<ChapterBoundary> {
        val rawHits = mutableListOf<Triple<Int, String, Int>>()
        pageTexts.toSortedMap().forEach { (pageNum, text) ->
            val match = chapterRegex.find(text)
            if (match != null) {
                val chNum = match.groupValues[1].toIntOrNull() ?: (rawHits.size + 1)
                val suffix = match.groupValues.getOrNull(2)?.trim().orEmpty()
                val title = if (suffix.isNotBlank()) "Chapter $chNum: $suffix" else "Chapter $chNum"
                if (rawHits.none { it.first == chNum }) {
                    rawHits.add(Triple(chNum, title, pageNum))
                }
            }
        }

        if (rawHits.isNotEmpty()) {
            return rawHits.mapIndexed { idx, hit ->
                val startPage = hit.third
                val endPage = if (idx + 1 < rawHits.size) {
                    (rawHits[idx + 1].third - 1).coerceAtLeast(startPage)
                } else {
                    totalPages
                }
                val combined = (startPage..endPage).mapNotNull { pageTexts[it] }.joinToString("\n\n")
                ChapterBoundary(
                    chapterNumber = hit.first,
                    title = hit.second,
                    startPage = startPage,
                    endPage = endPage,
                    content = combined
                )
            }
        }

        val pagesPerSegment = 5
        val segments = mutableListOf<ChapterBoundary>()
        var pageCursor = 1
        var chIdx = 1
        while (pageCursor <= totalPages && chIdx <= 12) {
            val end = (pageCursor + pagesPerSegment - 1).coerceAtMost(totalPages)
            val combined = (pageCursor..end).mapNotNull { pageTexts[it] }.joinToString("\n\n")
            segments.add(
                ChapterBoundary(
                    chapterNumber = chIdx,
                    title = "Chapter $chIdx (Pages $pageCursor–$end)",
                    startPage = pageCursor,
                    endPage = end,
                    content = combined
                )
            )
            pageCursor = end + 1
            chIdx++
        }
        return segments
    }

    fun resolveUriFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

object VoiceDocumentCommandParser {
    private val wordNumbers = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
    )

    fun parse(command: String): ParsedVoiceDocCommand {
        val lower = normalizeNumberWords(command.lowercase())

        val docQuery = when {
            "chemistry" in lower -> "chemistry"
            "biology" in lower -> "biology"
            "physics" in lower -> "physics"
            "math" in lower -> "math"
            else -> Regex("""open\s+(?:the\s+)?([a-z0-9_\-]+)\s*(?:pdf)?""").find(lower)?.groupValues?.getOrNull(1)
        }

        val chapterRangeMatch = Regex("""chapters?\s+(\d+)\s*(?:through|to|\-|and)\s*(\d+)""").find(lower)
        val singleChapterMatch = Regex("""chapter\s+(\d+)""").find(lower)
        val selectedPagesMatch = Regex("""(\d+)\s+selected\s+pages?""").find(lower)
            ?: Regex("""first\s+(\d+)\s+pages?""").find(lower)
            ?: Regex("""using\s+(\d+)\s+pages?""").find(lower)
        val pageRangeMatch = Regex("""pages?\s+(\d+)\s*(?:through|to|\-)\s*(\d+)""").find(lower)

        val questionCountMatch = Regex("""(\d+)\s+questions?""").find(lower)
        val difficulty = when {
            "advanced" in lower || "hard" in lower -> QuizDifficulty.ADVANCED
            "foundational" in lower || "easy" in lower || "basic" in lower -> QuizDifficulty.FOUNDATIONAL
            "intermediate" in lower || "medium" in lower -> QuizDifficulty.INTERMEDIATE
            else -> null
        }

        val action = if ("summar" in lower && "quiz" !in lower) "SUMMARY" else "QUIZ"

        return when {
            chapterRangeMatch != null -> {
                val startCh = chapterRangeMatch.groupValues[1].toIntOrNull() ?: 1
                val endCh = chapterRangeMatch.groupValues[2].toIntOrNull() ?: startCh
                ParsedVoiceDocCommand(
                    matchedDocQuery = docQuery,
                    mode = RangeSelectionMode.CHAPTER_RANGE,
                    startChapter = startCh,
                    endChapter = endCh,
                    pageCountOrEnd = null,
                    questionCount = questionCountMatch?.groupValues?.get(1)?.toIntOrNull(),
                    difficulty = difficulty,
                    action = action
                )
            }
            singleChapterMatch != null -> {
                val ch = singleChapterMatch.groupValues[1].toIntOrNull() ?: 1
                ParsedVoiceDocCommand(
                    matchedDocQuery = docQuery,
                    mode = RangeSelectionMode.CHAPTER_RANGE,
                    startChapter = ch,
                    endChapter = ch,
                    pageCountOrEnd = null,
                    questionCount = questionCountMatch?.groupValues?.get(1)?.toIntOrNull(),
                    difficulty = difficulty,
                    action = action
                )
            }
            selectedPagesMatch != null -> {
                val pages = selectedPagesMatch.groupValues[1].toIntOrNull() ?: 5
                ParsedVoiceDocCommand(
                    matchedDocQuery = docQuery,
                    mode = RangeSelectionMode.PAGE_RANGE,
                    startChapter = null,
                    endChapter = null,
                    pageCountOrEnd = pages,
                    questionCount = questionCountMatch?.groupValues?.get(1)?.toIntOrNull(),
                    difficulty = difficulty,
                    action = action
                )
            }
            pageRangeMatch != null -> {
                val endPage = pageRangeMatch.groupValues[2].toIntOrNull() ?: 5
                ParsedVoiceDocCommand(
                    matchedDocQuery = docQuery,
                    mode = RangeSelectionMode.PAGE_RANGE,
                    startChapter = null,
                    endChapter = null,
                    pageCountOrEnd = endPage,
                    questionCount = questionCountMatch?.groupValues?.get(1)?.toIntOrNull(),
                    difficulty = difficulty,
                    action = action
                )
            }
            else -> ParsedVoiceDocCommand(
                matchedDocQuery = docQuery,
                mode = RangeSelectionMode.CHAPTER_RANGE,
                startChapter = 1,
                endChapter = 2,
                pageCountOrEnd = null,
                questionCount = questionCountMatch?.groupValues?.get(1)?.toIntOrNull(),
                difficulty = difficulty,
                action = action
            )
        }
    }

    private fun normalizeNumberWords(input: String): String {
        var result = input
        wordNumbers.forEach { (word, digit) ->
            result = result.replace(Regex("\\b$word\\b"), digit.toString())
        }
        return result
    }
}

private val quizJsonParser = Json { ignoreUnknownKeys = true }

@Composable
fun DocumentFeatureScreen(
    selectedModel: BismuthModelConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val availableDocs = remember { mutableStateListOf<LoadedDocumentSource>() }
    var activeDoc by remember { mutableStateOf<LoadedDocumentSource?>(null) }

    var rangeMode by remember { mutableStateOf(RangeSelectionMode.CHAPTER_RANGE) }
    var startChapter by remember(activeDoc) { mutableIntStateOf(1) }
    var endChapter by remember(activeDoc) {
        mutableIntStateOf(activeDoc?.chapters?.size?.coerceAtMost(4) ?: 1)
    }
    var selectedPageCount by remember(activeDoc) {
        mutableIntStateOf(5.coerceAtMost(activeDoc?.totalPages ?: 1))
    }
    var questionCount by remember { mutableIntStateOf(5) }
    var difficulty by remember { mutableStateOf(QuizDifficulty.INTERMEDIATE) }

    var voiceCommandText by remember { mutableStateOf("") }
    var commandFeedbackBanner by remember { mutableStateOf<String?>(null) }
    var docErrorMessage by remember { mutableStateOf<String?>(null) }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedQuiz by remember { mutableStateOf<List<DocumentQuizQuestion>>(emptyList()) }
    var generatedSummary by remember { mutableStateOf<String?>(null) }
    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }
    var quizSubmitted by remember { mutableStateOf(false) }

    // Load persisted documents from app internal storage + scan device public folders on startup
    LaunchedEffect(Unit) {
        val persisted = PersistentDocumentStore.loadPersistedDocuments(context)
        val deviceFiles = PdfDocumentExtractor.scanDevicePdfFiles()
        val deviceDocs = deviceFiles.mapNotNull { PdfDocumentExtractor.extractPdfFile(context, it, it.name) }

        val combined = (persisted + deviceDocs).distinctBy { it.id }
        availableDocs.clear()
        availableDocs.addAll(combined)
        if (combined.isNotEmpty()) {
            activeDoc = combined.first()
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val extracted = PersistentDocumentStore.importAndPersistPdf(context, uri)
                if (extracted != null) {
                    val existingIdx = availableDocs.indexOfFirst { it.id == extracted.id }
                    if (existingIdx >= 0) {
                        availableDocs[existingIdx] = extracted
                    } else {
                        availableDocs.add(0, extracted)
                    }
                    activeDoc = extracted
                    startChapter = 1
                    endChapter = extracted.chapters.size.coerceAtMost(4)
                    selectedPageCount = 5.coerceAtMost(extracted.totalPages)
                    commandFeedbackBanner = "Saved to Library: ${extracted.fileName} (${extracted.totalPages} pages, ${extracted.chapters.size} segments)"
                    docErrorMessage = null
                } else {
                    docErrorMessage = "Unable to read and persist selected PDF file. Please verify file permissions."
                }
            }
        }
    }

    fun buildTargetContentSlice(doc: LoadedDocumentSource): Pair<String, String> {
        return if (rangeMode == RangeSelectionMode.CHAPTER_RANGE) {
            val s = startChapter.coerceIn(1, doc.chapters.size.coerceAtLeast(1))
            val e = endChapter.coerceIn(s, doc.chapters.size.coerceAtLeast(1))
            val chaptersSlice = doc.chapters.filter { it.chapterNumber in s..e }
            val label = "Chapters $s through $e (${doc.fileName})"
            val text = chaptersSlice.joinToString("\n\n") { "${it.title}\n${it.content}" }
            label to text
        } else {
            val maxP = selectedPageCount.coerceIn(1, doc.totalPages.coerceAtLeast(1))
            val label = "Selected Pages 1–$maxP (${doc.fileName})"
            val text = (1..maxP).mapNotNull { doc.pageTexts[it] }.joinToString("\n\n")
            label to text
        }
    }

    fun triggerQuizGeneration(doc: LoadedDocumentSource) {
        scope.launch {
            isGenerating = true
            docErrorMessage = null
            generatedSummary = null
            selectedAnswers.clear()
            quizSubmitted = false
            val (scopeLabel, rawSourceText) = buildTargetContentSlice(doc)
            val maxContextChars = 6_500
            val sourceText = if (rawSourceText.length > maxContextChars) {
                rawSourceText.take(maxContextChars) + "\n[Truncated to fit on-device context window]"
            } else {
                rawSourceText
            }
            val profile = com.bismuth.app.profile.StudentProfilePrefs.getProfile(context)
            val languageRule = com.bismuth.app.profile.StudentProfilePrefs.languageInstruction(profile.preferredLanguage)
            val prompt = """
                You are an academic examination board.
                Student Level: ${profile.academicLevel}.
                Language Requirement: $languageRule
                Generate a $questionCount-question multiple-choice quiz at ${difficulty.label} difficulty level strictly using the provided textbook content:
                
                Content Slice ($scopeLabel):
                $sourceText
                
                Output MUST be a valid JSON array of objects with fields:
                - question: string
                - options: array of exactly 4 strings
                - correctIndex: integer from 0 to 3
                - explanation: concise explanation based on the text
            """.trimIndent()

            val rawModelResponse = BismuthEngine.generateTextOrNull(
                context = context,
                prompt = prompt,
                config = selectedModel
            )

            val parsedQuiz = parseJsonQuiz(rawModelResponse, questionCount)
            if (parsedQuiz.isNotEmpty()) {
                generatedQuiz = parsedQuiz
            } else {
                docErrorMessage = "Could not generate quiz: Make sure an offline AI is saved in AI Brains."
            }
            isGenerating = false
        }
    }

    fun triggerSummaryGeneration(doc: LoadedDocumentSource) {
        scope.launch {
            isGenerating = true
            docErrorMessage = null
            generatedQuiz = emptyList()
            val (scopeLabel, rawSourceText) = buildTargetContentSlice(doc)
            val maxContextChars = 6_500
            val sourceText = if (rawSourceText.length > maxContextChars) {
                rawSourceText.take(maxContextChars) + "\n[Truncated to fit on-device context window]"
            } else {
                rawSourceText
            }
            val profile = com.bismuth.app.profile.StudentProfilePrefs.getProfile(context)
            val languageRule = com.bismuth.app.profile.StudentProfilePrefs.languageInstruction(profile.preferredLanguage)
            val prompt = """
                You are an academic scholar.
                Student Level: ${profile.academicLevel}.
                Language Requirement: $languageRule
                Summarize $scopeLabel into structured academic study notes with:
                1. Core Concepts & Definitions
                2. Key Mechanisms & Relationships
                3. Critical Exam Takeaways
                
                Document Text:
                $sourceText
            """.trimIndent()

            val rawModelResponse = BismuthEngine.generateTextOrNull(
                context = context,
                prompt = prompt,
                config = selectedModel
            )

            if (!rawModelResponse.isNullOrBlank()) {
                generatedSummary = rawModelResponse
            } else {
                docErrorMessage = "Could not generate summary: Make sure an offline AI is saved in AI Brains."
            }
            isGenerating = false
        }
    }

    fun executeNaturalLanguageVoiceCommand(rawCommand: String) {
        val current = activeDoc
        if (current == null) {
            docErrorMessage = "Please load a PDF document first before running voice commands."
            return
        }

        val parsed = VoiceDocumentCommandParser.parse(rawCommand)
        var targetDoc = current
        if (parsed.matchedDocQuery != null) {
            val matched = availableDocs.firstOrNull {
                it.fileName.contains(parsed.matchedDocQuery, ignoreCase = true)
            }
            if (matched != null) {
                targetDoc = matched
                activeDoc = matched
            }
        }
        rangeMode = parsed.mode
        if (parsed.mode == RangeSelectionMode.CHAPTER_RANGE) {
            val maxCh = targetDoc.chapters.size.coerceAtLeast(1)
            startChapter = (parsed.startChapter ?: 1).coerceIn(1, maxCh)
            endChapter = (parsed.endChapter ?: maxCh).coerceIn(startChapter, maxCh)
        } else {
            selectedPageCount = (parsed.pageCountOrEnd ?: 5).coerceIn(1, targetDoc.totalPages)
        }
        parsed.questionCount?.let { questionCount = it.coerceIn(3, 10) }
        parsed.difficulty?.let { difficulty = it }

        commandFeedbackBanner = buildString {
            append("Action: ${targetDoc.fileName} • ")
            if (rangeMode == RangeSelectionMode.CHAPTER_RANGE) {
                append("Chapters $startChapter–$endChapter • ")
            } else {
                append("$selectedPageCount Pages • ")
            }
            append("$questionCount Qs (${difficulty.label})")
        }

        if (parsed.action == "SUMMARY") {
            triggerSummaryGeneration(targetDoc)
        } else {
            triggerQuizGeneration(targetDoc)
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                voiceCommandText = spoken
                executeNaturalLanguageVoiceCommand(spoken)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
        Text(
            text = "Document Analysis & Quiz Studio",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Ingest real academic textbooks and PDFs. Segment by chapter boundaries or page ranges, generate AI quizzes offline, and produce grounded summaries.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Document Management & Picker Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Document Library",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (availableDocs.isEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Local PDF Documents Loaded",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Click 'Upload PDF' above to import and permanently store a textbook from your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stored Documents (${availableDocs.size}):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        activeDoc?.let { current ->
                            if (current.isAppInternalStorage) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            PersistentDocumentStore.deleteDocument(context, current)
                                            availableDocs.remove(current)
                                            activeDoc = availableDocs.firstOrNull()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Document",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableDocs.forEach { doc ->
                            val isDocSelected = doc.id == activeDoc?.id
                            FilterChip(
                                selected = isDocSelected,
                                onClick = {
                                    activeDoc = doc
                                    startChapter = 1
                                    endChapter = doc.chapters.size.coerceAtMost(4)
                                    selectedPageCount = 5.coerceAtMost(doc.totalPages)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "${doc.fileName} (${doc.totalPages}p)",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Active Document Controls & Voice Command Bar
        activeDoc?.let { doc ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Voice Command Bar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = voiceCommandText,
                            onValueChange = { voiceCommandText = it },
                            placeholder = {
                                Text("e.g. Generate a quiz based on Chapters 1 through 4", style = MaterialTheme.typography.bodySmall)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(
                                            RecognizerIntent.EXTRA_PROMPT,
                                            "Say e.g. 'Generate a 5-question quiz for Chapter 1'"
                                        )
                                    }
                                    speechRecognizerLauncher.launch(intent)
                                } catch (_: Exception) {
                                    if (voiceCommandText.isNotBlank()) {
                                        executeNaturalLanguageVoiceCommand(voiceCommandText)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Speak Command",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick-action chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                val cmd = "Generate a quiz based on Chapters 1 through 2"
                                voiceCommandText = cmd
                                executeNaturalLanguageVoiceCommand(cmd)
                            },
                            label = { Text("Quiz: Ch 1–2", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                val cmd = "Create a quiz using five selected pages"
                                voiceCommandText = cmd
                                executeNaturalLanguageVoiceCommand(cmd)
                            },
                            label = { Text("Quiz: 5 Pages", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                val cmd = "Summarize Chapters 1 through 2"
                                voiceCommandText = cmd
                                executeNaturalLanguageVoiceCommand(cmd)
                            },
                            label = { Text("Summarize: Ch 1–2", style = MaterialTheme.typography.labelSmall) }
                        )
                    }

                    if (voiceCommandText.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { executeNaturalLanguageVoiceCommand(voiceCommandText) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Voice Command", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (!commandFeedbackBanner.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = commandFeedbackBanner!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Manual Scope Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RangeSelectionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = rangeMode == mode,
                                onClick = { rangeMode = mode },
                                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (rangeMode == RangeSelectionMode.CHAPTER_RANGE) {
                        Text(
                            text = "Chapter Range (${doc.chapters.size} Segments detected):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("From:", style = MaterialTheme.typography.labelSmall)
                            doc.chapters.forEach { ch ->
                                FilterChip(
                                    selected = startChapter == ch.chapterNumber,
                                    onClick = {
                                        startChapter = ch.chapterNumber
                                        if (endChapter < startChapter) endChapter = startChapter
                                    },
                                    label = { Text("Ch ${ch.chapterNumber}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("To:", style = MaterialTheme.typography.labelSmall)
                            doc.chapters.forEach { ch ->
                                FilterChip(
                                    selected = endChapter == ch.chapterNumber,
                                    onClick = {
                                        endChapter = ch.chapterNumber.coerceAtLeast(startChapter)
                                    },
                                    label = { Text("Ch ${ch.chapterNumber}", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Page Count (Total: ${doc.totalPages} pages):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(3, 5, 8, 10, doc.totalPages).distinct().forEach { count ->
                                FilterChip(
                                    selected = selectedPageCount == count,
                                    onClick = { selectedPageCount = count },
                                    label = { Text("$count Pages", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Text("Questions:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(3, 5, 10).forEach { qCount ->
                                    FilterChip(
                                        selected = questionCount == qCount,
                                        onClick = { questionCount = qCount },
                                        label = { Text("$qCount Qs", style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        Column {
                            Text("Difficulty:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuizDifficulty.entries.forEach { diff ->
                                    FilterChip(
                                        selected = difficulty == diff,
                                        onClick = { difficulty = diff },
                                        label = { Text(diff.label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { triggerQuizGeneration(doc) },
                            enabled = !isGenerating,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate Quiz", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { triggerSummaryGeneration(doc) },
                            enabled = !isGenerating,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Study Summary", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        if (docErrorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = docErrorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (isGenerating) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScannerBeamView(modifier = Modifier.size(52.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Analyzing Document Segment",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Extracting grounded quiz questions with ${selectedModel.displayName}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Generated Quiz View
        if (generatedQuiz.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val correctCount = generatedQuiz.indices.count { idx ->
                        selectedAnswers[idx] == generatedQuiz[idx].correctIndex
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Generated Quiz (${generatedQuiz.size} Questions • ${difficulty.label})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (quizSubmitted) {
                            Text(
                                text = "Score: $correctCount / ${generatedQuiz.size}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (quizSubmitted && correctCount == generatedQuiz.size) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LottieEffectView(
                                assetName = "celebrate.json",
                                iterations = 1,
                                modifier = Modifier.size(90.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    generatedQuiz.forEachIndexed { qIdx, q ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Q${qIdx + 1}. ${q.question}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            q.options.forEachIndexed { optIdx, optText ->
                                val picked = selectedAnswers[qIdx] == optIdx
                                val cardBg = when {
                                    quizSubmitted && optIdx == q.correctIndex -> MaterialTheme.colorScheme.primaryContainer
                                    quizSubmitted && picked && optIdx != q.correctIndex -> MaterialTheme.colorScheme.errorContainer
                                    picked -> MaterialTheme.colorScheme.surfaceContainerHighest
                                    else -> MaterialTheme.colorScheme.surface
                                }
                                val cardTextColor = when {
                                    quizSubmitted && optIdx == q.correctIndex -> MaterialTheme.colorScheme.onPrimaryContainer
                                    quizSubmitted && picked && optIdx != q.correctIndex -> MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }

                                Surface(
                                    onClick = {
                                        if (!quizSubmitted) {
                                            selectedAnswers[qIdx] = optIdx
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = cardBg,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${('A' + optIdx)}. $optText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cardTextColor,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            if (quizSubmitted) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Explanation: ${q.explanation}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { quizSubmitted = !quizSubmitted },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (quizSubmitted) "Retry Quiz" else "Submit & Grade Offline")
                    }
                }
            }
        }

        // Summary View
        if (!generatedSummary.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Document Study Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = generatedSummary!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        }

        // Celebration Particle Burst on Quiz Submission
        CelebrationBurstOverlay(
            visible = quizSubmitted,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun parseJsonQuiz(
    rawJson: String?,
    questionCount: Int
): List<DocumentQuizQuestion> {
    if (rawJson.isNullOrBlank()) return emptyList()
    try {
        val startIdx = rawJson.indexOf('[')
        val endIdx = rawJson.lastIndexOf(']')
        if (startIdx >= 0 && endIdx > startIdx) {
            val slice = rawJson.substring(startIdx, endIdx + 1)
            val parsed = quizJsonParser.decodeFromString<List<DocumentQuizQuestion>>(slice)
            if (parsed.isNotEmpty()) return parsed.take(questionCount)
        }
    } catch (_: Exception) {
    }
    return emptyList()
}
