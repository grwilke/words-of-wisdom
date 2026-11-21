package com.cubeworks.wordsofwisdom.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.RawRes
import com.cubeworks.wordsofwisdom.R
import com.wordsofwisdom.core.GamePhase
import com.wordsofwisdom.core.TvCue
import java.util.Locale

/**
 * Manages looping background party music (MediaPlayer), zero-latency SFX (SoundPool),
 * and dramatic offline Android TV TextToSpeech metaphor narration with automatic BGM ducking.
 */
class SoundEngine(private val context: Context) {

    enum class MusicMood(@param:RawRes val resId: Int) {
        LOBBY(R.raw.bgm_lobby),
        PLAY(R.raw.bgm_play),
        PODIUM(R.raw.bgm_podium),
    }

    private var musicMuted: Boolean = false
    private var voiceMuted: Boolean = false
    private var sfxMuted: Boolean = false
    private var currentMood: MusicMood? = null
    private var mediaPlayer: MediaPlayer? = null

    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false

    init {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching {
                        tts?.language = Locale.US
                        tts?.setPitch(0.95f)
                        tts?.setSpeechRate(0.94f)
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                duckMusic(true)
                            }
                            override fun onDone(utteranceId: String?) {
                                duckMusic(false)
                            }
                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                duckMusic(false)
                            }
                        })
                        ttsReady = true
                    }
                }
            }
        }
    }

    private val soundPool: SoundPool? = runCatching {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(attrs)
            .build()
    }.getOrNull()

    private val sfxIds: Map<TvCue, Int> = buildMap {
        val pool = soundPool ?: return@buildMap
        fun load(cue: TvCue, @RawRes res: Int) {
            runCatching { pool.load(context, res, 1) }.getOrNull()?.let { put(cue, it) }
        }
        load(TvCue.PLAYER_JOINED, R.raw.sfx_join)
        load(TvCue.ROUND_STARTED, R.raw.sfx_join)
        load(TvCue.METAPHOR_SUBMITTED, R.raw.sfx_submit)
        load(TvCue.VOTE_LOCKED, R.raw.sfx_vote)
        load(TvCue.DOUBLE_DOWN_LOCKED, R.raw.sfx_fanfare)
        load(TvCue.COUNTDOWN_TICK, R.raw.sfx_tick)
        load(TvCue.REVEAL_DRAMATIC, R.raw.sfx_reveal)
        load(TvCue.CORRECT_BURST, R.raw.sfx_correct)
        load(TvCue.EVERYONE_MISSED, R.raw.sfx_wrong)
        load(TvCue.APPLAUSE_POP, R.raw.sfx_applause)
        load(TvCue.REACTION_POP, R.raw.sfx_vote)
        load(TvCue.TIE_BREAKER_START, R.raw.sfx_fanfare)
        load(TvCue.PODIUM_FANFARE, R.raw.sfx_fanfare)
    }

    fun setMusicMuted(muted: Boolean) {
        musicMuted = muted
        applyBgmVolume(if (muted) 0f else BGM_VOLUME)
    }

    fun setVoiceMuted(muted: Boolean) {
        voiceMuted = muted
        if (muted) stopSpeaking()
    }

    fun setSfxMuted(muted: Boolean) {
        sfxMuted = muted
    }

    fun speakMetaphor(sageName: String?, metaphor: String) {
        if (voiceMuted || !ttsReady || metaphor.isBlank()) return
        val line = if (!sageName.isNullOrBlank()) "$sageName says... $metaphor" else metaphor
        runCatching {
            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.95f)
            }
            tts?.speak(line, TextToSpeech.QUEUE_FLUSH, params, "metaphor-${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        duckMusic(false)
    }

    private fun duckMusic(ducked: Boolean) {
        if (musicMuted) return
        applyBgmVolume(if (ducked) BGM_DUCKED_VOLUME else BGM_VOLUME)
    }

    private fun applyBgmVolume(vol: Float) {
        runCatching { mediaPlayer?.setVolume(vol, vol) }
    }

    fun syncPhase(phase: GamePhase) {
        if (phase != GamePhase.GUESSING && phase != GamePhase.TIE_BREAKER) {
            stopSpeaking()
        }
        val target = when (phase) {
            GamePhase.LOBBY -> MusicMood.LOBBY
            GamePhase.WRITING, GamePhase.GUESSING, GamePhase.REVEAL, GamePhase.TIE_BREAKER -> MusicMood.PLAY
            GamePhase.PODIUM -> MusicMood.PODIUM
        }
        if (target == currentMood && mediaPlayer != null) return
        switchMusic(target)
    }

    private fun switchMusic(mood: MusicMood) {
        currentMood = mood
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = runCatching {
            MediaPlayer.create(context, mood.resId)?.apply {
                isLooping = true
                val vol = if (musicMuted) 0f else BGM_VOLUME
                setVolume(vol, vol)
                start()
            }
        }.getOrNull()
    }

    fun playCue(cue: TvCue) {
        if (sfxMuted) return
        val pool = soundPool ?: return
        val id = sfxIds[cue] ?: return
        val vol = when (cue) {
            TvCue.COUNTDOWN_TICK -> 0.55f
            TvCue.REACTION_POP -> 0.42f
            TvCue.PODIUM_FANFARE, TvCue.REVEAL_DRAMATIC, TvCue.DOUBLE_DOWN_LOCKED -> 0.92f
            else -> 0.80f
        }
        runCatching { pool.play(id, vol, vol, 1, 0, 1.0f) }
    }

    fun pause() {
        stopSpeaking()
        runCatching { if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause() }
    }

    fun resume() {
        runCatching { if (mediaPlayer != null && mediaPlayer?.isPlaying == false) mediaPlayer?.start() }
    }

    fun release() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        runCatching { soundPool?.release() }
    }

    companion object {
        private const val BGM_VOLUME = 0.28f
        private const val BGM_DUCKED_VOLUME = 0.09f
    }
}
