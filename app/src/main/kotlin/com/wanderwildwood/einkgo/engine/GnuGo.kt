package com.wanderwildwood.einkgo.engine

import com.wanderwildwood.einkgo.game.BOARD_SIZE
import com.wanderwildwood.einkgo.game.Point
import com.wanderwildwood.einkgo.game.Stone
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException

class GnuGoException(message: String) : Exception(message)

/** What came back from asking the engine for a move. */
sealed interface EngineMove {
    data class Play(val point: Point) : EngineMove
    data object Pass : EngineMove
    data object Resign : EngineMove
}

/**
 * GNU Go 3.8, running as a child process and spoken to over GTP on its stdin/stdout.
 *
 * The engine is authoritative for everything: legality, captures, ko, and scoring. This
 * app deliberately implements no Go rules of its own - it asks. That is only affordable
 * because the engine answers a 9x9 position in single-digit milliseconds.
 */
class GnuGo(private val binaryPath: String) {

    private var process: Process? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    fun start(level: Int, komi: Double) {
        val started = ProcessBuilder(binaryPath, "--mode", "gtp").start()
        process = started
        reader = started.inputStream.bufferedReader()
        writer = started.outputStream.bufferedWriter()

        // GNU Go says little on stderr in GTP mode, but an unread pipe that does fill up
        // would block the engine mid-move, so drain it and drop it on the floor.
        Thread {
            try {
                started.errorStream.bufferedReader().forEachLine { }
            } catch (_: IOException) {
                // The process went away; nothing to drain.
            }
        }.apply { isDaemon = true }.start()

        send("boardsize $BOARD_SIZE")
        send("clear_board")
        send("komi $komi")
        send("level $level")
    }

    /**
     * Sends one command and returns its response body.
     *
     * A GTP response is `=` or `?`, then the body, then a blank line. `?` means the
     * engine rejected the command, which for us is always a bug rather than a legal
     * move being refused - illegal moves are filtered before they are ever sent.
     */
    @Synchronized
    fun send(command: String): String {
        val out = writer ?: throw GnuGoException("Engine is not running")
        val input = reader ?: throw GnuGoException("Engine is not running")

        try {
            out.write(command)
            out.write("\n")
            out.flush()
        } catch (e: IOException) {
            throw GnuGoException("Engine stopped listening during '$command': ${e.message}")
        }

        val lines = mutableListOf<String>()
        while (true) {
            val line = input.readLine()
                ?: throw GnuGoException("Engine exited during '$command'")
            if (line.isBlank()) {
                if (lines.isEmpty()) continue else break
            }
            lines += line.trimEnd()
        }

        val head = lines.first()
        val body = (listOf(head.drop(1).removePrefix(" ")) + lines.drop(1))
            .joinToString("\n")
            .trim()
        if (head.startsWith("?")) throw GnuGoException("Engine refused '$command': $body")
        return body
    }

    fun play(color: Stone, point: Point?) {
        send("play ${color.gtp} ${point?.toVertex() ?: "pass"}")
    }

    fun genMove(color: Stone): EngineMove {
        val answer = send("genmove ${color.gtp}").uppercase()
        return when (answer) {
            "PASS" -> EngineMove.Pass
            "RESIGN" -> EngineMove.Resign
            else -> parseVertex(answer)?.let(EngineMove::Play) ?: EngineMove.Pass
        }
    }

    fun undo() {
        send("undo")
    }

    fun stones(color: Stone): Set<Point> = send("list_stones ${color.gtp}").toPoints()

    /** How many stones this colour has captured from the other. */
    fun captures(color: Stone): Int = send("captures ${color.gtp}").trim().toIntOrNull() ?: 0

    fun legalMoves(color: Stone): Set<Point> = send("all_legal ${color.gtp}").toPoints()

    /**
     * Null before either side has played, and after a pass. The engine answers
     * `last_move` with an error rather than an empty result on an empty board, which is
     * a fact about the position and not a failure.
     */
    fun lastMove(): Point? = try {
        send("last_move").split(" ").lastOrNull()?.let { parseVertex(it) }
    } catch (_: GnuGoException) {
        null
    }

    /** Something like `B+7.5`, `W+12.5`, or `0` for a draw. */
    fun finalScore(): String = send("final_score").trim()

    fun deadStones(): Set<Point> = send("final_status_list dead").toPoints()

    fun close() {
        try {
            send("quit")
        } catch (_: Exception) {
            // Already gone, or wedged - either way we are about to destroy it.
        }
        process?.destroy()
        process = null
        reader = null
        writer = null
    }

    private fun String.toPoints(): Set<Point> =
        split(Regex("\\s+")).mapNotNull { parseVertex(it) }.toSet()
}

private val Stone.gtp: String get() = if (this == Stone.BLACK) "black" else "white"

// GTP column letters skip I, so that a column is never mistaken for the digit 1.
private const val COLUMN_LETTERS = "ABCDEFGHJKLMNOPQRST"

/** `Point(0, 0)` is the top left of the board; GTP counts rows from the bottom. */
fun Point.toVertex(): String = "${COLUMN_LETTERS[col]}${BOARD_SIZE - row}"

fun parseVertex(vertex: String): Point? {
    val text = vertex.trim().uppercase()
    if (text.length < 2) return null
    val col = COLUMN_LETTERS.indexOf(text.first())
    val fromBottom = text.drop(1).toIntOrNull() ?: return null
    if (col !in 0 until BOARD_SIZE || fromBottom !in 1..BOARD_SIZE) return null
    return Point(col, BOARD_SIZE - fromBottom)
}
