package com.cubeworks.wordsofwisdom

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cubeworks.wordsofwisdom.ui.WisdomTvApp

class MainActivity : ComponentActivity() {

    private val vm: PartyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onBackPressedDispatcher.addCallback(this) {
            if (!vm.onRemoteBack()) finish()
        }

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            val join by vm.joinInfo.collectAsStateWithLifecycle()
            val muted by vm.musicMuted.collectAsStateWithLifecycle()
            val voiceMuted by vm.voiceMuted.collectAsStateWithLifecycle()
            val confirmingEnd by vm.confirmingEnd.collectAsStateWithLifecycle()

            WisdomTvApp(
                state = state,
                joinInfo = join,
                musicMuted = muted,
                voiceMuted = voiceMuted,
                confirmingEnd = confirmingEnd,
                onRemoteOk = vm::onRemoteOk,
                onAddBot = vm::onAddBot,
                onRemoveBot = vm::onRemoveBot,
                onToggleMode = vm::onToggleMode,
                onCyclePack = vm::onCyclePack,
                onToggleMute = vm::toggleMute,
                onToggleVoice = vm::toggleVoice,
                onConfirmEnd = vm::confirmEndGame,
                onDismissEnd = vm::dismissEndDialog,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResumeAudio()
    }

    override fun onPause() {
        super.onPause()
        vm.onPauseAudio()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_M -> {
                    vm.toggleMute()
                    return true
                }
                KeyEvent.KEYCODE_V -> {
                    vm.toggleVoice()
                    return true
                }
                KeyEvent.KEYCODE_MENU -> {
                    vm.onAddBot()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
