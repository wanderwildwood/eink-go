package com.wanderwildwood.kuroban.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.kuroban.game.GameState
import com.wanderwildwood.kuroban.game.Phase
import com.wanderwildwood.kuroban.game.Point
import com.wanderwildwood.kuroban.game.Stone
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(
    state: GameState,
    onTap: (Point) -> Unit,
    onConfirm: () -> Unit,
    onPass: () -> Unit,
    onHint: () -> Unit,
    onUndo: () -> Unit,
    onResign: () -> Unit,
    onNewGame: () -> Unit,
    onSetupTap: (Point, Stone?) -> Unit,
    onFirstToMove: (Stone) -> Unit,
    onPlayFromSetup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // What a tap on the board puts down while a position is being set up, or null for
    // taking a stone off. It is only ever about the next tap, so it lives here rather
    // than in the game.
    var placing by remember { mutableStateOf<Stone?>(Stone.BLACK) }
    // Keyed on the result, so a new result opens the dialog again after an earlier one
    // was dismissed to look at the board.
    var resultDismissed by remember(state.result) { mutableStateOf(false) }
    var breakageDismissed by remember(state.message) { mutableStateOf(false) }

    // Once a game is over there is nothing left to resign from, and its verdict already
    // carries the only two things left to do. So asking for the menu after the game has
    // ended asks for that dialog back, rather than a menu with a dead button in it.
    fun openMenu() {
        when {
            state.phase == Phase.BROKEN -> breakageDismissed = false
            state.result != null -> resultDismissed = false
            else -> menuOpen = true
        }
    }

    BackHandler { openMenu() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // Everything that is not the board lives up here now: whose turn it is, what
            // each side has captured, and the way out. The board gets the rest.
            TopAppBarMMD(
                title = {
                    // The count sits in the middle of the bar rather than tucked against
                    // the menu: equal air either side of it, so it reads as its own thing
                    // between whose turn it is and the way out, instead of as a label on
                    // the button next to it.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        StatusTitle(
                            state = state,
                            // Two things live in this slot, and both are pressed for the
                            // same reason - it is where the sentence you want to change or
                            // to read again already is. Setting up, it says which colour
                            // plays out of the position and swaps that colour when pressed.
                            // A dismissed result is not a discarded one: the verdict stays
                            // here, so here is where you press to ask for the rest of it.
                            onPress = when {
                                state.phase == Phase.SETUP ->
                                    { -> onFirstToMove(state.toMove.other) }

                                state.phase == Phase.FINISHED && state.result != null ->
                                    { -> resultDismissed = false }

                                else -> null
                            },
                        )
                        Spacer(Modifier.weight(1f))
                        // A dead engine has nothing to say about prisoners, and neither has
                        // a board nobody has played on yet. The room they were taking is
                        // room the explanation and the setting-up need.
                        if (state.phase != Phase.BROKEN && state.phase != Phase.SETUP) {
                            Captures(state)
                        }
                        Spacer(Modifier.weight(1f))
                    }
                },
                actions = {
                    MenuButton(onClick = { openMenu() })
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Goban(
                state = state,
                onTap = { point ->
                    if (state.phase == Phase.SETUP) onSetupTap(point, placing) else onTap(point)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )

            if (state.phase == Phase.SETUP) {
                SetupBar(
                    placing = placing,
                    onPlacing = { placing = it },
                    onPlay = onPlayFromSetup,
                )
            } else {
                BottomBar(
                    state = state,
                    onUndo = onUndo,
                    onPass = onPass,
                    onHint = onHint,
                    onConfirm = onConfirm,
                )
            }
        }
    }

    if (menuOpen) {
        MenuDialog(
            canResign = state.phase == Phase.PLAYING,
            settingUp = state.phase == Phase.SETUP,
            onResign = {
                menuOpen = false
                onResign()
            },
            onNewGame = {
                menuOpen = false
                onNewGame()
            },
            onDismiss = { menuOpen = false },
        )
    }

    val result = state.result
    if (result != null && !resultDismissed && !menuOpen) {
        EInkDialog(onDismiss = { resultDismissed = true }) {
            TextMMD(text = result, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            if (state.wasScored) {
                TextMMD(
                    text = buildString {
                        append("Scored with komi ${state.config.komi}")
                        if (state.config.handicap >= 2) {
                            append(" and ${state.config.handicap} handicap stones")
                        }
                        append(".")
                        if (state.dead.isNotEmpty()) {
                            append(" Stones marked × were counted as dead.")
                        }
                    },
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(18.dp))
            } else {
                Spacer(Modifier.height(14.dp))
            }
            ButtonMMD(
                onClick = onNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { TextMMD(text = "New game", fontSize = 15.sp) }
            Spacer(Modifier.height(10.dp))
            OutlinedButtonMMD(
                onClick = { resultDismissed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { TextMMD(text = "Look at board", fontSize = 15.sp) }
        }
    }

    // The engine dying ends that game: there is no move to take back and no way to carry
    // on. It used to be reported by putting the reason in the title bar, where a line and
    // a half of it fitted - so the one thing worth reading, which is the game number that
    // makes it happen again, was the part that got cut off.
    if (state.phase == Phase.BROKEN && !breakageDismissed && !menuOpen) {
        EInkDialog(onDismiss = { breakageDismissed = true }) {
            TextMMD(text = "The game stopped", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            TextMMD(
                text = state.message ?: "The engine stopped answering.",
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(18.dp))
            ButtonMMD(
                onClick = onNewGame,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { TextMMD(text = "New game", fontSize = 15.sp) }
            Spacer(Modifier.height(10.dp))
            OutlinedButtonMMD(
                onClick = { breakageDismissed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { TextMMD(text = "Look at board", fontSize = 15.sp) }
        }
    }
}

/**
 * Whose turn it is, and what is happening.
 *
 * The stone says which colour; the words say something the stone cannot. Writing "Black
 * to play" beside a black stone would be saying the same thing twice and taking the room
 * to do it. A passing message takes the same slot - it is always about the turn that has
 * just changed hands, so it never competes with the status for meaning.
 *
 * When a game is over this is also the way back to the full result - the score is worth
 * reading twice, and the komi, the handicap and which stones were counted as dead are
 * only in the dialog. [onReopen] is null whenever there is nothing to reopen.
 */
@Composable
private fun StatusTitle(state: GameState, onPress: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onPress == null) Modifier else Modifier.clickable(onClick = onPress),
    ) {
        val turnStone = when (state.phase) {
            Phase.PLAYING, Phase.THINKING, Phase.SETUP -> state.toMove
            else -> null
        }
        if (turnStone != null) {
            StoneGlyph(stone = turnStone, size = 17.dp)
            Spacer(Modifier.width(10.dp))
        }
        TextMMD(
            text = state.message ?: state.statusText(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Prisoners held by each side. */
@Composable
private fun RowScope.Captures(state: GameState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StoneGlyph(stone = Stone.BLACK, size = 12.dp)
        Spacer(Modifier.width(5.dp))
        TextMMD(text = "${state.blackCaptures}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        StoneGlyph(stone = Stone.WHITE, size = 12.dp)
        Spacer(Modifier.width(5.dp))
        TextMMD(text = "${state.whiteCaptures}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MenuButton(onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            for (i in 0..2) {
                val y = size.height * (0.18f + 0.32f * i)
                drawLine(ink, Offset(0f, y), Offset(size.width, y), stroke)
            }
        }
    }
}

/**
 * Undo, Pass, Hint, Place - always all four, in the same places.
 *
 * Place is styled rather than hidden when there is nothing to place. A button that comes
 * and goes reflows the row underneath the board on every single move, and on E Ink that
 * is a visible flash for no information gained.
 */
@Composable
private fun BottomBar(
    state: GameState,
    onUndo: () -> Unit,
    onPass: () -> Unit,
    onHint: () -> Unit,
    onConfirm: () -> Unit,
) {
    val canAct = state.phase == Phase.PLAYING && state.isHumanTurn

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButtonMMD(
            onClick = onUndo,
            enabled = state.canUndo,
            contentPadding = TIGHT,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) { TextMMD(text = "Undo", fontSize = 14.sp, maxLines = 1) }

        OutlinedButtonMMD(
            onClick = onPass,
            enabled = canAct,
            contentPadding = TIGHT,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) { TextMMD(text = "Pass", fontSize = 14.sp, maxLines = 1) }

        OutlinedButtonMMD(
            onClick = onHint,
            enabled = canAct,
            contentPadding = TIGHT,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) { TextMMD(text = "Hint", fontSize = 14.sp, maxLines = 1) }

        if (state.preview != null) {
            ButtonMMD(
                onClick = onConfirm,
                contentPadding = TIGHT,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) { TextMMD(text = "Place", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
        } else {
            OutlinedButtonMMD(
                onClick = {},
                enabled = false,
                contentPadding = TIGHT,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) { TextMMD(text = "Place", fontSize = 14.sp, maxLines = 1) }
        }
    }
}

/**
 * Black, White, Erase, Play - what a tap on the board does, and the way out.
 *
 * The first three are one choice and the last is an action, which is the same shape the
 * playing bar has: three things you might do and, at the end, the one that commits. The
 * chosen stone is solid and the others are outlined, the way every choice on the new-game
 * screen is drawn, so nothing here needs a colour or a check mark to read on E Ink.
 *
 * There is no Undo and no Clear. A stone put down in the wrong place comes off with one
 * tap of Erase, and a position not worth keeping is thrown away from the menu.
 */
@Composable
private fun SetupBar(
    placing: Stone?,
    onPlacing: (Stone?) -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((label, stone) in listOf("Black" to Stone.BLACK, "White" to Stone.WHITE, "Erase" to null)) {
            val content: @Composable () -> Unit = {
                TextMMD(text = label, fontSize = 14.sp, maxLines = 1)
            }
            if (placing == stone) {
                ButtonMMD(
                    onClick = { onPlacing(stone) },
                    contentPadding = TIGHT,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { content() }
            } else {
                OutlinedButtonMMD(
                    onClick = { onPlacing(stone) },
                    contentPadding = TIGHT,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) { content() }
            }
        }

        ButtonMMD(
            onClick = onPlay,
            contentPadding = TIGHT,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) { TextMMD(text = "Play", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
    }
}

/** Four buttons across 360dp cannot afford the default 16dp of padding each side. */
private val TIGHT = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)

/**
 * The menu the back button opens, mid-game.
 *
 * Both of the things in it throw away the game being played, and the back button is an
 * easy thing to press by accident, so neither happens on one tap: the button says what it
 * will do, and asks. It forgets being asked after a few seconds, so a menu left open on a
 * phone in a pocket is not a game waiting to be resigned.
 *
 * The way out is first and is the solid one. Opening this dialog by mistake is the most
 * likely reason to be looking at it.
 */
@Composable
private fun MenuDialog(
    canResign: Boolean,
    settingUp: Boolean,
    onResign: () -> Unit,
    onNewGame: () -> Unit,
    onDismiss: () -> Unit,
) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = "Menu", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(18.dp))
        ButtonMMD(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) { TextMMD(text = if (settingUp) "Back to the board" else "Back to game", fontSize = 15.sp) }
        Spacer(Modifier.height(10.dp))
        ConfirmingButton(
            label = "New game",
            armedLabel = if (settingUp) {
                "Throw away this position — tap again"
            } else {
                "Give up this game — tap again"
            },
            onConfirmed = onNewGame,
        )
        Spacer(Modifier.height(10.dp))
        ConfirmingButton(
            label = "Resign",
            armedLabel = "Resign — tap again",
            enabled = canResign,
            onConfirmed = onResign,
        )
    }
}

/**
 * A button that asks in its own face rather than stacking a second dialog on the first.
 *
 * One dialog on top of another is two full repaints to ask one question, and on this
 * screen the question is small enough to fit where the answer goes.
 */
@Composable
private fun ConfirmingButton(
    label: String,
    armedLabel: String,
    onConfirmed: () -> Unit,
    enabled: Boolean = true,
) {
    var armed by remember { mutableStateOf(false) }

    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }

    OutlinedButtonMMD(
        onClick = { if (armed) onConfirmed() else armed = true },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        TextMMD(
            text = if (armed) armedLabel else label,
            fontSize = 15.sp,
            fontWeight = if (armed) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

private fun GameState.statusText(): String = when (phase) {
    Phase.STARTING -> "Starting…"
    // The glyph beside this says which colour, the way it does mid-game.
    Phase.SETUP -> "plays first"
    Phase.THINKING -> "Thinking…"
    Phase.BROKEN -> "Engine stopped"
    Phase.FINISHED -> result ?: "Game over"
    Phase.PLAYING -> "Your move"
}
