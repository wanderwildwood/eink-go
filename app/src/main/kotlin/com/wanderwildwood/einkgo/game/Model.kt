package com.wanderwildwood.einkgo.game

/**
 * 9x9 only. The engine handles any size, and the UI would scale, but a 19x19 board on a
 * 360dp-wide panel gives 18dp touch targets, which is not a game anyone enjoys playing.
 */
const val BOARD_SIZE = 9

/** Komi is fixed. One fewer thing to explain, and 6.5 is the usual modern 9x9 value. */
const val KOMI = 6.5

/** A handicap game keeps only the half point that stops a draw. */
const val HANDICAP_KOMI = 0.5

enum class Stone {
    BLACK,
    WHITE;

    val other: Stone get() = if (this == BLACK) WHITE else BLACK
}

/** `Point(0, 0)` is the top left intersection as drawn. */
data class Point(val col: Int, val row: Int)

/**
 * COMPUTER is shown as "GNU Go", since that is what it is. The name here stays as it is:
 * it is written into every saved game, and renaming it would throw away a game in
 * progress to change a word nobody sees.
 */
enum class Opponent { COMPUTER, HUMAN }

/**
 * Two settings, because on a 9x9 board GNU Go only has two.
 *
 * Measured by self-play, colours alternating: levels 1 and 3 are indistinguishable from
 * each other (9-7 over 16 games), and so are levels 5 and 10 (12-12 over 24 games, mean
 * margin a tenth of a point). The only real step is between 3 and 5. Level 10 therefore
 * costs ten times the thinking time of level 5 - 3.4s against 0.33s per move on the
 * Kompakt, in a middlegame - and plays no better for it.
 *
 * A third, genuinely stronger setting was looked for and does not exist at a usable speed.
 * GNU Go's Monte Carlo mode, which it provides for 9x9 and smaller, does beat classic
 * level 10 (7-1 over 8 games, +6.2 points) - but it takes 14s a move on this phone, and
 * its strength is in the sheer number of simulations. Cheapening it to `--monte-carlo
 * --level 1`, at 1.3s a move, throws away the thing that made it strong: 26-28 over 54
 * games against classic level 5, which is a coin. There is no fast version of it.
 *
 * So the ladder stops at 5, and handicap is the dial for anything finer. That is the
 * Go-native answer anyway: handicap is how the game has always handled unequal players.
 */
enum class Difficulty(val level: Int, val label: String) {
    EASY(1, "Easy"),
    NORMAL(5, "Normal"),
}

data class GameConfig(
    val opponent: Opponent = Opponent.COMPUTER,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val humanColor: Stone = Stone.BLACK,
    /** Free stones for Black before play starts. 0, or 2 upwards - a handicap of 1 means nothing. */
    val handicap: Int = 0,
) {
    /** The handicap stones are Black's, so in a handicap game White opens. */
    val firstToMove: Stone get() = if (handicap >= 2) Stone.WHITE else Stone.BLACK

    /** In a handicap game the stones are the compensation, so komi all but disappears. */
    val komi: Double get() = if (handicap >= 2) HANDICAP_KOMI else KOMI
}

enum class Phase {
    /** Waiting on the engine to start up. */
    STARTING,
    PLAYING,

    /** The computer is choosing a move; the board does not take taps. */
    THINKING,
    FINISHED,

    /** The engine died. Nothing can be done except start a new game. */
    BROKEN,
}

data class GameState(
    val config: GameConfig,
    val phase: Phase = Phase.STARTING,
    val black: Set<Point> = emptySet(),
    val white: Set<Point> = emptySet(),
    val toMove: Stone = Stone.BLACK,
    /** Stones each colour has captured from the other. */
    val blackCaptures: Int = 0,
    val whiteCaptures: Int = 0,
    val legal: Set<Point> = emptySet(),
    val lastMove: Point? = null,
    /** Tapped but not yet committed. */
    val preview: Point? = null,
    val dead: Set<Point> = emptySet(),
    val result: String? = null,
    /** True only when the game ended in two passes and the engine counted it. */
    val wasScored: Boolean = false,
    val movesPlayed: Int = 0,
    /** Shown once under the board: a passed turn, a refused move, an engine failure. */
    val message: String? = null,
) {
    /** In a two-player game both sides are human, so whoever is to move is the player. */
    val isHumanTurn: Boolean
        get() = config.opponent == Opponent.HUMAN || toMove == config.humanColor

    val canUndo: Boolean
        get() = movesPlayed >= movesPerUndo && (phase == Phase.PLAYING || phase == Phase.FINISHED)

    /**
     * Against the computer, taking back your move means taking back its reply too -
     * otherwise you would just be handing it a free move.
     */
    val movesPerUndo: Int
        get() = if (config.opponent == Opponent.COMPUTER) 2 else 1
}

/** `B+7.5` and `W+12.5` and `0`, said the way a person would say them. */
fun formatResult(score: String, resignedBy: Stone? = null): String {
    if (resignedBy != null) {
        val winner = if (resignedBy == Stone.BLACK) "White" else "Black"
        return "$winner wins by resignation"
    }
    val trimmed = score.trim()
    if (trimmed.isEmpty() || trimmed == "0") return "A draw"
    val winner = when (trimmed.first().uppercaseChar()) {
        'B' -> "Black"
        'W' -> "White"
        else -> return trimmed
    }
    val margin = trimmed.substringAfter('+', "").trim()
    return when {
        margin.isEmpty() -> "$winner wins"
        // Scores are normally a number, but "+R" is a resignation written as a score.
        margin.equals("R", ignoreCase = true) -> "$winner wins by resignation"
        else -> "$winner wins by $margin"
    }
}
