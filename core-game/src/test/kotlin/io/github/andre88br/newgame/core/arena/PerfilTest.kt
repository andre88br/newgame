package io.github.andre88br.newgame.core.arena

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.games.canastra.*
import kotlin.test.Test

class PerfilTest {
    /**
     * Posições de meio de mão sempre iguais, alcançadas **sem** a IA.
     *
     * Usar a própria IA para chegar até aqui invalidaria a medição: ela decide por orçamento
     * de tempo, então um motor mais rápido busca mais fundo, joga diferente e chega a outra
     * posição — e aí se compararia o custo de posições distintas, não o custo do nó. Este
     * andador escolhe pelo índice, é reprodutível e não depende de velocidade nenhuma.
     */
    private fun posicoes(quantas: Int = 6): List<CanastraState> = (0 until quantas).map { i ->
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

    private inline fun cronometra(nome: String, voltas: Int, bloco: () -> Unit) {
        repeat(voltas / 4) { bloco() }
        val t0 = System.nanoTime()
        repeat(voltas) { bloco() }
        val us = (System.nanoTime() - t0) / 1000.0 / voltas
        println("PERFIL %-22s %8.2f µs".format(nome, us))
    }

    @Test
    fun `onde o no gasta`() {
        val ps = posicoes()
        val avaliador = CanastraEvaluatorImpl()
        val lancesPorPosicao = ps.map { it to CanastraGame.legalMoves(it) }
        println("PERFIL lances legais por posicao=${lancesPorPosicao.map { it.second.size }}")

        cronometra("legalMoves", 300) { for (p in ps) CanastraGame.legalMoves(p) }
        cronometra("evaluate", 300) { for (p in ps) avaliador.evaluate(p, p.turn) }
        cronometra("applyKnownLegal", 300) {
            for ((p, ms) in lancesPorPosicao) for (m in ms) CanastraGame.applyKnownLegal(p, m)
        }
        cronometra("ordering", 300) {
            for ((p, ms) in lancesPorPosicao) CanastraOrdering.order(p, ms)
        }
        cronometra("outcome", 300) { for (p in ps) CanastraGame.outcome(p) }
    }
}
