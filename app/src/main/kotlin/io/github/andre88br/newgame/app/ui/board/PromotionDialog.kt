package io.github.andre88br.newgame.app.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.core.session.PromotionChoice

/**
 * Pergunta em que peça o peão vira.
 *
 * Cada opção mostra o símbolo e o nome. A dama vem primeiro porque é a escolha certa quase
 * sempre — mas as outras três estão ali porque "quase sempre" não é sempre: há final em que
 * promover a dama é empate por afogamento e promover a torre é vitória.
 */
@Composable
fun PromotionDialog(
    choices: List<PromotionChoice>,
    onPick: (PromotionChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.promotion_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.promotion_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    choices.forEach { choice ->
                        OutlinedButton(
                            onClick = { onPick(choice) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("${symbolOf(choice.kind)}\n${nameOf(choice.kind)}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun symbolOf(kind: Char): String = when (kind) {
    'Q' -> "♛"
    'R' -> "♜"
    'B' -> "♝"
    'N' -> "♞"
    else -> kind.toString()
}

@Composable
private fun nameOf(kind: Char): String = stringResource(
    when (kind) {
        'Q' -> R.string.piece_queen
        'R' -> R.string.piece_rook
        'B' -> R.string.piece_bishop
        'N' -> R.string.piece_knight
        else -> R.string.piece_queen
    },
)
