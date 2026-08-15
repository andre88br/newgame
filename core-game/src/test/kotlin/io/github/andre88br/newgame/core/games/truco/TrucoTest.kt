package io.github.andre88br.newgame.core.games.truco

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O truco tem duas coisas que nenhum outro jogo deste app tem: manilhas fixas, que quebram a
 * ordem normal do baralho, e uma mão que **vale o que os dois lados combinarem**. Somando a
 * elas as regras de empate, que não se parecem com desempate nenhum, sai a lista do que este
 * arquivo precisa provar.
 */
class TrucoTest {

    private fun novo(seed: Long = 7, seats: Int = 2) =
        TrucoGame.initialState(MatchConfig(seed, seats = seats))

    private fun carta(rank: Rank, suit: Suit) = Card(rank, suit)

    // -------- o baralho e a ordem --------

    @Test
    fun `o baralho tem quarenta cartas, sem oito, nove e dez`() {
        val baralho = trucoDeck()
        assertEquals(40, baralho.size)
        assertEquals(40, baralho.distinct().size, "carta repetida no baralho")
        for (rank in listOf(Rank.EIGHT, Rank.NINE, Rank.TEN)) {
            assertTrue(baralho.none { it.rank == rank }, "o $rank não joga truco")
        }
        assertTrue(baralho.none { it.isJoker }, "truco não tem coringa")
    }

    @Test
    fun `a ordem comum vai do quatro ao tres`() {
        val ordem = listOf(
            Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN,
            Rank.QUEEN, Rank.JACK, Rank.KING, Rank.ACE, Rank.TWO, Rank.THREE,
        )
        // Ouros, que não tem nenhuma manilha a não ser o sete — evitado aqui de propósito.
        val comuns = ordem.map { carta(it, Suit.CLUBS) }.filterNot { isManilha(it) }
        val forcas = comuns.map { trucoStrength(it) }
        assertEquals(forcas.sorted(), forcas, "a ordem comum saiu fora de ordem: $forcas")
    }

    /**
     * As quatro manilhas são fixas e é isso que faz o truco ser mineiro. Elas ganham de
     * qualquer carta comum — inclusive do três, que é a mais forte das comuns.
     */
    @Test
    fun `as manilhas sao fixas e ganham de tudo`() {
        assertEquals(listOf(ZAP, COPAS, ESPADILHA, OURITO), MANILHAS, "a ordem das manilhas")
        assertEquals(carta(Rank.FOUR, Suit.CLUBS), ZAP)
        assertEquals(carta(Rank.SEVEN, Suit.HEARTS), COPAS)
        assertEquals(carta(Rank.ACE, Suit.SPADES), ESPADILHA)
        assertEquals(carta(Rank.SEVEN, Suit.DIAMONDS), OURITO)

        val maiorComum = trucoStrength(carta(Rank.THREE, Suit.SPADES))
        for (manilha in MANILHAS) {
            assertTrue(trucoStrength(manilha) > maiorComum, "$manilha devia ganhar do três")
        }
        assertTrue(trucoStrength(ZAP) > trucoStrength(COPAS))
        assertTrue(trucoStrength(COPAS) > trucoStrength(ESPADILHA))
        assertTrue(trucoStrength(ESPADILHA) > trucoStrength(OURITO))
    }

    /**
     * O zap é o quatro de paus, e o quatro é a carta mais fraca do baralho. Os outros três
     * quatros continuam sendo a carta mais fraca do baralho — a manilha é a **carta**, não o
     * valor.
     */
    @Test
    fun `so o quatro de paus e manilha, os outros quatros nao`() {
        assertTrue(isManilha(ZAP))
        for (naipe in listOf(Suit.HEARTS, Suit.DIAMONDS, Suit.SPADES)) {
            val quatro = carta(Rank.FOUR, naipe)
            assertTrue(!isManilha(quatro), "$quatro não é manilha")
            assertEquals(1, trucoStrength(quatro), "$quatro é a carta mais fraca")
        }
        assertTrue(!isManilha(carta(Rank.SEVEN, Suit.CLUBS)), "o sete de paus não é manilha")
        assertTrue(!isManilha(carta(Rank.ACE, Suit.CLUBS)), "o ás de paus não é manilha")
    }

    // -------- a distribuição --------

    @Test
    fun `cada um recebe tres cartas, e a mesa e de dois ou de quatro`() {
        assertEquals(listOf(2, 4), TrucoGame.seatOptions, "truco não é de três")
        for (seats in listOf(2, 4)) {
            val state = novo(seats = seats)
            assertEquals(seats, state.hands.size)
            assertTrue(state.hands.all { it.size == TRUCO_HAND_SIZE }, "mão fora do tamanho")
            assertEquals(
                seats * TRUCO_HAND_SIZE,
                state.hands.flatten().distinct().size,
                "carta repetida entre as mãos",
            )
            assertEquals(listOf(0, 0), state.scores, "a partida começa zerada")
            assertEquals(1, state.stake, "a mão nasce valendo um")
        }
    }

    @Test
    fun `a mesma semente reparte a mesma mao`() {
        assertEquals(novo(42).hands, novo(42).hands)
    }

    @Test
    fun `em quatro as duplas sao as cadeiras opostas`() {
        val state = novo(seats = 4)
        assertEquals(state.teamOf(Seat(0)), state.teamOf(Seat(2)))
        assertEquals(state.teamOf(Seat(1)), state.teamOf(Seat(3)))
        assertTrue(state.teamOf(Seat(0)) != state.teamOf(Seat(1)))
    }

    // -------- as rodadas --------

    @Test
    fun `a carta mais forte leva a rodada`() {
        val state = novo().copy(
            hands = listOf(listOf(ZAP), listOf(carta(Rank.THREE, Suit.SPADES))),
            turn = Seat.FIRST,
            leader = Seat.FIRST,
        )
        val depois = TrucoGame.applyOrThrow(state, TrucoMove.Play(ZAP))
        assertEquals(Seat(1), depois.turn, "a rodada segue para a outra cadeira")

        val fim = TrucoGame.applyOrThrow(depois, TrucoMove.Play(carta(Rank.THREE, Suit.SPADES)))
        assertEquals(listOf(0), fim.rounds, "o zap leva a rodada")
        assertEquals(Seat.FIRST, fim.leader, "quem levou abre a próxima")
        assertTrue(fim.table.isEmpty(), "a mesa se limpa entre rodadas")
    }

    @Test
    fun `cartas de mesma forca empatam a rodada`() {
        val state = novo().copy(
            hands = listOf(
                listOf(carta(Rank.KING, Suit.CLUBS), carta(Rank.FOUR, Suit.HEARTS)),
                listOf(carta(Rank.KING, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS)),
            ),
            turn = Seat.FIRST,
            leader = Seat.FIRST,
        )
        var depois = TrucoGame.applyOrThrow(state, TrucoMove.Play(carta(Rank.KING, Suit.CLUBS)))
        depois = TrucoGame.applyOrThrow(depois, TrucoMove.Play(carta(Rank.KING, Suit.HEARTS)))

        assertEquals(listOf(TRUCO_NOBODY), depois.rounds, "dois reis empatam")
        assertEquals(
            Seat.FIRST,
            depois.leader,
            "rodada empatada não tira a mão de quem já a tinha",
        )
    }

    // -------- os empates decidem a mão --------

    @Test
    fun `duas rodadas fecham a mao`() {
        assertEquals(0, TrucoGame.handWinner(listOf(0, 0)))
        assertEquals(1, TrucoGame.handWinner(listOf(1, 1)))
        assertNull(TrucoGame.handWinner(listOf(0)), "uma rodada não fecha nada")
        assertNull(TrucoGame.handWinner(listOf(0, 1)), "rodada para cada lado vai à terceira")
    }

    @Test
    fun `quem faz a primeira e empata a segunda ganha`() {
        assertEquals(0, TrucoGame.handWinner(listOf(0, TRUCO_NOBODY)))
        assertEquals(1, TrucoGame.handWinner(listOf(1, TRUCO_NOBODY)))
    }

    @Test
    fun `empatou a primeira, ganha quem fizer a segunda`() {
        assertEquals(1, TrucoGame.handWinner(listOf(TRUCO_NOBODY, 1)))
        assertEquals(0, TrucoGame.handWinner(listOf(TRUCO_NOBODY, 0)))
    }

    @Test
    fun `empatou a terceira, volta a valer quem fez a primeira`() {
        assertEquals(0, TrucoGame.handWinner(listOf(0, 1, TRUCO_NOBODY)))
        assertEquals(1, TrucoGame.handWinner(listOf(1, 0, TRUCO_NOBODY)))
    }

    @Test
    fun `empatou a primeira e a segunda, decide a terceira`() {
        assertNull(
            TrucoGame.handWinner(listOf(TRUCO_NOBODY, TRUCO_NOBODY)),
            "duas empatadas ainda não fecham: falta a terceira",
        )
        assertEquals(0, TrucoGame.handWinner(listOf(TRUCO_NOBODY, TRUCO_NOBODY, 0)))
    }

    /** Mão com as três rodadas empatadas não é de ninguém: ninguém marca. */
    @Test
    fun `tres rodadas empatadas nao dao ponto a ninguem`() {
        assertEquals(
            TRUCO_NOBODY,
            TrucoGame.handWinner(listOf(TRUCO_NOBODY, TRUCO_NOBODY, TRUCO_NOBODY)),
        )
    }

    // -------- a aposta --------

    @Test
    fun `a escada da aposta vai de um a doze`() {
        assertEquals(3, nextStake(1))
        assertEquals(6, nextStake(3))
        assertEquals(9, nextStake(6))
        assertEquals(12, nextStake(9))
        assertNull(nextStake(12), "doze é o teto")
    }

    @Test
    fun `trucar poe tres na mesa e passa a palavra`() {
        val state = novo()
        assertTrue(TrucoMove.Call in TrucoGame.legalMoves(state), "dá para trucar de saída")

        val depois = TrucoGame.applyOrThrow(state, TrucoMove.Call)
        assertTrue(depois.answering, "há truco na mesa")
        assertEquals(3, depois.pending)
        assertEquals(1, depois.stake, "enquanto não responde, a mão ainda vale um")
        assertEquals(Seat(1), depois.turn, "quem responde é o outro lado")

        // Enquanto o truco está de pé, não se joga carta.
        val carta = state.hand(Seat(1)).first()
        assertTrue(
            TrucoGame.applyMove(depois, TrucoMove.Play(carta)) is MoveResult.Illegal,
            "jogar carta antes de responder devia ser recusado",
        )
    }

    @Test
    fun `aceitar devolve a vez a quem trucou`() {
        val state = novo()
        val trucou = TrucoGame.applyOrThrow(state, TrucoMove.Call)
        val aceito = TrucoGame.applyOrThrow(trucou, TrucoMove.Accept)

        assertEquals(3, aceito.stake, "aceito, a mão passa a valer três")
        assertTrue(!aceito.answering)
        assertEquals(Seat.FIRST, aceito.turn, "quem trucou ainda tem carta para jogar")
        assertEquals(3, aceito.handSize(Seat.FIRST), "trucar não gasta carta")
    }

    /** Quem trucou e foi aceito passou a palavra: só o outro lado aumenta. */
    @Test
    fun `nao da para aumentar o proprio truco`() {
        val aceito = TrucoGame.applyOrThrow(
            TrucoGame.applyOrThrow(novo(), TrucoMove.Call),
            TrucoMove.Accept,
        )
        assertTrue(
            TrucoMove.Call !in TrucoGame.legalMoves(aceito),
            "quem trucou não pode trucar de novo",
        )
        assertTrue(TrucoGame.applyMove(aceito, TrucoMove.Call) is MoveResult.Illegal)

        // Depois de o outro lado jogar, a palavra é dele — e ele pode pedir seis.
        val jogou = TrucoGame.applyOrThrow(aceito, TrucoMove.Play(aceito.hand(Seat.FIRST).first()))
        assertTrue(TrucoMove.Call in TrucoGame.legalMoves(jogou), "o outro lado pode pedir seis")
    }

    @Test
    fun `correr entrega o valor de antes do pedido`() {
        val trucou = TrucoGame.applyOrThrow(novo(), TrucoMove.Call)
        val correu = TrucoGame.applyOrThrow(trucou, TrucoMove.Run)
        assertEquals(1, correu.score(0), "correr de um truco custa um ponto")
        assertEquals(0, correu.score(1))
    }

    /**
     * Aumentar aceita o valor anterior de passagem. É o que faz correr de um seis custar
     * três, e não um: o três já estava de pé quando o seis foi pedido.
     */
    @Test
    fun `aumentar aceita o valor anterior, e correr do seis custa tres`() {
        val trucou = TrucoGame.applyOrThrow(novo(), TrucoMove.Call)
        val pediuSeis = TrucoGame.applyOrThrow(trucou, TrucoMove.Call)

        assertEquals(3, pediuSeis.stake, "o três ficou combinado ao se pedir seis")
        assertEquals(6, pediuSeis.pending)
        assertEquals(Seat.FIRST, pediuSeis.turn, "volta a palavra para quem trucou")

        val correu = TrucoGame.applyOrThrow(pediuSeis, TrucoMove.Run)
        assertEquals(3, correu.score(1), "quem pediu seis leva os três")
        assertEquals(0, correu.score(0))
    }

    @Test
    fun `a escada sobe ate doze e para`() {
        var state = novo()
        for (esperado in listOf(3, 6, 9, 12)) {
            state = TrucoGame.applyOrThrow(state, TrucoMove.Call)
            assertEquals(esperado, state.pending, "degrau errado da escada")
        }
        assertTrue(
            TrucoMove.Call !in TrucoGame.legalMoves(state),
            "de doze não se sobe mais",
        )
        assertTrue(TrucoGame.applyMove(state, TrucoMove.Call) is MoveResult.Illegal)

        val aceito = TrucoGame.applyOrThrow(state, TrucoMove.Accept)
        assertEquals(12, aceito.stake, "a mão inteira vale a partida")
        assertTrue(TrucoMove.Call !in TrucoGame.legalMoves(aceito))
    }

    @Test
    fun `responder sem truco na mesa e recusado`() {
        val state = novo()
        assertTrue(TrucoGame.applyMove(state, TrucoMove.Accept) is MoveResult.Illegal)
        assertTrue(TrucoGame.applyMove(state, TrucoMove.Run) is MoveResult.Illegal)
    }

    @Test
    fun `carta que nao esta na mao e recusada`() {
        val state = novo()
        val fora = trucoDeck().first { it !in state.hand(Seat.FIRST) }
        assertTrue(TrucoGame.applyMove(state, TrucoMove.Play(fora)) is MoveResult.Illegal)
    }

    // -------- o placar --------

    @Test
    fun `a mao ganha vale o combinado, e a partida vai a doze`() {
        val quaseLa = novo().copy(scores = listOf(11, 0))
        val trucou = TrucoGame.applyOrThrow(quaseLa, TrucoMove.Call)
        val correu = TrucoGame.applyOrThrow(trucou, TrucoMove.Run)

        assertEquals(12, correu.score(0))
        assertEquals(Outcome.Win(Seat(0)), TrucoGame.outcome(correu), "doze fecha a partida")
        assertTrue(TrucoGame.legalMoves(correu).isEmpty(), "partida acabada não tem lance")
    }

    @Test
    fun `uma partida inteira termina sem travar`() {
        for (seats in listOf(2, 4)) {
            var state = TrucoGame.initialState(MatchConfig(seed = 2026, seats = seats))
            var guard = 0
            while (!TrucoGame.outcome(state).isOver && guard++ < 5_000) {
                val legais = TrucoGame.legalMoves(state)
                assertTrue(legais.isNotEmpty(), "mesa de $seats travou: $state")
                state = TrucoGame.applyOrThrow(state, legais.first())
            }
            assertTrue(
                TrucoGame.outcome(state).isOver,
                "mesa de $seats não terminou (placar ${state.scores})",
            )
            assertTrue(state.scores.max() >= TRUCO_TARGET)
        }
    }

    /**
     * A mão nunca passa de três rodadas, e o número de cartas na mesa nunca passa do número
     * de cadeiras. As duas coisas juntas são o que garante que a partida anda.
     */
    @Test
    fun `nenhuma mao passa de tres rodadas`() {
        var state = novo(seed = 31, seats = 4)
        var guard = 0
        while (!TrucoGame.outcome(state).isOver && guard++ < 3_000) {
            assertTrue(state.rounds.size < 3, "rodada demais: ${state.rounds}")
            assertTrue(state.table.size < state.seats, "carta demais na mesa")
            assertTrue(state.stake in listOf(1, 3, 6, 9, 12), "valor estranho: ${state.stake}")
            state = TrucoGame.applyOrThrow(state, TrucoGame.legalMoves(state).first())
        }
    }

    // -------- informação oculta --------

    @Test
    fun `a mao dos outros nao vaza, nem a do parceiro`() {
        val state = novo(seats = 4)
        val visto = TrucoGame.redactFor(state, Seat.FIRST)

        assertEquals(state.hand(Seat.FIRST), visto.hand(Seat.FIRST), "a própria mão fica")
        for (index in 1 until 4) {
            assertTrue(visto.hand(Seat(index)).all { it.isHidden }, "vazou a mão da cadeira $index")
            assertEquals(state.handSize(Seat(index)), visto.handSize(Seat(index)))
        }
        // O parceiro é a cadeira 2, e ele também fica escondido: metade do truco de dupla é
        // não saber o que o parceiro tem.
        assertTrue(visto.hand(Seat(2)).all { it.isHidden })
        assertEquals(state.scores, visto.scores, "o placar é público")
    }

    @Test
    fun `com a mao virada nao ha lance de carta`() {
        val visto = TrucoGame.redactFor(novo(), Seat(1))
        assertTrue(
            TrucoGame.legalMoves(visto).none { it is TrucoMove.Play },
            "quem não vê a mão não escolhe carta por ela",
        )
    }

    // -------- a máquina --------

    @Test
    fun `a maquina joga so com o que enxerga, e sempre lance legal`() {
        for (nivel in Difficulty.entries) {
            var state = novo(seed = 11, seats = 4)
            var guard = 0
            while (!TrucoGame.outcome(state).isOver && guard++ < 600) {
                val comoEleVe = TrucoGame.redactFor(state, state.turn)
                val escolhido = TrucoAi.chooseMove(comoEleVe, nivel, seed = guard.toLong())
                assertTrue(escolhido != null, "a IA não escolheu lance no ply ${state.ply}")
                assertTrue(
                    TrucoGame.applyMove(state, escolhido) is MoveResult.Ok,
                    "a IA escolheu lance ilegal no $nivel: ${escolhido.describe()}",
                )
                state = TrucoGame.applyOrThrow(state, escolhido)
            }
        }
    }

    /**
     * O nível difícil precisa ganhar do fácil.
     *
     * É o único teste aqui que mede jogo em vez de regra, e ele existe porque a IA do truco
     * é feita de limiares escolhidos a dedo: sem uma prova de que a escolha rende, "difícil"
     * e "fácil" seriam só dois nomes para o mesmo adversário. A margem exigida é folgada de
     * propósito — o truco tem carta, e carta é sorte.
     */
    @Test
    fun `o nivel dificil ganha do facil na maioria das partidas`() {
        var dificil = 0
        var facil = 0
        for (semente in 1L..200L) {
            var state = novo(seed = semente)
            var guard = 0
            while (!TrucoGame.outcome(state).isOver && guard++ < 3_000) {
                val nivel = if (state.turn.index == 0) Difficulty.HARD else Difficulty.EASY
                val visto = TrucoGame.redactFor(state, state.turn)
                val lance = TrucoAi.chooseMove(visto, nivel, seed = semente * 1_000 + guard) ?: break
                state = TrucoGame.applyOrThrow(state, lance)
            }
            val fim = TrucoGame.outcome(state)
            assertTrue(fim is Outcome.Win, "a partida $semente não terminou")
            if (fim.seat.index == 0) dificil++ else facil++
        }
        assertTrue(
            // Duzentas partidas medidas dão 120 a 80 — sessenta por cento. A barra fica
            // abaixo disso para não quebrar ao primeiro ajuste de limiar, e bem acima do
            // meio a meio que denunciaria dois níveis com o mesmo jogo.
            dificil >= facil * 5 / 4,
            "o difícil devia ganhar com folga do fácil: $dificil a $facil",
        )
    }

    /** Com o zap na mão, matar um quatro com ele seria ganhar a rodada e perder a mão. */
    @Test
    fun `a maquina mata com a menor carta que ganha`() {
        val quatro = carta(Rank.FOUR, Suit.HEARTS)
        val state = novo().copy(
            hands = listOf(
                listOf(quatro),
                listOf(ZAP, carta(Rank.FIVE, Suit.CLUBS), carta(Rank.KING, Suit.SPADES)),
            ),
            table = listOf(OnTable(0, quatro)),
            turn = Seat(1),
            leader = Seat.FIRST,
            // Sem espaço para trucar: o que se testa aqui é a escolha da carta.
            stake = 12,
        )
        val escolhido = TrucoAi.chooseMove(
            TrucoGame.redactFor(state, Seat(1)),
            Difficulty.HARD,
            seed = 5,
        )
        assertEquals(
            TrucoMove.Play(carta(Rank.FIVE, Suit.CLUBS)),
            escolhido,
            "o cinco já ganha do quatro: o zap fica para depois",
        )
    }

    /** Com a rodada já ganha pelo parceiro, cobrir a carta dele é jogar contra si mesmo. */
    @Test
    fun `a maquina nao cobre a carta do proprio parceiro`() {
        val state = novo(seats = 4).copy(
            hands = listOf(
                emptyList(),
                listOf(carta(Rank.FOUR, Suit.SPADES)),
                listOf(ZAP, carta(Rank.FOUR, Suit.DIAMONDS)),
                emptyList(),
            ),
            table = listOf(
                OnTable(0, carta(Rank.THREE, Suit.CLUBS)),
                OnTable(1, carta(Rank.FOUR, Suit.SPADES)),
            ),
            turn = Seat(2),
            leader = Seat.FIRST,
            stake = 12,
        )
        val escolhido = TrucoAi.chooseMove(
            TrucoGame.redactFor(state, Seat(2)),
            Difficulty.HARD,
            seed = 5,
        )
        assertEquals(
            TrucoMove.Play(carta(Rank.FOUR, Suit.DIAMONDS)),
            escolhido,
            "o parceiro já está ganhando: joga-se a mais fraca",
        )
    }

    /** Mão de manilha não corre de um truco. */
    @Test
    fun `a maquina aguenta o truco com mao forte e corre com mao fraca`() {
        val forte = novo().copy(
            hands = listOf(emptyList(), listOf(ZAP, COPAS, ESPADILHA)),
            pending = 3,
            bettor = 0,
            turn = Seat(1),
            resume = Seat.FIRST,
        )
        val fraca = forte.copy(
            hands = listOf(
                emptyList(),
                listOf(
                    carta(Rank.FOUR, Suit.HEARTS),
                    carta(Rank.FIVE, Suit.SPADES),
                    carta(Rank.SIX, Suit.DIAMONDS),
                ),
            ),
        )

        val comForte = TrucoAi.chooseMove(
            TrucoGame.redactFor(forte, Seat(1)),
            Difficulty.HARD,
            seed = 3,
        )
        assertTrue(
            comForte == TrucoMove.Accept || comForte == TrucoMove.Call,
            "com três manilhas não se corre: veio $comForte",
        )

        // A teimosia do nível difícil é sorteada; no médio a decisão é limpa.
        val comFraca = TrucoAi.chooseMove(
            TrucoGame.redactFor(fraca, Seat(1)),
            Difficulty.MEDIUM,
            seed = 3,
        )
        assertEquals(TrucoMove.Run, comFraca, "quatro, cinco e seis é mão de correr")
    }

    /**
     * Correr quando o adversário fecha a partida com o que já está na mesa é entregar o
     * jogo por medo. Aguentar não pode custar mais caro, e ainda pode ganhar.
     */
    @Test
    fun `a maquina nao corre quando correr ja perde a partida`() {
        val state = novo().copy(
            scores = listOf(11, 0),
            hands = listOf(
                emptyList(),
                listOf(
                    carta(Rank.FOUR, Suit.HEARTS),
                    carta(Rank.FIVE, Suit.SPADES),
                    carta(Rank.SIX, Suit.DIAMONDS),
                ),
            ),
            stake = 1,
            pending = 3,
            bettor = 0,
            turn = Seat(1),
            resume = Seat.FIRST,
        )
        val escolhido = TrucoAi.chooseMove(
            TrucoGame.redactFor(state, Seat(1)),
            Difficulty.MEDIUM,
            seed = 3,
        )
        assertTrue(escolhido != TrucoMove.Run, "correr aqui entrega a partida: veio $escolhido")
    }
}
