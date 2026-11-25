package com.chessoverlay.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chessoverlay.app.container
import com.chessoverlay.data.AppSettings
import com.chessoverlay.ui.board.BoardTheme
import com.chessoverlay.ui.board.PieceSet
import com.chessoverlay.ui.components.RoundIconButton
import com.chessoverlay.ui.theme.Palette

@Composable
fun appSettings(): AppSettings {
    val ctx = LocalContext.current
    val s by ctx.container.settings.collectAsStateWithLifecycle()
    return s
}

val AppSettings.theme: BoardTheme get() = BoardTheme.fromId(boardTheme)
val AppSettings.pieces: PieceSet get() = PieceSet.fromId(pieceStyle)

/** En-tête d'écran : titre, sous-titre, retour et actions. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", onBack, size = 42.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

/** Boîte de dialogue de saisie (FEN, PGN, nom…), avec collage depuis le presse-papiers. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String = "",
    singleLine: Boolean = false,
    confirmText: String = "Valider",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.GraphiteHigh,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = singleLine,
                    modifier = Modifier.fillMaxWidth().heightIn(min = if (singleLine) 0.dp else 140.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Palette.Violet, cursorColor = Palette.Cyan),
                )
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Text("Coller depuis le presse-papiers") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmText, color = Palette.CyanSoft) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = Palette.TextSecondary) } },
    )
}

/** Confirmation simple. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.GraphiteHigh,
        title = { Text(title) },
        text = { Text(text, color = Palette.TextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Palette.Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = Palette.TextSecondary) } },
    )
}

fun formatDate(ms: Long): String =
    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE).format(java.util.Date(ms))
