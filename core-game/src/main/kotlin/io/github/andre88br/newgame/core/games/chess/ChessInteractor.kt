package io.github.andre88br.newgame.core.games.chess

import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Reason
import io.github.andre88br.newgame.core.engine.ReasonKey
import io.github.andre88br.newgame.core.session.BoardInteractor
import io.github.andre88br.newgame.core.session.PromotionChoice
import io.github.andre88br.newgame.core.session.TapResult

/**
 * Xadrez com dois toques: a peça e o destino.
 *
 * Roque se joga movendo o rei duas casas — não tocando na torre —, que é como funciona em
 * todo aplicativo de xadrez e é também como o motor representa o lance.
 *
 * Chegando um peão à última fileira, o toque não vira lance direto: devolve
 * [TapResult.ChoosePromotion] com as quatro peças, para a tela perguntar. Promover a torre
 * ou a cavalo é raro, mas existe — e há posição em que virar dama é empate por afogamento
 * enquanto virar torre é vitória.
 */
object ChessInteractor : BoardInteractor {

    /** Ordem em que as peças aparecem no diálogo de promoção. */
    private val PROMOTION_ORDER = listOf('Q', 'R', 'B', 'N')

    override val rows: Int = CHESS_SIZE
    override val columns: Int = CHESS_SIZE

    override fun tap(state: GameState, selected: Int?, square: Int): TapResult {
        val board = state as ChessState
        if (ChessGame.outcome(board).isOver) return TapResult.Ignored
        if (square !in 0 until CHESS_CELLS) return TapResult.Ignored

        if (selected == square) return TapResult.Deselect

        if (selected != null) {
            val candidates = ChessGame.movesFrom(board, selected).filter { it.to == square }
            if (candidates.isNotEmpty()) {
                val promotions = candidates.filter { it.promotion != null }
                if (promotions.isNotEmpty()) {
                    return TapResult.ChoosePromotion(
                        from = selected,
                        to = square,
                        // Na ordem em que se costuma escolher: dama primeiro, cavalo por último.
                        choices = PROMOTION_ORDER.mapNotNull { kind ->
                            promotions.firstOrNull { it.promotion == kind }
                                ?.let { PromotionChoice(kind, it) }
                        },
                    )
                }
                return TapResult.Play(candidates.first())
            }
            if (board.pieceAt(square).pieceSeat() == board.turn) return select(board, square)
            return TapResult.Rejected(rejectionFor(board, selected, square))
        }

        if (board.pieceAt(square).pieceSeat() != board.turn) return TapResult.Ignored
        return select(board, square)
    }

    /** Origem e destino. As casas da torre no roque ficam de fora: o olho segue o rei. */
    override fun squaresOf(move: Move): List<Int> {
        val chess = move as ChessMove
        return listOf(chess.from, chess.to)
    }

    private fun select(state: ChessState, square: Int): TapResult {
        val destinations = ChessGame.movesFrom(state, square).map { it.to }.distinct()
        if (destinations.isEmpty()) {
            val reason = if (state.inCheck(state.turn)) {
                ReasonKey.KING_IN_CHECK_ONLY_RESOLVING.reason()
            } else {
                ReasonKey.PIECE_HAS_NO_MOVE.reason()
            }
            return TapResult.Rejected(reason)
        }
        return TapResult.Select(square, destinations)
    }

    private fun rejectionFor(state: ChessState, from: Int, to: Int): Reason {
        // Distingue "a peça não anda assim" de "andaria, mas o rei ficaria em xeque" — a
        // segunda é a que deixa quem está aprendendo achando que o app travou.
        val pseudo = ChessMoves.pseudoLegal(state).any { it.from == from && it.to == to }
        return when {
            pseudo && state.inCheck(state.turn) -> ReasonKey.KING_IN_CHECK_UNRESOLVED.reason()
            pseudo -> ReasonKey.WOULD_EXPOSE_KING.reason()
            else -> ReasonKey.PIECE_CANNOT_GO_THERE.reason()
        }
    }
}
