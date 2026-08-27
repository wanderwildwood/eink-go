package com.wanderwildwood.einkgo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.wanderwildwood.einkgo.game.BOARD_SIZE
import com.wanderwildwood.einkgo.game.GameState
import com.wanderwildwood.einkgo.game.Point
import com.wanderwildwood.einkgo.game.Stone
import kotlin.math.roundToInt

/** Hoshi on a 9x9 board: the four 3-3 points and tengen. */
private val STAR_POINTS = setOf(
    Point(2, 2), Point(6, 2), Point(4, 4), Point(2, 6), Point(6, 6),
)

/**
 * The board.
 *
 * Everything is drawn at full black or full white with hard edges. E Ink has no useful
 * greys at this size and dithering a stone only makes it look like a smudge, so a white
 * stone is a white disc with a black rim and that is the whole trick.
 */
@Composable
fun Goban(
    state: GameState,
    onTap: (Point) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val density = LocalDensity.current
        val sidePx = with(density) { side.toPx() }

        // A stone on the edge line sticks out past it by its own radius, so the grid is
        // inset rather than run to the canvas edge - otherwise corner stones are clipped
        // and the board looks like it is falling off the screen.
        val inset = sidePx * 0.035f
        val cell = (sidePx - 2 * inset) / BOARD_SIZE
        val origin = inset + cell / 2f
        val lineWidth = with(density) { 1.dp.toPx() }
        val borderWidth = with(density) { 2.dp.toPx() }
        val stoneRadius = cell * 0.46f

        fun center(point: Point) =
            Offset(origin + point.col * cell, origin + point.row * cell)

        Canvas(
            modifier = Modifier
                .size(side)
                .pointerInput(cell) {
                    detectTapGestures { offset ->
                        val col = ((offset.x - origin) / cell).roundToInt()
                        val row = ((offset.y - origin) / cell).roundToInt()
                        if (col in 0 until BOARD_SIZE && row in 0 until BOARD_SIZE) {
                            onTap(Point(col, row))
                        }
                    }
                }
        ) {
            val span = (BOARD_SIZE - 1) * cell

            for (i in 0 until BOARD_SIZE) {
                val at = origin + i * cell
                drawLine(ink, Offset(origin, at), Offset(origin + span, at), lineWidth)
                drawLine(ink, Offset(at, origin), Offset(at, origin + span), lineWidth)
            }
            drawRect(
                color = ink,
                topLeft = Offset(origin, origin),
                size = Size(span, span),
                style = Stroke(borderWidth),
            )

            for (star in STAR_POINTS) {
                drawCircle(ink, radius = cell * 0.07f, center = center(star))
            }

            for (point in state.black) {
                drawStone(center(point), stoneRadius, Stone.BLACK, ink, paper, borderWidth)
            }
            for (point in state.white) {
                drawStone(center(point), stoneRadius, Stone.WHITE, ink, paper, borderWidth)
            }

            // A quiet dot on the stone just played, so a board you looked away from can
            // still tell you where the last move landed.
            state.lastMove?.let { last ->
                if (last in state.black || last in state.white) {
                    val onBlack = last in state.black
                    drawCircle(
                        color = if (onBlack) paper else ink,
                        radius = cell * 0.13f,
                        center = center(last),
                    )
                }
            }

            // Stones the engine judged dead when the game was scored.
            for (point in state.dead) {
                val onBlack = point in state.black
                val mark = if (onBlack) paper else ink
                val at = center(point)
                val arm = stoneRadius * 0.55f
                drawLine(mark, Offset(at.x - arm, at.y - arm), Offset(at.x + arm, at.y + arm), borderWidth)
                drawLine(mark, Offset(at.x - arm, at.y + arm), Offset(at.x + arm, at.y - arm), borderWidth)
            }

            // The stone about to be played: a smaller solid stone inside a dashed ring,
            // which reads as "here, not yet" without needing a legend.
            state.preview?.let { preview ->
                val at = center(preview)
                drawStone(at, stoneRadius * 0.62f, state.toMove, ink, paper, borderWidth)
                drawCircle(
                    color = ink,
                    radius = stoneRadius,
                    center = at,
                    style = Stroke(
                        width = borderWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(cell * 0.12f, cell * 0.10f)
                        ),
                    ),
                )
            }
        }
    }
}

private fun DrawScope.drawStone(
    center: Offset,
    radius: Float,
    stone: Stone,
    ink: Color,
    paper: Color,
    rimWidth: Float,
) {
    if (stone == Stone.BLACK) {
        drawCircle(ink, radius = radius, center = center)
    } else {
        drawCircle(paper, radius = radius, center = center)
        drawCircle(ink, radius = radius, center = center, style = Stroke(rimWidth))
    }
}
