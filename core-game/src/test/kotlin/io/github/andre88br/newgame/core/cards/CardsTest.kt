package io.github.andre88br.newgame.core.cards

import io.github.andre88br.newgame.core.engine.Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A base que os quatro jogos de carta dividem. Erro aqui aparece longe daqui — carta
 * repetida numa mão, mão sumindo ao ser guardada num conjunto —, e por isso as invariantes
 * ficam presas no lugar em que nascem.
 */
class CardsTest {

    @Test
    fun `o baralho tem as 52 cartas, todas distintas`() {
        val baralho = standardDeck()
        assertEquals(52, baralho.size)
        assertEquals(52, baralho.distinct().size, "carta repetida no baralho")
        assertTrue(baralho.none { it.isJoker || it.isHidden }, "baralho comum não tem curinga")
    }

    @Test
    fun `cada naipe tem os treze valores`() {
        for (suit in Suit.entries) {
            val doNaipe = standardDeck().filter { it.suit == suit }
            assertEquals(13, doNaipe.size, "naipe $suit incompleto")
            assertEquals(13, doNaipe.map { it.rank }.distinct().size, "valor repetido em $suit")
        }
    }

    /**
     * Duas cartas iguais de baralhos diferentes precisam continuar sendo **duas**. Se
     * fossem o mesmo objeto, uma mão com as duas perderia uma ao virar conjunto — e a
     * canastra, que se joga com dois baralhos, ficaria devendo cartas.
     */
    @Test
    fun `o baralho duplo tem cada carta duas vezes`() {
        val duplo = deckOf(decks = 2, jokersPerDeck = 2)
        assertEquals(52 * 2 + 4, duplo.size)
        assertEquals(4, duplo.count { it.isJoker }, "dois baralhos trazem quatro curingas")
        assertEquals(2, duplo.count { it.isJoker && it.isRed }, "metade dos curingas é vermelha")

        val asDeEspadas = duplo.count { it.rank == Rank.ACE && it.suit == Suit.SPADES }
        assertEquals(2, asDeEspadas, "o ás de espadas some no baralho duplo")
    }

    @Test
    fun `embaralhar nao perde nem inventa carta`() {
        val baralho = standardDeck()
        val embaralhado = Rng.seeded(7).shuffle(baralho).value

        assertEquals(baralho.size, embaralhado.size)
        assertEquals(baralho.toSet(), embaralhado.toSet(), "o embaralhamento mudou o baralho")
        assertTrue(baralho != embaralhado, "com 52 cartas, sair na mesma ordem seria suspeito")
    }

    /** Reabrir uma partida salva precisa dar a mesma mão: é a semente que garante isso. */
    @Test
    fun `a mesma semente reparte as mesmas maos`() {
        val a = Rng.seeded(99).deal(standardDeck(), hands = 4, size = 13).value
        val b = Rng.seeded(99).deal(standardDeck(), hands = 4, size = 13).value
        assertEquals(a.hands, b.hands)
        assertEquals(a.rest, b.rest)
    }

    @Test
    fun `repartir divide o baralho sem sobreposicao`() {
        val deal = Rng.seeded(3).deal(standardDeck(), hands = 4, size = 13).value

        assertEquals(4, deal.hands.size)
        assertTrue(deal.hands.all { it.size == 13 }, "mão com tamanho errado")
        assertTrue(deal.rest.isEmpty(), "quatro mãos de treze consomem o baralho inteiro")

        val todas = deal.hands.flatten()
        assertEquals(52, todas.distinct().size, "a mesma carta caiu em duas mãos")
    }

    @Test
    fun `o que sobra da distribuicao vira monte`() {
        val deal = Rng.seeded(5).deal(standardDeck(), hands = 2, size = 9).value
        assertEquals(52 - 18, deal.rest.size)
        assertEquals(52, (deal.hands.flatten() + deal.rest).distinct().size)
    }

    @Test
    fun `mao virada guarda quantas cartas sao, e nao quais`() {
        val mao = listOf(Card(Rank.ACE, Suit.SPADES), Card(Rank.TWO, Suit.HEARTS))
        val virada = mao.hidden()

        assertEquals(mao.size, virada.size, "virar a mão não pode mudar a contagem")
        assertTrue(virada.all { it.isHidden }, "sobrou carta aberta numa mão virada")
        assertTrue(virada.none { it in mao }, "a carta de verdade vazou na mão virada")
    }

    @Test
    fun `a mao sai arrumada por naipe e por valor`() {
        val mao = listOf(
            Card(Rank.KING, Suit.HEARTS),
            Card(Rank.TWO, Suit.CLUBS),
            Card(Rank.ACE, Suit.HEARTS),
            Card(Rank.TEN, Suit.CLUBS),
        )
        assertEquals(
            listOf(
                Card(Rank.TWO, Suit.CLUBS),
                Card(Rank.TEN, Suit.CLUBS),
                Card(Rank.KING, Suit.HEARTS),
                Card(Rank.ACE, Suit.HEARTS),
            ),
            mao.sortedForHand(),
        )
    }

    @Test
    fun `o tres vermelho e o tres preto se distinguem pela cor`() {
        // A canastra trata os dois de forma oposta, e o que os separa é a cor, não o naipe.
        val vermelhos = standardDeck().filter { it.rank == Rank.THREE && it.isRed }
        val pretos = standardDeck().filter { it.rank == Rank.THREE && !it.isRed }

        assertEquals(2, vermelhos.size)
        assertEquals(2, pretos.size)
        assertEquals(setOf(Suit.DIAMONDS, Suit.HEARTS), vermelhos.map { it.suit }.toSet())
        assertEquals(setOf(Suit.CLUBS, Suit.SPADES), pretos.map { it.suit }.toSet())
    }
}
