package io.github.andre88br.newgame.app.ui.board.painters

import io.github.andre88br.newgame.app.ui.board.BoardPainter
import io.github.andre88br.newgame.core.engine.GameId

/**
 * Qual desenhista cuida de cada jogo de grade, ou `null` para os que não se jogam numa
 * grade de casas — o dominó e o ludo têm telas próprias, em `ui/board/surfaces`.
 *
 * É um dos dois lugares do módulo Android que precisam saber que jogos existem; o resto
 * trabalha pelo `GameCatalog`.
 */
fun painterFor(gameId: GameId): BoardPainter? = when (gameId) {
    GameId.TIC_TAC_TOE -> TicTacToePainter
    GameId.CHECKERS -> CheckersPainter
    GameId.REVERSI -> ReversiPainter
    GameId.CHESS -> ChessPainter
    GameId.DOMINOES, GameId.LUDO -> null
}
