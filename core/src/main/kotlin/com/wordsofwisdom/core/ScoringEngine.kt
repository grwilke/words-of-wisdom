package com.wordsofwisdom.core

import kotlin.math.roundToInt

data class SeekerScoreResult(
    val basePoints: Int,
    val speedBonus: Int,
    val streakBonus: Int,
    val totalPoints: Int,
)

data class SageScoreResult(
    val basePoints: Int,
    val clarityBonus: Int,
    val totalPoints: Int,
)

/**
 * Balanced couch-party scoring model for Seekers and Sages across 2..10 players.
 *
 * - Seekers earn +100 base, up to +30 speed bonus, and +20 per prior streak (capped at +60).
 * - Sages earn strictly monotonic points per correct seeker, normalized to 150 + 30 clarity bonus
 *   ("Master Sage") when 100% of seekers (>= 2) decode the metaphor.
 * - Applause adds a small bounded charm bonus (+10 per bravo, max +30).
 */
object ScoringEngine {

    const val SEEKER_BASE_POINTS = 100
    const val MAX_SPEED_BONUS = 30
    const val STREAK_STEP_POINTS = 20
    const val MAX_STREAK_BONUS = 60

    const val SAGE_FULL_SCALE = 150
    const val SAGE_MASTER_CLARITY_BONUS = 30

    const val APPLAUSE_POINTS_EACH = 10
    const val MAX_APPLAUSE_BONUS = 30

    fun scoreSeeker(
        correct: Boolean,
        remainingMs: Long,
        totalGuessMs: Long,
        previousStreak: Int,
    ): SeekerScoreResult {
        if (!correct) return SeekerScoreResult(0, 0, 0, 0)
        val ratio = (remainingMs.toDouble() / totalGuessMs.coerceAtLeast(1L)).coerceIn(0.0, 1.0)
        val speed = (MAX_SPEED_BONUS * ratio).roundToInt()
        val streak = if (previousStreak >= 1) {
            (previousStreak * STREAK_STEP_POINTS).coerceAtMost(MAX_STREAK_BONUS)
        } else {
            0
        }
        return SeekerScoreResult(
            basePoints = SEEKER_BASE_POINTS,
            speedBonus = speed,
            streakBonus = streak,
            totalPoints = SEEKER_BASE_POINTS + speed + streak,
        )
    }

    fun scoreSage(
        correctCount: Int,
        seekerCount: Int,
    ): SageScoreResult {
        if (correctCount <= 0 || seekerCount <= 0) return SageScoreResult(0, 0, 0)
        val clampedCorrect = correctCount.coerceAtMost(seekerCount)
        if (seekerCount == 1) {
            return SageScoreResult(
                basePoints = SAGE_FULL_SCALE,
                clarityBonus = 0,
                totalPoints = SAGE_FULL_SCALE,
            )
        }
        val base = (SAGE_FULL_SCALE.toDouble() * clampedCorrect / seekerCount).roundToInt()
        val clarity = if (clampedCorrect == seekerCount) SAGE_MASTER_CLARITY_BONUS else 0
        return SageScoreResult(
            basePoints = base,
            clarityBonus = clarity,
            totalPoints = base + clarity,
        )
    }

    fun applauseBonus(applauseCount: Int): Int =
        (applauseCount.coerceAtLeast(0) * APPLAUSE_POINTS_EACH).coerceAtMost(MAX_APPLAUSE_BONUS)
}
