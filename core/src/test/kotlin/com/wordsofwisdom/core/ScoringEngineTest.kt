package com.wordsofwisdom.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoringEngineTest {

    @Test
    fun seekerCorrectGuessGetsBaseSpeedAndStreakBonus() {
        val first = ScoringEngine.scoreSeeker(
            correct = true,
            remainingMs = 35_000L,
            totalGuessMs = 35_000L,
            previousStreak = 0,
        )
        assertEquals(100, first.basePoints)
        assertEquals(30, first.speedBonus)
        assertEquals(0, first.streakBonus)
        assertEquals(130, first.totalPoints)

        val thirdStreak = ScoringEngine.scoreSeeker(
            correct = true,
            remainingMs = 17_500L,
            totalGuessMs = 35_000L,
            previousStreak = 2,
        )
        assertEquals(100, thirdStreak.basePoints)
        assertEquals(15, thirdStreak.speedBonus)
        assertEquals(40, thirdStreak.streakBonus)
        assertEquals(155, thirdStreak.totalPoints)

        // Streak bonus caps at +60 even on huge streak
        val hugeStreak = ScoringEngine.scoreSeeker(
            correct = true,
            remainingMs = 0L,
            totalGuessMs = 35_000L,
            previousStreak = 10,
        )
        assertEquals(60, hugeStreak.streakBonus)
        assertEquals(160, hugeStreak.totalPoints)
    }

    @Test
    fun seekerWrongGuessScoresZero() {
        val miss = ScoringEngine.scoreSeeker(
            correct = false,
            remainingMs = 30_000L,
            totalGuessMs = 35_000L,
            previousStreak = 4,
        )
        assertEquals(0, miss.totalPoints)
        assertEquals(0, miss.basePoints)
        assertEquals(0, miss.speedBonus)
        assertEquals(0, miss.streakBonus)
    }

    @Test
    fun sageScoreIsStrictlyMonotonicInCorrectGuessersAndBalancedAcrossPartySizes() {
        for (seekers in 2..8) {
            var prev = -1
            for (correct in 0..seekers) {
                val res = ScoringEngine.scoreSage(correctCount = correct, seekerCount = seekers)
                assertTrue(
                    res.totalPoints > prev,
                    "Sage score must strictly increase with correct guessers (seekers=$seekers, correct=$correct, prev=$prev, cur=${res.totalPoints})",
                )
                prev = res.totalPoints
            }
            val zero = ScoringEngine.scoreSage(0, seekers)
            assertEquals(0, zero.totalPoints)
            val perfect = ScoringEngine.scoreSage(seekers, seekers)
            assertEquals(180, perfect.totalPoints, "Master Sage score should be balanced at 180 for seekers=$seekers")
            assertEquals(30, perfect.clarityBonus)
        }
    }

    @Test
    fun twoPlayerSageGetsBalancedRewardWithoutMultiSeekerBonus() {
        val hit = ScoringEngine.scoreSage(correctCount = 1, seekerCount = 1)
        assertEquals(150, hit.totalPoints)
        assertEquals(0, hit.clarityBonus)
    }

    @Test
    fun applauseBonusIsSmallBoundedCharmReward() {
        assertEquals(0, ScoringEngine.applauseBonus(0))
        assertEquals(10, ScoringEngine.applauseBonus(1))
        assertEquals(30, ScoringEngine.applauseBonus(3))
        assertEquals(30, ScoringEngine.applauseBonus(8)) // capped at +30 so it stays fun without skewing balance
    }
}
