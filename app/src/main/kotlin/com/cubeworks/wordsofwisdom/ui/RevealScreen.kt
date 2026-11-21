package com.cubeworks.wordsofwisdom.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordsofwisdom.core.ChoiceOption
import com.wordsofwisdom.core.PlayerView
import com.wordsofwisdom.core.TvSnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevealScreen(
    state: TvSnapshot,
    musicMuted: Boolean,
    onNext: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playersById = state.players.associateBy { it.id }
    val deltasById = state.roundDeltas.associateBy { it.playerId }
    val anyCorrect = state.roundDeltas.any { it.wasCorrect }

    TvSafeScaffold(
        roomCode = state.roomCode,
        roundLabel = "TURN ${state.turnNumber} OF ${state.totalTurns} · THE GRAND REVEAL",
        musicMuted = musicMuted,
        onToggleMute = onToggleMute,
        celebratory = anyCorrect,
        showConfetti = anyCorrect,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Left: Metaphor + Revealed Option Cards with Voter Avatars
            Column(
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Compact Metaphor Recap + Outcome Headline
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ParlorColors.VelvetCard.copy(alpha = 0.9f),
                    border = BorderStroke(1.5.dp, ParlorColors.Gold.copy(alpha = 0.55f)),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.statusBanner ?: "The true life lesson is revealed!",
                                color = ParlorColors.GoldBright,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.applauseCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                StatusPill(text = "👏 ${state.applauseCount} Bravo!", accent = ParlorColors.GoldBright)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "“${state.metaphorText.orEmpty()}” — ${state.sageAvatar?.emoji ?: ""} ${state.sageName.orEmpty()}",
                            color = ParlorColors.Cream.copy(alpha = 0.92f),
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Revealed Options List (sized so all 4 or 5 options fit cleanly without overflow)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
                ) {
                    state.options.forEach { opt ->
                        RevealOptionRow(
                            option = opt,
                            voters = opt.voterIds.mapNotNull { playersById[it] },
                        )
                    }
                }

                // Next Action Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvActionPill(
                        label = "Next Round → (OK)",
                        onClick = onNext,
                        gold = true,
                    )
                    Text(
                        text = "Seekers: tap 👏 Bravo on your phone!",
                        color = ParlorColors.MutedLavender,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Right: Live Round Scoreboard Panel
            Surface(
                modifier = Modifier
                    .weight(0.85f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                color = ParlorColors.VelvetMid.copy(alpha = 0.92f),
                border = BorderStroke(1.5.dp, ParlorColors.VelvetBorder),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "STANDINGS",
                            color = ParlorColors.GoldBright,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp,
                        )
                        CountdownRing(
                            remainingMs = state.remainingMs,
                            totalMs = state.totalPhaseMs,
                            size = 54.dp,
                        )
                    }

                    state.players.take(7).forEach { p ->
                        val delta = deltasById[p.id]
                        PlayerAvatarChip(
                            player = p,
                            highlightRole = if (p.id == state.sageId) (delta?.roleLabel ?: "Sage") else null,
                            deltaPoints = delta?.pointsEarned,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RevealOptionRow(
    option: ChoiceOption,
    voters: List<PlayerView>,
) {
    val isRight = option.isCorrect
    val scale by animateFloatAsState(if (isRight) 1.015f else 0.985f, tween(350), label = "rev_scale")
    val alpha by animateFloatAsState(if (isRight) 1f else 0.76f, tween(350), label = "rev_alpha")
    val accent = if (isRight) ParlorColors.Success else ParlorColors.optionAccent(option.id)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha),
        shape = RoundedCornerShape(18.dp),
        color = if (isRight) ParlorColors.SuccessBg else ParlorColors.VelvetCard.copy(alpha = 0.72f),
        border = BorderStroke(if (isRight) 2.5.dp else 1.dp, accent.copy(alpha = if (isRight) 1f else 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isRight) "✓" else option.id,
                    color = if (isRight) ParlorColors.InkDark else Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                if (isRight) {
                    Text(
                        text = "TRUE LIFE LESSON",
                        color = ParlorColors.Success,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp,
                    )
                }
                Text(
                    text = option.text,
                    color = ParlorColors.Cream,
                    fontSize = if (isRight) 17.sp else 15.sp,
                    lineHeight = if (isRight) 21.sp else 19.sp,
                    fontWeight = if (isRight) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Voter Avatars Pill Cluster
            if (voters.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    voters.forEach { v ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = ParlorColors.DeepNight.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, ParlorColors.parseHex(v.avatar.colorHex)),
                        ) {
                            Text(
                                text = "${v.avatar.emoji} ${v.name}",
                                color = ParlorColors.Cream,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
