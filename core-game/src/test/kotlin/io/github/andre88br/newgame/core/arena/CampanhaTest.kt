package io.github.andre88br.newgame.core.arena

import io.github.andre88br.newgame.core.games.canastra.PesosCanastra
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A campanha que afina os pesos da canastra.
 *
 * Roda curta por padrão — só o bastante para provar que a máquina toda funciona sem atrasar
 * a suíte. A campanha de verdade é pedida na linha de comando:
 *
 * ```
 * ./gradlew :core-game:test --tests '*CampanhaTest*' -Pnewgame.campanha=10,32
 * ```
 *
 * onde os números são rodadas e sementes por rodada. O resultado sai no console: o que cada
 * rodada decidiu, os pesos vencedores e o veredito do portão final em sementes inéditas.
 * Nada é gravado automaticamente — quem decide se aqueles números entram no aplicativo é
 * uma pessoa, olhando a margem e a barra de erro.
 */
class CampanhaTest {

    @Test
    fun `campanha de afinacao`() {
        val pedido = System.getProperty("newgame.campanha").orEmpty()
        val longa = pedido.isNotBlank()
        val (rodadas, sementes) = if (longa) {
            val partes = pedido.split(",")
            partes[0].trim().toInt() to (partes.getOrNull(1)?.trim()?.toInt() ?: 24)
        } else {
            1 to 3
        }

        println("CAMPANHA ${if (longa) "longa" else "curta (use -Pnewgame.campanha=rodadas,sementes)"}: $rodadas rodadas de $sementes sementes")
        val campanha = afinarCanastra(rodadas = rodadas, sementesPorRodada = sementes)
        println("CAMPANHA campeão: ${diferencaEntre(PesosCanastra.PADRAO, campanha.campeao)}")
        println("CAMPANHA aceitas: ${campanha.rodadasAceitas} de $rodadas")

        if (campanha.campeao != PesosCanastra.PADRAO) {
            val veredito = portaoFinal(campanha.campeao)
            println("CAMPANHA portão final (sementes inéditas): $veredito")
            println("CAMPANHA ${if (veredito.margemMedia > 0 && veredito.significativo) "VALE TROCAR" else "não vale trocar"}")
        }

        assertTrue(campanha.historico.size == rodadas, "toda rodada precisa deixar registro")
    }
}
