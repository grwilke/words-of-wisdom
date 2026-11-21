package com.cubeworks.wordsofwisdom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubeworks.wordsofwisdom.QrMatrix
import com.cubeworks.wordsofwisdom.TvJoinInfo
import com.wordsofwisdom.core.PlayerAvatar
import com.wordsofwisdom.core.PlayerView
import kotlin.math.ceil

/** Root TV scaffold respecting 5% overscan safe margins and rendering a clean header strip. */
@Composable
fun TvSafeScaffold(
    roomCode: String,
    roundLabel: String?,
    musicMuted: Boolean,
    onToggleMute: () -> Unit,
    celebratory: Boolean = false,
    showConfetti: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        MidnightParlorBackdrop(celebratory = celebratory)
        ConfettiOverlay(active = showConfetti)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 44.dp, vertical = 26.dp),
        ) {
            // Compact Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "🦉 Words of Wisdom",
                        color = ParlorColors.GoldBright,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Serif,
                    )
                    if (roundLabel != null) {
                        StatusPill(text = roundLabel, accent = ParlorColors.Gold)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusPill(text = "ROOM $roomCode", accent = ParlorColors.MutedLavender)
                    TvActionPill(
                        label = if (musicMuted) "🔇 Muted" else "🎵 Music On",
                        onClick = onToggleMute,
                        gold = false,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    accent: Color = ParlorColors.Gold,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            color = if (accent == ParlorColors.MutedLavender) ParlorColors.Cream else accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** TV-remote focusable button with crisp focus halo and spring scale. */
@Composable
fun TvActionPill(
    label: String,
    onClick: () -> Unit,
    gold: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(140), label = "btn_scale")
    val bg = when {
        !enabled -> Color(0x33FFFFFF)
        gold -> ParlorColors.Gold
        focused -> ParlorColors.VelvetCard
        else -> Color(0x28FFFFFF)
    }
    val fg = when {
        !enabled -> ParlorColors.MutedLavender
        gold -> ParlorColors.InkDark
        else -> ParlorColors.Cream
    }
    val border = when {
        focused -> BorderStroke(2.5.dp, ParlorColors.GoldBright)
        gold -> BorderStroke(1.dp, ParlorColors.GoldBright)
        else -> BorderStroke(1.dp, Color(0x40FFFFFF))
    }

    Surface(
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = border,
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/** Crisp high-contrast QR code card with quiet zone, room code, and LAN URL fallback. */
@Composable
fun QrJoinCard(
    joinInfo: TvJoinInfo?,
    roomCode: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = ParlorColors.VelvetCard.copy(alpha = 0.94f),
        border = BorderStroke(2.dp, ParlorColors.Gold.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "JOIN WITH YOUR PHONE",
                color = ParlorColors.GoldBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(12.dp))

            if (joinInfo != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier.size(214.dp),
                ) {
                    QrCodeCanvas(
                        qr = joinInfo.qr,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Scan QR or open in browser:",
                    color = ParlorColors.MutedLavender,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ParlorColors.DeepNight,
                    border = BorderStroke(1.dp, ParlorColors.Gold.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = joinInfo.displayAddress,
                        color = ParlorColors.GoldBright,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(214.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ParlorColors.DeepNight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Connect TV to Wi-Fi\nto generate QR code…",
                        color = ParlorColors.MutedLavender,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Same Wi-Fi · Zero App Install · Room $roomCode",
                color = ParlorColors.MutedLavender,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun QrCodeCanvas(qr: QrMatrix, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cell = size.width / qr.size.toFloat()
        for (y in 0 until qr.size) {
            for (x in 0 until qr.size) {
                if (qr.isDark(x, y)) {
                    drawRect(
                        color = Color(0xFF140E26),
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.5f, cell + 0.5f),
                    )
                }
            }
        }
    }
}

/** Circular animated countdown timer ring with urgency color pulse in final 10 seconds. */
@Composable
fun CountdownRing(
    remainingMs: Long,
    totalMs: Long,
    size: Dp = 84.dp,
    modifier: Modifier = Modifier,
) {
    val secs = ceil(remainingMs.coerceAtLeast(0L) / 1000.0).toInt()
    val fraction = if (totalMs > 0L) (remainingMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
    val urgent = secs in 1..10
    val ringColor by animateColorAsState(
        targetValue = if (urgent) ParlorColors.Danger else ParlorColors.Gold,
        animationSpec = tween(250),
        label = "ring_color",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = Color(0x30FFFFFF),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                style = stroke,
            )
        }
        Text(
            text = "${secs}s",
            color = if (urgent) Color(0xFFFFB4BA) else ParlorColors.Cream,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

/** Expressive couch-legible player badge with custom avatar ring, score, streak, and status dot. */
@Composable
fun PlayerAvatarChip(
    player: PlayerView,
    highlightRole: String? = null,
    showVotedCheck: Boolean = false,
    deltaPoints: Int? = null,
    modifier: Modifier = Modifier,
) {
    val accent = ParlorColors.parseHex(player.avatar.colorHex)
    val bg = when {
        highlightRole != null -> accent.copy(alpha = 0.24f)
        !player.connected -> Color(0x14FFFFFF)
        else -> ParlorColors.VelvetCard.copy(alpha = 0.88f)
    }
    val border = when {
        highlightRole != null -> BorderStroke(2.dp, ParlorColors.Gold)
        showVotedCheck && player.hasActed -> BorderStroke(2.dp, ParlorColors.Success)
        else -> BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .requiredSize(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.28f))
                    .border(2.dp, accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = player.avatar.emoji, fontSize = 18.sp)
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = player.name,
                        color = if (player.connected) ParlorColors.Cream else ParlorColors.MutedLavender,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (player.isHost) Text("👑", fontSize = 13.sp)
                    if (player.isBot) Text("🤖", fontSize = 12.sp)
                }
                val sub = when {
                    highlightRole != null -> highlightRole
                    showVotedCheck && player.hasActed -> "Locked in ✓"
                    showVotedCheck -> "Thinking…"
                    player.streak >= 2 -> "${player.score} pts · 🔥${player.streak}"
                    else -> "${player.score} pts"
                }
                Text(
                    text = sub,
                    color = if (showVotedCheck && player.hasActed) ParlorColors.Success else ParlorColors.MutedLavender,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }

            if (deltaPoints != null && deltaPoints > 0) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ParlorColors.Success,
                ) {
                    Text(
                        text = "+$deltaPoints",
                        color = ParlorColors.InkDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

/** Illuminated warm-parchment scroll card presenting the Sage's metaphor with theatrical poise. */
@Composable
fun IlluminatedScrollCard(
    metaphor: String,
    authorName: String,
    authorAvatar: PlayerAvatar?,
    themeLabel: String?,
    kickerOverride: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(2.5.dp, ParlorColors.Gold),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(ParlorColors.ParchmentLight, ParlorColors.ParchmentWarm),
                    ),
                )
                .padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = kickerOverride ?: "✨ ${authorAvatar?.emoji ?: "🪶"} ${authorName.uppercase()}'S METAPHOR${themeLabel?.let { "  ·  ${it.uppercase()}" } ?: ""} ✨",
                color = Color(0xFF7D5000),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "“$metaphor”",
                color = ParlorColors.InkDark,
                fontSize = 25.sp,
                lineHeight = 32.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
