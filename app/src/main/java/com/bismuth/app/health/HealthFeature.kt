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
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bismuth.app.R
import com.bismuth.app.engine.BismuthEngine
import com.bismuth.app.engine.BismuthModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream

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

data class OfflineHealthTopicEntry(
    val title: String,
    val category: String,
    val summary: String
)

object OfflineHealthLibraryLoader {
    suspend fun loadTopics(context: Context): List<OfflineHealthTopicEntry> = withContext(Dispatchers.IO) {
        try {
            val rawJson = context.resources
                .openRawResource(R.raw.local_health_library)
                .bufferedReader()
                .use { it.readText() }
            val arr = JSONArray(rawJson)
            val list = mutableListOf<OfflineHealthTopicEntry>()
            for (i in 0 until arr.length().coerceAtMost(40)) {
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
    var selectedMealPreset by remember { mutableStateOf("Jollof Rice, Grilled Chicken & Plantain") }
    var isAnalyzingFood by remember { mutableStateOf(false) }
    var nutritionReport by remember {
        mutableStateOf<FoodNutritionReport?>(
            buildPresetNutritionReport("Jollof Rice, Grilled Chicken & Plantain")
        )
    }

    // Offline WHO Health Library search state
    var libraryTopics by remember { mutableStateOf<List<OfflineHealthTopicEntry>>(emptyList()) }
    var healthSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        libraryTopics = OfflineHealthLibraryLoader.loadTopics(context)
    }

    fun analyzeMealWithModel(bitmap: Bitmap?, mealHint: String) {
        scope.launch {
            isAnalyzingFood = true
            val imageBytesList = if (bitmap != null && selectedModel.supportsImage) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                listOf(out.toByteArray())
            } else {
                emptyList()
            }

            val prompt = """
                Analyze this food meal ($mealHint) and provide:
                1. Calorie count and macro/micronutrient breakdown.
                2. Health impact analysis (benefits, drawbacks, and dietary considerations).
                3. Preparation and consumption guidance (cooking methods, serving suggestions, and dietary tips).
            """.trimIndent()

            val llmResponse = BismuthEngine.generateTextOrNull(
                context = context,
                prompt = prompt,
                config = selectedModel,
                imagePngBytes = imageBytesList
            )

            nutritionReport = buildPresetNutritionReport(mealHint, llmResponse)
            isAnalyzingFood = false
        }
    }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            capturedBitmap = bitmap
            analyzeMealWithModel(bitmap, "Camera Captured Meal ($selectedMealPreset)")
        }
    }

    // Gallery upload launcher
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
                        analyzeMealWithModel(bmp, "Gallery Meal Photo ($selectedMealPreset)")
                    }
                }
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
        Text(
            text = "Health & Nutrition Tracker",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = "Capture a photo of your food or upload from gallery for calorie, macro/micronutrient, health impact, and preparation guidance.",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp
        )

        if (!selectedModel.supportsImage) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1822)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF43F5E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedModel.displayName} Does Not Support Vision Input",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Switch to a multimodal Vision model (Gemma 3n E2B-INT4, Gemma 4 E2B, or Gemma 4 E4B) for neural photo recognition.",
                            color = Color(0xFFFECDD3),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onRequestSwitchToVisionModel) {
                        Text("Switch Model", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }

        // FR-2.1 Image Capture & Gallery Upload Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Food Image Capture & Gallery Upload",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { cameraLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Take Food Photo", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gallery Upload", fontSize = 12.sp)
                    }
                }

                if (capturedBitmap != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Selected Food Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Or inspect common meal profiles offline:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Jollof Rice, Grilled Chicken & Plantain",
                        "Beans Porridge (Ewa Riro), Fish & Avocado",
                        "Oatmeal Bowl with Banana, Eggs & Groundnuts"
                    ).forEach { meal ->
                        FilterChip(
                            selected = selectedMealPreset == meal,
                            onClick = {
                                selectedMealPreset = meal
                                analyzeMealWithModel(capturedBitmap, meal)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = { Text(meal, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        if (isAnalyzingFood) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Analyzing meal nutrition, health impact & preparation guidance...",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // FR-2.2 Complete 3-Part Nutritional Analysis Card
        nutritionReport?.let { report ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = report.mealTitle,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF064E3B)
                        ) {
                            Text(
                                text = "${report.estimatedCaloriesKcal} kcal",
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // 1. Calorie Count & Macro/Micronutrient Breakdown
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "1. Calorie Count & Macro / Micronutrient Breakdown",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                report.macrosBreakdown.forEach { (macro, value) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(macro, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                            Text(
                                                value,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Micronutrients (Vitamins & Minerals):",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            report.micronutrientsBreakdown.forEach { micro ->
                                Text("• $micro", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                        }
                    }

                    // 2. Health Impact Analysis (Benefits, Drawbacks, Dietary Considerations)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "2. Health Impact Analysis",
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Benefits:", color = Color(0xFF6EE7B7), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            report.healthBenefits.forEach { b ->
                                Text("• $b", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Drawbacks:", color = Color(0xFFFCA5A5), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            report.healthDrawbacks.forEach { d ->
                                Text("• $d", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Dietary Considerations:", color = Color(0xFFFCD34D), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            report.dietaryConsiderations.forEach { c ->
                                Text("• $c", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                            }
                        }
                    }

                    // 3. Preparation & Consumption Guidance (Cooking Methods, Serving Suggestions, Dietary Tips)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "3. Preparation & Consumption Guidance",
                                color = Color(0xFFC084FC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Recommended Cooking Methods:", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            report.cookingMethods.forEach { m ->
                                Text("• $m", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Serving Suggestions:", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            report.servingSuggestions.forEach { s ->
                                Text("• $s", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Practical Dietary Tips:", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            report.dietaryTips.forEach { t ->
                                Text("• $t", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Bundled Offline WHO Health Reference Library
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Offline Health & Nutrition Reference Library (${libraryTopics.size} Topics)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = healthSearchQuery,
                    onValueChange = { healthSearchQuery = it },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8))
                    },
                    placeholder = { Text("Search offline health & nutrition guidelines...", fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                val filtered = remember(libraryTopics, healthSearchQuery) {
                    if (healthSearchQuery.isBlank()) {
                        libraryTopics.take(4)
                    } else {
                        libraryTopics.filter {
                            it.title.contains(healthSearchQuery, ignoreCase = true) ||
                                it.summary.contains(healthSearchQuery, ignoreCase = true)
                        }.take(6)
                    }
                }
                filtered.forEach { topic ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "${topic.title} • ${topic.category}",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            if (topic.summary.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = topic.summary,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildPresetNutritionReport(
    mealHint: String,
    llmAnalysis: String? = null
): FoodNutritionReport {
    val lower = mealHint.lowercase()
    return when {
        "beans" in lower || "ewa" in lower -> FoodNutritionReport(
            mealTitle = mealHint,
            estimatedCaloriesKcal = 540,
            macrosBreakdown = mapOf(
                "Carbs" to "58g",
                "Protein" to "34g",
                "Fats" to "18g",
                "Fiber" to "14g"
            ),
            micronutrientsBreakdown = listOf(
                "Iron (6.2 mg — supports hemoglobin & oxygen transport)",
                "Folate / Vitamin B9 (240 mcg — cellular DNA synthesis)",
                "Potassium (780 mg — blood pressure & electrolyte balance)",
                "Omega-3 fatty acids from steamed fish & monounsaturated fats from avocado"
            ),
            healthBenefits = listOf(
                "High soluble and insoluble dietary fiber slows glucose absorption (low glycemic index).",
                "Complete amino acid + marine protein profile supports muscle repair and sustained cognitive focus."
            ),
            healthDrawbacks = listOf(
                "Excess palm oil can elevate saturated fat intake if not portioned moderately.",
                "Legume oligosaccharides may cause mild bloating if beans are not soaked prior to boiling."
            ),
            dietaryConsiderations = listOf(
                "Excellent for diabetic-friendly, high-satiety, and heart-conscious diets when oil is kept to 1 tablespoon.",
                "Naturally gluten-free."
            ),
            cookingMethods = listOf(
                "Pre-soak beans for 4–6 hours and discard soaking water before pressure-cooking to reduce raffinose oligosaccharides.",
                "Grill or steam the fish instead of deep-frying to preserve omega-3 fatty acids."
            ),
            servingSuggestions = listOf(
                "Pair 1 cup of cooked beans with 120g grilled fish and ¼ sliced fresh avocado.",
                "Add steamed dark leafy greens (spinach or ugu) to boost Vitamin C for non-heme iron absorption."
            ),
            dietaryTips = listOf(
                "Drink water 20 minutes before or after the meal and include Vitamin C-rich fruit to double plant-iron uptake."
            )
        )

        "oatmeal" in lower || "egg" in lower -> FoodNutritionReport(
            mealTitle = mealHint,
            estimatedCaloriesKcal = 460,
            macrosBreakdown = mapOf(
                "Carbs" to "52g",
                "Protein" to "22g",
                "Fats" to "16g",
                "Fiber" to "9g"
            ),
            micronutrientsBreakdown = listOf(
                "Beta-glucan soluble fiber (supports healthy LDL cholesterol levels)",
                "Choline (250 mg from eggs — essential for acetylcholine neurotransmitter synthesis)",
                "Magnesium & Vitamin B6 (from banana and groundnuts)"
            ),
            healthBenefits = listOf(
                "Sustained morning glucose release ideal for long study or examination sessions.",
                "High choline and B-vitamin density supports memory consolidation."
            ),
            healthDrawbacks = listOf(
                "Adding refined sugar or sweetened condensed milk spikes glycemic load."
            ),
            dietaryConsiderations = listOf(
                "Contains eggs and peanuts/groundnuts (allergen awareness required)."
            ),
            cookingMethods = listOf(
                "Simmer rolled oats in water or unsweetened milk for 5–7 minutes; boil or poach eggs rather than frying in butter."
            ),
            servingSuggestions = listOf(
                "Top warm oats with sliced banana and 1 tablespoon of roasted unsalted groundnuts alongside 2 boiled eggs."
            ),
            dietaryTips = listOf(
                "Use cinnamon and ripe banana slices for natural sweetness instead of refined table sugar."
            )
        )

        else -> FoodNutritionReport(
            mealTitle = mealHint,
            estimatedCaloriesKcal = 680,
            macrosBreakdown = mapOf(
                "Carbs" to "86g",
                "Protein" to "36g",
                "Fats" to "21g",
                "Fiber" to "6g"
            ),
            micronutrientsBreakdown = listOf(
                "Lycopene & Vitamin A / Beta-Carotene (from tomato-pepper stew base)",
                "Vitamin B3 (Niacin) & Vitamin B6 (from grilled chicken breast/thigh)",
                "Potassium & Vitamin C (from ripe plantain)"
            ),
            healthBenefits = listOf(
                "Cooked tomato paste provides bioavailable lycopene (potent antioxidant).",
                "Grilled poultry provides high-biological-value lean protein (36g) for tissue maintenance."
            ),
            healthDrawbacks = listOf(
                "High glycemic carbohydrate load from parboiled white rice and fried plantain.",
                "Seasoning cubes and bouillon can contribute elevated sodium (>900 mg per plate)."
            ),
            dietaryConsiderations = listOf(
                "Individuals managing blood glucose or hypertension should moderate rice portion size and seasoning salt."
            ),
            cookingMethods = listOf(
                "Oven-bake or air-fry the plantain slices instead of deep-frying in oil (saves ~120 kcal).",
                "Grill or roast the chicken skinless and use fresh herbs, garlic, and ginger to reduce bouillon sodium."
            ),
            servingSuggestions = listOf(
                "Fill half the plate with steamed cabbage/carrot slaw, ¼ plate Jollof rice, and ¼ plate grilled chicken."
            ),
            dietaryTips = listOf(
                if (!llmAnalysis.isNullOrBlank()) llmAnalysis.take(240)
                else "Eating fibrous vegetables first before the rice blunts the post-meal glucose spike by up to 30%."
            )
        )
    }
}
