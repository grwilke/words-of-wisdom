package com.cubeworks.wordsofwisdom.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubeworks.wordsofwisdom.R
import com.cubeworks.wordsofwisdom.TvJoinInfo
import com.wordsofwisdom.core.GameMode
import com.wordsofwisdom.core.TvSnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LobbyScreen(
    state: TvSnapshot,
    joinInfo: TvJoinInfo?,
    musicMuted: Boolean,
    voiceMuted: Boolean,
    onStart: () -> Unit,
    onAddBot: () -> Unit,
    onRemoveBot: () -> Unit,
    onToggleMode: () -> Unit,
    onCyclePack: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val floatAnim = rememberInfiniteTransition(label = "owl_float")
    val bobY by floatAnim.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob_y",
    )

    TvSafeScaffold(
        roomCode = state.roomCode,
        roundLabel = "PARLOR LOBBY",
        musicMuted = musicMuted,
        onToggleMute = onToggleMute,
        voiceMuted = voiceMuted,
        onToggleVoice = onToggleVoice,
        reactions = state.recentReactions,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Hero + How to Play + Joined Players Stage
            Column(
                modifier = Modifier
                    .weight(1.48f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Brand Banner Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.owl_mascot),
                        contentDescription = "Wise Owl Mascot",
                        modifier = Modifier
                            .size(84.dp)
                            .offset(y = bobY.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                    Column {
                        Text(
                            text = "Words of Wisdom",
                            color = ParlorColors.GoldBright,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Serif,
                            maxLines = 1,
                        )
                        Text(
                            text = "& Life Lessons · The Metaphor Party Game",
                            color = ParlorColors.MutedLavender,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }

                // Compact Uniform-Height 3-Step Visual Rules Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(94.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RuleStepCard("1", "🪶 Rewrite", "Turn a secret lesson into a metaphor.", Modifier.weight(1f).fillMaxHeight())
                    RuleStepCard("2", "🔮 Decode", "Spot the real lesson among decoys.", Modifier.weight(1f).fillMaxHeight())
                    RuleStepCard("3", "🏆 Score", "Points for right guesses & clear clues!", Modifier.weight(1f).fillMaxHeight())
                }

                // Joined Players Stage (generous flex height so 4-10 guests never clip!)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    color = ParlorColors.VelvetMid.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, ParlorColors.VelvetBorder),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "PARLOR GUESTS (${state.players.size} / 10)",
                                color = ParlorColors.GoldBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp,
                            )
                            Text(
                                text = "${state.config.mode.label} · ${state.config.pack.label} · ${state.config.length.label}",
                                color = ParlorColors.MutedLavender,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        if (state.players.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Scan the QR code on the right with your phone — or tap + Add AI Bot!",
                                    color = ParlorColors.MutedLavender,
                                    fontSize = 16.sp,
                                )
                            }
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                state.players.forEach { p ->
                                    PlayerAvatarChip(player = p)
                                }
                            }
                        }
                    }
                }

                // Bottom TV Remote Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TvActionPill(
                        label = if (state.canStart) "▶ Start (OK)" else "Need 2+ Players",
                        onClick = onStart,
                        gold = true,
                        enabled = state.canStart,
                    )
                    TvActionPill(
                        label = if (state.config.mode == GameMode.PARTY_SPRINT) "⚡ Sprint" else "🪶 Turns",
                        onClick = onToggleMode,
                        gold = false,
                    )
                    TvActionPill(
                        label = "📚 ${state.config.pack.label}",
                        onClick = onCyclePack,
                        gold = false,
                    )
                    TvActionPill(
                        label = "+ Bot",
                        onClick = onAddBot,
                        gold = false,
                        enabled = state.players.size < 10,
                    )
                    if (state.players.any { it.isBot }) {
                        TvActionPill(
                            label = "− Bot",
                            onClick = onRemoveBot,
                            gold = false,
                        )
                    }
                }
            }

            // Right QR Join Card
            QrJoinCard(
                joinInfo = joinInfo,
                roomCode = state.roomCode,
                modifier = Modifier.width(310.dp),
            )
        }
    }
}

@Composable
private fun RuleStepCard(
    num: String,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ParlorColors.VelvetCard.copy(alpha = 0.78f),
        border = BorderStroke(1.dp, ParlorColors.VelvetBorder),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = "$num. $title",
                color = ParlorColors.GoldBright,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                color = ParlorColors.Cream.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
            )
        }
    }
}
