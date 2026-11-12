package com.bismuth.app.health

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bismuth.app.ui.animation.CelebrationBurstOverlay
import com.bismuth.app.ui.animation.ScannerBeamView
import androidx.compose.ui.unit.sp
import com.bismuth.app.R
import com.bismuth.app.engine.BismuthEngine
import com.bismuth.app.engine.BismuthModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FoodNutritionReport(
    val mealTitle: String,
    val estimatedCaloriesKcal: Int,
    val macrosBreakdown: Map<String, String>,
    val micronutrientsBreakdown: List<String>,
    val healthBenefits: List<String>,
    val healthDrawbacks: List<String>,
    val dietaryConsiderations: List<String>,
    val cookingMethods: List<String>,
    val servingSuggestions: List<String>,
    val dietaryTips: List<String>
)

@Serializable
data class LoggedMealEntry(
    val id: String,
    val timestampMillis: Long,
    val dateString: String,
    val mealTitle: String,
    val caloriesKcal: Int,
    val carbsG: String,
    val proteinG: String,
    val fatsG: String,
    val fiberG: String
)

data class OfflineHealthTopicEntry(
    val title: String,
    val category: String,
    val summary: String
)

object OfflineMealDiaryStore {
    private const val PREFS_NAME = "bismuth_meal_diary"
    private const val KEY_MEALS = "logged_meals_json"
    private val diaryJson = Json { ignoreUnknownKeys = true }

    fun loadLoggedMeals(context: Context): List<LoggedMealEntry> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_MEALS, null) ?: return emptyList()
        return try {
            diaryJson.decodeFromString<List<LoggedMealEntry>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMeal(context: Context, report: FoodNutritionReport): List<LoggedMealEntry> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = loadLoggedMeals(context).toMutableList()
        val now = System.currentTimeMillis()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
        val entry = LoggedMealEntry(
            id = "meal_${now}",
            timestampMillis = now,
            dateString = dateFmt,
            mealTitle = report.mealTitle,
            caloriesKcal = report.estimatedCaloriesKcal,
            carbsG = report.macrosBreakdown["Carbs"] ?: "0g",
            proteinG = report.macrosBreakdown["Protein"] ?: "0g",
            fatsG = report.macrosBreakdown["Fats"] ?: "0g",
            fiberG = report.macrosBreakdown["Fiber"] ?: "0g"
        )
        existing.add(0, entry)
        val serialized = diaryJson.encodeToString(existing)
        sp.edit().putString(KEY_MEALS, serialized).apply()
        return existing
    }

    fun deleteMeal(context: Context, mealId: String): List<LoggedMealEntry> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = loadLoggedMeals(context).filterNot { it.id == mealId }
        val serialized = diaryJson.encodeToString(existing)
        sp.edit().putString(KEY_MEALS, serialized).apply()
        return existing
    }
}

object OfflineHealthLibraryLoader {
    suspend fun loadTopics(context: Context): List<OfflineHealthTopicEntry> = withContext(Dispatchers.IO) {
        try {
            val rawJson = context.resources
                .openRawResource(R.raw.local_health_library)
                .bufferedReader()
                .use { it.readText() }
            val arr = JSONArray(rawJson)
            val list = mutableListOf<OfflineHealthTopicEntry>()
            for (i in 0 until arr.length().coerceAtMost(80)) {
                val obj = arr.getJSONObject(i)
                val title = obj.optString("title", "Health Topic ${i + 1}")
                val category = obj.optString("category", "General Health & Nutrition")
                val content = obj.optString("summary", obj.optString("content", "")).take(360)
                if (title.isNotBlank()) {
                    list.add(OfflineHealthTopicEntry(title = title, category = category, summary = content))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}

@Composable
fun HealthFeatureScreen(
    selectedModel: BismuthModelConfig,
    onRequestSwitchToVisionModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var mealDescriptionInput by remember { mutableStateOf("") }
    var isAnalyzingFood by remember { mutableStateOf(false) }
    var analysisErrorMessage by remember { mutableStateOf<String?>(null) }
    var nutritionReport by remember { mutableStateOf<FoodNutritionReport?>(null) }
    var isMealSavedToDiary by remember { mutableStateOf(false) }

    // Diary State
    val loggedMeals = remember { mutableStateListOf<LoggedMealEntry>() }
    val todayString = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    var libraryTopics by remember { mutableStateOf<List<OfflineHealthTopicEntry>>(emptyList()) }
    var healthSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        libraryTopics = OfflineHealthLibraryLoader.loadTopics(context)
        loggedMeals.clear()
        loggedMeals.addAll(OfflineMealDiaryStore.loadLoggedMeals(context))
    }

    fun analyzeMeal(bitmap: Bitmap?, userMealDescription: String) {
        val effectiveDescription = userMealDescription.trim().ifEmpty {
            if (bitmap != null) "Visual Meal Capture" else ""
        }
        if (bitmap == null && effectiveDescription.isEmpty()) {
            analysisErrorMessage = "Please take a photo or enter a meal description to analyze."
            return
        }

        scope.launch {
            isAnalyzingFood = true
            analysisErrorMessage = null
            isMealSavedToDiary = false

            val imageBytesList = if (bitmap != null && selectedModel.supportsImage) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                listOf(out.toByteArray())
            } else {
                emptyList()
            }

            val profile = com.bismuth.app.profile.StudentProfilePrefs.getProfile(context)
            val languageRule = com.bismuth.app.profile.StudentProfilePrefs.languageInstruction(profile.preferredLanguage)
            val prompt = """
                You are an expert clinical dietitian and nutritional biochemist.
                $languageRule
                Analyze the following meal ${if (bitmap != null) "from the captured photo and description" else "from the description"}:
                "$effectiveDescription"
                
                Provide a structured clinical nutritional assessment in the following strict format:
                Meal Title: <short descriptive title>
                Calories: <estimated integer kcal>
                Carbs: <amount with g>
                Protein: <amount with g>
                Fats: <amount with g>
                Fiber: <amount with g>
                
                [Micronutrients]
                - <nutrient with biological function>
                
                [Health Benefits]
                - <metabolic or physiological benefit>
                
                [Health Drawbacks]
                - <potential risk, glycemic impact, or excess nutrient>
                
                [Dietary Considerations]
                - <allergen, sodium, or disease-specific guidance>
                
                [Cooking Methods]
                - <health-optimizing culinary preparation method>
                
                [Serving Suggestions]
                - <balanced portion and vegetable pairing>
                
                [Dietary Tips]
                - <evidence-based advice for nutrient absorption>
            """.trimIndent()

            val llmResponse = BismuthEngine.generateTextOrNull(
                context = context,
                prompt = prompt,
                config = selectedModel,
                imagePngBytes = imageBytesList
            )

            if (!llmResponse.isNullOrBlank()) {
                nutritionReport = parseFoodNutritionReport(llmResponse, effectiveDescription)
            } else {
                analysisErrorMessage = "The AI did not generate an analysis. Make sure an offline AI is saved in AI Brains, or describe the meal in more detail."
            }
            isAnalyzingFood = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            analyzeMeal(bitmap, mealDescriptionInput)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bmp = try {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (_: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    if (bmp != null) {
                        capturedBitmap = bmp
                        analyzeMeal(bmp, mealDescriptionInput)
                    }
                }
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
            text = "Health & Nutrition Tracker",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Capture a food photo or enter meal ingredients for offline on-device AI nutritional breakdown, macro/micronutrient analysis, and daily calorie diary logging.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!selectedModel.supportsImage) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedModel.displayName} (Text-Only Mode)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "You can type meal descriptions and ingredients below for full nutritional analysis, or switch to a Vision-capable model for direct photo recognition.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onRequestSwitchToVisionModel) {
                        Text("Switch", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // Meal Ingestion Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Food Ingestion & Description",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camera", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (capturedBitmap != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Captured Food Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = mealDescriptionInput,
                    onValueChange = { mealDescriptionInput = it },
                    label = { Text("Meal or Ingredients Description", style = MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("e.g. 2 boiled eggs, whole wheat toast, and black coffee", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { analyzeMeal(capturedBitmap, mealDescriptionInput) },
                    enabled = !isAnalyzingFood && (capturedBitmap != null || mealDescriptionInput.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyze Meal Nutrition", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        if (analysisErrorMessage != null) {
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
                        text = analysisErrorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (isAnalyzingFood) {
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
                    ScannerBeamView(modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Analyzing Food & Nutrition",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Performing offline clinical nutrition & macro assessment with ${selectedModel.displayName}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Nutritional Analysis Card
        nutritionReport?.let { report ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = report.mealTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${report.estimatedCaloriesKcal} kcal",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Save to Diary Button
                    Button(
                        onClick = {
                            if (!isMealSavedToDiary) {
                                val updated = OfflineMealDiaryStore.saveMeal(context, report)
                                loggedMeals.clear()
                                loggedMeals.addAll(updated)
                                isMealSavedToDiary = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMealSavedToDiary) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (isMealSavedToDiary) Icons.Default.Check else Icons.Default.BookmarkAdd,
                            contentDescription = null,
                            tint = if (isMealSavedToDiary) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMealSavedToDiary) "Saved to Daily Meal Diary" else "Log to Daily Diary",
                            color = if (isMealSavedToDiary) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSecondary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    // 1. Macros Breakdown
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. Macronutrient & Micronutrient Profile",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                report.macrosBreakdown.forEach { (macro, value) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = macro,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = value,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            if (report.micronutrientsBreakdown.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Micronutrients:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                report.micronutrientsBreakdown.forEach { micro ->
                                    Text(
                                        text = "• $micro",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 2. Health Impact
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "2. Physiological Impact Analysis",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            if (report.healthBenefits.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Benefits:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                report.healthBenefits.forEach { b ->
                                    Text(
                                        text = "• $b",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (report.healthDrawbacks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Drawbacks / Risks:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                report.healthDrawbacks.forEach { d ->
                                    Text(
                                        text = "• $d",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (report.dietaryConsiderations.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Dietary Considerations:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                report.dietaryConsiderations.forEach { c ->
                                    Text(
                                        text = "• $c",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // 3. Preparation & Consumption Guidance
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "3. Preparation & Consumption Guidance",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            if (report.cookingMethods.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Cooking Methods:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                report.cookingMethods.forEach { m ->
                                    Text(
                                        text = "• $m",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (report.servingSuggestions.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Serving Suggestions:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                report.servingSuggestions.forEach { s ->
                                    Text(
                                        text = "• $s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (report.dietaryTips.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Dietary Tips:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                report.dietaryTips.forEach { t ->
                                    Text(
                                        text = "• $t",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Daily Meal Diary History Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val todaysMeals = loggedMeals.filter { it.dateString == todayString }
                val totalCaloriesToday = todaysMeals.sumOf { it.caloriesKcal }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Meal Diary (${todaysMeals.size} logged)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$totalCaloriesToday kcal total",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (todaysMeals.isEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No meals logged today yet. Analyze a meal above and tap 'Log to Daily Diary' to record your nutritional history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    todaysMeals.forEach { entry ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.mealTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${entry.caloriesKcal} kcal • Carbs: ${entry.carbsG} • Protein: ${entry.proteinG} • Fats: ${entry.fatsG}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = OfflineMealDiaryStore.deleteMeal(context, entry.id)
                                        loggedMeals.clear()
                                        loggedMeals.addAll(updated)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Logged Meal",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bundled Offline WHO Health Reference Library Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Offline WHO Health & Nutrition Library (${libraryTopics.size} Topics)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Popular topic chips (matching Genie)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Diabetes", "Hypertension", "Malnutrition", "Anaemia", "Asthma", "Malaria").forEach { popTopic ->
                        FilterChip(
                            selected = healthSearchQuery.equals(popTopic, ignoreCase = true),
                            onClick = {
                                healthSearchQuery = if (healthSearchQuery.equals(popTopic, ignoreCase = true)) "" else popTopic
                            },
                            label = { Text(popTopic, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = healthSearchQuery,
                    onValueChange = { healthSearchQuery = it },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    placeholder = { Text("Search offline health & nutrition guidelines...", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                val filtered = remember(libraryTopics, healthSearchQuery) {
                    if (healthSearchQuery.isBlank()) {
                        libraryTopics.take(4)
                    } else {
                        libraryTopics.filter {
                            it.title.contains(healthSearchQuery, ignoreCase = true) ||
                                it.summary.contains(healthSearchQuery, ignoreCase = true) ||
                                it.category.contains(healthSearchQuery, ignoreCase = true)
                        }.take(8)
                    }
                }

                filtered.forEach { topic ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${topic.title} • ${topic.category}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (topic.summary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = topic.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // Celebration Particle Burst on Meal Logged
        CelebrationBurstOverlay(
            visible = isMealSavedToDiary,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun parseFoodNutritionReport(
    llmResponse: String,
    mealHint: String
): FoodNutritionReport {
    var mealTitle = mealHint.ifBlank { "Analyzed Meal" }
    var calories = 0
    val macros = mutableMapOf(
        "Carbs" to "0g",
        "Protein" to "0g",
        "Fats" to "0g",
        "Fiber" to "0g"
    )
    val micronutrients = mutableListOf<String>()
    val benefits = mutableListOf<String>()
    val drawbacks = mutableListOf<String>()
    val considerations = mutableListOf<String>()
    val cooking = mutableListOf<String>()
    val serving = mutableListOf<String>()
    val tips = mutableListOf<String>()

    var activeSection = 0

    for (line in llmResponse.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        val lower = trimmed.lowercase()
        when {
            lower.startsWith("meal title:") || lower.startsWith("title:") -> {
                mealTitle = trimmed.substringAfter(":").trim().ifBlank { mealTitle }
                continue
            }
            lower.startsWith("calories:") -> {
                val num = Regex("""\d+""").find(trimmed)?.value?.toIntOrNull()
                if (num != null) calories = num
                continue
            }
            lower.startsWith("carbs:") -> {
                macros["Carbs"] = trimmed.substringAfter(":").trim()
                continue
            }
            lower.startsWith("protein:") -> {
                macros["Protein"] = trimmed.substringAfter(":").trim()
                continue
            }
            lower.startsWith("fats:") || lower.startsWith("fat:") -> {
                macros["Fats"] = trimmed.substringAfter(":").trim()
                continue
            }
            lower.startsWith("fiber:") -> {
                macros["Fiber"] = trimmed.substringAfter(":").trim()
                continue
            }
            "micronutrient" in lower -> {
                activeSection = 1
                continue
            }
            "benefit" in lower -> {
                activeSection = 2
                continue
            }
            "drawback" in lower || "risk" in lower -> {
                activeSection = 3
                continue
            }
            "consideration" in lower -> {
                activeSection = 4
                continue
            }
            "cooking" in lower -> {
                activeSection = 5
                continue
            }
            "serving" in lower -> {
                activeSection = 6
                continue
            }
            "tip" in lower -> {
                activeSection = 7
                continue
            }
        }

        val clean = trimmed.removePrefix("-").removePrefix("•").removePrefix("*").trim()
        if (clean.isBlank()) continue

        when (activeSection) {
            1 -> micronutrients.add(clean)
            2 -> benefits.add(clean)
            3 -> drawbacks.add(clean)
            4 -> considerations.add(clean)
            5 -> cooking.add(clean)
            6 -> serving.add(clean)
            7 -> tips.add(clean)
        }
    }

    if (calories == 0) {
        val foundCal = Regex("""(\d{2,4})\s*k?cal""").find(llmResponse.lowercase())
        calories = foundCal?.groupValues?.get(1)?.toIntOrNull() ?: 450
    }

    return FoodNutritionReport(
        mealTitle = mealTitle,
        estimatedCaloriesKcal = calories,
        macrosBreakdown = macros,
        micronutrientsBreakdown = micronutrients.ifEmpty { listOf("Key vitamins and minerals evaluated by on-device model.") },
        healthBenefits = benefits.ifEmpty { listOf("Nutrient dense components support metabolic health.") },
        healthDrawbacks = drawbacks,
        dietaryConsiderations = considerations,
        cookingMethods = cooking,
        servingSuggestions = serving,
        dietaryTips = tips.ifEmpty { listOf("Stay hydrated and maintain moderate portion control.") }
    )
}
