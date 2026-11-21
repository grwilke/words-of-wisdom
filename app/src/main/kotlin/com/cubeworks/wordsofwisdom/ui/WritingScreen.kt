package com.cubeworks.wordsofwisdom.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubeworks.wordsofwisdom.TvJoinInfo
import com.wordsofwisdom.core.SprintWriterStatus
import com.wordsofwisdom.core.TvSnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WritingScreen(
    state: TvSnapshot,
    joinInfo: TvJoinInfo? = null,
    musicMuted: Boolean,
    voiceMuted: Boolean,
    onToggleMute: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isSimultaneousWriting) {
        PartySprintWritingScreen(state, joinInfo, musicMuted, voiceMuted, onToggleMute, onToggleVoice, modifier)
        return
    }

    val pulse = rememberInfiniteTransition(label = "quill_pulse")
    val haloScale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo_scale",
    )
    val quillTilt by pulse.animateFloat(
        initialValue = -10f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "quill_tilt",
    )

    val typingProgress by animateFloatAsState(
        targetValue = (state.sageTypingChars / 90f).coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "typing_energy",
    )

    val accent = state.sageAvatar?.let { ParlorColors.parseHex(it.colorHex) } ?: ParlorColors.Gold

    TvSafeScaffold(
        roomCode = state.roomCode,
        roundLabel = "TURN ${state.turnNumber}/${state.totalTurns} · WRITING",
        musicMuted = musicMuted,
        onToggleMute = onToggleMute,
        voiceMuted = voiceMuted,
        onToggleVoice = onToggleVoice,
        joinInfo = joinInfo,
        showMiniQr = true,
        reactions = state.recentReactions,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusPill(text = "THEME: ${(state.currentTheme ?: "WISDOM").uppercase()}", accent = ParlorColors.GoldBright)
                    if (state.roundMultiplier > 1) {
                        StatusPill(text = "🔥 2× FINAL ROUND", accent = ParlorColors.Danger)
                    }
                }
                CountdownRing(remainingMs = state.remainingMs, totalMs = state.totalPhaseMs, size = 86.dp)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(0.84f),
                shape = RoundedCornerShape(26.dp),
                color = ParlorColors.VelvetCard.copy(alpha = 0.9f),
                border = BorderStroke(2.dp, ParlorColors.Gold.copy(alpha = 0.6f)),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(haloScale)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.0f)))),
                        )
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(ParlorColors.DeepNight)
                                .border(3.dp, accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = state.sageAvatar?.emoji ?: "🦉", fontSize = 36.sp)
                        }
                        Text(
                            text = "🪶",
                            fontSize = 26.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .rotate(quillTilt),
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${state.sageName ?: "The Sage"} is crafting a metaphor…",
                        color = ParlorColors.GoldBright,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = state.statusBanner ?: "No peeking! The secret life lesson is glowing on the Sage's phone.",
                        color = ParlorColors.MutedLavender,
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(14.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(0.72f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = if (state.sageTypingChars > 0) "✨ Quill is moving!" else "💭 Pondering the wisdom…",
                                color = ParlorColors.GoldBright,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (state.sageTypingChars > 0) "${state.sageTypingChars} chars brewed" else "Waiting for ink…",
                                color = ParlorColors.MutedLavender,
                                fontSize = 14.sp,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(ParlorColors.DeepNight),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(typingProgress.coerceAtLeast(0.05f))
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Brush.horizontalGradient(listOf(ParlorColors.GoldDeep, ParlorColors.Gold, ParlorColors.GoldBright))),
                            )
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.players.forEach { p ->
                    PlayerAvatarChip(
                        player = p,
                        highlightRole = if (p.id == state.sageId) "🪶 Active Sage" else null,
                    )
                }
            }
        }
    }
}

/** Simultaneous "Party Sprint" TV board showing every player crafting their metaphor at once! */
@Composable
private fun PartySprintWritingScreen(
    state: TvSnapshot,
    joinInfo: TvJoinInfo? = null,
    musicMuted: Boolean,
    voiceMuted: Boolean,
    onToggleMute: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sealedCount = state.sprintWriters.count { it.submitted }
    val totalWriters = state.sprintWriters.size.coerceAtLeast(1)

    TvSafeScaffold(
        roomCode = state.roomCode,
        roundLabel = "⚡ SPRINT · ROUND ${state.cycleNumber}/${state.totalCycles}",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "⚡ ALL SAGES AT THE QUILL!",
                            color = ParlorColors.GoldBright,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                        )
                        if (state.roundMultiplier > 1) {
                            StatusPill(text = "🔥 2× FINAL ROUND", accent = ParlorColors.Danger)
                        }
                    }
                    Text(
                        text = "Check your phone — everyone writes a secret metaphor at once! ($sealedCount / $totalWriters scrolls sealed)",
                        color = ParlorColors.MutedLavender,
                        fontSize = 17.sp,
                    )
                }
                CountdownRing(remainingMs = state.remainingMs, totalMs = state.totalPhaseMs, size = 90.dp)
            }

            // 2-Column Live Writing Progress Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                state.sprintWriters.chunked(2).forEach { rowWriters ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        rowWriters.forEach { writer ->
                            SprintWriterCard(writer = writer, modifier = Modifier.weight(1f))
                        }
                        if (rowWriters.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Text(
                text = "💡 Tip: Finish early? Toss live reactions onto the TV while your friends finish!",
                color = ParlorColors.GoldBright.copy(alpha = 0.9f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SprintWriterCard(
    writer: SprintWriterStatus,
    modifier: Modifier = Modifier,
) {
    val accent = ParlorColors.parseHex(writer.avatar.colorHex)
    val progress by animateFloatAsState(
        targetValue = if (writer.submitted) 1f else (writer.typingChars / 85f).coerceIn(0.06f, 0.92f),
        animationSpec = tween(250),
        label = "sprint_bar",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = if (writer.submitted) ParlorColors.SuccessBg else ParlorColors.VelvetCard.copy(alpha = 0.9f),
        border = BorderStroke(
            width = if (writer.submitted) 2.5.dp else 1.5.dp,
            color = if (writer.submitted) ParlorColors.Success else accent.copy(alpha = 0.6f),
        ),
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
                    .requiredSize(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.28f))
                    .border(2.5.dp, if (writer.submitted) ParlorColors.Success else accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = writer.avatar.emoji, fontSize = 24.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = writer.name,
                        color = ParlorColors.Cream,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (writer.submitted) "✨ Scroll Sealed ✓" else if (writer.typingChars > 0) "🪶 Writing…" else "💭 Pondering…",
                        color = if (writer.submitted) ParlorColors.Success else ParlorColors.GoldBright,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ParlorColors.DeepNight),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (writer.submitted) {
                                    Brush.horizontalGradient(listOf(ParlorColors.Success, Color(0xFF7EF2BE)))
                                } else {
                                    Brush.horizontalGradient(listOf(ParlorColors.GoldDeep, ParlorColors.GoldBright))
                                },
                            ),
                    )
                }
            }
        }
    }
}
