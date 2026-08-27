package com.wanderwildwood.einkgo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A save that will not load costs somebody the game they were in the middle of, and does it
 * silently - the app simply opens on a new game as though nothing had been going on. That
 * is a bad failure to discover by accident, so the round trip is pinned here.
 */
class GameStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store() = GameStore(folder.newFile())

    @Test
    fun `a game comes back exactly as it went in`() {
        val store = store()
        val config = GameConfig(
            opponent = Opponent.COMPUTER,
            difficulty = Difficulty.EASY,
            humanColor = Stone.WHITE,
            handicap = 4,
        )
        val moves = listOf(Point(4, 4), Point(0, 8), null, Point(8, 0))

        store.save(config, moves)
        val loaded = store.load()

        assertEquals(config, loaded?.config)
        assertEquals(moves, loaded?.moves)
    }

    @Test
    fun `passes survive, including one as the very last move`() {
        val store = store()
        val moves = listOf(null, Point(4, 4), null)
        store.save(GameConfig(), moves)
        assertEquals(moves, store.load()?.moves)
    }

    @Test
    fun `a game with no moves yet is still a game`() {
        val store = store()
        store.save(GameConfig(handicap = 2), emptyList())
        val loaded = store.load()
        assertEquals(emptyList<Point?>(), loaded?.moves)
        assertEquals(2, loaded?.config?.handicap)
    }

    @Test
    fun `clearing leaves nothing to come back to`() {
        val file = folder.newFile()
        val store = GameStore(file)
        store.save(GameConfig(), listOf(Point(0, 0)))
        store.clear()
        assertFalse(file.exists())
        assertNull(store.load())
    }

    @Test
    fun `a missing file is simply no game`() {
        assertNull(GameStore(folder.newFile().also { it.delete() }).load())
    }

    @Test
    fun `a file from a future format is refused rather than misread`() {
        val file = folder.newFile()
        file.writeText("2\nCOMPUTER NORMAL BLACK 0\nE5\n")
        assertNull(GameStore(file).load())
    }

    @Test
    fun `damaged files are refused, not half-loaded`() {
        val damaged = listOf(
            "",
            "1\n",
            "1\nCOMPUTER NORMAL BLACK\nE5\n",
            "1\nCOMPUTER NORMAL PURPLE 0\nE5\n",
            "1\nSOMEBODY NORMAL BLACK 0\nE5\n",
            "1\nCOMPUTER NORMAL BLACK x\nE5\n",
            "1\nCOMPUTER NORMAL BLACK 0\nE5 Z9\n",
        )
        for (text in damaged) {
            val file = folder.newFile()
            file.writeText(text)
            assertNull("expected null for:\n$text", GameStore(file).load())
        }
    }
}
