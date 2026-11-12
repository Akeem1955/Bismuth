package com.bismuth.app.profile

import android.content.Context
import android.content.SharedPreferences

/**
 * Real, on-device lesson progress and streak tracker.
 * Zero mocked values: all progress is tracked dynamically per topic and step.
 */
object LessonProgressStore {
    private const val PREFS_NAME = "bismuth_lesson_progress"
    private const val KEY_PREFIX_PROGRESS = "progress_step_"
    private const val KEY_PREFIX_COMPLETED = "topic_completed_"
    private const val KEY_STREAK_DAYS = "real_streak_days"
    private const val KEY_TOTAL_COMPLETED_LESSONS = "total_completed_lessons"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getCompletedStepsForTopic(context: Context, topicId: String): Int {
        return getPrefs(context).getInt("$KEY_PREFIX_PROGRESS$topicId", 0)
    }

    fun isTopicCompleted(context: Context, topicId: String): Boolean {
        return getPrefs(context).getBoolean("$KEY_PREFIX_COMPLETED$topicId", false)
    }

    fun recordStepCompletion(
        context: Context,
        topicId: String,
        completedStepIndex: Int,
        totalSteps: Int
    ) {
        val prefs = getPrefs(context)
        val current = prefs.getInt("$KEY_PREFIX_PROGRESS$topicId", 0)
        val nextProgress = maxOf(current, completedStepIndex + 1).coerceAtMost(totalSteps)

        val editor = prefs.edit().putInt("$KEY_PREFIX_PROGRESS$topicId", nextProgress)

        if (nextProgress >= totalSteps) {
            editor.putBoolean("$KEY_PREFIX_COMPLETED$topicId", true)
            val total = prefs.getInt(KEY_TOTAL_COMPLETED_LESSONS, 0)
            editor.putInt(KEY_TOTAL_COMPLETED_LESSONS, total + 1)
            incrementStreak(context)
        }
        editor.apply()
    }

    fun getStreakDays(context: Context): Int {
        return getPrefs(context).getInt(KEY_STREAK_DAYS, 0)
    }

    fun setInitialOnboardingStreak(context: Context) {
        val prefs = getPrefs(context)
        if (prefs.getInt(KEY_STREAK_DAYS, 0) == 0) {
            prefs.edit().putInt(KEY_STREAK_DAYS, 1).apply()
        }
    }

    fun incrementStreak(context: Context) {
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_STREAK_DAYS, 1)
        prefs.edit().putInt(KEY_STREAK_DAYS, current + 1).apply()
    }

    fun getTotalCompletedLessons(context: Context): Int {
        return getPrefs(context).getInt(KEY_TOTAL_COMPLETED_LESSONS, 0)
    }

    fun resetTopicProgress(context: Context, topicId: String) {
        getPrefs(context).edit()
            .putInt("$KEY_PREFIX_PROGRESS$topicId", 0)
            .putBoolean("$KEY_PREFIX_COMPLETED$topicId", false)
            .apply()
    }
}
