package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.canastra.CanastraState
import io.github.andre88br.newgame.core.games.dominoes.DominoesState
import io.github.andre88br.newgame.core.games.hearts.HeartsState
import io.github.andre88br.newgame.core.games.ludo.LudoState

/**
 * A tela dos jogos que não se jogam tocando em casas de uma grade.
 *
 * O `GameEntry` desses jogos traz `interactor = null`, e é isso que manda a tela do
 * tabuleiro vir parar aqui em vez de montar o `GridBoard`. Cada jogo desenha o que é seu —
 * a mão no dominó, a cruz no ludo — e devolve o lance pronto.
 *
 * É o segundo e último lugar do módulo Android que sabe quais jogos existem; o outro é o
 * `painterFor`, dos jogos de grade.
 */
@Composable
fun MoveSurface(
    gameId: GameId,
    state: GameState,
    viewer: Seat,
    /** O nome de cada cadeira. Vazia numa partida salva antes de existirem nomes. */
    names: List<String>,
    enabled: Boolean,
    hinted: Move?,
    /** Segue o ajuste de animações: desligado, o dado do ludo revela sem chacoalhar. */
    animated: Boolean,
    modifier: Modifier = Modifier,
    onMove: (Move) -> Unit,
) {
    when {
        gameId == GameId.DOMINOES && state is DominoesState ->
            DominoesSurface(state, viewer, names, enabled, hinted, modifier, onMove)

        gameId == GameId.LUDO && state is LudoState ->
            LudoSurface(state, viewer, enabled, hinted, animated, modifier, onMove)

        gameId == GameId.HEARTS && state is HeartsState ->
            HeartsSurface(state, viewer, names, enabled, hinted, modifier, onMove)

        gameId == GameId.CANASTRA && state is CanastraState ->
            CanastraSurface(state, viewer, names, enabled, hinted, modifier, onMove)

        // Jogo sem tela: dizer isso é melhor do que mostrar uma área em branco.
        else -> Text(stringResource(R.string.board_no_surface, gameId.name))
    }
}
