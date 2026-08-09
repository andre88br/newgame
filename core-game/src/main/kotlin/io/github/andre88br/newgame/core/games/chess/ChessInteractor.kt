package io.github.andre88br.newgame.core.games.chess

import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.session.BoardInteractor
import io.github.andre88br.newgame.core.session.TapResult

/**
 * Xadrez com dois toques: a peça e o destino.
 *
 * Roque se joga movendo o rei duas casas — não tocando na torre —, que é como funciona em
 * todo aplicativo de xadrez e é também como o motor representa o lance.
 *
 * **Promoção sai sempre dama.** O motor gera as quatro peças, mas a tela ainda não pergunta
 * qual. Promover a torre, bispo ou cavalo aparece em pouquíssimas partidas, e quase sempre
 * a dama é o certo; oferecer a escolha fica para quando houver diálogo de promoção.
 */
object ChessInteractor : BoardInteractor {

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
                return TapResult.Play(candidates.firstOrNull { it.promotion == 'Q' } ?: candidates.first())
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
                "Seu rei está em xeque: só valem lances que resolvam isso"
            } else {
                "Essa peça não tem lance disponível"
            }
            return TapResult.Rejected(reason)
        }
        return TapResult.Select(square, destinations)
    }

    private fun rejectionFor(state: ChessState, from: Int, to: Int): String {
        // Distingue "a peça não anda assim" de "andaria, mas o rei ficaria em xeque" — a
        // segunda é a que deixa quem está aprendendo achando que o app travou.
        val pseudo = ChessMoves.pseudoLegal(state).any { it.from == from && it.to == to }
        return when {
            pseudo && state.inCheck(state.turn) -> "Seu rei está em xeque: esse lance não resolve"
            pseudo -> "Esse lance deixaria seu rei em xeque"
            else -> "Essa peça não pode ir para aí"
        }
    }
}
