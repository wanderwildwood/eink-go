package com.wanderwildwood.kuroban.engine

import com.wanderwildwood.kuroban.game.BOARD_SIZE
import com.wanderwildwood.kuroban.game.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every move the app plays crosses this translation twice, and a board that disagrees with
 * the engine about which point is which would be a very confusing bug to read.
 */
class VertexTest {

    @Test
    fun `every point survives a round trip`() {
        for (row in 0 until BOARD_SIZE) {
            for (col in 0 until BOARD_SIZE) {
                val point = Point(col, row)
                assertEquals(point, parseVertex(point.toVertex()))
            }
        }
    }

    @Test
    fun `the corners are where a Go player expects them`() {
        // Row 1 is the bottom of the board, and Point(0, 0) is drawn at the top left.
        assertEquals("A9", Point(0, 0).toVertex())
        assertEquals("A1", Point(0, BOARD_SIZE - 1).toVertex())
        assertEquals("J9", Point(BOARD_SIZE - 1, 0).toVertex())
        assertEquals("J1", Point(BOARD_SIZE - 1, BOARD_SIZE - 1).toVertex())
        assertEquals("E5", Point(4, 4).toVertex())
    }

    @Test
    fun `the column letter I is skipped, as GTP requires`() {
        val letters = (0 until BOARD_SIZE).map { Point(it, 0).toVertex().first() }
        assertEquals("ABCDEFGHJ".toList(), letters)
        assertNull(parseVertex("I5"))
    }

    @Test
    fun `nonsense is refused rather than guessed at`() {
        for (bad in listOf("", "A", "5", "Z5", "A0", "A10", "pass", "PASS", "resign", "--")) {
            assertNull("expected null for '$bad'", parseVertex(bad))
        }
    }

    @Test
    fun `case and surrounding space do not matter`() {
        assertEquals(Point(4, 4), parseVertex("  e5 "))
    }
}
