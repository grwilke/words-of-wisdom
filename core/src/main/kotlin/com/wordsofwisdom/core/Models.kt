package com.wordsofwisdom.core

import kotlinx.serialization.Serializable

/** Supported party phases in strict lifecycle order. */
@Serializable
enum class GamePhase {
    LOBBY,
    WRITING,
    GUESSING,
    REVEAL,
    TIE_BREAKER,
    PODIUM,
}

/** Game length preset controlling how many turns each player takes as the Sage. */
@Serializable
enum class GameLength(val label: String) {
    QUICK("Quick"),
    CLASSIC("Classic"),
    MARATHON("Marathon"),
}

/** Lobby-configurable ruleset tuned for couch playability. */
@Serializable
data class GameConfig(
    val length: GameLength = GameLength.CLASSIC,
    val writeSeconds: Int = 75,
    val guessSeconds: Int = 35,
    val revealSeconds: Int = 14,
    val optionCount: Int = 4, // 4 or 5
    val strictMetaphorGuard: Boolean = true,
    val enableTieBreaker: Boolean = true,
    val selectedThemes: List<String> = emptyList(), // empty = all themes
) {
    companion object {
        val VALID_WRITE_SECONDS = setOf(45, 60, 75, 90, 120)
        val VALID_GUESS_SECONDS = setOf(20, 30, 35, 45, 60)
        val VALID_OPTION_COUNTS = setOf(4, 5)
    }
}

/** Curated visual identity for each player on TV and phones. */
@Serializable
data class PlayerAvatar(
    val id: String,
    val emoji: String,
    val label: String,
    val colorHex: String,
)

object AvatarCatalog {
    val ALL: List<PlayerAvatar> = listOf(
        PlayerAvatar("owl", "🦉", "Wise Owl", "#F5B731"),
        PlayerAvatar("fox", "🦊", "Clever Fox", "#F26B3A"),
        PlayerAvatar("octopus", "🐙", "Ink Octopus", "#9B5DE5"),
        PlayerAvatar("cat", "🐱", "Velvet Cat", "#2EC486"),
        PlayerAvatar("frog", "🐸", "Pond Frog", "#3A86FF"),
        PlayerAvatar("dragon", "🐉", "Fable Dragon", "#E84855"),
        PlayerAvatar("penguin", "🐧", "Tux Penguin", "#38BDF8"),
        PlayerAvatar("peacock", "🦚", "Plume Peacock", "#14B8A6"),
        PlayerAvatar("bear", "🐻", "Hearth Bear", "#D97706"),
        PlayerAvatar("turtle", "🐢", "Sage Tortoise", "#65A30D"),
        PlayerAvatar("bee", "🐝", "Honey Bee", "#EAB308"),
        PlayerAvatar("flamingo", "🦩", "Parlor Flamingo", "#EC4899"),
    )

    fun forIndex(index: Int): PlayerAvatar = ALL[index.mod(ALL.size)]
}

/** Curated wisdom lesson entry loaded from wisdom_bank.json. */
@Serializable
data class WisdomLesson(
    val id: String,
    val theme: String,
    val lesson: String,
    val decoys: List<String>,
    val sparks: List<String>,
    val botMetaphor: String,
)

@Serializable
data class WisdomBankFile(
    val version: Int = 1,
    val count: Int = 0,
    val lessons: List<WisdomLesson>,
)

/** Public player state rendered on TV and phones. */
@Serializable
data class PlayerView(
    val id: String,
    val name: String,
    val avatar: PlayerAvatar,
    val score: Int,
    val rank: Int,
    val streak: Int,
    val bestStreak: Int,
    val isHost: Boolean,
    val isBot: Boolean,
    val connected: Boolean,
    val hasActed: Boolean = false,
    val sagePoints: Int = 0,
    val guessPoints: Int = 0,
    val applauseReceived: Int = 0,
    val correctGuesses: Int = 0,
    val totalGuesses: Int = 0,
)

/** One multiple-choice option shown during Guessing and Reveal. */
@Serializable
data class ChoiceOption(
    val id: String, // "A", "B", "C", "D", ("E")
    val text: String,
    val isCorrect: Boolean = false, // masked as false during GUESSING on wire!
    val voterIds: List<String> = emptyList(),
)

/** Per-player delta shown during Reveal. */
@Serializable
data class RoundScoreDelta(
    val playerId: String,
    val pointsEarned: Int,
    val basePoints: Int = 0,
    val speedBonus: Int = 0,
    val streakBonus: Int = 0,
    val clarityBonus: Int = 0,
    val applauseBonus: Int = 0,
    val wasCorrect: Boolean = false,
    val roleLabel: String = "",
)

/** Best round moments saved for the Podium "Hall of Metaphors" carousel. */
@Serializable
data class MetaphorHighlight(
    val sageId: String,
    val sageName: String,
    val sageAvatar: PlayerAvatar,
    val metaphor: String,
    val originalLesson: String,
    val theme: String,
    val correctGuessers: Int,
    val totalSeekers: Int,
    val applauseCount: Int,
)

/** Fun superlative award displayed on the Podium screen. */
@Serializable
data class PodiumAward(
    val title: String,
    val subtitle: String,
    val emoji: String,
    val playerId: String,
    val playerName: String,
    val avatar: PlayerAvatar,
)

/** Sound/animation one-shot cue emitted by the engine for the TV UI and AudioEngine. */
@Serializable
enum class TvCue {
    PLAYER_JOINED,
    ROUND_STARTED,
    METAPHOR_SUBMITTED,
    VOTE_LOCKED,
    COUNTDOWN_TICK,
    REVEAL_DRAMATIC,
    CORRECT_BURST,
    EVERYONE_MISSED,
    APPLAUSE_POP,
    TIE_BREAKER_START,
    PODIUM_FANFARE,
}

/** Complete immutable state snapshot rendered by the TV Compose UI. */
@Serializable
data class TvSnapshot(
    val roomCode: String,
    val phase: GamePhase,
    val config: GameConfig,
    val availableThemes: List<String>,
    val players: List<PlayerView>,
    val turnNumber: Int,
    val totalTurns: Int,
    val sageId: String?,
    val sageName: String?,
    val sageAvatar: PlayerAvatar?,
    val currentTheme: String?,
    val metaphorText: String?,
    val sageTypingChars: Int,
    val options: List<ChoiceOption>,
    val remainingMs: Long,
    val totalPhaseMs: Long,
    val correctOptionId: String?,
    val originalLesson: String?,
    val roundDeltas: List<RoundScoreDelta>,
    val applauseCount: Int,
    val statusBanner: String?,
    val tieBreakerContenderIds: List<String>,
    val tieBreakerWinnerId: String?,
    val highlights: List<MetaphorHighlight>,
    val awards: List<PodiumAward>,
    val canStart: Boolean,
    val startHint: String?,
    val cueSeq: Long,
    val lastCue: TvCue?,
)

/** Validation result for a Sage's metaphor draft. */
@Serializable
data class MetaphorValidationResult(
    val valid: Boolean,
    val reason: String? = null,
    val forbiddenWords: List<String> = emptyList(),
)

/** Personalized phone view sent over WebSocket to a single player. */
@Serializable
data class PhoneStateMessage(
    val t: String = "state",
    val roomCode: String,
    val phase: GamePhase,
    val you: PlayerView,
    val players: List<PlayerView>,
    val config: GameConfig,
    val availableThemes: List<String>,
    val turnNumber: Int,
    val totalTurns: Int,
    val isSage: Boolean,
    val sageName: String?,
    val sageAvatar: PlayerAvatar?,
    val secretLesson: String?,      // ONLY populated for the Sage during WRITING!
    val secretSparks: List<String>, // ONLY populated for the Sage during WRITING!
    val forbiddenRoots: List<String>, // ONLY populated for the Sage during WRITING for live UI hints!
    val rerollsLeft: Int,
    val metaphorText: String?,
    val currentTheme: String?,
    val options: List<ChoiceOption>,
    val myChoiceId: String?,
    val hasApplauded: Boolean,
    val applauseCount: Int,
    val remainingMs: Long,
    val totalPhaseMs: Long,
    val correctOptionId: String?,
    val originalLesson: String?,
    val myDelta: RoundScoreDelta?,
    val statusBanner: String?,
    val canStart: Boolean,
    val startHint: String?,
    val isTieBreakerContender: Boolean,
    val awards: List<PodiumAward>,
    val highlights: List<MetaphorHighlight>,
)

/** Outgoing WebSocket message directed at a specific connection id. */
data class OutgoingMessage(
    val connectionId: String,
    val payloadJson: String,
)
