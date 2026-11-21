package com.wordsofwisdom.server

import com.wordsofwisdom.core.OutgoingMessage
import com.wordsofwisdom.core.TvSnapshot
import com.wordsofwisdom.core.WisdomGameEngine
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

/**
 * Embedded LAN HTTP + WebSocket server running directly on the TV.
 *
 * Serves the zero-dependency mobile web controller (`/`, `/style.css`, `/controller.js`) and
 * bridges each phone's `/ws` connection onto a single serialized game thread owning [engine].
 */
class PartyLanServer(
    private val engine: WisdomGameEngine,
    private val tickIntervalMs: Long = 250L,
) {
    private val gameDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "wisdom-party-engine").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(gameDispatcher)

    private val sessions = HashMap<String, DefaultWebSocketServerSession>()
    private val openConnections = AtomicInteger(0)
    private val nextConnId = AtomicLong(1L)

    private val _state = MutableStateFlow(engine.tvSnapshot())
    val state: StateFlow<TvSnapshot> = _state.asStateFlow()

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    private val indexBytes: ByteArray by lazy { loadResource("/controller/index.html") }
    private val cssBytes: ByteArray by lazy { loadResource("/controller/style.css") }
    private val jsBytes: ByteArray by lazy { loadResource("/controller/controller.js") }

    @Synchronized
    fun start(preferredPort: Int = DEFAULT_PORT): Int {
        check(scope.isActive) { "A stopped PartyLanServer cannot be restarted." }
        check(server == null) { "PartyLanServer is already running." }

        val started = try {
            launchKtor(preferredPort)
        } catch (_: Exception) {
            launchKtor(0)
        }
        server = started

        scope.launch {
            while (isActive) {
                delay(tickIntervalMs)
                step { engine.tick() }
            }
        }
        return runBlocking { started.engine.resolvedConnectors().first().port }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.stop(gracePeriodMillis = 0, timeoutMillis = 1_500) }
        server = null
        scope.cancel()
        gameDispatcher.close()
    }

    fun remoteOk() {
        scope.launch { step { engine.remoteOk() } }
    }

    fun remoteAddBot() {
        scope.launch { step { engine.remoteAddBot() } }
    }

    fun remoteRemoveBot() {
        scope.launch { step { engine.remoteRemoveBot() } }
    }

    fun remoteToggleMode() {
        scope.launch { step { engine.remoteToggleMode() } }
    }

    fun remoteCyclePack() {
        scope.launch { step { engine.remoteCyclePack() } }
    }

    fun remoteEndGame() {
        scope.launch { step { engine.remoteEndGame() } }
    }

    private fun launchKtor(port: Int): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> {
        val srv = embeddedServer(CIO, configure = {
            connector {
                host = "0.0.0.0"
                this.port = port
            }
        }) {
            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 40.seconds
                maxFrameSize = 8_192L
            }
            routing {
                get("/") {
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respondBytes(indexBytes, ContentType.Text.Html)
                }
                get("/style.css") {
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respondBytes(cssBytes, ContentType.Text.CSS)
                }
                get("/controller.js") {
                    call.response.header(HttpHeaders.CacheControl, "no-store")
                    call.respondBytes(jsBytes, ContentType.Application.JavaScript)
                }
                get("/health") {
                    call.respondText("ok", ContentType.Text.Plain)
                }
                webSocket("/ws") {
                    handleWebSocket(this)
                }
                get("/{...}") {
                    call.respondText("Not Found", status = HttpStatusCode.NotFound)
                }
            }
        }
        srv.start(wait = false)
        return srv
    }

    private suspend fun handleWebSocket(ws: DefaultWebSocketServerSession) {
        if (openConnections.incrementAndGet() > MAX_SOCKETS) {
            openConnections.decrementAndGet()
            ws.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many connections"))
            return
        }
        val connId = "ws-${nextConnId.getAndIncrement()}"
        var windowStartMs = System.nanoTime() / 1_000_000L
        var msgsInWindow = 0

        try {
            withContext(gameDispatcher) {
                sessions[connId] = ws
                step { engine.onConnect(connId) }
            }
            for (frame in ws.incoming) {
                if (frame is Frame.Text) {
                    val nowMs = System.nanoTime() / 1_000_000L
                    if (nowMs - windowStartMs >= 1_000L) {
                        windowStartMs = nowMs
                        msgsInWindow = 0
                    }
                    if (++msgsInWindow > MAX_MSGS_PER_SECOND) {
                        ws.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Rate limit exceeded"))
                        break
                    }
                    val text = frame.readText()
                    withContext(gameDispatcher) {
                        step { engine.onMessage(connId, text) }
                    }
                }
            }
        } catch (_: Exception) {
            // Normal disconnect or socket closed
        } finally {
            openConnections.decrementAndGet()
            withContext(NonCancellable + gameDispatcher) {
                sessions.remove(connId)
                step { engine.onDisconnect(connId) }
            }
        }
    }

    private suspend fun step(action: () -> List<OutgoingMessage>) {
        val outgoing = action()
        _state.value = engine.tvSnapshot()
        for (msg in outgoing) {
            val session = sessions[msg.connectionId] ?: continue
            runCatching { session.send(Frame.Text(msg.payloadJson)) }
        }
    }

    private fun loadResource(path: String): ByteArray {
        val stream = PartyLanServer::class.java.getResourceAsStream(path)
            ?: error("Missing controller resource: $path")
        return stream.use { it.readBytes() }
    }

    companion object {
        const val DEFAULT_PORT = 8777
        const val MAX_SOCKETS = 32
        const val MAX_MSGS_PER_SECOND = 25
    }
}
