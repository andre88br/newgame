package io.github.andre88br.newgame.core.games.poker

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A IA do pôquer não faz busca: ela estima a própria chance de vencer sorteando o resto do
 * baralho e olha se o preço do pote compensa. Não há como cravar o lance exato que ela vai
 * escolher — é amostragem, não conta fechada — mas dá para exigir duas coisas com confiança
 * alta: mão muito forte aposta em vez de passar, e mão sem nada desiste de um lance caríssimo
 * em vez de pagar. E, o que mais importa, ela nunca sugere um lance que o motor recusa.
 */
class PokerAiTest {

    private fun carta(rank: Rank, suit: Suit) = Card(rank, suit)

    private fun novo(seed: Long = 1, seats: Int = 2, buyIn: Int = 1000, bigBlind: Int = 20) =
        PokerGame.initialState(
            MatchConfig(
                seed,
                seats = seats,
                options = mapOf(POKER_OPTION_BUY_IN to buyIn.toString(), POKER_OPTION_BIG_BLIND to bigBlind.toString()),
            ),
        )

    @Test
    fun `com quadra feita e ninguem apostado, aposta em vez de passar`() {
        val base = novo(buyIn = 1000, bigBlind = 20)
        val estado = base.copy(
            hands = listOf(
                listOf(carta(Rank.NINE, Suit.CLUBS), carta(Rank.NINE, Suit.DIAMONDS)),
                listOf(carta(Rank.TWO, Suit.HEARTS), carta(Rank.SEVEN, Suit.SPADES)),
            ),
            board = listOf(carta(Rank.NINE, Suit.HEARTS), carta(Rank.NINE, Suit.SPADES), carta(Rank.KING, Suit.CLUBS)),
            street = PokerStreet.FLOP,
            streetBet = listOf(0, 0),
            toAct = listOf(true, true),
            folded = listOf(false, false),
            turn = Seat(0),
            minRaise = base.bigBlind,
        )

        val lance = PokerAi.chooseMove(estado, Difficulty.HARD, seed = 1)
        assertIs<PokerMove.Raise>(lance, "quadra de nove não passa a vez de graça")
        assertIs<MoveResult.Ok<PokerState>>(PokerGame.applyMove(estado, lance))
    }

    @Test
    fun `mao sem nada desiste de pagar caro no rio`() {
        val base = novo(buyIn = 1000, bigBlind = 20)
        // Sete e dois: a pior largada do pôquer, e o rio não ajudou nada. Só o preço já
        // bastaria: pagar quinhentos num pote de quarenta pede quase noventa e três por cento
        // de chance de vitória, e sete-dois não chega perto disso contra mão aleatória nenhuma.
        val estado = base.copy(
            hands = listOf(
                listOf(carta(Rank.SEVEN, Suit.CLUBS), carta(Rank.TWO, Suit.DIAMONDS)),
                listOf(carta(Rank.ACE, Suit.HEARTS), carta(Rank.ACE, Suit.SPADES)),
            ),
            board = listOf(
                carta(Rank.THREE, Suit.HEARTS), carta(Rank.FOUR, Suit.SPADES), carta(Rank.NINE, Suit.CLUBS),
                carta(Rank.JACK, Suit.DIAMONDS), carta(Rank.QUEEN, Suit.HEARTS),
            ),
            street = PokerStreet.RIVER,
            streetBet = listOf(0, 500),
            stacks = listOf(500, 0),
            pot = 40,
            toAct = listOf(true, false),
            folded = listOf(false, false),
            turn = Seat(0),
            minRaise = base.bigBlind,
        )

        val lance = PokerAi.chooseMove(estado, Difficulty.HARD, seed = 2)
        assertIs<PokerMove.Fold>(lance)
    }

    /**
     * Quando o único adversário já mostrou a mão (all-in revelado, ver [PokerGame.redactFor])
     * e a mesa está completa, não sobra carta nenhuma pra sortear: a equity é a comparação
     * exata das duas mãos, sempre a mesma não importa a semente. Antes da correção,
     * `estimateEquity` ignorava a mão revelada e sorteava duas cartas quaisquer para o
     * adversário a cada amostra — a mesma mão dava um número diferente (e quase nunca 1.0)
     * conforme a semente, mesmo com as duas mãos já conhecidas por inteiro.
     */
    @Test
    fun `com a mao do unico adversario ja revelada, a equity e exata e nao varia por semente`() {
        val base = novo(seats = 2, buyIn = 1000, bigBlind = 20)
        val estado = base.copy(
            // Par de ases contra sete-e-dois desacompanhados: vitória garantida no showdown,
            // sem empate possível — não há carta comum entre as duas mãos e nenhuma delas
            // combina com a mesa a ponto de gerar dúvida.
            hands = listOf(
                listOf(carta(Rank.ACE, Suit.SPADES), carta(Rank.ACE, Suit.DIAMONDS)),
                listOf(carta(Rank.SEVEN, Suit.HEARTS), carta(Rank.THREE, Suit.CLUBS)),
            ),
            board = listOf(
                carta(Rank.TWO, Suit.CLUBS), carta(Rank.FIVE, Suit.DIAMONDS), carta(Rank.NINE, Suit.SPADES),
                carta(Rank.JACK, Suit.HEARTS), carta(Rank.FOUR, Suit.SPADES),
            ),
            street = PokerStreet.RIVER,
            stacks = listOf(1000, 0),
            // A rua precisa estar fechada (ninguém apostado) para a mão all-in ser pública —
            // é a mesma condição de PokerState.allInRevealed usada por PokerGame.redactFor.
            streetBet = listOf(0, 0),
            toAct = listOf(true, false),
            folded = listOf(false, false),
            turn = Seat(0),
        )
        assertTrue(estado.allInRevealed(Seat(1)), "o teste depende da mão da cadeira 1 estar publica")

        for (semente in listOf(1L, 2L, 3L, 999L)) {
            val equity = PokerAi.estimateEquity(estado, Rng.seeded(semente), amostras = 50)
            assertEquals(1.0, equity, "semente $semente: mão revelada e mesa completa não deixam nada ao acaso")
        }
    }

    @Test
    fun `nunca sugere um lance que o motor recusaria, em qualquer dificuldade`() {
        for (dificuldade in Difficulty.entries) {
            var estado = novo(seed = 99, buyIn = 300, bigBlind = 20)
            var passos = 0
            while (!PokerGame.outcome(estado).isOver && passos < 500) {
                val lance = PokerAi.chooseMove(estado, dificuldade, seed = 1000L + passos)
                assertTrue(lance != null, "a IA não devia ficar sem lance com a partida em andamento")
                val resultado = PokerGame.applyMove(estado, lance)
                assertIs<MoveResult.Ok<PokerState>>(resultado, "lance recusado pelo motor: $lance em $dificuldade")
                estado = resultado.state
                passos++
            }
            assertTrue(PokerGame.outcome(estado).isOver, "partida não terminou em $dificuldade")
        }
    }
}
