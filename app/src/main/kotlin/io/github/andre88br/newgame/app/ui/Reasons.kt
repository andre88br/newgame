package io.github.andre88br.newgame.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.andre88br.newgame.core.engine.Reason

/**
 * O motivo de recusa no idioma do aparelho.
 *
 * O motor manda uma chave (`CAPTURE_MANDATORY`) e os argumentos (`2`); aqui vira
 * "Captura é obrigatória: existe lance que captura 2 peça(s)" ou o equivalente em inglês.
 *
 * **Faltar tradução não pode apagar a explicação.** Se o recurso não existir, cai no texto
 * de referência que vem do próprio motor, em português — uma frase no idioma errado ainda
 * diz por que o lance não valeu, e um aviso vazio não diz nada. Um teste do repositório
 * confere que nenhuma chave chega a esse caminho.
 */
@Composable
fun reasonText(reason: Reason): String {
    val context = LocalContext.current
    val resourceId = remember(reason.key) {
        context.resources.getIdentifier(reason.key.resourceName, "string", context.packageName)
    }
    return when {
        resourceId == 0 -> reason.text()
        reason.args.isEmpty() -> stringResource(resourceId)
        else -> stringResource(resourceId, *reason.args.toTypedArray())
    }
}
