package com.wordsofwisdom.core

import kotlin.random.Random

/**
 * Deterministic, pure-Kotlin state machine for Words of Wisdom (& Life Lessons).
 *
 * Single-threaded / externally serialized. All time-driven behavior reads [clock]
 * monotonically and advances inside [tick], making every transition 100% unit-testable.
 */
class WisdomGameEngine(
    private val bank: LessonBank = LessonBank.loadDefault(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val rng: Random = Random.Default,
    val roomCode: String = generateRoomCode(rng),
) {

    private class PlayerRecord(
        val id: String,
        val token: String,
        val name: String,
        val avatar: PlayerAvatar,
        val isBot: Boolean,
        val botPersona: BotPersona?,
        val joinOrder: Int,
        var connectionId: String? = null,
        var connected: Boolean = true,
        var disconnectedAtMs: Long? = null,
        var score: Int = 0,
        var streak: Int = 0,
        var bestStreak: Int = 0,
        var sagePoints: Int = 0,
        var guessPoints: Int = 0,
        var applauseReceived: Int = 0,
        var correctGuesses: Int = 0,
        var totalGuesses: Int = 0,
    )

    private data class VoteRecord(
        val optionId: String,
        val votedAtMs: Long,
        val remainingMs: Long,
    )

    private data class PhaseLens(
        val showWritingSecretsToSage: Boolean,
        val showMetaphor: Boolean,
        val showOptions: Boolean,
        val revealAnswers: Boolean,
        val showDeltas: Boolean,
        val showPodium: Boolean,
    ) {
        companion object {
            fun forPhase(phase: GamePhase): PhaseLens = when (phase) {
                GamePhase.LOBBY -> PhaseLens(false, false, false, false, false, false)
                GamePhase.WRITING -> PhaseLens(true, false, false, false, false, false)
                GamePhase.GUESSING, GamePhase.TIE_BREAKER -> PhaseLens(false, true, true, false, false, false)
                GamePhase.REVEAL -> PhaseLens(false, true, true, true, true, false)
                GamePhase.PODIUM -> PhaseLens(false, false, false, false, false, true)
            }
        }
    }

    private val players = mutableListOf<PlayerRecord>()
    private val connToPlayerId = HashMap<String, String?>()
    private val usedLessonIds = HashSet<String>()
    private val highlights = mutableListOf<MetaphorHighlight>()

    private var nextPlayerNum: Int = 1
    private var hostPlayerId: String? = null
    private var hostDisconnectedAtMs: Long? = null

    private var config: GameConfig = GameConfig()
    private var phase: GamePhase = GamePhase.LOBBY
    private var turnOrder: List<String> = emptyList()
    private var turnIndex: Int = 0 // 0-based active index into turnOrder

    // Active round state
    private var sageId: String? = null
    private var currentLesson: WisdomLesson? = null
    private var rerollsLeft: Int = 1
    private var sageTypingChars: Int = 0
    private var lastDraftText: String? = null
    private var metaphorText: String? = null
    private var options: List<ChoiceOption> = emptyList()
    private var correctOptionId: String? = null
    private val votes = LinkedHashMap<String, VoteRecord>()
    private val applaudedPlayerIds = HashSet<String>()
    private var roundDeltas: List<RoundScoreDelta> = emptyList()
    private var statusBanner: String? = null

    // Timing & automation schedules
    private var phaseStartedAtMs: Long = clock()
    private var phaseDurationMs: Long = 0L
    private var phaseDeadlineMs: Long = Long.MAX_VALUE
    private var allVotedRevealAtMs: Long = Long.MAX_VALUE
    private val botScheduledActionMs = HashMap<String, Long>()
    private var revealOutcomeCueAtMs: Long = Long.MAX_VALUE
    private var revealOutcomeCue: TvCue? = null
    private var lastCountdownTickSec: Int = -1
    private var botRevealApplauseDone: Boolean = false

    // Tie-breaker & Podium
    private var tieBreakerContenderIds: List<String> = emptyList()
    private var tieBreakerWinnerId: String? = null
    private var tieBreakerResolveAtMs: Long = Long.MAX_VALUE
    private var awards: List<PodiumAward> = emptyList()

    // One-shot TV audio/VFX cues
    private var cueSeq: Long = 0L
    private var lastCue: TvCue? = null

    // ---- Connection & Message Entry Points ----

    fun onConnect(connId: String): List<OutgoingMessage> {
        connToPlayerId[connId] = null
        return emptyList()
    }

    fun onDisconnect(connId: String): List<OutgoingMessage> {
        val playerId = connToPlayerId.remove(connId) ?: return emptyList()
        val rec = findPlayer(playerId) ?: return emptyList()
        if (rec.connectionId != connId) return emptyList()

        val now = clock()
        rec.connected = false
        rec.connectionId = null
        rec.disconnectedAtMs = now
        if (rec.id == hostPlayerId) {
            hostDisconnectedAtMs = now
        }
        return broadcastAll(now)
    }

    fun onMessage(connId: String, rawJson: String): List<OutgoingMessage> {
        val now = clock()
        if (rawJson.length > MAX_MESSAGE_CHARS) {
            return listOf(OutgoingMessage(connId, WireProtocol.encodeError("bad_message", "Message too large.")))
        }
        val msg = WireProtocol.decodeInbound(rawJson)
            ?: return listOf(OutgoingMessage(connId, WireProtocol.encodeError("bad_message", "Invalid message.")))

        return when (msg) {
            is InboundMessage.Join -> handleJoin(connId, msg, now)
            is InboundMessage.Config -> handleConfig(connId, msg, now)
            is InboundMessage.AddBot -> handleAddBot(connId, now)
            is InboundMessage.RemoveBot -> handleRemoveBot(connId, msg, now)
            is InboundMessage.Start -> handleStart(connId, now)
            is InboundMessage.Reroll -> handleReroll(connId, now)
            is InboundMessage.Typing -> handleTyping(connId, msg, now)
            is InboundMessage.SubmitMetaphor -> handleSubmitMetaphor(connId, msg, now)
            is InboundMessage.Vote -> handleVote(connId, msg, now)
            is InboundMessage.Applause -> handleApplause(connId, now)
            is InboundMessage.Next -> handleNext(connId, now)
            is InboundMessage.EndGame -> handleEndGame(connId, now)
            is InboundMessage.PlayAgain -> handlePlayAgain(connId, now)
        }
    }

    fun tick(): List<OutgoingMessage> {
        val now = clock()
        var phoneChanged = false

        // 1. Host migration after 10s disconnect
        if (checkHostMigration(now)) {
            phoneChanged = true
        }

        // 2. Remove long-disconnected players in LOBBY (after 60s)
        if (phase == GamePhase.LOBBY) {
            val removed = players.removeAll { p ->
                !p.isBot && !p.connected && p.disconnectedAtMs != null && (now - p.disconnectedAtMs!! >= LOBBY_PRUNE_MS)
            }
            if (removed) {
                ensureHostAssigned()
                phoneChanged = true
            }
        }

        // 3. Process due bot actions (and simulated bot typing in WRITING)
        if (processBots(now)) {
            phoneChanged = true
        }

        // 4. Staggered reveal audio beat (650ms after entering REVEAL)
        if (phase == GamePhase.REVEAL && now >= revealOutcomeCueAtMs && revealOutcomeCue != null) {
            emitCue(revealOutcomeCue!!)
            revealOutcomeCue = null
            revealOutcomeCueAtMs = Long.MAX_VALUE
        }

        // 5. Early lock-in pause after all connected seekers voted -> REVEAL
        if (phase == GamePhase.GUESSING && now >= allVotedRevealAtMs) {
            allVotedRevealAtMs = Long.MAX_VALUE
            enterReveal(now)
            phoneChanged = true
        }

        // 6. Tie-breaker win resolution pause -> PODIUM
        if (phase == GamePhase.TIE_BREAKER && now >= tieBreakerResolveAtMs) {
            tieBreakerResolveAtMs = Long.MAX_VALUE
            finalizeTieBreakerToPodium(now)
            phoneChanged = true
        }

        // 7. Phase deadline timeouts
        if (now >= phaseDeadlineMs) {
            onPhaseTimeout(now)
            phoneChanged = true
        }

        // 8. Final 5-second countdown tick cue (TV-only, no phone spam!)
        if ((phase == GamePhase.WRITING || phase == GamePhase.GUESSING || phase == GamePhase.TIE_BREAKER) &&
            phaseDeadlineMs != Long.MAX_VALUE
        ) {
            val remSec = ((phaseDeadlineMs - now + 999L) / 1000L).toInt()
            if (remSec in 1..5 && remSec != lastCountdownTickSec) {
                lastCountdownTickSec = remSec
                emitCue(TvCue.COUNTDOWN_TICK)
            }
        }

        return if (phoneChanged) broadcastAll(now) else emptyList()
    }

    // ---- TV Remote Controls ----

    fun remoteOk(): List<OutgoingMessage> {
        val now = clock()
        return when (phase) {
            GamePhase.LOBBY -> {
                if (!canStartNow()) return emptyList()
                startGame(now)
                broadcastAll(now)
            }
            GamePhase.REVEAL -> {
                advanceAfterReveal(now)
                broadcastAll(now)
            }
            GamePhase.PODIUM -> {
                resetToLobby(now)
                broadcastAll(now)
            }
            else -> emptyList()
        }
    }

    fun remoteAddBot(): List<OutgoingMessage> {
        if (phase != GamePhase.LOBBY || players.size >= MAX_PLAYERS) return emptyList()
        addBotPlayer()
        emitCue(TvCue.PLAYER_JOINED)
        return broadcastAll(clock())
    }

    fun remoteRemoveBot(): List<OutgoingMessage> {
        if (phase != GamePhase.LOBBY) return emptyList()
        val lastBot = players.lastOrNull { it.isBot } ?: return emptyList()
        players.remove(lastBot)
        return broadcastAll(clock())
    }

    fun remoteEndGame(): List<OutgoingMessage> {
        if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM) return emptyList()
        val now = clock()
        enterPodium(now)
        return broadcastAll(now)
    }

    // ---- Public Projections & Test Hook ----

    fun internalCorrectOptionIdForTest(): String? = correctOptionId

    fun tvSnapshot(): TvSnapshot {
        val now = clock()
        val lens = PhaseLens.forPhase(phase)
        val ranked = rankedPlayerViews()
        val sageRec = sageId?.let(::findPlayer)
        val remMs = remainingMsAt(now)

        return TvSnapshot(
            roomCode = roomCode,
            phase = phase,
            config = config,
            availableThemes = bank.themes,
            players = ranked,
            turnNumber = if (phase == GamePhase.LOBBY) 0 else (turnIndex + 1).coerceAtMost(turnOrder.size.coerceAtLeast(1)),
            totalTurns = turnOrder.size,
            sageId = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER) null else sageId,
            sageName = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER) null else sageRec?.name,
            sageAvatar = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER) null else sageRec?.avatar,
            currentTheme = if (lens.showMetaphor || lens.showWritingSecretsToSage) currentLesson?.theme else null,
            metaphorText = if (lens.showMetaphor) metaphorText else null,
            sageTypingChars = if (phase == GamePhase.WRITING) sageTypingChars else 0,
            options = projectedOptions(lens),
            remainingMs = remMs,
            totalPhaseMs = phaseDurationMs,
            correctOptionId = if (lens.revealAnswers) correctOptionId else null,
            originalLesson = if (lens.revealAnswers) currentLesson?.lesson else null,
            roundDeltas = if (lens.showDeltas) roundDeltas else emptyList(),
            applauseCount = if (phase == GamePhase.REVEAL) applaudedPlayerIds.size else 0,
            statusBanner = statusBanner,
            tieBreakerContenderIds = if (phase == GamePhase.TIE_BREAKER) tieBreakerContenderIds else emptyList(),
            tieBreakerWinnerId = tieBreakerWinnerId,
            highlights = if (lens.showPodium) highlights.toList() else emptyList(),
            awards = if (lens.showPodium) awards else emptyList(),
            canStart = canStartNow(),
            startHint = startHintText(),
            cueSeq = cueSeq,
            lastCue = lastCue,
        )
    }

    fun phoneStateFor(playerId: String): PhoneStateMessage? {
        val now = clock()
        val ranked = rankedPlayerViews()
        val you = ranked.firstOrNull { it.id == playerId } ?: return null
        return buildPhoneState(you, ranked, now)
    }

    // ---- Message Handlers ----

    private fun handleJoin(connId: String, msg: InboundMessage.Join, now: Long): List<OutgoingMessage> {
        // 1. Token-based reconnection
        val tok = msg.token?.trim()?.takeIf { it.isNotEmpty() }
        if (tok != null) {
            val existing = players.firstOrNull { !it.isBot && it.token == tok }
            if (existing != null) {
                existing.connectionId?.let { oldConn -> if (oldConn != connId) connToPlayerId.remove(oldConn) }
                existing.connectionId = connId
                existing.connected = true
                existing.disconnectedAtMs = null
                connToPlayerId[connId] = existing.id
                if (existing.id == hostPlayerId) hostDisconnectedAtMs = null

                val joined = OutgoingMessage(
                    connId,
                    WireProtocol.encodeJoined(
                        JoinedMessage(playerId = existing.id, token = existing.token, roomCode = roomCode, reconnected = true),
                    ),
                )
                return listOf(joined) + broadcastAll(now)
            } else {
                return listOf(OutgoingMessage(connId, WireProtocol.encodeError("bad_token", "Session expired. Join again.")))
            }
        }

        // 2. Fresh join
        if (players.size >= MAX_PLAYERS) {
            return listOf(OutgoingMessage(connId, WireProtocol.encodeError("full", "Parlor is full (max $MAX_PLAYERS players).")))
        }
        val cleanName = sanitizeName(msg.name)
            ?: return listOf(OutgoingMessage(connId, WireProtocol.encodeError("bad_name", "Please enter a name.")))

        val uniqueName = disambiguateName(cleanName)
        val pid = "p${nextPlayerNum++}"
        val token = generateToken(rng)
        val avatar = msg.avatarId?.let { id -> AvatarCatalog.ALL.firstOrNull { it.id == id } }
            ?: pickFreshAvatar()

        val rec = PlayerRecord(
            id = pid,
            token = token,
            name = uniqueName,
            avatar = avatar,
            isBot = false,
            botPersona = null,
            joinOrder = players.size,
            connectionId = connId,
            connected = true,
        )
        players.add(rec)
        connToPlayerId[connId] = pid
        if (hostPlayerId == null) {
            hostPlayerId = pid
            hostDisconnectedAtMs = null
        }

        emitCue(TvCue.PLAYER_JOINED)
        val joined = OutgoingMessage(
            connId,
            WireProtocol.encodeJoined(
                JoinedMessage(playerId = pid, token = token, roomCode = roomCode, reconnected = false),
            ),
        )
        return listOf(joined) + broadcastAll(now)
    }

    private fun handleConfig(connId: String, msg: InboundMessage.Config, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (player.id != hostPlayerId) {
            return listOf(OutgoingMessage(connId, WireProtocol.encodeError("not_host", "Only the host can change settings.")))
        }
        if (phase != GamePhase.LOBBY) return emptyList()

        val newWrite = msg.writeSeconds?.takeIf { it in GameConfig.VALID_WRITE_SECONDS } ?: config.writeSeconds
        val newGuess = msg.guessSeconds?.takeIf { it in GameConfig.VALID_GUESS_SECONDS } ?: config.guessSeconds
        val newReveal = msg.revealSeconds?.coerceIn(6, 30) ?: config.revealSeconds
        val newOpts = msg.optionCount?.takeIf { it in GameConfig.VALID_OPTION_COUNTS } ?: config.optionCount
        val validThemes = bank.themes.toSet()
        val newThemes = msg.selectedThemes?.filter { it in validThemes }?.distinct() ?: config.selectedThemes

        config = config.copy(
            length = msg.length ?: config.length,
            writeSeconds = newWrite,
            guessSeconds = newGuess,
            revealSeconds = newReveal,
            optionCount = newOpts,
            strictMetaphorGuard = msg.strictMetaphorGuard ?: config.strictMetaphorGuard,
            enableTieBreaker = msg.enableTieBreaker ?: config.enableTieBreaker,
            selectedThemes = newThemes,
        )
        return broadcastAll(now)
    }

    private fun handleAddBot(connId: String, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (player.id != hostPlayerId || phase != GamePhase.LOBBY) return emptyList()
        if (players.size >= MAX_PLAYERS) {
            return listOf(OutgoingMessage(connId, WireProtocol.encodeError("full", "Parlor is already at $MAX_PLAYERS players.")))
        }
        addBotPlayer()
        emitCue(TvCue.PLAYER_JOINED)
        return broadcastAll(now)
    }

    private fun handleRemoveBot(connId: String, msg: InboundMessage.RemoveBot, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (player.id != hostPlayerId || phase != GamePhase.LOBBY) return emptyList()
        val target = msg.playerId?.let { id -> players.firstOrNull { it.isBot && it.id == id } }
            ?: players.lastOrNull { it.isBot }
            ?: return emptyList()
        players.remove(target)
        return broadcastAll(now)
    }

    private fun handleStart(connId: String, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (player.id != hostPlayerId || phase != GamePhase.LOBBY) return emptyList()
        if (!canStartNow()) {
            return listOf(OutgoingMessage(connId, WireProtocol.encodeError("not_ready", startHintText() ?: "Need at least 2 players.")))
        }
        startGame(now)
        return broadcastAll(now)
    }

    private fun handleReroll(connId: String, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (phase != GamePhase.WRITING || player.id != sageId || rerollsLeft <= 0) return emptyList()

        rerollsLeft--
        val prevId = currentLesson?.id
        val exclude = if (prevId != null) usedLessonIds + prevId else usedLessonIds
        val fresh = bank.drawLesson(config.selectedThemes, exclude, rng)
        currentLesson = fresh
        usedLessonIds.add(fresh.id)
        sageTypingChars = 0
        lastDraftText = null
        statusBanner = "${player.name} swapped for a fresh life lesson!"
        return broadcastAll(now)
    }

    private fun handleTyping(connId: String, msg: InboundMessage.Typing, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (phase != GamePhase.WRITING || player.id != sageId) return emptyList()

        sageTypingChars = msg.chars.coerceIn(0, MetaphorValidator.MAX_LENGTH)
        if (msg.text != null) {
            lastDraftText = msg.text.take(MetaphorValidator.MAX_LENGTH)
        }
        // TV reads sageTypingChars directly from tvSnapshot(); avoid unnecessary phone broadcast spam.
        return emptyList()
    }

    private fun handleSubmitMetaphor(connId: String, msg: InboundMessage.SubmitMetaphor, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (phase != GamePhase.WRITING || player.id != sageId) return emptyList()

        val lesson = currentLesson ?: return emptyList()
        val check = MetaphorValidator.validate(msg.text, lesson.lesson, config.strictMetaphorGuard)
        if (!check.valid) {
            return listOf(
                OutgoingMessage(
                    connId,
                    WireProtocol.encodeMetaphorRejected(
                        MetaphorRejectedMessage(
                            reason = check.reason ?: "Try making it more metaphorical!",
                            forbiddenWords = check.forbiddenWords,
                        ),
                    ),
                ),
            )
        }

        enterGuessing(msg.text.trim(), now)
        return broadcastAll(now)
    }

    private fun handleVote(connId: String, msg: InboundMessage.Vote, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        return when (phase) {
            GamePhase.GUESSING -> recordGuessVote(player, msg.optionId, now)
            GamePhase.TIE_BREAKER -> recordTieBreakerVote(player, msg.optionId, now)
            else -> emptyList()
        }
    }

    private fun handleApplause(connId: String, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (phase != GamePhase.REVEAL || player.id == sageId) return emptyList()
        if (!applaudedPlayerIds.add(player.id)) return emptyList()

        applyApplauseToSage(player)
        emitCue(TvCue.APPLAUSE_POP)
        return broadcastAll(now)
    }

    private fun handleNext(connId: String, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (player.id != hostPlayerId || phase != GamePhase.REVEAL) return emptyList()
        advanceAfterReveal(now)
        return broadcastAll(now)
    }

    private fun handleEndGame(connId: String, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (player.id != hostPlayerId || phase == GamePhase.LOBBY || phase == GamePhase.PODIUM) return emptyList()
        enterPodium(now)
        return broadcastAll(now)
    }

    private fun handlePlayAgain(connId: String, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (player.id != hostPlayerId || phase != GamePhase.PODIUM) return emptyList()
        resetToLobby(now)
        return broadcastAll(now)
    }

    // ---- Phase Transitions ----

    private fun startGame(now: Long) {
        for (p in players) {
            p.score = 0
            p.streak = 0
            p.bestStreak = 0
            p.sagePoints = 0
            p.guessPoints = 0
            p.applauseReceived = 0
            p.correctGuesses = 0
            p.totalGuesses = 0
        }
        highlights.clear()
        awards = emptyList()
        tieBreakerContenderIds = emptyList()
        tieBreakerWinnerId = null
        tieBreakerResolveAtMs = Long.MAX_VALUE

        val cycles = when (config.length) {
            GameLength.QUICK -> 1
            GameLength.CLASSIC -> if (players.size <= 4) 2 else 1
            GameLength.MARATHON -> if (players.size <= 4) 3 else 2
        }
        val order = ArrayList<String>(players.size * cycles)
        repeat(cycles) {
            for (p in players) order.add(p.id)
        }
        turnOrder = order
        turnIndex = 0
        startTurn(now)
    }

    private fun startTurn(now: Long) {
        if (turnIndex >= turnOrder.size) {
            checkTieBreakerOrPodium(now)
            return
        }
        val nextSageId = turnOrder[turnIndex]
        val sage = findPlayer(nextSageId)
        if (sage == null) {
            turnIndex++
            startTurn(now)
            return
        }

        val lesson = bank.drawLesson(config.selectedThemes, usedLessonIds, rng)
        usedLessonIds.add(lesson.id)

        phase = GamePhase.WRITING
        sageId = sage.id
        currentLesson = lesson
        rerollsLeft = 1
        sageTypingChars = 0
        lastDraftText = null
        metaphorText = null
        options = emptyList()
        correctOptionId = null
        votes.clear()
        applaudedPlayerIds.clear()
        roundDeltas = emptyList()
        allVotedRevealAtMs = Long.MAX_VALUE
        revealOutcomeCueAtMs = Long.MAX_VALUE
        revealOutcomeCue = null
        botRevealApplauseDone = false
        lastCountdownTickSec = -1

        phaseStartedAtMs = now
        phaseDurationMs = config.writeSeconds * 1000L
        phaseDeadlineMs = now + phaseDurationMs

        botScheduledActionMs.clear()
        if (sage.isBot) {
            val persona = sage.botPersona ?: BotBrain.PERSONAS.first()
            val delay = BotBrain.planWriteDelayMs(persona, phaseDurationMs, rng)
            botScheduledActionMs[sage.id] = now + delay
        }

        statusBanner = listOf(
            "${sage.name} is translating secret wisdom into metaphor…",
            "Shhh — ${sage.name} took the Golden Quill!",
            "${sage.name} is crafting a riddle. Prepare to decode!",
        )[turnIndex.mod(3)]

        emitCue(TvCue.ROUND_STARTED)
    }

    private fun enterGuessing(submittedMetaphor: String, now: Long) {
        val lesson = currentLesson ?: return
        val builtOptions = bank.buildOptions(lesson, config.optionCount, rng)
        val correctId = builtOptions.first { it.isCorrect }.id

        phase = GamePhase.GUESSING
        metaphorText = submittedMetaphor
        options = builtOptions
        correctOptionId = correctId
        votes.clear()
        allVotedRevealAtMs = Long.MAX_VALUE
        lastCountdownTickSec = -1

        phaseStartedAtMs = now
        phaseDurationMs = config.guessSeconds * 1000L
        phaseDeadlineMs = now + phaseDurationMs

        botScheduledActionMs.clear()
        for (p in players) {
            if (p.isBot && p.id != sageId) {
                val persona = p.botPersona ?: BotBrain.PERSONAS.first()
                val delay = BotBrain.planVoteDelayMs(persona, phaseDurationMs, rng)
                botScheduledActionMs[p.id] = now + delay
            }
        }

        val sageName = sageId?.let { findPlayer(it)?.name } ?: "Sage"
        statusBanner = "Which original life lesson inspired $sageName's metaphor?"
        emitCue(TvCue.METAPHOR_SUBMITTED)
    }

    private fun recordGuessVote(player: PlayerRecord, optionId: String, now: Long): List<OutgoingMessage> {
        if (player.id == sageId) return emptyList()
        if (player.id in votes) return emptyList()
        val normalized = optionId.trim().uppercase()
        if (options.none { it.id == normalized }) return emptyList()

        votes[player.id] = VoteRecord(normalized, now, remainingMsAt(now))
        emitCue(TvCue.VOTE_LOCKED)
        checkAllSeekersVoted(now)
        return broadcastAll(now)
    }

    private fun checkAllSeekersVoted(now: Long) {
        val activeSeekers = players.filter { it.id != sageId && (it.isBot || it.connected) }
        if (activeSeekers.isNotEmpty() && activeSeekers.all { it.id in votes }) {
            if (allVotedRevealAtMs == Long.MAX_VALUE) {
                allVotedRevealAtMs = now + LOCK_IN_PAUSE_MS
                statusBanner = "Everyone locked in! Lifting the velvet curtain…"
            }
        }
    }

    private fun enterReveal(now: Long) {
        val lesson = currentLesson ?: return
        val correctId = correctOptionId ?: return
        val sage = sageId?.let(::findPlayer)

        val seekers = players.filter { it.id != sageId }
        val deltas = ArrayList<RoundScoreDelta>(players.size)
        var correctCount = 0

        for (seeker in seekers) {
            val vote = votes[seeker.id]
            seeker.totalGuesses++
            val isRight = vote != null && vote.optionId == correctId
            if (isRight) {
                correctCount++
                val res = ScoringEngine.scoreSeeker(
                    correct = true,
                    remainingMs = vote!!.remainingMs,
                    totalGuessMs = phaseDurationMs,
                    previousStreak = seeker.streak,
                )
                seeker.streak++
                if (seeker.streak > seeker.bestStreak) seeker.bestStreak = seeker.streak
                seeker.correctGuesses++
                seeker.guessPoints += res.totalPoints
                seeker.score += res.totalPoints
                deltas.add(
                    RoundScoreDelta(
                        playerId = seeker.id,
                        pointsEarned = res.totalPoints,
                        basePoints = res.basePoints,
                        speedBonus = res.speedBonus,
                        streakBonus = res.streakBonus,
                        wasCorrect = true,
                        roleLabel = "Seeker",
                    ),
                )
            } else {
                seeker.streak = 0
                deltas.add(
                    RoundScoreDelta(
                        playerId = seeker.id,
                        pointsEarned = 0,
                        wasCorrect = false,
                        roleLabel = "Seeker",
                    ),
                )
            }
        }

        if (sage != null) {
            val sageRes = ScoringEngine.scoreSage(correctCount = correctCount, seekerCount = seekers.size)
            sage.sagePoints += sageRes.totalPoints
            sage.score += sageRes.totalPoints
            deltas.add(
                RoundScoreDelta(
                    playerId = sage.id,
                    pointsEarned = sageRes.totalPoints,
                    basePoints = sageRes.basePoints,
                    clarityBonus = sageRes.clarityBonus,
                    wasCorrect = correctCount > 0,
                    roleLabel = if (sageRes.clarityBonus > 0) "Master Sage" else "Sage",
                ),
            )

            highlights.add(
                MetaphorHighlight(
                    sageId = sage.id,
                    sageName = sage.name,
                    sageAvatar = sage.avatar,
                    metaphor = metaphorText.orEmpty(),
                    originalLesson = lesson.lesson,
                    theme = lesson.theme,
                    correctGuessers = correctCount,
                    totalSeekers = seekers.size,
                    applauseCount = 0,
                ),
            )
        }

        phase = GamePhase.REVEAL
        roundDeltas = deltas
        phaseStartedAtMs = now
        phaseDurationMs = config.revealSeconds * 1000L
        phaseDeadlineMs = now + phaseDurationMs
        allVotedRevealAtMs = Long.MAX_VALUE
        botScheduledActionMs.clear()

        statusBanner = when {
            seekers.size >= 2 && correctCount == seekers.size ->
                "MASTER SAGE! Every seeker decoded ${sage?.name}'s metaphor (+30 clarity)!"
            correctCount == 0 ->
                "Total mystery! Nobody cracked ${sage?.name}'s metaphor."
            else ->
                "$correctCount of ${seekers.size} seekers decoded ${sage?.name}'s wisdom!"
        }

        emitCue(TvCue.REVEAL_DRAMATIC)
        revealOutcomeCue = if (correctCount > 0) TvCue.CORRECT_BURST else TvCue.EVERYONE_MISSED
        revealOutcomeCueAtMs = now + 650L
    }

    private fun applyApplauseToSage(applauder: PlayerRecord) {
        val sage = sageId?.let(::findPlayer) ?: return
        val beforeBonus = ScoringEngine.applauseBonus(applaudedPlayerIds.size - 1)
        val afterBonus = ScoringEngine.applauseBonus(applaudedPlayerIds.size)
        val increment = afterBonus - beforeBonus

        sage.applauseReceived++
        if (increment > 0) {
            sage.sagePoints += increment
            sage.score += increment
            roundDeltas = roundDeltas.map { d ->
                if (d.playerId == sage.id) {
                    d.copy(
                        pointsEarned = d.pointsEarned + increment,
                        applauseBonus = d.applauseBonus + increment,
                    )
                } else {
                    d
                }
            }
        }
        if (highlights.isNotEmpty() && highlights.last().sageId == sage.id) {
            val last = highlights.last()
            highlights[highlights.lastIndex] = last.copy(applauseCount = last.applauseCount + 1)
        }
    }

    private fun advanceAfterReveal(now: Long) {
        turnIndex++
        if (turnIndex < turnOrder.size) {
            startTurn(now)
        } else {
            checkTieBreakerOrPodium(now)
        }
    }

    private fun checkTieBreakerOrPodium(now: Long) {
        val topScore = players.maxOfOrNull { it.score } ?: 0
        val tiedLeaders = if (topScore > 0) players.filter { it.score == topScore } else emptyList()
        if (config.enableTieBreaker && tiedLeaders.size >= 2) {
            enterTieBreaker(tiedLeaders, now)
        } else {
            enterPodium(now)
        }
    }

    private fun enterTieBreaker(contenders: List<PlayerRecord>, now: Long) {
        val lesson = bank.drawLesson(config.selectedThemes, usedLessonIds, rng)
        usedLessonIds.add(lesson.id)
        val builtOptions = bank.buildOptions(lesson, config.optionCount, rng)
        val correctId = builtOptions.first { it.isCorrect }.id

        phase = GamePhase.TIE_BREAKER
        sageId = null
        currentLesson = lesson
        metaphorText = lesson.botMetaphor
        options = builtOptions
        correctOptionId = correctId
        votes.clear()
        tieBreakerContenderIds = contenders.map { it.id }
        tieBreakerWinnerId = null
        tieBreakerResolveAtMs = Long.MAX_VALUE
        lastCountdownTickSec = -1

        phaseStartedAtMs = now
        phaseDurationMs = TIE_BREAKER_SECONDS * 1000L
        phaseDeadlineMs = now + phaseDurationMs

        botScheduledActionMs.clear()
        for (c in contenders) {
            if (c.isBot) {
                val persona = c.botPersona ?: BotBrain.PERSONAS.first()
                botScheduledActionMs[c.id] = now + BotBrain.planVoteDelayMs(persona, phaseDurationMs, rng)
            }
        }

        statusBanner = "SUDDEN DEATH! ${contenders.joinToString(" vs ") { it.name }} — fastest right answer wins!"
        emitCue(TvCue.TIE_BREAKER_START)
    }

    private fun recordTieBreakerVote(player: PlayerRecord, optionId: String, now: Long): List<OutgoingMessage> {
        if (player.id !in tieBreakerContenderIds) return emptyList()
        if (player.id in votes || tieBreakerWinnerId != null) return emptyList()
        val normalized = optionId.trim().uppercase()
        if (options.none { it.id == normalized }) return emptyList()

        votes[player.id] = VoteRecord(normalized, now, remainingMsAt(now))
        emitCue(TvCue.VOTE_LOCKED)

        if (normalized == correctOptionId) {
            tieBreakerWinnerId = player.id
            statusBanner = "${player.name} wins Sudden Death!"
            tieBreakerResolveAtMs = now + TIE_BREAKER_PAUSE_MS
        } else {
            val allVoted = tieBreakerContenderIds.all { it in votes }
            if (allVoted) {
                tieBreakerResolveAtMs = now + TIE_BREAKER_PAUSE_MS
            }
        }
        return broadcastAll(now)
    }

    private fun finalizeTieBreakerToPodium(now: Long) {
        val winnerId = tieBreakerWinnerId ?: pickFallbackTieWinner()
        tieBreakerWinnerId = winnerId
        winnerId?.let { id ->
            findPlayer(id)?.let { w ->
                w.score += TIE_BREAKER_BONUS_POINTS
                w.guessPoints += TIE_BREAKER_BONUS_POINTS
            }
        }
        enterPodium(now)
    }

    private fun pickFallbackTieWinner(): String? {
        val contenders = tieBreakerContenderIds.mapNotNull(::findPlayer)
        return contenders.maxWithOrNull(
            compareBy<PlayerRecord> { it.correctGuesses }
                .thenBy { it.applauseReceived }
                .thenByDescending { it.joinOrder },
        )?.id
    }

    private fun enterPodium(now: Long) {
        phase = GamePhase.PODIUM
        phaseStartedAtMs = now
        phaseDurationMs = 0L
        phaseDeadlineMs = Long.MAX_VALUE
        allVotedRevealAtMs = Long.MAX_VALUE
        tieBreakerResolveAtMs = Long.MAX_VALUE
        revealOutcomeCueAtMs = Long.MAX_VALUE
        revealOutcomeCue = null
        botScheduledActionMs.clear()

        val ranked = rankedPlayerViews()
        awards = computePodiumAwards(ranked)
        // Sort highlights so the best-loved & clearest metaphors shine first in the carousel
        highlights.sortWith(
            compareByDescending<MetaphorHighlight> { it.applauseCount * 2 + it.correctGuessers }
                .thenByDescending { it.correctGuessers },
        )
        val champ = ranked.firstOrNull()
        statusBanner = champ?.let { "🏆 ${it.name} is crowned Grand Sage with ${it.score} pts!" } ?: "Grand Finale!"
        emitCue(TvCue.PODIUM_FANFARE)
    }

    private fun resetToLobby(now: Long) {
        phase = GamePhase.LOBBY
        turnOrder = emptyList()
        turnIndex = 0
        sageId = null
        currentLesson = null
        metaphorText = null
        options = emptyList()
        correctOptionId = null
        votes.clear()
        applaudedPlayerIds.clear()
        roundDeltas = emptyList()
        highlights.clear()
        awards = emptyList()
        tieBreakerContenderIds = emptyList()
        tieBreakerWinnerId = null
        statusBanner = null
        phaseStartedAtMs = now
        phaseDurationMs = 0L
        phaseDeadlineMs = Long.MAX_VALUE
        allVotedRevealAtMs = Long.MAX_VALUE
        tieBreakerResolveAtMs = Long.MAX_VALUE
        botScheduledActionMs.clear()

        for (p in players) {
            p.score = 0
            p.streak = 0
            p.bestStreak = 0
            p.sagePoints = 0
            p.guessPoints = 0
            p.applauseReceived = 0
            p.correctGuesses = 0
            p.totalGuesses = 0
        }
    }

    // ---- Timeouts & Automation ----

    private fun onPhaseTimeout(now: Long) {
        phaseDeadlineMs = Long.MAX_VALUE
        when (phase) {
            GamePhase.WRITING -> {
                val lesson = currentLesson
                val draft = lastDraftText?.trim()
                if (lesson != null && !draft.isNullOrEmpty() &&
                    MetaphorValidator.validate(draft, lesson.lesson, config.strictMetaphorGuard).valid
                ) {
                    statusBanner = "Time's up — casting the Sage's draft!"
                    enterGuessing(draft, now)
                } else {
                    val sageName = sageId?.let { findPlayer(it)?.name } ?: "The Sage"
                    turnIndex++
                    if (turnIndex < turnOrder.size) {
                        startTurn(now)
                        statusBanner = "$sageName entered deep meditation — moving to the next Sage!"
                    } else {
                        checkTieBreakerOrPodium(now)
                    }
                }
            }
            GamePhase.GUESSING -> enterReveal(now)
            GamePhase.REVEAL -> advanceAfterReveal(now)
            GamePhase.TIE_BREAKER -> finalizeTieBreakerToPodium(now)
            else -> Unit
        }
    }

    private fun processBots(now: Long): Boolean {
        var changed = false
        when (phase) {
            GamePhase.WRITING -> {
                val sid = sageId ?: return false
                val sage = findPlayer(sid) ?: return false
                if (!sage.isBot) return false
                val dueAt = botScheduledActionMs[sid] ?: return false
                val lesson = currentLesson ?: return false
                if (now >= dueAt) {
                    botScheduledActionMs.remove(sid)
                    enterGuessing(lesson.botMetaphor, now)
                    changed = true
                } else {
                    val planned = (dueAt - phaseStartedAtMs).coerceAtLeast(1L)
                    sageTypingChars = BotBrain.simulatedTypedChars(now - phaseStartedAtMs, planned, lesson.botMetaphor.length)
                }
            }
            GamePhase.GUESSING -> {
                val correctId = correctOptionId ?: return false
                val metaLen = metaphorText?.length ?: 60
                val dueBots = botScheduledActionMs.entries.filter { now >= it.value }.map { it.key }
                for (bid in dueBots) {
                    botScheduledActionMs.remove(bid)
                    val bot = findPlayer(bid) ?: continue
                    if (bot.id == sageId || bot.id in votes) continue
                    val persona = bot.botPersona ?: BotBrain.PERSONAS.first()
                    val pick = BotBrain.chooseOptionId(persona, options, correctId, metaLen, rng)
                    votes[bot.id] = VoteRecord(pick, now, remainingMsAt(now))
                    emitCue(TvCue.VOTE_LOCKED)
                    changed = true
                }
                if (changed) checkAllSeekersVoted(now)
            }
            GamePhase.TIE_BREAKER -> {
                val correctId = correctOptionId ?: return false
                val metaLen = metaphorText?.length ?: 60
                val dueBots = botScheduledActionMs.entries.filter { now >= it.value }.map { it.key }
                for (bid in dueBots) {
                    botScheduledActionMs.remove(bid)
                    if (tieBreakerWinnerId != null) break
                    val bot = findPlayer(bid) ?: continue
                    if (bot.id !in tieBreakerContenderIds || bot.id in votes) continue
                    val persona = bot.botPersona ?: BotBrain.PERSONAS.first()
                    val pick = BotBrain.chooseOptionId(persona, options, correctId, metaLen, rng)
                    votes[bot.id] = VoteRecord(pick, now, remainingMsAt(now))
                    emitCue(TvCue.VOTE_LOCKED)
                    if (pick == correctId) {
                        tieBreakerWinnerId = bot.id
                        statusBanner = "${bot.name} wins Sudden Death!"
                        tieBreakerResolveAtMs = now + TIE_BREAKER_PAUSE_MS
                    } else if (tieBreakerContenderIds.all { it in votes }) {
                        tieBreakerResolveAtMs = now + TIE_BREAKER_PAUSE_MS
                    }
                    changed = true
                }
            }
            GamePhase.REVEAL -> {
                // Friendly bots occasionally applaud a human Sage's clever metaphor!
                if (!botRevealApplauseDone && now - phaseStartedAtMs >= 1_800L) {
                    botRevealApplauseDone = true
                    val sage = sageId?.let(::findPlayer)
                    if (sage != null && !sage.isBot) {
                        val botFans = players.filter { it.isBot && it.id != sage.id && it.id !in applaudedPlayerIds }
                        for (bot in botFans) {
                            val chance = bot.botPersona?.applauseChance ?: 0.5
                            if (rng.nextDouble() < chance) {
                                applaudedPlayerIds.add(bot.id)
                                applyApplauseToSage(bot)
                                emitCue(TvCue.APPLAUSE_POP)
                                changed = true
                            }
                        }
                    }
                }
            }
            else -> Unit
        }
        return changed
    }

    private fun checkHostMigration(now: Long): Boolean {
        val discAt = hostDisconnectedAtMs ?: return false
        if (now - discAt < HOST_MIGRATION_MS) return false
        val nextHost = players.firstOrNull { !it.isBot && it.connected && it.id != hostPlayerId }
            ?: return false
        hostPlayerId = nextHost.id
        hostDisconnectedAtMs = null
        return true
    }

    private fun ensureHostAssigned() {
        if (hostPlayerId != null && players.any { it.id == hostPlayerId }) return
        hostPlayerId = players.firstOrNull { !it.isBot && it.connected }?.id
            ?: players.firstOrNull { !it.isBot }?.id
        hostDisconnectedAtMs = null
    }

    // ---- Helpers & Projections ----

    private fun addBotPlayer() {
        val existingNames = players.map { it.name }.toSet()
        val persona = BotBrain.pickPersona(existingNames, rng)
        val uniqueName = disambiguateName(persona.name)
        val pid = "bot${nextPlayerNum++}"
        players.add(
            PlayerRecord(
                id = pid,
                token = "bot-token-$pid",
                name = uniqueName,
                avatar = persona.avatar,
                isBot = true,
                botPersona = persona,
                joinOrder = players.size,
                connected = true,
            ),
        )
    }

    private fun computePodiumAwards(ranked: List<PlayerView>): List<PodiumAward> {
        if (ranked.isEmpty()) return emptyList()
        val out = mutableListOf<PodiumAward>()
        val usedIds = HashSet<String>()

        fun pick(eligible: List<PlayerView>, comp: Comparator<PlayerView>): PlayerView? {
            val fresh = eligible.filterNot { it.id in usedIds }
            return (fresh.ifEmpty { eligible }).maxWithOrNull(comp)?.also { usedIds.add(it.id) }
        }

        pick(
            ranked.filter { it.sagePoints > 0 },
            compareBy<PlayerView> { it.sagePoints }.thenBy { it.applauseReceived }.thenBy { it.score },
        )?.let { p ->
            out.add(
                PodiumAward(
                    title = "Golden Quill",
                    subtitle = "${p.sagePoints} Sage pts of vivid metaphors",
                    emoji = "🪶",
                    playerId = p.id,
                    playerName = p.name,
                    avatar = p.avatar,
                ),
            )
        }

        pick(
            ranked.filter { it.correctGuesses > 0 },
            compareBy<PlayerView> { it.correctGuesses.toDouble() / it.totalGuesses.coerceAtLeast(1) }
                .thenBy { it.correctGuesses }
                .thenBy { it.guessPoints },
        )?.let { p ->
            val pct = (p.correctGuesses * 100) / p.totalGuesses.coerceAtLeast(1)
            out.add(
                PodiumAward(
                    title = "Oracle Eye",
                    subtitle = "Decoded ${p.correctGuesses}/${p.totalGuesses} ($pct% accuracy)",
                    emoji = "🔮",
                    playerId = p.id,
                    playerName = p.name,
                    avatar = p.avatar,
                ),
            )
        }

        val applauded = ranked.filter { it.applauseReceived > 0 }
        if (applauded.isNotEmpty()) {
            pick(applauded, compareBy<PlayerView> { it.applauseReceived }.thenBy { it.sagePoints })?.let { p ->
                out.add(
                    PodiumAward(
                        title = "Crowd Favorite",
                        subtitle = "${p.applauseReceived} parlor bravos earned",
                        emoji = "👏",
                        playerId = p.id,
                        playerName = p.name,
                        avatar = p.avatar,
                    ),
                )
            }
        } else {
            pick(
                ranked.filter { it.bestStreak >= 2 },
                compareBy<PlayerView> { it.bestStreak }.thenBy { it.score },
            )?.let { p ->
                out.add(
                    PodiumAward(
                        title = "Hot Streak",
                        subtitle = "${p.bestStreak} correct guesses in a row",
                        emoji = "🔥",
                        playerId = p.id,
                        playerName = p.name,
                        avatar = p.avatar,
                    ),
                )
            }
        }

        return out
    }

    private fun rankedPlayerViews(): List<PlayerView> {
        val sorted = if (phase == GamePhase.LOBBY) {
            players.sortedBy { it.joinOrder }
        } else {
            players.sortedWith(
                compareByDescending<PlayerRecord> { it.score }
                    .thenByDescending { it.correctGuesses }
                    .thenByDescending { it.applauseReceived }
                    .thenBy { it.joinOrder },
            )
        }

        var prevScore = Int.MIN_VALUE
        var currentRank = 0
        return sorted.mapIndexed { idx, p ->
            if (phase == GamePhase.LOBBY) {
                currentRank = idx + 1
            } else if (p.score != prevScore) {
                currentRank = idx + 1
                prevScore = p.score
            }
            val acted = when (phase) {
                GamePhase.WRITING -> p.id == sageId && sageTypingChars > 0
                GamePhase.GUESSING -> p.id != sageId && p.id in votes
                GamePhase.TIE_BREAKER -> p.id in tieBreakerContenderIds && p.id in votes
                else -> false
            }
            PlayerView(
                id = p.id,
                name = p.name,
                avatar = p.avatar,
                score = p.score,
                rank = currentRank,
                streak = p.streak,
                bestStreak = p.bestStreak,
                isHost = p.id == hostPlayerId,
                isBot = p.isBot,
                connected = p.connected,
                hasActed = acted,
                sagePoints = p.sagePoints,
                guessPoints = p.guessPoints,
                applauseReceived = p.applauseReceived,
                correctGuesses = p.correctGuesses,
                totalGuesses = p.totalGuesses,
            )
        }
    }

    private fun projectedOptions(lens: PhaseLens): List<ChoiceOption> {
        if (!lens.showOptions) return emptyList()
        return options.map { opt ->
            if (lens.revealAnswers) {
                opt.copy(voterIds = votes.filterValues { it.optionId == opt.id }.keys.toList())
            } else {
                opt.copy(isCorrect = false, voterIds = emptyList())
            }
        }
    }

    private fun buildPhoneState(you: PlayerView, ranked: List<PlayerView>, now: Long): PhoneStateMessage {
        val lens = PhaseLens.forPhase(phase)
        val isSage = you.id == sageId
        val sageRec = sageId?.let(::findPlayer)
        val showSecrets = lens.showWritingSecretsToSage && isSage
        val lesson = currentLesson

        return PhoneStateMessage(
            roomCode = roomCode,
            phase = phase,
            you = you,
            players = ranked,
            config = config,
            availableThemes = bank.themes,
            turnNumber = if (phase == GamePhase.LOBBY) 0 else (turnIndex + 1).coerceAtMost(turnOrder.size.coerceAtLeast(1)),
            totalTurns = turnOrder.size,
            isSage = isSage,
            sageName = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER) null else sageRec?.name,
            sageAvatar = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER) null else sageRec?.avatar,
            secretLesson = if (showSecrets) lesson?.lesson else null,
            secretSparks = if (showSecrets) lesson?.sparks.orEmpty() else emptyList(),
            forbiddenRoots = if (showSecrets && lesson != null) bank.forbiddenHintsFor(lesson) else emptyList(),
            rerollsLeft = if (showSecrets) rerollsLeft else 0,
            metaphorText = if (lens.showMetaphor) metaphorText else null,
            currentTheme = if (lens.showMetaphor || showSecrets) lesson?.theme else null,
            options = projectedOptions(lens),
            myChoiceId = if (lens.showOptions) votes[you.id]?.optionId else null,
            hasApplauded = phase == GamePhase.REVEAL && you.id in applaudedPlayerIds,
            applauseCount = if (phase == GamePhase.REVEAL) applaudedPlayerIds.size else 0,
            remainingMs = remainingMsAt(now),
            totalPhaseMs = phaseDurationMs,
            correctOptionId = if (lens.revealAnswers) correctOptionId else null,
            originalLesson = if (lens.revealAnswers) lesson?.lesson else null,
            myDelta = if (lens.showDeltas) roundDeltas.firstOrNull { it.playerId == you.id } else null,
            statusBanner = statusBanner,
            canStart = canStartNow(),
            startHint = startHintText(),
            isTieBreakerContender = phase == GamePhase.TIE_BREAKER && you.id in tieBreakerContenderIds,
            awards = if (lens.showPodium) awards else emptyList(),
            highlights = if (lens.showPodium) highlights.toList() else emptyList(),
        )
    }

    private fun broadcastAll(now: Long): List<OutgoingMessage> {
        val ranked = rankedPlayerViews()
        val byId = ranked.associateBy { it.id }
        val out = ArrayList<OutgoingMessage>(players.size)
        for (p in players) {
            val cid = p.connectionId ?: continue
            if (!p.connected || p.isBot) continue
            val view = byId[p.id] ?: continue
            val payload = WireProtocol.encodeState(buildPhoneState(view, ranked, now))
            out.add(OutgoingMessage(cid, payload))
        }
        return out
    }

    private fun canStartNow(): Boolean =
        players.size in MIN_PLAYERS..MAX_PLAYERS && players.any { !it.isBot && it.connected }

    private fun startHintText(): String? = when {
        players.isEmpty() -> "Scan the QR code to join the parlor!"
        players.size < MIN_PLAYERS -> "Invite 1 more player — or tap + Add AI Bot to play now!"
        else -> null
    }

    private fun remainingMsAt(now: Long): Long =
        if (phaseDeadlineMs == Long.MAX_VALUE) 0L else (phaseDeadlineMs - now).coerceAtLeast(0L)

    private fun emitCue(cue: TvCue) {
        cueSeq++
        lastCue = cue
    }

    private fun findPlayer(id: String): PlayerRecord? = players.firstOrNull { it.id == id }

    private fun playerForConn(connId: String): PlayerRecord? =
        connToPlayerId[connId]?.let(::findPlayer)

    private fun sanitizeName(raw: String): String? {
        val cleaned = raw.filter { !it.isISOControl() }.trim().take(MAX_NAME_LEN)
        return cleaned.takeIf { it.isNotEmpty() }
    }

    private fun disambiguateName(base: String): String {
        val taken = players.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base $n".lowercase() in taken) n++
        return "$base $n"
    }

    private fun pickFreshAvatar(): PlayerAvatar {
        val used = players.map { it.avatar.id }.toSet()
        return AvatarCatalog.ALL.firstOrNull { it.id !in used }
            ?: AvatarCatalog.forIndex(players.size)
    }

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 10
        const val MAX_NAME_LEN = 16
        const val MAX_MESSAGE_CHARS = 2_000
        const val LOCK_IN_PAUSE_MS = 1_200L
        const val HOST_MIGRATION_MS = 10_000L
        const val LOBBY_PRUNE_MS = 60_000L
        const val TIE_BREAKER_SECONDS = 25
        const val TIE_BREAKER_PAUSE_MS = 1_500L
        const val TIE_BREAKER_BONUS_POINTS = 10

        private val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"

        fun generateRoomCode(rng: Random = Random.Default): String =
            (1..4).map { CODE_ALPHABET[rng.nextInt(CODE_ALPHABET.length)] }.joinToString("")

        private fun generateToken(rng: Random): String {
            val chars = "0123456789abcdef"
            return (1..24).map { chars[rng.nextInt(chars.length)] }.joinToString("")
        }
    }
}
