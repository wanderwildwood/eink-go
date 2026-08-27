package com.wanderwildwood.einkgo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
                title = {
                    TextMMD(
                        text = state.statusText(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                    )
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
            TextMMD(
                text = "Black ${state.blackCaptures}   ·   White ${state.whiteCaptures}",
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
            )

            Goban(
                state = state,
                onTap = onTap,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            )

            TextMMD(
                text = state.message.orEmpty(),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
            )

            BottomBar(
                state = state,
                onMenu = { menuOpen = true },
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

@Composable
private fun BottomBar(
    state: GameState,
    onMenu: () -> Unit,
    onUndo: () -> Unit,
    onPass: () -> Unit,
    onConfirm: () -> Unit,
) {
    val canAct = state.phase == Phase.PLAYING && state.isHumanTurn

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButtonMMD(
            onClick = onMenu,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
        ) { TextMMD(text = "MENU", fontSize = 14.sp) }

        OutlinedButtonMMD(
            onClick = onUndo,
            enabled = state.canUndo,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
        ) { TextMMD(text = "UNDO", fontSize = 14.sp) }

        OutlinedButtonMMD(
            onClick = onPass,
            enabled = canAct,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
        ) { TextMMD(text = "PASS", fontSize = 14.sp) }

        if (state.preview != null) {
            ButtonMMD(
                onClick = onConfirm,
                modifier = Modifier
                    .weight(1.2f)
                    .height(46.dp),
            ) { TextMMD(text = "PLACE", fontSize = 14.sp) }
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
