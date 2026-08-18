package io.github.andre88br.newgame.core.games.poker

import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O avaliador de mãos é a peça que todo o resto do pôquer depende — decide quem leva o pote
 * no showdown. Um teste por categoria, mais os casos que costumam sair errado numa primeira
 * tentativa: o A-2-3-4-5 (o ás baixando), o full house com duas trincas na mesma mão de sete
 * cartas, e o desempate por carta alta dentro da mesma categoria.
 */
class PokerHandTest {

    private fun carta(rank: Rank, suit: Suit) = Card(rank, suit)

    // -------- categorias, da mais fraca à mais forte --------

    @Test
    fun `carta alta quando nao ha par nem sequencia nem flush`() {
        val mao = listOf(
            carta(Rank.TWO, Suit.CLUBS), carta(Rank.FIVE, Suit.DIAMONDS), carta(Rank.NINE, Suit.HEARTS),
            carta(Rank.JACK, Suit.SPADES), carta(Rank.KING, Suit.CLUBS),
        )
        val valor = bestHand(mao)
        assertEquals(PokerHandCategory.HIGH_CARD, valor.category)
        assertEquals(listOf(13, 11, 9, 5, 2), valor.tiebreakers)
    }

    @Test
    fun `um par`() {
        val mao = listOf(
            carta(Rank.SEVEN, Suit.CLUBS), carta(Rank.SEVEN, Suit.DIAMONDS), carta(Rank.NINE, Suit.HEARTS),
            carta(Rank.JACK, Suit.SPADES), carta(Rank.KING, Suit.CLUBS),
        )
        assertEquals(PokerHandCategory.PAIR, bestHand(mao).category)
    }

    @Test
    fun `dois pares, o maior par desempata primeiro`() {
        val maoA = listOf(
            carta(Rank.SEVEN, Suit.CLUBS), carta(Rank.SEVEN, Suit.DIAMONDS),
            carta(Rank.THREE, Suit.HEARTS), carta(Rank.THREE, Suit.SPADES), carta(Rank.KING, Suit.CLUBS),
        )
        val maoB = listOf(
            carta(Rank.SIX, Suit.CLUBS), carta(Rank.SIX, Suit.DIAMONDS),
            carta(Rank.FIVE, Suit.HEARTS), carta(Rank.FIVE, Suit.SPADES), carta(Rank.ACE, Suit.CLUBS),
        )
        val valorA = bestHand(maoA)
        val valorB = bestHand(maoB)
        assertEquals(PokerHandCategory.TWO_PAIR, valorA.category)
        assertTrue(valorA > valorB, "par de sete e três bate par de seis e cinco, mesmo com ás solto do outro lado")
    }

    @Test
    fun `trinca`() {
        val mao = listOf(
            carta(Rank.NINE, Suit.CLUBS), carta(Rank.NINE, Suit.DIAMONDS), carta(Rank.NINE, Suit.HEARTS),
            carta(Rank.JACK, Suit.SPADES), carta(Rank.KING, Suit.CLUBS),
        )
        assertEquals(PokerHandCategory.THREE_OF_A_KIND, bestHand(mao).category)
    }

    @Test
    fun `sequencia comum`() {
        val mao = listOf(
            carta(Rank.SIX, Suit.CLUBS), carta(Rank.SEVEN, Suit.DIAMONDS), carta(Rank.EIGHT, Suit.HEARTS),
            carta(Rank.NINE, Suit.SPADES), carta(Rank.TEN, Suit.CLUBS),
        )
        val valor = bestHand(mao)
        assertEquals(PokerHandCategory.STRAIGHT, valor.category)
        assertEquals(listOf(10), valor.tiebreakers)
    }

    @Test
    fun `sequencia do as baixo, a-2-3-4-5, vale pelo cinco`() {
        val mao = listOf(
            carta(Rank.ACE, Suit.CLUBS), carta(Rank.TWO, Suit.DIAMONDS), carta(Rank.THREE, Suit.HEARTS),
            carta(Rank.FOUR, Suit.SPADES), carta(Rank.FIVE, Suit.CLUBS),
        )
        val valor = bestHand(mao)
        assertEquals(PokerHandCategory.STRAIGHT, valor.category)
        assertEquals(listOf(5), valor.tiebreakers, "o ás desce: essa sequência vale menos que 2-3-4-5-6")

        val seisAlto = listOf(
            carta(Rank.TWO, Suit.HEARTS), carta(Rank.THREE, Suit.CLUBS), carta(Rank.FOUR, Suit.DIAMONDS),
            carta(Rank.FIVE, Suit.SPADES), carta(Rank.SIX, Suit.HEARTS),
        )
        assertTrue(bestHand(seisAlto) > valor, "2-3-4-5-6 bate a sequência do ás baixo")
    }

    @Test
    fun `nao confunde quatro naipes iguais mais um solto com flush`() {
        // Quatro de copas e um de espadas: por pouco não é flush.
        val mao = listOf(
            carta(Rank.TWO, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.NINE, Suit.HEARTS),
            carta(Rank.JACK, Suit.HEARTS), carta(Rank.KING, Suit.SPADES),
        )
        assertEquals(PokerHandCategory.HIGH_CARD, bestHand(mao).category)
    }

    @Test
    fun flush() {
        val mao = listOf(
            carta(Rank.TWO, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.NINE, Suit.HEARTS),
            carta(Rank.JACK, Suit.HEARTS), carta(Rank.KING, Suit.HEARTS),
        )
        assertEquals(PokerHandCategory.FLUSH, bestHand(mao).category)
    }

    @Test
    fun `full house`() {
        val mao = listOf(
            carta(Rank.NINE, Suit.CLUBS), carta(Rank.NINE, Suit.DIAMONDS), carta(Rank.NINE, Suit.HEARTS),
            carta(Rank.KING, Suit.SPADES), carta(Rank.KING, Suit.CLUBS),
        )
        val valor = bestHand(mao)
        assertEquals(PokerHandCategory.FULL_HOUSE, valor.category)
        assertEquals(listOf(9, 13), valor.tiebreakers)
    }

    @Test
    fun `full house com sete cartas escolhe a trinca maior entre duas possiveis`() {
        // Nove-nove-nove-rei-rei-rei-dois: dá para fazer full house dos dois lados; a trinca
        // de rei é a que vale mais.
        val sete = listOf(
            carta(Rank.NINE, Suit.CLUBS), carta(Rank.NINE, Suit.DIAMONDS), carta(Rank.NINE, Suit.HEARTS),
            carta(Rank.KING, Suit.SPADES), carta(Rank.KING, Suit.CLUBS), carta(Rank.KING, Suit.DIAMONDS),
            carta(Rank.TWO, Suit.HEARTS),
        )
        val valor = bestHand(sete)
        assertEquals(PokerHandCategory.FULL_HOUSE, valor.category)
        assertEquals(listOf(13, 9), valor.tiebreakers, "a trinca de rei é a que decide, o par de nove é só o resto")
    }

    @Test
    fun quadra() {
        val mao = listOf(
            carta(Rank.NINE, Suit.CLUBS), carta(Rank.NINE, Suit.DIAMONDS), carta(Rank.NINE, Suit.HEARTS),
            carta(Rank.NINE, Suit.SPADES), carta(Rank.KING, Suit.CLUBS),
        )
        assertEquals(PokerHandCategory.FOUR_OF_A_KIND, bestHand(mao).category)
    }

    @Test
    fun `straight flush`() {
        val mao = listOf(
            carta(Rank.SIX, Suit.HEARTS), carta(Rank.SEVEN, Suit.HEARTS), carta(Rank.EIGHT, Suit.HEARTS),
            carta(Rank.NINE, Suit.HEARTS), carta(Rank.TEN, Suit.HEARTS),
        )
        val valor = bestHand(mao)
        assertEquals(PokerHandCategory.STRAIGHT_FLUSH, valor.category)
        assertEquals(listOf(10), valor.tiebreakers)
    }

    // -------- sete cartas: a mesa entra na conta --------

    @Test
    fun `com sete cartas acha a melhor combinacao de cinco, nao so as duas da mao`() {
        // Mão: dois soltos, sem nada a ver com sequência nenhuma. Mesa sozinha já fecha uma.
        val sete = listOf(
            carta(Rank.TWO, Suit.CLUBS), carta(Rank.KING, Suit.DIAMONDS), // a mão
            carta(Rank.SIX, Suit.HEARTS), carta(Rank.SEVEN, Suit.SPADES), carta(Rank.EIGHT, Suit.CLUBS),
            carta(Rank.NINE, Suit.DIAMONDS), carta(Rank.TEN, Suit.HEARTS),
        )
        val valor = bestHand(sete)
        assertEquals(PokerHandCategory.STRAIGHT, valor.category)
        assertEquals(listOf(10), valor.tiebreakers)
    }

    @Test
    fun `duas maos iguais na categoria e nos desempates empatam de verdade`() {
        val maoA = listOf(
            carta(Rank.NINE, Suit.CLUBS), carta(Rank.NINE, Suit.DIAMONDS), carta(Rank.KING, Suit.HEARTS),
            carta(Rank.JACK, Suit.SPADES), carta(Rank.FOUR, Suit.CLUBS),
        )
        val maoB = listOf(
            carta(Rank.NINE, Suit.HEARTS), carta(Rank.NINE, Suit.SPADES), carta(Rank.KING, Suit.CLUBS),
            carta(Rank.JACK, Suit.DIAMONDS), carta(Rank.FOUR, Suit.HEARTS),
        )
        assertEquals(0, bestHand(maoA).compareTo(bestHand(maoB)), "mesmo par, mesmos soltos: é empate de verdade")
    }
}
