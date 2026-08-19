package io.github.andre88br.newgame.core.games.poker

import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * O pôquer é o primeiro jogo deste app em que a mão **vale fichas de verdade** através de
 * várias rodadas de aposta — copas e pife têm pontuação, o truco tem uma mão que muda de
 * valor, mas nenhum tem quatro ruas de decisão (pré-flop, flop, turn, river) nem a
 * possibilidade de zerar e sair do torneio. Por isso a maior parte deste arquivo persegue
 * duas coisas que, erradas, travariam ou vazariam fichas silenciosamente: a rodada de aposta
 * sempre fecha e passa a vez para alguém que ainda pode decidir algo, e o total de fichas na
 * mesa nunca muda — só troca de mão.
 */
class PokerTest {

    private fun novo(seed: Long = 1, seats: Int = 2, buyIn: Int = 1000, bigBlind: Int = 20) =
        PokerGame.initialState(
            MatchConfig(
                seed,
                seats = seats,
                options = mapOf(POKER_OPTION_BUY_IN to buyIn.toString(), POKER_OPTION_BIG_BLIND to bigBlind.toString()),
            ),
        )

    @Test
    fun `duas a quatro cadeiras, sem faixa quebrada`() {
        assertEquals(2..4, PokerGame.supportedSeats)
        assertEquals(listOf(2, 3, 4), PokerGame.seatOptions)
    }

    // -------- reparte e blinds --------

    @Test
    fun `reparte duas cartas para cada um e posta os blinds`() {
        val estado = novo(buyIn = 1000, bigBlind = 20)
        assertEquals(2, estado.hand(Seat(0)).size)
        assertEquals(2, estado.hand(Seat(1)).size)
        assertEquals(0, estado.board.size)

        // A dois, o botão é quem posta o small blind — e é ele quem age primeiro no pré-flop.
        assertEquals(Seat(1), estado.button)
        assertEquals(10, estado.streetBet[1], "small blind")
        assertEquals(20, estado.streetBet[0], "big blind")
        assertEquals(990, estado.stack(Seat(1)))
        assertEquals(980, estado.stack(Seat(0)))
        assertEquals(30, estado.pot)
        assertEquals(Seat(1), estado.turn)
    }

    @Test
    fun `passar e igualar fecham o pre-flop e abrem o flop com tres cartas`() {
        var estado = novo(buyIn = 1000, bigBlind = 20)
        // O botão (small blind) só precisa igualar os vinte já apostados pelo big blind.
        estado = PokerGame.applyOrThrow(estado, PokerMove.Call)
        assertEquals(20, estado.streetBet[1])
        assertEquals(Seat(0), estado.turn, "depois de igualar, a vez volta para o big blind")

        assertTrue(PokerMove.Check in PokerGame.legalMoves(estado), "ninguém deve nada: o big blind pode passar")
        estado = PokerGame.applyOrThrow(estado, PokerMove.Check)

        assertEquals(PokerStreet.FLOP, estado.street)
        assertEquals(3, estado.board.size)
        assertTrue(estado.streetBet.all { it == 0 }, "a rua nova começa sem aposta")
        // Fora do pré-flop, quem fala primeiro é sempre o próximo depois do botão — no jogo a
        // dois isso é o outro jogador, não o próprio botão.
        assertEquals(Seat(0), estado.turn)
    }

    @Test
    fun `aumento abaixo do minimo e recusado`() {
        val estado = novo(buyIn = 1000, bigBlind = 20)
        val resultado = PokerGame.applyMove(estado, PokerMove.Raise(to = 25))
        assertIs<MoveResult.Illegal>(resultado)
    }

    @Test
    fun `aumento maior que a pilha e recusado`() {
        val estado = novo(buyIn = 1000, bigBlind = 20)
        val resultado = PokerGame.applyMove(estado, PokerMove.Raise(to = 999_999))
        assertIs<MoveResult.Illegal>(resultado)
    }

    // -------- desistência, pote e rotação do botão --------

    @Test
    fun `desistir com um so restando fecha a mao sem showdown, e o botao roda para a mao seguinte`() {
        val primeira = novo(buyIn = 1000, bigBlind = 20)
        val totalDeFichas = primeira.stacks.sum() + primeira.pot
        assertEquals(Seat(1), primeira.button)

        // A mão seguinte já é repartida (e seus blinds, postados) na mesma resposta — por
        // isso a conta certa não é "o vencedor ficou com o pote", e sim "nada se perdeu": o
        // pote da mão que fechou virou fichas de alguém, e o resto é o blind da mão nova.
        val depoisDaDesistencia = PokerGame.applyOrThrow(primeira, PokerMove.Fold)
        assertEquals(totalDeFichas, depoisDaDesistencia.stacks.sum() + depoisDaDesistencia.pot)
        assertTrue(
            depoisDaDesistencia.stack(Seat(0)) > depoisDaDesistencia.stack(Seat(1)),
            "quem desistiu ficou mais pobre que quem ficou com o pote da mão anterior",
        )
        // Mão nova já repartida: duas cartas para cada um, botão rodou para a outra cadeira.
        assertEquals(2, depoisDaDesistencia.hand(Seat(0)).size)
        assertEquals(2, depoisDaDesistencia.hand(Seat(1)).size)
        assertEquals(Seat(0), depoisDaDesistencia.button)

        val segundaDesistencia = PokerGame.applyOrThrow(depoisDaDesistencia, PokerMove.Fold)
        assertEquals(totalDeFichas, segundaDesistencia.stacks.sum() + segundaDesistencia.pot)
        assertEquals(Seat(1), segundaDesistencia.button, "o botão volta a rodar na terceira mão")
    }

    @Test
    fun `o resultado da mao anterior fica guardado depois que a proxima ja foi repartida`() {
        val primeira = novo(buyIn = 1000, bigBlind = 20)
        assertEquals(null, primeira.lastResult, "antes da primeira mão fechar não há resultado nenhum")
        assertEquals(0, primeira.handNumber, "a partida começa na mão zero")

        val depois = PokerGame.applyOrThrow(primeira, PokerMove.Fold)
        assertEquals(PokerHandResult(pots = listOf(PokerPotShare(winners = listOf(0), amount = 30))), depois.lastResult)
        // O número da mão é o sinal que a tela usa para pausar no fim de cada uma — veja
        // io.github.andre88br.newgame.core.engine.GameEntry.handOf.
        assertEquals(1, depois.handNumber, "uma mão fechou: a próxima já é a de número um")

        // A mão seguinte já está em andamento (outro pote, outra rodada) — paga em vez de
        // desistir de novo, só para não fechar esta segunda mão também — e o resultado da
        // anterior precisa continuar ali até esta fechar e sobrescrever.
        val meioDaProxima = PokerGame.applyOrThrow(depois, PokerMove.Call)
        assertEquals(depois.lastResult, meioDaProxima.lastResult)
        assertEquals(depois.handNumber, meioDaProxima.handNumber, "ainda na mesma mão: o número não muda no meio dela")
    }

    // -------- all-in trava aumento, e a mesa se revela sozinha até o showdown --------

    @Test
    fun `com alguem all-in ninguem mais aumenta, e o resto da mesa sai de uma vez ate o showdown`() {
        // Pilhas curtas de propósito: o small blind já entra apostando quase tudo o que tem.
        var estado = novo(buyIn = 50, bigBlind = 20)
        assertEquals(40, estado.stack(Seat(1)))
        assertEquals(30, estado.stack(Seat(0)))

        estado = PokerGame.applyOrThrow(estado, PokerMove.Raise(to = 50)) // small blind vai all-in
        assertEquals(0, estado.stack(Seat(1)))
        assertTrue(estado.anyAllIn)
        assertFalse(
            PokerGame.legalMoves(estado).any { it is PokerMove.Raise },
            "com alguém all-in, aumentar deixa de ser opção para quem ainda decide",
        )
        assertIs<MoveResult.Illegal>(PokerGame.applyMove(estado, PokerMove.Raise(to = 60)))

        // O big blind só tem trinta fichas: pagar aqui também é ir all-in. Ninguém mais decide
        // nada depois disso — o motor revela sozinho flop, turn e river e faz o showdown.
        estado = PokerGame.applyOrThrow(estado, PokerMove.Call)
        assertEquals(0, estado.stack(Seat(0)))
        assertEquals(100, estado.stacks.sum() + estado.pot, "as cem fichas da mesa não podem sumir nem duplicar")

        val resultado = PokerGame.outcome(estado)
        if (resultado is Outcome.Win) {
            // Só uma cadeira tinha ficha para continuar: o torneio acabou aqui, sem mão nova.
            assertEquals(100, estado.stack(resultado.seat), "quem venceu o showdown leva o pote inteiro")
            assertEquals(5, estado.board.size)
            assertEquals(0, estado.pot)
        } else {
            // Empate no showdown: o pote voltou meio a meio, e o torneio segue com uma mão nova
            // já repartida — as duas cadeiras têm ficha de novo, e a mesa mostrou de volta.
            assertTrue(estado.stacks.all { it == 40 || it == 30 }, "os blinds da mão nova, sobre 50 e 50 empatados")
            assertEquals(0, estado.board.size, "mão nova já repartida depois do empate")
        }
    }

    // -------- pote lateral: quem foi all-in por menos só disputa até onde cobriu --------

    @Test
    fun `all-in por menos so ganha ate o dobro do que apostou, e o resto volta para quem cobriu mais`() {
        // Cadeira 0 foi all-in por 30 e tem a mão melhor; cadeira 1 cobriu 100. Mesmo vencendo
        // o showdown, a cadeira 0 só pode levar o que os dois colocaram até seus 30 — o
        // restante que a cadeira 1 apostou sozinha (70) não estava em disputa e volta para ela.
        val estado = PokerState(
            hands = listOf(
                listOf(Card(Rank.ACE, Suit.CLUBS), Card(Rank.ACE, Suit.DIAMONDS)),
                listOf(Card(Rank.KING, Suit.CLUBS), Card(Rank.KING, Suit.DIAMONDS)),
            ),
            board = listOf(
                Card(Rank.TWO, Suit.CLUBS),
                Card(Rank.SEVEN, Suit.DIAMONDS),
                Card(Rank.NINE, Suit.HEARTS),
                Card(Rank.QUEEN, Suit.SPADES),
                Card(Rank.THREE, Suit.CLUBS),
            ),
            deck = emptyList(),
            stacks = listOf(0, 400),
            streetBet = listOf(0, 0),
            contrib = listOf(30, 100),
            folded = listOf(false, false),
            toAct = listOf(true, false),
            pot = 130,
            street = PokerStreet.RIVER,
            minRaise = 20,
            button = Seat(1),
            smallBlind = 10,
            bigBlind = 20,
            turn = Seat(0),
            ply = 10,
            seats = 2,
            rng = Rng(1),
        )
        val totalDeFichas = estado.stacks.sum() + estado.pot

        val depois = PokerGame.applyOrThrow(estado, PokerMove.Check)

        assertEquals(
            PokerHandResult(
                pots = listOf(
                    PokerPotShare(winners = listOf(0), amount = 60),
                    PokerPotShare(winners = listOf(1), amount = 70),
                ),
            ),
            depois.lastResult,
            "cadeira 0 leva só o dobro dos 30 que apostou; os outros 70 voltam para a cadeira 1",
        )
        assertEquals(totalDeFichas, depois.stacks.sum() + depois.pot, "fichas não podem sumir nem duplicar")
    }

    // -------- all-in vira a carta, mas só depois que a rodada em que aconteceu fecha --------

    @Test
    fun `quem foi all-in fica com a carta escondida enquanto ainda falta alguem decidir se paga`() {
        // Cadeira 0 foi all-in por 30; cadeira 1 já cobriu os 100. Cadeira 2 ainda não decidiu
        // se paga aquela aposta — mostrar a carta da 0 agora daria a ela informação que não
        // teria numa mesa de verdade, bem na hora em que essa decisão ainda vale alguma coisa.
        val estado = PokerState(
            hands = listOf(
                listOf(Card(Rank.ACE, Suit.CLUBS), Card(Rank.ACE, Suit.DIAMONDS)),
                listOf(Card(Rank.KING, Suit.CLUBS), Card(Rank.KING, Suit.DIAMONDS)),
                listOf(Card(Rank.QUEEN, Suit.CLUBS), Card(Rank.QUEEN, Suit.DIAMONDS)),
            ),
            board = emptyList(),
            deck = emptyList(),
            stacks = listOf(0, 300, 300),
            streetBet = listOf(30, 100, 0),
            contrib = listOf(30, 100, 0),
            folded = listOf(false, false, false),
            toAct = listOf(false, false, true),
            pot = 130,
            street = PokerStreet.PREFLOP,
            minRaise = 20,
            button = Seat(2),
            smallBlind = 10,
            bigBlind = 20,
            turn = Seat(2),
            ply = 3,
            seats = 3,
            rng = Rng(1),
        )

        val paraCadeira2 = PokerGame.redactFor(estado, Seat(2))

        assertTrue(
            paraCadeira2.hand(Seat(0)).all { it.isHidden },
            "a rodada ainda está aberta: cadeira 2 ainda decide se paga o all-in",
        )
        assertEquals(estado.hand(Seat(2)), paraCadeira2.hand(Seat(2)), "a própria mão nunca é escondida de quem é dela")
    }

    @Test
    fun `quem foi all-in vira a carta assim que a rodada em que aconteceu fecha`() {
        // Mesmo all-in de 30 da cadeira 0, mas agora já no flop: a rodada em que ela foi
        // all-in fechou, ninguém mais pode aumentar (a regra do motor), e o resto da mão é só
        // passar até o showdown — não há mais decisão nenhuma que ver a carta possa mudar.
        val estado = PokerState(
            hands = listOf(
                listOf(Card(Rank.ACE, Suit.CLUBS), Card(Rank.ACE, Suit.DIAMONDS)),
                listOf(Card(Rank.KING, Suit.CLUBS), Card(Rank.KING, Suit.DIAMONDS)),
                listOf(Card(Rank.QUEEN, Suit.CLUBS), Card(Rank.QUEEN, Suit.DIAMONDS)),
            ),
            board = listOf(Card(Rank.TWO, Suit.SPADES), Card(Rank.SEVEN, Suit.HEARTS), Card(Rank.NINE, Suit.CLUBS)),
            deck = emptyList(),
            stacks = listOf(0, 200, 200),
            streetBet = listOf(0, 0, 0),
            contrib = listOf(30, 100, 100),
            folded = listOf(false, false, false),
            toAct = listOf(false, true, true),
            pot = 230,
            street = PokerStreet.FLOP,
            minRaise = 20,
            button = Seat(2),
            smallBlind = 10,
            bigBlind = 20,
            turn = Seat(1),
            ply = 5,
            seats = 3,
            rng = Rng(1),
        )

        val paraCadeira2 = PokerGame.redactFor(estado, Seat(2))

        assertFalse(
            paraCadeira2.hand(Seat(0)).any { it.isHidden },
            "a rodada onde a cadeira 0 foi all-in já fechou: não há mais nada que a carta escondida protegeria",
        )
        assertTrue(paraCadeira2.hand(Seat(1)).all { it.isHidden }, "quem não foi all-in continua com a carta escondida")
    }

    // -------- uma partida inteira, jogada até o fim --------

    /**
     * O mesmo espírito do teste de canastra que joga uma partida inteira sozinho: em vez de
     * confiar só nos pedaços testados isoladamente, joga cadeira contra cadeira — sempre
     * pagando ou passando, nunca aumentando nem desistindo — até o torneio zerar uma das
     * pilhas. Prova três coisas de uma vez: nunca fica sem lance legal com a partida em
     * andamento, o total de fichas na mesa não muda de uma mão para a outra, e o motor
     * realmente termina, em vez de repartir mão atrás de mão para sempre.
     */
    @Test
    fun `uma partida inteira termina sem travar, e as fichas nunca somem`() {
        var estado = novo(seed = 42, buyIn = 60, bigBlind = 20)
        val totalDeFichas = estado.stacks.sum() + estado.pot

        var passos = 0
        while (!PokerGame.outcome(estado).isOver && passos < 5000) {
            val lances = PokerGame.legalMoves(estado)
            assertTrue(lances.isNotEmpty(), "sem lance legal no passo $passos, com a partida em andamento:\n$estado")
            val escolhido = lances.firstOrNull { it is PokerMove.Call }
                ?: lances.firstOrNull { it is PokerMove.Check }
                ?: lances.first()
            estado = PokerGame.applyOrThrow(estado, escolhido)
            assertEquals(
                totalDeFichas,
                estado.stacks.sum() + estado.pot,
                "fichas sumiram ou apareceram do nada no passo $passos",
            )
            passos++
        }

        assertTrue(PokerGame.outcome(estado).isOver, "o torneio não terminou em $passos passos")
        assertTrue(PokerGame.legalMoves(estado).isEmpty(), "partida terminada não devia oferecer lance")
        val vencedor = PokerGame.outcome(estado)
        assertIs<Outcome.Win>(vencedor)
        assertEquals(totalDeFichas, estado.stack(vencedor.seat), "quem vence o torneio fica com todas as fichas")
    }
}
