package io.github.andre88br.newgame.core.games.tictactoe

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.SearchBasedAi
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent

/**
 * Conta linhas ainda disputáveis: uma linha com duas marcas minhas e a terceira casa
 * vazia vale muito mais do que uma linha com uma marca só, e linha que já tem marca dos
 * dois lados não vale nada para ninguém.
 *
 * No nível difícil a busca chega ao fim do jogo e a avaliação nem chega a ser usada; ela
 * existe para os níveis rasos terem algum senso posicional.
 */
object TicTacToeEvaluator : Evaluator<TicTacToeState> {

    override fun evaluate(state: TicTacToeState, seat: Seat): Int {
        val mine = seat.index
        val theirs = seat.opponent().index
        var score = 0
        for (line in TicTacToeState.LINES) {
            var mineCount = 0
            var theirCount = 0
            for (cell in line) {
                when (state.cells[cell]) {
                    mine -> mineCount++
                    theirs -> theirCount++
                }
            }
            if (mineCount > 0 && theirCount > 0) continue // linha morta para os dois
            score += LINE_WEIGHT[mineCount] - LINE_WEIGHT[theirCount]
        }
        return score
    }

    private val LINE_WEIGHT = intArrayOf(0, 10, 100, 1000)
}

val TicTacToeAi: SearchBasedAi<TicTacToeState, TicTacToeMove> = SearchBasedAi(
    game = TicTacToeGame,
    evaluator = TicTacToeEvaluator,
    limits = { difficulty ->
        when (difficulty) {
            // Enxerga só a jogada seguinte: bloqueia ameaça imediata e nada mais.
            Difficulty.EASY -> SearchLimits(maxDepth = 2, timeBudgetMillis = 200)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 4, timeBudgetMillis = 300)
            // 9 casas: a árvore inteira cabe na busca, então o difícil joga perfeito.
            Difficulty.HARD -> SearchLimits(maxDepth = 9, timeBudgetMillis = 1_000)
        }
    },
)
