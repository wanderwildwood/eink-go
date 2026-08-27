package com.wanderwildwood.einkgo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wanderwildwood.einkgo.game.Stone

/**
 * A stone, small, for use in a line of text.
 *
 * Drawn rather than typed: the ● and ○ characters land at different sizes and baselines
 * depending on which font ends up serving them, and a pair that should read as a matched
 * set instead reads as a mistake.
 */
@Composable
fun StoneGlyph(stone: Stone, size: Dp = 13.dp) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.size(size)) {
        val rim = 1.5.dp.toPx()
        val radius = this.size.minDimension / 2f - rim / 2f
        if (stone == Stone.BLACK) {
            drawCircle(ink, radius = radius)
        } else {
            drawCircle(paper, radius = radius)
            drawCircle(ink, radius = radius, style = Stroke(rim))
        }
    }
}
