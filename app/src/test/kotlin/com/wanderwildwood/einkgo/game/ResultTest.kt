package com.wanderwildwood.einkgo.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** The one sentence a player reads at the end of a game, so it had better be right. */
class ResultTest {

    @Test
    fun `a scored win is read out the way a person would say it`() {
        assertEquals("Black wins by 7.5", formatResult("B+7.5"))
        assertEquals("White wins by 12.5", formatResult("W+12.5"))
        assertEquals("Black wins by 0.5", formatResult(" b+0.5 "))
    }

    @Test
    fun `a resignation names the other player as the winner`() {
        assertEquals("White wins by resignation", formatResult("", resignedBy = Stone.BLACK))
        assertEquals("Black wins by resignation", formatResult("", resignedBy = Stone.WHITE))
    }

    @Test
    fun `a resignation ignores whatever the score happened to be`() {
        assertEquals("White wins by resignation", formatResult("B+40.5", resignedBy = Stone.BLACK))
    }

    @Test
    fun `a drawn game says so`() {
        assertEquals("A draw", formatResult("0"))
        assertEquals("A draw", formatResult(""))
    }

    @Test
    fun `a resignation written as a score reads as one`() {
        assertEquals("Black wins by resignation", formatResult("B+R"))
        assertEquals("White wins by resignation", formatResult("W+r"))
    }

    @Test
    fun `anything unrecognised is passed through rather than invented`() {
        assertEquals("?", formatResult("?"))
        assertEquals("unfinished", formatResult("unfinished"))
    }
}
