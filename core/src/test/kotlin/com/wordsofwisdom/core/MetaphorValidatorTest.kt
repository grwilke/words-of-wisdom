package com.wordsofwisdom.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetaphorValidatorTest {

    @Test
    fun acceptsClassicScarecrowMetaphor() {
        val res = MetaphorValidator.validate(
            metaphor = "A single scarecrow in the field only smiles when he sees the crows",
            originalLesson = "Nobody can be happy alone",
            strictGuard = true,
        )
        assertTrue(res.valid, "Expected scarecrow metaphor to be valid, got: ${res.reason}")
        assertTrue(res.forbiddenWords.isEmpty())
    }

    @Test
    fun rejectsTooShortOrTooFewWords() {
        val tooShort = MetaphorValidator.validate("Sad bird", "Nobody can be happy alone", strictGuard = true)
        assertFalse(tooShort.valid)
        val twoLongWords = MetaphorValidator.validate("Lonely scarecrow", "Nobody can be happy alone", strictGuard = true)
        assertFalse(twoLongWords.valid)
    }

    @Test
    fun rejectsOverMaxLength() {
        val huge = "A ".repeat(95) + "scarecrow smiles in the field"
        val res = MetaphorValidator.validate(huge, "Nobody can be happy alone", strictGuard = true)
        assertFalse(res.valid)
    }

    @Test
    fun rejectsLiteralContentWordReuseInStrictMode() {
        val res = MetaphorValidator.validate(
            metaphor = "Nobody ever feels happy sitting completely alone at home",
            originalLesson = "Nobody can be happy alone",
            strictGuard = true,
        )
        assertFalse(res.valid)
        assertTrue(res.forbiddenWords.any { it.equals("happy", ignoreCase = true) || it.equals("alone", ignoreCase = true) })
    }

    @Test
    fun rejectsMorphologicalVariantsOfLessonWords() {
        val res = MetaphorValidator.validate(
            metaphor = "True happiness never arrives when walking blindly in loneliness",
            originalLesson = "Nobody can be happy alone",
            strictGuard = true,
        )
        assertFalse(res.valid, "Morphological stem 'happiness' should collide with 'happy'")
    }

    @Test
    fun allowsCommonStopWordsWithoutFalsePositive() {
        val res = MetaphorValidator.validate(
            metaphor = "A lone candle in the wind flickers until another flame arrives",
            originalLesson = "You rarely miss water until the well runs dry",
            strictGuard = true,
        )
        assertTrue(res.valid, "Stop word 'until' should not trigger anti-spoiler guard: ${res.reason}")
    }

    @Test
    fun relaxedModeStillRejectsNearExactCopy() {
        val exactCopy = MetaphorValidator.validate(
            metaphor = "Nobody can be happy alone!",
            originalLesson = "Nobody can be happy alone",
            strictGuard = false,
        )
        assertFalse(exactCopy.valid)

        val mildOverlap = MetaphorValidator.validate(
            metaphor = "A happy scarecrow in a quiet field waves at passing crows",
            originalLesson = "Nobody can be happy alone",
            strictGuard = false,
        )
        assertTrue(mildOverlap.valid, "Relaxed mode should permit single-word overlap when distinct metaphor")
    }

    @Test
    fun rejectsGibberishSymbolsAndRepeatedChars() {
        val symbols = MetaphorValidator.validate("!!! ??? --- *** %%%", "Nobody can be happy alone", strictGuard = true)
        assertFalse(symbols.valid)
        val mash = MetaphorValidator.validate("aaaaaa bbbbbb cccccc", "Nobody can be happy alone", strictGuard = true)
        assertFalse(mash.valid)
    }

    @Test
    fun extractsReadableForbiddenHintsForSageUi() {
        val hints = MetaphorValidator.forbiddenHintWords("Nobody can be happy alone")
        assertTrue("happy" in hints)
        assertTrue("alone" in hints)
        assertFalse("can" in hints)
        assertFalse("be" in hints)
        assertEquals(hints.distinct(), hints)
    }
}
