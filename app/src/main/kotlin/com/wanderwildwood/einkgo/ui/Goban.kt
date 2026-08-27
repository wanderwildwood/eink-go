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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanderwildwood.einkgo.game.BOARD_SIZE
import com.wanderwildwood.einkgo.game.GameState
import com.wanderwildwood.einkgo.game.Point
import com.wanderwildwood.einkgo.game.Stone
import kotlin.math.roundToInt

/** Hoshi on a 9x9 board: the four 3-3 points and tengen. */
private val STAR_POINTS = setOf(
    Point(2, 2), Point(6, 2), Point(4, 4), Point(2, 6), Point(6, 6),
)

/** GTP column letters skip I, so a column is never mistaken for the digit 1. */
private const val COLUMN_LETTERS = "ABCDEFGHJ"

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
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val density = LocalDensity.current
        val sidePx = with(density) { side.toPx() }

        // Room down the left and along the bottom for the coordinates, and a little
        // clearance all round so that stones on the edge line - which stick out past it
        // by their own radius - are not clipped or crowded against the screen edge.
        val gutter = sidePx * 0.055f
        val edgePad = sidePx * 0.02f
        val cell = (sidePx - gutter - 2 * edgePad) / BOARD_SIZE
        val originX = gutter + edgePad + cell / 2f
        val originY = edgePad + cell / 2f

        val lineWidth = with(density) { 1.dp.toPx() }
        val borderWidth = with(density) { 2.dp.toPx() }
        val stoneRadius = cell * 0.46f
        val labelStyle = TextStyle(
            fontSize = with(density) { (cell * 0.30f).toSp() },
            fontWeight = FontWeight.Medium,
            color = ink,
        )

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

            drawCoordinates(measurer, labelStyle, originX, originY, cell, span, stoneRadius)

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

/** Letters along the bottom, numbers down the left, the way a goban is labelled. */
private fun DrawScope.drawCoordinates(
    measurer: TextMeasurer,
    style: TextStyle,
    originX: Float,
    originY: Float,
    cell: Float,
    span: Float,
    stoneRadius: Float,
) {
    val below = originY + span + stoneRadius + cell * 0.16f
    for (col in 0 until BOARD_SIZE) {
        val label = measurer.measure(COLUMN_LETTERS[col].toString(), style)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(originX + col * cell - label.size.width / 2f, below),
        )
    }

    val leftOf = originX - stoneRadius - cell * 0.16f
    for (row in 0 until BOARD_SIZE) {
        // Row 1 is at the bottom of the board, as it is written in every game record.
        val label = measurer.measure((BOARD_SIZE - row).toString(), style)
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                leftOf - label.size.width,
                originY + row * cell - label.size.height / 2f,
            ),
        )
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
