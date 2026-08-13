package io.github.andre88br.newgame.app.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.core.engine.GameEntry

/**
 * As regras do jogo, em texto.
 *
 * Não é documentação: é parte do app. Damas brasileiras obrigam a capturar o máximo, o
 * reversi passa a vez sozinho, o ludo só tira peão com 6 — regras que quem senta para
 * jogar não necessariamente conhece, e que sem explicação fazem o app parecer quebrado
 * quando ele recusa um lance.
 *
 * O texto sai de `strings.xml` por convenção de nome (`rules_<chave do jogo>`), do mesmo
 * jeito que o nome do jogo. Jogo novo ganha regras acrescentando o texto — nenhum `when`
 * de tela precisa saber que jogos existem.
 */
@Composable
fun HowToPlayDialog(entry: GameEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val chave = remember(entry.id) { "rules_" + entry.nameKey.removePrefix("game_") }
    val resourceId = remember(chave) {
        context.resources.getIdentifier(chave, "string", context.packageName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.how_to_play)) },
        text = {
            Column(
                // Rola: as regras do xadrez não cabem numa tela de celular.
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (resourceId != 0) {
                        stringResource(resourceId)
                    } else {
                        stringResource(R.string.rules_missing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.rules_common),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
