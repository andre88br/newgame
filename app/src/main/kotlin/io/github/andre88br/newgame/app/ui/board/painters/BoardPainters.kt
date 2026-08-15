package io.github.andre88br.newgame.app.ui.board.painters

import io.github.andre88br.newgame.app.ui.board.BoardPainter
import io.github.andre88br.newgame.core.engine.GameId

/**
 * Qual desenhista cuida de cada jogo de grade, ou `null` para os que não se jogam numa
 * grade de casas — o dominó, o ludo e os jogos de carta têm telas próprias, em
 * `ui/board/surfaces`.
 *
 * É um dos dois lugares do módulo Android que precisam saber que jogos existem; o resto
 * trabalha pelo `GameCatalog`. O `when` é exaustivo de propósito, sem `else`: acrescentar
 * um jogo ao motor tem de quebrar a compilação **aqui**, para ninguém esquecer de dizer
 * como ele é desenhado. Foi o que aconteceu quando a copas entrou.
 */
fun painterFor(gameId: GameId): BoardPainter? = when (gameId) {
    GameId.TIC_TAC_TOE -> TicTacToePainter
    GameId.CHECKERS -> CheckersPainter
    GameId.REVERSI -> ReversiPainter
    GameId.CHESS -> ChessPainter
    GameId.DOMINOES, GameId.LUDO, GameId.HEARTS, GameId.CANASTRA, GameId.PIFE -> null
}
