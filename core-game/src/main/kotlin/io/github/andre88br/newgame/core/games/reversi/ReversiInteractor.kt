package io.github.andre88br.newgame.core.games.reversi

import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.session.BoardInteractor
import io.github.andre88br.newgame.core.session.TapResult

/** No reversi um toque é o lance inteiro: escolhe-se a casa, e as viradas vêm de brinde. */
object ReversiInteractor : BoardInteractor {

    override val rows: Int = REVERSI_SIZE
    override val columns: Int = REVERSI_SIZE

    override fun tap(state: GameState, selected: Int?, square: Int): TapResult {
        val board = state as ReversiState
        if (ReversiGame.outcome(board).isOver) return TapResult.Ignored
        if (square !in 0 until REVERSI_CELLS) return TapResult.Ignored

        if (board.discAt(square) != REVERSI_EMPTY) {
            return TapResult.Rejected("Essa casa já está ocupada")
        }
        if (ReversiGame.flipsFor(board.board, board.turn, square).isEmpty()) {
            return TapResult.Rejected("Só vale jogar onde se cerca alguma peça do adversário")
        }
        return TapResult.Play(ReversiMove(square))
    }

    /**
     * Só a casa jogada. As peças viradas dependem do tabuleiro, e esta função recebe apenas
     * o lance — mas marcar onde a peça caiu já é o que a pessoa precisa ver.
     */
    override fun squaresOf(move: Move): List<Int> = listOf((move as ReversiMove).square)
}
