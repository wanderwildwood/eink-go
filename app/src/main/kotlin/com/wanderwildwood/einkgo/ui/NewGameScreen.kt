package com.wanderwildwood.einkgo.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.einkgo.game.Difficulty
import com.wanderwildwood.einkgo.game.GameConfig
import com.wanderwildwood.einkgo.game.Opponent
import com.wanderwildwood.einkgo.game.Stone

/**
 * New game options, laid out the way Chess+ lays its own out: the opponent choice at the
 * top, and the settings that only mean something against the computer disappearing when
 * you pick two players.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGameScreen(onPlay: (GameConfig) -> Unit) {
    var opponent by remember { mutableStateOf(Opponent.COMPUTER) }
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    var humanColor by remember { mutableStateOf(Stone.BLACK) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = "eInk GO", fontSize = 24.sp, fontWeight = FontWeight.Medium) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            ChoiceRow(
                label = "Opponent",
                options = listOf(Opponent.COMPUTER, Opponent.HUMAN),
                selected = opponent,
                optionLabel = { if (it == Opponent.COMPUTER) "Computer" else "2 players" },
                onSelect = { opponent = it },
            )

            if (opponent == Opponent.COMPUTER) {
                Spacer(Modifier.height(20.dp))
                ChoiceRow(
                    label = "Difficulty",
                    options = Difficulty.entries.toList(),
                    selected = difficulty,
                    optionLabel = { it.label },
                    onSelect = { difficulty = it },
                )

                Spacer(Modifier.height(20.dp))
                ChoiceRow(
                    label = "Your stones",
                    options = listOf(Stone.BLACK, Stone.WHITE),
                    selected = humanColor,
                    optionLabel = { if (it == Stone.BLACK) "Black" else "White" },
                    onSelect = { humanColor = it },
                )
                Spacer(Modifier.height(8.dp))
                TextMMD(
                    text = "Black plays first.",
                    fontSize = 14.sp,
                )
            }

            Spacer(Modifier.weight(1f))

            ButtonMMD(
                onClick = {
                    onPlay(
                        GameConfig(
                            opponent = opponent,
                            difficulty = difficulty,
                            humanColor = humanColor,
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                TextMMD(text = "PLAY", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * A labelled row of mutually exclusive choices. The selected one is a solid black
 * button and the rest are outlined, which survives E Ink's lack of colour and does not
 * depend on the user spotting a small check mark.
 */
@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TextMMD(text = label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (option in options) {
                val isSelected = option == selected
                val content: @Composable () -> Unit = {
                    TextMMD(text = optionLabel(option), fontSize = 15.sp)
                }
                if (isSelected) {
                    ButtonMMD(
                        onClick = { onSelect(option) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) { content() }
                } else {
                    OutlinedButtonMMD(
                        onClick = { onSelect(option) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) { content() }
                }
            }
        }
    }
}
