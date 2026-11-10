package com.bismuth.app.teaching

import kotlinx.serialization.Serializable

@Serializable
data class BoardPoint(
    val x: Float = 0f,
    val y: Float = 0f,
)

@Serializable
data class BoardSize(
    val width: Float = 200f,
    val height: Float = 96f,
)

@Serializable
data class BoardStyle(
    val fillColor: String = "#1E293B",
    val strokeColor: String = "#38BDF8",
    val textColor: String = "#F8FAFC",
    val accentColor: String = "#00E5FF",
    val strokeWidth: Float = 2.5f,
    val cornerRadius: Float = 18f,
    val textSize: Float = 22f,
    val dashed: Boolean = false,
)

/**
 * Interactive board object on the teaching whiteboard.
 * Tapping the object on the canvas opens a pop-up explanation card.
 */
@Serializable
data class BoardObject(
    val objectId: String,
    val objectType: String, // "title", "box", "card", "circle", "formula", "arrow"
    val label: String,
    val subLabel: String = "",
    val explanation: String = "",
    val keyFormulaOrRule: String = "",
    val position: BoardPoint = BoardPoint(),
    val size: BoardSize = BoardSize(),
    val style: BoardStyle = BoardStyle(),
    val stepId: String? = null,
)

@Serializable
data class BoardArrow(
    val fromObjectId: String,
    val toObjectId: String,
    val label: String = "",
    val stepId: String? = null,
    val colorHex: String = "#38BDF8",
)

/**
 * Model-tier aware inline quiz presented before advancing to the next whiteboard step.
 * - Foundational prompt/options used on low-end tier (Gemma 3n Lite / E2B).
 * - Deep conceptual prompt/options used on high-end tier (Gemma 4 E2B / E4B).
 */
@Serializable
data class StepInlineQuiz(
    val basicQuestion: String,
    val basicOptions: List<String>,
    val basicCorrectIndex: Int,
    val basicExplanation: String,
    val advancedQuestion: String,
    val advancedOptions: List<String>,
    val advancedCorrectIndex: Int,
    val advancedExplanation: String,
)

@Serializable
data class BoardStep(
    val stepId: String,
    val stepNumber: Int,
    val title: String,
    val narration: String,
    val tutorTip: String = "",
    val focusObjectIds: List<String> = emptyList(),
    val inlineQuiz: StepInlineQuiz? = null,
)

@Serializable
data class TeachingBoardScene(
    val sceneId: String,
    val subjectId: String,
    val chapterTitle: String,
    val topicTitle: String,
    val theme: String = "neon_lab",
    val objects: List<BoardObject>,
    val arrows: List<BoardArrow> = emptyList(),
    val steps: List<BoardStep>,
) {
    fun visibleObjectsForStep(stepIndex: Int): List<BoardObject> {
        if (steps.isEmpty()) return objects
        val allowedStepIds = steps.take(stepIndex + 1).map { it.stepId }.toSet()
        return objects.filter { it.stepId == null || it.stepId in allowedStepIds }
    }

    fun visibleArrowsForStep(stepIndex: Int): List<BoardArrow> {
        if (steps.isEmpty()) return arrows
        val allowedStepIds = steps.take(stepIndex + 1).map { it.stepId }.toSet()
        return arrows.filter { it.stepId == null || it.stepId in allowedStepIds }
    }
}

enum class TutorRiveEmotion(val label: String, val emoji: String, val riveInput: String) {
    IDLE("Ready", "🤖", "isIdle"),
    EXPLAINING("Explaining", "🗣️", "isSpeaking"),
    THINKING("Thinking", "🧠", "isThinking"),
    CELEBRATING("Correct!", "🎉", "isExcited"),
    ENCOURAGING("Try Again", "💡", "isListening")
}
