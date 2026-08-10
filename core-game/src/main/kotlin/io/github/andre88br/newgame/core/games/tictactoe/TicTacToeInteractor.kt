package io.github.andre88br.newgame.core.games.tictactoe

import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.ReasonKey
import io.github.andre88br.newgame.core.session.BoardInteractor
import io.github.andre88br.newgame.core.session.TapResult

/** No jogo da velha um toque já é o lance inteiro: não há nada a escolher antes. */
object TicTacToeInteractor : BoardInteractor {

    override val rows: Int = 3
    override val columns: Int = 3

    override fun tap(state: GameState, selected: Int?, square: Int): TapResult {
        val board = state as TicTacToeState
        if (TicTacToeGame.outcome(board).isOver) return TapResult.Ignored
        if (square !in 0..8) return TapResult.Ignored
        if (!board.isEmpty(square)) return TapResult.Rejected(ReasonKey.SQUARE_TAKEN)
        return TapResult.Play(TicTacToeMove(square))
    }

    override fun squaresOf(move: Move): List<Int> = listOf((move as TicTacToeMove).cell)
}
