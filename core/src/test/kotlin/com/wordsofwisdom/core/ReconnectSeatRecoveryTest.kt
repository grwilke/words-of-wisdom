package com.wordsofwisdom.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies mid-game seat recovery when a player closes their phone browser (losing localStorage)
 * and rejoins by scanning the always-visible QR code with the same display name.
 */
class ReconnectSeatRecoveryTest {

    @Test
    fun disconnectedPlayerRejoiningWithSameNameRecoversSeatAndScore() {
        var now = 10_000L
        val engine = WisdomGameEngine(
            bank = LessonBank.loadDefault(),
            clock = { now },
            rng = Random(42),
            roomCode = "QR01",
            initialConfig = GameConfig(
                mode = GameMode.CLASSIC_TURNS,
                length = GameLength.QUICK,
                strictMetaphorGuard = false,
            ),
        )

        engine.onConnect("c1"); engine.onMessage("c1", """{"t":"join","name":"Alice","token":null}""")
        engine.onConnect("c2"); engine.onMessage("c2", """{"t":"join","name":"Bob","token":null}""")
        engine.onConnect("c3"); engine.onMessage("c3", """{"t":"join","name":"Cara","token":null}""")
        engine.onMessage("c1", """{"t":"start"}""")

        // Play one round so Bob earns points
        val sageId = engine.tvSnapshot().sageId!!
        val connByPlayer = mapOf(
            engine.tvSnapshot().players.first { it.name == "Alice" }.id to "c1",
            engine.tvSnapshot().players.first { it.name == "Bob" }.id to "c2",
            engine.tvSnapshot().players.first { it.name == "Cara" }.id to "c3",
        )
        val sageConn = connByPlayer[sageId]!!
        engine.onMessage(
            sageConn,
            """{"t":"submit_metaphor","text":"A single lantern warms the porch only when neighbors step inside"}""",
        )
        val correctId = engine.internalCorrectOptionIdForTest()!!
        connByPlayer.forEach { (pid, cid) ->
            if (pid != sageId) {
                engine.onMessage(cid, """{"t":"vote","optionId":"$correctId"}""")
            }
        }
        now += 2_000L
        engine.tick()
        assertEquals(GamePhase.REVEAL, engine.tvSnapshot().phase)

        val bobBefore = engine.tvSnapshot().players.first { it.name == "Bob" }
        assertTrue(bobBefore.score > 0, "Bob should have earned points before disconnecting")

        // Bob closes his browser tab -> server marks him disconnected
        engine.onDisconnect("c2")
        assertFalse(engine.tvSnapshot().players.first { it.id == bobBefore.id }.connected)

        // Bob scans the always-on QR code in a fresh tab (no token!) and types "bob"
        engine.onConnect("c2-rejoin")
        engine.onMessage("c2-rejoin", """{"t":"join","name":"bob","token":null}""")

        val playersAfter = engine.tvSnapshot().players
        assertEquals(3, playersAfter.size, "Must not create a duplicate seat")
        val bobAfter = playersAfter.first { it.id == bobBefore.id }
        assertTrue(bobAfter.connected, "Recovered player must be marked connected")
        assertEquals(bobBefore.score, bobAfter.score, "Recovered player must preserve score")
        assertEquals(bobBefore.avatar, bobAfter.avatar, "Recovered player must preserve avatar")
    }

    @Test
    fun connectedPlayerWithSameNameStillDisambiguatesNewJoiner() {
        val engine = WisdomGameEngine(bank = LessonBank.loadDefault(), rng = Random(7), roomCode = "QR02")
        engine.onConnect("c1"); engine.onMessage("c1", """{"t":"join","name":"Alice","token":null}""")
        engine.onConnect("c2"); engine.onMessage("c2", """{"t":"join","name":"Alice","token":null}""")

        val players = engine.tvSnapshot().players
        assertEquals(2, players.size)
        assertNotEquals(players[0].id, players[1].id)
        assertEquals("Alice", players[0].name)
        assertEquals("Alice 2", players[1].name)
    }
}
