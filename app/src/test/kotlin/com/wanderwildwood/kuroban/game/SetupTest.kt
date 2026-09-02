package com.wanderwildwood.kuroban.game

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupTest {

    /**
     * Who opens is derived rather than stored, and a hand-placed position is the third
     * thing that decides it. Getting this wrong hands the first move to the wrong side,
     * which in a life-and-death position is the whole answer.
     */
    @Test
    fun `a hand-placed position says who opens, over anything else`() {
        assertEquals(Stone.BLACK, GameConfig().firstToMove)
        assertEquals(Stone.WHITE, GameConfig(handicap = 4).firstToMove)
        assertEquals(
            Stone.WHITE,
            GameConfig(setup = Setup(toMove = Stone.WHITE)).firstToMove,
        )
        assertEquals(
            Stone.BLACK,
            GameConfig(handicap = 4, setup = Setup(toMove = Stone.BLACK)).firstToMove,
        )
    }

    /**
     * These are the points the liberty check asks the engine about after a stone goes
     * down. Counting a point that is off the board would ask about a vertex that does not
     * exist, and missing one would let an impossible position through.
     */
    @Test
    fun `a point in the middle has four neighbours and a corner has two`() {
        assertEquals(4, Point(4, 4).neighbours().size)
        assertEquals(
            setOf(Point(1, 0), Point(0, 1)),
            Point(0, 0).neighbours().toSet(),
        )
        assertEquals(
            setOf(Point(7, 8), Point(8, 7)),
            Point(8, 8).neighbours().toSet(),
        )
        assertEquals(3, Point(4, 0).neighbours().size)
    }
}
