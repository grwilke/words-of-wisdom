package com.cubeworks.wordsofwisdom.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ParlorColors {
    val DeepNight = Color(0xFF0F0B20)
    val VelvetMid = Color(0xFF1A1336)
    val VelvetCard = Color(0xFF231A47)
    val VelvetBorder = Color(0x4DF5B731)

    val Gold = Color(0xFFF5B731)
    val GoldBright = Color(0xFFFFE28A)
    val GoldDeep = Color(0xFFC48612)

    val ParchmentLight = Color(0xFFFFF8E7)
    val ParchmentWarm = Color(0xFFF7E4B5)
    val InkDark = Color(0xFF1A1230)

    val Cream = Color(0xFFFFF9EE)
    val MutedLavender = Color(0xFFC2B9DC)
    val SoftWhite = Color(0x18FFFFFF)

    val Success = Color(0xFF2EC486)
    val SuccessBg = Color(0x2E2EC486)
    val Danger = Color(0xFFE84855)
    val DangerBg = Color(0x33E84855)

    val Silver = Color(0xFFD8DEE9)
    val Bronze = Color(0xFFE09F5A)

    fun optionAccent(optionId: String): Color = when (optionId.uppercase()) {
        "A" -> Color(0xFFE84855)
        "B" -> Color(0xFF3A86FF)
        "C" -> Color(0xFFF5A623)
        "D" -> Color(0xFF2EC486)
        "E" -> Color(0xFF9B5DE5)
        else -> Gold
    }

    fun parseHex(hex: String, fallback: Color = Gold): Color = runCatching {
        val clean = hex.removePrefix("#")
        val value = clean.toLong(16)
        when (clean.length) {
            6 -> Color((0xFF000000L or value).toInt())
            8 -> Color(value.toInt())
            else -> fallback
        }
    }.getOrDefault(fallback)
}

private val ParlorScheme = darkColorScheme(
    primary = ParlorColors.Gold,
    onPrimary = ParlorColors.InkDark,
    secondary = ParlorColors.Success,
    background = ParlorColors.DeepNight,
    surface = ParlorColors.VelvetCard,
    onBackground = ParlorColors.Cream,
    onSurface = ParlorColors.Cream,
)

@Composable
fun WordsOfWisdomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ParlorScheme,
        content = content,
    )
}
