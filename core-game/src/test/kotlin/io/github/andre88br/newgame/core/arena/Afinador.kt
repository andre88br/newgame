package io.github.andre88br.newgame.core.arena

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.games.canastra.PesosCanastra
import io.github.andre88br.newgame.core.games.canastra.canastraAi
import kotlin.math.roundToInt

/**
 * Cada peso afinável, com um nome e o par ler/escrever.
 *
 * Escrito à mão de propósito: reflexão resolveria em três linhas, mas então acrescentar um
 * peso novo aos [PesosCanastra] o arrastaria para a campanha sem ninguém decidir isso. Aqui,
 * incluir um peso é um ato deliberado — e é onde se registra que ele é palpite, não regra.
 */
private val AFINAVEIS: List<Triple<String, (PesosCanastra) -> Int, (PesosCanastra, Int) -> PesosCanastra>> = listOf(
    Triple("utilidadeDaCanastra", { p: PesosCanastra -> p.utilidadeDaCanastra }, { p: PesosCanastra, v: Int -> p.copy(utilidadeDaCanastra = v) }),
    Triple("promessaDeCrescimento", { p: PesosCanastra -> p.promessaDeCrescimento }, { p: PesosCanastra, v: Int -> p.copy(promessaDeCrescimento = v) }),
    Triple("promessaDeCrescimentoLimpa", { p: PesosCanastra -> p.promessaDeCrescimentoLimpa }, { p: PesosCanastra, v: Int -> p.copy(promessaDeCrescimentoLimpa = v) }),
    Triple("custoNaipeRepartido", { p: PesosCanastra -> p.custoNaipeRepartido }, { p: PesosCanastra, v: Int -> p.copy(custoNaipeRepartido = v) }),
    Triple("custoNaipeRepartidoDuplas", { p: PesosCanastra -> p.custoNaipeRepartidoDuplas }, { p: PesosCanastra, v: Int -> p.copy(custoNaipeRepartidoDuplas = v) }),
    Triple("custoCuringaNaMao", { p: PesosCanastra -> p.custoCuringaNaMao }, { p: PesosCanastra, v: Int -> p.copy(custoCuringaNaMao = v) }),
    Triple("pesoAmeacaDeBatida", { p: PesosCanastra -> p.pesoAmeacaDeBatida }, { p: PesosCanastra, v: Int -> p.copy(pesoAmeacaDeBatida = v) }),
    Triple("progressoBalanceado", { p: PesosCanastra -> p.progressoBalanceado }, { p: PesosCanastra, v: Int -> p.copy(progressoBalanceado = v) }),
)

fun diferencaEntre(a: PesosCanastra, b: PesosCanastra): String =
    AFINAVEIS.mapNotNull { (nome, ler, _) ->
        val de = ler(a)
        val para = ler(b)
        if (de == para) null else "$nome $de→$para"
    }.joinToString(", ").ifEmpty { "nada mudou" }

/**
 * Sorteia uma variação dos pesos, mexendo em todos de uma vez.
 *
 * A perturbação é **relativa** ao próprio peso porque a escala varia muito entre eles — um
 * vale três, outro vale mil e seiscentos. Somar um passo fixo mudaria o de três de cabo a
 * rabo e nem cocaria o de mil e seiscentos.
 */
private fun perturbar(pesos: PesosCanastra, forca: Double, rng: Rng): PesosCanastra {
    var atual = pesos
    var gerador = rng
    for ((_, ler, escrever) in AFINAVEIS) {
        val sorteio = gerador.nextInt(2001)
        gerador = sorteio.rng
        val fator = 1.0 + forca * (sorteio.value - 1000) / 1000.0
        val novo = (ler(atual) * fator).roundToInt().coerceAtLeast(0)
        atual = escrever(atual, novo)
    }
    return atual
}

data class Campanha(val campeao: PesosCanastra, val rodadasAceitas: Int, val historico: List<String>)

/**
 * Afina os pesos por subida de encosta com portão de significância.
 *
 * A cada rodada, sorteia um desafiante a partir do campeão e põe os dois na arena. O
 * desafiante só toma o lugar se vencer **e** a vitória passar da barra de erro — sem esse
 * portão a campanha passeia ao sabor do ruído e "melhora" para lugar nenhum.
 *
 * As sementes mudam a cada rodada de propósito. Fixá-las faria a campanha decorar aquelas
 * distribuições em vez de aprender a jogar, e o ganho evaporaria no celular. Por isso, no
 * fim, quem decide é uma bateria de sementes que a campanha nunca viu — ver [portaoFinal].
 *
 * A busca é fixada em profundidade, com relógio folgado: uma campanha medida por tempo daria
 * resultado diferente em cada máquina, e a decisão sobre o que entra no aplicativo não pode
 * depender de quem rodou o teste.
 */
fun afinarCanastra(
    inicio: PesosCanastra = PesosCanastra.PADRAO,
    rodadas: Int = 12,
    sementesPorRodada: Int = 12,
    forca: Double = 0.35,
    maos: Int = 1,
    semente: Long = 20_260_824L,
    log: (String) -> Unit = ::println,
): Campanha {
    val arena = arenaDaCanastra(maos = maos)
    var campeao = inicio
    var aceitas = 0
    val historico = mutableListOf<String>()
    var rng = Rng.seeded(semente)

    for (rodada in 0 until rodadas) {
        val salto = rng.nextInt(1_000_000)
        rng = salto.rng
        val desafiante = perturbar(campeao, forca, Rng.seeded(salto.value.toLong()))
        val primeira = 1_000L + rodada * sementesPorRodada
        val resultado = arena.duelo(
            a = iaDeAfinacao(desafiante),
            b = iaDeAfinacao(campeao),
            sementes = primeira until (primeira + sementesPorRodada),
            dificuldade = Difficulty.HARD,
        )
        val venceu = resultado.margemMedia > 0 && resultado.significativo
        val linha = "rodada $rodada: $resultado ${if (venceu) "ACEITO" else "recusado"} [${diferencaEntre(campeao, desafiante)}]"
        log(linha)
        historico += linha
        if (venceu) {
            campeao = desafiante
            aceitas++
        }
    }
    return Campanha(campeao, aceitas, historico)
}

/**
 * A IA usada na campanha: profundidade fixa, poucos mundos e sem sorteio de erro.
 *
 * Sem o sorteio de erro porque afinar contra ruído proposital só faz a campanha precisar de
 * mais partidas para enxergar a mesma diferença.
 *
 * O relógio existe só como válvula: a intenção é que a profundidade seja o limite, mas há
 * posições em que um turno de canastra se ramifica muito e a busca demora demais. Sem teto,
 * uma dessas posições trava a campanha inteira — foi o que aconteceu na primeira tentativa.
 * Com um teto folgado, quase toda posição termina pela profundidade e só as patológicas são
 * cortadas.
 */
fun iaDeAfinacao(pesos: PesosCanastra, profundidade: Int = 1, mundos: Int = 2) = canastraAi(
    pesos = pesos,
    limites = { SearchLimits(maxDepth = profundidade, timeBudgetMillis = 250) },
    mundos = { mundos },
    chanceDeErro = { 0 },
)

/**
 * O juiz final, em sementes que a campanha nunca viu.
 *
 * É o que separa "achou um peso melhor" de "decorou as partidas do treino".
 */
fun portaoFinal(
    candidato: PesosCanastra,
    campeaoAtual: PesosCanastra = PesosCanastra.PADRAO,
    sementes: LongRange = 90_000L..90_039L,
    maos: Int = 1,
): ResultadoDoDuelo = arenaDaCanastra(maos = maos).duelo(
    a = iaDeAfinacao(candidato),
    b = iaDeAfinacao(campeaoAtual),
    sementes = sementes,
    dificuldade = Difficulty.HARD,
)
