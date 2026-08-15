package io.github.andre88br.newgame.core.cards

import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Roll
import kotlinx.serialization.Serializable

/**
 * Naipe da carta.
 *
 * A ordem das constantes é a de referência do baralho e não vale como força: quem decide
 * qual naipe ganha de qual é cada jogo, e na maioria deles naipe nenhum ganha de outro.
 */
@Serializable
enum class Suit(val symbol: String) {
    CLUBS("♣"),
    DIAMONDS("♦"),
    HEARTS("♥"),
    SPADES("♠"),
    ;

    /** Vermelho ou preto. No três da canastra é isto, e não o naipe, que muda a regra. */
    val isRed: Boolean get() = this == DIAMONDS || this == HEARTS
}

/**
 * Valor da carta.
 *
 * [order] cresce do dois ao ás, que é a ordem em que as cartas se comparam na maioria dos
 * jogos — inclusive nas sequências da canastra e do pife. Onde o ás vale por baixo (a
 * sequência A-2-3), quem trata disso é o jogo, porque só ele sabe se a volta é permitida.
 */
@Serializable
enum class Rank(val order: Int, val short: String) {
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A"),

    /** Curinga. Não entra em baralho comum; a canastra pede baralho com ele. */
    JOKER(0, "★"),

    /**
     * Carta de outra pessoa, virada para baixo.
     *
     * Existe pelo mesmo motivo da peça oculta do dominó: a tela precisa saber **quantas**
     * cartas o adversário tem sem saber quais são, e trocar a lista por um número perderia
     * a contagem em qualquer lugar que percorra a mão.
     */
    HIDDEN(-1, "?"),
}

/**
 * Uma carta.
 *
 * O curinga também é uma carta com naipe: o naipe dele não joga, mas serve para separar os
 * dois curingas de um baralho — e, no baralho duplo da canastra, os quatro. Sem isso duas
 * cartas iguais viveriam como o mesmo objeto e sumiriam ao entrar num conjunto.
 */
@Serializable
data class Card(val rank: Rank, val suit: Suit) {

    val isHidden: Boolean get() = rank == Rank.HIDDEN

    val isJoker: Boolean get() = rank == Rank.JOKER

    val isRed: Boolean get() = suit.isRed

    override fun toString(): String = when (rank) {
        Rank.HIDDEN -> "??"
        Rank.JOKER -> if (isRed) "★v" else "★p"
        else -> "${rank.short}${suit.symbol}"
    }

    companion object {
        /** Carta virada para baixo. Veja [Rank.HIDDEN]. */
        val HIDDEN: Card = Card(Rank.HIDDEN, Suit.CLUBS)
    }
}

/** Os treze valores de um baralho comum, sem curinga e sem carta oculta. */
val PLAYING_RANKS: List<Rank> = Rank.entries.filter { it != Rank.JOKER && it != Rank.HIDDEN }

/**
 * As 52 cartas de um baralho, em ordem fixa.
 *
 * Ordem fixa de propósito: quem embaralha é [Rng.shuffle], a partir da semente guardada na
 * partida. Um baralho que já nascesse em ordem aleatória tiraria do registro a capacidade
 * de reproduzir a mesma distribuição ao reabrir o jogo salvo.
 */
fun standardDeck(): List<Card> = buildList {
    for (suit in Suit.entries) {
        for (rank in PLAYING_RANKS) add(Card(rank, suit))
    }
}

/**
 * Baralho de [decks] baralhos, cada um com [jokersPerDeck] curingas.
 *
 * A canastra brasileira se joga com dois baralhos e quatro curingas; o pife e a copas, com
 * um baralho e nenhum. Os curingas saem um vermelho e um preto por baralho, que é como eles
 * vêm na caixa.
 */
fun deckOf(decks: Int = 1, jokersPerDeck: Int = 0): List<Card> = buildList {
    require(decks >= 1) { "Baralho precisa de ao menos um: veio $decks" }
    repeat(decks) {
        addAll(standardDeck())
        repeat(jokersPerDeck) { index ->
            add(Card(Rank.JOKER, if (index % 2 == 0) Suit.HEARTS else Suit.SPADES))
        }
    }
}

/** Embaralha e reparte [hands] mãos de [size] cartas, devolvendo também o que sobrou. */
fun Rng.deal(deck: List<Card>, hands: Int, size: Int): Roll<Deal> {
    require(hands >= 1) { "Mesa precisa de ao menos uma mão: veio $hands" }
    require(hands * size <= deck.size) {
        "O baralho tem ${deck.size} cartas e a distribuição pede ${hands * size}"
    }
    val shuffled = shuffle(deck)
    val repartidas = List(hands) { seat ->
        shuffled.value.subList(seat * size, (seat + 1) * size).toList()
    }
    val resto = shuffled.value.drop(hands * size)
    return Roll(Deal(repartidas, resto), shuffled.rng)
}

/** O resultado de repartir: as mãos e o que sobrou para o monte. */
data class Deal(val hands: List<List<Card>>, val rest: List<Card>)

/** Troca a mão inteira por cartas viradas, mantendo quantas são. */
fun List<Card>.hidden(): List<Card> = List(size) { Card.HIDDEN }

/**
 * Ordena a mão como quem joga arrumaria: por naipe, e dentro do naipe do menor ao maior.
 *
 * Serve à tela. Uma mão na ordem em que caiu do baralho obriga quem joga a varrer a fileira
 * inteira a cada decisão, e é o tipo de atrito que faz um jogo de cartas parecer difícil
 * quando o difícil devia ser o jogo, não achar a carta.
 */
fun List<Card>.sortedForHand(): List<Card> =
    sortedWith(compareBy({ it.suit.ordinal }, { it.rank.order }))
