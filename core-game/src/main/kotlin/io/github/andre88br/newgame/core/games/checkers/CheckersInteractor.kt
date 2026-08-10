package io.github.andre88br.newgame.core.games.checkers

import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.ReasonKey
import io.github.andre88br.newgame.core.session.BoardInteractor
import io.github.andre88br.newgame.core.session.TapResult
import io.github.andre88br.newgame.core.games.checkers.isPlayable as isDarkSquare

/**
 * Nas damas o lance tem dois toques: a peça e o destino.
 *
 * A captura em sequência não muda isso. O motor já devolve a sequência inteira como um
 * lance só, então basta tocar na casa onde a peça termina — não é preciso ir pulando de
 * casa em casa.
 */
object CheckersInteractor : BoardInteractor {

    override val rows: Int = BOARD_SIZE
    override val columns: Int = BOARD_SIZE

    override fun isPlayable(square: Int): Boolean =
        square in 0 until BOARD_CELLS && isDarkSquare(square)

    override fun tap(state: GameState, selected: Int?, square: Int): TapResult {
        val board = state as CheckersState
        if (CheckersGame.outcome(board).isOver) return TapResult.Ignored
        if (square !in 0 until BOARD_CELLS) return TapResult.Ignored

        if (selected == square) return TapResult.Deselect

        if (selected != null) {
            // As sequências que terminam na mesma casa capturam a mesma quantidade — é o
            // que a regra da captura máxima garante —, então a primeira serve.
            val move = CheckersGame.movesFrom(board, selected).firstOrNull { it.to == square }
            if (move != null) return TapResult.Play(move)

            // Não deu certo: pode ser que a pessoa esteja trocando de peça.
            if (board.pieceAt(square).pieceOwner() == board.turn) {
                return select(board, square)
            }
            return TapResult.Rejected(ReasonKey.PIECE_CANNOT_GO_THERE)
        }

        if (board.pieceAt(square).pieceOwner() != board.turn) return TapResult.Ignored
        return select(board, square)
    }

    /** Origem, casas de pouso e as peças capturadas: tudo o que muda de aparência no lance. */
    override fun squaresOf(move: Move): List<Int> {
        val checkers = move as CheckersMove
        return (listOf(checkers.from) + checkers.path + checkers.captured).distinct()
    }

    private fun select(state: CheckersState, square: Int): TapResult {
        val destinations = CheckersGame.movesFrom(state, square).map { it.to }.distinct()
        if (destinations.isEmpty()) {
            val reason = CheckersGame.explainNoMovesFrom(state, square)
                ?: ReasonKey.PIECE_HAS_NOWHERE_TO_GO.reason()
            return TapResult.Rejected(reason)
        }
        return TapResult.Select(square, destinations)
    }
}
