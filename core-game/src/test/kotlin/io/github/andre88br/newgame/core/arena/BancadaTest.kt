package io.github.andre88br.newgame.core.arena

import io.github.andre88br.newgame.core.ai.AlphaBetaSearch
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.games.canastra.CanastraAi
import io.github.andre88br.newgame.core.games.canastra.CanastraEvaluatorImpl
import io.github.andre88br.newgame.core.games.canastra.CanastraGame
import io.github.andre88br.newgame.core.games.canastra.CanastraOrdering
import io.github.andre88br.newgame.core.games.canastra.CanastraState
import io.github.andre88br.newgame.core.games.canastra.completeCanastra
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Bancada de medição da IA: quantos nós por segundo a busca faz, e quanto uma versão vale
 * contra outra.
 *
 * Fica curta de propósito, para rodar junto com o resto da suíte. As campanhas longas — as
 * que afinam pesos — usam as mesmas peças com sementes de sobra.
 */
class BancadaTest {

    /** Posições de meio de mão, alcançadas sempre igual: a medição precisa ser comparável. */
    /**
     * Posições de meio de mão sempre iguais, alcançadas **sem** a IA.
     *
     * Usar a própria IA para chegar até aqui invalidaria a medição: ela decide por orçamento
     * de tempo, então um motor mais rápido busca mais fundo, joga diferente e chega a outra
     * posição — e aí se compararia o custo de posições distintas, não o custo do nó. Este
     * andador escolhe pelo índice, é reprodutível e não depende de velocidade nenhuma.
     */
    private fun posicoes(quantas: Int = 4): List<CanastraState> = (0 until quantas).map { i ->
        var state = CanastraGame.initialState(MatchConfig(200L + i, seats = 4))
        var passo = 0
        while (CanastraGame.outcome(state) == Outcome.InProgress && passo < 60) {
            val legais = CanastraGame.legalMoves(state)
            if (legais.isEmpty()) break
            val escolha = legais[(passo * 7 + i * 13) % legais.size]
            state = (CanastraGame.applyMove(state, escolha) as? MoveResult.Ok)?.state ?: break
            passo++
        }
        completeCanastra(CanastraGame.redactFor(state, state.turn), Rng.seeded(1))
    }

    /**
     * Custo do nó, sem relógio na jogada: profundidade fixa e orçamento folgado, para o
     * número de nós ser sempre o mesmo e só o tempo mudar entre uma versão e outra.
     */
    @Test
    fun `custo do no da canastra`() {
        val posicoes = posicoes()
        fun rodada(): Pair<Long, Long> {
            var nos = 0L
            val t0 = System.nanoTime()
            for (p in posicoes) {
                nos += AlphaBetaSearch(CanastraGame, CanastraEvaluatorImpl(), CanastraOrdering, completeTurns = true)
                    .search(p, SearchLimits(maxDepth = 1, timeBudgetMillis = 600_000)).nodes
            }
            return nos to (System.nanoTime() - t0) / 1_000_000
        }
        rodada() // aquecimento: sem isto mede-se o compilador, não a busca
        val (nos, ms) = rodada()
        val porSegundo = if (ms > 0) nos * 1000 / ms else nos
        println("BANCADA canastra: $nos nós em ${ms}ms = $porSegundo nós/s (${ms * 1000 / maxOf(nos, 1)}µs por nó)")
        assertTrue(nos > 0, "a busca precisa visitar algum nó")
    }

    /** Fumaça: a arena roda de ponta a ponta e uma versão contra ela mesma dá empate técnico. */
    @Test
    fun `a arena empata uma versao contra ela mesma`() {
        val resultado = arenaDaCanastra(maos = 1).duelo(
            a = CanastraAi,
            b = CanastraAi,
            sementes = 1L..3L,
            dificuldade = Difficulty.EASY,
        )
        println("BANCADA espelho: $resultado")
        assertTrue(resultado.partidas == 6, "três sementes pareadas são seis partidas")
        assertTrue(
            !resultado.significativo,
            "a mesma versão contra ela mesma não podia acusar diferença significativa: $resultado",
        )
    }
}
