package io.github.andre88br.newgame.core.games.klondike

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.cards.standardDeck
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A paciência é o único jogo do app sem adversário, e por isso o único em que um defeito de
 * regra não aparece por alguém reclamar: não há quem reclame. Ou o motor recusa um lance que
 * vale — e a mesa trava sem motivo —, ou aceita um que não vale, e o jogo deixa de ser jogo.
 */
class KlondikeTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)

    private fun nova(seed: Long = 7) = KlondikeGame.initialState(MatchConfig(seed, seats = 1))

    /** Uma mesa montada à mão, para testar regra sem depender do embaralhamento. */
    private fun mesa(
        downs: List<List<Card>> = List(KLONDIKE_PILES) { emptyList() },
        ups: List<List<Card>> = List(KLONDIKE_PILES) { emptyList() },
        foundations: List<List<Card>> = List(KLONDIKE_FOUNDATIONS) { emptyList() },
        stock: List<Card> = emptyList(),
        waste: List<Card> = emptyList(),
    ) = KlondikeState(downs, ups, foundations, stock, waste)

    // -------- a distribuição --------

    @Test
    fun `a mesa abre com as sete colunas e o resto no monte`() {
        val state = nova()

        assertEquals(KLONDIKE_PILES, state.ups.size)
        for (coluna in 0 until KLONDIKE_PILES) {
            assertEquals(coluna, state.downs[coluna].size, "coluna $coluna com viradas demais")
            assertEquals(1, state.ups[coluna].size, "coluna $coluna devia abrir com uma carta")
        }

        // 1+2+…+7 = 28 na mesa, 24 no monte.
        assertEquals(24, state.stock.size)
        assertTrue(state.waste.isEmpty())
        assertEquals(0, state.placed)
    }

    @Test
    fun `as 52 cartas do baralho estao na mesa, sem falta nem sobra`() {
        val state = nova(seed = 31)
        val todas = state.downs.flatten() + state.ups.flatten() + state.stock + state.waste
        assertEquals(52, todas.size)
        assertEquals(standardDeck().toSet(), todas.toSet())
    }

    @Test
    fun `mesas de sementes diferentes sao diferentes, e a mesma semente repete`() {
        assertEquals(nova(seed = 4), nova(seed = 4), "a mesma semente tem de dar a mesma mesa")
        assertTrue(nova(seed = 4) != nova(seed = 5))
    }

    // -------- a regra da coluna --------

    @Test
    fun `na coluna a carta desce uma e troca de cor`() {
        assertTrue(stacksOnTableau(c(Rank.NINE, Suit.HEARTS), c(Rank.TEN, Suit.SPADES)))
        assertTrue(stacksOnTableau(c(Rank.NINE, Suit.SPADES), c(Rank.TEN, Suit.DIAMONDS)))
        assertTrue(
            !stacksOnTableau(c(Rank.NINE, Suit.HEARTS), c(Rank.TEN, Suit.DIAMONDS)),
            "duas vermelhas não empilham",
        )
        assertTrue(
            !stacksOnTableau(c(Rank.EIGHT, Suit.HEARTS), c(Rank.TEN, Suit.SPADES)),
            "tem de ser uma abaixo, não duas",
        )
    }

    /** O ás é a menor carta aqui, embora no baralho compartilhado ele valha 14. */
    @Test
    fun `o as e a menor carta da paciencia`() {
        assertEquals(1, Rank.ACE.klondikeOrder)
        assertEquals(13, Rank.KING.klondikeOrder)
        assertTrue(stacksOnTableau(c(Rank.ACE, Suit.HEARTS), c(Rank.TWO, Suit.SPADES)))
        assertTrue(
            !stacksOnTableau(c(Rank.KING, Suit.HEARTS), c(Rank.ACE, Suit.SPADES)),
            "o ás não segura rei nenhum",
        )
    }

    @Test
    fun `coluna vazia so recebe rei`() {
        // A carta virada embaixo é o que faz o lance valer a pena — e o que o torna legal:
        // mudar uma coluna inteira sem nada embaixo para outra vazia não é lance.
        val comViradaEmbaixo = List(KLONDIKE_PILES) { if (it == 0) listOf(c(Rank.TWO, Suit.CLUBS)) else emptyList() }

        val comRei = KlondikeGame.applyMove(
            mesa(
                downs = comViradaEmbaixo,
                ups = List(KLONDIKE_PILES) { if (it == 0) listOf(c(Rank.KING, Suit.SPADES)) else emptyList() },
            ),
            KlondikeMove.PileToPile(from = 0, count = 1, to = 1),
        )
        assertTrue(comRei is MoveResult.Ok)

        val comDama = KlondikeGame.applyMove(
            mesa(
                downs = comViradaEmbaixo,
                ups = List(KLONDIKE_PILES) { if (it == 0) listOf(c(Rank.QUEEN, Suit.SPADES)) else emptyList() },
            ),
            KlondikeMove.PileToPile(from = 0, count = 1, to = 1),
        )
        assertTrue(comDama is MoveResult.Illegal, "dama não entra em coluna vazia")
    }

    /**
     * Trocar uma coluna inteira de lugar não é lance: só muda o buraco de coluna. Se fosse
     * legal, a dica ficaria empurrando o mesmo rei de um lado para o outro para sempre.
     */
    @Test
    fun `mudar uma coluna inteira para outra vazia nao e lance`() {
        val state = mesa(ups = List(KLONDIKE_PILES) { if (it == 0) listOf(c(Rank.KING, Suit.SPADES)) else emptyList() })
        val lances = KlondikeGame.legalMoves(state).filterIsInstance<KlondikeMove.PileToPile>()
        assertTrue(lances.isEmpty(), "coluna sem carta virada não muda de lugar: $lances")
    }

    @Test
    fun `bloco de varias cartas se move junto`() {
        val state = mesa(
            ups = List(KLONDIKE_PILES) {
                when (it) {
                    0 -> listOf(c(Rank.KING, Suit.CLUBS), c(Rank.QUEEN, Suit.HEARTS), c(Rank.JACK, Suit.SPADES))
                    1 -> listOf(c(Rank.KING, Suit.SPADES))
                    else -> emptyList()
                }
            },
            downs = List(KLONDIKE_PILES) { if (it == 0) listOf(c(Rank.TWO, Suit.CLUBS)) else emptyList() },
        )

        val depois = KlondikeGame.applyOrThrow(state, KlondikeMove.PileToPile(from = 0, count = 2, to = 1))
        assertEquals(
            listOf(c(Rank.KING, Suit.SPADES), c(Rank.QUEEN, Suit.HEARTS), c(Rank.JACK, Suit.SPADES)),
            depois.ups[1],
            "a dama e o valete foram juntos, na ordem",
        )
        assertEquals(listOf(c(Rank.KING, Suit.CLUBS)), depois.ups[0])
    }

    // -------- a regra da casa --------

    @Test
    fun `a casa sobe do as ao rei, no mesmo naipe`() {
        assertTrue(stacksOnFoundation(c(Rank.ACE, Suit.HEARTS), emptyList()))
        assertTrue(!stacksOnFoundation(c(Rank.TWO, Suit.HEARTS), emptyList()), "a casa abre com ás")
        assertTrue(stacksOnFoundation(c(Rank.TWO, Suit.HEARTS), listOf(c(Rank.ACE, Suit.HEARTS))))
        assertTrue(
            !stacksOnFoundation(c(Rank.TWO, Suit.SPADES), listOf(c(Rank.ACE, Suit.HEARTS))),
            "a casa é de um naipe só",
        )
    }

    @Test
    fun `a carta que sobe para a casa sai da coluna e desvira a de baixo`() {
        val state = mesa(
            downs = List(KLONDIKE_PILES) { if (it == 3) listOf(c(Rank.NINE, Suit.CLUBS)) else emptyList() },
            ups = List(KLONDIKE_PILES) { if (it == 3) listOf(c(Rank.ACE, Suit.SPADES)) else emptyList() },
        )

        val depois = KlondikeGame.applyOrThrow(state, KlondikeMove.PileToFoundation(pile = 3))
        assertEquals(listOf(c(Rank.ACE, Suit.SPADES)), depois.foundationOf(Suit.SPADES))
        assertEquals(listOf(c(Rank.NINE, Suit.CLUBS)), depois.ups[3], "a de baixo desvirou sozinha")
        assertTrue(depois.downs[3].isEmpty())
    }

    /** Descer da casa é permitido: às vezes é o único jeito de destravar a mesa. */
    @Test
    fun `da casa a carta pode voltar para a coluna`() {
        val state = mesa(
            ups = List(KLONDIKE_PILES) { if (it == 0) listOf(c(Rank.THREE, Suit.HEARTS)) else emptyList() },
            foundations = List(KLONDIKE_FOUNDATIONS) {
                if (it == Suit.SPADES.ordinal) listOf(c(Rank.ACE, Suit.SPADES), c(Rank.TWO, Suit.SPADES)) else emptyList()
            },
        )

        val depois = KlondikeGame.applyOrThrow(state, KlondikeMove.FoundationToPile(Suit.SPADES, pile = 0))
        assertEquals(listOf(c(Rank.ACE, Suit.SPADES)), depois.foundationOf(Suit.SPADES))
        assertEquals(
            listOf(c(Rank.THREE, Suit.HEARTS), c(Rank.TWO, Suit.SPADES)),
            depois.ups[0],
        )
    }

    // -------- monte e descarte --------

    @Test
    fun `comprar tira do monte e poe no descarte`() {
        val state = nova()
        val proxima = state.stock.last()
        val depois = KlondikeGame.applyOrThrow(state, KlondikeMove.Draw)

        assertEquals(state.stock.size - 1, depois.stock.size)
        assertEquals(proxima, depois.wasteTop)
    }

    @Test
    fun `o descarte volta ao monte na mesma ordem`() {
        var state = nova()
        val ordemComprada = mutableListOf<Card>()
        repeat(state.stock.size) {
            state = KlondikeGame.applyOrThrow(state, KlondikeMove.Draw)
            ordemComprada += state.wasteTop!!
        }
        assertTrue(state.stock.isEmpty())

        val depois = KlondikeGame.applyOrThrow(state, KlondikeMove.Recycle)
        assertTrue(depois.waste.isEmpty())
        assertEquals(1, depois.redeals)

        // Comprar de novo devolve as cartas na mesma ordem da primeira volta.
        var girando = depois
        val segundaVolta = mutableListOf<Card>()
        repeat(girando.stock.size) {
            girando = KlondikeGame.applyOrThrow(girando, KlondikeMove.Draw)
            segundaVolta += girando.wasteTop!!
        }
        assertEquals(ordemComprada, segundaVolta)
    }

    @Test
    fun `virar o descarte com monte cheio e recusado`() {
        val state = nova()
        assertTrue(KlondikeGame.applyMove(state, KlondikeMove.Recycle) is MoveResult.Illegal)
    }

    // -------- fim de jogo --------

    @Test
    fun `com as 52 cartas nas casas a partida esta ganha`() {
        val cheias = Suit.entries.map { naipe ->
            listOf(Rank.ACE).plus(Rank.entries.filter { it.klondikeOrder in 2..13 }.sortedBy { it.klondikeOrder })
                .map { c(it, naipe) }
        }
        val state = mesa(foundations = cheias)

        assertEquals(52, state.placed)
        assertEquals(Outcome.Win(Seat.FIRST), KlondikeGame.outcome(state))
        assertTrue(KlondikeGame.legalMoves(state).isEmpty())
    }

    /**
     * Empacar não é o mesmo que não ter lance: com carta no monte sempre dá para comprar.
     * A mão morre quando nada da mesa se move e nada do que ainda vai passar pelo descarte
     * tem onde pousar — girar o monte de novo daria exatamente no mesmo.
     */
    @Test
    fun `mesa sem lance util termina empacada`() {
        // Duas colunas de uma carta cada, nada empilha, e o monte tem uma carta que também
        // não serve para nada.
        val state = mesa(
            ups = List(KLONDIKE_PILES) {
                when (it) {
                    0 -> listOf(c(Rank.FIVE, Suit.HEARTS))
                    1 -> listOf(c(Rank.SEVEN, Suit.HEARTS))
                    else -> listOf(c(Rank.NINE, Suit.HEARTS))
                }
            },
            downs = List(KLONDIKE_PILES) { listOf(c(Rank.KING, Suit.CLUBS)) },
            stock = listOf(c(Rank.NINE, Suit.DIAMONDS)),
        )

        assertTrue(KlondikeGame.outcome(state) is Outcome.Draw, "nada aqui se move")
        assertTrue(KlondikeGame.legalMoves(state).isEmpty())
    }

    @Test
    fun `mesa com um lance util ainda nao acabou`() {
        val state = mesa(
            ups = List(KLONDIKE_PILES) {
                when (it) {
                    0 -> listOf(c(Rank.FIVE, Suit.HEARTS))
                    else -> listOf(c(Rank.NINE, Suit.HEARTS))
                }
            },
            downs = List(KLONDIKE_PILES) { listOf(c(Rank.KING, Suit.CLUBS)) },
            // O quatro de paus encosta no cinco de copas.
            stock = listOf(c(Rank.FOUR, Suit.CLUBS)),
        )
        assertTrue(!KlondikeGame.outcome(state).isOver)
    }

    // -------- informação oculta --------

    @Test
    fun `a redacao esconde o monte e o fundo das colunas, e mantem a contagem`() {
        val state = nova(seed = 12)
        val visto = KlondikeGame.redactFor(state, Seat.FIRST)

        assertTrue(visto.stock.all { it.isHidden }, "o monte não se lê")
        assertEquals(state.stock.size, visto.stock.size, "mas se conta")
        for (coluna in 0 until KLONDIKE_PILES) {
            assertTrue(visto.downs[coluna].all { it.isHidden })
            assertEquals(state.downs[coluna].size, visto.downs[coluna].size)
            assertEquals(state.ups[coluna], visto.ups[coluna], "o que está para cima continua à vista")
        }
    }

    /** Com o monte virado para baixo, a dica não tem como decretar que a mão acabou. */
    @Test
    fun `a visao redigida nao declara mesa empacada`() {
        val state = mesa(
            ups = List(KLONDIKE_PILES) { listOf(c(Rank.NINE, Suit.HEARTS)) },
            downs = List(KLONDIKE_PILES) { listOf(c(Rank.KING, Suit.CLUBS)) },
            stock = listOf(c(Rank.NINE, Suit.DIAMONDS)),
        )
        assertTrue(KlondikeGame.outcome(state) is Outcome.Draw)

        val visto = KlondikeGame.redactFor(state, Seat.FIRST)
        assertTrue(!KlondikeGame.outcome(visto).isOver, "de fora do monte não se afirma isso")
    }

    // -------- a dica --------

    @Test
    fun `a dica prefere desvirar carta a subir para a casa`() {
        val state = mesa(
            // A coluna 0 tem um ás em cima de uma carta virada: subir o ás desvira.
            downs = List(KLONDIKE_PILES) { if (it == 0) listOf(c(Rank.SEVEN, Suit.CLUBS)) else emptyList() },
            ups = List(KLONDIKE_PILES) {
                when (it) {
                    0 -> listOf(c(Rank.ACE, Suit.SPADES))
                    else -> emptyList()
                }
            },
        )
        assertEquals(
            KlondikeMove.PileToFoundation(pile = 0),
            KlondikeAi.chooseMove(state, Difficulty.MEDIUM, seed = 1),
        )
    }

    @Test
    fun `a dica nao fica remexendo colunas sem desvirar nada`() {
        val state = mesa(
            ups = List(KLONDIKE_PILES) {
                when (it) {
                    0 -> listOf(c(Rank.KING, Suit.CLUBS), c(Rank.QUEEN, Suit.DIAMONDS))
                    1 -> listOf(c(Rank.KING, Suit.SPADES))
                    else -> emptyList()
                }
            },
            stock = listOf(c(Rank.THREE, Suit.HEARTS)),
        )
        // A dama de ouros cabe nos dois reis pretos, e trocá-la de um para o outro não
        // desvira nada nem abre coluna: é rearrumar a mesa.
        assertTrue(
            KlondikeMove.PileToPile(from = 0, count = 1, to = 1) in KlondikeGame.legalMoves(state),
        )
        assertEquals(
            KlondikeMove.Draw,
            KlondikeAi.chooseMove(state, Difficulty.MEDIUM, seed = 1),
            "mover a dama de um rei para o outro não leva a nada; comprar leva",
        )
    }

    @Test
    fun `a dica sempre escolhe lance legal, mesa apos mesa`() {
        for (seed in 1L..12L) {
            var state = nova(seed)
            var guarda = 0
            while (!KlondikeGame.outcome(state).isOver && guarda++ < 400) {
                val visto = KlondikeGame.redactFor(state, Seat.FIRST)
                val dica = KlondikeAi.chooseMove(visto, Difficulty.MEDIUM, seed) ?: break
                assertTrue(
                    dica in KlondikeGame.legalMoves(state),
                    "a dica sugeriu $dica, que não é lance legal na mesa $seed",
                )
                state = KlondikeGame.applyOrThrow(state, dica)
            }
        }
    }

    // -------- a partida inteira --------

    @Test
    fun `nenhuma carta se perde ao longo da partida`() {
        for (seed in 1L..6L) {
            var state = nova(seed)
            var guarda = 0
            while (!KlondikeGame.outcome(state).isOver && guarda++ < 300) {
                val todas = state.downs.flatten() + state.ups.flatten() +
                    state.foundations.flatten() + state.stock + state.waste
                assertEquals(52, todas.size, "sumiu ou sobrou carta na mesa $seed, lance ${state.ply}")
                assertEquals(standardDeck().toSet(), todas.toSet())
                state = KlondikeGame.applyOrThrow(state, KlondikeGame.legalMoves(state).first())
            }
        }
    }

    /**
     * A parte virada para cima de uma coluna é sempre uma sequência descendente de cores
     * trocadas. É essa invariante que deixa mover um bloco sem conferir o bloco — se ela
     * quebrar, o jogo passa a aceitar pilhas que não existem na mesa de verdade.
     */
    @Test
    fun `a parte virada para cima e sempre uma sequencia valida`() {
        for (seed in 1L..6L) {
            var state = nova(seed)
            var guarda = 0
            while (!KlondikeGame.outcome(state).isOver && guarda++ < 300) {
                for (coluna in state.ups) {
                    for (i in 1 until coluna.size) {
                        assertTrue(
                            stacksOnTableau(coluna[i], coluna[i - 1]),
                            "coluna fora de ordem na mesa $seed: $coluna",
                        )
                    }
                }
                val dica = KlondikeAi.chooseMove(KlondikeGame.redactFor(state, Seat.FIRST), Difficulty.MEDIUM, seed)
                state = KlondikeGame.applyOrThrow(state, dica ?: break)
            }
        }
    }
}
