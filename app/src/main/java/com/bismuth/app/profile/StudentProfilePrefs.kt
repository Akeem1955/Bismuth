package com.bismuth.app.profile

import android.content.Context
import android.content.SharedPreferences

data class StudentProfile(
    val name: String,
    val academicLevel: String,
    val primarySubject: String,
    val learningStyle: String,
    val preferredLanguage: String = "English",
    val streakDays: Int,
    val isConfigured: Boolean
)

object StudentProfilePrefs {
    private const val PREFS_NAME = "bismuth_student_profile"
    private const val KEY_NAME = "student_name"
    private const val KEY_LEVEL = "student_level"
    private const val KEY_SUBJECT = "student_primary_subject"
    private const val KEY_STYLE = "student_learning_style"
    private const val KEY_LANGUAGE = "student_preferred_language"
    private const val KEY_CONFIGURED = "student_is_configured"

    val ACADEMIC_LEVELS = listOf(
        "High School STEM",
        "AP / College Entrance Prep",
        "Undergraduate STEM",
        "Self-Directed Explorer"
    )

    val PRIMARY_SUBJECTS = listOf(
        "Mathematics",
        "Physics",
        "Chemistry",
        "Biology",
        "Computer Science"
    )

    val LEARNING_STYLES = listOf(
        "Visual Socratic (Balances & Proofs)",
        "Analogy-First Intuition",
        "Rigorous First-Principles Derivation"
    )

    val STUDY_LANGUAGES = listOf(
        "English",
        "Nigerian Pidgin"
    )

    fun languageInstruction(preferredLanguage: String): String {
        return if (preferredLanguage.equals("Nigerian Pidgin", ignoreCase = true)) {
            "Respond strictly in clear, natural, respectful Nigerian Pidgin English so concepts are easy for a student in Nigeria to understand, while keeping mathematical formulas and scientific terms accurate."
        } else {
            "Respond clearly in standard educational English."
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isConfigured(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CONFIGURED, false)
    }

    fun getProfile(context: Context): StudentProfile {
        val prefs = getPrefs(context)
        val isConfigured = prefs.getBoolean(KEY_CONFIGURED, false)
        val realStreak = LessonProgressStore.getStreakDays(context)

        return StudentProfile(
            name = prefs.getString(KEY_NAME, "") ?: "",
            academicLevel = prefs.getString(KEY_LEVEL, "Undergraduate STEM") ?: "Undergraduate STEM",
            primarySubject = prefs.getString(KEY_SUBJECT, "Mathematics") ?: "Mathematics",
            learningStyle = prefs.getString(KEY_STYLE, "Visual Socratic (Balances & Proofs)") ?: "Visual Socratic (Balances & Proofs)",
            preferredLanguage = prefs.getString(KEY_LANGUAGE, "English") ?: "English",
            streakDays = realStreak,
            isConfigured = isConfigured
        )
    }

    fun saveProfile(
        context: Context,
        name: String,
        academicLevel: String,
        primarySubject: String,
        learningStyle: String,
        preferredLanguage: String = "English"
    ) {
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_NAME, name.trim())
            .putString(KEY_LEVEL, academicLevel)
            .putString(KEY_SUBJECT, primarySubject)
            .putString(KEY_STYLE, learningStyle)
            .putString(KEY_LANGUAGE, preferredLanguage)
            .putBoolean(KEY_CONFIGURED, true)
            .apply()

        LessonProgressStore.setInitialOnboardingStreak(context)
    }

    fun resetProfile(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
