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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Canastra tem regras que não existem em nenhum outro jogo deste app, e são as que o usuário
 * pediu por nome: o jogo normal é uma **sequência** do mesmo naipe, e não uma trinca; a
 * **trinca** só se libera depois da primeira canastra; o três vermelho vale ponto parado, e
 * só com canastra; o três preto tranca o lixo e nunca entra em jogo; e cada carta além da
 * sétima numa canastra **limpa** rende mais cem pontos. O resto do arquivo cuida do que
 * sustenta tudo isso — morto, distribuição e a contagem.
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
     * O três vermelho só vale alguma coisa se a dupla tem canastra. Sem canastra, ele não
     * subtrai nem soma: fica em zero. É o que impede alguém de tratá-lo como ponto garantido
     * e não jogar — mas também não pune quem simplesmente ainda não fechou nenhuma.
     */
    @Test
    fun `o tres vermelho conta com canastra e nao conta sem`() {
        val comCanastra = novo().copy(
            melds = listOf(listOf(Meld(List(7) { carta(Rank.KING, Suit.CLUBS) })), emptyList()),
            redThrees = listOf(2, 0),
            hands = List(4) { emptyList() },
            scores = listOf(0, 0),
            batidas = listOf(0, 0),
        )
        val semCanastra = comCanastra.copy(melds = listOf(emptyList(), emptyList()))

        val com = CanastraGame.scoreHand(comCanastra)[0]
        val sem = CanastraGame.scoreHand(semCanastra)[0]

        // A diferença é a canastra (que só existe do lado "com") mais os dois vermelhos a
        // 100 cada — e nada além disso, porque sem canastra eles não descontam.
        assertEquals(
            Meld(List(7) { carta(Rank.KING, Suit.CLUBS) }).score + 2 * RED_THREE_VALUE,
            com - sem,
            "com canastra a canastra soma e os vermelhos somam; sem, só falta os dois",
        )
    }

    @Test
    fun `quatro tres vermelhos somam quatrocentos, sem dobrar`() {
        val state = novo().copy(
            melds = listOf(listOf(Meld(List(7) { carta(Rank.KING, Suit.CLUBS) })), emptyList()),
            redThrees = listOf(4, 0),
            hands = List(4) { emptyList() },
            scores = listOf(0, 0),
            batidas = listOf(0, 0),
        )
        val pontos = CanastraGame.scoreHand(state)[0]
        assertEquals(
            Meld(List(7) { carta(Rank.KING, Suit.CLUBS) }).score + 4 * RED_THREE_VALUE,
            pontos,
            "quatro vermelhos são 400, não 800",
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
    fun `o tres preto nao entra em jogo nenhum`() {
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
        assertTrue(recusa is MoveResult.Illegal, "baixar três preto devia ser recusado")
    }

    /** Guardar um três preto na mão custa caro — é o preço de segurar a tranca do lixo. */
    @Test
    fun `o tres preto na mao custa cem pontos negativos`() {
        val state = novo().copy(
            hands = listOf(listOf(carta(Rank.THREE, Suit.CLUBS)), emptyList(), emptyList(), emptyList()),
            melds = List(2) { emptyList() },
            redThrees = listOf(0, 0),
            scores = listOf(0, 0),
            batidas = listOf(0, 0),
        )
        assertEquals(-100, CanastraGame.scoreHand(state)[0])
    }

    // -------- o curinga tranca o lixo --------

    @Test
    fun `um curinga em cima tranca o lixo, coringa ou dois`() {
        for (curinga in listOf(carta(Rank.JOKER, Suit.HEARTS), carta(Rank.TWO, Suit.CLUBS))) {
            val state = novo().copy(
                phase = CanastraPhase.DRAW,
                discard = listOf(carta(Rank.KING, Suit.HEARTS), curinga),
            )
            assertTrue(state.discardBlocked, "$curinga em cima devia trancar")
            assertTrue(
                CanastraMove.TakeDiscard !in CanastraGame.legalMoves(state),
                "com o lixo trancado por $curinga, pegar o lixo não é lance",
            )
            assertTrue(
                CanastraGame.applyMove(state, CanastraMove.TakeDiscard) is MoveResult.Illegal,
                "pegar lixo trancado por $curinga devia ser recusado",
            )
        }
    }

    @Test
    fun `descartar o curinga tranca o lixo de quem vem`() {
        val curinga = carta(Rank.JOKER, Suit.HEARTS)
        val state = novo().let {
            it.copy(
                phase = CanastraPhase.PLAY,
                hands = it.hands.mapIndexed { index, mao -> if (index == 0) mao + curinga else mao },
            )
        }
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.Discard(curinga))
        assertTrue(depois.discardBlocked, "descartar o curinga tranca o lixo, igual ao três preto")
    }

    // -------- sequências --------

    @Test
    fun `tres cartas seguidas do mesmo naipe formam sequencia`() {
        val seq = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS))
        assertEquals(seq, asSequence(seq), "já vinha na ordem certa")
        assertEquals(seq, asSequence(seq.shuffled(kotlin.random.Random(1))), "a ordem de escolha não importa")
    }

    @Test
    fun `naipes diferentes nao formam sequencia`() {
        val mista = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.SPADES), carta(Rank.SIX, Suit.HEARTS))
        assertNull(asSequence(mista), "naipe misto não é sequência")
    }

    @Test
    fun `o tres nunca entra em sequencia`() {
        val comTres = listOf(carta(Rank.THREE, Suit.HEARTS), carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS))
        assertNull(asSequence(comTres), "o três não é carta de sequência")
    }

    @Test
    fun `o as fecha por cima e nao da a volta`() {
        val fecha = listOf(carta(Rank.QUEEN, Suit.SPADES), carta(Rank.KING, Suit.SPADES), carta(Rank.ACE, Suit.SPADES))
        assertEquals(fecha, asSequence(fecha), "dama, rei, ás fecha a sequência")

        // Ás mais quatro não é sequência: não existe "ás-dois-três-quatro" na canastra.
        val naoDaVolta = listOf(carta(Rank.ACE, Suit.SPADES), carta(Rank.FOUR, Suit.SPADES), carta(Rank.FIVE, Suit.SPADES))
        assertNull(asSequence(naoDaVolta), "o ás não emenda com o quatro")
    }

    @Test
    fun `um curinga tapa um buraco so, na posicao exata`() {
        val par = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS), carta(Rank.JOKER, Suit.SPADES))
        val arranjo = asSequence(par)
        assertEquals(
            listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.JOKER, Suit.SPADES), carta(Rank.SIX, Suit.HEARTS)),
            arranjo,
            "o curinga tapa o cinco, no meio",
        )
    }

    /**
     * `{4, 5, curinga}` só pode fechar como 4-5-6: o curinga na frente seria o três, que a
     * canastra proíbe em jogo. A cauda é a escolha certa, e não uma entre duas.
     */
    @Test
    fun `curinga em corrida fechada vai para a cauda, nunca vira o tres`() {
        val par = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.JOKER, Suit.SPADES))
        val arranjo = asSequence(par)
        assertEquals(
            listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.JOKER, Suit.SPADES)),
            arranjo,
            "o curinga vira o seis, não o três",
        )
    }

    @Test
    fun `dois curingas na mesma sequencia sao recusados`() {
        val doisCuringas = listOf(
            carta(Rank.FOUR, Suit.HEARTS),
            carta(Rank.JOKER, Suit.SPADES),
            carta(Rank.JOKER, Suit.HEARTS),
        )
        assertNull(asSequence(doisCuringas), "no máximo um curinga por jogo")
    }

    @Test
    fun `duas cartas do mesmo valor nao formam sequencia`() {
        val repetida = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FOUR, Suit.SPADES), carta(Rank.FIVE, Suit.HEARTS))
        assertNull(asSequence(repetida), "valor repetido não é corrida")
    }

    // -------- trincas --------

    @Test
    fun `tres cartas do mesmo valor formam trinca, com no maximo um curinga`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        assertTrue(isValidSet(List(3) { rei }), "três reis formam trinca")
        assertTrue(isValidSet(listOf(rei, rei, carta(Rank.JOKER, Suit.HEARTS))), "duas naturais e um curinga")
        assertTrue(!isValidSet(listOf(rei, rei)), "duas cartas não formam jogo")
        assertTrue(
            !isValidSet(listOf(rei, rei, carta(Rank.JOKER, Suit.HEARTS), carta(Rank.TWO, Suit.SPADES))),
            "mais de um curinga não é jogo",
        )
        assertTrue(
            !isValidSet(listOf(rei, rei, carta(Rank.QUEEN, Suit.CLUBS))),
            "valores diferentes não formam trinca",
        )
    }

    @Test
    fun `asMeld escolhe sequencia ou trinca conforme as cartas`() {
        val sequencia = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS))
        assertEquals(MeldKind.SEQUENCE, asMeld(sequencia)?.kind)

        val trinca = List(3) { carta(Rank.KING, Suit.CLUBS) }
        assertEquals(MeldKind.SET, asMeld(trinca)?.kind)

        assertNull(asMeld(listOf(carta(Rank.KING, Suit.CLUBS), carta(Rank.QUEEN, Suit.HEARTS))))
    }

    /** A regra do usuário: trinca só desce depois que a dupla já tem uma canastra. */
    @Test
    fun `trinca e recusada antes da primeira canastra, e aceita depois`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(List(3) { rei } + List(10) { carta(Rank.SEVEN, Suit.SPADES) }, emptyList(), emptyList(), emptyList()),
            melds = List(2) { emptyList() },
        )
        val lance = CanastraMove.Meld(List(3) { rei })

        assertTrue(
            CanastraGame.applyMove(state, lance) is MoveResult.Illegal,
            "sem canastra, trinca é recusada",
        )
        assertTrue(lance !in CanastraGame.legalMoves(state), "e nem devia ser oferecida")

        val comCanastra = state.copy(
            melds = listOf(listOf(Meld(List(7) { carta(Rank.QUEEN, Suit.CLUBS) })), emptyList()),
        )
        assertTrue(
            CanastraGame.applyMove(comCanastra, lance) is MoveResult.Ok,
            "com canastra, a trinca passa a valer",
        )
    }

    // -------- estender jogo baixado --------

    @Test
    fun `uma carta natural estende pela cabeca ou pela cauda`() {
        val meio = Meld(listOf(carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS), carta(Rank.SEVEN, Suit.HEARTS)))

        val comCauda = extendMeld(meio, carta(Rank.EIGHT, Suit.HEARTS))
        assertEquals(
            listOf(
                carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS),
                carta(Rank.SEVEN, Suit.HEARTS), carta(Rank.EIGHT, Suit.HEARTS),
            ),
            comCauda?.cards,
        )

        val comCabeca = extendMeld(meio, carta(Rank.FOUR, Suit.HEARTS))
        assertEquals(
            listOf(
                carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS),
                carta(Rank.SIX, Suit.HEARTS), carta(Rank.SEVEN, Suit.HEARTS),
            ),
            comCabeca?.cards,
        )

        assertNull(extendMeld(meio, carta(Rank.NINE, Suit.HEARTS)), "um degrau a mais não encaixa")
        assertNull(extendMeld(meio, carta(Rank.EIGHT, Suit.SPADES)), "naipe errado não encaixa")
    }

    /** Um lance só pode crescer as duas pontas de uma vez: uma carta abre espaço para a outra. */
    @Test
    fun `um lance pode estender as duas pontas ao mesmo tempo`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(
                listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.NINE, Suit.HEARTS)) + List(9) { rei },
                emptyList(), emptyList(), emptyList(),
            ),
            melds = listOf(
                listOf(
                    Meld(
                        listOf(
                            carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS),
                            carta(Rank.SEVEN, Suit.HEARTS), carta(Rank.EIGHT, Suit.HEARTS),
                        ),
                    ),
                ),
                emptyList(),
            ),
        )
        val lance = CanastraMove.Meld(
            listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.NINE, Suit.HEARTS)),
            into = 0,
        )
        val depois = CanastraGame.applyOrThrow(state, lance)
        assertEquals(6, depois.meldsOf(Seat.FIRST).first().cards.size, "cresceu dos dois lados")
    }

    // -------- trocar o curinga --------

    @Test
    fun `trocar o curinga cresce o jogo em uma carta e o desloca para a ponta`() {
        val naipe = Suit.SPADES
        val seis = carta(Rank.SIX, naipe)
        val jogo = Meld(
            listOf(
                carta(Rank.FOUR, naipe), carta(Rank.FIVE, naipe),
                carta(Rank.JOKER, Suit.HEARTS), carta(Rank.SEVEN, naipe),
            ),
        )
        assertEquals(seis, wildRepresents(jogo), "o curinga está fazendo de seis")

        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(listOf(seis) + List(10) { carta(Rank.KING, Suit.CLUBS) }, emptyList(), emptyList(), emptyList()),
            melds = listOf(listOf(jogo), emptyList()),
        )
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.SwapWild(0, seis))
        val jogoDepois = depois.meldsOf(Seat.FIRST).first()

        assertEquals(5, jogoDepois.cards.size, "o jogo cresceu de quatro para cinco")
        assertEquals(1, jogoDepois.wilds.size, "o curinga continua lá, só mudou de lugar")
        assertEquals(
            carta(Rank.EIGHT, naipe),
            wildRepresents(jogoDepois),
            "o curinga desceu para a ponta livre, que era o oito",
        )
        assertTrue(seis !in depois.hand(Seat.FIRST), "a carta trocada saiu da mão")
    }

    @Test
    fun `sem ponta livre nao da para trocar o curinga`() {
        val naipe = Suit.SPADES
        // Do quatro ao rei, mais o curinga fazendo de ás: a sequência já ocupa a escala inteira.
        val completa = CANASTRA_SEQUENCE_RANKS.dropLast(1).map { carta(it, naipe) } + carta(Rank.JOKER, Suit.HEARTS)
        val jogo = Meld(completa)
        val as_ = carta(Rank.ACE, naipe)
        assertEquals(as_, wildRepresents(jogo), "o curinga está fazendo de ás")

        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(listOf(as_) + List(10) { carta(Rank.KING, Suit.DIAMONDS) }, emptyList(), emptyList(), emptyList()),
            melds = listOf(listOf(jogo), emptyList()),
        )
        val resultado = CanastraGame.applyMove(state, CanastraMove.SwapWild(0, as_))
        assertTrue(resultado is MoveResult.Illegal, "as duas pontas já estão ocupadas")
    }

    // -------- canastra e o prêmio de render --------

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

    /**
     * A conta do usuário: canastra limpa do quatro ao ás, sem curinga, vale 700. É o que prova
     * que cada carta além da sétima soma mais cem — só na limpa.
     */
    @Test
    fun `canastra limpa do quatro ao as vale setecentos`() {
        val naipe = Suit.HEARTS
        val quatroAoAs = Meld(CANASTRA_SEQUENCE_RANKS.map { carta(it, naipe) })

        assertEquals(11, quatroAoAs.cards.size)
        assertTrue(quatroAoAs.isClean)
        assertEquals(700, quatroAoAs.score)
    }

    @Test
    fun `o premio de render so vale para a canastra limpa`() {
        val rei = carta(Rank.KING, Suit.CLUBS)
        // Oito reis mais um dois: nove cartas, e é o dois que suja.
        val suja = Meld(List(8) { rei } + carta(Rank.TWO, Suit.SPADES))
        assertTrue(suja.isCanastra && !suja.isClean, "o dois suja mesmo com nove cartas")
        // Sem o prêmio de render, o bônus fica fixo em cem, não importa quantas cartas a mais.
        assertEquals(suja.cards.sumOf { cardValue(it) } + 100, suja.score)
    }

    // -------- o morto --------

    /**
     * Ficar sem cartas não acaba a mão: pega-se o morto e continua. É o que separa canastra
     * de um jogo em que basta se livrar das cartas. Usa uma sequência (e não trinca) para não
     * disparar, sem querer, o portão da trinca — que é outra regra.
     */
    @Test
    fun `ficar sem cartas pega o morto em vez de bater`() {
        val sequencia = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS))
        val state = novo(seats = 2).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(sequencia, emptyList()),
        )
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.Meld(sequencia))

        assertTrue(depois.tookMorto[depois.teamOf(Seat.FIRST)], "quem zerou devia ter pegado o morto")
        assertEquals(CANASTRA_HAND_SIZE, depois.handSize(Seat.FIRST), "o morto tem treze cartas")
        assertTrue(depois.mortos.isEmpty(), "o morto é um só: pego, a mesa fica sem")
        assertEquals(-1, depois.wentOut, "pegar o morto não é bater")
        assertEquals(1, depois.batidas[depois.teamOf(Seat.FIRST)], "pegar o morto conta como batida")
    }

    /**
     * O morto é da **mesa**, e não de cada lado: quem chegar primeiro leva, e o outro fica
     * sem. Era a diferença invisível enquanto havia dois mortos, um para cada dupla.
     */
    @Test
    fun `pego o morto, o outro lado fica sem`() {
        val sequencia = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS))
        val primeiro = CanastraGame.applyOrThrow(
            novo(seats = 2).copy(
                phase = CanastraPhase.PLAY,
                hands = listOf(sequencia, emptyList()),
            ),
            CanastraMove.Meld(sequencia),
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
     * descarte zeraria a mão do mesmo jeito. Sequência, não trinca: a trinca já teria seu
     * próprio portão antes de chegar aqui.
     */
    @Test
    fun `sem canastra e sem morto nao da para esvaziar a mao`() {
        val sequencia = listOf(carta(Rank.FOUR, Suit.CLUBS), carta(Rank.FIVE, Suit.CLUBS), carta(Rank.SIX, Suit.CLUBS))
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(sequencia, emptyList(), emptyList(), emptyList()),
            melds = List(2) { emptyList() },
            mortos = emptyList(),
            tookMorto = listOf(false, false),
        )
        val lance = CanastraMove.Meld(sequencia)

        assertTrue(
            CanastraGame.applyMove(state, lance) is MoveResult.Illegal,
            "baixar a mão inteira sem canastra devia ser recusado",
        )
        assertTrue(lance !in CanastraGame.legalMoves(state), "e nem devia ser oferecido")

        // Com uma canastra na mesa o mesmo lance passa: aí é bater de verdade.
        val comCanastra = state.copy(melds = listOf(listOf(Meld(List(7) { carta(Rank.KING, Suit.HEARTS) })), emptyList()))
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

    /**
     * A mesma mão pode marcar a batida duas vezes: uma ao pegar o morto, outra ao bater de
     * vez depois. É por isso que o bônus é contado por evento, e não por mão.
     */
    @Test
    fun `pegar o morto e depois bater na mesma mao marca a batida duas vezes`() {
        val sequencia = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS))
        val filler = List(13) { carta(Rank.KING, Suit.DIAMONDS) }
        val inicial = novo(seats = 2).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(sequencia, emptyList()),
            mortos = listOf(filler),
            tookMorto = listOf(false, false),
        )
        val depoisDoMorto = CanastraGame.applyOrThrow(inicial, CanastraMove.Meld(sequencia))
        assertEquals(1, depoisDoMorto.batidas[0], "primeira batida: pegou o morto")

        // Simula ter jogado o resto da mão até sobrar uma carta, com canastra já na mesa —
        // o que importa aqui é só a segunda batida, não como se chegou até ela.
        val quaseLa = depoisDoMorto.copy(
            hands = listOf(listOf(carta(Rank.SEVEN, Suit.CLUBS)), emptyList()),
            melds = listOf(listOf(Meld(List(7) { carta(Rank.QUEEN, Suit.SPADES) })), emptyList()),
            // Perto do alvo, para a mão fechar sem redistribuir — e dar para inspecionar o
            // resultado em vez de já estar olhando a mão seguinte.
            scores = listOf(CANASTRA_TARGET - 1, 0),
        )
        val final = CanastraGame.applyOrThrow(quaseLa, CanastraMove.Discard(carta(Rank.SEVEN, Suit.CLUBS)))

        assertEquals(2, final.batidas[0], "segunda batida: bateu de vez")
        assertTrue(final.scores[0] >= CANASTRA_TARGET, "a partida devia ter fechado")
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
        for (seats in listOf(2, 3, 4)) {
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

    /**
     * A ordenação (que só ajuda a poda, não decide o lance) já tratava o curinga como último
     * recurso; o que faltava era o avaliador (que decide de verdade) parar de contar o
     * curinga na mão como dívida quase do tamanho de um três. Ver [CanastraEvaluator].
     */
    @Test
    fun `a ordenacao poe o curinga por ultimo com um so, e menos por ultimo com excesso`() {
        val curinga = carta(Rank.JOKER, Suit.HEARTS)
        val rei = carta(Rank.KING, Suit.CLUBS)
        val umSo = novo().copy(hands = listOf(listOf(curinga, rei), emptyList(), emptyList(), emptyList()))
        val ordemUmSo = CanastraOrdering.order(
            umSo,
            listOf(CanastraMove.Discard(curinga), CanastraMove.Discard(rei)),
        )
        assertEquals(
            CanastraMove.Discard(rei),
            ordemUmSo.first(),
            "com um curinga só na mão, descartar o rei devia vir na frente",
        )

        val segundo = carta(Rank.TWO, Suit.SPADES)
        val comExcesso = novo().copy(
            hands = listOf(listOf(curinga, segundo, rei), emptyList(), emptyList(), emptyList()),
        )
        val ordemExcesso = CanastraOrdering.order(
            comExcesso,
            listOf(CanastraMove.Discard(curinga), CanastraMove.Discard(rei)),
        )
        assertEquals(
            CanastraMove.Discard(rei),
            ordemExcesso.first(),
            "mesmo com excesso, um descarte comum ainda vem na frente do curinga",
        )
    }

    /**
     * A prova de ponta a ponta: com um curinga só na mão e uma carta claramente pior
     * disponível, a IA não descarta o curinga — nem no nível difícil, que é o que mais
     * enxerga.
     */
    @Test
    fun `a ia nao descarta o unico curinga quando ha carta pior para descartar`() {
        val curinga = carta(Rank.JOKER, Suit.HEARTS)
        val barata = carta(Rank.FOUR, Suit.CLUBS)
        // Mesa de dois, com a mão do adversário como veio da distribuição de verdade — uma
        // mão vazia ali criaria giros de "sem carta" que não têm nada a ver com o que este
        // teste quer medir, e afogariam a diferença de 2 pontos entre guardar o curinga (3)
        // e guardar a carta barata (5) em ruído de outra coisa.
        val base = novo(seats = 2)
        val state = base.copy(
            phase = CanastraPhase.PLAY,
            hands = base.hands.mapIndexed { index, mao -> if (index == 0) listOf(curinga, barata) else mao },
        )
        val escolhido = CanastraAi.chooseMove(state, Difficulty.HARD, seed = 1)
        assertEquals(
            CanastraMove.Discard(barata),
            escolhido,
            "a IA devia descartar a carta barata, guardando o curinga",
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
