package com.wordsofwisdom.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedInputStream
import kotlin.random.Random

/**
 * Pre-indexed immutable library of curated life lessons and confusable decoys.
 * Loaded once via streaming UTF-8 decode and shared across games with zero redundant parsing.
 */
class LessonBank(rawLessons: List<WisdomLesson>) {

    private val lessonsArray: Array<WisdomLesson> = rawLessons.toTypedArray()
    val lessons: List<WisdomLesson> = lessonsArray.asList()
    val themes: List<String>

    private val themeIndexMap: HashMap<String, Int>
    private val themeBuckets: Array<IntArray>
    private val idToIndex: HashMap<String, Int>
    private val precomputedForbiddenHints: Array<List<String>>

    init {
        val grouped = LinkedHashMap<String, MutableList<Int>>(16)
        idToIndex = HashMap((lessonsArray.size / 0.75f).toInt() + 1)
        for (i in lessonsArray.indices) {
            val l = lessonsArray[i]
            idToIndex[l.id] = i
            grouped.getOrPut(l.theme) { ArrayList(16) }.add(i)
        }
        themes = grouped.keys.toList()
        themeIndexMap = HashMap((themes.size / 0.75f).toInt() + 1)
        themeBuckets = Array(themes.size) { tIdx ->
            val theme = themes[tIdx]
            themeIndexMap[theme] = tIdx
            grouped.getValue(theme).toIntArray()
        }
        precomputedForbiddenHints = Array(lessonsArray.size) { i ->
            MetaphorValidator.forbiddenHintWords(lessonsArray[i].lesson)
        }
    }

    fun forbiddenHintsFor(lesson: WisdomLesson): List<String> =
        idToIndex[lesson.id]?.let { precomputedForbiddenHints[it] }
            ?: MetaphorValidator.forbiddenHintWords(lesson.lesson)

    fun drawLesson(
        selectedThemes: List<String>,
        usedIds: Set<String>,
        rng: Random,
    ): WisdomLesson {
        require(lessonsArray.isNotEmpty()) { "LessonBank cannot be empty" }

        val candidates: List<IntArray> = if (selectedThemes.isEmpty()) {
            emptyList()
        } else {
            selectedThemes.mapNotNull { themeIndexMap[it]?.let { idx -> themeBuckets[idx] } }
        }

        if (candidates.isEmpty()) {
            return pickFromRange(lessonsArray.indices, usedIds, rng)
        }

        var freshCount = 0
        var totalCount = 0
        for (bucket in candidates) {
            totalCount += bucket.size
            for (idx in bucket) {
                if (lessonsArray[idx].id !in usedIds) freshCount++
            }
        }
        if (totalCount == 0) return pickFromRange(lessonsArray.indices, usedIds, rng)

        val useFresh = freshCount > 0
        var target = rng.nextInt(if (useFresh) freshCount else totalCount)
        for (bucket in candidates) {
            for (idx in bucket) {
                val lesson = lessonsArray[idx]
                if (!useFresh || lesson.id !in usedIds) {
                    if (target == 0) return lesson
                    target--
                }
            }
        }
        return lessonsArray[0]
    }

    private fun pickFromRange(range: IntRange, usedIds: Set<String>, rng: Random): WisdomLesson {
        var freshCount = 0
        for (i in range) {
            if (lessonsArray[i].id !in usedIds) freshCount++
        }
        if (freshCount == 0) {
            return lessonsArray[rng.nextInt(lessonsArray.size)]
        }
        var target = rng.nextInt(freshCount)
        for (i in range) {
            val l = lessonsArray[i]
            if (l.id !in usedIds) {
                if (target == 0) return l
                target--
            }
        }
        return lessonsArray[0]
    }

    fun buildOptions(
        lesson: WisdomLesson,
        optionCount: Int,
        rng: Random,
    ): List<ChoiceOption> {
        require(optionCount in 2..OPTION_IDS.size) { "Unsupported optionCount=$optionCount" }
        val texts = ArrayList<Pair<String, Boolean>>(optionCount)
        texts.add(lesson.lesson to true)

        val seen = HashSet<String>(optionCount * 2)
        seen.add(lesson.lesson.lowercase())

        val shuffledDecoys = lesson.decoys.shuffled(rng)
        for (decoy in shuffledDecoys) {
            if (texts.size >= optionCount) break
            val norm = decoy.trim()
            if (norm.isNotEmpty() && seen.add(norm.lowercase())) {
                texts.add(norm to false)
            }
        }

        // Graceful fallback if a custom lesson ever lacked enough unique decoys
        var fallbackTries = 0
        while (texts.size < optionCount && fallbackTries < lessonsArray.size * 2) {
            fallbackTries++
            val candidate = lessonsArray[rng.nextInt(lessonsArray.size)].lesson
            if (seen.add(candidate.lowercase())) {
                texts.add(candidate to false)
            }
        }

        val finalOrder = texts.shuffled(rng)
        return finalOrder.mapIndexed { idx, (text, isCorrect) ->
            ChoiceOption(
                id = OPTION_IDS[idx],
                text = text,
                isCorrect = isCorrect,
            )
        }
    }

    companion object {
        private const val RESOURCE_PATH = "/lessons/wisdom_bank.json"
        private val OPTION_IDS = arrayOf("A", "B", "C", "D", "E", "F")
        private val BankJson = Json { ignoreUnknownKeys = true }

        @OptIn(ExperimentalSerializationApi::class)
        private val defaultInstance: LessonBank by lazy(LazyThreadSafetyMode.PUBLICATION) {
            val stream = LessonBank::class.java.getResourceAsStream(RESOURCE_PATH)
                ?: Thread.currentThread().contextClassLoader?.getResourceAsStream(RESOURCE_PATH.removePrefix("/"))
                ?: error("Missing bundled lesson bank resource: $RESOURCE_PATH")
            BufferedInputStream(stream, 16 * 1024).use { buffered ->
                val bankFile = BankJson.decodeFromStream<WisdomBankFile>(buffered)
                LessonBank(bankFile.lessons)
            }
        }

        fun loadDefault(): LessonBank = defaultInstance
    }
}
