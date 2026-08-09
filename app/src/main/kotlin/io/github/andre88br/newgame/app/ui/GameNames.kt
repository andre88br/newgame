package io.github.andre88br.newgame.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.core.engine.GameEntry

/**
 * Nome traduzido do jogo.
 *
 * O `core-game` não conhece recursos do Android, então guarda só uma chave (`nameKey`) e a
 * tradução vive aqui. A busca por nome é o preço dessa separação — em troca, acrescentar um
 * jogo não obriga a mexer num `when` de tela nenhuma.
 */
@Composable
fun gameName(entry: GameEntry): String {
    val context = LocalContext.current
    val resourceId = remember(entry.nameKey) {
        context.resources.getIdentifier(entry.nameKey, "string", context.packageName)
    }
    return if (resourceId != 0) stringResource(resourceId) else entry.nameKey
}

@Composable
fun difficultyName(difficulty: Difficulty): String = stringResource(
    when (difficulty) {
        Difficulty.EASY -> R.string.setup_difficulty_easy
        Difficulty.MEDIUM -> R.string.setup_difficulty_medium
        Difficulty.HARD -> R.string.setup_difficulty_hard
    },
)
