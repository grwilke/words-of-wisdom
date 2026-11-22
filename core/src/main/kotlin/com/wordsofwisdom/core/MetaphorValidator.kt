package com.wordsofwisdom.core

import kotlin.math.max
import kotlin.math.min

/**
 * Anti-spoiler and playability guard for a Sage's metaphorical rewrite.
 *
 * Balances strictness (blocking literal word/stem reuse in strict mode) with fairness
 * (ignoring common English stop words and allowing creative metaphors).
 */
object MetaphorValidator {

    const val MIN_LENGTH = 12
    const val MAX_LENGTH = 180
    const val MIN_WORDS = 3

    private val WORD_REGEX = Regex("[A-Za-z']+")

    private val STOP_WORDS: Set<String> = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "to", "of", "in", "on", "at", "by", "for", "with", "from", "as", "into",
        "and", "or", "but", "nor", "so", "yet", "if", "than", "that", "this",
        "these", "those", "it", "its", "you", "your", "yourself", "we", "our",
        "they", "their", "them", "he", "him", "his", "she", "her", "who", "whom",
        "what", "which", "when", "where", "why", "how", "can", "cannot", "cant",
        "could", "will", "would", "shall", "should", "may", "might", "must",
        "do", "does", "did", "dont", "doesnt", "didnt", "not", "no", "never",
        "always", "every", "all", "one", "ones", "only", "just", "more", "most",
        "less", "very", "too", "own", "have", "has", "had", "make", "makes",
        "made", "take", "takes", "give", "gives", "get", "gets", "go", "goes",
        "come", "comes", "see", "sees", "know", "knows", "even", "still", "much",
        "many", "some", "any", "each", "other", "another", "before", "after",
        "without", "within", "through", "over", "under", "about", "out", "up",
        "down", "off", "away", "often", "sometimes", "there", "here", "then",
        "until", "while", "because", "although", "though", "since", "unless",
        "whether", "either", "neither", "both", "such", "same", "also", "like",
        "back", "keep", "keeps", "kept", "put", "puts", "let", "lets",
    )

    private val SUFFIXES = listOf(
        "iness", "ingly", "edly", "ment", "ness", "tion", "sion", "able", "ible",
        "less", "ful", "ing", "ies", "ied", "ers", "est", "ly", "ed", "es", "s",
    )

    /** Irregular morphological root aliases so 'alone' catches 'loneliness'/'lonely'. */
    private val ROOT_ALIASES: Map<String, String> = mapOf(
        "alone" to "lone",
        "lonely" to "lone",
        "loneliness" to "lone",
        "happy" to "happ",
        "happily" to "happ",
        "happiness" to "happ",
        "unhappy" to "happ",
        "wise" to "wis",
        "wisdom" to "wis",
        "wisely" to "wis",
        "wisest" to "wis",
        "true" to "tru",
        "truth" to "tru",
        "truly" to "tru",
        "strong" to "strong",
        "strength" to "strong",
        "brave" to "brav",
        "bravery" to "brav",
        "angry" to "angr",
        "anger" to "angr",
        "proud" to "prid",
        "pride" to "prid",
        "kind" to "kind",
        "kindness" to "kind",
        "kindly" to "kind",
        "patient" to "patienc",
        "patience" to "patienc",
        "forgive" to "forgiv",
        "forgiveness" to "forgiv",
        "forgiving" to "forgiv",
        "fail" to "fail",
        "failure" to "fail",
        "failing" to "fail",
        "friend" to "friend",
        "friendship" to "friend",
        "friendly" to "friend",
    )

    fun stem(raw: String): String {
        val w = buildString(raw.length) {
            for (c in raw) {
                if (c in 'A'..'Z') append(c + 32) else if (c in 'a'..'z') append(c)
            }
        }
        ROOT_ALIASES[w]?.let { return it }
        if (w.length <= 3) return w
        for (suffix in SUFFIXES) {
            if (w.endsWith(suffix) && w.length - suffix.length >= 3) {
                val base = w.substring(0, w.length - suffix.length)
                val normalized = if (suffix == "ies" || suffix == "ied" || suffix == "iness") {
                    "${base}y"
                } else {
                    base
                }
                return ROOT_ALIASES[normalized] ?: normalized
            }
        }
        return w
    }

    fun extractWords(text: String): List<String> =
        WORD_REGEX.findAll(text).map { it.value.replace("'", "").lowercase() }
            .filter { it.isNotEmpty() }
            .toList()

    fun forbiddenHintWords(originalLesson: String): List<String> =
        extractWords(originalLesson)
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .distinct()

    fun validate(
        metaphor: String,
        originalLesson: String,
        strictGuard: Boolean = true,
    ): MetaphorValidationResult {
        val trimmed = metaphor.trim()
        if (trimmed.length > MAX_LENGTH) {
            return MetaphorValidationResult(
                valid = false,
                reason = "Keep your metaphor under $MAX_LENGTH characters so everyone on the couch can read it.",
            )
        }

        if (countEmojis(trimmed) >= 3) {
            return MetaphorValidationResult(valid = true)
        }

        if (trimmed.length < MIN_LENGTH) {
            return MetaphorValidationResult(
                valid = false,
                reason = "Write a fuller metaphor (at least $MIN_LENGTH characters).",
            )
        }

        val words = extractWords(trimmed)
        if (words.size < MIN_WORDS) {
            return MetaphorValidationResult(
                valid = false,
                reason = "Use at least $MIN_WORDS English words to paint a clear metaphor.",
            )
        }

        if (isGibberish(trimmed, words)) {
            return MetaphorValidationResult(
                valid = false,
                reason = "Use real English words instead of repeated letters or symbols.",
            )
        }

        if (isNearExactCopy(trimmed, originalLesson, words)) {
            return MetaphorValidationResult(
                valid = false,
                reason = "That's too close to the original lesson! Rewrite it as an image or story.",
                forbiddenWords = forbiddenHintWords(originalLesson),
            )
        }

        if (strictGuard) {
            val lessonHints = forbiddenHintWords(originalLesson)
            val draftStems = words.map(::stem).toHashSet()
            val collided = lessonHints.filter { hint -> stem(hint) in draftStems }
            if (collided.isNotEmpty()) {
                val preview = collided.joinToString(", ") { "'$it'" }
                return MetaphorValidationResult(
                    valid = false,
                    reason = "Try a metaphor instead of reusing literal words: $preview.",
                    forbiddenWords = collided,
                )
            }
        }

        return MetaphorValidationResult(valid = true)
    }

    private fun countEmojis(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (cp in 0x1F300..0x1FAFF || cp in 0x2600..0x27BF) {
                count++
            }
            i += charCount
        }
        return count
    }

    private fun isGibberish(trimmed: String, words: List<String>): Boolean {
        val nonSpace = trimmed.count { !it.isWhitespace() }
        val letters = trimmed.count { it.isLetter() }
        if (nonSpace == 0 || letters.toDouble() / nonSpace < 0.55) return true

        val mashedWords = words.count { w ->
            w.length >= 4 && (w.toSet().size <= 1 || hasFourRepeatedChars(w))
        }
        return mashedWords >= max(1, (words.size + 1) / 2)
    }

    private fun hasFourRepeatedChars(word: String): Boolean {
        var run = 1
        for (i in 1 until word.length) {
            if (word[i] == word[i - 1]) {
                run++
                if (run >= 4) return true
            } else {
                run = 1
            }
        }
        return false
    }

    private fun isNearExactCopy(trimmed: String, originalLesson: String, draftWords: List<String>): Boolean {
        val normDraft = draftWords.joinToString(" ")
        val lessonWords = extractWords(originalLesson)
        val normLesson = lessonWords.joinToString(" ")
        if (normDraft == normLesson) return true
        if (normDraft.contains(normLesson) || normLesson.contains(normDraft)) return true

        val draftSet = draftWords.map(::stem).toSet()
        val lessonSet = lessonWords.map(::stem).toSet()
        val union = (draftSet + lessonSet).size.coerceAtLeast(1)
        val jaccard = (draftSet intersect lessonSet).size.toDouble() / union
        return jaccard >= 0.72
    }
}
