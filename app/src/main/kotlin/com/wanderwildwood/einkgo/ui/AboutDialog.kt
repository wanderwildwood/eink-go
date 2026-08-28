package com.wanderwildwood.einkgo.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.einkgo.BuildConfig

/**
 * What this is, what it is made of, and where the source lives.
 *
 * The store listing links the repository, which is what the GPL actually requires. But
 * somebody who installed the APK and never saw a listing has no way to learn they have
 * source rights at all, and a licence nobody can find is not much of one.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = "Go", fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        TextMMD(text = "Version ${BuildConfig.VERSION_NAME}", fontSize = 14.sp)

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "A game of Go for the Kompakt. 9x9, against GNU Go or two people " +
                "sharing the phone. No permissions, no network, nothing leaves the device.",
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(
            text = "Free software under the GNU General Public License v3.",
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(10.dp))
        TextMMD(
            text = "Plays using GNU Go 3.8, copyright 1999-2009 Free Software Foundation, " +
                "included unmodified under the same licence.",
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(14.dp))
        TextMMD(text = "Source, and your rights to it:", fontSize = 14.sp)
        TextMMD(
            text = "github.com/wanderwildwood/kuroban",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(18.dp))
        OutlinedButtonMMD(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) { TextMMD(text = "CLOSE", fontSize = 15.sp) }
    }
}
