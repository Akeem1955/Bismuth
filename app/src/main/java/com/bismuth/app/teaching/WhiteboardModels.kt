package com.bismuth.app.teaching

import kotlinx.serialization.Serializable

@Serializable
data class BoardPoint(
    val x: Float = 0f,
    val y: Float = 0f,
)

@Serializable
data class BoardSize(
    val width: Float = 180f,
    val height: Float = 88f,
)

@Serializable
data class BoardStyle(
    val strokeWidth: Float = 2f,
    val cornerRadius: Float = 16f,
    val isDashed: Boolean = false,
)

/**
 * Interactive board node on the teaching whiteboard.
 * Tapping this node on the canvas opens a native Material 3 ModalBottomSheet.
 */
@Serializable
data class BoardObject(
    val objectId: String,
    val objectType: String = "box", // "box", "circle", "formula", "highlight"
    val label: String,
    val subLabel: String = "",
    val formula: String = "",
    val explanation: String = "",
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
)

/**
 * Socratic inline checkpoint presented to verify comprehension before advancing to the next step.
 */
@Serializable
data class StepInlineQuiz(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String,
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
    val diagramResourceId: String = "",
    val diagramRelativePath: String = "",
    val videoResourceId: String = "",
    val videoRelativePath: String = "",
)

@Serializable
data class TeachingBoardScene(
    val sceneId: String,
    val subjectTitle: String,
    val chapterTitle: String,
    val topicTitle: String,
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
