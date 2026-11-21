package com.wordsofwisdom.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartyUpgradesV11Test {

    private class FakeClock(var now: Long = 50_000L) {
        fun advance(ms: Long) {
            now += ms
        }
    }

    private fun newEngine(
        clock: FakeClock = FakeClock(),
        seed: Int = 99,
        initialConfig: GameConfig = GameConfig(),
    ): WisdomGameEngine = WisdomGameEngine(
        bank = LessonBank.loadDefault(),
        clock = { clock.now },
        rng = Random(seed),
        roomCode = "FUN1",
        initialConfig = initialConfig,
    )

    @Test
    fun lessonBankContainsModernFunnyPackAndFiltersCleanly() {
        val bank = LessonBank.loadDefault()
        assertEquals(150, bank.lessons.size, "Expected 120 Classic + 30 Modern Funny lessons")
        val modernCount = bank.lessons.count { it.pack == "MODERN" }
        val classicCount = bank.lessons.count { it.pack == "CLASSIC" }
        assertEquals(30, modernCount)
        assertEquals(120, classicCount)

        val rng = Random(7)
        val drawnModern = (1..20).map {
            bank.drawLesson(selectedThemes = emptyList(), usedIds = emptySet(), rng = rng, pack = PromptPack.MODERN)
        }
        assertTrue(drawnModern.all { it.pack == "MODERN" })

        val drawnClassic = (1..20).map {
            bank.drawLesson(selectedThemes = emptyList(), usedIds = emptySet(), rng = rng, pack = PromptPack.CLASSIC)
        }
        assertTrue(drawnClassic.all { it.pack == "CLASSIC" })
    }

    @Test
    fun partySprintSimultaneousWritingEliminatesTurnDowntime() {
        val clock = FakeClock()
        val engine = newEngine(clock, initialConfig = GameConfig(mode = GameMode.PARTY_SPRINT, length = GameLength.QUICK))

        engine.onConnect("c1"); engine.onMessage("c1", """{"t":"join","name":"Maya","token":null}""")
        engine.onConnect("c2"); engine.onMessage("c2", """{"t":"join","name":"Leo","token":null}""")
        engine.onConnect("c3"); engine.onMessage("c3", """{"t":"join","name":"Sofia","token":null}""")
        engine.onMessage("c1", """{"t":"start"}""")

        val writingTv = engine.tvSnapshot()
        assertEquals(GamePhase.WRITING, writingTv.phase)
        assertTrue(writingTv.isSimultaneousWriting)
        assertEquals(3, writingTv.sprintWriters.size)

        // ALL 3 players receive their OWN distinct secret lessons, decoy previews, and wildcard offers!
        val ids = writingTv.players.map { it.id }
        val states = ids.map { engine.phoneStateFor(it)!! }
        assertTrue(states.all { it.isSage && it.secretLesson != null })
        assertEquals(3, states.map { it.secretLesson }.toSet().size)
        assertTrue(states.all { it.decoyPreview.size == 2 })
        assertTrue(states.all { it.offeredWildcard != null })

        // Players 1 and 2 submit -> stays in WRITING until Player 3 submits!
        engine.onMessage("c1", """{"t":"submit_metaphor","text":"A wooden boat waits for harbor winds before lifting its sails","wildcardAccepted":true}""")
        assertEquals(GamePhase.WRITING, engine.tvSnapshot().phase)
        assertTrue(engine.phoneStateFor(ids[0])!!.hasSubmittedSprintMetaphor)

        engine.onMessage("c2", """{"t":"submit_metaphor","text":"Two sparrows sharing one warm chimney brave the snowy night","wildcardAccepted":false}""")
        assertEquals(GamePhase.WRITING, engine.tvSnapshot().phase)

        engine.onMessage("c3", """{"t":"submit_metaphor","text":"A hungry squirrel hoarding twenty acorns drops half on the stairs","wildcardAccepted":true}""")

        // The instant all 3 submit -> straight into GUESSING showcase 1 of 3!
        assertEquals(GamePhase.GUESSING, engine.tvSnapshot().phase)
        assertEquals(1, engine.tvSnapshot().turnNumber)
        assertEquals(3, engine.tvSnapshot().totalTurns)

        // Advance through all 3 showcases -> ZERO additional WRITING phases!
        val connById = mapOf(ids[0] to "c1", ids[1] to "c2", ids[2] to "c3")
        var writingPhasesSeen = 0
        repeat(3) {
            val tv = engine.tvSnapshot()
            assertEquals(GamePhase.GUESSING, tv.phase)
            val sageId = tv.sageId!!
            val correctId = engine.internalCorrectOptionIdForTest()!!
            val seekers = tv.players.filter { it.id != sageId }
            seekers.forEach { s ->
                engine.onMessage(connById[s.id]!!, """{"t":"vote","optionId":"$correctId"}""")
            }
            clock.advance(1_500L)
            engine.tick()
            assertEquals(GamePhase.REVEAL, engine.tvSnapshot().phase)
            engine.remoteOk()
            if (engine.tvSnapshot().phase == GamePhase.WRITING) writingPhasesSeen++
        }
        assertEquals(0, writingPhasesSeen, "Party Sprint QUICK should never re-enter WRITING between showcases")
        assertTrue(engine.tvSnapshot().phase in setOf(GamePhase.PODIUM, GamePhase.TIE_BREAKER))
    }

    @Test
    fun sageDecoyPreviewMatchesActualBoardDecoysWithoutLeakingToSeekers() {
        val clock = FakeClock()
        val engine = newEngine(clock, initialConfig = GameConfig(mode = GameMode.CLASSIC_TURNS))
        engine.onConnect("c1"); engine.onMessage("c1", """{"t":"join","name":"Ana","token":null}""")
        engine.onConnect("c2"); engine.onMessage("c2", """{"t":"join","name":"Ben","token":null}""")
        engine.onMessage("c1", """{"t":"start"}""")

        val tv = engine.tvSnapshot()
        val sageId = tv.sageId!!
        val seekerId = tv.players.first { it.id != sageId }.id
        val sagePhone = engine.phoneStateFor(sageId)!!
        val seekerPhone = engine.phoneStateFor(seekerId)!!

        assertEquals(2, sagePhone.decoyPreview.size)
        assertTrue(seekerPhone.decoyPreview.isEmpty(), "Seekers must never see decoy preview during WRITING")
        assertFalse(sagePhone.secretLesson in sagePhone.decoyPreview)

        val sageConn = if (sageId == tv.players[0].id) "c1" else "c2"
        engine.onMessage(sageConn, """{"t":"submit_metaphor","text":"A bright brass lantern warms the whole snowy trail at midnight"}""")

        val optionTexts = engine.tvSnapshot().options.map { it.text }.toSet()
        for (previewDecoy in sagePhone.decoyPreview) {
            assertTrue(previewDecoy in optionTexts, "Previewed decoy '$previewDecoy' must appear on the GUESSING board!")
        }
    }

    @Test
    fun goldenOwlDoubleDownDoublesHitPenalizesMissAndConsumesToken() {
        val clock = FakeClock()
        val engine = newEngine(clock, initialConfig = GameConfig(mode = GameMode.CLASSIC_TURNS, length = GameLength.CLASSIC))
        engine.onConnect("c1"); engine.onMessage("c1", """{"t":"join","name":"Ana","token":null}""")
        engine.onConnect("c2"); engine.onMessage("c2", """{"t":"join","name":"Ben","token":null}""")
        engine.onConnect("c3"); engine.onMessage("c3", """{"t":"join","name":"Cara","token":null}""")
        engine.onMessage("c1", """{"t":"start"}""")

        val connById = mapOf(
            engine.tvSnapshot().players[0].id to "c1",
            engine.tvSnapshot().players[1].id to "c2",
            engine.tvSnapshot().players[2].id to "c3",
        )
        val sageId = engine.tvSnapshot().sageId!!
        val seekers = engine.tvSnapshot().players.filter { it.id != sageId }

        engine.onMessage(connById[sageId]!!, """{"t":"submit_metaphor","text":"A tall wooden windmill catches every gust while fences stand idle","wildcardAccepted":true}""")
        val correctId = engine.internalCorrectOptionIdForTest()!!
        val wrongId = engine.tvSnapshot().options.first { it.id != correctId }.id

        // Seeker 0 hits with Double Down! Seeker 1 misses with Double Down!
        engine.onMessage(connById[seekers[0].id]!!, """{"t":"vote","optionId":"$correctId","doubleDown":true}""")
        assertTrue(engine.tvSnapshot().players.first { it.id == seekers[0].id }.doubledDownThisTurn)
        engine.onMessage(connById[seekers[1].id]!!, """{"t":"vote","optionId":"$wrongId","doubleDown":true}""")

        clock.advance(1_500L)
        engine.tick()
        val reveal = engine.tvSnapshot()
        assertEquals(GamePhase.REVEAL, reveal.phase)

        val delta0 = reveal.roundDeltas.first { it.playerId == seekers[0].id }
        val delta1 = reveal.roundDeltas.first { it.playerId == seekers[1].id }
        val sageDelta = reveal.roundDeltas.first { it.playerId == sageId }

        assertTrue(delta0.usedDoubleDown)
        assertTrue(delta0.pointsEarned >= 200, "Double Down correct hit should be >= 200 pts, was ${delta0.pointsEarned}")
        assertTrue(delta1.usedDoubleDown)
        assertEquals(-50, delta1.pointsEarned, "Double Down miss should report -50 delta")
        assertEquals(0, reveal.players.first { it.id == seekers[1].id }.score, "Score must floor at 0")
        assertFalse(reveal.players.first { it.id == seekers[0].id }.doubleDownAvailable, "Token should be spent")
        assertEquals(40, sageDelta.wildcardBonus, "Accepted Wildcard with >= 1 correct guesser earns +40 Style Bonus")

        // Poet's Star (applause) gives boosted +25 pts to Sage!
        val beforeSageScore = reveal.players.first { it.id == sageId }.score
        engine.onMessage(connById[seekers[0].id]!!, """{"t":"applause"}""")
        val afterSageScore = engine.tvSnapshot().players.first { it.id == sageId }.score
        assertEquals(beforeSageScore + 25, afterSageScore, "Poet's Star applause should award +25 pts")
    }

    @Test
    fun finalCycleAppliesDoublePointsMultiplierFairly() {
        val clock = FakeClock()
        val engine = newEngine(clock, initialConfig = GameConfig(mode = GameMode.CLASSIC_TURNS, length = GameLength.CLASSIC))
        engine.onConnect("c1"); engine.onMessage("c1", """{"t":"join","name":"Ana","token":null}""")
        engine.onConnect("c2"); engine.onMessage("c2", """{"t":"join","name":"Ben","token":null}""")
        engine.onMessage("c1", """{"t":"start"}""")

        // 2 players CLASSIC = 2 cycles (4 turns). Turns 1-2 -> 1x, Turns 3-4 -> 2x!
        assertEquals(1, engine.tvSnapshot().roundMultiplier)
        assertEquals(2, engine.tvSnapshot().totalCycles)

        val connById = mapOf(
            engine.tvSnapshot().players[0].id to "c1",
            engine.tvSnapshot().players[1].id to "c2",
        )
        val lines = listOf(
            "A glowing fireplace invites cold boots without raising its voice",
            "Two bright lanterns outshine a lonely candle along foggy cobblestones",
            "Carving steady wooden oars beats wishing for gentler summer tides",
            "Fresh peppermint tea cools heated brows faster than loud arguments",
        )

        val multipliers = mutableListOf<Int>()
        repeat(4) { i ->
            val tv = engine.tvSnapshot()
            multipliers.add(tv.roundMultiplier)
            val sageId = tv.sageId!!
            val seekerId = tv.players.first { it.id != sageId }.id
            engine.onMessage(connById[sageId]!!, """{"t":"submit_metaphor","text":"${lines[i]}"}""")
            val correctId = engine.internalCorrectOptionIdForTest()!!
            clock.advance(5_000L)
            engine.onMessage(connById[seekerId]!!, """{"t":"vote","optionId":"$correctId"}""")
            clock.advance(1_500L)
            engine.tick()
            if (i == 2) {
                // Verify 2x multiplier doubled seeker points (> 200) on Turn 3!
                val d = engine.tvSnapshot().roundDeltas.first { it.playerId == seekerId }
                assertEquals(2, d.multiplier)
                assertTrue(d.pointsEarned >= 200)
            }
            engine.remoteOk()
        }
        assertEquals(listOf(1, 1, 2, 2), multipliers)
    }

    @Test
    fun liveEmojiReactionsRateLimitAndRejectUnknownPayloads() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        engine.onConnect("c1"); engine.onMessage("c1", """{"t":"join","name":"Maya","token":null}""")

        engine.onMessage("c1", """{"t":"react","emoji":"🔥"}""")
        assertEquals(1, engine.tvSnapshot().recentReactions.size)
        assertEquals("🔥", engine.tvSnapshot().recentReactions.last().emoji)

        // Spam within 800ms ignored
        clock.advance(200L)
        engine.onMessage("c1", """{"t":"react","emoji":"😂"}""")
        assertEquals(1, engine.tvSnapshot().recentReactions.size)

        // After 800ms accepted; disallowed text ignored
        clock.advance(700L)
        engine.onMessage("c1", """{"t":"react","emoji":"HACK"}""")
        assertEquals(1, engine.tvSnapshot().recentReactions.size)
        engine.onMessage("c1", """{"t":"react","emoji":"😂"}""")
        assertEquals(2, engine.tvSnapshot().recentReactions.size)
    }

    @Test
    fun lateJoinerCanEnterMidGameAsSeeker() {
        val clock = FakeClock()
        val engine = newEngine(clock)
        engine.onConnect("c1"); engine.onMessage("c1", """{"t":"join","name":"Ana","token":null}""")
        engine.onConnect("c2"); engine.onMessage("c2", """{"t":"join","name":"Ben","token":null}""")
        engine.onMessage("c1", """{"t":"start"}""")
        assertEquals(GamePhase.WRITING, engine.tvSnapshot().phase)

        // Third player walks into the living room mid-game!
        engine.onConnect("c3")
        val res = engine.onMessage("c3", """{"t":"join","name":"Cara","token":null}""")
        assertTrue(res.any { it.connectionId == "c3" && it.payloadJson.contains("\"joined\"") })
        assertEquals(3, engine.tvSnapshot().players.size)
        val cara = engine.tvSnapshot().players.first { it.name == "Cara" }
        assertEquals(0, cara.score)
        assertTrue(cara.doubleDownAvailable)
        assertNull(engine.phoneStateFor(cara.id)!!.secretLesson)
    }
}
