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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordsofwisdom.core.TvSnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WritingScreen(
    state: TvSnapshot,
    musicMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        roundLabel = "TURN ${state.turnNumber} OF ${state.totalTurns} · SAGE AT THE QUILL",
        musicMuted = musicMuted,
        onToggleMute = onToggleMute,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Center Stage: Sage Spotlight + Countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = "THEME: ${(state.currentTheme ?: "WISDOM").uppercase()}",
                    accent = ParlorColors.GoldBright,
                )
                CountdownRing(
                    remainingMs = state.remainingMs,
                    totalMs = state.totalPhaseMs,
                    size = 86.dp,
                )
            }

            // Center Theatrical Spotlight Card
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
                                .background(
                                    Brush.radialGradient(
                                        listOf(accent.copy(alpha = 0.45f), accent.copy(alpha = 0.0f)),
                                    ),
                                ),
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

                    // Live Quill Ink Energy Meter (shows typing activity without leaking a single letter!)
                    Column(
                        modifier = Modifier.fillMaxWidth(0.72f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
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
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(ParlorColors.GoldDeep, ParlorColors.Gold, ParlorColors.GoldBright),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }

            // Bottom Player Standings Strip
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
