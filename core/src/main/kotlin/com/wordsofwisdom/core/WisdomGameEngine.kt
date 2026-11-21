package com.wordsofwisdom.core

import kotlin.random.Random

/**
 * Deterministic, pure-Kotlin state machine for Words of Wisdom: Life Lessons (v1.1.0).
 *
 * Supports both Simultaneous "Party Sprint" writing (zero couch downtime) and Classic
 * Turn-by-Turn mode, Sage decoy preview, Golden Owl 2x Double-Down bets, 2x Final Cycle,
 * Wildcard Metaphor Worlds (+40), Poet's Star applause (+25), live emoji reactions,
 * Practice AI Bots, mid-game late joiners, token reconnect, host migration, and Sudden-Death Tie-Breakers.
 */
class WisdomGameEngine(
    private val bank: LessonBank = LessonBank.loadDefault(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val rng: Random = Random.Default,
    val roomCode: String = generateRoomCode(rng),
    initialConfig: GameConfig = GameConfig(),
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
        var doubleDownAvailable: Boolean = true,
        var lastReactionAtMs: Long = Long.MIN_VALUE,
        var score: Int = 0,
        var streak: Int = 0,
        var bestStreak: Int = 0,
        var sagePoints: Int = 0,
        var guessPoints: Int = 0,
        var applauseReceived: Int = 0,
        var correctGuesses: Int = 0,
        var totalGuesses: Int = 0,
    )

    private class PendingScroll(
        val sageId: String,
        var lesson: WisdomLesson,
        var prebuiltOptions: List<ChoiceOption>,
        var decoyPreview: List<String>,
        val offeredWildcard: WildcardWorld,
        var rerollsLeft: Int = 1,
        var typingChars: Int = 0,
        var lastDraft: String? = null,
        var submittedMetaphor: String? = null,
        var wildcardAccepted: Boolean = false,
    )

    private data class SubmittedShowcase(
        val sageId: String,
        val lesson: WisdomLesson,
        val options: List<ChoiceOption>,
        val metaphor: String,
        val activeWildcard: WildcardWorld?,
    )

    private data class VoteRecord(
        val optionId: String,
        val votedAtMs: Long,
        val remainingMs: Long,
        val doubleDown: Boolean,
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
    private val recentReactions = ArrayDeque<LiveReaction>()

    private var nextPlayerNum: Int = 1
    private var nextReactionSeq: Long = 1L
    private var hostPlayerId: String? = null
    private var hostDisconnectedAtMs: Long? = null

    private var config: GameConfig = initialConfig
    private var phase: GamePhase = GamePhase.LOBBY

    // Cycle & Turn Scheduling
    private var totalCycles: Int = 1
    private var currentCycle: Int = 1 // 1-based
    private var totalTurns: Int = 0
    private var completedShowcases: Int = 0

    // Classic Turn-by-Turn schedule
    private var classicTurnOrder: List<String> = emptyList()
    private var classicTurnIndex: Int = 0

    // Unified Writing / Sprint state (1 entry in CLASSIC_TURNS, N entries in PARTY_SPRINT)
    private val pendingScrolls = LinkedHashMap<String, PendingScroll>()
    private val showcaseQueue = ArrayDeque<SubmittedShowcase>()

    // Active showcase / turn state
    private var sageId: String? = null
    private var currentLesson: WisdomLesson? = null
    private var activeWildcard: WildcardWorld? = null
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
            is InboundMessage.React -> handleReact(connId, msg, now)
            is InboundMessage.Next -> handleNext(connId, now)
            is InboundMessage.EndGame -> handleEndGame(connId, now)
            is InboundMessage.PlayAgain -> handlePlayAgain(connId, now)
        }
    }

    fun tick(): List<OutgoingMessage> {
        val now = clock()
        var phoneChanged = false

        if (checkHostMigration(now)) phoneChanged = true

        if (phase == GamePhase.LOBBY) {
            val removed = players.removeAll { p ->
                !p.isBot && !p.connected && p.disconnectedAtMs != null && (now - p.disconnectedAtMs!! >= LOBBY_PRUNE_MS)
            }
            if (removed) {
                ensureHostAssigned()
                phoneChanged = true
            }
        }

        if (processBots(now)) phoneChanged = true

        if (phase == GamePhase.REVEAL && now >= revealOutcomeCueAtMs && revealOutcomeCue != null) {
            emitCue(revealOutcomeCue!!)
            revealOutcomeCue = null
            revealOutcomeCueAtMs = Long.MAX_VALUE
        }

        if (phase == GamePhase.GUESSING && now >= allVotedRevealAtMs) {
            allVotedRevealAtMs = Long.MAX_VALUE
            enterReveal(now)
            phoneChanged = true
        }

        if (phase == GamePhase.TIE_BREAKER && now >= tieBreakerResolveAtMs) {
            tieBreakerResolveAtMs = Long.MAX_VALUE
            finalizeTieBreakerToPodium(now)
            phoneChanged = true
        }

        if (now >= phaseDeadlineMs) {
            onPhaseTimeout(now)
            phoneChanged = true
        }

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

    fun remoteToggleMode(): List<OutgoingMessage> {
        if (phase != GamePhase.LOBBY) return emptyList()
        val next = if (config.mode == GameMode.PARTY_SPRINT) GameMode.CLASSIC_TURNS else GameMode.PARTY_SPRINT
        config = config.copy(mode = next)
        return broadcastAll(clock())
    }

    fun remoteCyclePack(): List<OutgoingMessage> {
        if (phase != GamePhase.LOBBY) return emptyList()
        val packs = PromptPack.entries
        val next = packs[(packs.indexOf(config.pack) + 1) % packs.size]
        config = config.copy(pack = next)
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

    private fun currentRoundMultiplier(): Int =
        if (config.enableFinalDoublePoints && totalCycles >= 2 && currentCycle == totalCycles) 2 else 1

    private fun isSimultaneousWritingNow(): Boolean =
        phase == GamePhase.WRITING && config.mode == GameMode.PARTY_SPRINT

    fun tvSnapshot(): TvSnapshot {
        val now = clock()
        val lens = PhaseLens.forPhase(phase)
        val ranked = rankedPlayerViews()
        val isSprintWriting = isSimultaneousWritingNow()
        val sageRec = if (isSprintWriting) null else sageId?.let(::findPlayer)
        val remMs = remainingMsAt(now)
        val sprintStatuses = if (isSprintWriting) {
            pendingScrolls.values.mapNotNull { scroll ->
                val p = findPlayer(scroll.sageId) ?: return@mapNotNull null
                SprintWriterStatus(
                    playerId = p.id,
                    name = p.name,
                    avatar = p.avatar,
                    typingChars = scroll.typingChars,
                    submitted = scroll.submittedMetaphor != null,
                    wildcardTitle = if (scroll.wildcardAccepted) "${scroll.offeredWildcard.emoji} ${scroll.offeredWildcard.title}" else null,
                )
            }
        } else {
            emptyList()
        }

        val activeScrollTyping = if (phase == GamePhase.WRITING && !isSprintWriting) {
            sageId?.let { pendingScrolls[it]?.typingChars } ?: 0
        } else {
            0
        }

        val displayTurn = when {
            phase == GamePhase.LOBBY -> 0
            isSprintWriting -> (completedShowcases + 1).coerceAtMost(totalTurns.coerceAtLeast(1))
            else -> (completedShowcases + 1).coerceAtMost(totalTurns.coerceAtLeast(1))
        }

        return TvSnapshot(
            roomCode = roomCode,
            phase = phase,
            config = config,
            availableThemes = bank.themes,
            players = ranked,
            turnNumber = displayTurn,
            totalTurns = totalTurns,
            cycleNumber = currentCycle,
            totalCycles = totalCycles,
            roundMultiplier = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM) 1 else currentRoundMultiplier(),
            isSimultaneousWriting = isSprintWriting,
            sprintWriters = sprintStatuses,
            sageId = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER || isSprintWriting) null else sageId,
            sageName = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER || isSprintWriting) null else sageRec?.name,
            sageAvatar = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER || isSprintWriting) null else sageRec?.avatar,
            currentTheme = if (isSprintWriting) null else if (lens.showMetaphor || lens.showWritingSecretsToSage) currentLesson?.theme else null,
            activeWildcard = if (lens.showMetaphor) activeWildcard else null,
            metaphorText = if (lens.showMetaphor) metaphorText else null,
            sageTypingChars = activeScrollTyping,
            options = projectedOptions(lens),
            remainingMs = remMs,
            totalPhaseMs = phaseDurationMs,
            correctOptionId = if (lens.revealAnswers) correctOptionId else null,
            originalLesson = if (lens.revealAnswers) currentLesson?.lesson else null,
            roundDeltas = if (lens.showDeltas) roundDeltas else emptyList(),
            applauseCount = if (phase == GamePhase.REVEAL) applaudedPlayerIds.size else 0,
            recentReactions = recentReactions.toList(),
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

        if (players.size >= MAX_PLAYERS) {
            return listOf(OutgoingMessage(connId, WireProtocol.encodeError("full", "Parlor is full (max $MAX_PLAYERS players).")))
        }
        val cleanName = sanitizeName(msg.name)
            ?: return listOf(OutgoingMessage(connId, WireProtocol.encodeError("bad_name", "Please enter a name.")))

        // Smart seat recovery: if a player closed their browser (losing localStorage or using a
        // fresh tab) and rejoins with the exact same name as a disconnected player, restore their seat!
        val disconnectedSameName = players.firstOrNull {
            !it.isBot && !it.connected && it.name.equals(cleanName, ignoreCase = true)
        }
        if (disconnectedSameName != null) {
            disconnectedSameName.connectionId?.let { oldConn -> if (oldConn != connId) connToPlayerId.remove(oldConn) }
            disconnectedSameName.connectionId = connId
            disconnectedSameName.connected = true
            disconnectedSameName.disconnectedAtMs = null
            connToPlayerId[connId] = disconnectedSameName.id
            if (disconnectedSameName.id == hostPlayerId) hostDisconnectedAtMs = null

            val rejoined = OutgoingMessage(
                connId,
                WireProtocol.encodeJoined(
                    JoinedMessage(
                        playerId = disconnectedSameName.id,
                        token = disconnectedSameName.token,
                        roomCode = roomCode,
                        reconnected = true,
                    ),
                ),
            )
            return listOf(rejoined) + broadcastAll(now)
        }

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
            doubleDownAvailable = true,
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
            mode = msg.mode ?: config.mode,
            pack = msg.pack ?: config.pack,
            length = msg.length ?: config.length,
            writeSeconds = newWrite,
            guessSeconds = newGuess,
            revealSeconds = newReveal,
            optionCount = newOpts,
            strictMetaphorGuard = msg.strictMetaphorGuard ?: config.strictMetaphorGuard,
            enableTieBreaker = msg.enableTieBreaker ?: config.enableTieBreaker,
            enableFinalDoublePoints = msg.enableFinalDoublePoints ?: config.enableFinalDoublePoints,
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
        if (phase != GamePhase.WRITING) return emptyList()
        val scroll = pendingScrolls[player.id] ?: return emptyList()
        if (scroll.submittedMetaphor != null || scroll.rerollsLeft <= 0) return emptyList()

        scroll.rerollsLeft--
        val prevId = scroll.lesson.id
        val fresh = bank.drawLesson(config.selectedThemes, usedLessonIds + prevId, rng, config.pack)
        usedLessonIds.add(fresh.id)
        val builtOpts = bank.buildOptions(fresh, config.optionCount, rng)
        scroll.lesson = fresh
        scroll.prebuiltOptions = builtOpts
        scroll.decoyPreview = builtOpts.filterNot { it.isCorrect }.take(2).map { it.text }
        scroll.typingChars = 0
        scroll.lastDraft = null

        if (!isSimultaneousWritingNow()) {
            currentLesson = fresh
            statusBanner = "${player.name} swapped for a fresh life lesson!"
        }
        return broadcastAll(now)
    }

    private fun handleTyping(connId: String, msg: InboundMessage.Typing, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (phase != GamePhase.WRITING) return emptyList()
        val scroll = pendingScrolls[player.id] ?: return emptyList()
        if (scroll.submittedMetaphor != null) return emptyList()

        scroll.typingChars = msg.chars.coerceIn(0, MetaphorValidator.MAX_LENGTH)
        if (msg.text != null) {
            scroll.lastDraft = msg.text.take(MetaphorValidator.MAX_LENGTH)
        }
        return emptyList()
    }

    private fun handleSubmitMetaphor(connId: String, msg: InboundMessage.SubmitMetaphor, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (phase != GamePhase.WRITING) return emptyList()
        val scroll = pendingScrolls[player.id] ?: return emptyList()
        if (scroll.submittedMetaphor != null) return emptyList()

        val check = MetaphorValidator.validate(msg.text, scroll.lesson.lesson, config.strictMetaphorGuard)
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

        scroll.submittedMetaphor = msg.text.trim()
        scroll.wildcardAccepted = msg.wildcardAccepted
        scroll.typingChars = scroll.submittedMetaphor!!.length

        if (isSimultaneousWritingNow()) {
            emitCue(TvCue.VOTE_LOCKED)
            checkAllSprintWritersDone(now)
        } else {
            val showcase = SubmittedShowcase(
                sageId = scroll.sageId,
                lesson = scroll.lesson,
                options = scroll.prebuiltOptions,
                metaphor = scroll.submittedMetaphor!!,
                activeWildcard = if (scroll.wildcardAccepted) scroll.offeredWildcard else null,
            )
            enterGuessingShowcase(showcase, now)
        }
        return broadcastAll(now)
    }

    private fun handleVote(connId: String, msg: InboundMessage.Vote, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        return when (phase) {
            GamePhase.GUESSING -> recordGuessVote(player, msg.optionId, msg.doubleDown, now)
            GamePhase.TIE_BREAKER -> recordTieBreakerVote(player, msg.optionId, now)
            else -> emptyList()
        }
    }

    private fun handleApplause(connId: String, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        if (phase != GamePhase.REVEAL || player.id == sageId) return emptyList()
        if (!applaudedPlayerIds.add(player.id)) return emptyList()

        applyPoetStarToSage()
        emitCue(TvCue.APPLAUSE_POP)
        return broadcastAll(now)
    }

    private fun handleReact(connId: String, msg: InboundMessage.React, now: Long): List<OutgoingMessage> {
        val player = playerForConn(connId) ?: return emptyList()
        val emoji = msg.emoji.trim()
        if (emoji !in ALLOWED_REACTIONS) return emptyList()
        if (player.lastReactionAtMs != Long.MIN_VALUE && now - player.lastReactionAtMs < REACTION_COOLDOWN_MS) return emptyList()
        player.lastReactionAtMs = now

        recentReactions.addLast(
            LiveReaction(
                seq = nextReactionSeq++,
                playerId = player.id,
                senderName = player.name,
                emoji = emoji,
            ),
        )
        while (recentReactions.size > MAX_RECENT_REACTIONS) {
            recentReactions.removeFirst()
        }
        emitCue(TvCue.REACTION_POP)
        return emptyList() // TV-only floating reaction; zero phone broadcast churn
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
            p.doubleDownAvailable = true
        }
        highlights.clear()
        awards = emptyList()
        recentReactions.clear()
        tieBreakerContenderIds = emptyList()
        tieBreakerWinnerId = null
        tieBreakerResolveAtMs = Long.MAX_VALUE
        showcaseQueue.clear()
        pendingScrolls.clear()

        totalCycles = when (config.length) {
            GameLength.QUICK -> 1
            GameLength.CLASSIC -> if (players.size <= 4) 2 else 1
            GameLength.MARATHON -> if (players.size <= 4) 3 else 2
        }
        currentCycle = 1
        completedShowcases = 0
        totalTurns = players.size * totalCycles

        if (config.mode == GameMode.PARTY_SPRINT) {
            startSprintWritingCycle(now)
        } else {
            val order = ArrayList<String>(totalTurns)
            repeat(totalCycles) {
                for (p in players) order.add(p.id)
            }
            classicTurnOrder = order
            classicTurnIndex = 0
            startClassicTurn(now)
        }
    }

    private fun createPendingScroll(sageId: String): PendingScroll {
        val lesson = bank.drawLesson(config.selectedThemes, usedLessonIds, rng, config.pack)
        usedLessonIds.add(lesson.id)
        val builtOptions = bank.buildOptions(lesson, config.optionCount, rng)
        val preview = builtOptions.filterNot { it.isCorrect }.take(2).map { it.text }
        val wildcard = WildcardCatalog.ALL[rng.nextInt(WildcardCatalog.ALL.size)]
        return PendingScroll(
            sageId = sageId,
            lesson = lesson,
            prebuiltOptions = builtOptions,
            decoyPreview = preview,
            offeredWildcard = wildcard,
        )
    }

    private fun startSprintWritingCycle(now: Long) {
        phase = GamePhase.WRITING
        pendingScrolls.clear()
        showcaseQueue.clear()
        sageId = null
        currentLesson = null
        activeWildcard = null
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

        val activeWriters = players.filter { it.isBot || it.connected }
        for (p in activeWriters) {
            pendingScrolls[p.id] = createPendingScroll(p.id)
            if (p.isBot) {
                val persona = p.botPersona ?: BotBrain.PERSONAS.first()
                botScheduledActionMs[p.id] = now + BotBrain.planWriteDelayMs(persona, phaseDurationMs, rng)
            }
        }

        val multTag = if (currentRoundMultiplier() == 2) "🔥 FINAL ROUND (2× POINTS)! " else ""
        statusBanner = "${multTag}Everyone grab your phone — craft your metaphor at once!"
        emitCue(TvCue.ROUND_STARTED)
    }

    private fun checkAllSprintWritersDone(now: Long) {
        val expected = pendingScrolls.values.filter { sc ->
            val p = findPlayer(sc.sageId)
            p != null && (p.isBot || p.connected)
        }
        if (expected.isNotEmpty() && expected.all { it.submittedMetaphor != null }) {
            beginSprintShowcases(now)
        }
    }

    private fun beginSprintShowcases(now: Long) {
        showcaseQueue.clear()
        for (sc in pendingScrolls.values) {
            val metaphor = sc.submittedMetaphor ?: sc.lastDraft?.trim()?.takeIf {
                MetaphorValidator.validate(it, sc.lesson.lesson, config.strictMetaphorGuard).valid
            }
            if (metaphor != null) {
                showcaseQueue.addLast(
                    SubmittedShowcase(
                        sageId = sc.sageId,
                        lesson = sc.lesson,
                        options = sc.prebuiltOptions,
                        metaphor = metaphor,
                        activeWildcard = if (sc.wildcardAccepted) sc.offeredWildcard else null,
                    ),
                )
            }
        }
        pendingScrolls.clear()
        if (showcaseQueue.isEmpty()) {
            advanceCycleOrFinish(now)
        } else {
            totalTurns = completedShowcases + showcaseQueue.size + ((totalCycles - currentCycle) * players.size)
            enterGuessingShowcase(showcaseQueue.removeFirst(), now)
        }
    }

    private fun startClassicTurn(now: Long) {
        if (classicTurnIndex >= classicTurnOrder.size) {
            checkTieBreakerOrPodium(now)
            return
        }
        currentCycle = (classicTurnIndex / players.size.coerceAtLeast(1)) + 1
        val nextSageId = classicTurnOrder[classicTurnIndex]
        val sage = findPlayer(nextSageId)
        if (sage == null) {
            classicTurnIndex++
            startClassicTurn(now)
            return
        }

        val scroll = createPendingScroll(sage.id)
        pendingScrolls.clear()
        pendingScrolls[sage.id] = scroll

        phase = GamePhase.WRITING
        sageId = sage.id
        currentLesson = scroll.lesson
        activeWildcard = null
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
            botScheduledActionMs[sage.id] = now + BotBrain.planWriteDelayMs(persona, phaseDurationMs, rng)
        }

        val multTag = if (currentRoundMultiplier() == 2) "🔥 2× FINAL ROUND · " else ""
        statusBanner = multTag + listOf(
            "${sage.name} is translating secret wisdom into metaphor…",
            "Shhh — ${sage.name} took the Golden Quill!",
            "${sage.name} is crafting a riddle. Prepare to decode!",
        )[classicTurnIndex.mod(3)]

        emitCue(TvCue.ROUND_STARTED)
    }

    private fun enterGuessingShowcase(showcase: SubmittedShowcase, now: Long) {
        val correctId = showcase.options.first { it.isCorrect }.id

        phase = GamePhase.GUESSING
        sageId = showcase.sageId
        currentLesson = showcase.lesson
        activeWildcard = showcase.activeWildcard
        metaphorText = showcase.metaphor
        options = showcase.options
        correctOptionId = correctId
        votes.clear()
        applaudedPlayerIds.clear()
        roundDeltas = emptyList()
        allVotedRevealAtMs = Long.MAX_VALUE
        lastCountdownTickSec = -1

        phaseStartedAtMs = now
        phaseDurationMs = config.guessSeconds * 1000L
        phaseDeadlineMs = now + phaseDurationMs

        botScheduledActionMs.clear()
        for (p in players) {
            if (p.isBot && p.id != sageId) {
                val persona = p.botPersona ?: BotBrain.PERSONAS.first()
                botScheduledActionMs[p.id] = now + BotBrain.planVoteDelayMs(persona, phaseDurationMs, rng)
            }
        }

        val sageName = findPlayer(showcase.sageId)?.name ?: "Sage"
        val multTag = if (currentRoundMultiplier() == 2) "🔥 2× POINTS · " else ""
        statusBanner = "${multTag}Which original life lesson inspired $sageName's metaphor?"
        emitCue(TvCue.METAPHOR_SUBMITTED)
    }

    private fun recordGuessVote(
        player: PlayerRecord,
        optionId: String,
        doubleDown: Boolean,
        now: Long,
    ): List<OutgoingMessage> {
        if (player.id == sageId) return emptyList()
        if (player.id in votes) return emptyList()
        val normalized = optionId.trim().uppercase()
        if (options.none { it.id == normalized }) return emptyList()

        val useDouble = doubleDown && player.doubleDownAvailable
        votes[player.id] = VoteRecord(normalized, now, remainingMsAt(now), useDouble)
        emitCue(if (useDouble) TvCue.DOUBLE_DOWN_LOCKED else TvCue.VOTE_LOCKED)
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
        val mult = currentRoundMultiplier()

        val seekers = players.filter { it.id != sageId }
        val deltas = ArrayList<RoundScoreDelta>(players.size)
        var correctCount = 0

        for (seeker in seekers) {
            val vote = votes[seeker.id]
            seeker.totalGuesses++
            val usedDouble = vote?.doubleDown == true
            if (usedDouble) seeker.doubleDownAvailable = false

            val isRight = vote != null && vote.optionId == correctId
            if (isRight) {
                correctCount++
                val res = ScoringEngine.scoreSeeker(
                    correct = true,
                    remainingMs = vote!!.remainingMs,
                    totalGuessMs = phaseDurationMs,
                    previousStreak = seeker.streak,
                )
                val finalMult = mult * (if (usedDouble) 2 else 1)
                val earned = res.totalPoints * finalMult
                seeker.streak++
                if (seeker.streak > seeker.bestStreak) seeker.bestStreak = seeker.streak
                seeker.correctGuesses++
                seeker.guessPoints += earned
                seeker.score += earned
                deltas.add(
                    RoundScoreDelta(
                        playerId = seeker.id,
                        pointsEarned = earned,
                        basePoints = res.basePoints,
                        speedBonus = res.speedBonus,
                        streakBonus = res.streakBonus,
                        usedDoubleDown = usedDouble,
                        multiplier = mult,
                        wasCorrect = true,
                        roleLabel = if (usedDouble) "🦉 2× Seeker" else "Seeker",
                    ),
                )
            } else {
                seeker.streak = 0
                val deltaPts = if (usedDouble) ScoringEngine.DOUBLE_DOWN_PENALTY else 0
                if (deltaPts != 0) {
                    seeker.score = (seeker.score + deltaPts).coerceAtLeast(0)
                }
                deltas.add(
                    RoundScoreDelta(
                        playerId = seeker.id,
                        pointsEarned = deltaPts,
                        usedDoubleDown = usedDouble,
                        multiplier = mult,
                        wasCorrect = false,
                        roleLabel = if (usedDouble) "🦉 Bust" else "Seeker",
                    ),
                )
            }
        }

        if (sage != null) {
            val sageRes = ScoringEngine.scoreSage(correctCount = correctCount, seekerCount = seekers.size)
            val wcBonus = ScoringEngine.wildcardStyleBonus(activeWildcard != null, correctCount)
            val earned = (sageRes.totalPoints + wcBonus) * mult
            sage.sagePoints += earned
            sage.score += earned
            deltas.add(
                RoundScoreDelta(
                    playerId = sage.id,
                    pointsEarned = earned,
                    basePoints = sageRes.basePoints,
                    clarityBonus = sageRes.clarityBonus,
                    wildcardBonus = wcBonus,
                    multiplier = mult,
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
                    wildcardTitle = activeWildcard?.let { "${it.emoji} ${it.title}" },
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
        revealOutcomeCueAtMs = now + 1_800L // Matches Beat 2 illumination on TV!
    }

    private fun applyPoetStarToSage() {
        val sage = sageId?.let(::findPlayer) ?: return
        val beforeBonus = ScoringEngine.poetStarBonus(applaudedPlayerIds.size - 1)
        val afterBonus = ScoringEngine.poetStarBonus(applaudedPlayerIds.size)
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
        completedShowcases++
        if (config.mode == GameMode.PARTY_SPRINT) {
            if (showcaseQueue.isNotEmpty()) {
                enterGuessingShowcase(showcaseQueue.removeFirst(), now)
            } else {
                advanceCycleOrFinish(now)
            }
        } else {
            classicTurnIndex++
            if (classicTurnIndex < classicTurnOrder.size) {
                startClassicTurn(now)
            } else {
                checkTieBreakerOrPodium(now)
            }
        }
    }

    private fun advanceCycleOrFinish(now: Long) {
        if (currentCycle < totalCycles) {
            currentCycle++
            startSprintWritingCycle(now)
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
        val lesson = bank.drawLesson(config.selectedThemes, usedLessonIds, rng, config.pack)
        usedLessonIds.add(lesson.id)
        val builtOptions = bank.buildOptions(lesson, config.optionCount, rng)
        val correctId = builtOptions.first { it.isCorrect }.id

        phase = GamePhase.TIE_BREAKER
        sageId = null
        currentLesson = lesson
        activeWildcard = null
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

        votes[player.id] = VoteRecord(normalized, now, remainingMsAt(now), doubleDown = false)
        emitCue(TvCue.VOTE_LOCKED)

        if (normalized == correctOptionId) {
            tieBreakerWinnerId = player.id
            statusBanner = "${player.name} wins Sudden Death!"
            tieBreakerResolveAtMs = now + TIE_BREAKER_PAUSE_MS
        } else if (tieBreakerContenderIds.all { it in votes }) {
            tieBreakerResolveAtMs = now + TIE_BREAKER_PAUSE_MS
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
        classicTurnOrder = emptyList()
        classicTurnIndex = 0
        completedShowcases = 0
        totalTurns = 0
        currentCycle = 1
        totalCycles = 1
        pendingScrolls.clear()
        showcaseQueue.clear()
        sageId = null
        currentLesson = null
        activeWildcard = null
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
            p.doubleDownAvailable = true
        }
    }

    // ---- Timeouts & Automation ----

    private fun onPhaseTimeout(now: Long) {
        phaseDeadlineMs = Long.MAX_VALUE
        when (phase) {
            GamePhase.WRITING -> {
                if (isSimultaneousWritingNow()) {
                    beginSprintShowcases(now)
                } else {
                    val scroll = sageId?.let { pendingScrolls[it] }
                    val draft = scroll?.lastDraft?.trim()
                    if (scroll != null && !draft.isNullOrEmpty() &&
                        MetaphorValidator.validate(draft, scroll.lesson.lesson, config.strictMetaphorGuard).valid
                    ) {
                        statusBanner = "Time's up — casting the Sage's draft!"
                        enterGuessingShowcase(
                            SubmittedShowcase(
                                sageId = scroll.sageId,
                                lesson = scroll.lesson,
                                options = scroll.prebuiltOptions,
                                metaphor = draft,
                                activeWildcard = if (scroll.wildcardAccepted) scroll.offeredWildcard else null,
                            ),
                            now,
                        )
                    } else {
                        val sageName = sageId?.let { findPlayer(it)?.name } ?: "The Sage"
                        classicTurnIndex++
                        completedShowcases++
                        if (classicTurnIndex < classicTurnOrder.size) {
                            startClassicTurn(now)
                            statusBanner = "$sageName entered deep meditation — moving to the next Sage!"
                        } else {
                            checkTieBreakerOrPodium(now)
                        }
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
                val dueBots = botScheduledActionMs.entries.filter { now >= it.value }.map { it.key }
                for (bid in dueBots) {
                    botScheduledActionMs.remove(bid)
                    val scroll = pendingScrolls[bid] ?: continue
                    if (scroll.submittedMetaphor != null) continue
                    scroll.submittedMetaphor = scroll.lesson.botMetaphor
                    scroll.typingChars = scroll.lesson.botMetaphor.length
                    changed = true
                    if (!isSimultaneousWritingNow()) {
                        enterGuessingShowcase(
                            SubmittedShowcase(
                                sageId = scroll.sageId,
                                lesson = scroll.lesson,
                                options = scroll.prebuiltOptions,
                                metaphor = scroll.lesson.botMetaphor,
                                activeWildcard = null,
                            ),
                            now,
                        )
                        return true
                    }
                }
                // Update simulated typing chars for bots still writing
                for ((bid, dueAt) in botScheduledActionMs) {
                    val scroll = pendingScrolls[bid] ?: continue
                    val planned = (dueAt - phaseStartedAtMs).coerceAtLeast(1L)
                    scroll.typingChars = BotBrain.simulatedTypedChars(now - phaseStartedAtMs, planned, scroll.lesson.botMetaphor.length)
                }
                if (changed && isSimultaneousWritingNow()) {
                    checkAllSprintWritersDone(now)
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
                    votes[bot.id] = VoteRecord(pick, now, remainingMsAt(now), doubleDown = false)
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
                    votes[bot.id] = VoteRecord(pick, now, remainingMsAt(now), doubleDown = false)
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
                if (!botRevealApplauseDone && now - phaseStartedAtMs >= 2_400L) {
                    botRevealApplauseDone = true
                    val sage = sageId?.let(::findPlayer)
                    if (sage != null && !sage.isBot) {
                        val botFans = players.filter { it.isBot && it.id != sage.id && it.id !in applaudedPlayerIds }
                        for (bot in botFans) {
                            val chance = bot.botPersona?.applauseChance ?: 0.5
                            if (rng.nextDouble() < chance) {
                                applaudedPlayerIds.add(bot.id)
                                applyPoetStarToSage()
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
                        subtitle = "${p.applauseReceived} Poet's Stars earned",
                        emoji = "⭐",
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
                GamePhase.WRITING -> {
                    val sc = pendingScrolls[p.id]
                    if (isSimultaneousWritingNow()) sc?.submittedMetaphor != null else (sc != null && sc.typingChars > 0)
                }
                GamePhase.GUESSING -> p.id != sageId && p.id in votes
                GamePhase.TIE_BREAKER -> p.id in tieBreakerContenderIds && p.id in votes
                else -> false
            }
            val doubledNow = (phase == GamePhase.GUESSING || phase == GamePhase.REVEAL) && votes[p.id]?.doubleDown == true
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
                doubleDownAvailable = p.doubleDownAvailable,
                doubledDownThisTurn = doubledNow,
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
        val isSprintWriting = isSimultaneousWritingNow()
        val myScroll = if (phase == GamePhase.WRITING) pendingScrolls[you.id] else null
        val isSage = if (isSprintWriting) myScroll != null else (you.id == sageId)
        val sageRec = if (isSprintWriting) null else sageId?.let(::findPlayer)
        val showSecrets = lens.showWritingSecretsToSage && myScroll != null && myScroll.submittedMetaphor == null
        val lesson = if (phase == GamePhase.WRITING) myScroll?.lesson else currentLesson
        val myVote = votes[you.id]

        return PhoneStateMessage(
            roomCode = roomCode,
            phase = phase,
            you = you,
            players = ranked,
            config = config,
            availableThemes = bank.themes,
            turnNumber = if (phase == GamePhase.LOBBY) 0 else (completedShowcases + 1).coerceAtMost(totalTurns.coerceAtLeast(1)),
            totalTurns = totalTurns,
            cycleNumber = currentCycle,
            totalCycles = totalCycles,
            roundMultiplier = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM) 1 else currentRoundMultiplier(),
            isSimultaneousWriting = isSprintWriting,
            hasSubmittedSprintMetaphor = isSprintWriting && myScroll?.submittedMetaphor != null,
            isSage = isSage,
            sageName = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER || isSprintWriting) null else sageRec?.name,
            sageAvatar = if (phase == GamePhase.LOBBY || phase == GamePhase.PODIUM || phase == GamePhase.TIE_BREAKER || isSprintWriting) null else sageRec?.avatar,
            secretLesson = if (showSecrets) myScroll?.lesson?.lesson else null,
            secretSparks = if (showSecrets) myScroll?.lesson?.sparks.orEmpty() else emptyList(),
            decoyPreview = if (showSecrets) myScroll?.decoyPreview.orEmpty() else emptyList(),
            offeredWildcard = if (showSecrets) myScroll?.offeredWildcard else null,
            activeWildcard = if (lens.showMetaphor) activeWildcard else null,
            forbiddenRoots = if (showSecrets && myScroll != null) bank.forbiddenHintsFor(myScroll.lesson) else emptyList(),
            rerollsLeft = if (showSecrets) myScroll?.rerollsLeft ?: 0 else 0,
            metaphorText = if (lens.showMetaphor) metaphorText else null,
            currentTheme = if (showSecrets) myScroll?.lesson?.theme else if (lens.showMetaphor) lesson?.theme else null,
            options = projectedOptions(lens),
            myChoiceId = if (lens.showOptions) myVote?.optionId else null,
            doubleDownAvailable = you.doubleDownAvailable,
            myDoubleDownActive = lens.showOptions && myVote?.doubleDown == true,
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
        const val REACTION_COOLDOWN_MS = 800L
        const val MAX_RECENT_REACTIONS = 12

        val ALLOWED_REACTIONS = setOf("🔥", "😂", "🤔", "👏", "🤯", "🦉")
        private val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ"

        fun generateRoomCode(rng: Random = Random.Default): String =
            (1..4).map { CODE_ALPHABET[rng.nextInt(CODE_ALPHABET.length)] }.joinToString("")

        private fun generateToken(rng: Random): String {
            val chars = "0123456789abcdef"
            return (1..24).map { chars[rng.nextInt(chars.length)] }.joinToString("")
        }
    }
}
