package com.cubeworks.wordsofwisdom.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.cubeworks.wordsofwisdom.TvJoinInfo
import com.wordsofwisdom.core.ChoiceOption
import com.wordsofwisdom.core.PlayerView
import com.wordsofwisdom.core.TvSnapshot
import kotlinx.coroutines.delay

/**
 * 3-Beat Theatrical Reveal Screen:
 *  - Beat 1 (0.0s - 1.8s): Voter avatar badges pop onto options in suspense!
 *  - Beat 2 (1.8s - 2.5s): True Life Lesson illuminates emerald-gold + confetti burst!
 *  - Beat 3 (2.5s+): Score counters animate up and +points / Double-Down / Poet's Star pills appear!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RevealScreen(
    state: TvSnapshot,
    joinInfo: TvJoinInfo? = null,
    musicMuted: Boolean,
    voiceMuted: Boolean,
    onNext: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playersById = state.players.associateBy { it.id }
    val deltasById = state.roundDeltas.associateBy { it.playerId }
    val anyCorrect = state.roundDeltas.any { it.wasCorrect }

    var beat by remember(state.turnNumber, state.correctOptionId) { mutableIntStateOf(1) }
    LaunchedEffect(state.turnNumber, state.correctOptionId) {
        beat = 1
        delay(1_800L)
        beat = 2
        delay(650L)
        beat = 3
    }

    val revealAnswer = beat >= 2
    val revealScores = beat >= 3

    TvSafeScaffold(
        roomCode = state.roomCode,
        roundLabel = "TURN ${state.turnNumber}/${state.totalTurns} · REVEAL",
        musicMuted = musicMuted,
        onToggleMute = onToggleMute,
        voiceMuted = voiceMuted,
        onToggleVoice = onToggleVoice,
        joinInfo = joinInfo,
        showMiniQr = true,
        reactions = state.recentReactions,
        celebratory = revealAnswer && anyCorrect,
        showConfetti = revealAnswer && anyCorrect,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Left: Metaphor Recap + Staggered Option Cards + Footer
            Column(
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
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
                            val headline = if (!revealAnswer) {
                                "🥁 Drumroll… here's where everyone placed their bets!"
                            } else {
                                state.statusBanner ?: "The true life lesson is revealed!"
                            }
                            Text(
                                text = headline,
                                color = ParlorColors.GoldBright,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.applauseCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                StatusPill(text = "⭐ ${state.applauseCount} Poet's Star", accent = ParlorColors.GoldBright)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        val wcTag = state.activeWildcard?.let { " [${it.emoji} ${it.title}]" }.orEmpty()
                        Text(
                            text = "“${state.metaphorText.orEmpty()}” — ${state.sageAvatar?.emoji ?: ""} ${state.sageName.orEmpty()}$wcTag",
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

                // Option Rows (Beat 1 shows voters in suspense; Beat 2 illuminates Truth!)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                ) {
                    state.options.forEach { opt ->
                        RevealOptionRow(
                            option = opt,
                            voters = opt.voterIds.mapNotNull { playersById[it] },
                            revealed = revealAnswer,
                        )
                    }
                    val topSilly = state.sillyProverbs.firstOrNull()
                    if (topSilly != null && revealScores) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x28F5B731),
                            border = BorderStroke(1.dp, ParlorColors.Gold.copy(alpha = 0.6f)),
                        ) {
                            val authorTag = topSilly.authorName?.let { " — ${topSilly.authorAvatar?.emoji ?: "🤪"} $it (${topSilly.votes} votes)" } ?: " (vote on phones!)"
                            Text(
                                text = "🤪 Silly Proverb: “${topSilly.text}”$authorTag",
                                color = ParlorColors.GoldBright,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

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
                        text = "Seekers: tap ⭐ Poet's Star (+25) on your phone for great metaphors!",
                        color = ParlorColors.MutedLavender,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Right: Live Animated Round Scoreboard Panel
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
                            text = if (state.roundMultiplier > 1) "STANDINGS · 2×" else "STANDINGS",
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
                        val targetScore = if (revealScores) p.score else (p.score - (delta?.pointsEarned ?: 0)).coerceAtLeast(0)
                        val animatedScore by animateIntAsState(
                            targetValue = targetScore,
                            animationSpec = tween(650),
                            label = "score_roll_${p.id}",
                        )
                        val roleBadge = when {
                            p.id == state.sageId && revealScores && (delta?.wildcardBonus ?: 0) > 0 ->
                                "${delta?.roleLabel ?: "Sage"} · Wildcard +40"
                            p.id == state.sageId -> delta?.roleLabel ?: "Sage"
                            revealScores && delta?.usedDoubleDown == true -> delta.roleLabel
                            else -> null
                        }
                        PlayerAvatarChip(
                            player = p.copy(score = animatedScore),
                            highlightRole = roleBadge,
                            deltaPoints = if (revealScores) delta?.pointsEarned else null,
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
    revealed: Boolean,
) {
    val isRight = revealed && option.isCorrect
    val dimmed = revealed && !option.isCorrect
    val scale by animateFloatAsState(if (isRight) 1.015f else if (dimmed) 0.985f else 1f, tween(350), label = "rev_scale")
    val alpha by animateFloatAsState(if (dimmed) 0.72f else 1f, tween(350), label = "rev_alpha")
    val accent = if (isRight) ParlorColors.Success else ParlorColors.optionAccent(option.id)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha),
        shape = RoundedCornerShape(18.dp),
        color = if (isRight) ParlorColors.SuccessBg else ParlorColors.VelvetCard.copy(alpha = 0.78f),
        border = BorderStroke(if (isRight) 2.5.dp else 1.2.dp, accent.copy(alpha = if (isRight) 1f else 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
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

            if (voters.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    voters.forEach { v ->
                        val ddMark = if (v.doubledDownThisTurn) "🦉" else ""
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = ParlorColors.DeepNight.copy(alpha = 0.85f),
                            border = BorderStroke(
                                if (v.doubledDownThisTurn) 2.dp else 1.dp,
                                if (v.doubledDownThisTurn) ParlorColors.GoldBright else ParlorColors.parseHex(v.avatar.colorHex),
                            ),
                        ) {
                            Text(
                                text = "$ddMark${v.avatar.emoji} ${v.name}",
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
