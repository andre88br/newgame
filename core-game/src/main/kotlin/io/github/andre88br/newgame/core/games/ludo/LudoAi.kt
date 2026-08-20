package io.github.andre88br.newgame.core.games.ludo

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.MoveOrdering
import io.github.andre88br.newgame.core.ai.SearchBasedAi
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.engine.Seat

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

    /**
     * A vantagem sobre quem está **melhor** entre os outros, e não sobre a média.
     *
     * Numa mesa de três ou quatro, comparar com a média deixaria a máquina satisfeita
     * enquanto um adversário dispara na frente. O que decide a partida é quem está na
     * liderança, então é com ele que a conta é feita.
     */
    override fun evaluate(state: LudoState, seat: Seat): Int {
        val meu = sideScore(state, seat)
        val melhorDosOutros = state.others(seat).maxOfOrNull { sideScore(state, it) } ?: 0
        return meu - melhorDosOutros
    }

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

                    val square = absoluteSquare(seat, progress, state.seats, state.firstArm)
                    if (square != null && !isSafeSquare(square)) score -= DANGER
                }
            }
        }
        return score
    }
}

/**
 * Chegar primeiro, capturar depois, tirar do curral por último.
 *
 * A captura precisava de [LudoGame.isCapture] para ser reconhecida — sem isso, um lance que
 * manda peão adversário pro curral pontuava igual a um lance qualquer do mesmo avanço, e a
 * segunda prioridade do comentário nunca existiu de fato no código.
 */
val LudoOrdering: MoveOrdering<LudoState, LudoMove> =
    MoveOrdering<LudoState, LudoMove> { state, moves ->
        if (moves.size < 2) {
            moves
        } else {
            moves.sortedByDescending { move ->
                val progress = state.tokensOf(state.turn)[move.token]
                val passo = progress + state.die
                when {
                    progress == LUDO_YARD -> 0
                    passo >= LUDO_GOAL -> 300
                    LudoGame.isCapture(state, move) -> 200 + passo
                    else -> passo
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
