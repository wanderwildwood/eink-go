package com.wanderwildwood.einkgo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
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
    onUndo: () -> Unit,
    onResign: () -> Unit,
    onNewGame: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Keyed on the result, so a new result opens the dialog again after an earlier one
    // was dismissed to look at the board.
    var resultDismissed by remember(state.result) { mutableStateOf(false) }

    BackHandler { menuOpen = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { StatusTitle(state) },
                actions = { MenuButton(onClick = { menuOpen = true }) },
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
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )

            Captures(state)

            // Fixed height: a line that appears and disappears would shove the board up
            // and down the screen, and every one of those shoves is a full E Ink repaint.
            TextMMD(
                text = state.message.orEmpty(),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .padding(top = 2.dp),
            )

            BottomBar(
                state = state,
                onUndo = onUndo,
                onPass = onPass,
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
            TextMMD(
                text = if (state.dead.isEmpty()) {
                    "Scored with komi 6.5."
                } else {
                    "Scored with komi 6.5. Stones marked × were counted as dead."
                },
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
                onClick = { resultDismissed = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) { TextMMD(text = "LOOK AT BOARD", fontSize = 15.sp) }
        }
    }
}

/** Whose turn it is, said with a stone rather than only with the word for its colour. */
@Composable
private fun StatusTitle(state: GameState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val turnStone = when (state.phase) {
            Phase.PLAYING, Phase.THINKING -> state.toMove
            else -> null
        }
        if (turnStone != null) {
            StoneGlyph(stone = turnStone, size = 16.dp)
            Spacer(Modifier.width(10.dp))
        }
        TextMMD(
            text = state.statusText(),
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Prisoners held by each side. */
@Composable
private fun Captures(state: GameState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        StoneGlyph(stone = Stone.BLACK)
        Spacer(Modifier.width(6.dp))
        TextMMD(text = "${state.blackCaptures}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(28.dp))
        StoneGlyph(stone = Stone.WHITE)
        Spacer(Modifier.width(6.dp))
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
 * Undo, Pass, Place - always all three, in the same places.
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
    onConfirm: () -> Unit,
) {
    val canAct = state.phase == Phase.PLAYING && state.isHumanTurn
    val hasPreview = state.preview != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButtonMMD(
            onClick = onUndo,
            enabled = state.canUndo,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) { TextMMD(text = "Undo", fontSize = 15.sp) }

        OutlinedButtonMMD(
            onClick = onPass,
            enabled = canAct,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
        ) { TextMMD(text = "Pass", fontSize = 15.sp) }

        if (hasPreview) {
            ButtonMMD(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) { TextMMD(text = "Place", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        } else {
            OutlinedButtonMMD(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
            ) { TextMMD(text = "Place", fontSize = 15.sp) }
        }
    }
}

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
    Phase.PLAYING -> if (toMove == Stone.BLACK) "Black to play" else "White to play"
}
