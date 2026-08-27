package com.wanderwildwood.einkgo.ui

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
import com.wanderwildwood.einkgo.game.GameState
import com.wanderwildwood.einkgo.game.Phase
import com.wanderwildwood.einkgo.game.Point
import com.wanderwildwood.einkgo.game.Stone

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
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Keyed on the result, so a new result opens the dialog again after an earlier one
    // was dismissed to look at the board.
    var resultDismissed by remember(state.result) { mutableStateOf(false) }
    var breakageDismissed by remember(state.message) { mutableStateOf(false) }

    BackHandler { menuOpen = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            // Everything that is not the board lives up here now: whose turn it is, what
            // each side has captured, and the way out. The board gets the rest.
            TopAppBarMMD(
                title = {
                    StatusTitle(
                        state = state,
                        // A dismissed result is not a discarded one: the verdict stays in
                        // this slot, so the slot is where you would press to ask for the
                        // rest of it back.
                        onReopen = { resultDismissed = false }.takeIf {
                            state.phase == Phase.FINISHED && state.result != null
                        },
                    )
                },
                actions = {
                    // A dead engine has nothing to say about prisoners, and the room it
                    // was taking is room the explanation needs.
                    if (state.phase != Phase.BROKEN) Captures(state)
                    MenuButton(onClick = { menuOpen = true })
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
                onTap = onTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )

            BottomBar(
                state = state,
                onUndo = onUndo,
                onPass = onPass,
                onHint = onHint,
                onConfirm = onConfirm,
            )
        }
    }

    if (menuOpen) {
        MenuDialog(
            canResign = state.phase == Phase.PLAYING,
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
            ) { TextMMD(text = "NEW GAME", fontSize = 15.sp) }
            Spacer(Modifier.height(10.dp))
            OutlinedButtonMMD(
                onClick = { resultDismissed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { TextMMD(text = "LOOK AT BOARD", fontSize = 15.sp) }
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
            ) { TextMMD(text = "NEW GAME", fontSize = 15.sp) }
            Spacer(Modifier.height(10.dp))
            OutlinedButtonMMD(
                onClick = { breakageDismissed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { TextMMD(text = "LOOK AT BOARD", fontSize = 15.sp) }
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
private fun StatusTitle(state: GameState, onReopen: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onReopen == null) Modifier else Modifier.clickable(onClick = onReopen),
    ) {
        val turnStone = when (state.phase) {
            Phase.PLAYING, Phase.THINKING -> state.toMove
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

/** Four buttons across 360dp cannot afford the default 16dp of padding each side. */
private val TIGHT = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)

@Composable
private fun MenuDialog(
    canResign: Boolean,
    onResign: () -> Unit,
    onNewGame: () -> Unit,
    onDismiss: () -> Unit,
) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = "Menu", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(18.dp))
        ButtonMMD(
            onClick = onNewGame,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) { TextMMD(text = "NEW GAME", fontSize = 15.sp) }
        Spacer(Modifier.height(10.dp))
        OutlinedButtonMMD(
            onClick = onResign,
            enabled = canResign,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) { TextMMD(text = "RESIGN", fontSize = 15.sp) }
        Spacer(Modifier.height(10.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) { TextMMD(text = "BACK TO GAME", fontSize = 15.sp) }
    }
}

private fun GameState.statusText(): String = when (phase) {
    Phase.STARTING -> "Starting…"
    Phase.THINKING -> "Thinking…"
    Phase.BROKEN -> "Engine stopped"
    Phase.FINISHED -> result ?: "Game over"
    Phase.PLAYING -> "Your move"
}
