package com.bismuth.app.teaching

import android.content.Context
import com.bismuth.app.engine.BismuthEngine
import com.bismuth.app.engine.BismuthModelConfig
import com.bismuth.app.profile.StudentProfile
import com.bismuth.app.skills.SubjectSkillCatalog
import com.bismuth.app.skills.TopicSkillBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class DynamicStepQuiz(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
    val misconceptionRationale: String = "",
    val isAiGenerated: Boolean = false
)

object DynamicQuizOrchestrator {

    /**
     * Modern 2026 Skill Orchestration:
     * Dispatches a step-scoped Socratic prompt to LiteRT-LM (Gemma 4 / 3N) to dynamically
     * synthesize a personalized checkpoint challenge. If the local model is not yet loaded,
     * it generates a dynamic algebraic/conceptual variation rooted in the SKILL's misconception matrix.
     */
    suspend fun generateStepQuiz(
        context: Context,
        bundle: TopicSkillBundle,
        step: BoardStep,
        profile: StudentProfile,
        selectedModel: BismuthModelConfig?
    ): DynamicStepQuiz = withContext(Dispatchers.IO) {
        // Attempt on-device LiteRT-LM inference first if model is present
        if (selectedModel != null && selectedModel.isDownloaded(context)) {
            val dynamicAiQuiz = tryGenerateViaLiteRt(context, bundle, step, profile, selectedModel)
            if (dynamicAiQuiz != null) {
                return@withContext dynamicAiQuiz
            }
        }

        // Skill-driven variation / assessment fallback anchored in SKILL.md misconception rules
        return@withContext generateProceduralSkillVariation(bundle, step)
    }

    private suspend fun tryGenerateViaLiteRt(
        context: Context,
        bundle: TopicSkillBundle,
        step: BoardStep,
        profile: StudentProfile,
        selectedModel: BismuthModelConfig
    ): DynamicStepQuiz? {
        val languageRule = com.bismuth.app.profile.StudentProfilePrefs.languageInstruction(profile.preferredLanguage)
        val systemPrompt = """
            You are Bismuth Socratic Orchestrator. 
            Student: ${profile.name}, Level: ${profile.academicLevel}, Style: ${profile.learningStyle}, Preferred Language: ${profile.preferredLanguage}.
            Skill Module: ${bundle.title} (Topic ID: ${bundle.topicId}).
            Active Step: ${step.stepNumber} (${step.title}).
            Language Rule: $languageRule
            Golden Invariant: ${bundle.instructions.socraticSystemPrompt}
            Generate one rigorous multiple-choice checkpoint question testing this exact step.
            Format EXACTLY:
            QUESTION: <question text>
            A) <option 1>
            B) <option 2>
            C) <option 3>
            D) <option 4>
            CORRECT: <A, B, C, or D>
            EXPLANATION: <short Socratic verification>
        """.trimIndent()

        val prompt = "Synthesize an interactive checkpoint question for step ${step.stepNumber}: ${step.title}. Ensure distractors test common student misconceptions."

        val output = BismuthEngine.generateTextOrNull(
            context = context,
            prompt = prompt,
            config = selectedModel,
            systemPrompt = systemPrompt
        ) ?: return null

        return parseModelQuizOutput(output)
    }

    private fun parseModelQuizOutput(raw: String): DynamicStepQuiz? {
        try {
            val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
            var question: String? = null
            val options = mutableListOf<String>()
            var correctLetter = 'A'
            var explanation = "Well done! Your inverse operation preserves mathematical equality."

            for (line in lines) {
                when {
                    line.startsWith("QUESTION:", ignoreCase = true) -> {
                        question = line.substringAfter(":").trim()
                    }
                    line.matches(Regex("^[A-D]\\).*")) -> {
                        options.add(line.substring(2).trim())
                    }
                    line.startsWith("CORRECT:", ignoreCase = true) -> {
                        correctLetter = line.substringAfter(":").trim().firstOrNull()?.uppercaseChar() ?: 'A'
                    }
                    line.startsWith("EXPLANATION:", ignoreCase = true) -> {
                        explanation = line.substringAfter(":").trim()
                    }
                }
            }

            if (question != null && options.size == 4) {
                val correctIndex = (correctLetter - 'A').coerceIn(0, 3)
                return DynamicStepQuiz(
                    question = question,
                    options = options,
                    correctIndex = correctIndex,
                    explanation = explanation,
                    isAiGenerated = true
                )
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Procedural & Skill-Anchored Generator:
     * Produces randomized algebraic variations for Linear Equations and uses each topic's
     * verified `inlineQuiz`, `assessment.json`, and `SKILL.md` misconception matrix for all 10 modules.
     */
    private fun generateProceduralSkillVariation(
        bundle: TopicSkillBundle,
        step: BoardStep
    ): DynamicStepQuiz {
        val topicHint = bundle.instructions.commonMisconceptions
            .getOrElse(step.stepNumber - 1) { bundle.instructions.commonMisconceptions.firstOrNull().orEmpty() }
            .ifBlank { step.tutorTip.ifBlank { "Review Step ${step.stepNumber} carefully before answering." } }

        if (bundle.topicId == "math_linear_equations") {
            when (step.stepNumber) {
                1 -> {
                    val a = Random.nextInt(2, 6)
                    val b = Random.nextInt(3, 9)
                    val xVal = Random.nextInt(2, 7)
                    val c = a * xVal + b

                    return DynamicStepQuiz(
                        question = "In the linear equation $a·x + $b = $c, what is the required inverse operation to eliminate the constant +$b from the left side?",
                        options = listOf(
                            "Subtract $b from both sides",
                            "Divide only $a·x by $a",
                            "Add $c to both sides",
                            "Multiply the left side by $b"
                        ),
                        correctIndex = 0,
                        explanation = "Subtracting $b from both sides cancels +$b on the left side, leaving $a·x = ${c - b} while strictly preserving the balance invariant.",
                        misconceptionRationale = "Dividing before clearing the constant produces fractional terms and violates reverse PEMDAS order."
                    )
                }
                2 -> {
                    val a = Random.nextInt(2, 5)
                    val b = Random.nextInt(4, 10)
                    val xVal = Random.nextInt(3, 8)
                    val c = a * xVal - b

                    return DynamicStepQuiz(
                        question = "If solving $a·x - $b = $c, which transformation preserves balance and isolates the variable term?",
                        options = listOf(
                            "Add $b to both sides, yielding $a·x = ${c + b}",
                            "Subtract $b from both sides, yielding $a·x = ${c - b}",
                            "Divide both sides by $a immediately",
                            "Add $c to both sides"
                        ),
                        correctIndex = 0,
                        explanation = "The inverse of -$b is +$b. Adding $b to both sides gives $a·x = ${c + b}.",
                        misconceptionRationale = "Subtracting $b when moving -$b results in a sign error (-$b - $b = -2b)."
                    )
                }
                3 -> {
                    val a = Random.nextInt(2, 6)
                    val xVal = Random.nextInt(3, 9)
                    val rhs = a * xVal

                    return DynamicStepQuiz(
                        question = "To isolate x from $a·x = $rhs, what multiplicative inverse must be applied to both sides?",
                        options = listOf(
                            "Divide both sides by $a (x = $xVal)",
                            "Subtract $a from both sides (x = ${rhs - a})",
                            "Multiply both sides by $a (x = ${rhs * a})",
                            "Divide only the right side by $a"
                        ),
                        correctIndex = 0,
                        explanation = "Since x is multiplied by $a, dividing both sides by $a yields x = $rhs / $a = $xVal. Substitution confirms: $a($xVal) = $rhs.",
                        misconceptionRationale = "Applying the division to only one side destroys the equality invariant."
                    )
                }
            }
        }

        // Primary step checkpoint from SKILL-curated scene.json
        val stepQuiz = step.inlineQuiz
        if (stepQuiz != null) {
            return DynamicStepQuiz(
                question = stepQuiz.question,
                options = stepQuiz.options,
                correctIndex = stepQuiz.correctIndex,
                explanation = stepQuiz.explanation,
                misconceptionRationale = topicHint
            )
        }

        // Fallback to module-level assessment.json question aligned with step index
        val assessmentFallback = bundle.assessment.getOrNull((step.stepNumber - 1).coerceAtLeast(0))
        if (assessmentFallback != null) {
            return DynamicStepQuiz(
                question = assessmentFallback.question,
                options = assessmentFallback.options,
                correctIndex = assessmentFallback.correctIndex,
                explanation = assessmentFallback.explanation,
                misconceptionRationale = topicHint
            )
        }

        val targetPrinciple = bundle.instructions.learningObjectives
            .getOrElse(step.stepNumber - 1) { step.narration.ifBlank { bundle.summary } }

        return DynamicStepQuiz(
            question = "Which principle correctly guides Step ${step.stepNumber} (${step.title}) in ${bundle.title}?",
            options = listOf(
                targetPrinciple,
                bundle.instructions.commonMisconceptions.firstOrNull() ?: "Skip verifying units and direction before calculating",
                "Apply formulas without checking the initial conditions or invariants",
                "Discard opposing factors when simplifying the problem"
            ),
            correctIndex = 0,
            explanation = step.narration.ifBlank { targetPrinciple },
            misconceptionRationale = topicHint
        )
    }
}
