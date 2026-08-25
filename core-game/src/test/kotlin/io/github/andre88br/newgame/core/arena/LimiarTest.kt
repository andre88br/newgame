package io.github.andre88br.newgame.core.arena

import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.games.canastra.CANASTRA_OPENING_THRESHOLD
import io.github.andre88br.newgame.core.games.canastra.CanastraGame
import io.github.andre88br.newgame.core.games.canastra.CanastraState
import kotlin.test.Test

/**
 * Mede o preço da regra do primeiro jogo de 150 pontos, que é um problema em aberto.
 *
 * Enquanto um time está acima de [CANASTRA_OPENING_THRESHOLD] e ainda não fez o primeiro
 * jogo, `legalMoves` consulta `isOpeningPathPreserved` uma vez por lance candidato, e essa
 * checagem procura recursivamente, refazendo os jogos possíveis a cada nível, se a mão que
 * sobra ainda alcança os 150. Medido aqui: oito vezes mais caro do que a mesma posição com o
 * primeiro jogo já feito.
 *
 * Isso não é só lentidão de teste: a IA busca por orçamento de tempo, então ela fica oito
 * vezes mais rasa exatamente na reta final da partida, que é quando decide.
 *
 * Duas tentativas de micro-otimização — podar pelo teto de pontos da mão e memoizar as mãos
 * repetidas — renderam menos de dez por cento e foram descartadas por não pagarem a
 * complexidade. O conserto de verdade é trocar a recursão por uma conta de subconjuntos, e
 * está por fazer. Este teste é o instrumento para medir quando alguém for fazê-lo; ele não
 * falha por tempo, porque cronômetro em integração contínua só gera alarme falso.
 */
class LimiarTest {
    private fun posicoes(quantas: Int = 5): List<CanastraState> = (0 until quantas).map { i ->
        var state = CanastraGame.initialState(MatchConfig(300L + i, seats = 4))
        var passo = 0
        while (CanastraGame.outcome(state) == Outcome.InProgress && passo < 50) {
            val legais = CanastraGame.legalMoves(state)
            if (legais.isEmpty()) break
            state = (CanastraGame.applyMove(state, legais[(passo * 5 + i) % legais.size]) as? MoveResult.Ok)?.state ?: break
            passo++
        }
        state
    }

    private inline fun cronometra(nome: String, voltas: Int, bloco: () -> Unit) {
        repeat(voltas / 4) { bloco() }
        val t0 = System.nanoTime()
        repeat(voltas) { bloco() }
        println("LIMIAR %-34s %9.2f µs".format(nome, (System.nanoTime() - t0) / 1000.0 / voltas))
    }

    @Test
    fun `custo de legalMoves abaixo e acima do limiar de abertura`() {
        val base = posicoes()
        // mesma posicao, so o placar muda: abaixo do limiar a regra dos 150 nem se aplica
        val abaixo = base.map { it.copy(scores = List(it.teams) { 0 }, firstMeldDone = List(it.teams) { false }) }
        val acima = base.map {
            it.copy(
                scores = List(it.teams) { CANASTRA_OPENING_THRESHOLD + 100 },
                firstMeldDone = List(it.teams) { false },
                openingProgress = List(it.teams) { 0 },
            )
        }
        println("LIMIAR maos=${base.map { it.handSize(it.turn) }}")
        // mesmo placar alto, mas com o primeiro jogo ja feito: a regra dos 150 nao se aplica
        val acimaComPrimeiroJogo = acima.map { it.copy(firstMeldDone = List(it.teams) { true }) }
        cronometra("abaixo de 1500", 200) { for (p in abaixo) CanastraGame.legalMoves(p) }
        cronometra("acima, 1o jogo pendente", 200) { for (p in acima) CanastraGame.legalMoves(p) }
        cronometra("acima, 1o jogo ja feito", 200) { for (p in acimaComPrimeiroJogo) CanastraGame.legalMoves(p) }
    }
}
