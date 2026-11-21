package com.cubeworks.wordsofwisdom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubeworks.wordsofwisdom.TvJoinInfo
import com.wordsofwisdom.core.GamePhase
import com.wordsofwisdom.core.TvSnapshot

@Composable
fun WisdomTvApp(
    state: TvSnapshot,
    joinInfo: TvJoinInfo?,
    musicMuted: Boolean,
    confirmingEnd: Boolean,
    onRemoteOk: () -> Unit,
    onAddBot: () -> Unit,
    onRemoveBot: () -> Unit,
    onToggleMute: () -> Unit,
    onConfirmEnd: () -> Unit,
    onDismissEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WordsOfWisdomTheme {
        Box(modifier = modifier.fillMaxSize()) {
            // Render the active phase directly from the authoritative snapshot so no exiting
            // transition frame can ever display stale phase chrome paired with next-phase data.
            when (state.phase) {
                GamePhase.LOBBY -> LobbyScreen(
                    state = state,
                    joinInfo = joinInfo,
                    musicMuted = musicMuted,
                    onStart = onRemoteOk,
                    onAddBot = onAddBot,
                    onRemoveBot = onRemoveBot,
                    onToggleMute = onToggleMute,
                )
                GamePhase.WRITING -> WritingScreen(
                    state = state,
                    musicMuted = musicMuted,
                    onToggleMute = onToggleMute,
                )
                GamePhase.GUESSING -> GuessingScreen(
                    state = state,
                    musicMuted = musicMuted,
                    onToggleMute = onToggleMute,
                )
                GamePhase.REVEAL -> RevealScreen(
                    state = state,
                    musicMuted = musicMuted,
                    onNext = onRemoteOk,
                    onToggleMute = onToggleMute,
                )
                GamePhase.TIE_BREAKER -> TieBreakerScreen(
                    state = state,
                    musicMuted = musicMuted,
                    onToggleMute = onToggleMute,
                )
                GamePhase.PODIUM -> PodiumScreen(
                    state = state,
                    musicMuted = musicMuted,
                    onPlayAgain = onRemoteOk,
                    onToggleMute = onToggleMute,
                )
            }

            if (confirmingEnd) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAA0A0716)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = ParlorColors.VelvetCard,
                        border = BorderStroke(2.dp, ParlorColors.Gold),
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "End this party early?",
                                color = ParlorColors.GoldBright,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Serif,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Press OK to jump straight to the Podium, or Back to keep playing.",
                                color = ParlorColors.MutedLavender,
                                fontSize = 17.sp,
                            )
                            Spacer(Modifier.height(22.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                TvActionPill("Keep Playing (Back)", onClick = onDismissEnd, gold = false)
                                TvActionPill("End Game (OK)", onClick = onConfirmEnd, gold = true)
                            }
                        }
                    }
                }
            }
        }
    }
}
