package com.cubeworks.wordsofwisdom.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import com.wordsofwisdom.core.LiveReaction
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Firefly(
    val xFrac: Float,
    val yFrac: Float,
    val radius: Float,
    val phaseOffset: Float,
    val driftX: Float,
    val driftY: Float,
    val warm: Boolean,
)

private data class ConfettiPiece(
    val xFrac: Float,
    val startYFrac: Float,
    val speed: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val width: Float,
    val height: Float,
    val spinSpeed: Float,
    val color: Color,
)

/** Animated theatrical midnight-indigo parlor background with warm spotlights and drifting fireflies. */
@Composable
fun MidnightParlorBackdrop(
    celebratory: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val fireflies = remember {
        val rng = Random(2026)
        List(34) {
            Firefly(
                xFrac = rng.nextFloat(),
                yFrac = rng.nextFloat(),
                radius = 2.2f + rng.nextFloat() * 4.5f,
                phaseOffset = rng.nextFloat() * (2f * PI.toFloat()),
                driftX = 14f + rng.nextFloat() * 28f,
                driftY = 10f + rng.nextFloat() * 24f,
                warm = rng.nextFloat() > 0.35f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "parlor_bg")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "firefly_phase",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF191136),
                    ParlorColors.DeepNight,
                    Color(0xFF0A0716),
                ),
            ),
        )

        // Top theatrical warm golden spotlight cone
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ParlorColors.Gold.copy(alpha = if (celebratory) 0.22f else 0.13f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, -size.height * 0.1f),
                radius = size.width * 0.55f,
            ),
            radius = size.width * 0.55f,
            center = Offset(size.width * 0.5f, -size.height * 0.1f),
        )

        // Subtle violet side ambiance
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x307C3AED), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * 0.85f),
                radius = size.width * 0.42f,
            ),
            radius = size.width * 0.42f,
            center = Offset(size.width * 0.08f, size.height * 0.85f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x28F59E0B), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * 0.82f),
                radius = size.width * 0.38f,
            ),
            radius = size.width * 0.38f,
            center = Offset(size.width * 0.92f, size.height * 0.82f),
        )

        // Drifting fireflies & stardust
        for (f in fireflies) {
            val angle = phase + f.phaseOffset
            val x = f.xFrac * size.width + cos(angle) * f.driftX
            val y = f.yFrac * size.height + sin(angle * 0.8f) * f.driftY
            val twinkle = 0.28f + 0.52f * ((sin(angle * 2f) + 1f) * 0.5f)
            val tint = if (f.warm) ParlorColors.GoldBright else Color(0xFFB794F4)
            drawCircle(
                color = tint.copy(alpha = twinkle * 0.35f),
                radius = f.radius * 3.0f,
                center = Offset(x, y),
            )
            drawCircle(
                color = tint.copy(alpha = twinkle),
                radius = f.radius,
                center = Offset(x, y),
            )
        }
    }
}

/** Smooth continuous festive confetti overlay for Reveal hits and the Winner Podium. */
@Composable
fun ConfettiOverlay(
    active: Boolean,
    intensity: Int = 52,
    modifier: Modifier = Modifier,
) {
    if (!active) return
    val pieces = remember(intensity) {
        val rng = Random(777)
        val palette = listOf(
            ParlorColors.Gold,
            ParlorColors.GoldBright,
            Color(0xFFE84855),
            Color(0xFF3A86FF),
            Color(0xFF2EC486),
            Color(0xFF9B5DE5),
            Color(0xFFFF8FAB),
        )
        List(intensity) {
            ConfettiPiece(
                xFrac = rng.nextFloat(),
                startYFrac = rng.nextFloat(),
                speed = 0.65f + rng.nextFloat() * 0.9f,
                swayAmp = 14f + rng.nextFloat() * 30f,
                swayFreq = 1.5f + rng.nextFloat() * 2.5f,
                width = 8f + rng.nextFloat() * 10f,
                height = 5f + rng.nextFloat() * 7f,
                spinSpeed = 120f + rng.nextFloat() * 240f,
                color = palette[rng.nextInt(palette.size)],
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confetti")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confetti_fall",
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        for (p in pieces) {
            val yFrac = (p.startYFrac + progress * p.speed) % 1.15f - 0.08f
            val y = yFrac * size.height
            val x = p.xFrac * size.width + sin(progress * 2f * PI.toFloat() * p.swayFreq + p.startYFrac * 10f) * p.swayAmp
            val angle = (progress * p.spinSpeed + p.startYFrac * 360f) % 360f
            rotate(degrees = angle, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = 0.88f),
                    topLeft = Offset(x - p.width / 2f, y - p.height / 2f),
                    size = Size(p.width, p.height),
                )
            }
        }
    }
}

/** Floating live couch reactions anchored cleanly along the bottom-right stage edge so text is never occluded. */
@Composable
fun FloatingReactionsOverlay(
    reactions: List<LiveReaction>,
    modifier: Modifier = Modifier,
) {
    val active = remember { mutableStateListOf<LiveReaction>() }
    val seen = remember { HashSet<Long>() }

    LaunchedEffect(reactions) {
        for (r in reactions) {
            if (seen.add(r.seq)) {
                active.add(r)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        active.forEach { reaction ->
            FloatingReactionBubble(
                reaction = reaction,
                onFinished = { active.remove(reaction) },
            )
        }
    }
}

@Composable
private fun FloatingReactionBubble(
    reaction: LiveReaction,
    onFinished: () -> Unit,
) {
    val progress = remember(reaction.seq) { Animatable(0f) }
    val laneShift = remember(reaction.seq) { -((reaction.seq * 53L) % 160L).toFloat() }

    LaunchedEffect(reaction.seq) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 2_100, easing = LinearEasing))
        delay(40)
        onFinished()
    }

    val p = progress.value
    val yOffset = -(p * 110f)
    val xSway = laneShift + sin(p * 6f) * 10f
    val alpha = when {
        p < 0.14f -> p / 0.14f
        p > 0.72f -> ((1f - p) / 0.28f).coerceIn(0f, 1f)
        else -> 0.94f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 44.dp, bottom = 22.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Surface(
            modifier = Modifier
                .offset(x = xSway.dp, y = yOffset.dp)
                .alpha(alpha),
            shape = RoundedCornerShape(999.dp),
            color = ParlorColors.VelvetCard.copy(alpha = 0.92f),
            border = BorderStroke(1.5.dp, ParlorColors.Gold),
            shadowElevation = 6.dp,
        ) {
            Text(
                text = "${reaction.emoji} ${reaction.senderName}",
                color = ParlorColors.GoldBright,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}
