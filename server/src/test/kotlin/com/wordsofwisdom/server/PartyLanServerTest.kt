package com.wordsofwisdom.server

import com.wordsofwisdom.core.GamePhase
import com.wordsofwisdom.core.LessonBank
import com.wordsofwisdom.core.WisdomGameEngine
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyLanServerTest {

    private class TestWsClient : WebSocket.Listener {
        val messages = LinkedBlockingQueue<String>()
        private val buffer = StringBuilder()

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            buffer.append(data)
            if (last) {
                messages.offer(buffer.toString())
                buffer.setLength(0)
            }
            webSocket.request(1)
            return CompletableFuture.completedFuture(null)
        }

        fun awaitContaining(needle: String, timeoutSec: Long = 5): String {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec)
            while (System.nanoTime() < deadline) {
                val leftMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(1)
                val msg = messages.poll(leftMs, TimeUnit.MILLISECONDS) ?: break
                if (msg.contains(needle)) return msg
            }
            error("Timed out waiting for message containing '$needle'")
        }
    }

    @Test
    fun servesControllerAssetsAndRejectsUnknownPaths() {
        val engine = WisdomGameEngine(LessonBank.loadDefault(), rng = Random(7), roomCode = "TEST")
        val server = PartyLanServer(engine)
        val port = server.start(preferredPort = 0)
        try {
            val http = HttpClient.newHttpClient()
            val indexRes = http.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, indexRes.statusCode())
            assertTrue(indexRes.body().contains("Words of Wisdom"))

            val cssRes = http.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/style.css")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, cssRes.statusCode())

            val jsRes = http.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/controller.js")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(200, jsRes.statusCode())

            val missingRes = http.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/secret.txt")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertEquals(404, missingRes.statusCode())
        } finally {
            server.stop()
        }
    }

    @Test
    fun twoWebSocketPhonesJoinStartWriteAndVoteLive() {
        val engine = WisdomGameEngine(LessonBank.loadDefault(), rng = Random(42), roomCode = "LIVE")
        val server = PartyLanServer(engine)
        val port = server.start(preferredPort = 0)
        try {
            val http = HttpClient.newHttpClient()
            val l1 = TestWsClient()
            val l2 = TestWsClient()
            val ws1 = http.newWebSocketBuilder().buildAsync(URI("ws://127.0.0.1:$port/ws"), l1).get(5, TimeUnit.SECONDS)
            val ws2 = http.newWebSocketBuilder().buildAsync(URI("ws://127.0.0.1:$port/ws"), l2).get(5, TimeUnit.SECONDS)

            ws1.sendText("""{"t":"join","name":"Maya","token":null,"avatarId":"owl"}""", true).get(3, TimeUnit.SECONDS)
            l1.awaitContaining("\"joined\"")
            ws2.sendText("""{"t":"join","name":"Noah","token":null,"avatarId":"fox"}""", true).get(3, TimeUnit.SECONDS)
            l2.awaitContaining("\"joined\"")

            // Host starts
            ws1.sendText("""{"t":"start"}""", true).get(3, TimeUnit.SECONDS)
            val state1 = l1.awaitContaining("\"WRITING\"")
            l2.awaitContaining("\"WRITING\"")

            // Determine who is Sage and submit a metaphor
            val sageWs = if (state1.contains("\"isSage\":true")) ws1 else ws2
            val seekerWs = if (sageWs === ws1) ws2 else ws1
            sageWs.sendText("""{"t":"submit_metaphor","text":"A wooden lantern glows warmest when shared along a foggy path"}""", true)
                .get(3, TimeUnit.SECONDS)

            l1.awaitContaining("\"GUESSING\"")
            l2.awaitContaining("\"GUESSING\"")

            // Seeker votes A -> triggers 1.2s lock-in -> REVEAL
            seekerWs.sendText("""{"t":"vote","optionId":"A"}""", true).get(3, TimeUnit.SECONDS)
            l1.awaitContaining("\"REVEAL\"", timeoutSec = 6)
            assertEquals(GamePhase.REVEAL, server.state.value.phase)

            ws1.sendClose(WebSocket.NORMAL_CLOSURE, "done")
            ws2.sendClose(WebSocket.NORMAL_CLOSURE, "done")
        } finally {
            server.stop()
        }
    }
}
