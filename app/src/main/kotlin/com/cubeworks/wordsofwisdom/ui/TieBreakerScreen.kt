package com.cubeworks.wordsofwisdom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordsofwisdom.core.TvSnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TieBreakerScreen(
    state: TvSnapshot,
    musicMuted: Boolean,
    voiceMuted: Boolean,
    onToggleMute: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contenders = state.players.filter { it.id in state.tieBreakerContenderIds }

    TvSafeScaffold(
        roomCode = state.roomCode,
        roundLabel = "⚡ SUDDEN DEATH TIE-BREAKER",
        musicMuted = musicMuted,
        onToggleMute = onToggleMute,
        voiceMuted = voiceMuted,
        onToggleVoice = onToggleVoice,
        reactions = state.recentReactions,
        celebratory = true,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚡ SUDDEN DEATH WISDOM DUEL!",
                        color = ParlorColors.GoldBright,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        text = state.statusBanner ?: "First tied leader to decode the bonus metaphor wins the crown!",
                        color = ParlorColors.Cream,
                        fontSize = 17.sp,
                    )
                }
                CountdownRing(
                    remainingMs = state.remainingMs,
                    totalMs = state.totalPhaseMs,
                    size = 86.dp,
                )
            }

            Spacer(Modifier.height(10.dp))

            IlluminatedScrollCard(
                metaphor = state.metaphorText.orEmpty(),
                authorName = "The Parlor",
                authorAvatar = null,
                themeLabel = state.currentTheme,
                kickerOverride = "⚡ GOLDEN DUEL BONUS METAPHOR ⚡",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                state.options.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rowItems.forEach { opt ->
                            GuessOptionCard(option = opt, modifier = Modifier.weight(1f))
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                contenders.forEach { p ->
                    PlayerAvatarChip(
                        player = p,
                        highlightRole = "⚡ Duelist",
                        showVotedCheck = true,
                    )
                }
            }
        }
    }
}
