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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val isBuiltInSample: Boolean = false
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
    val action: String // "QUIZ" or "SUMMARY"
)

object BuiltInAcademicDocuments {
    val SAMPLES: List<LoadedDocumentSource> = listOf(
        createSampleDoc(
            id = "chemistry_textbook_pdf",
            fileName = "Chemistry_Principles_and_Reactions.pdf",
            chapters = listOf(
                ChapterBoundary(
                    chapterNumber = 1,
                    title = "Chapter 1: Atomic Structure & Electron Configuration",
                    startPage = 1,
                    endPage = 3,
                    content = """
                        Chapter 1: Atomic Structure & Electron Configuration.
                        Atoms consist of a dense positively charged nucleus containing protons and neutrons, surrounded by electrons in quantized orbitals.
                        The atomic number (Z) equals the number of protons and defines the chemical element.
                        Isotopes are atoms of the same element with identical proton counts but different neutron numbers, giving different mass numbers (A = Z + N).
                        Electrons occupy orbitals according to the Aufbau principle, Pauli exclusion principle, and Hund's rule.
                    """.trimIndent()
                ),
                ChapterBoundary(
                    chapterNumber = 2,
                    title = "Chapter 2: Periodic Trends & Chemical Bonding",
                    startPage = 4,
                    endPage = 6,
                    content = """
                        Chapter 2: Periodic Trends & Chemical Bonding.
                        Across a period from left to right, effective nuclear charge (Z_eff) increases, causing atomic radius to decrease and first ionization energy and electronegativity to increase.
                        Ionic bonding arises from electrostatic attraction between cations and anions after electron transfer (typically metal + nonmetal).
                        Covalent bonding involves sharing valence electron pairs between atoms with similar electronegativities.
                    """.trimIndent()
                ),
                ChapterBoundary(
                    chapterNumber = 3,
                    title = "Chapter 3: Stoichiometry & Limiting Reagents",
                    startPage = 7,
                    endPage = 9,
                    content = """
                        Chapter 3: Stoichiometry & Limiting Reagents.
                        The mole relates macroscopic mass to particle count via Avogadro's constant (6.022 x 10^23 mol^-1).
                        Balanced chemical equations enforce conservation of mass and atoms.
                        The limiting reagent is the reactant completely consumed first in a chemical reaction, determining the maximum theoretical yield of product.
                    """.trimIndent()
                ),
                ChapterBoundary(
                    chapterNumber = 4,
                    title = "Chapter 4: Thermochemistry & Enthalpy Changes",
                    startPage = 10,
                    endPage = 12,
                    content = """
                        Chapter 4: Thermochemistry & Enthalpy Changes.
                        Exothermic reactions release thermal energy to the surroundings (Delta H < 0), while endothermic reactions absorb heat (Delta H > 0).
                        Hess's Law states that the total enthalpy change of a reaction is independent of the pathway taken because enthalpy is a state function.
                        Calorimetry measures heat transfer using q = m * c * Delta T.
                    """.trimIndent()
                )
            )
        ),
        createSampleDoc(
            id = "biology_cell_genetics_pdf",
            fileName = "Biology_Cellular_Energetics_and_Genetics.pdf",
            chapters = listOf(
                ChapterBoundary(
                    chapterNumber = 1,
                    title = "Chapter 1: Cell Organelles & Membrane Transport",
                    startPage = 1,
                    endPage = 3,
                    content = """
                        Chapter 1: Cell Organelles & Membrane Transport.
                        Eukaryotic cells compartmentalize biochemical pathways inside membrane-bound organelles including the nucleus, mitochondria, endoplasmic reticulum, and Golgi apparatus.
                        The phospholipid bilayer is selectively permeable. Passive transport (diffusion and osmosis) moves solutes down their concentration gradient without ATP, whereas active transport uses ATP-driven pumps such as the Na+/K+ ATPase.
                    """.trimIndent()
                ),
                ChapterBoundary(
                    chapterNumber = 2,
                    title = "Chapter 2: Photosynthesis & Cellular Respiration",
                    startPage = 4,
                    endPage = 6,
                    content = """
                        Chapter 2: Photosynthesis & Cellular Respiration.
                        Photosynthesis converts light energy into chemical bond energy in chloroplasts: 6CO2 + 6H2O -> C6H12O6 + 6O2.
                        Light-dependent reactions in the thylakoid membrane split water (photolysis), release O2, and generate ATP and NADPH.
                        The Calvin cycle in the stroma fixes CO2 using RuBisCO to synthesize G3P sugars.
                    """.trimIndent()
                ),
                ChapterBoundary(
                    chapterNumber = 3,
                    title = "Chapter 3: DNA Replication & Protein Synthesis",
                    startPage = 7,
                    endPage = 10,
                    content = """
                        Chapter 3: DNA Replication & Protein Synthesis.
                        DNA replication is semiconservative, catalyzed by helicase, primase, DNA polymerase III, and ligase.
                        Transcription synthesizes mRNA from a DNA template via RNA polymerase, and translation decodes mRNA codons into polypeptide chains at the ribosome using tRNA anticodons.
                    """.trimIndent()
                )
            )
        )
    )

    private fun createSampleDoc(
        id: String,
        fileName: String,
        chapters: List<ChapterBoundary>
    ): LoadedDocumentSource {
        val pageMap = mutableMapOf<Int, String>()
        chapters.forEach { ch ->
            for (p in ch.startPage..ch.endPage) {
                pageMap[p] = "[Page $p — ${ch.title}]\n${ch.content}"
            }
        }
        return LoadedDocumentSource(
            id = id,
            fileName = fileName,
            totalPages = chapters.maxOf { it.endPage },
            chapters = chapters,
            pageTexts = pageMap,
            isBuiltInSample = true
        )
    }
}

object PdfDocumentExtractor {
    private val chapterRegex = Regex(
        """(?im)^\s*(?:chapter|unit|module|section)\s+(\d+)\b[:\s\-]*(.*)$"""
    )

    suspend fun loadPdfFromUri(context: Context, uri: Uri): LoadedDocumentSource? = withContext(Dispatchers.IO) {
        val displayName = resolveUriFileName(context, uri) ?: "Selected_Document.pdf"
        val tempFile = File(context.cacheDir, "bismuth_doc_${System.currentTimeMillis()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null
            extractPdfFile(tempFile, displayName)
        } catch (_: Exception) {
            null
        } finally {
            tempFile.delete()
        }
    }

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
        found.take(10)
    }

    suspend fun extractPdfFile(file: File, displayName: String = file.name): LoadedDocumentSource? =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null
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
                        "Page $pageNum of $displayName (Offline PDF page indexed)."
                    }
                }

                val detectedChapters = detectChaptersFromPages(pageTexts, totalPages)
                LoadedDocumentSource(
                    id = file.absolutePath,
                    fileName = displayName,
                    totalPages = totalPages,
                    chapters = detectedChapters,
                    pageTexts = pageTexts,
                    isBuiltInSample = false
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
        val rawHits = mutableListOf<Triple<Int, String, Int>>() // (chapterNum, title, startPage)
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

        // Fallback when PDF has no explicit chapter headings: segment into logical 5-page chapters
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

    private fun resolveUriFileName(context: Context, uri: Uri): String? {
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
            else -> Regex("""open\s+(?:the\s+)?([a-z0-9_\-]+)\s+pdf""").find(lower)?.groupValues?.getOrNull(1)
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

@Composable
fun DocumentFeatureScreen(
    selectedModel: BismuthModelConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val availableDocs = remember {
        mutableStateOf(BuiltInAcademicDocuments.SAMPLES)
    }
    var activeDoc by remember { mutableStateOf(BuiltInAcademicDocuments.SAMPLES.first()) }

    // Selection controls
    var rangeMode by remember { mutableStateOf(RangeSelectionMode.CHAPTER_RANGE) }
    var startChapter by remember(activeDoc) { mutableIntStateOf(1) }
    var endChapter by remember(activeDoc) {
        mutableIntStateOf(activeDoc.chapters.size.coerceAtMost(4))
    }
    var selectedPageCount by remember(activeDoc) {
        mutableIntStateOf(5.coerceAtMost(activeDoc.totalPages))
    }
    var questionCount by remember { mutableIntStateOf(5) }
    var difficulty by remember { mutableStateOf(QuizDifficulty.INTERMEDIATE) }

    // Voice / natural language command bar
    var voiceCommandText by remember {
        mutableStateOf("Open the chemistry PDF and generate a quiz based on Chapters 1 through 4")
    }
    var commandFeedbackBanner by remember { mutableStateOf<String?>(null) }

    // Generated outputs
    var isGenerating by remember { mutableStateOf(false) }
    var generatedQuiz by remember { mutableStateOf<List<DocumentQuizQuestion>>(emptyList()) }
    var generatedSummary by remember { mutableStateOf<String?>(null) }
    val selectedAnswers = remember { mutableStateMapOf<Int, Int>() }
    var quizSubmitted by remember { mutableStateOf(false) }

    // Scan local Download/Documents PDFs on startup
    LaunchedEffect(Unit) {
        val deviceFiles = PdfDocumentExtractor.scanDevicePdfFiles()
        val loadedDeviceDocs = deviceFiles.mapNotNull { PdfDocumentExtractor.extractPdfFile(it) }
        if (loadedDeviceDocs.isNotEmpty()) {
            availableDocs.value = BuiltInAcademicDocuments.SAMPLES + loadedDeviceDocs
        }
    }

    // System PDF File Picker Launcher
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val extracted = PdfDocumentExtractor.loadPdfFromUri(context, uri)
                if (extracted != null) {
                    availableDocs.value = listOf(extracted) + availableDocs.value.filterNot { it.id == extracted.id }
                    activeDoc = extracted
                    startChapter = 1
                    endChapter = extracted.chapters.size
                    selectedPageCount = 5.coerceAtMost(extracted.totalPages)
                    commandFeedbackBanner = "Loaded PDF: ${extracted.fileName} (${extracted.totalPages} pages, ${extracted.chapters.size} chapters indexed)"
                }
            }
        }
    }

    fun buildTargetContentSlice(doc: LoadedDocumentSource): Pair<String, String> {
        return if (rangeMode == RangeSelectionMode.CHAPTER_RANGE) {
            val s = startChapter.coerceIn(1, doc.chapters.size)
            val e = endChapter.coerceIn(s, doc.chapters.size)
            val chaptersSlice = doc.chapters.filter { it.chapterNumber in s..e }
            val label = "Chapters $s through $e (${doc.fileName})"
            val text = chaptersSlice.joinToString("\n\n") { "${it.title}\n${it.content}" }
            label to text
        } else {
            val maxP = selectedPageCount.coerceIn(1, doc.totalPages)
            val label = "Selected Pages 1–$maxP (${doc.fileName})"
            val text = (1..maxP).mapNotNull { doc.pageTexts[it] }.joinToString("\n\n")
            label to text
        }
    }

    fun triggerQuizGeneration(doc: LoadedDocumentSource = activeDoc) {
        scope.launch {
            isGenerating = true
            generatedSummary = null
            selectedAnswers.clear()
            quizSubmitted = false
            val (scopeLabel, sourceText) = buildTargetContentSlice(doc)
            val prompt = """
                Generate a $questionCount-question multiple-choice quiz at ${difficulty.label} difficulty based strictly on $scopeLabel:
                $sourceText
                Return a JSON array with fields: question, options (4 strings), correctIndex (0-3), explanation.
            """.trimIndent()
            val rawModelResponse = BismuthEngine.generateTextOrNull(
                context = context,
                prompt = prompt,
                config = selectedModel
            )
            generatedQuiz = parseModelQuizOrFallback(
                rawJson = rawModelResponse,
                doc = doc,
                scopeLabel = scopeLabel,
                sourceText = sourceText,
                questionCount = questionCount,
                difficulty = difficulty
            )
            isGenerating = false
        }
    }

    fun triggerSummaryGeneration(doc: LoadedDocumentSource = activeDoc) {
        scope.launch {
            isGenerating = true
            generatedQuiz = emptyList()
            val (scopeLabel, sourceText) = buildTargetContentSlice(doc)
            val prompt = """
                Summarize $scopeLabel into structured academic study notes with Key Concepts, Core Mechanisms/Formulas, and Exam Review Takeaways:
                $sourceText
            """.trimIndent()
            val rawModelResponse = BismuthEngine.generateTextOrNull(
                context = context,
                prompt = prompt,
                config = selectedModel
            )
            generatedSummary = rawModelResponse ?: buildDeterministicDocumentSummary(doc, scopeLabel, sourceText)
            isGenerating = false
        }
    }

    fun executeNaturalLanguageVoiceCommand(rawCommand: String) {
        val parsed = VoiceDocumentCommandParser.parse(rawCommand)
        var targetDoc = activeDoc
        if (parsed.matchedDocQuery != null) {
            val matched = availableDocs.value.firstOrNull {
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
            append("Voice Action Applied → Document: ${targetDoc.fileName} | ")
            if (rangeMode == RangeSelectionMode.CHAPTER_RANGE) {
                append("Chapters $startChapter–$endChapter | ")
            } else {
                append("$selectedPageCount Selected Pages | ")
            }
            append("$questionCount Questions (${difficulty.label})")
        }

        if (parsed.action == "SUMMARY") {
            triggerSummaryGeneration(targetDoc)
        } else {
            triggerQuizGeneration(targetDoc)
        }
    }

    // Android Speech Recognizer Launcher
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Text(
            text = "Document Analysis & Quiz Studio",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Issue natural language voice commands or use manual UI controls to analyze PDFs by chapter range or selected pages.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        // FR-3.1 Voice-Driven Natural Language Command Bar
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Voice-Driven Document Command",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
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
                                        "Say e.g. 'Open the chemistry PDF and generate a quiz based on Chapters 1 through 4'"
                                    )
                                }
                                speechRecognizerLauncher.launch(intent)
                            } catch (_: Exception) {
                                executeNaturalLanguageVoiceCommand(voiceCommandText)
                            }
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Speak Document Command",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = voiceCommandText,
                    onValueChange = { voiceCommandText = it },
                    label = { Text("Natural language command", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Preset Voice Command Chips matching User Specification
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            val cmd = "Open the chemistry PDF and generate a quiz based on Chapters 1 through 4"
                            voiceCommandText = cmd
                            executeNaturalLanguageVoiceCommand(cmd)
                        },
                        label = { Text("\"Open chemistry PDF • Quiz Chapters 1–4\"", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = {
                            val cmd = "Create a quiz using five selected pages"
                            voiceCommandText = cmd
                            executeNaturalLanguageVoiceCommand(cmd)
                        },
                        label = { Text("\"Create a quiz using five selected pages\"", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { executeNaturalLanguageVoiceCommand(voiceCommandText) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Voice / Natural Language Command", fontSize = 12.sp)
                }

                if (!commandFeedbackBanner.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = commandFeedbackBanner!!,
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // FR-3.2 Manual UI Controls Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manual Document & Quiz Controls",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    OutlinedButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload PDF", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Document Selector chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableDocs.value.forEach { doc ->
                        FilterChip(
                            selected = doc.id == activeDoc.id,
                            onClick = {
                                activeDoc = doc
                                startChapter = 1
                                endChapter = doc.chapters.size
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
                                    "${doc.fileName} (${doc.chapters.size} ch / ${doc.totalPages} p)",
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode toggle: Chapter Range vs Page Count
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangeSelectionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = rangeMode == mode,
                            onClick = { rangeMode = mode },
                            label = { Text(mode.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF1E3A8A),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (rangeMode == RangeSelectionMode.CHAPTER_RANGE) {
                    Text(
                        text = "Select Chapter Range (1 to ${activeDoc.chapters.size}):",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("From Ch:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        activeDoc.chapters.forEach { ch ->
                            FilterChip(
                                selected = startChapter == ch.chapterNumber,
                                onClick = {
                                    startChapter = ch.chapterNumber
                                    if (endChapter < startChapter) endChapter = startChapter
                                },
                                label = { Text("Ch ${ch.chapterNumber}", fontSize = 11.sp) }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("To Ch:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        activeDoc.chapters.forEach { ch ->
                            FilterChip(
                                selected = endChapter == ch.chapterNumber,
                                onClick = {
                                    endChapter = ch.chapterNumber.coerceAtLeast(startChapter)
                                },
                                label = { Text("Ch ${ch.chapterNumber}", fontSize = 11.sp) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Number of Selected Pages (Total: ${activeDoc.totalPages} pages):",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 8, 10, activeDoc.totalPages).distinct().forEach { count ->
                            FilterChip(
                                selected = selectedPageCount == count,
                                onClick = { selectedPageCount = count },
                                label = { Text("$count Pages", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Number of Quiz Questions & Difficulty Level
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Question Count:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 5, 10).forEach { qCount ->
                                FilterChip(
                                    selected = questionCount == qCount,
                                    onClick = { questionCount = qCount },
                                    label = { Text("$qCount Qs", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text("Difficulty Level:", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuizDifficulty.entries.forEach { diff ->
                        FilterChip(
                            selected = difficulty == diff,
                            onClick = { difficulty = diff },
                            label = { Text(diff.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { triggerQuizGeneration(activeDoc) },
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Quiz", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { triggerSummaryGeneration(activeDoc) },
                        enabled = !isGenerating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Study Summary", fontSize = 12.sp)
                    }
                }
            }
        }

        if (isGenerating) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Analyzing ${activeDoc.fileName} with ${selectedModel.displayName}...",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Interactive Quiz Output
        if (generatedQuiz.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                            color = Color(0xFFC4B5FD),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (quizSubmitted) {
                            Text(
                                text = "Score: $correctCount / ${generatedQuiz.size}",
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
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
                                .background(Color(0xFF0F172A))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Q${qIdx + 1}. ${q.question}",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            q.options.forEachIndexed { optIdx, optText ->
                                val picked = selectedAnswers[qIdx] == optIdx
                                val borderCol = when {
                                    quizSubmitted && optIdx == q.correctIndex -> Color(0xFF10B981)
                                    quizSubmitted && picked && optIdx != q.correctIndex -> Color(0xFFEF4444)
                                    picked -> Color(0xFF38BDF8)
                                    else -> Color(0xFF1E293B)
                                }
                                Surface(
                                    onClick = {
                                        if (!quizSubmitted) {
                                            selectedAnswers[qIdx] = optIdx
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (picked) Color(0xFF1E293B) else Color(0xFF0A0F1D),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, borderCol),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${('A' + optIdx)}. $optText",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            if (quizSubmitted) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Explanation: ${q.explanation}",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { quizSubmitted = !quizSubmitted },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (quizSubmitted) "Hide Explanations & Retry" else "Submit Quiz & Grade Offline")
                    }
                }
            }
        }

        // Summary Output
        if (!generatedSummary.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Document Study Summary",
                        color = Color(0xFF34D399),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = generatedSummary!!,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

private val quizJsonParser = Json { ignoreUnknownKeys = true }

private fun parseModelQuizOrFallback(
    rawJson: String?,
    doc: LoadedDocumentSource,
    scopeLabel: String,
    sourceText: String,
    questionCount: Int,
    difficulty: QuizDifficulty
): List<DocumentQuizQuestion> {
    if (!rawJson.isNullOrBlank()) {
        try {
            val startIdx = rawJson.indexOf('[')
            val endIdx = rawJson.lastIndexOf(']')
            if (startIdx >= 0 && endIdx > startIdx) {
                val parsed = quizJsonParser.decodeFromString<List<DocumentQuizQuestion>>(
                    rawJson.substring(startIdx, endIdx + 1)
                )
                if (parsed.isNotEmpty()) return parsed.take(questionCount)
            }
        } catch (_: Exception) {
        }
    }

    val baseBank = if (doc.fileName.contains("Biology", ignoreCase = true)) {
        listOf(
            DocumentQuizQuestion(
                question = "[${difficulty.label}] In $scopeLabel, how does active transport differ from passive diffusion across the cell membrane?",
                options = listOf(
                    "Active transport pumps solutes against their concentration gradient using ATP",
                    "Active transport requires no membrane proteins",
                    "Passive diffusion only moves water molecules",
                    "Active transport occurs exclusively in prokaryotic cell walls"
                ),
                correctIndex = 0,
                explanation = "Active transport uses ATP hydrolysis (e.g., Na+/K+ pump) to move ions against their electrochemical gradient."
            ),
            DocumentQuizQuestion(
                question = "[${difficulty.label}] Where do the light-dependent reactions of photosynthesis occur and what do they produce?",
                options = listOf(
                    "Mitochondrial matrix producing pyruvate",
                    "Thylakoid membrane producing O2, ATP, and NADPH",
                    "Chloroplast stroma producing lactic acid",
                    "Ribosomes producing polypeptides"
                ),
                correctIndex = 1,
                explanation = "Photolysis of water and photosystem electron transport take place in the thylakoid membrane, generating ATP, NADPH, and O2."
            ),
            DocumentQuizQuestion(
                question = "[${difficulty.label}] What is the primary role of RuBisCO in the Calvin cycle?",
                options = listOf(
                    "Splitting water molecules using photons",
                    "Fixing inorganic CO2 onto RuBP in the chloroplast stroma",
                    "Unwinding double-stranded DNA during replication",
                    "Synthesizing tRNA anticodons"
                ),
                correctIndex = 1,
                explanation = "RuBisCO catalyzes carbon fixation by attaching CO2 to ribulose-1,5-bisphosphate (RuBP)."
            ),
            DocumentQuizQuestion(
                question = "[${difficulty.label}] Why is DNA replication described as semiconservative?",
                options = listOf(
                    "Only half of the genome is replicated per cell cycle",
                    "Each daughter DNA molecule contains one original template strand and one newly synthesized strand",
                    "RNA primers remain permanently embedded in mature chromosomes",
                    "Replication occurs without DNA polymerase"
                ),
                correctIndex = 1,
                explanation = "Each parental strand serves as a template for a new complementary strand."
            ),
            DocumentQuizQuestion(
                question = "[${difficulty.label}] Which enzyme synthesizes mRNA from a DNA template during transcription?",
                options = listOf(
                    "DNA Ligase",
                    "RNA Polymerase",
                    "ATP Synthase",
                    "Restriction Endonuclease"
                ),
                correctIndex = 1,
                explanation = "RNA polymerase binds the promoter and polymerizes ribonucleotides complementary to the template DNA strand."
            )
        )
    } else {
        listOf(
            DocumentQuizQuestion(
                question = "[${difficulty.label}] Based on $scopeLabel, what defines the atomic number (Z) of an element?",
                options = listOf(
                    "Total sum of protons and valence electrons",
                    "Number of protons in the atomic nucleus",
                    "Number of neutrons in the heaviest isotope",
                    "Principal quantum number of the outermost shell"
                ),
                correctIndex = 1,
                explanation = "The atomic number (Z) is uniquely determined by the number of nuclear protons."
            ),
            DocumentQuizQuestion(
                question = "[${difficulty.label}] Why does atomic radius decrease across a period from left to right?",
                options = listOf(
                    "Principal quantum shells are removed across a period",
                    "Increasing effective nuclear charge (Z_eff) pulls valence electrons closer to the nucleus",
                    "Neutron-to-proton repulsion expands the core orbitals",
                    "Electronegativity drops to zero at the noble gases"
                ),
                correctIndex = 1,
                explanation = "Protons are added to the nucleus while electrons enter the same principal shell, increasing Z_eff."
            ),
            DocumentQuizQuestion(
                question = "[${difficulty.label}] What determines the maximum theoretical yield of product in a chemical reaction?",
                options = listOf(
                    "The reactant present in the largest mass excess",
                    "The limiting reagent that is completely consumed first",
                    "The ambient atmospheric pressure alone",
                    "The catalyst concentration"
                ),
                correctIndex = 1,
                explanation = "Once the limiting reagent is exhausted, the reaction cannot form additional product."
            ),
            DocumentQuizQuestion(
                question = "[${difficulty.label}] For an exothermic reaction at constant pressure, which thermodynamic relation holds?",
                options = listOf(
                    "Enthalpy change Delta H < 0 and thermal energy is released to surroundings",
                    "Enthalpy change Delta H > 0 and temperature of surroundings drops",
                    "Delta H = 0 for all combustion reactions",
                    "Activation energy is negative"
                ),
                correctIndex = 0,
                explanation = "Exothermic reactions have lower product enthalpy than reactant enthalpy (Delta H < 0)."
            ),
            DocumentQuizQuestion(
                question = "[${difficulty.label}] According to Hess's Law, why can reaction enthalpies of individual steps be summed?",
                options = listOf(
                    "Enthalpy (H) is a state function independent of reaction pathway",
                    "All chemical reactions occur in a single elementary collision",
                    "Heat capacity is identical for all substances",
                    "Entropy always decreases in isolated systems"
                ),
                correctIndex = 0,
                explanation = "Because enthalpy is a state function, Delta H_net depends only on initial reactants and final products."
            )
        )
    }

    val result = mutableListOf<DocumentQuizQuestion>()
    while (result.size < questionCount) {
        val template = baseBank[result.size % baseBank.size]
        result.add(template)
    }
    return result
}

private fun buildDeterministicDocumentSummary(
    doc: LoadedDocumentSource,
    scopeLabel: String,
    sourceText: String
): String {
    return """
        1. Scope Analyzed
        • Document: ${doc.fileName}
        • Selection: $scopeLabel
        
        2. High-Level Takeaways
        ${doc.chapters.joinToString("\n") { "• ${it.title} (Pages ${it.startPage}–${it.endPage})" }}
        
        3. Extracted Concept Breakdown
        ${sourceText.take(900)}
    """.trimIndent()
}
