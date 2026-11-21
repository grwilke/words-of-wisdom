package com.wordsofwisdom.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WisdomGameEngineTest {

    private class FakeClock(var now: Long = 10_000L) {
        fun advance(ms: Long) {
            now += ms
        }
    }

    private fun newEngine(clock: FakeClock = FakeClock(), seed: Int = 1234): WisdomGameEngine =
        WisdomGameEngine(
            bank = LessonBank.loadDefault(),
            clock = { clock.now },
            rng = Random(seed),
            roomCode = "SAGE",
        )

    @Test
    fun fullThreePlayerGameLifecycleFromLobbyToPodium() {
        val clock = FakeClock()
        val engine = newEngine(clock)

        // 3 humans join
        engine.onConnect("c1")
        engine.onMessage("c1", """{"t":"join","name":"Alice","token":null}""")
        engine.onConnect("c2")
        engine.onMessage("c2", """{"t":"join","name":"Bob","token":null}""")
        engine.onConnect("c3")
        engine.onMessage("c3", """{"t":"join","name":"Cara","token":null}""")

        val lobby = engine.tvSnapshot()
        assertEquals(GamePhase.LOBBY, lobby.phase)
        assertEquals(3, lobby.players.size)
        assertTrue(lobby.canStart)
        assertTrue(lobby.players.first().isHost)

        // Configure QUICK game (for 3 players: min 4 turns or 1 per player -> check totalTurns >= 3)
        engine.onMessage("c1", """{"t":"config","length":"QUICK","writeSeconds":60,"guessSeconds":30,"optionCount":4}""")
        engine.onMessage("c1", """{"t":"start"}""")

        var tv = engine.tvSnapshot()
        assertEquals(GamePhase.WRITING, tv.phase)
        val totalTurns = tv.totalTurns
        assertTrue(totalTurns >= 3)

        // Play every turn to completion!
        val connById = mapOf(
            tv.players[0].id to "c1",
            tv.players[1].id to "c2",
            tv.players[2].id to "c3",
        )

        val cleverMetaphors = listOf(
            "A wooden sailboat drifts nowhere when the harbor breeze sleeps",
            "Two sparrows sharing one warm chimney outlast a snowstorm",
            "Polishing a dusty violin each dusk fills the hall with gold",
            "A stubborn squirrel drops ten acorns trying to hug eleven",
            "Fresh basil tastes brighter when planted beside gentle thyme",
            "Old lanterns glow warmest along foggy cobblestones at midnight",
        )

        var safety = 0
        while (engine.tvSnapshot().phase != GamePhase.PODIUM && safety < 15) {
            safety++
            tv = engine.tvSnapshot()
            when (tv.phase) {
                GamePhase.WRITING -> {
                    val sageId = assertNotNull(tv.sageId)
                    val sageConn = assertNotNull(connById[sageId])
                    // Verify ONLY sage receives secretLesson
                    val sageState = engine.phoneStateFor(sageId)
                    assertNotNull(sageState?.secretLesson)
                    for (other in tv.players.filter { it.id != sageId }) {
                        assertNull(engine.phoneStateFor(other.id)?.secretLesson, "Seekers must NEVER see secret lesson during WRITING")
                    }
                    // Sage types and submits metaphor
                    val metaphor = cleverMetaphors[(safety - 1) % cleverMetaphors.size]
                    engine.onMessage(sageConn, """{"t":"typing","chars":${metaphor.length}}""")
                    assertEquals(metaphor.length, engine.tvSnapshot().sageTypingChars)
                    engine.onMessage(sageConn, """{"t":"submit_metaphor","text":"$metaphor"}""")
                    assertEquals(GamePhase.GUESSING, engine.tvSnapshot().phase)
                }

                GamePhase.GUESSING -> {
                    val sageId = assertNotNull(tv.sageId)
                    val correctId = assertNotNull(engine.internalCorrectOptionIdForTest())
                    // Verify wire hides isCorrect during GUESSING
                    assertTrue(tv.options.none { it.isCorrect })
                    // First seeker guesses right, second seeker guesses wrong on odd turns so scores diverge cleanly
                    val seekers = tv.players.filter { it.id != sageId }
                    clock.advance(4_000L)
                    engine.onMessage(connById[seekers[0].id]!!, """{"t":"vote","optionId":"$correctId"}""")
                    val wrongId = tv.options.first { it.id != correctId }.id
                    val pick2 = if (safety % 2 == 0) correctId else wrongId
                    clock.advance(2_000L)
                    engine.onMessage(connById[seekers[1].id]!!, """{"t":"vote","optionId":"$pick2"}""")
                    // Tick past short 1.2s lock-in pause -> REVEAL
                    clock.advance(1_500L)
                    engine.tick()
                    assertEquals(GamePhase.REVEAL, engine.tvSnapshot().phase)
                }

                GamePhase.REVEAL -> {
                    // Seeker applauds Sage
                    val sageId = assertNotNull(tv.sageId)
                    val seeker = tv.players.first { it.id != sageId }
                    engine.onMessage(connById[seeker.id]!!, """{"t":"applause"}""")
                    assertEquals(1, engine.tvSnapshot().applauseCount)
                    // Host advances
                    engine.onMessage("c1", """{"t":"next"}""")
                }

                GamePhase.TIE_BREAKER -> {
                    val correctId = assertNotNull(engine.internalCorrectOptionIdForTest())
                    val contenderId = tv.tieBreakerContenderIds.first()
                    engine.onMessage(connById[contenderId]!!, """{"t":"vote","optionId":"$correctId"}""")
                    clock.advance(2_000L)
                    engine.tick()
                }

                else -> break
            }
        }

        val podium = engine.tvSnapshot()
        assertEquals(GamePhase.PODIUM, podium.phase)
        assertTrue(podium.players.first().score > 0)
        assertTrue(podium.highlights.isNotEmpty(), "Podium should showcase Hall of Metaphors")
        assertTrue(podium.awards.isNotEmpty(), "Podium should award fun superlatives")

        // Play again resets scores and returns to LOBBY keeping players
        engine.onMessage("c1", """{"t":"play_again"}""")
        val resetLobby = engine.tvSnapshot()
        assertEquals(GamePhase.LOBBY, resetLobby.phase)
        assertEquals(3, resetLobby.players.size)
        assertTrue(resetLobby.players.all { it.score == 0 })
    }

    @Test
    fun sageCanRerollOnceAndAntiSpoilerBlocksBadMetaphor() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        engine.onConnect("c1")
        engine.onMessage("c1", """{"t":"join","name":"Ana","token":null}""")
        engine.onConnect("c2")
        engine.onMessage("c2", """{"t":"join","name":"Ben","token":null}""")
        engine.onMessage("c1", """{"t":"start"}""")

        val sageId = assertNotNull(engine.tvSnapshot().sageId)
        val sageConn = if (sageId == engine.tvSnapshot().players[0].id) "c1" else "c2"

        val firstLesson = assertNotNull(engine.phoneStateFor(sageId)?.secretLesson)
        assertEquals(1, engine.phoneStateFor(sageId)?.rerollsLeft)

        // Reroll once
        engine.onMessage(sageConn, """{"t":"reroll"}""")
        val secondLesson = assertNotNull(engine.phoneStateFor(sageId)?.secretLesson)
        assertTrue(firstLesson != secondLesson, "Reroll should supply a fresh lesson")
        assertEquals(0, engine.phoneStateFor(sageId)?.rerollsLeft)

        // Second reroll ignored
        engine.onMessage(sageConn, """{"t":"reroll"}""")
        assertEquals(secondLesson, engine.phoneStateFor(sageId)?.secretLesson)

        // Literal copy blocked and phase stays WRITING
        val out = engine.onMessage(sageConn, """{"t":"submit_metaphor","text":"$secondLesson"}""")
        assertEquals(GamePhase.WRITING, engine.tvSnapshot().phase)
        assertTrue(out.any { it.connectionId == sageConn && it.payloadJson.contains("metaphor_rejected") })
    }

    @Test
    fun soloPlayerWithBotsPlaysSmoothlyEndToEnd() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        engine.onConnect("c1")
        engine.onMessage("c1", """{"t":"join","name":"Gustavo","token":null}""")
        assertFalse(engine.tvSnapshot().canStart, "Need at least 2 players to start")

        // Add 2 AI bots
        engine.onMessage("c1", """{"t":"add_bot"}""")
        engine.onMessage("c1", """{"t":"add_bot"}""")
        assertEquals(3, engine.tvSnapshot().players.size)
        assertTrue(engine.tvSnapshot().canStart)

        engine.onMessage("c1", """{"t":"config","length":"QUICK","writeSeconds":45,"guessSeconds":20,"optionCount":4}""")
        engine.onMessage("c1", """{"t":"start"}""")

        // If human is Sage, submit; if Bot is Sage, advancing clock causes bot to submit automatically!
        for (step in 1..40) {
            val tv = engine.tvSnapshot()
            if (tv.phase == GamePhase.PODIUM) break
            when (tv.phase) {
                GamePhase.WRITING -> {
                    val sage = tv.players.first { it.id == tv.sageId }
                    if (!sage.isBot) {
                        engine.onMessage("c1", """{"t":"submit_metaphor","text":"A bright brass lantern warms the whole snowy trail at midnight"}""")
                    } else {
                        clock.advance(5_000L)
                        engine.tick()
                    }
                }
                GamePhase.GUESSING -> {
                    val sage = tv.players.first { it.id == tv.sageId }
                    if (!sage.isBot) {
                        // Bots vote automatically as time advances
                        clock.advance(6_000L)
                        engine.tick()
                    } else {
                        engine.onMessage("c1", """{"t":"vote","optionId":"A"}""")
                        clock.advance(6_000L)
                        engine.tick()
                    }
                }
                GamePhase.REVEAL -> {
                    engine.remoteOk()
                }
                GamePhase.TIE_BREAKER -> {
                    val correctId = engine.internalCorrectOptionIdForTest() ?: "A"
                    engine.onMessage("c1", """{"t":"vote","optionId":"$correctId"}""")
                    clock.advance(6_000L)
                    engine.tick()
                }
                else -> break
            }
        }
        assertEquals(GamePhase.PODIUM, engine.tvSnapshot().phase)
    }

    @Test
    fun playerCanReconnectWithTokenAndHostMigratesOnDisconnect() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        engine.onConnect("c1")
        val join1 = engine.onMessage("c1", """{"t":"join","name":"Ana","token":null}""")
        val token1 = Regex("\"token\":\"([^\"]+)\"").find(join1.first { it.payloadJson.contains("\"joined\"") }.payloadJson)!!.groupValues[1]

        engine.onConnect("c2")
        engine.onMessage("c2", """{"t":"join","name":"Leo","token":null}""")

        // Host disconnects; after 10s Leo becomes host
        engine.onDisconnect("c1")
        clock.advance(11_000L)
        engine.tick()
        val playersAfterMigration = engine.tvSnapshot().players
        assertTrue(playersAfterMigration.first { it.name == "Leo" }.isHost)

        // Ana reconnects with token on a new socket c3
        engine.onConnect("c3")
        engine.onMessage("c3", """{"t":"join","name":"IgnoredName","token":"$token1"}""")
        val ana = engine.tvSnapshot().players.first { it.name == "Ana" }
        assertTrue(ana.connected)
        assertEquals(2, engine.tvSnapshot().players.size)
    }

    @Test
    fun suddenDeathTieBreakerResolvesTiedFirstPlace() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        engine.onConnect("c1")
        engine.onMessage("c1", """{"t":"join","name":"P1","token":null}""")
        engine.onConnect("c2")
        engine.onMessage("c2", """{"t":"join","name":"P2","token":null}""")

        // Start a 2-player QUICK game (2 turns: P1 is Sage, then P2 is Sage)
        engine.onMessage("c1", """{"t":"config","length":"QUICK","writeSeconds":45,"guessSeconds":30,"optionCount":4}""")
        engine.onMessage("c1", """{"t":"start"}""")

        // Both players earn IDENTICAL points: each Sage writes a valid metaphor, each Seeker guesses right at exact same elapsed time (5000ms)
        val totalTurns = engine.tvSnapshot().totalTurns
        val connById = mapOf(
            engine.tvSnapshot().players[0].id to "c1",
            engine.tvSnapshot().players[1].id to "c2",
        )
        val metaphors = listOf(
            "A tall silver lighthouse blinks steadily across misty dark swells",
            "Two warm teacups sitting together never turn icy before sunset",
            "A sleepy tortoise crossing mossy pebbles reaches the meadow first",
            "Carving simple birch spoons requires patience rather than shiny saws",
        )
        repeat(totalTurns) { i ->
            val tv = engine.tvSnapshot()
            val sageId = tv.sageId!!
            val seekerId = tv.players.first { it.id != sageId }.id
            engine.onMessage(connById[sageId]!!, """{"t":"submit_metaphor","text":"${metaphors[i % metaphors.size]}"}""")
            val correctId = engine.internalCorrectOptionIdForTest()!!
            clock.advance(5_000L)
            engine.onMessage(connById[seekerId]!!, """{"t":"vote","optionId":"$correctId"}""")
            clock.advance(1_500L)
            engine.tick()
            assertEquals(GamePhase.REVEAL, engine.tvSnapshot().phase)
            engine.remoteOk()
        }

        // Because P1 and P2 have identical scores (> 0), engine enters TIE_BREAKER!
        val tb = engine.tvSnapshot()
        assertEquals(GamePhase.TIE_BREAKER, tb.phase)
        assertEquals(2, tb.tieBreakerContenderIds.size)
        assertNotNull(tb.metaphorText)

        // P2 guesses right first -> P2 wins sudden death!
        val correctId = engine.internalCorrectOptionIdForTest()!!
        clock.advance(3_000L)
        engine.onMessage("c2", """{"t":"vote","optionId":"$correctId"}""")
        clock.advance(3_000L)
        engine.tick()

        val finalState = engine.tvSnapshot()
        assertEquals(GamePhase.PODIUM, finalState.phase)
        assertEquals("P2", finalState.players.first().name)
    }

    @Test
    fun writingTimeoutAutoSubmitsValidDraftOrGracefullySkips() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        engine.onConnect("c1")
        engine.onMessage("c1", """{"t":"join","name":"Ana","token":null}""")
        engine.onConnect("c2")
        engine.onMessage("c2", """{"t":"join","name":"Ben","token":null}""")
        engine.onMessage("c1", """{"t":"config","length":"QUICK","writeSeconds":45,"guessSeconds":30,"optionCount":4}""")
        engine.onMessage("c1", """{"t":"start"}""")

        val firstSage = engine.tvSnapshot().sageId
        // Timeout with zero typing -> gracefully advances to next turn without crashing
        clock.advance(46_000L)
        engine.tick()
        val next = engine.tvSnapshot()
        assertEquals(GamePhase.WRITING, next.phase)
        assertTrue(next.sageId != firstSage || next.turnNumber == 2)
    }
}
