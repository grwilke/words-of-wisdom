package com.wordsofwisdom.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LessonBankTest {

    @Test
    fun loadsBundledBankWith120LessonsAndValidBotMetaphors() {
        val bank = LessonBank.loadDefault()
        assertTrue(bank.lessons.size >= 120, "Expected >= 120 lessons, found ${bank.lessons.size}")
        assertEquals(10, bank.themes.size)
        assertTrue(bank.lessons.any { it.lesson == "Nobody can be happy alone" })

        for (entry in bank.lessons) {
            assertTrue(entry.decoys.size >= 5, "${entry.id} needs >= 5 decoys")
            val check = MetaphorValidator.validate(entry.botMetaphor, entry.lesson, strictGuard = true)
            assertTrue(check.valid, "Bundled botMetaphor invalid for ${entry.id}: ${check.reason}")
        }
    }

    @Test
    fun drawRespectsThemeFilterAndAvoidsRepeatsUntilExhausted() {
        val bank = LessonBank.loadDefault()
        val rng = Random(7)
        val used = mutableSetOf<String>()
        val drawn = (1..12).map {
            val l = bank.drawLesson(selectedThemes = listOf("Connection"), usedIds = used, rng = rng)
            used.add(l.id)
            l
        }
        assertTrue(drawn.all { it.theme == "Connection" })
        assertEquals(12, drawn.map { it.id }.toSet().size)

        // 13th draw gracefully recycles instead of crashing
        val recycled = bank.drawLesson(selectedThemes = listOf("Connection"), usedIds = used, rng = rng)
        assertEquals("Connection", recycled.theme)
    }

    @Test
    fun buildChoiceOptionsIncludesExactlyOneCorrectAndDistinctDecoys() {
        val bank = LessonBank.loadDefault()
        val lesson = bank.lessons.first()
        for (count in listOf(4, 5)) {
            val options = bank.buildOptions(lesson, optionCount = count, rng = Random(42))
            assertEquals(count, options.size)
            assertEquals(1, options.count { it.isCorrect })
            assertEquals(lesson.lesson, options.first { it.isCorrect }.text)
            assertEquals(count, options.map { it.text }.toSet().size)
            assertEquals((0 until count).map { ('A' + it).toString() }, options.map { it.id })
        }
    }

    @Test
    fun buildOptionsShufflesCorrectPositionAcrossSeeds() {
        val bank = LessonBank.loadDefault()
        val lesson = bank.lessons.first()
        val positions = (1..30).map { seed ->
            bank.buildOptions(lesson, 4, Random(seed)).first { it.isCorrect }.id
        }.toSet()
        assertTrue(positions.size >= 3, "Expected correct option to appear in varied positions, got $positions")
        assertNotEquals(setOf("A"), positions)
    }
}
