package com.wanderwildwood.einkgo.engine

import android.util.Log
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
    private var stderrDrain: Thread? = null

    /**
     * The last thing the engine said on stderr before it stopped saying anything.
     *
     * GNU Go's internal assertions print the assertion that failed, the board, and an
     * SGF of the position here, and then abort. That is the whole diagnosis of an engine
     * bug, and it used to go straight in the bin - a crash reached the player as "Engine
     * exited during genmove" and nothing else, twice unhelpfully, because the engine
     * seeds itself from the clock and so the game cannot be played again to look at it.
     */
    private val diagnostics = ArrayDeque<String>()

    // Not the instance lock: `send` holds that for the length of a move, and the drain
    // thread must never wait on it. A blocked drain is a full pipe, and a full pipe stops
    // the engine dead - which is the failure that thread exists to prevent.
    private val diagnosticsLock = Any()

    /**
     * [seed] fixes the game the engine will play. GNU Go seeds itself from the clock when
     * it is not told, which makes every game different but also makes none of them
     * repeatable; passing our own keeps the variety and buys back the repeat.
     */
    fun start(level: Int, komi: Double, handicap: Int, seed: Int) {
        val started = ProcessBuilder(binaryPath, "--mode", "gtp", "--seed", "$seed").start()
        process = started
        reader = started.inputStream.bufferedReader()
        writer = started.outputStream.bufferedWriter()

        // GNU Go says little on stderr in GTP mode, but an unread pipe that does fill up
        // would block the engine mid-move, so it has to be drained whatever happens.
        // Keeping the tail of what it says costs nothing.
        stderrDrain = Thread {
            try {
                started.errorStream.bufferedReader().forEachLine { line ->
                    synchronized(diagnosticsLock) {
                        diagnostics.addLast(line)
                        while (diagnostics.size > DIAGNOSTIC_LINES) diagnostics.removeFirst()
                    }
                }
            } catch (_: IOException) {
                // The process went away; nothing left to drain.
            }
        }.apply { isDaemon = true }.also { it.start() }

        send("boardsize $BOARD_SIZE")
        send("clear_board")
        send("komi $komi")
        send("level $level")
        // Places the stones on the star points and leaves White to open.
        if (handicap >= 2) send("fixed_handicap $handicap")
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
            val line = input.readLine() ?: throw GnuGoException(deathMessage(command))
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

    /**
     * What to say about an engine that stopped answering, having first asked it why.
     *
     * Everything it said goes to the log, where `adb logcat` can reach it. The phone
     * itself gets the one line that makes the failure repeatable: the seed, which replays
     * the same game move for move.
     */
    private fun deathMessage(command: String): String {
        // stderr is a separate pipe drained by a separate thread, so it is routinely a
        // line or two behind stdout closing. Its last words are the point of this path.
        stderrDrain?.join(DRAIN_GRACE_MS)
        val said = synchronized(diagnosticsLock) { diagnostics.toList() }
        if (said.isEmpty()) return "The engine stopped during '$command'"

        Log.e(TAG, "Engine died during '$command':\n" + said.joinToString("\n"))
        val bug = said.firstOrNull { it.contains(BUG_REPORT) }
            ?: return "The engine stopped during '$command'"
        // gnugo 3.8 (seed 1787830629): You stepped on a bug.
        val seed = SEED_IN_BUG_REPORT.find(bug)?.groupValues?.get(1)
        return if (seed == null) "The engine hit a bug in itself"
        else "The engine hit a bug in itself. Game $seed."
    }

    fun play(color: Stone, point: Point?) {
        send("play ${color.gtp} ${point?.toVertex() ?: "pass"}")
    }

    fun genMove(color: Stone): EngineMove {
        val answer = send("genmove ${color.gtp}").uppercase()
        return when (answer) {
            "PASS" -> EngineMove.Pass
            "RESIGN" -> EngineMove.Resign
            // An answer that is not a point is not a pass. A dying engine can put its
            // own diagnostics down the same pipe its moves come along, and reading that
            // as a pass would quietly hand the game over instead of saying what happened.
            else -> parseVertex(answer)?.let(EngineMove::Play)
                ?: throw GnuGoException("The engine answered '$answer', which is not a move")
        }
    }

    fun undo() {
        send("undo")
    }

    /**
     * The move the engine would play, without playing it.
     *
     * `reg_genmove` is genmove with the side effect removed, which is exactly what a hint
     * wants: the suggestion can be dropped straight into the preview slot and either
     * accepted or overruled by tapping somewhere else.
     */
    fun suggest(color: Stone): Point? =
        parseVertex(send("reg_genmove ${color.gtp}"))

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

    private companion object {
        const val TAG = "GnuGo"

        /** Enough for an assertion, the board it happened on, and the SGF beneath it. */
        const val DIAGNOSTIC_LINES = 200
        const val DRAIN_GRACE_MS = 1000L
        const val BUG_REPORT = "You stepped on a bug"
        val SEED_IN_BUG_REPORT = Regex("\\(seed (\\d+)\\)")
    }
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
