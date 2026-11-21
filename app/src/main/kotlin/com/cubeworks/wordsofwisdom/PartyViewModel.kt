package com.cubeworks.wordsofwisdom

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubeworks.wordsofwisdom.audio.SoundEngine
import com.wordsofwisdom.core.GamePhase
import com.wordsofwisdom.core.LessonBank
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
 */
class PartyViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = WisdomGameEngine(LessonBank.loadDefault())
    private val server = PartyLanServer(engine)
    private val sound = SoundEngine(application.applicationContext)

    val state: StateFlow<TvSnapshot> = server.state

    private val _joinInfo = MutableStateFlow<TvJoinInfo?>(null)
    val joinInfo: StateFlow<TvJoinInfo?> = _joinInfo.asStateFlow()

    private val _musicMuted = MutableStateFlow(false)
    val musicMuted: StateFlow<Boolean> = _musicMuted.asStateFlow()

    private val _confirmingEnd = MutableStateFlow(false)
    val confirmingEnd: StateFlow<Boolean> = _confirmingEnd.asStateFlow()

    private var lastCueSeq: Long = 0L

    init {
        // Start embedded Ktor LAN server off main thread and watch TV IP address
        viewModelScope.launch(Dispatchers.IO) {
            val port = server.start(PartyLanServer.DEFAULT_PORT)
            var lastAddr: Inet4Address? = null
            while (isActive) {
                val addr = NetworkUtils.currentLanAddress(getApplication())
                if (addr != lastAddr) {
                    lastAddr = addr
                    _joinInfo.value = addr?.let {
                        // Direct local HTTP URL works immediately on every Wi-Fi phone (and offline!)
                        NetworkUtils.buildJoinInfo(it, port, useBridge = false)
                    }
                }
                delay(2_000L)
            }
        }

        // Sync background music mood & one-shot SFX cues to live engine snapshots
        viewModelScope.launch {
            server.state.collect { snap ->
                sound.syncPhase(snap.phase)
                if (snap.cueSeq > lastCueSeq) {
                    lastCueSeq = snap.cueSeq
                    snap.lastCue?.let { sound.playCue(it) }
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

    fun toggleMute() {
        val next = !_musicMuted.value
        _musicMuted.value = next
        sound.setMusicMuted(next)
    }

    /** Returns true if Back was consumed by the app; false lets Activity exit. */
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

    fun onPauseAudio() = sound.pause()
    fun onResumeAudio() = sound.resume()

    override fun onCleared() {
        super.onCleared()
        sound.release()
        server.stop()
    }
}
