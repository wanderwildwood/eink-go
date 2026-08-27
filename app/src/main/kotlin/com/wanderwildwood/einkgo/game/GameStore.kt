package com.wanderwildwood.einkgo.game

import com.wanderwildwood.einkgo.engine.parseVertex
import com.wanderwildwood.einkgo.engine.toVertex
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

    fun save(config: GameConfig, moves: List<Point?>) {
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
                        ).joinToString(" ")
                    )
                    appendLine(moves.joinToString(" ") { it?.toVertex() ?: PASS })
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
            else -> parse(lines[1], lines[2])
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

    private fun parse(settings: String, moves: String): SavedGame? {
        val parts = settings.trim().split(" ")
        if (parts.size < 4) return null
        val config = GameConfig(
            opponent = Opponent.entries.firstOrNull { it.name == parts[0] } ?: return null,
            difficulty = Difficulty.entries.firstOrNull { it.name == parts[1] } ?: return null,
            humanColor = Stone.entries.firstOrNull { it.name == parts[2] } ?: return null,
            handicap = parts[3].toIntOrNull() ?: return null,
        )
        val played = moves.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .map { if (it == PASS) null else parseVertex(it) ?: return null }
        return SavedGame(config, played)
    }

    private companion object {
        const val VERSION = "1"
        const val PASS = "pass"
    }
}

/** A game to be rebuilt by replaying [moves]; a null move is a pass. */
data class SavedGame(val config: GameConfig, val moves: List<Point?>)
