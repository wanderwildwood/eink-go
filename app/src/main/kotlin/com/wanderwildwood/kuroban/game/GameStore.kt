package com.wanderwildwood.kuroban.game

import com.wanderwildwood.kuroban.engine.parseVertex
import com.wanderwildwood.kuroban.engine.toVertex
import java.io.File

/**
 * Remembers the game in progress across the app being killed.
 *
 * What is stored is the list of moves, not the position. The engine's own `printsgf`
 * writes a position - add-stone properties and whose turn it is - which loses the move
 * history that Undo walks back through, loses the prisoner counts, and writes a komi of
 * its own choosing. Replaying the moves instead hands the engine everything it needs to
 * rebuild all of that exactly, and sixty `play` commands cost milliseconds.
 *
 * The format is one game per file, deliberately plain: a version line so a later change
 * can recognise and discard an older file, the settings, then the moves.
 */
class GameStore(private val file: File) {

    fun save(config: GameConfig, moves: List<Point?>, seed: Int, editing: Boolean = false) {
        try {
            file.writeText(
                buildString {
                    appendLine(VERSION)
                    appendLine(
                        listOf(
                            config.opponent.name,
                            config.difficulty.name,
                            config.humanColor.name,
                            config.handicap.toString(),
                            seed.toString(),
                        ).joinToString(" ")
                    )
                    appendLine(moves.joinToString(" ") { it?.toVertex() ?: PASS })
                    // Three more lines, only when there is a hand-placed position to keep.
                    // They are appended rather than folded into the settings line so that
                    // a file written before this existed still reads, and one written now
                    // still reads to anything that only knows the first three lines.
                    config.setup?.let { setup ->
                        appendLine("${setup.toMove.name} ${if (editing) EDITING else PLAYING}")
                        appendLine(setup.black.joinToString(" ") { it.toVertex() })
                        appendLine(setup.white.joinToString(" ") { it.toVertex() })
                    }
                }
            )
        } catch (_: Exception) {
            // Losing a saved game is a disappointment, not a failure worth crashing over.
        }
    }

    fun load(): SavedGame? = try {
        val lines = file.takeIf { it.isFile }?.readLines()
        when {
            lines == null || lines.size < 3 -> null
            lines[0].trim() != VERSION -> null
            else -> parse(lines[1], lines[2], lines.drop(3))
        }
    } catch (_: Exception) {
        null
    }

    fun clear() {
        try {
            file.delete()
        } catch (_: Exception) {
            // It will be overwritten by the next save regardless.
        }
    }

    private fun parse(settings: String, moves: String, rest: List<String>): SavedGame? {
        val parts = settings.trim().split(" ")
        if (parts.size < 4) return null
        var config = GameConfig(
            opponent = Opponent.entries.firstOrNull { it.name == parts[0] } ?: return null,
            difficulty = Difficulty.entries.firstOrNull { it.name == parts[1] } ?: return null,
            humanColor = Stone.entries.firstOrNull { it.name == parts[2] } ?: return null,
            handicap = parts[3].toIntOrNull() ?: return null,
        )
        val played = moves.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .map { if (it == PASS) null else parseVertex(it) ?: return null }

        var editing = false
        if (rest.size >= 3) {
            val head = rest[0].trim().split(" ")
            val toMove = Stone.entries.firstOrNull { it.name == head.getOrNull(0) } ?: return null
            editing = head.getOrNull(1) == EDITING
            config = config.copy(
                setup = Setup(
                    black = rest[1].toPoints() ?: return null,
                    white = rest[2].toPoints() ?: return null,
                    toMove = toMove,
                )
            )
        }

        // The seed arrived after the format did. A file without one is still a perfectly
        // good game; it just resumes under a new seed, the way it always used to.
        return SavedGame(config, played, parts.getOrNull(4)?.toIntOrNull(), editing)
    }

    /** Null rather than a short set, so a line that is damaged loses the game once and loudly. */
    private fun String.toPoints(): Set<Point>? {
        val words = trim().split(" ").filter { it.isNotBlank() }
        val points = words.mapNotNull { parseVertex(it) }
        return if (points.size == words.size) points.toSet() else null
    }

    private companion object {
        const val VERSION = "1"
        const val PASS = "pass"
        const val EDITING = "editing"
        const val PLAYING = "playing"
    }
}

/**
 * A game to be rebuilt by replaying [moves]; a null move is a pass.
 *
 * [seed] is the engine's, so that a resumed game carries on playing the game it was
 * playing rather than a fresh one. Null in a file written before seeds were recorded.
 */
data class SavedGame(
    val config: GameConfig,
    val moves: List<Point?>,
    val seed: Int?,
    /** True if the stones were still being placed rather than played. */
    val editing: Boolean = false,
)
