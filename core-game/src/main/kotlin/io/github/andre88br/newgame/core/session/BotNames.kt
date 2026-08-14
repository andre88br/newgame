package io.github.andre88br.newgame.core.session

import io.github.andre88br.newgame.core.engine.Rng

/**
 * Os nomes que a máquina usa quando ocupa uma cadeira.
 *
 * Mora no motor, e não na tela, por dois motivos. O primeiro é o teste: sortear nomes
 * distintos e estáveis para a mesma semente é regra, não desenho, e regra tem teste. O
 * segundo é que a semente vem da partida — reabrir um jogo salvo precisa trazer de volta os
 * mesmos adversários, e não uma mesa de estranhos.
 *
 * São nomes próprios curtos, sem sobrenome de propósito: cabem no rótulo da mão do dominó
 * num celular estreito, e nome próprio não se traduz — a mesma lista serve nos dois idiomas.
 */
object BotNames {

    /**
     * A lista de onde os nomes saem.
     *
     * Grande o bastante para uma mesa de quatro raramente repetir a mesma dupla de
     * adversários em partidas seguidas, e curta o bastante para caber de olho.
     */
    val ALL: List<String> = listOf(
        "Ana", "Bento", "Bia", "Caio", "Célia", "Davi", "Dora", "Elis",
        "Fábio", "Gabi", "Gil", "Heitor", "Iara", "Ivo", "Joana", "Kaio",
        "Lia", "Lucas", "Malu", "Miro", "Nina", "Noel", "Olívia", "Otto",
        "Pedro", "Rita", "Rui", "Sofia", "Téo", "Vera", "Vitor", "Zeca",
    )

    /**
     * Sorteia [count] nomes **distintos**, sempre os mesmos para a mesma [seed].
     *
     * Distintos importa mais do que parece: numa mesa de quatro, dois adversários chamados
     * "Nina" tornariam o rótulo da mão do dominó inútil — a pessoa não saberia de qual das
     * duas mãos está falando o aviso na tela.
     *
     * Pedir mais nomes do que a lista tem não é erro: a partir daí eles voltam numerados
     * ("Ana 2"), porque ficar sem nome seria pior do que repetir com número.
     */
    fun pick(count: Int, seed: Long, exclude: Collection<String> = emptyList()): List<String> {
        if (count <= 0) return emptyList()

        val taken = exclude.map { it.trim().lowercase() }.toMutableSet()
        val shuffled = Rng.seeded(seed).shuffle(ALL).value

        val chosen = ArrayList<String>(count)
        var round = 0
        while (chosen.size < count) {
            for (name in shuffled) {
                if (chosen.size == count) break
                // Na primeira volta os nomes saem limpos; se a lista acabar, a volta
                // seguinte numera em vez de repetir.
                val candidate = if (round == 0) name else "$name ${round + 1}"
                if (taken.add(candidate.lowercase())) chosen += candidate
            }
            round++
            // Trava de segurança: com `exclude` gigante, uma volta pode não render nada.
            if (round > count + 1) {
                while (chosen.size < count) chosen += "${ALL.first()} ${chosen.size + round}"
            }
        }
        return chosen
    }

    /**
     * Deixa um nome digitado em forma de ser guardado e mostrado.
     *
     * Corta o excesso de espaço e limita o tamanho: um nome de duzentas letras empurraria o
     * placar para fora da tela, e o campo de texto não tem como impedir quem cola.
     */
    fun sanitize(raw: String, max: Int = MAX_LENGTH): String =
        raw.trim().replace(WHITESPACE, " ").take(max).trim()

    /** Teto do que cabe num rótulo de placar sem empurrar o resto da linha. */
    const val MAX_LENGTH: Int = 16

    private val WHITESPACE = Regex("\\s+")
}
