package io.github.andre88br.newgame.core.games.checkers

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.MoveOrdering
import io.github.andre88br.newgame.core.ai.SearchBasedAi
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent

/**
 * Avaliação material com alguns ajustes posicionais baratos.
 *
 * A dama voadora das regras brasileiras é bem mais forte que a dama do xadrez de damas
 * anglo-saxão, daí valer mais de três pedras. O bônus de avanço faz a IA empurrar as
 * pedras em vez de ficar arrastando peças de um lado para o outro, e o bônus de última
 * fileira desestimula abrir a própria linha de promoção cedo demais.
 */
object CheckersEvaluator : Evaluator<CheckersState> {

    private const val MAN = 100
    private const val KING = 340
    private const val ADVANCE = 7
    private const val BACK_ROW = 14
    private const val EDGE = 5

    override fun evaluate(state: CheckersState, seat: Seat): Int {
        var score = 0
        for (index in 0 until BOARD_CELLS) {
            val piece = state.board[index]
            val owner = piece.pieceOwner() ?: continue

            var value = if (piece.isKing()) KING else MAN
            if (piece.isMan()) {
                val row = rowOf(index)
                // Distância percorrida rumo à promoção.
                val advance = if (owner == Seat.FIRST) BOARD_SIZE - 1 - row else row
                value += advance * ADVANCE
                if (row == owner.opponent().promotionRow()) value += BACK_ROW
            }
            // Peça na borda não pode ser capturada por aquele lado.
            if (colOf(index) == 0 || colOf(index) == BOARD_SIZE - 1) value += EDGE

            score += if (owner == seat) value else -value
        }
        return score
    }
}

/**
 * Ordem de exame dos lances. Capturas maiores e promoções primeiro: quanto antes a busca
 * encontra o lance forte, mais ramos a poda alpha-beta descarta sem visitar.
 */
val CheckersOrdering: MoveOrdering<CheckersState, CheckersMove> =
    MoveOrdering<CheckersState, CheckersMove> { state, moves ->
        if (moves.size < 2) {
            moves
        } else {
            moves.sortedByDescending { move ->
                val promotes = state.board[move.from].isMan() &&
                    rowOf(move.to) == state.turn.promotionRow()
                move.captured.size * 100 + (if (promotes) 30 else 0) + move.path.size
            }
        }
    }

val CheckersAi: SearchBasedAi<CheckersState, CheckersMove> = SearchBasedAi(
    game = CheckersGame,
    evaluator = CheckersEvaluator,
    ordering = CheckersOrdering,
    limits = { difficulty ->
        // O orçamento de tempo é o limite que costuma valer; a profundidade é só um teto
        // para o fim de jogo, quando restam poucas peças e a árvore fica rasa.
        when (difficulty) {
            Difficulty.EASY -> SearchLimits(maxDepth = 3, timeBudgetMillis = 300)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 7, timeBudgetMillis = 900)
            Difficulty.HARD -> SearchLimits(maxDepth = 14, timeBudgetMillis = 2_500)
        }
    },
)
