package com.wordsofwisdom.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EasyAndSillyV13Test {

    @Test
    fun emojiMetaphorsAreAcceptedWithoutRequiringThreeEnglishWords() {
        val res = MetaphorValidator.validate(
            metaphor = "🦉🔥🍕",
            originalLesson = "Nobody can be happy alone",
            strictGuard = false,
        )
        assertTrue(res.valid, "Expected 3+ emoji metaphor to be valid: ${res.reason}")
    }

    @Test
    fun buildOptionsSupportsThreeEasyChoicesWithContrastDecoy() {
        val bank = LessonBank.loadDefault()
        val lesson = bank.lessons.first()
        val opts = bank.buildOptions(lesson, optionCount = 3, rng = Random(42))

        assertEquals(3, opts.size)
        assertEquals(1, opts.count { it.isCorrect })
        assertEquals(3, opts.map { it.text }.toSet().size)
        assertTrue(GameConfig.VALID_OPTION_COUNTS.contains(3))
    }
}
