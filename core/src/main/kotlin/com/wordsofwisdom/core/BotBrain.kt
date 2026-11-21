package com.wordsofwisdom.core

import kotlin.random.Random

/** Witty parlor personas and timing/guessing curves for built-in Practice AI Bots. */
data class BotPersona(
    val name: String,
    val avatar: PlayerAvatar,
    val writeDelayRangeMs: LongRange,
    val voteDelayRangeMs: LongRange,
    val baseAccuracy: Double,
    val applauseChance: Double,
)

object BotBrain {

    val PERSONAS: List<BotPersona> = listOf(
        BotPersona("Barnaby Hoot", AvatarCatalog.ALL[0], 2200L..3800L, 2200L..4200L, 0.74, 0.75),
        BotPersona("Vesper Sly", AvatarCatalog.ALL[1], 1800L..3000L, 1700L..3100L, 0.62, 0.45),
        BotPersona("Inky Jules", AvatarCatalog.ALL[2], 2400L..3900L, 2500L..4400L, 0.70, 0.65),
        BotPersona("Mochi Purr", AvatarCatalog.ALL[3], 2100L..3600L, 2300L..4100L, 0.66, 0.50),
        BotPersona("Sir Ribbit", AvatarCatalog.ALL[4], 1900L..3200L, 1900L..3400L, 0.58, 0.90),
        BotPersona("Ember Quill", AvatarCatalog.ALL[5], 2300L..3700L, 2100L..3900L, 0.78, 0.60),
        BotPersona("Pip Tuxedo", AvatarCatalog.ALL[6], 2000L..3500L, 2400L..4300L, 0.68, 0.70),
        BotPersona("Plume Bea", AvatarCatalog.ALL[7], 2500L..4000L, 2600L..4500L, 0.69, 0.85),
    )

    fun pickPersona(existingNames: Set<String>, rng: Random): BotPersona {
        val unused = PERSONAS.filterNot { it.name in existingNames }
        return if (unused.isNotEmpty()) unused[rng.nextInt(unused.size)] else PERSONAS[rng.nextInt(PERSONAS.size)]
    }

    fun planWriteDelayMs(persona: BotPersona, writeWindowMs: Long, rng: Random): Long {
        val raw = rng.nextLong(persona.writeDelayRangeMs.first, persona.writeDelayRangeMs.last + 1)
        return raw.coerceAtMost((writeWindowMs / 3).coerceAtLeast(1500L)).coerceAtMost(4_000L)
    }

    fun planVoteDelayMs(persona: BotPersona, guessWindowMs: Long, rng: Random): Long {
        val raw = rng.nextLong(persona.voteDelayRangeMs.first, persona.voteDelayRangeMs.last + 1)
        return raw.coerceAtMost((guessWindowMs / 2).coerceAtLeast(1500L)).coerceAtMost(4_500L)
    }

    fun simulatedTypedChars(elapsedMs: Long, plannedDelayMs: Long, targetLen: Int): Int {
        if (elapsedMs <= 350L) return 0
        val frac = ((elapsedMs - 350L).toDouble() / (plannedDelayMs - 350L).coerceAtLeast(1L)).coerceIn(0.0, 1.0)
        return (targetLen * frac).toInt()
    }

    fun chooseOptionId(
        persona: BotPersona,
        options: List<ChoiceOption>,
        correctOptionId: String,
        metaphorLength: Int,
        rng: Random,
    ): String {
        val clarityBoost = if (metaphorLength in 40..135) 0.06 else 0.0
        val accuracy = (persona.baseAccuracy + clarityBoost).coerceIn(0.45, 0.85)
        if (rng.nextDouble() < accuracy) return correctOptionId
        val decoys = options.filter { it.id != correctOptionId }
        return if (decoys.isNotEmpty()) decoys[rng.nextInt(decoys.size)].id else correctOptionId
    }
}
