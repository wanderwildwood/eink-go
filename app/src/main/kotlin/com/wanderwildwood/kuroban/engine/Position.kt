package com.wanderwildwood.kuroban.engine

import com.wanderwildwood.kuroban.game.BOARD_SIZE
import com.wanderwildwood.kuroban.game.Point
import com.wanderwildwood.kuroban.game.Stone
import java.io.File

/**
 * Writes a hand-placed position where the engine can read it back with `loadsgf`.
 *
 * A position placed by hand cannot be fed to the engine as moves. The stones go down in
 * whatever order somebody taps them, which is rarely an order the rules would allow: a
 * white stone laid next to a lone black one captures it, and the black stone of a group
 * that is not surrounded yet is suicide until the rest of it arrives. `play` would
 * refuse half of it and quietly eat the other half.
 *
 * SGF's add-stone properties have none of that. `AB` and `AW` say where the stones are,
 * not how they got there, and `PL` says who is to play. Which makes an SGF file the way
 * in, and `loadsgf` the only GTP command that takes one.
 *
 * The engine still gets the last word: an impossible position - a group already down to
 * no liberties - is one `loadsgf` will accept without complaint, so it is refused before
 * it is ever written. See the liberty check in GameViewModel.
 */
fun writePosition(
    file: File,
    black: Set<Point>,
    white: Set<Point>,
    toMove: Stone,
    komi: Double,
) {
    file.writeText(
        buildString {
            append("(;GM[1]FF[4]SZ[")
            append(BOARD_SIZE)
            append("]KM[")
            append(komi)
            append("]PL[")
            append(if (toMove == Stone.BLACK) 'B' else 'W')
            append(']')
            if (black.isNotEmpty()) append(black.joinToString("", prefix = "AB") { it.toSgf() })
            if (white.isNotEmpty()) append(white.joinToString("", prefix = "AW") { it.toSgf() })
            append(")\n")
        }
    )
}

/**
 * SGF counts from the top left corner, which is where [Point] counts from too, so this
 * is a straight substitution and not the flip that [toVertex] has to do. GTP is the odd
 * one out: it numbers rows from the bottom, the way a person reading a board does.
 */
private fun Point.toSgf(): String = "[${'a' + col}${'a' + row}]"
