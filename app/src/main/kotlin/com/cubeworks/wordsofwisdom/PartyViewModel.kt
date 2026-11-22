package com.cubeworks.wordsofwisdom

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubeworks.wordsofwisdom.audio.SoundEngine
import com.wordsofwisdom.core.GameConfig
import com.wordsofwisdom.core.GameMode
import com.wordsofwisdom.core.GamePhase
import com.wordsofwisdom.core.LessonBank
import com.wordsofwisdom.core.PromptPack
import com.wordsofwisdom.core.TvSnapshot
import com.wordsofwisdom.core.WisdomGameEngine
import com.wordsofwisdom.server.PartyLanServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address

/**
 * Owns the embedded [PartyLanServer], LAN address watcher, and [SoundEngine] across config changes.
 * Defaults live TV rooms to simultaneous PARTY_SPRINT mode + ALL (Classic & Modern Funny) lessons.
 */
class PartyViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = WisdomGameEngine(
        bank = LessonBank.loadDefault(),
        initialConfig = GameConfig(
            mode = GameMode.PARTY_SPRINT,
            pack = PromptPack.MODERN,
            optionCount = 3,
            strictMetaphorGuard = false,
        ),
    )
    private val server = PartyLanServer(engine)
    private val sound = SoundEngine(application.applicationContext)

    val state: StateFlow<TvSnapshot> = server.state

    private val _joinInfo = MutableStateFlow<TvJoinInfo?>(null)
    val joinInfo: StateFlow<TvJoinInfo?> = _joinInfo.asStateFlow()

    private val _musicMuted = MutableStateFlow(false)
    val musicMuted: StateFlow<Boolean> = _musicMuted.asStateFlow()

    private val _voiceMuted = MutableStateFlow(false)
    val voiceMuted: StateFlow<Boolean> = _voiceMuted.asStateFlow()

    private val _confirmingEnd = MutableStateFlow(false)
    val confirmingEnd: StateFlow<Boolean> = _confirmingEnd.asStateFlow()

    private var lastCueSeq: Long = 0L
    private var lastSpokenKey: String = ""

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val port = server.start(PartyLanServer.DEFAULT_PORT)
            var lastAddr: Inet4Address? = null
            while (isActive) {
                val addr = NetworkUtils.currentLanAddress(getApplication())
                if (addr != lastAddr) {
                    lastAddr = addr
                    _joinInfo.value = addr?.let {
                        // Uses the verified live GitHub Pages HTTPS bridge for Android 17 Chrome LNA!
                        NetworkUtils.buildJoinInfo(it, port, useBridge = true)
                    }
                }
                delay(2_000L)
            }
        }

        viewModelScope.launch {
            server.state.collect { snap ->
                sound.syncPhase(snap.phase)
                if (snap.cueSeq > lastCueSeq) {
                    lastCueSeq = snap.cueSeq
                    snap.lastCue?.let { sound.playCue(it) }
                }
                if ((snap.phase == GamePhase.GUESSING || snap.phase == GamePhase.TIE_BREAKER) &&
                    !snap.metaphorText.isNullOrBlank()
                ) {
                    val key = "${snap.phase}:${snap.turnNumber}:${snap.metaphorText}"
                    if (key != lastSpokenKey) {
                        lastSpokenKey = key
                        sound.speakMetaphor(
                            sageName = if (snap.phase == GamePhase.TIE_BREAKER) "Sudden Death" else snap.sageName,
                            metaphor = snap.metaphorText!!,
                        )
                    }
                }
            }
        }
    }

    fun onRemoteOk() {
        if (_confirmingEnd.value) {
            confirmEndGame()
            return
        }
        server.remoteOk()
    }

    fun onAddBot() = server.remoteAddBot()
    fun onRemoveBot() = server.remoteRemoveBot()
    fun onToggleMode() = server.remoteToggleMode()
    fun onCyclePack() = server.remoteCyclePack()

    fun toggleMute() {
        val next = !_musicMuted.value
        _musicMuted.value = next
        sound.setMusicMuted(next)
    }

    fun toggleVoice() {
        val next = !_voiceMuted.value
        _voiceMuted.value = next
        sound.setVoiceMuted(next)
    }

    fun onRemoteBack(): Boolean {
        if (_confirmingEnd.value) {
            _confirmingEnd.value = false
            return true
        }
        val phase = state.value.phase
        if (phase != GamePhase.LOBBY && phase != GamePhase.PODIUM) {
            _confirmingEnd.value = true
            return true
        }
        return false
    }

    fun confirmEndGame() {
        _confirmingEnd.value = false
        server.remoteEndGame()
    }

    fun dismissEndDialog() {
        _confirmingEnd.value = false
    }

    fun onAppForeground() = sound.onAppForeground()
    fun onAppBackground() = sound.onAppBackground()

    fun onAppFinishing() {
        sound.onAppBackground()
        sound.release()
        server.stop()
    }

    override fun onCleared() {
        super.onCleared()
        sound.onAppBackground()
        sound.release()
        server.stop()
    }
}
