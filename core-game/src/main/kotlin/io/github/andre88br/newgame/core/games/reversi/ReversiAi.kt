package io.github.andre88br.newgame.core.games.reversi

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.MoveOrdering
import io.github.andre88br.newgame.core.ai.SearchBasedAi
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent

/**
 * Avaliação posicional do reversi.
 *
 * Contar peças no meio da partida engana: quem tem mais peças costuma ter menos opções, e
 * uma virada no fim inverte tudo. O que decide é ocupar cantos — que nunca podem ser
 * virados — e evitar as casas vizinhas a eles, que entregam o canto ao adversário. Daí a
 * tabela de pesos, e daí a contagem de peças só passar a valer quando o tabuleiro está
 * quase cheio e não há mais tempo para reviravolta.
 */
object ReversiEvaluator : Evaluator<ReversiState> {

    private val SQUARE_WEIGHT = intArrayOf(
        120, -20, 20, 5, 5, 20, -20, 120,
        -20, -40, -5, -5, -5, -5, -40, -20,
        20, -5, 15, 3, 3, 15, -5, 20,
        5, -5, 3, 3, 3, 3, -5, 5,
        5, -5, 3, 3, 3, 3, -5, 5,
        20, -5, 15, 3, 3, 15, -5, 20,
        -20, -40, -5, -5, -5, -5, -40, -20,
        120, -20, 20, 5, 5, 20, -20, 120,
    )

    /** A partir daqui a partida está decidida por contagem, não por posição. */
    private const val ENDGAME_EMPTIES = 10

    override fun evaluate(state: ReversiState, seat: Seat): Int {
        val mine = state.count(seat)
        val theirs = state.count(seat.opponent())

        if (state.emptyCount <= ENDGAME_EMPTIES) {
            return (mine - theirs) * 100
        }

        var positional = 0
        for (square in 0 until REVERSI_CELLS) {
            when (state.board[square].discOwner()) {
                seat -> positional += SQUARE_WEIGHT[square]
                null -> Unit
                else -> positional -= SQUARE_WEIGHT[square]
            }
        }

        // Mobilidade: deixar o adversário sem opções vale mais que qualquer peça a mais.
        val myMoves = ReversiGame.movesFor(state.board, seat).size
        val theirMoves = ReversiGame.movesFor(state.board, seat.opponent()).size
        val mobility = (myMoves - theirMoves) * 12

        return positional + mobility
    }
}

/** Cantos primeiro, casas vizinhas a canto por último. */
val ReversiOrdering: MoveOrdering<ReversiState, ReversiMove> =
    MoveOrdering<ReversiState, ReversiMove> { _, moves ->
        if (moves.size < 2) moves else moves.sortedByDescending { CORNER_FIRST[it.square] }
    }

private val CORNER_FIRST = intArrayOf(
    100, -10, 8, 6, 6, 8, -10, 100,
    -10, -20, 1, 1, 1, 1, -20, -10,
    8, 1, 5, 2, 2, 5, 1, 8,
    6, 1, 2, 1, 1, 2, 1, 6,
    6, 1, 2, 1, 1, 2, 1, 6,
    8, 1, 5, 2, 2, 5, 1, 8,
    -10, -20, 1, 1, 1, 1, -20, -10,
    100, -10, 8, 6, 6, 8, -10, 100,
)

val ReversiAi: SearchBasedAi<ReversiState, ReversiMove> = SearchBasedAi(
    game = ReversiGame,
    evaluator = ReversiEvaluator,
    ordering = ReversiOrdering,
    limits = { difficulty ->
        when (difficulty) {
            Difficulty.EASY -> SearchLimits(maxDepth = 2, timeBudgetMillis = 300)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 5, timeBudgetMillis = 900)
            Difficulty.HARD -> SearchLimits(maxDepth = 12, timeBudgetMillis = 2_500)
        }
    },
)
