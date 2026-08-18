package io.github.andre88br.newgame.core.games.poker

import io.github.andre88br.newgame.core.cards.Card

/** As nove categorias de mão do pôquer, da mais fraca à mais forte — a ordem de declaração é a força. */
enum class PokerHandCategory {
    HIGH_CARD,
    PAIR,
    TWO_PAIR,
    THREE_OF_A_KIND,
    STRAIGHT,
    FLUSH,
    FULL_HOUSE,
    FOUR_OF_A_KIND,
    STRAIGHT_FLUSH,
}

/**
 * O valor de uma mão de cinco cartas.
 *
 * A [categoria][category] decide primeiro; dentro da mesma categoria, [tiebreakers] desempata
 * em ordem — do mais importante ao menos, na mesma sequência em que uma pessoa comparia duas
 * trincas ou dois pares na mesa: o valor do grupo maior primeiro, depois o do menor, depois as
 * cartas soltas da maior para a menor.
 */
data class PokerHandValue(
    val category: PokerHandCategory,
    val tiebreakers: List<Int>,
) : Comparable<PokerHandValue> {
    override fun compareTo(other: PokerHandValue): Int {
        val porCategoria = category.ordinal.compareTo(other.category.ordinal)
        if (porCategoria != 0) return porCategoria
        for (i in tiebreakers.indices) {
            val cmp = tiebreakers[i].compareTo(other.tiebreakers.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }
}

/**
 * A melhor mão de cinco cartas dentro de [cards] — no Texas Hold'em, as duas da mão mais até
 * cinco da mesa, mas a função serve para qualquer conjunto de cinco cartas ou mais.
 *
 * Sete cartas cabem em só vinte e uma combinações de cinco: testar todas é mais barato — e
 * muito mais fácil de provar correto, com um teste por categoria — do que qualquer atalho que
 * tente reconhecer a categoria sem montar a mão.
 */
fun bestHand(cards: List<Card>): PokerHandValue {
    require(cards.size >= 5) { "precisa de ao menos cinco cartas para formar uma mão: veio ${cards.size}" }
    return combinacoesDeCinco(cards).map(::avaliaCincoCartas).max()
}

/** Avalia exatamente cinco cartas — o bloco que [bestHand] tenta em cada combinação possível. */
private fun avaliaCincoCartas(cinco: List<Card>): PokerHandValue {
    val ordens = cinco.map { it.rank.order }.sortedDescending()
    val naipeUnico = cinco.all { it.suit == cinco.first().suit }

    // Grupos por quantidade de cartas do mesmo valor, do maior grupo para o menor — e, dentro
    // do mesmo tamanho de grupo, do valor maior para o menor. É a ordem certa de desempate
    // tanto para o full house (a trinca decide antes do par) quanto para quadra/trinca/par.
    val grupos = ordens.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })
        .map { it.key }

    val distintos = ordens.distinct().sorted()
    val topoDaSequencia = when {
        distintos.size == 5 && distintos.last() - distintos.first() == 4 -> distintos.last()
        distintos == listOf(2, 3, 4, 5, 14) -> 5 // A-2-3-4-5: o ás desce, o cinco é o topo.
        else -> null
    }
    val tamanhos = ordens.groupingBy { it }.eachCount().values.sortedDescending()

    return when {
        topoDaSequencia != null && naipeUnico -> PokerHandValue(PokerHandCategory.STRAIGHT_FLUSH, listOf(topoDaSequencia))
        tamanhos.first() == 4 -> PokerHandValue(PokerHandCategory.FOUR_OF_A_KIND, grupos)
        tamanhos == listOf(3, 2) -> PokerHandValue(PokerHandCategory.FULL_HOUSE, grupos)
        naipeUnico -> PokerHandValue(PokerHandCategory.FLUSH, ordens)
        topoDaSequencia != null -> PokerHandValue(PokerHandCategory.STRAIGHT, listOf(topoDaSequencia))
        tamanhos.first() == 3 -> PokerHandValue(PokerHandCategory.THREE_OF_A_KIND, grupos)
        tamanhos == listOf(2, 2, 1) -> PokerHandValue(PokerHandCategory.TWO_PAIR, grupos)
        tamanhos.first() == 2 -> PokerHandValue(PokerHandCategory.PAIR, grupos)
        else -> PokerHandValue(PokerHandCategory.HIGH_CARD, ordens)
    }
}

/** Todas as combinações de cinco cartas dentro de [cards], sem repetir e sem se importar com a ordem. */
private fun combinacoesDeCinco(cards: List<Card>): List<List<Card>> {
    fun combinar(restantes: List<Card>, k: Int): List<List<Card>> = when {
        k == 0 -> listOf(emptyList())
        restantes.size < k -> emptyList()
        else -> {
            val cabeca = restantes.first()
            val cauda = restantes.drop(1)
            combinar(cauda, k - 1).map { listOf(cabeca) + it } + combinar(cauda, k)
        }
    }
    return combinar(cards, 5)
}
