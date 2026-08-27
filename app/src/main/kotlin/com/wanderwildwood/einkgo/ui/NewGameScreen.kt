package com.wanderwildwood.einkgo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var humanColor by remember { mutableStateOf(Stone.BLACK) }
    var handicap by remember { mutableStateOf(0) }
    var aboutOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = "eInk GO", fontSize = 24.sp, fontWeight = FontWeight.Medium) },
                actions = { InfoButton(onClick = { aboutOpen = true }) },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            ChoiceRow(
                label = "Opponent",
                options = listOf(Opponent.COMPUTER, Opponent.HUMAN),
                selected = opponent,
                optionLabel = { if (it == Opponent.COMPUTER) "Computer" else "2 players" },
                onSelect = { opponent = it },
            )

            if (opponent == Opponent.COMPUTER) {
                Spacer(Modifier.height(22.dp))
                ChoiceRow(
                    label = "Difficulty",
                    options = Difficulty.entries.toList(),
                    selected = difficulty,
                    optionLabel = { it.label },
                    onSelect = { difficulty = it },
                )

                Spacer(Modifier.height(22.dp))
                ChoiceRow(
                    label = "Your stones",
                    options = listOf(Stone.BLACK, Stone.WHITE),
                    selected = humanColor,
                    optionLabel = { if (it == Stone.BLACK) "Black" else "White" },
                    onSelect = { humanColor = it },
                )
            }

            Spacer(Modifier.height(22.dp))
            ChoiceRow(
                label = "Handicap",
                options = listOf(0, 2, 3, 4, 5),
                selected = handicap,
                optionLabel = { "$it" },
                onSelect = { handicap = it },
            )
            Spacer(Modifier.height(10.dp))
            TextMMD(
                text = if (handicap == 0) {
                    "Black plays first."
                } else {
                    "Black starts with $handicap stones on the board, and White plays first."
                },
                fontSize = 13.sp,
            )

            // The empty middle of this screen was doing nothing; a corner of a board fills
            // it and says what the app is without a word of explanation. It takes whatever
            // room is going spare - which is most of the screen once two players is picked
            // and the computer settings go away - and bows out entirely when there is none,
            // rather than being wedged in at a size that looks like a mistake.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val available = minOf(maxWidth, maxHeight)
                if (available >= 88.dp) {
                    GobanMark(size = minOf(available, 200.dp))
                }
            }

            ButtonMMD(
                onClick = {
                    onPlay(
                        GameConfig(
                            opponent = opponent,
                            difficulty = difficulty,
                            humanColor = humanColor,
                            handicap = handicap,
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                TextMMD(text = "PLAY", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }

    if (aboutOpen) {
        AboutDialog(onDismiss = { aboutOpen = false })
    }
}

@Composable
private fun InfoButton(onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val stroke = 2.dp.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            val middle = Offset(size.width / 2f, size.height / 2f)
            drawCircle(ink, radius = radius, center = middle, style = Stroke(stroke))
            drawCircle(ink, radius = stroke * 0.7f, center = Offset(middle.x, size.height * 0.28f))
            drawLine(
                ink,
                Offset(middle.x, size.height * 0.44f),
                Offset(middle.x, size.height * 0.74f),
                stroke,
            )
        }
    }
}

/** A small square of goban with four stones on it, drawn the same way the real board is. */
@Composable
private fun GobanMark(size: androidx.compose.ui.unit.Dp = 132.dp) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    val lines = 5

    Canvas(modifier = Modifier.size(size)) {
        val cell = this.size.minDimension / lines
        val origin = cell / 2f
        val span = (lines - 1) * cell
        val hairline = 1.dp.toPx()
        val rim = 2.dp.toPx()
        val radius = cell * 0.46f

        for (i in 0 until lines) {
            val at = origin + i * cell
            drawLine(ink, Offset(origin, at), Offset(origin + span, at), hairline)
            drawLine(ink, Offset(at, origin), Offset(at, origin + span), hairline)
        }
        drawCircle(ink, radius = cell * 0.08f, center = Offset(origin + 2 * cell, origin + 2 * cell))

        fun at(col: Int, row: Int) = Offset(origin + col * cell, origin + row * cell)

        // A quiet, symmetric arrangement - it is a mark, not a position worth reading.
        drawCircle(ink, radius = radius, center = at(1, 1))
        drawCircle(ink, radius = radius, center = at(3, 3))
        for (point in listOf(at(3, 1), at(1, 3))) {
            drawCircle(paper, radius = radius, center = point)
            drawCircle(ink, radius = radius, center = point, style = Stroke(rim))
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
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (option in options) {
                val isSelected = option == selected
                val content: @Composable () -> Unit = {
                    TextMMD(text = optionLabel(option), fontSize = 15.sp, maxLines = 1)
                }
                if (isSelected) {
                    ButtonMMD(
                        onClick = { onSelect(option) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) { content() }
                } else {
                    OutlinedButtonMMD(
                        onClick = { onSelect(option) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) { content() }
                }
            }
        }
    }
}
