package com.wanderwildwood.kuroban.ui

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
import com.wanderwildwood.kuroban.game.BOARD_SIZE
import com.wanderwildwood.kuroban.game.GameState
import com.wanderwildwood.kuroban.game.Point
import com.wanderwildwood.kuroban.game.Stone
import kotlin.math.roundToInt

/** Hoshi on a 9x9 board: the four 3-3 points and tengen. */
private val STAR_POINTS = setOf(
    Point(2, 2), Point(6, 2), Point(4, 4), Point(2, 6), Point(6, 6),
)

/**
 * The board.
 *
 * No coordinates. On a nine-line board every point is within four lines of an edge and
 * the star points already say where you are, so labels were costing width and attention
 * to name an axis nobody was reading.
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

        // Just enough clearance that stones on the edge line - which stick out past it by
        // their own radius - are not clipped.
        val edgePad = sidePx * 0.006f
        val cell = (sidePx - 2 * edgePad) / BOARD_SIZE
        val originX = edgePad + cell / 2f
        val originY = edgePad + cell / 2f

        val lineWidth = with(density) { 1.dp.toPx() }
        val borderWidth = with(density) { 2.dp.toPx() }
        val stoneRadius = cell * 0.44f
        val seamWidth = with(density) { 1.5.dp.toPx() }

        fun center(point: Point) =
            Offset(originX + point.col * cell, originY + point.row * cell)

        Canvas(
            modifier = Modifier
                .size(side)
                .pointerInput(cell, originX, originY) {
                    detectTapGestures { offset ->
                        val col = ((offset.x - originX) / cell).roundToInt()
                        val row = ((offset.y - originY) / cell).roundToInt()
                        if (col in 0 until BOARD_SIZE && row in 0 until BOARD_SIZE) {
                            onTap(Point(col, row))
                        }
                    }
                }
        ) {
            val span = (BOARD_SIZE - 1) * cell

            for (i in 0 until BOARD_SIZE) {
                val at = originX + i * cell
                val down = originY + i * cell
                drawLine(ink, Offset(originX, down), Offset(originX + span, down), lineWidth)
                drawLine(ink, Offset(at, originY), Offset(at, originY + span), lineWidth)
            }

            // A real goban's outer line is heavier than the ones inside it.
            drawRect(
                color = ink,
                topLeft = Offset(originX, originY),
                size = Size(span, span),
                style = Stroke(borderWidth),
            )

            for (star in STAR_POINTS) {
                drawCircle(ink, radius = cell * 0.075f, center = center(star))
            }


            for (point in state.black) {
                drawStone(center(point), stoneRadius, Stone.BLACK, ink, paper, borderWidth, seamWidth)
            }
            for (point in state.white) {
                drawStone(center(point), stoneRadius, Stone.WHITE, ink, paper, borderWidth, seamWidth)
            }

            // A quiet dot on the stone just played, so a board you looked away from can
            // still tell you where the last move landed.
            state.lastMove?.let { last ->
                if (last in state.black || last in state.white) {
                    drawCircle(
                        color = if (last in state.black) paper else ink,
                        radius = cell * 0.13f,
                        center = center(last),
                    )
                }
            }

            // Stones the engine judged dead when the game was scored.
            for (point in state.dead) {
                val mark = if (point in state.black) paper else ink
                val at = center(point)
                val arm = stoneRadius * 0.55f
                drawLine(mark, Offset(at.x - arm, at.y - arm), Offset(at.x + arm, at.y + arm), borderWidth)
                drawLine(mark, Offset(at.x - arm, at.y + arm), Offset(at.x + arm, at.y - arm), borderWidth)
            }

            // The stone about to be played: a smaller solid stone inside a dashed ring,
            // which reads as "here, not yet" without needing a legend.
            state.preview?.let { preview ->
                val at = center(preview)
                drawStone(at, stoneRadius * 0.62f, state.toMove, ink, paper, borderWidth, seamWidth)
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
    seamWidth: Float,
) {
    if (stone == Stone.BLACK) {
        drawCircle(ink, radius = radius, center = center)
        // A paper-coloured seam around every black stone. On a real board two touching
        // black stones are still two objects because they are round and catch the light;
        // drawn flat in one colour they merge into a blob you cannot count. This is what
        // keeps a black chain readable as stones.
        drawCircle(paper, radius = radius, center = center, style = Stroke(seamWidth))
    } else {
        drawCircle(paper, radius = radius, center = center)
        drawCircle(ink, radius = radius, center = center, style = Stroke(rimWidth))
    }
}
