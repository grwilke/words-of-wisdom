package com.wordsofwisdom.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SillyProverbSideQuestTest {

    @Test
    fun waitingPlayersWriteSillyProverbsAndBlindlyRateThemOnReveal() {
        var now = 10_000L
        val engine = WisdomGameEngine(
            bank = LessonBank.loadDefault(),
            clock = { now },
            rng = Random(11),
            roomCode = "SILLY",
            initialConfig = GameConfig(
                mode = GameMode.CLASSIC_TURNS,
                length = GameLength.QUICK,
                strictMetaphorGuard = false,
            ),
        )

        engine.onConnect("c1"); engine.onMessage("c1", """{"t":"join","name":"Maya","token":null}""")
        engine.onConnect("c2"); engine.onMessage("c2", """{"t":"join","name":"Leo","token":null}""")
        engine.onConnect("c3"); engine.onMessage("c3", """{"t":"join","name":"Sofia","token":null}""")
        engine.onMessage("c1", """{"t":"start"}""")

        val sageId = engine.tvSnapshot().sageId!!
        val connById = mapOf(
            engine.tvSnapshot().players.first { it.name == "Maya" }.id to "c1",
            engine.tvSnapshot().players.first { it.name == "Leo" }.id to "c2",
            engine.tvSnapshot().players.first { it.name == "Sofia" }.id to "c3",
        )
        val waiters = connById.filterKeys { it != sageId }.values.toList()
        val waiterIds = connById.filterKeys { it != sageId }.keys.toList()

        // Active Sage cannot submit a silly proverb; waiting players can!
        engine.onMessage(connById[sageId]!!, """{"t":"submit_silly_proverb","text":"Sage should not submit this"}""")
        engine.onMessage(waiters[0], """{"t":"submit_silly_proverb","text":"Never trust a raccoon holding a tiny spoon"}""")
        engine.onMessage(waiters[1], """{"t":"submit_silly_proverb","text":"A dramatic toaster only pops when you look away"}""")

        // Sage submits metaphor and everyone votes to reach REVEAL
        engine.onMessage(connById[sageId]!!, """{"t":"submit_metaphor","text":"A lantern glows brightest when shared with neighbors"}""")
        val correctId = engine.internalCorrectOptionIdForTest()!!
        waiters.forEach { engine.onMessage(it, """{"t":"vote","optionId":"$correctId"}""") }
        now += 2_000L
        engine.tick()
        assertEquals(GamePhase.REVEAL, engine.tvSnapshot().phase)

        // Before voting, authors must be hidden (blind rating!)
        val leoStateBefore = engine.phoneStateFor(waiterIds[0])!!
        assertEquals(2, leoStateBefore.sillyProverbs.size)
        assertTrue(leoStateBefore.sillyProverbs.all { it.authorName == null })

        // Self-vote must be ignored
        val myProverb = leoStateBefore.sillyProverbs.first { it.isMine }
        val otherProverb = leoStateBefore.sillyProverbs.first { !it.isMine }
        val otherAuthorScoreBefore = engine.tvSnapshot().players.first { it.id == waiterIds[1] }.score
        engine.onMessage(waiters[0], """{"t":"vote_silly_proverb","proverbId":"${myProverb.id}"}""")
        assertEquals(otherAuthorScoreBefore, engine.tvSnapshot().players.first { it.id == waiterIds[1] }.score)

        // Valid vote for other player's proverb awards +25 pts and reveals authors!
        engine.onMessage(waiters[0], """{"t":"vote_silly_proverb","proverbId":"${otherProverb.id}"}""")
        val otherAuthorScoreAfter = engine.tvSnapshot().players.first { it.id == waiterIds[1] }.score
        assertEquals(otherAuthorScoreBefore + WisdomGameEngine.SILLY_PROVERB_VOTE_POINTS, otherAuthorScoreAfter)

        val leoStateAfter = engine.phoneStateFor(waiterIds[0])!!
        assertTrue(leoStateAfter.sillyProverbs.any { it.votedByMe && it.authorName != null })
        assertNotNull(engine.tvSnapshot().sillyProverbs.firstOrNull()?.authorName)
    }
}
