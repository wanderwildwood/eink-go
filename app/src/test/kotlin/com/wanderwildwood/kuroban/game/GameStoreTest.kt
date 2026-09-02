package com.wanderwildwood.kuroban.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        store.save(config, moves, seed = 4242)
        val loaded = store.load()

        assertEquals(config, loaded?.config)
        assertEquals(moves, loaded?.moves)
        assertEquals(4242, loaded?.seed)
    }

    /**
     * Seeds were added to the format without bumping its version, because a saved game
     * from before them is still a good game - it just resumes on a new seed. Bumping
     * would have thrown away a game in progress to gain nothing.
     */
    @Test
    fun `a file written before seeds were recorded still loads`() {
        val file = folder.newFile()
        file.writeText("1\nCOMPUTER NORMAL BLACK 0\nE5\n")
        val loaded = GameStore(file).load()
        assertEquals(listOf(Point(4, 4)), loaded?.moves)
        assertNull(loaded?.seed)
    }

    @Test
    fun `passes survive, including one as the very last move`() {
        val store = store()
        val moves = listOf(null, Point(4, 4), null)
        store.save(GameConfig(), moves, seed = 1)
        assertEquals(moves, store.load()?.moves)
    }

    @Test
    fun `a game with no moves yet is still a game`() {
        val store = store()
        store.save(GameConfig(handicap = 2), emptyList(), seed = 1)
        val loaded = store.load()
        assertEquals(emptyList<Point?>(), loaded?.moves)
        assertEquals(2, loaded?.config?.handicap)
    }

    @Test
    fun `clearing leaves nothing to come back to`() {
        val file = folder.newFile()
        val store = GameStore(file)
        store.save(GameConfig(), listOf(Point(0, 0)), seed = 1)
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
    fun `a hand-placed position comes back with the game`() {
        val store = store()
        val setup = Setup(
            black = setOf(Point(0, 0), Point(1, 1)),
            white = setOf(Point(8, 8)),
            toMove = Stone.WHITE,
        )
        val moves = listOf(Point(4, 4), null)

        store.save(GameConfig(setup = setup), moves, seed = 7, editing = false)
        val loaded = store.load()

        assertEquals(setup, loaded?.config?.setup)
        assertEquals(moves, loaded?.moves)
        assertFalse(loaded?.editing ?: true)
    }

    /**
     * Copying a problem out of a book is exactly when somebody puts the phone down to look
     * at the book, so a position half placed has to survive being killed as surely as a
     * game does - and has to come back to the board editor rather than into a game.
     */
    @Test
    fun `a position still being placed comes back as one`() {
        val store = store()
        val setup = Setup(black = setOf(Point(2, 3)), white = emptySet(), toMove = Stone.BLACK)

        store.save(GameConfig(setup = setup), emptyList(), seed = 7, editing = true)
        val loaded = store.load()

        assertEquals(setup, loaded?.config?.setup)
        assertTrue(loaded?.editing ?: false)
    }

    @Test
    fun `an empty position is a position, not the absence of one`() {
        val store = store()
        store.save(GameConfig(setup = Setup()), emptyList(), seed = 7, editing = true)
        assertEquals(Setup(), store.load()?.config?.setup)
    }

    /**
     * The three lines a position takes were appended rather than folded into the settings
     * line, so that a game saved before the board editor existed still opens.
     */
    @Test
    fun `a file written before positions could be saved still loads`() {
        val file = folder.newFile()
        file.writeText("1\nCOMPUTER NORMAL BLACK 0 42\nE5\n")
        val loaded = GameStore(file).load()
        assertEquals(listOf(Point(4, 4)), loaded?.moves)
        assertNull(loaded?.config?.setup)
        assertFalse(loaded?.editing ?: true)
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
            // A position whose stones will not parse loses the game once and visibly,
            // rather than coming back with some of its stones missing.
            "1\nCOMPUTER NORMAL BLACK 0 1\nE5\nBLACK editing\nA1 Z9\nB2\n",
            "1\nCOMPUTER NORMAL BLACK 0 1\nE5\nPURPLE editing\nA1\nB2\n",
        )
        for (text in damaged) {
            val file = folder.newFile()
            file.writeText(text)
            assertNull("expected null for:\n$text", GameStore(file).load())
        }
    }
}
