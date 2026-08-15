package io.github.andre88br.newgame.core.games.canastra

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
import kotlin.test.assertTrue

/**
 * Canastra tem duas regras que não existem em nenhum outro jogo de formar trincas, e são as
 * que o usuário pediu por nome: o três vermelho, que vale ponto parado e nunca se joga, e o
 * três preto, que tranca o lixo de quem vem a seguir. O resto do arquivo cuida do que
 * sustenta as duas — morto, canastra e a contagem.
 */
class CanastraTest {

    private fun novo(seed: Long = 7, seats: Int = 4) =
        CanastraGame.initialState(MatchConfig(seed, seats = seats))

    private fun carta(rank: Rank, suit: Suit) = Card(rank, suit)

    // -------- a distribuição --------

    @Test
    fun `a mesa comeca com treze cartas para cada um`() {
        for (seats in 2..4) {
            val state = CanastraGame.initialState(MatchConfig(seed = 7, seats = seats))
            assertEquals(seats, state.hands.size)
            assertTrue(
                state.hands.all { it.size == CANASTRA_HAND_SIZE },
                "mão fora do tamanho a $seats: ${state.hands.map { it.size }}",
            )
            assertEquals(1, state.discard.size, "a mão abre com uma carta no lixo")
        }
    }

    /**
     * O morto é um só, e some na mesa de duplas. Não é detalhe de distribuição: sem ele,
     * ficar sem cartas passa a ser bater, e bater exige canastra.
     */
    @Test
    fun `o morto e um so, e a mesa de duplas nao tem`() {
        assertEquals(1, mortosFor(2), "a dois há um morto")
        assertEquals(1, mortosFor(3), "a três há um morto")
        assertEquals(0, mortosFor(4), "em duplas não há morto")

        for (seats in 2..4) {
            val state = CanastraGame.initialState(MatchConfig(seed = 7, seats = seats))
            assertEquals(mortosFor(seats), state.mortos.size, "mortos errados a $seats")
            assertTrue(
                state.mortos.all { it.size == CANASTRA_HAND_SIZE },
                "o morto tem o tamanho de uma mão",
            )
        }
    }

    @Test
    fun `o baralho e de dois, com quatro curingas, e nada se perde na distribuicao`() {
        val state = novo()
        val todas = state.hands.flatten() + state.mortos.flatten() + state.stock + state.discard
        // Os três vermelhos que foram para a mesa saíram das mãos: entram na conta à parte.
        assertEquals(
            108,
            todas.size + state.redThrees.sum(),
            "sumiu ou sobrou carta na distribuição",
        )
        assertEquals(4, todas.count { it.isJoker }, "dois baralhos trazem quatro curingas")
    }

    @Test
    fun `a mesma semente reparte a mesma mesa`() {
        assertEquals(novo(42).hands, novo(42).hands)
    }

    /**
     * Duplas só existem a quatro. A dois e a três joga-se individual — cada um é o seu
     * próprio time —, e é assim que a mesa pedida continua sendo a mesa montada.
     */
    @Test
    fun `so a mesa de quatro joga em duplas`() {
        for (seats in 2..4) {
            val state = CanastraGame.initialState(MatchConfig(seed = 3, seats = seats))
            assertEquals(seats, state.seats, "pedi mesa de $seats e veio outra")
            assertEquals(if (seats == 4) 2 else seats, state.teams, "times errados a $seats")
        }
    }

    @Test
    fun `em quatro as duplas sao as cadeiras opostas`() {
        val state = novo(seats = 4)
        assertEquals(state.teamOf(Seat(0)), state.teamOf(Seat(2)), "0 e 2 jogam juntos")
        assertEquals(state.teamOf(Seat(1)), state.teamOf(Seat(3)), "1 e 3 jogam juntos")
        assertTrue(state.teamOf(Seat(0)) != state.teamOf(Seat(1)), "vizinhos são adversários")
    }

    // -------- o três vermelho --------

    /**
     * O três vermelho nunca fica na mão: vai para a mesa assim que aparece, e quem o tirou
     * do monte compra outra carta no lugar. É ponto de graça, e não carta de jogo.
     */
    @Test
    fun `o tres vermelho sai da mao na distribuicao`() {
        for (seed in 1L..40L) {
            val state = novo(seed)
            assertTrue(
                state.hands.flatten().none { isRedThree(it) },
                "sobrou três vermelho na mão com a semente $seed",
            )
        }
    }

    @Test
    fun `comprar tres vermelho poe na mesa e compra de novo`() {
        val base = novo()
        val time = base.teamOf(Seat.FIRST)
        val comVermelho = base.copy(
            stock = listOf(carta(Rank.THREE, Suit.HEARTS), carta(Rank.KING, Suit.CLUBS)) + base.stock,
            phase = CanastraPhase.DRAW,
        )
        val depois = CanastraGame.applyOrThrow(comVermelho, CanastraMove.DrawStock)

        assertEquals(
            base.redThrees[time] + 1,
            depois.redThrees[time],
            "o três vermelho devia ter ido para a mesa",
        )
        assertTrue(
            carta(Rank.KING, Suit.CLUBS) in depois.hand(Seat.FIRST),
            "quem tira três vermelho compra outra carta no lugar",
        )
        assertTrue(depois.hand(Seat.FIRST).none { isRedThree(it) }, "vermelho não fica na mão")
    }

    @Test
    fun `o tres vermelho nao pode ser descartado nem baixado`() {
        val state = novo().let {
            it.copy(
                phase = CanastraPhase.PLAY,
                hands = it.hands.mapIndexed { index, mao ->
                    if (index == 0) mao + carta(Rank.THREE, Suit.DIAMONDS) else mao
                },
            )
        }
        val descarte = CanastraGame.applyMove(state, CanastraMove.Discard(carta(Rank.THREE, Suit.DIAMONDS)))
        assertTrue(descarte is MoveResult.Illegal, "descartar três vermelho devia ser recusado")

        val baixar = CanastraGame.applyMove(
            state,
            CanastraMove.Meld(List(3) { carta(Rank.THREE, Suit.DIAMONDS) }),
        )
        assertTrue(baixar is MoveResult.Illegal, "baixar três vermelho devia ser recusado")
    }

    /**
     * O sinal do três vermelho depende de a dupla ter canastra. É o que impede alguém de
     * tratá-lo como ponto garantido e não jogar.
     */
    @Test
    fun `o tres vermelho conta a favor com canastra e contra sem`() {
        val comCanastra = novo().copy(
            melds = listOf(listOf(Meld(List(7) { carta(Rank.KING, Suit.CLUBS) })), emptyList()),
            redThrees = listOf(2, 0),
            hands = List(4) { emptyList() },
            scores = listOf(0, 0),
        )
        val semCanastra = comCanastra.copy(melds = listOf(emptyList(), emptyList()))

        val com = CanastraGame.scoreHand(comCanastra)[0]
        val sem = CanastraGame.scoreHand(semCanastra)[0]

        assertTrue(com > sem, "com canastra o vermelho soma; sem, subtrai (com=$com sem=$sem)")
        // A diferença é a canastra mais o **dobro** do que valem os vermelhos: num lado eles
        // entram somando, no outro subtraindo, e a distância entre os dois é duas vezes.
        assertEquals(
            2 * (2 * RED_THREE_VALUE),
            com - sem - Meld(List(7) { carta(Rank.KING, Suit.CLUBS) }).score,
            "os três vermelhos trocam de sinal conforme a dupla tenha canastra",
        )
    }

    // -------- o três preto --------

    @Test
    fun `o tres preto em cima tranca o lixo`() {
        val state = novo().copy(
            phase = CanastraPhase.DRAW,
            discard = listOf(carta(Rank.KING, Suit.HEARTS), carta(Rank.THREE, Suit.SPADES)),
        )
        assertTrue(state.discardBlocked, "três preto em cima devia trancar")
        assertTrue(
            CanastraMove.TakeDiscard !in CanastraGame.legalMoves(state),
            "com o lixo trancado, pegar o lixo não é lance",
        )
        assertTrue(
            CanastraGame.applyMove(state, CanastraMove.TakeDiscard) is MoveResult.Illegal,
            "pegar lixo trancado devia ser recusado",
        )
    }

    @Test
    fun `sem o tres preto em cima o lixo pode ser pego`() {
        val state = novo().copy(
            phase = CanastraPhase.DRAW,
            discard = listOf(carta(Rank.THREE, Suit.SPADES), carta(Rank.KING, Suit.HEARTS)),
        )
        assertTrue(!state.discardBlocked, "o três preto embaixo não tranca nada")

        val antes = state.handSize(Seat.FIRST)
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.TakeDiscard)
        assertEquals(antes + 2, depois.handSize(Seat.FIRST), "pega-se o lixo inteiro")
        assertTrue(depois.discard.isEmpty(), "o lixo fica vazio depois de pego")
    }

    @Test
    fun `o tres preto pode ser descartado, e e isso que tranca`() {
        val preto = carta(Rank.THREE, Suit.CLUBS)
        val state = novo().let {
            it.copy(
                phase = CanastraPhase.PLAY,
                hands = it.hands.mapIndexed { index, mao -> if (index == 0) mao + preto else mao },
            )
        }
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.Discard(preto))
        assertTrue(depois.discardBlocked, "descartar três preto tranca o lixo de quem vem")
    }

    @Test
    fun `o tres preto nao entra em jogo comum`() {
        val preto = carta(Rank.THREE, Suit.CLUBS)
        val state = novo().let {
            it.copy(
                phase = CanastraPhase.PLAY,
                hands = it.hands.mapIndexed { index, mao ->
                    if (index == 0) mao + List(3) { preto } else mao
                },
            )
        }
        val recusa = CanastraGame.applyMove(state, CanastraMove.Meld(List(3) { preto }))
        assertTrue(recusa is MoveResult.Illegal, "baixar três preto fora da batida devia ser recusado")
    }

    // -------- jogos e canastra --------

    @Test
    fun `um jogo precisa de tres cartas, duas naturais e no maximo tres curingas`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        val coringa = carta(Rank.JOKER, Suit.HEARTS)
        val dois = carta(Rank.TWO, Suit.SPADES)

        assertTrue(isValidMeld(List(3) { rei }), "três reis formam jogo")
        assertTrue(isValidMeld(listOf(rei, rei, coringa)), "duas naturais e um curinga formam jogo")
        assertTrue(!isValidMeld(listOf(rei, rei)), "duas cartas não formam jogo")
        assertTrue(!isValidMeld(listOf(rei, coringa, dois)), "uma natural só não sustenta jogo")
        assertTrue(
            !isValidMeld(listOf(rei, rei, coringa, coringa, dois, dois)),
            "mais de três curingas não é jogo",
        )
        assertTrue(
            !isValidMeld(listOf(rei, rei, carta(Rank.QUEEN, Suit.CLUBS))),
            "valores diferentes não formam jogo",
        )
    }

    /**
     * O que suja a canastra é o **dois**, e não o curinga em geral. Os dois são curinga do
     * mesmo jeito na hora de formar o jogo; a diferença é só de prêmio.
     */
    @Test
    fun `sete cartas fazem canastra, e so o dois a suja`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        val limpa = Meld(List(7) { rei })
        val comCoringa = Meld(List(6) { rei } + carta(Rank.JOKER, Suit.HEARTS))
        val suja = Meld(List(6) { rei } + carta(Rank.TWO, Suit.SPADES))

        assertTrue(limpa.isCanastra && limpa.isClean, "sete naturais são canastra limpa")
        assertTrue(comCoringa.isClean, "canastra fechada com coringa continua limpa")
        assertTrue(suja.isCanastra && !suja.isClean, "canastra com dois é suja")

        assertEquals(limpa.cards.sumOf { cardValue(it) } + 200, limpa.score)
        assertEquals(comCoringa.cards.sumOf { cardValue(it) } + 200, comCoringa.score)
        assertEquals(suja.cards.sumOf { cardValue(it) } + 100, suja.score)
    }

    @Test
    fun `os valores das cartas seguem a tabela da canastra`() {
        assertEquals(50, cardValue(carta(Rank.JOKER, Suit.HEARTS)))
        assertEquals(20, cardValue(carta(Rank.TWO, Suit.CLUBS)))
        assertEquals(20, cardValue(carta(Rank.ACE, Suit.SPADES)))
        assertEquals(10, cardValue(carta(Rank.KING, Suit.HEARTS)))
        assertEquals(10, cardValue(carta(Rank.EIGHT, Suit.CLUBS)))
        assertEquals(5, cardValue(carta(Rank.SEVEN, Suit.CLUBS)))
        assertEquals(5, cardValue(carta(Rank.FOUR, Suit.DIAMONDS)))
        assertEquals(5, cardValue(carta(Rank.THREE, Suit.CLUBS)))
    }

    @Test
    fun `o dois e curinga, e por isso vale mais do que a ordem dele sugere`() {
        assertTrue(isWild(carta(Rank.TWO, Suit.HEARTS)))
        assertTrue(isWild(carta(Rank.JOKER, Suit.CLUBS)))
        assertTrue(!isWild(carta(Rank.THREE, Suit.HEARTS)))
        assertTrue(cardValue(carta(Rank.TWO, Suit.CLUBS)) > cardValue(carta(Rank.FOUR, Suit.CLUBS)))
    }

    // -------- o morto --------

    /**
     * Ficar sem cartas não acaba a mão: pega-se o morto e continua. É o que separa canastra
     * de um jogo em que basta se livrar das cartas.
     */
    @Test
    fun `ficar sem cartas pega o morto em vez de bater`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        val state = novo(seats = 2).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(List(3) { rei }, emptyList()),
        )
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.Meld(List(3) { rei }))

        assertTrue(depois.tookMorto[depois.teamOf(Seat.FIRST)], "quem zerou devia ter pegado o morto")
        assertEquals(CANASTRA_HAND_SIZE, depois.handSize(Seat.FIRST), "o morto tem treze cartas")
        assertTrue(depois.mortos.isEmpty(), "o morto é um só: pego, a mesa fica sem")
        assertEquals(-1, depois.wentOut, "pegar o morto não é bater")
    }

    /**
     * O morto é da **mesa**, e não de cada lado: quem chegar primeiro leva, e o outro fica
     * sem. Era a diferença invisível enquanto havia dois mortos, um para cada dupla.
     */
    @Test
    fun `pego o morto, o outro lado fica sem`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        val primeiro = CanastraGame.applyOrThrow(
            novo(seats = 2).copy(
                phase = CanastraPhase.PLAY,
                hands = listOf(List(3) { rei }, emptyList()),
            ),
            CanastraMove.Meld(List(3) { rei }),
        )
        assertTrue(primeiro.mortos.isEmpty())

        // Agora a outra cadeira zera a mão, com canastra na mesa para poder bater.
        val dama = carta(Rank.QUEEN, Suit.HEARTS)
        val outro = primeiro.copy(
            turn = Seat(1),
            phase = CanastraPhase.PLAY,
            hands = listOf(primeiro.hand(Seat.FIRST), List(3) { dama }),
            melds = listOf(primeiro.melds[0], listOf(Meld(List(7) { dama }))),
            scores = listOf(0, 0),
        )
        val depois = CanastraGame.applyOrThrow(outro, CanastraMove.Meld(List(3) { dama }))

        assertTrue(
            depois.scores.any { it > 0 },
            "sem morto para pegar, zerar a mão bate: ${depois.scores}",
        )
    }

    /**
     * Sem morto e sem canastra, baixar tudo seria bater sem ter direito. O motor recusa — e
     * recusa também deixar **uma** carta, porque a vez ainda termina em descarte e esse
     * descarte zeraria a mão do mesmo jeito.
     */
    @Test
    fun `sem canastra e sem morto nao da para esvaziar a mao`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(List(3) { rei }, emptyList(), emptyList(), emptyList()),
            melds = List(2) { emptyList() },
            mortos = emptyList(),
            tookMorto = listOf(false, false),
        )
        val lance = CanastraMove.Meld(List(3) { rei })

        assertTrue(
            CanastraGame.applyMove(state, lance) is MoveResult.Illegal,
            "baixar a mão inteira sem canastra devia ser recusado",
        )
        assertTrue(lance !in CanastraGame.legalMoves(state), "e nem devia ser oferecido")

        // Com uma canastra na mesa o mesmo lance passa: aí é bater de verdade.
        val comCanastra = state.copy(melds = listOf(listOf(Meld(List(7) { rei })), emptyList()))
        assertTrue(
            CanastraGame.applyMove(comCanastra, lance) is MoveResult.Ok,
            "com canastra, bater é permitido",
        )
    }

    /** O próprio lance pode fechar a sétima carta — e aí ele já vale como batida. */
    @Test
    fun `o lance que fecha a canastra pode ser o que bate`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            // Uma carta na mão: baixá-la zera a mão e, ao mesmo tempo, fecha a sétima.
            hands = listOf(listOf(rei), emptyList(), emptyList(), emptyList()),
            melds = listOf(listOf(Meld(List(6) { rei })), emptyList()),
            mortos = emptyList(),
            tookMorto = listOf(false, false),
            scores = listOf(0, 0),
        )
        val lance = CanastraMove.Meld(listOf(rei), into = 0)
        assertTrue(
            CanastraGame.applyMove(state, lance) is MoveResult.Ok,
            "a sétima carta fecha a canastra e autoriza a batida no mesmo lance",
        )

        // A mesma carta sem a canastra a caminho: recusada.
        val curta = state.copy(melds = listOf(listOf(Meld(List(5) { rei })), emptyList()))
        assertTrue(
            CanastraGame.applyMove(curta, lance) is MoveResult.Illegal,
            "a sexta carta não fecha nada, e zerar a mão sem canastra é recusado",
        )
    }

    @Test
    fun `com o morto pego e canastra na mesa, acabar as cartas bate`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        val time = novo().teamOf(Seat.FIRST)
        val pegou = MutableList(2) { false }.also { it[time] = true }
        val state = novo().copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(List(3) { rei }, emptyList(), emptyList(), emptyList()),
            melds = List(2) { if (it == time) listOf(Meld(List(7) { rei })) else emptyList() },
            tookMorto = pegou.toList(),
            mortos = emptyList(),
            scores = listOf(0, 0),
        )
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.Meld(List(3) { rei }))

        // Bateu: a mão fechou e a partida seguiu para a mão seguinte, com o placar somado.
        assertTrue(
            depois.scores.any { it > 0 },
            "bater devia somar pontos: ${depois.scores}",
        )
    }

    // -------- a vez --------

    @Test
    fun `a vez comeca comprando, e so depois se baixa ou descarta`() {
        val state = novo()
        assertEquals(CanastraPhase.DRAW, state.phase)

        val legais = CanastraGame.legalMoves(state)
        assertTrue(legais.all { it is CanastraMove.DrawStock || it is CanastraMove.TakeDiscard })

        val recusa = CanastraGame.applyMove(state, CanastraMove.Discard(state.hand(Seat.FIRST).first()))
        assertTrue(recusa is MoveResult.Illegal, "descartar antes de comprar devia ser recusado")
    }

    @Test
    fun `comprar duas vezes na mesma vez e recusado`() {
        val depois = CanastraGame.applyOrThrow(novo(), CanastraMove.DrawStock)
        assertEquals(CanastraPhase.PLAY, depois.phase)
        assertTrue(
            CanastraGame.applyMove(depois, CanastraMove.DrawStock) is MoveResult.Illegal,
            "só se compra uma vez por vez",
        )
    }

    @Test
    fun `o descarte passa a vez`() {
        val state = CanastraGame.applyOrThrow(novo(), CanastraMove.DrawStock)
        val carta = state.hand(Seat.FIRST).first { !isRedThree(it) }
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.Discard(carta))

        assertEquals(Seat(1), depois.turn, "a vez passa para a cadeira seguinte")
        assertEquals(CanastraPhase.DRAW, depois.phase, "a vez nova começa comprando")
        assertEquals(carta, depois.discardTop, "a carta descartada fica em cima do lixo")
    }

    // -------- a partida inteira --------

    @Test
    fun `uma partida inteira termina sem travar`() {
        for (seats in listOf(2, 4)) {
            var state = CanastraGame.initialState(MatchConfig(seed = 2026, seats = seats))
            var guard = 0
            while (!CanastraGame.outcome(state).isOver && guard++ < 20_000) {
                val legais = CanastraGame.legalMoves(state)
                assertTrue(
                    legais.isNotEmpty(),
                    "mesa de $seats travou sem lance no ply ${state.ply}: $state",
                )
                state = CanastraGame.applyOrThrow(state, legais.first())
            }
            assertTrue(
                CanastraGame.outcome(state).isOver,
                "mesa de $seats não terminou em $guard lances (placar ${state.scores})",
            )
            assertTrue(
                state.scores.any { it >= CANASTRA_TARGET },
                "a partida só acaba com alguém em $CANASTRA_TARGET: ${state.scores}",
            )
        }
    }

    @Test
    fun `nenhuma carta se perde ao longo da mao`() {
        var state = novo(seed = 55)
        var guard = 0
        val naMaoInicial = state.hands.flatten().size
        while (guard++ < 400 && !CanastraGame.outcome(state).isOver) {
            val total = state.hands.flatten().size +
                state.melds.flatten().sumOf { it.cards.size } +
                state.stock.size + state.discard.size +
                state.mortos.flatten().size + state.redThrees.sum()
            assertTrue(total <= 108, "apareceu carta a mais no ply ${state.ply}: $total")
            state = CanastraGame.applyOrThrow(state, CanastraGame.legalMoves(state).first())
        }
        assertTrue(naMaoInicial > 0)
    }

    // -------- informação oculta --------

    @Test
    fun `a mao dos outros, o monte e os mortos nao vazam`() {
        val state = novo()
        val visto = CanastraGame.redactFor(state, Seat.FIRST)

        assertEquals(state.hand(Seat.FIRST), visto.hand(Seat.FIRST), "a própria mão fica")
        for (index in 1 until 4) {
            assertTrue(visto.hand(Seat(index)).all { it.isHidden }, "vazou mão da cadeira $index")
            assertEquals(state.handSize(Seat(index)), visto.handSize(Seat(index)), "contagem errada")
        }
        assertTrue(visto.stock.all { it.isHidden }, "o monte não pode ser visto")
        assertTrue(visto.mortos.all { morto -> morto.all { it.isHidden } }, "o morto não pode ser visto")
        // Os jogos na mesa são públicos: escondê-los seria esconder o tabuleiro.
        assertEquals(state.melds, visto.melds, "os jogos baixados são de todos")
        assertEquals(state.discard, visto.discard, "o lixo é público")
    }

    @Test
    fun `o mundo sorteado respeita as contagens`() {
        val state = novo(seed = 9)
        val visto = CanastraGame.redactFor(state, Seat.FIRST)
        val mundo = completeCanastra(visto, Rng.seeded(4))

        for (index in 0 until 4) {
            assertEquals(
                state.handSize(Seat(index)),
                mundo.handSize(Seat(index)),
                "cadeira $index ficou com o número errado de cartas",
            )
        }
        assertEquals(state.stock.size, mundo.stock.size, "o monte mudou de tamanho")
        assertTrue(mundo.hands.flatten().none { it.isHidden }, "sobrou carta virada no mundo")
        assertEquals(
            state.hand(Seat.FIRST),
            mundo.hand(Seat.FIRST),
            "o mundo não pode mexer na mão de quem olha",
        )
        assertTrue(
            mundo.hands.flatten().none { isRedThree(it) },
            "o mundo não pode inventar três vermelho na mão: ele nunca fica lá",
        )
    }

    @Test
    fun `a maquina joga so com o que enxerga, e sempre lance legal`() {
        var state = novo(seed = 11)
        var guard = 0
        while (!CanastraGame.outcome(state).isOver && guard++ < 80) {
            val comoEleVe = CanastraGame.redactFor(state, state.turn)
            val escolhido = CanastraAi.chooseMove(comoEleVe, Difficulty.EASY, seed = guard.toLong())
            assertTrue(escolhido != null, "a IA não escolheu lance no ply ${state.ply}")
            assertTrue(
                CanastraGame.applyMove(state, escolhido) is MoveResult.Ok,
                "a IA escolheu lance ilegal: ${escolhido.describe()}",
            )
            state = CanastraGame.applyOrThrow(state, escolhido)
        }
    }
}
