package io.github.andre88br.newgame.core.games.ludo

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.MoveOrdering
import io.github.andre88br.newgame.core.ai.SearchBasedAi
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent

/**
 * Avaliação do ludo.
 *
 * A busca enxerga pouco à frente aqui, e por um motivo de fundo: o dado seguinte é sorteio,
 * então cada nível da árvore assume um valor específico que tinha uma chance em seis de
 * sair. Aprofundar não melhora muito — o que decide é a avaliação estar certa sobre o que
 * vale a pena agora: peão na chegada, peão adiantado, e peão fora do alcance de captura.
 */
object LudoEvaluator : Evaluator<LudoState> {

    private const val FINISHED = 400
    private const val OUT_OF_YARD = 60
    private const val DANGER = 25

    override fun evaluate(state: LudoState, seat: Seat): Int =
        sideScore(state, seat) - sideScore(state, seat.opponent())

    private fun sideScore(state: LudoState, seat: Seat): Int {
        var score = 0
        for (progress in state.tokensOf(seat)) {
            when {
                progress >= LUDO_GOAL -> score += FINISHED
                progress == LUDO_YARD -> Unit
                else -> {
                    // Andar vale, mas o corredor final vale mais: de lá não se é capturado.
                    score += OUT_OF_YARD + progress
                    if (progress >= LUDO_TRACK) score += 80

                    val square = absoluteSquare(seat, progress)
                    if (square != null && !isSafeSquare(square)) score -= DANGER
                }
            }
        }
        return score
    }
}

/** Chegar primeiro, capturar depois, tirar do curral por último. */
val LudoOrdering: MoveOrdering<LudoState, LudoMove> =
    MoveOrdering<LudoState, LudoMove> { state, moves ->
        if (moves.size < 2) {
            moves
        } else {
            moves.sortedByDescending { move ->
                val progress = state.tokensOf(state.turn)[move.token]
                when {
                    progress == LUDO_YARD -> 0
                    progress + state.die >= LUDO_GOAL -> 100
                    else -> progress + state.die
                }
            }
        }
    }

val LudoAi: SearchBasedAi<LudoState, LudoMove> = SearchBasedAi(
    game = LudoGame,
    evaluator = LudoEvaluator,
    ordering = LudoOrdering,
    limits = { difficulty ->
        when (difficulty) {
            Difficulty.EASY -> SearchLimits(maxDepth = 1, timeBudgetMillis = 150)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 3, timeBudgetMillis = 400)
            Difficulty.HARD -> SearchLimits(maxDepth = 5, timeBudgetMillis = 1_200)
        }
    },
)
