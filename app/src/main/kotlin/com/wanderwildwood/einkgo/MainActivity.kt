package com.wanderwildwood.einkgo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.einkgo.game.GameViewModel
import com.wanderwildwood.einkgo.ui.GameScreen
import com.wanderwildwood.einkgo.ui.NewGameScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD {
                EInkGo()
            }
        }
    }
}

@Composable
private fun EInkGo(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val game = state) {
        null -> NewGameScreen(onPlay = viewModel::newGame)
        else -> GameScreen(
            state = game,
            onTap = viewModel::tap,
            onConfirm = viewModel::confirmMove,
            onPass = viewModel::pass,
            onUndo = viewModel::undo,
            onResign = viewModel::resign,
            onNewGame = viewModel::endGame,
        )
    }
}
