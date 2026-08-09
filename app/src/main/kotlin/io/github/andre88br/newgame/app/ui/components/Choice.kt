package io.github.andre88br.newgame.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Um grupo de opções mutuamente exclusivas, feito com botões comuns.
 *
 * Poderia ser `SegmentedButton` ou `FilterChip`, mas ambos ainda são API experimental do
 * Material 3, e este módulo foi escrito sem poder compilar — API estável reduz a chance de
 * o projeto não abrir na primeira tentativa.
 */
@Composable
fun <T> ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val text = optionLabel(option)
                if (option == selected) {
                    Button(onClick = { onSelect(option) }) { Text(text) }
                } else {
                    OutlinedButton(onClick = { onSelect(option) }) { Text(text) }
                }
            }
        }
    }
}
