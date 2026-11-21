package com.cubeworks.wordsofwisdom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubeworks.wordsofwisdom.TvJoinInfo
import com.wordsofwisdom.core.ChoiceOption
import com.wordsofwisdom.core.TvSnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuessingScreen(
    state: TvSnapshot,
    joinInfo: TvJoinInfo? = null,
    musicMuted: Boolean,
    voiceMuted: Boolean,
    onToggleMute: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSafeScaffold(
        roomCode = state.roomCode,
        roundLabel = "TURN ${state.turnNumber}/${state.totalTurns} · DECODE",
        musicMuted = musicMuted,
        onToggleMute = onToggleMute,
        voiceMuted = voiceMuted,
        onToggleVoice = onToggleVoice,
        joinInfo = joinInfo,
        showMiniQr = true,
        reactions = state.recentReactions,
        celebratory = state.roundMultiplier > 1,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Row: Illuminated Metaphor Scroll + Countdown Ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IlluminatedScrollCard(
                    metaphor = state.metaphorText.orEmpty(),
                    authorName = state.sageName ?: "Sage",
                    authorAvatar = state.sageAvatar,
                    themeLabel = state.currentTheme,
                    activeWildcard = state.activeWildcard,
                    roundMultiplier = state.roundMultiplier,
                    modifier = Modifier.weight(1f),
                )
                CountdownRing(
                    remainingMs = state.remainingMs,
                    totalMs = state.totalPhaseMs,
                    size = 94.dp,
                )
            }

            // Middle: Crisp 2-Column Color-Coded Option Grid (supports 4 or 5 options cleanly!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                val rows = state.options.chunked(2)
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rowItems.forEach { opt ->
                            GuessOptionCard(
                                option = opt,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Bottom: Live Seeker Vote Status Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    state.players.forEach { p ->
                        val isSage = p.id == state.sageId
                        PlayerAvatarChip(
                            player = p,
                            highlightRole = if (isSage) "🪶 Sage" else null,
                            showVotedCheck = !isSage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GuessOptionCard(
    option: ChoiceOption,
    modifier: Modifier = Modifier,
) {
    val accent = ParlorColors.optionAccent(option.id)
    val badgeText = if (option.id == "C" || option.id == "D") ParlorColors.InkDark else Color.White

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = ParlorColors.VelvetCard.copy(alpha = 0.92f),
        border = BorderStroke(2.dp, accent.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.id,
                    color = badgeText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = option.text,
                color = ParlorColors.Cream,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
