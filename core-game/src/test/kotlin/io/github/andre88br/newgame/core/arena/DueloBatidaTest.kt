package io.github.andre88br.newgame.core.arena

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.games.canastra.PesosCanastra
import kotlin.test.Test

/**
 * Põe frente a frente o avaliador que separa ponto de promessa e o que não separava.
 *
 * O "antigo" é reproduzido pelos próprios pesos: promessa que nunca decai e mão que sempre
 * pesa inteira. É a vantagem de os pesos serem dado — dá para reviver a versão anterior sem
 * mexer numa linha de código.
 */
class DueloBatidaTest {
    @Test
    fun `separar ponto de promessa joga melhor`() {
        val antigo = PesosCanastra.PADRAO.copy(promessaNoFimPorCento = 100, pisoDoPesoDaMaoPorCento = 100)
        val sementes = (System.getProperty("newgame.campanha").orEmpty().toIntOrNull() ?: 6)
        val r = arenaDaCanastra(maos = 1).duelo(
            a = iaDeAfinacao(PesosCanastra.PADRAO),
            b = iaDeAfinacao(antigo),
            sementes = 7_000L until (7_000L + sementes),
            dificuldade = Difficulty.HARD,
        )
        println("DUELO novo x antigo em $sementes sementes pareadas: $r")
    }
}
