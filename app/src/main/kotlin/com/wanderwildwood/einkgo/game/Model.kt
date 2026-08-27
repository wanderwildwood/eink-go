package com.wanderwildwood.einkgo.game

/**
 * 9x9 only. The engine handles any size, and the UI would scale, but a 19x19 board on a
 * 360dp-wide panel gives 18dp touch targets, which is not a game anyone enjoys playing.
 */
const val BOARD_SIZE = 9

/** Komi is fixed. One fewer thing to explain, and 6.5 is the usual modern 9x9 value. */
const val KOMI = 6.5

enum class Stone {
    BLACK,
    WHITE;

    val other: Stone get() = if (this == BLACK) WHITE else BLACK
}

/** `Point(0, 0)` is the top left intersection as drawn. */
data class Point(val col: Int, val row: Int)

enum class Opponent { COMPUTER, HUMAN }

enum class Difficulty(val level: Int, val label: String) {
    EASY(1, "Easy"),
    MEDIUM(5, "Medium"),
    HARD(10, "Hard"),
}

data class GameConfig(
    val opponent: Opponent = Opponent.COMPUTER,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val humanColor: Stone = Stone.BLACK,
)

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
    return if (margin.isEmpty()) "$winner wins" else "$winner wins by $margin"
}
