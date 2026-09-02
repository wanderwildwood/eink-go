package com.wanderwildwood.kuroban.engine

import com.wanderwildwood.kuroban.game.Point
import com.wanderwildwood.kuroban.game.Stone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The one place in the app where a point is written down in something other than GTP.
 *
 * SGF counts rows from the top and GTP counts them from the bottom, so a mistake here is
 * a position that loads upside down - which on a board with no coordinates and a
 * symmetric star pattern is entirely possible to miss by eye.
 */
class PositionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun written(
        black: Set<Point> = emptySet(),
        white: Set<Point> = emptySet(),
        toMove: Stone = Stone.BLACK,
        komi: Double = 6.5,
    ): String {
        val file = folder.newFile()
        writePosition(file, black, white, toMove, komi)
        return file.readText().trim()
    }

    @Test
    fun `the top left corner is the first letter of both axes`() {
        assertEquals(
            "(;GM[1]FF[4]SZ[9]KM[6.5]PL[B]AB[aa])",
            written(black = setOf(Point(0, 0))),
        )
    }

    @Test
    fun `the bottom right corner is the last letter of both`() {
        assertEquals(
            "(;GM[1]FF[4]SZ[9]KM[6.5]PL[B]AB[ii])",
            written(black = setOf(Point(8, 8))),
        )
    }

    /**
     * Column then row, which is SGF's order and the one that reads the same as A1 does.
     * Writing them the other way round mirrors every position about the diagonal, and a
     * problem mirrored about the diagonal is still a legal position - just the wrong one.
     */
    @Test
    fun `column comes before row`() {
        assertEquals(
            "(;GM[1]FF[4]SZ[9]KM[6.5]PL[B]AB[ca])",
            written(black = setOf(Point(col = 2, row = 0))),
        )
    }

    @Test
    fun `it agrees with the vertex the rest of the app speaks`() {
        // A9 is the top left in GTP, and so is Point(0, 0) as drawn.
        assertEquals("A9", Point(0, 0).toVertex())
        assertEquals("J1", Point(8, 8).toVertex())
    }

    @Test
    fun `both colours and the side to play are written`() {
        assertEquals(
            "(;GM[1]FF[4]SZ[9]KM[0.5]PL[W]AB[aa]AW[bb])",
            written(
                black = setOf(Point(0, 0)),
                white = setOf(Point(1, 1)),
                toMove = Stone.WHITE,
                komi = 0.5,
            ),
        )
    }

    /**
     * An empty board is what the editor opens on, so it has to be a file the engine will
     * take - an `AB` with nothing after it is not.
     */
    @Test
    fun `an empty position writes no add-stone properties at all`() {
        assertEquals("(;GM[1]FF[4]SZ[9]KM[6.5]PL[B])", written())
    }
}
