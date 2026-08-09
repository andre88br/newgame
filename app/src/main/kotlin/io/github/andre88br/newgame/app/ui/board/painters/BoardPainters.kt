package io.github.andre88br.newgame.app.ui.board.painters

import io.github.andre88br.newgame.app.ui.board.BoardPainter
import io.github.andre88br.newgame.core.engine.GameId

/**
 * Qual desenhista cuida de cada jogo.
 *
 * É o único lugar do módulo Android que precisa saber que jogos existem — o resto trabalha
 * pelo `GameCatalog`. Jogo novo entra aqui com uma linha.
 */
fun painterFor(gameId: GameId): BoardPainter = when (gameId) {
    GameId.TIC_TAC_TOE -> TicTacToePainter
    GameId.CHECKERS -> CheckersPainter
    GameId.CHESS, GameId.REVERSI, GameId.DOMINOES, GameId.LUDO ->
        error("O jogo $gameId ainda não tem tela — está previsto para uma fase seguinte")
}
