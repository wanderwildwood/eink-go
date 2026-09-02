package com.wanderwildwood.kuroban.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.kuroban.engine.EngineMove
import com.wanderwildwood.kuroban.engine.GnuGo
import com.wanderwildwood.kuroban.engine.writePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/**
 * Owns the engine process and the game in front of it.
 *
 * A null state means no game is in progress and the new-game screen is showing.
 *
 * Whose turn it is is never stored: one side opens, every move alternates, and passes count
 * as moves, so the side to move is always derivable from how many moves have been played.
 * That leaves one number to keep straight instead of two that can disagree. Black opens
 * unless there is a handicap, in which case its stones are already down and White opens.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val binaryPath =
        File(application.applicationInfo.nativeLibraryDir, ENGINE_BINARY).absolutePath

    private val store = GameStore(File(application.filesDir, "game-in-progress.txt"))

    /**
     * Where a hand-placed position is written for the engine to read.
     *
     * Scratch, and rewritten on every stone: what is worth keeping is saved by [store]
     * alongside the rest of the game.
     */
    private val positionFile = File(application.filesDir, "position.sgf")

    /** Every move of the current game, a null being a pass. This is what gets saved. */
    private val moves = mutableListOf<Point?>()

    private var engine: GnuGo? = null

    /**
     * What the engine is playing this game with.
     *
     * Left to itself GNU Go seeds from the clock, so no game can ever be played twice -
     * including the one that just crashed it. Choosing the seed here keeps every game
     * different and makes any single one of them repeatable, which is the whole of a bug
     * report for an engine that reports its own seed as it dies.
     */
    private var seed = newSeed()
    private var movesPlayed = 0
    private var consecutivePasses = 0
    private var firstToMove = Stone.BLACK

    /** The stones placed by hand, while they are still being placed and afterwards. */
    private var setupBlack: Set<Point> = emptySet()
    private var setupWhite: Set<Point> = emptySet()

    /**
     * Held for as long as a move is being played out.
     *
     * The guards in the handlers below run on the main thread but the work happens in a
     * coroutine, so without this a second tap that lands before the first has updated the
     * state passes the same guard and plays a second move. On a slow E Ink refresh that is
     * not a rare race: two quick taps on Pass would end the game by accident.
     */
    private val moveInFlight = AtomicBoolean(false)

    private val _state = MutableStateFlow<GameState?>(null)
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val toMove: Stone
        get() = if (movesPlayed % 2 == 0) firstToMove else firstToMove.other

    init {
        restore()
    }

    /**
     * Rebuilds the game that was in progress when the app was last killed, by starting a
     * fresh engine and replaying the moves into it. The engine works out the position, the
     * captures, the ko and the move history that Undo needs - none of which has to be
     * stored, because all of it follows from the moves.
     */
    private fun restore() {
        val saved = store.load() ?: return
        val config = saved.config
        seed = saved.seed ?: newSeed()
        movesPlayed = 0
        consecutivePasses = 0
        firstToMove = config.firstToMove
        setupBlack = config.setup?.black.orEmpty()
        setupWhite = config.setup?.white.orEmpty()
        moves.clear()
        _state.value = GameState(config = config, phase = Phase.STARTING)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fresh = GnuGo(binaryPath).apply {
                    start(config.difficulty.level, config.komi, config.handicap, seed)
                }
                engine = fresh
                // The hand-placed stones go down before anything played on top of them.
                if (config.setup != null) loadSetup(fresh)
                if (saved.editing) {
                    sync(fresh, Phase.SETUP)
                    return@launch
                }
                for (move in saved.moves) {
                    fresh.play(toMove, move)
                    movesPlayed++
                    moves += move
                }
                consecutivePasses = saved.moves.reversed().takeWhile { it == null }.count()

                val owedAMove = config.opponent == Opponent.COMPUTER &&
                    toMove != config.humanColor
                when {
                    consecutivePasses >= 2 -> finish(fresh)
                    // Killed while the engine was thinking: it still owes its reply.
                    owedAMove -> {
                        sync(fresh, Phase.THINKING)
                        computerTurn(fresh)
                    }

                    else -> sync(fresh, Phase.PLAYING)
                }
            } catch (e: Exception) {
                // A save that will not replay is worse than none: drop it rather than
                // meet the player with the same failure every time they open the app.
                store.clear()
                broken(e)
            }
        }
    }

    private fun persist(editing: Boolean = false) {
        val config = _state.value?.config ?: return
        // Hand-placed stones keep changing while they are being placed, so they are taken
        // from here rather than from whatever the config was carrying when it started.
        val toSave = if (config.setup == null) config else config.copy(setup = currentSetup())
        store.save(toSave, moves.toList(), seed, editing)
    }

    fun newGame(config: GameConfig) {
        val previous = engine
        engine = null
        seed = newSeed()
        movesPlayed = 0
        consecutivePasses = 0
        moveInFlight.set(false)
        firstToMove = config.firstToMove
        setupBlack = config.setup?.black.orEmpty()
        setupWhite = config.setup?.white.orEmpty()
        moves.clear()
        _state.value = GameState(config = config, phase = Phase.STARTING)

        viewModelScope.launch(Dispatchers.IO) {
            previous?.close()
            try {
                val fresh = GnuGo(binaryPath).apply {
                    start(config.difficulty.level, config.komi, config.handicap, seed)
                }
                engine = fresh
                // A setup goes to the board editor rather than straight into a game -
                // including an empty one, which is what asking to set a position up
                // gives you before you have placed anything.
                if (config.setup != null) {
                    loadSetup(fresh)
                    sync(fresh, Phase.SETUP)
                    return@launch
                }
                val computerOpens = config.opponent == Opponent.COMPUTER &&
                    config.humanColor != config.firstToMove
                if (computerOpens) {
                    sync(fresh, Phase.THINKING)
                    computerTurn(fresh)
                } else {
                    sync(fresh, Phase.PLAYING)
                }
            } catch (e: Exception) {
                broken(e)
            }
        }
    }

    /** Leaves the game and returns to the new-game screen. */
    fun endGame() {
        val previous = engine
        engine = null
        moves.clear()
        _state.value = null
        viewModelScope.launch(Dispatchers.IO) {
            store.clear()
            previous?.close()
        }
    }

    /**
     * A tap on an intersection previews a stone there; a second tap on the same one
     * plays it. The Place button does the same thing for anyone who would rather press
     * a button than tap twice.
     */
    fun tap(point: Point) {
        val current = _state.value ?: return
        if (current.phase != Phase.PLAYING || !current.isHumanTurn) return
        if (point !in current.legal) return
        if (current.preview == point) confirmMove() else _state.update { it?.copy(preview = point, message = null) }
    }

    /**
     * Places, replaces or lifts a stone while a position is being set up.
     *
     * [placing] is the stone a tap puts down, or null to take one off. Tapping a point
     * that already holds the stone being placed does nothing: taking a stone off is what
     * the Erase setting is for, and a tap that quietly undid itself would be worse than
     * a tap that does nothing.
     */
    fun setupTap(point: Point, placing: Stone?) {
        val current = _state.value ?: return
        val active = engine ?: return
        if (current.phase != Phase.SETUP) return

        val standing = when {
            point in setupBlack -> Stone.BLACK
            point in setupWhite -> Stone.WHITE
            else -> null
        }
        if (standing == placing) return

        if (!moveInFlight.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val black = setupBlack.toMutableSet().apply { remove(point) }
                val white = setupWhite.toMutableSet().apply { remove(point) }
                when (placing) {
                    Stone.BLACK -> black += point
                    Stone.WHITE -> white += point
                    null -> Unit
                }

                loadSetup(active, black, white)
                // Lifting a stone can only give the stones around it more room, so the
                // only edit that can produce an impossible position is one that adds.
                if (placing != null && strangles(active, point, black, white)) {
                    loadSetup(active)
                    // The bar holds about this much at the size it is set in, and the
                    // sentence is cut rather than wrapped when it does not fit.
                    _state.update { it?.copy(message = "That leaves no liberties") }
                } else {
                    setupBlack = black
                    setupWhite = white
                    sync(active, Phase.SETUP)
                }
            } catch (e: Exception) {
                broken(e)
            } finally {
                moveInFlight.set(false)
            }
        }
    }

    /** Which colour plays the first move out of the position being set up. */
    fun setFirstToMove(stone: Stone) {
        val current = _state.value ?: return
        val active = engine ?: return
        if (current.phase != Phase.SETUP || stone == firstToMove) return

        if (!moveInFlight.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                firstToMove = stone
                loadSetup(active)
                sync(active, Phase.SETUP)
            } catch (e: Exception) {
                broken(e)
            } finally {
                moveInFlight.set(false)
            }
        }
    }

    /** Leaves the stones where they are and starts playing from them. */
    fun startFromSetup() {
        val current = _state.value ?: return
        val active = engine ?: return
        if (current.phase != Phase.SETUP) return

        if (!moveInFlight.compareAndSet(false, true)) return
        val config = current.config.copy(setup = currentSetup())
        _state.update { it?.copy(config = config, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val computerOpens = config.opponent == Opponent.COMPUTER &&
                    config.humanColor != firstToMove
                if (computerOpens) {
                    sync(active, Phase.THINKING)
                    computerTurn(active)
                } else {
                    sync(active, Phase.PLAYING)
                }
            } catch (e: Exception) {
                broken(e)
            } finally {
                moveInFlight.set(false)
            }
        }
    }

    private fun currentSetup() = Setup(setupBlack, setupWhite, firstToMove)

    /**
     * Whether the stone just placed at [placed] has left some group without a liberty.
     *
     * Only the new stone's own group and whatever it touches can have lost one, so those
     * are the only points worth asking about - and the engine is the one asked, as it is
     * for every other question with a Go answer in it.
     */
    private fun strangles(
        active: GnuGo,
        placed: Point,
        black: Set<Point>,
        white: Set<Point>,
    ): Boolean = (listOf(placed) + placed.neighbours())
        .filter { it in black || it in white }
        .any { active.liberties(it) == 0 }

    /** Puts a hand-placed position on the engine's board, in place of whatever is there. */
    private fun loadSetup(
        active: GnuGo,
        black: Set<Point> = setupBlack,
        white: Set<Point> = setupWhite,
    ) {
        val config = _state.value?.config ?: return
        writePosition(positionFile, black, white, firstToMove, config.komi)
        active.loadPosition(positionFile, config.komi, config.difficulty.level)
    }

    fun confirmMove() {
        val current = _state.value ?: return
        val point = current.preview ?: return
        val active = engine ?: return
        if (current.phase != Phase.PLAYING || !current.isHumanTurn) return

        if (!moveInFlight.compareAndSet(false, true)) return
        _state.update { it?.copy(preview = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                active.play(toMove, point)
                movesPlayed++
                moves += point
                consecutivePasses = 0
                continueAfterHumanMove(active, current.config)
            } catch (e: Exception) {
                broken(e)
            } finally {
                moveInFlight.set(false)
            }
        }
    }

    fun pass() {
        val current = _state.value ?: return
        val active = engine ?: return
        if (current.phase != Phase.PLAYING || !current.isHumanTurn) return

        if (!moveInFlight.compareAndSet(false, true)) return
        _state.update { it?.copy(preview = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                active.play(toMove, null)
                movesPlayed++
                moves += null
                consecutivePasses++
                if (consecutivePasses >= 2) {
                    finish(active)
                } else {
                    continueAfterHumanMove(active, current.config, message = "You passed")
                }
            } catch (e: Exception) {
                broken(e)
            } finally {
                moveInFlight.set(false)
            }
        }
    }

    /**
     * Asks the engine what it would play and drops that into the preview slot, so a hint
     * is accepted by pressing Place and refused by tapping anywhere else. Nothing is
     * committed on the engine's board by asking.
     */
    fun hint() {
        val current = _state.value ?: return
        val active = engine ?: return
        if (current.phase != Phase.PLAYING || !current.isHumanTurn) return

        if (!moveInFlight.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val suggestion = active.suggest(toMove)
                _state.update {
                    it?.copy(
                        preview = suggestion,
                        message = if (suggestion == null) "Nothing worth suggesting" else null,
                    )
                }
            } catch (e: Exception) {
                broken(e)
            } finally {
                moveInFlight.set(false)
            }
        }
    }

    fun resign() {
        val current = _state.value ?: return
        if (current.phase == Phase.BROKEN) return
        viewModelScope.launch(Dispatchers.IO) { store.clear() }
        _state.update { current ->
            current ?: return@update null
            current.copy(
                phase = Phase.FINISHED,
                preview = null,
                legal = emptySet(),
                result = formatResult(score = "", resignedBy = current.toMove),
                wasScored = false,
            )
        }
    }

    /**
     * Takes back the last move, and against the computer its reply as well. A finished
     * game can be taken back into too, so a game lost by one careless move at the end is
     * still worth looking at.
     */
    fun undo() {
        val current = _state.value ?: return
        val active = engine ?: return
        if (!current.canUndo) return

        if (!moveInFlight.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val steps = minOf(current.movesPerUndo, movesPlayed)
                repeat(steps) { active.undo() }
                repeat(steps) { moves.removeLastOrNull() }
                movesPlayed -= steps
                // Whether the passes that ended the game are still on the board is no
                // longer knowable cheaply; zero is the safe answer, since its only cost
                // is that a game being closed out needs its two passes played again.
                consecutivePasses = 0
                _state.update { it?.copy(result = null, dead = emptySet(), wasScored = false, message = null) }
                sync(active, Phase.PLAYING)
            } catch (e: Exception) {
                broken(e)
            } finally {
                moveInFlight.set(false)
            }
        }
    }

    fun dismissMessage() = _state.update { it?.copy(message = null) }

    /** A counted game: who won by how much, and which stones were counted as dead. */
    private class Counted(val score: String, val dead: Set<Point>)

    private fun continueAfterHumanMove(active: GnuGo, config: GameConfig, message: String? = null) {
        if (config.opponent == Opponent.COMPUTER) {
            sync(active, Phase.THINKING, message)
            computerTurn(active)
        } else {
            sync(active, Phase.PLAYING, message)
        }
    }

    private fun computerTurn(active: GnuGo) {
        when (val move = active.genMove(toMove)) {
            is EngineMove.Play -> {
                movesPlayed++
                moves += move.point
                consecutivePasses = 0
                sync(active, Phase.PLAYING)
            }

            EngineMove.Pass -> {
                movesPlayed++
                moves += null
                consecutivePasses++
                if (consecutivePasses >= 2) {
                    finish(active)
                } else {
                    sync(active, Phase.PLAYING, message = "GNU Go passed")
                }
            }

            EngineMove.Resign -> {
                _state.update { current ->
                    current ?: return@update null
                    current.copy(
                        phase = Phase.FINISHED,
                        preview = null,
                        legal = emptySet(),
                        result = formatResult(score = "", resignedBy = toMove),
                        wasScored = false,
                    )
                }
            }
        }
    }

    /**
     * Counts the finished game - and if the engine dies counting it, builds another one
     * and asks that.
     *
     * GNU Go can hit an assertion inside its own scoring code: `findstones` handed a pass
     * where a stone belongs, in `final_score`. It is not reliable - the same finished
     * position crashed twice and then counted correctly on the third attempt, and never
     * crashes at all when the same commands are replayed outside the app - which is what
     * makes a retry worth having. Nothing is lost when it happens: every move is known,
     * so a fresh engine can be handed the whole game and asked again.
     *
     * A second failure falls through to the caller, which reports it and stops.
     */
    private fun finish(active: GnuGo) {
        store.clear()
        val first = runCatching { count(active) }
        val (counter, counted) = when {
            first.isSuccess -> active to first.getOrThrow()
            else -> rebuiltEngine().let { it to count(it) }
        }
        sync(counter, Phase.FINISHED)
        _state.update {
            it?.copy(
                result = formatResult(counted.score),
                dead = counted.dead,
                // Two passes and a count is the only way here; resigning and taking a
                // finished game back both clear this. Without it the dialog never showed
                // the komi, the handicap or the dead stones it was written to explain.
                wasScored = true,
                message = null,
            )
        }
    }

    private fun count(active: GnuGo) = Counted(active.finalScore(), active.deadStones())

    /**
     * A new engine with this game replayed into it, replacing the one that died.
     *
     * The seed is a new one on purpose. The moves decide the position and the position is
     * what gets counted, so a different seed costs nothing and does not walk the engine
     * back down whichever path it fell off.
     */
    private fun rebuiltEngine(): GnuGo {
        val config = _state.value?.config ?: throw IllegalStateException("No game to count")
        runCatching { engine?.close() }
        engine = null
        seed = newSeed()
        val fresh = GnuGo(binaryPath).apply {
            start(config.difficulty.level, config.komi, config.handicap, seed)
        }
        // Same as anywhere else the game is rebuilt: the stones that were placed by hand
        // are the board the moves were played on, so they go down before the moves do.
        if (config.setup != null) {
            writePosition(positionFile, setupBlack, setupWhite, firstToMove, config.komi)
            fresh.loadPosition(positionFile, config.komi, config.difficulty.level)
        }
        moves.forEachIndexed { index, move -> fresh.play(colourOf(index), move) }
        engine = fresh
        return fresh
    }

    /** Which side played the move at [index]. One side opens and every move alternates. */
    private fun colourOf(index: Int): Stone =
        if (index % 2 == 0) firstToMove else firstToMove.other

    /** Reads the whole position back out of the engine, which is the only thing that knows it. */
    private fun sync(active: GnuGo, phase: Phase, message: String? = null) {
        val black = active.stones(Stone.BLACK)
        val white = active.stones(Stone.WHITE)
        val blackCaptures = active.captures(Stone.BLACK)
        val whiteCaptures = active.captures(Stone.WHITE)
        val lastMove = active.lastMove()
        val legal = if (phase == Phase.PLAYING) active.legalMoves(toMove) else emptySet()

        _state.update { current ->
            current?.copy(
                phase = phase,
                black = black,
                white = white,
                toMove = toMove,
                blackCaptures = blackCaptures,
                whiteCaptures = whiteCaptures,
                legal = legal,
                lastMove = lastMove,
                movesPlayed = movesPlayed,
                message = message,
            )
        }

        // Saving here rather than at each call site means no new way of ending a move can
        // forget to do it. A finished or broken game is not saved - and finish() and
        // resign() clear the file outright, so opening the app lands on a new game.
        when (phase) {
            Phase.PLAYING, Phase.THINKING -> persist()
            // A position half placed is worth keeping too. Copying a problem out of a
            // book is exactly the moment somebody puts the phone down to look at the book.
            Phase.SETUP -> persist(editing = true)
            else -> Unit
        }
    }

    private fun broken(cause: Exception) {
        engine = null
        _state.update {
            it?.copy(
                phase = Phase.BROKEN,
                legal = emptySet(),
                preview = null,
                message = cause.message ?: "The engine stopped",
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        val previous = engine
        engine = null
        // Not viewModelScope: it is cancelled by the time this runs.
        Thread { previous?.close() }.start()
    }

    private companion object {
        /**
         * The engine is an executable, but Android only unpacks and grants exec
         * permission to files named lib*.so inside jniLibs.
         */
        const val ENGINE_BINARY = "libgnugo.so"

        /** Positive, because GNU Go prints its seed as a signed decimal and 0 means "pick one". */
        fun newSeed(): Int = Random.nextInt(1, Int.MAX_VALUE)
    }
}
