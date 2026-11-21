package com.cubeworks.wordsofwisdom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wordsofwisdom.core.PlayerView
import com.wordsofwisdom.core.TvSnapshot
import kotlinx.coroutines.delay

@Composable
fun PodiumScreen(
    state: TvSnapshot,
    musicMuted: Boolean,
    voiceMuted: Boolean,
    onPlayAgain: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val top3 = state.players.take(3)
    var highlightIdx by remember(state.highlights.size) { mutableIntStateOf(0) }

    LaunchedEffect(state.highlights.size) {
        if (state.highlights.size > 1) {
            while (true) {
                delay(6_000L)
                highlightIdx = (highlightIdx + 1) % state.highlights.size
            }
        }
    }

    TvSafeScaffold(
        roomCode = state.roomCode,
        roundLabel = "🏆 GRAND PARLOR FINALE",
        musicMuted = musicMuted,
        onToggleMute = onToggleMute,
        voiceMuted = voiceMuted,
        onToggleVoice = onToggleVoice,
        reactions = state.recentReactions,
        celebratory = true,
        showConfetti = true,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Left: Animated 3-Tier Podium + Play Again CTA
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🏆 GRAND SAGE PODIUM",
                        color = ParlorColors.GoldBright,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        maxLines = 1,
                    )
                    state.players.firstOrNull()?.let { champ ->
                        Text(
                            text = "${champ.avatar.emoji} ${champ.name} reigns supreme with ${champ.score} points!",
                            color = ParlorColors.Cream,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }

                // 3-Tier Staircase Podium (2nd - 1st - 3rd) with clear visual hierarchy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    top3.getOrNull(1)?.let { second ->
                        PodiumPillar(
                            player = second,
                            medal = "🥈",
                            rankText = "2ND",
                            pillarHeight = 124.dp,
                            pillarColor = ParlorColors.Silver,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    top3.getOrNull(0)?.let { first ->
                        PodiumPillar(
                            player = first,
                            medal = "👑",
                            rankText = "1ST",
                            pillarHeight = 166.dp,
                            pillarColor = ParlorColors.Gold,
                            modifier = Modifier.weight(1.12f),
                        )
                    }
                    top3.getOrNull(2)?.let { third ->
                        PodiumPillar(
                            player = third,
                            medal = "🥉",
                            rankText = "3RD",
                            pillarHeight = 104.dp,
                            pillarColor = ParlorColors.Bronze,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                TvActionPill(
                    label = "🎉 Play Again (OK)",
                    onClick = onPlayAgain,
                    gold = true,
                )
            }

            // Right: Superlative Awards + Hall of Metaphors Carousel
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Superlative Awards Panel
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = ParlorColors.VelvetCard.copy(alpha = 0.92f),
                    border = BorderStroke(1.5.dp, ParlorColors.Gold.copy(alpha = 0.55f)),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "PARLOR HONORS",
                            color = ParlorColors.GoldBright,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp,
                        )
                        state.awards.take(3).forEach { award ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(text = award.emoji, fontSize = 26.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${award.title} — ${award.avatar.emoji} ${award.playerName}",
                                        color = ParlorColors.Cream,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = award.subtitle,
                                        color = ParlorColors.MutedLavender,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // Hall of Metaphors Highlight Carousel
                val highlight = state.highlights.getOrNull(highlightIdx)
                if (highlight != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.Transparent,
                        border = BorderStroke(2.dp, ParlorColors.Gold),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(ParlorColors.ParchmentLight, ParlorColors.ParchmentWarm),
                                    ),
                                )
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "📜 HALL OF METAPHORS (${highlightIdx + 1}/${state.highlights.size})",
                                    color = Color(0xFF7D5000),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp,
                                )
                                if (highlight.applauseCount > 0) {
                                    Text(
                                        text = "👏 ${highlight.applauseCount}",
                                        color = Color(0xFF7D5000),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }

                            Text(
                                text = "“${highlight.metaphor}”",
                                color = ParlorColors.InkDark,
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                                fontFamily = FontFamily.Serif,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Original: ${highlight.originalLesson}",
                                    color = Color(0xFF3E2D5C),
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "— ${highlight.sageAvatar.emoji} ${highlight.sageName} · ${highlight.correctGuessers}/${highlight.totalSeekers} decoded",
                                    color = Color(0xFF6B4C12),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumPillar(
    player: PlayerView,
    medal: String,
    rankText: String,
    pillarHeight: Dp,
    pillarColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(text = medal, fontSize = 26.sp)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ParlorColors.DeepNight)
                .border(2.5.dp, pillarColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = player.avatar.emoji, fontSize = 30.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = player.name,
            color = ParlorColors.Cream,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(pillarHeight),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = pillarColor.copy(alpha = 0.24f),
            border = BorderStroke(2.dp, pillarColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = rankText,
                    color = pillarColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${player.score}",
                    color = ParlorColors.Cream,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "PTS",
                    color = ParlorColors.MutedLavender,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
