package com.wordsofwisdom.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedInputStream
import kotlin.random.Random

/**
 * Pre-indexed immutable library of curated life lessons (Classic + Modern Funny) and confusable decoys.
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
            grouped.getOrPut(l.theme) { ArrayList(20) }.add(i)
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

    private fun matchesPack(lesson: WisdomLesson, pack: PromptPack): Boolean = when (pack) {
        PromptPack.ALL -> true
        PromptPack.CLASSIC -> lesson.pack.equals("CLASSIC", ignoreCase = true)
        PromptPack.MODERN -> lesson.pack.equals("MODERN", ignoreCase = true)
    }

    fun drawLesson(
        selectedThemes: List<String>,
        usedIds: Set<String>,
        rng: Random,
        pack: PromptPack = PromptPack.ALL,
    ): WisdomLesson {
        require(lessonsArray.isNotEmpty()) { "LessonBank cannot be empty" }

        val themeSet = if (selectedThemes.isEmpty()) null else selectedThemes.toSet()
        val eligible = lessonsArray.filter { l ->
            matchesPack(l, pack) && (themeSet == null || l.theme in themeSet)
        }.ifEmpty {
            lessonsArray.filter { matchesPack(it, pack) }.ifEmpty { lessons }
        }

        val fresh = eligible.filter { it.id !in usedIds }
        val pool = if (fresh.isNotEmpty()) fresh else eligible
        return pool[rng.nextInt(pool.size)]
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
