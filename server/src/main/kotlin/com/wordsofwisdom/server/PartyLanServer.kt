package com.wordsofwisdom.server

import com.wordsofwisdom.core.OutgoingMessage
import com.wordsofwisdom.core.TvSnapshot
import com.wordsofwisdom.core.WisdomGameEngine
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.serverConfig
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.options
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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

/**
 * Embedded LAN HTTP + WebSocket server running directly on the TV.
 *
 * Hardened for Android TV:
 * - `reuseAddress = true` so quick app restarts cleanly rebind port 8777
 * - `developmentMode = false` + `CoroutineExceptionHandler` so Ktor never crashes Android
 * - Async non-blocking WebSocket sends on [sendScope] with timeout so a sleeping/closed phone
 *   socket can never stall [gameDispatcher] or other players
 * - Private Network Access headers so Chrome's Local Network Access probe succeeds immediately
 */
class PartyLanServer(
    private val engine: WisdomGameEngine,
    private val tickIntervalMs: Long = 250L,
) {
    private val gameDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "wisdom-party-engine").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(gameDispatcher + CoroutineExceptionHandler { _, _ -> })
    private val sendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })

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
        sendScope.cancel()
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
        val cfg = serverConfig {
            developmentMode = false
            watchPaths = emptyList()
            parentCoroutineContext = CoroutineExceptionHandler { _, _ -> }
            module { installRoutes() }
        }
        val srv = embeddedServer(CIO, cfg) {
            connector {
                host = "0.0.0.0"
                this.port = port
            }
            reuseAddress = (port != 0)
        }
        try {
            srv.start(wait = false)
            return srv
        } catch (e: Exception) {
            runCatching { srv.stop(0, 0) }
            throw e
        }
    }

    private fun Application.installRoutes() {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 40.seconds
            maxFrameSize = 8_192L
        }
        routing {
            options("/{...}") {
                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                call.response.header("Access-Control-Allow-Private-Network", "true")
                call.respondText("", status = HttpStatusCode.NoContent)
            }
            get("/") {
                call.response.header(HttpHeaders.CacheControl, "no-store")
                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                call.response.header("Access-Control-Allow-Private-Network", "true")
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
                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                call.response.header("Access-Control-Allow-Private-Network", "true")
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

    private fun step(action: () -> List<OutgoingMessage>) {
        val outgoing = action()
        _state.value = engine.tvSnapshot()
        for (msg in outgoing) {
            val session = sessions[msg.connectionId] ?: continue
            sendScope.launch {
                val sent = withTimeoutOrNull(SEND_TIMEOUT_MS) {
                    runCatching { session.send(Frame.Text(msg.payloadJson)) }.isSuccess
                } ?: false
                if (!sent) {
                    runCatching { session.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Send timeout")) }
                }
            }
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
        private const val SEND_TIMEOUT_MS = 3_000L
    }
}
