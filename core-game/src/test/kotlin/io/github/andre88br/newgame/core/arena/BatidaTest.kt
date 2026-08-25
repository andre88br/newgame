package io.github.andre88br.newgame.core.arena

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.games.canastra.CanastraGame
import io.github.andre88br.newgame.core.games.canastra.CanastraPhase
import io.github.andre88br.newgame.core.games.canastra.CanastraState
import io.github.andre88br.newgame.core.games.canastra.PesosCanastra
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Mede quantas chances de fechar a rodada a IA deixa passar.
 *
 * É a métrica do bug em que ela encaixava cartas para poder bater e então descartava. Com o
 * avaliador que separa ponto de promessa, a taxa de chances perdidas caiu de sessenta e nove
 * por cento para dez.
 *
 * Roda curta por padrão, porque auto-jogo é caro. A medição de verdade é pedida na linha de
 * comando, igual à campanha:
 *
 * ```
 * ./gradlew :core-game:test --tests '*BatidaTest*' -Pnewgame.campanha=5
 * ```
 */
class BatidaTest {

    /** Existe alguma sequência de lances, neste turno, que fecha a rodada? */
    private fun podeBaterAgora(inicial: CanastraState, profundidade: Int = 7): Boolean {
        fun busca(atual: CanastraState, resta: Int): Boolean {
            if (atual.handNumber != inicial.handNumber) return true
            if (resta == 0 || atual.turn != inicial.turn) return false
            for (m in CanastraGame.legalMoves(atual)) {
                val depois = (CanastraGame.applyMove(atual, m) as? MoveResult.Ok)?.state ?: continue
                if (busca(depois, resta - 1)) return true
            }
            return false
        }
        return busca(inicial, profundidade)
    }

    @Test
    fun `a ia aproveita a maioria das chances de fechar a rodada`() {
        val quantasSementes = System.getProperty("newgame.campanha").orEmpty().toLongOrNull() ?: 1L
        val tetoDeLances = if (quantasSementes > 1L) 500 else 150
        val ia = iaDeAfinacao(PesosCanastra.PADRAO, profundidade = 2)

        var perdidas = 0
        var chances = 0
        var turnos = 0

        for (semente in 0L until quantasSementes) {
            var estado = CanastraGame.initialState(MatchConfig(500L + semente, seats = 4))
            var lances = 0
            while (CanastraGame.outcome(estado) == Outcome.InProgress && lances < tetoDeLances) {
                val cadeira = estado.turn
                val maoNo = estado.handNumber
                var podia = false
                var passos = 0
                turnos++

                // joga o turno inteiro, olhando a cada lance se já dava para fechar
                while (estado.turn == cadeira && estado.handNumber == maoNo && passos < 12) {
                    if (estado.phase == CanastraPhase.PLAY && !podia && podeBaterAgora(estado)) podia = true
                    val move = ia.chooseMove(CanastraGame.redactFor(estado, estado.turn), Difficulty.HARD, lances.toLong())
                        ?: break
                    estado = (CanastraGame.applyMove(estado, move) as? MoveResult.Ok)?.state ?: break
                    passos++
                    lances++
                }
                if (passos == 0) break
                if (podia) {
                    chances++
                    if (estado.handNumber == maoNo) perdidas++
                }
            }
        }

        println("BATIDA turnos=$turnos chances=$chances perdidas=$perdidas")
        if (chances >= 4) {
            assertTrue(
                perdidas * 2 <= chances,
                "a maioria das chances de fechar a rodada tinha que ser aproveitada, e foram perdidas $perdidas de $chances",
            )
        }
    }
}
