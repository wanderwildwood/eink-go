package com.wanderwildwood.kuroban

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.kuroban.game.GameViewModel
import com.wanderwildwood.kuroban.ui.GameScreen
import com.wanderwildwood.kuroban.ui.NewGameScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD {
                Kuroban()
            }
        }
    }
}

@Composable
private fun Kuroban(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val game = state) {
        null -> NewGameScreen(onPlay = viewModel::newGame)
        else -> GameScreen(
            state = game,
            onTap = viewModel::tap,
            onConfirm = viewModel::confirmMove,
            onPass = viewModel::pass,
            onHint = viewModel::hint,
            onUndo = viewModel::undo,
            onResign = viewModel::resign,
            onNewGame = viewModel::endGame,
        )
    }
}
