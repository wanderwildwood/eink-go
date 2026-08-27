package com.wanderwildwood.einkgo.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.einkgo.engine.EngineMove
import com.wanderwildwood.einkgo.engine.GnuGo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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

    private var engine: GnuGo? = null
    private var movesPlayed = 0
    private var consecutivePasses = 0
    private var firstToMove = Stone.BLACK

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

    fun newGame(config: GameConfig) {
        val previous = engine
        engine = null
        movesPlayed = 0
        consecutivePasses = 0
        moveInFlight.set(false)
        firstToMove = config.firstToMove
        _state.value = GameState(config = config, phase = Phase.STARTING)

        viewModelScope.launch(Dispatchers.IO) {
            previous?.close()
            try {
                val fresh = GnuGo(binaryPath).apply {
                    start(config.difficulty.level, config.komi, config.handicap)
                }
                engine = fresh
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
        _state.value = null
        viewModelScope.launch(Dispatchers.IO) { previous?.close() }
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
                consecutivePasses = 0
                sync(active, Phase.PLAYING)
            }

            EngineMove.Pass -> {
                movesPlayed++
                consecutivePasses++
                if (consecutivePasses >= 2) {
                    finish(active)
                } else {
                    sync(active, Phase.PLAYING, message = "The computer passed")
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

    private fun finish(active: GnuGo) {
        val score = active.finalScore()
        val dead = active.deadStones()
        sync(active, Phase.FINISHED)
        _state.update {
            it?.copy(result = formatResult(score), dead = dead, message = null)
        }
    }

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
    }
}
