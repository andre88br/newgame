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
import kotlin.test.assertFailsWith
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
            assertEquals(0, state.discard.size, "a mão abre com o lixo vazio: a primeira compra é do monte")
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
        val meioCaminho = CanastraGame.applyOrThrow(comVermelho, CanastraMove.DrawStock)
        assertEquals(
            base.redThrees[time] + 1,
            meioCaminho.redThrees[time],
            "o três vermelho devia ter ido para a mesa",
        )
        assertEquals(1, meioCaminho.pendingReplacements, "fica devendo uma compra de reposição")
        assertTrue(meioCaminho.hand(Seat.FIRST).none { isRedThree(it) }, "vermelho não fica na mão")

        // A reposição não vem sozinha: quem tirou o três vermelho compra de novo, à parte.
        val depois = CanastraGame.applyOrThrow(meioCaminho, CanastraMove.DrawStock)
        assertEquals(0, depois.pendingReplacements, "a reposição quita a dívida")
        assertTrue(
            carta(Rank.KING, Suit.CLUBS) in depois.hand(Seat.FIRST),
            "quem tira três vermelho compra outra carta no lugar",
        )
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

        val com = CanastraGame.scoreHand(comCanastra)[0].totalRodada
        val sem = CanastraGame.scoreHand(semCanastra)[0].totalRodada

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
        val pontos = CanastraGame.scoreHand(state)[0].totalRodada
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
        // A carta do topo (rei de copas) precisa formar jogo para poder ser pega — regra nova
        // —, então a mão ganha dama e valete de copas para fechar a sequência com ela.
        val rei = carta(Rank.KING, Suit.HEARTS)
        val state = novo().let {
            it.copy(
                phase = CanastraPhase.DRAW,
                discard = listOf(carta(Rank.THREE, Suit.SPADES), rei),
                hands = it.hands.mapIndexed { index, mao ->
                    if (index == 0) mao + listOf(carta(Rank.QUEEN, Suit.HEARTS), carta(Rank.JACK, Suit.HEARTS)) else mao
                },
            )
        }
        assertTrue(!state.discardBlocked, "o três preto embaixo não tranca nada")

        val antes = state.handSize(Seat.FIRST)
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.TakeDiscard)
        assertEquals(antes + 1, depois.handSize(Seat.FIRST), "só a carta devida chega na hora")
        assertTrue(depois.discard.isEmpty(), "o lixo fica vazio depois de pego")
        assertEquals(rei, depois.owedCard, "a carta do topo fica devida até entrar em jogo")

        // O resto do lixo (o três preto) só chega na mão quando a carta devida é baixada.
        val valete = carta(Rank.JACK, Suit.HEARTS)
        val dama = carta(Rank.QUEEN, Suit.HEARTS)
        val jogado = CanastraGame.applyOrThrow(depois, CanastraMove.Meld(listOf(valete, dama, rei)))
        assertNull(jogado.owedCard, "a carta devida foi baixada")
        assertEquals(antes - 1, jogado.handSize(Seat.FIRST), "sai o trio baixado, entra o resto do lixo")
        assertTrue(
            carta(Rank.THREE, Suit.SPADES) in jogado.hand(Seat.FIRST),
            "o resto do lixo (o três preto) chega na mão junto",
        )
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
        assertEquals(-100, CanastraGame.scoreHand(state)[0].totalRodada)
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

    /**
     * O curinga não tem posição fixa: se a carta nova abre um buraco mais perto do que a
     * ponta onde ele está, ele desce para lá — em vez de a carta ser recusada porque "não é
     * a ponta atual". É o bug relatado: `7 8 9 10♦ + curinga(valete)`, jogar um `5♦` devia
     * dar `5♦ + curinga(seis) + 7 8 9 10♦`, e não "essa carta não encaixa".
     */
    @Test
    fun `uma carta que abre buraco mais perto reposiciona o curinga, em vez de ser recusada`() {
        val naipe = Suit.DIAMONDS
        val curinga = carta(Rank.TWO, Suit.SPADES)
        val jogo = Meld(
            listOf(
                carta(Rank.SEVEN, naipe), carta(Rank.EIGHT, naipe),
                carta(Rank.NINE, naipe), carta(Rank.TEN, naipe), curinga,
            ),
        )
        assertEquals(carta(Rank.JACK, naipe), wildRepresents(jogo), "o curinga começa fazendo de valete")

        val cinco = carta(Rank.FIVE, naipe)
        val depois = extendMeld(jogo, cinco)

        assertEquals(6, depois?.cards?.size, "o jogo cresceu de cinco para seis")
        assertEquals(1, depois?.wilds?.size, "continua um curinga só")
        assertEquals(
            carta(Rank.SIX, naipe),
            depois?.let { wildRepresents(it) },
            "o curinga desceu de valete para seis, tapando o buraco que o cinco abriu",
        )
        assertTrue(cinco in (depois?.cards ?: emptyList()), "o cinco entrou no jogo")
        assertTrue(
            listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN).all { rank ->
                carta(rank, naipe) in (depois?.cards ?: emptyList())
            },
            "as cartas que já estavam no jogo continuam lá",
        )
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
    fun `sem ponta livre de verdade o curinga trocado ainda assim fica no jogo, depois do as`() {
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
        val antes = state.handSize(Seat.FIRST)
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.SwapWild(0, as_))
        val jogoDepois = depois.meldsOf(Seat.FIRST).first()

        assertEquals(completa.size + 1, jogoDepois.cards.size, "o jogo cresce mesmo sem ponta livre de verdade")
        assertEquals(1, jogoDepois.wilds.size, "o curinga continua no jogo, só encostado no fim")
        assertNull(wildRepresents(jogoDepois), "depois do ás não há carta nenhuma para o curinga representar")
        assertEquals(antes - 1, depois.handSize(Seat.FIRST), "a mão perde o ás e o curinga não volta")
        assertTrue(as_ !in depois.hand(Seat.FIRST), "o ás trocado saiu da mão")
        assertTrue(depois.hand(Seat.FIRST).none { isWild(it) }, "o curinga não volta para a mão")
    }

    // -------- pegar o lixo obriga baixar --------

    @Test
    fun `pegar o lixo e recusado se a carta do topo nao forma jogo nenhum`() {
        // Rei de copas sozinho: sem outra copa por perto na mão, e sem canastra para valer
        // trinca — não há como formar jogo nenhum com ele.
        val rei = carta(Rank.KING, Suit.HEARTS)
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.DRAW,
            discard = listOf(rei),
            hands = listOf(List(10) { carta(Rank.FOUR, Suit.CLUBS) }, emptyList(), emptyList(), emptyList()),
        )
        assertTrue(
            CanastraMove.TakeDiscard !in CanastraGame.legalMoves(state),
            "sem jogo possível com o rei de copas, pegar o lixo não é lance",
        )
        val resultado = CanastraGame.applyMove(state, CanastraMove.TakeDiscard)
        assertTrue(resultado is MoveResult.Illegal, "pegar o lixo sem jogo possível devia ser recusado")
    }

    @Test
    fun `pegar o lixo com jogo possivel deixa a carta devida, e so ela vira lance ate ser baixada`() {
        val rei = carta(Rank.KING, Suit.HEARTS)
        val dama = carta(Rank.QUEEN, Suit.HEARTS)
        val valete = carta(Rank.JACK, Suit.HEARTS)
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.DRAW,
            discard = listOf(rei),
            hands = listOf(
                listOf(dama, valete) + List(8) { carta(Rank.FOUR, Suit.CLUBS) },
                emptyList(), emptyList(), emptyList(),
            ),
        )
        assertTrue(
            CanastraMove.TakeDiscard in CanastraGame.legalMoves(state),
            "rei fecha sequência com a dama e o valete de copas na mão",
        )

        val depois = CanastraGame.applyOrThrow(state, CanastraMove.TakeDiscard)
        assertEquals(rei, depois.owedCard, "a carta do topo fica devida")

        val legais = CanastraGame.legalMoves(depois)
        assertTrue(legais.isNotEmpty(), "sempre existe pelo menos um jeito de cumprir a dívida")
        assertTrue(
            legais.all { move ->
                when (move) {
                    is CanastraMove.Meld -> rei in move.cards
                    is CanastraMove.SwapWild -> move.card == rei
                    else -> false
                }
            },
            "com carta devida, só valem lances que a incluam: $legais",
        )

        val cumpre = legais.filterIsInstance<CanastraMove.Meld>().first { rei in it.cards }
        val depoisDeBaixar = CanastraGame.applyOrThrow(depois, cumpre)
        assertNull(depoisDeBaixar.owedCard, "baixar a carta devida limpa a dívida")
        assertTrue(
            CanastraGame.legalMoves(depoisDeBaixar).any { it is CanastraMove.Discard },
            "depois de cumprida a dívida, a vez volta ao normal e dá para descartar",
        )
    }

    @Test
    fun `monte seco e lixo sem jogo possivel fecha a mao sozinha, sem travar`() {
        val rei = carta(Rank.KING, Suit.HEARTS)
        val state = novo(seats = 2).copy(
            phase = CanastraPhase.PLAY,
            stock = emptyList(),
            discard = emptyList(),
            hands = listOf(
                listOf(rei) + List(10) { carta(Rank.FOUR, Suit.CLUBS) },
                List(10) { carta(Rank.SEVEN, Suit.DIAMONDS) },
            ),
        )
        // O rei de copas não fecha jogo nenhum para quem recebe a vez (sem copa na mão, e
        // sem canastra para valer trinca), e o monte já secou: sem a correção em `settle`,
        // isto travaria a tela numa vez sem lance nenhum — o motor precisa reconhecer que
        // travou e fechar a mão sozinho, como já faz para o três preto.
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.Discard(rei))
        assertTrue(
            depois.phase != CanastraPhase.DRAW || CanastraGame.legalMoves(depois).isNotEmpty(),
            "a mão devia fechar sozinha, não travar sem lance nenhum: $depois",
        )
        // O número da mão e o placar guardado são o sinal que a tela usa para pausar no fim
        // de cada rodada — veja io.github.andre88br.newgame.core.engine.GameEntry.handOf.
        assertEquals(state.handNumber + 1, depois.handNumber, "uma mão fechou: a próxima já é a seguinte")
        assertTrue(depois.lastScores.isNotEmpty(), "o placar da mão que fechou fica guardado")
    }

    // -------- mínimo de abertura com 1500 pontos --------

    @Test
    fun `abaixo de 1500 o primeiro jogo da mao pode valer qualquer coisa`() {
        val baixo = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS))
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(baixo + List(10) { carta(Rank.KING, Suit.CLUBS) }, emptyList(), emptyList(), emptyList()),
        )
        val resultado = CanastraGame.applyMove(state, CanastraMove.Meld(baixo))
        assertTrue(resultado is MoveResult.Ok, "abaixo de 1500 não há mínimo de abertura")
    }

    @Test
    fun `com 1500 pontos, um jogo sozinho abaixo de 150 e recusado quando nao ha mais nada para somar`() {
        val baixo = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS))
        assertTrue(
            baixo.sumOf { cardValue(it) } < CANASTRA_OPENING_MIN_VALUE,
            "o jogo de teste precisa valer menos que o mínimo, senão o teste não prova nada",
        )
        // O resto da mão é só reis do mesmo naipe: sem canastra ainda, não formam trinca, e não
        // fecham outra sequência — não há como somar mais nada a este jogo nesta vez.
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(baixo + List(10) { carta(Rank.KING, Suit.CLUBS) }, emptyList(), emptyList(), emptyList()),
            scores = listOf(CANASTRA_OPENING_THRESHOLD, 0),
        )
        assertTrue(
            CanastraMove.Meld(baixo) !in CanastraGame.legalMoves(state),
            "jogo abaixo do mínimo nem aparece como lance oferecido quando não há como completar",
        )
        val resultado = CanastraGame.applyMove(state, CanastraMove.Meld(baixo))
        assertTrue(resultado is MoveResult.Illegal, "jogo abaixo do mínimo de abertura devia ser recusado")
    }

    @Test
    fun `o minimo de abertura pode somar mais de um jogo na mesma vez`() {
        // Três corridas de seis cartas (oito ao rei, dez pontos cada carta), uma por naipe:
        // sessenta pontos cada, curtas demais para virar canastra sozinhas. Cada uma sozinha
        // fica abaixo do mínimo de 150, duas juntas também (120), só as três juntas (180)
        // fecham — é a soma da vez, e não um jogo isolado, que decide.
        fun seisAltas(suit: Suit) = listOf(Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING)
            .map { carta(it, suit) }
        val primeira = seisAltas(Suit.CLUBS)
        val segunda = seisAltas(Suit.DIAMONDS)
        val terceira = seisAltas(Suit.SPADES)
        // Sobra na mão para nenhum lance de baixar esvaziá-la: sem canastra nenhuma formada
        // por corridas de seis cartas, esvaziar a mão bateria sem poder, o que travaria a
        // conta com "encurrala" antes mesmo de chegar na regra que este teste quer provar.
        val sobra = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS))
        assertTrue(
            primeira.sumOf { cardValue(it) } < CANASTRA_OPENING_MIN_VALUE,
            "cada corrida sozinha precisa ficar abaixo do mínimo, senão o teste não prova nada",
        )
        assertTrue(
            primeira.sumOf { cardValue(it) } + segunda.sumOf { cardValue(it) } < CANASTRA_OPENING_MIN_VALUE,
            "duas corridas juntas ainda precisam ficar abaixo do mínimo, senão o teste não prova a soma de três",
        )

        var state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(primeira + segunda + terceira + sobra, emptyList(), emptyList(), emptyList()),
            scores = listOf(CANASTRA_OPENING_THRESHOLD, 0),
        )

        // Sozinha, a primeira corrida não fecha o mínimo: a dupla ainda não tem o primeiro jogo.
        state = CanastraGame.applyOrThrow(state, CanastraMove.Meld(primeira))
        assertTrue(!state.firstMeldDone[state.teamOf(Seat.FIRST)], "uma corrida de 60 sozinha não fecha o mínimo de 150")
        // E descartar ainda não é lance: falta somar mais, e ainda há como.
        assertTrue(
            CanastraGame.legalMoves(state).none { it is CanastraMove.Discard },
            "com a abertura incompleta e mais jogo possível, descarte não deveria ser oferecido",
        )

        // A segunda corrida soma 120 no total: ainda não fecha.
        state = CanastraGame.applyOrThrow(state, CanastraMove.Meld(segunda))
        assertTrue(!state.firstMeldDone[state.teamOf(Seat.FIRST)], "60 + 60 ainda não fecha o mínimo de 150")

        // A terceira corrida soma 180 no total: agora fecha.
        state = CanastraGame.applyOrThrow(state, CanastraMove.Meld(terceira))
        assertTrue(state.firstMeldDone[state.teamOf(Seat.FIRST)], "60 + 60 + 60 fecha o mínimo de 150")
        assertTrue(
            CanastraGame.legalMoves(state).any { it is CanastraMove.Discard },
            "com a abertura completa, descarte volta a ser lance",
        )
    }

    @Test
    fun `um jogo de 150 ou mais passa com 1500 pontos, e marca o primeiro jogo feito`() {
        // Nenhuma sequência chega a 150 (o teto, do quatro ao ás, é 100) — só uma trinca
        // grande, e trinca pede canastra já feita. Oito ases (20 cada, 160 no total) bastam,
        // com uma canastra qualquer, noutro naipe, já pronta na mesa para liberar a trinca.
        val jaTemCanastra = Meld(CANASTRA_SEQUENCE_RANKS.take(CANASTRA_SIZE).map { carta(it, Suit.CLUBS) })
        val ases = listOf(Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS, Suit.SPADES)
            .flatMap { listOf(carta(Rank.ACE, it), carta(Rank.ACE, it)) }
        assertTrue(ases.sumOf { cardValue(it) } >= CANASTRA_OPENING_MIN_VALUE, "o jogo de teste precisa bater o mínimo")

        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(ases + List(5) { carta(Rank.KING, Suit.DIAMONDS) }, emptyList(), emptyList(), emptyList()),
            melds = listOf(listOf(jaTemCanastra), emptyList()),
            scores = listOf(CANASTRA_OPENING_THRESHOLD, 0),
        )
        val depois = CanastraGame.applyOrThrow(state, CanastraMove.Meld(ases))
        assertTrue(depois.firstMeldDone[state.teamOf(Seat.FIRST)], "o primeiro jogo da mão ficou marcado")
    }

    @Test
    fun `depois do primeiro jogo, extensao nao precisa bater o minimo de abertura`() {
        // Em ordem canônica (crescente): dez, valete, dama, rei — como um jogo já na mesa
        // precisa estar para `sequenceSpan` fazer sentido.
        val existente = Meld(
            listOf(
                carta(Rank.TEN, Suit.HEARTS), carta(Rank.JACK, Suit.HEARTS),
                carta(Rank.QUEEN, Suit.HEARTS), carta(Rank.KING, Suit.HEARTS),
            ),
        )
        val nove = carta(Rank.NINE, Suit.HEARTS)
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(listOf(nove) + List(10) { carta(Rank.KING, Suit.CLUBS) }, emptyList(), emptyList(), emptyList()),
            melds = listOf(listOf(existente), emptyList()),
            firstMeldDone = listOf(true, false),
            scores = listOf(CANASTRA_OPENING_THRESHOLD, 0),
        )
        // O nove sozinho vale só 5 pontos, bem abaixo do mínimo — é assim que o teste prova
        // que a extensão não passa pela checagem, e não que ela por acaso bateria o mínimo.
        val resultado = CanastraGame.applyMove(state, CanastraMove.Meld(listOf(nove), into = 0))
        assertTrue(resultado is MoveResult.Ok, "extensão não é o primeiro jogo, e o mínimo não vale para ela")
    }

    @Test
    fun `pegar o lixo e recusado se o unico jogo possivel nao bate o minimo de 150`() {
        // Quatro, cinco e seis de copas fecham sequência com o topo do lixo, mas valem só
        // 15 pontos — bem abaixo do que a dupla, já em 1500, deve no primeiro jogo da mão.
        val quatro = carta(Rank.FOUR, Suit.HEARTS)
        val cinco = carta(Rank.FIVE, Suit.HEARTS)
        val seis = carta(Rank.SIX, Suit.HEARTS)
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.DRAW,
            discard = listOf(seis),
            hands = listOf(
                listOf(quatro, cinco) + List(8) { carta(Rank.KING, Suit.CLUBS) },
                emptyList(), emptyList(), emptyList(),
            ),
            scores = listOf(CANASTRA_OPENING_THRESHOLD, 0),
        )
        assertTrue(
            CanastraMove.TakeDiscard !in CanastraGame.legalMoves(state),
            "o único jogo possível (4-5-6 de copas) não bate os 150: pegar o lixo não é lance",
        )
        val resultado = CanastraGame.applyMove(state, CanastraMove.TakeDiscard)
        assertTrue(resultado is MoveResult.Illegal, "pegar o lixo devia ser recusado")
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

    /**
     * `knownOpponentCards` só guarda cartas que alguém pegou do lixo — e o lixo já era
     * visível a todos antes de ser pego, então isso nunca foi segredo de ninguém. Redigir
     * essa memória para vazio (como o código fazia) não protegia informação nenhuma: só
     * apagava, da visão da própria IA, uma pista pública que ela tinha todo o direito de
     * usar — e derrubava de vez a heurística "não descarta perto do que o adversário pegou"
     * em [CanastraOrdering], que nunca via outra coisa que não fosse mapa vazio.
     */
    @Test
    fun `knownOpponentCards e publico e sobrevive a redacao`() {
        val carta = carta(Rank.SEVEN, Suit.DIAMONDS)
        val state = novo().copy(knownOpponentCards = mapOf(1 to listOf(carta)))
        val visto = CanastraGame.redactFor(state, Seat.FIRST)
        assertEquals(
            mapOf(1 to listOf(carta)),
            visto.knownOpponentCards,
            "cartas pegas do lixo já eram públicas antes de serem pegas",
        )
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
     * "Próximo a jogar" é quem vai ter a chance de aproveitar o descarte, e por regra do
     * jogo isso é sempre `turn + 1` (ver `proximoTurno` em `Canastra.kt`) — nunca `turn - 1`,
     * que é quem *já jogou*. Com quatro cadeiras a fórmula errada ainda acertava a dupla
     * certa por acidente (times alternam e ambas as vizinhas são inimigas), mas mirava no
     * jogador errado dentro dela; com uma mesa de três, cada cadeira é seu próprio time e o
     * erro troca de inimigo de vez. Este teste usa três cadeiras exatamente para expor isso:
     * só a cadeira 1 (a que joga a seguir) tem memória de lixo; a cadeira 2 (a anterior) tem
     * memória de uma carta que bateria na regra se fosse ela a considerada.
     */
    @Test
    fun `a ordenacao evita o descarte perto do que o PROXIMO adversario pegou, nao o anterior`() {
        val descartada = carta(Rank.EIGHT, Suit.HEARTS)
        val longe = carta(Rank.KING, Suit.CLUBS)
        val base = novo(seats = 3)
        val state = base.copy(
            turn = Seat.FIRST,
            phase = CanastraPhase.PLAY,
            hands = base.hands.mapIndexed { index, mao -> if (index == 0) listOf(descartada, longe) else mao },
            // cadeira 1 é quem joga a seguir; pegou uma carta pertinho da que está para ser
            // descartada. Cadeira 2 (a anterior) pegou a mesma carta — se a ordenação ainda
            // mirasse nela por engano, o teste não distinguiria os dois casos.
            knownOpponentCards = mapOf(1 to listOf(carta(Rank.SEVEN, Suit.HEARTS))),
        )
        val ordem = CanastraOrdering.order(
            state,
            listOf(CanastraMove.Discard(descartada), CanastraMove.Discard(longe)),
        )
        assertEquals(
            CanastraMove.Discard(longe),
            ordem.first(),
            "a carta perto do que a cadeira 1 pegou devia vir por último, não primeiro",
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

    /**
     * A dupla já tem canastra limpa de paus — sete ao ás, o curinga fazendo de dama — e a mão
     * guarda a dama exata. Trocar o curinga não custa carta nenhuma que já não fosse gasta e
     * sempre melhora o jogo: nem o sorteio de erro do nível fácil devia escolher outra coisa
     * no lugar. Roda muitas sementes de propósito — antes da correção, cerca de trinta por
     * cento delas escolhiam qualquer lance legal ao acaso, inclusive descartar a dama.
     */
    @Test
    fun `a ia troca o curinga de graca em vez de descarta-lo, mesmo no nivel facil`() {
        val naipe = Suit.CLUBS
        val damaDePaus = carta(Rank.QUEEN, naipe)
        val jogo = Meld(
            listOf(
                carta(Rank.SEVEN, naipe), carta(Rank.EIGHT, naipe), carta(Rank.NINE, naipe),
                carta(Rank.TEN, naipe), carta(Rank.JACK, naipe), carta(Rank.JOKER, Suit.HEARTS),
                carta(Rank.KING, naipe), carta(Rank.ACE, naipe),
            ),
        )
        assertEquals(damaDePaus, wildRepresents(jogo), "o curinga está fazendo de dama")

        val base = novo(seats = 2)
        val state = base.copy(
            phase = CanastraPhase.PLAY,
            melds = listOf(listOf(jogo), emptyList()),
            hands = base.hands.mapIndexed { index, mao ->
                if (index == 0) listOf(damaDePaus, carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.SPADES)) else mao
            },
        )

        for (dificuldade in listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD)) {
            repeat(20) { semente ->
                val escolhido = CanastraAi.chooseMove(state, dificuldade, seed = semente.toLong())
                assertEquals(
                    CanastraMove.SwapWild(0, damaDePaus),
                    escolhido,
                    "$dificuldade, semente $semente: a troca não podia perder para outro lance",
                )
            }
        }
    }

    /**
     * A regra da casa: cada carta além da sétima numa canastra **limpa** rende mais cem
     * pontos ([Meld.score], somado por `scoreHand` no fim da mão). O avaliador da IA
     * reimplementava a pontuação em vez de acompanhá-la e tinha esquecido justamente esta
     * parcela — crescer uma canastra já pronta valia, para ela, só o valor solto da carta
     * (dez pontos por uma dama). Era a raiz de a IA descartar uma carta que encaixava: a
     * diferença entre encaixar e jogar fora ficava pequena demais para sobreviver ao ruído
     * da amostragem.
     */
    @Test
    fun `o avaliador paga os cem por carta alem da setima numa canastra limpa`() {
        val naipe = Suit.CLUBS
        val naturais = listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.KING, Rank.ACE)
            .map { carta(it, naipe) }
        val curinga = carta(Rank.JOKER, Suit.HEARTS)
        val dama = carta(Rank.QUEEN, naipe)

        val comOito = Meld(checkNotNull(asSequence(naturais + curinga)))
        val comNove = Meld(checkNotNull(asSequence(naturais + dama + curinga)))
        assertTrue(comOito.isClean && comNove.isClean, "coringa não suja canastra")
        assertEquals(8, comOito.cards.size)
        assertEquals(9, comNove.cards.size)

        // Mãos vazias de propósito: isola o ganho de crescer o jogo do bônus de promessa
        // que as cartas ainda na mão dariam.
        val avaliador = CanastraEvaluatorImpl()
        val base = novo(seats = 2).copy(hands = listOf(emptyList(), emptyList()))
        val antes = avaliador.evaluate(base.copy(melds = listOf(listOf(comOito), emptyList())), Seat.FIRST)
        val depois = avaliador.evaluate(base.copy(melds = listOf(listOf(comNove), emptyList())), Seat.FIRST)

        assertTrue(
            depois - antes >= 100 + cardValue(dama),
            "crescer a canastra limpa tem que valer os cem da regra mais a carta, e valeu ${depois - antes}",
        )
    }

    /**
     * Tirar o curinga do meio não vale só a carta que entrou: ele sai do buraco que estava
     * tapando, vai para uma ponta e passa a representar **outro** valor — e é esse valor novo
     * que pode ser trocado de novo, fazendo a canastra crescer mais uma vez.
     *
     * Aqui a sequência vai do sete ao ás com o curinga fazendo de dama. Não há ponta livre
     * por cima (o ás fecha a escala), então ao entrar a dama de verdade o curinga desce para
     * **antes** do sete e vira um seis; com o seis natural na mão, uma segunda troca leva o
     * jogo a dez cartas. É essa cadeia que a IA precisa enxergar para não largar a primeira
     * carta no lixo.
     */
    @Test
    fun `trocar o curinga o reposiciona e abre uma segunda troca, crescendo a canastra de novo`() {
        val naipe = Suit.CLUBS
        val naturais = listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.KING, Rank.ACE)
            .map { carta(it, naipe) }
        val curinga = carta(Rank.JOKER, Suit.HEARTS)
        val dama = carta(Rank.QUEEN, naipe)
        val seis = carta(Rank.SIX, naipe)

        val antes = Meld(checkNotNull(asSequence(naturais + curinga)))
        assertEquals(8, antes.cards.size)
        assertEquals(dama, wildRepresents(antes), "antes da troca o curinga faz de dama")

        val state = novo(seats = 2).copy(
            phase = CanastraPhase.PLAY,
            melds = listOf(listOf(antes), emptyList()),
            hands = listOf(listOf(dama, seis), emptyList()),
        )
        val depoisDaPrimeira = CanastraGame.applyOrThrow(state, CanastraMove.SwapWild(0, dama))
        val comNove = depoisDaPrimeira.meldsOf(Seat.FIRST).first()

        assertEquals(9, comNove.cards.size, "o jogo cresceu com a dama")
        assertEquals(seis, wildRepresents(comNove), "o curinga desceu para a outra ponta e agora faz de seis")

        // E é isto que a primeira troca destravou: o curinga virou um seis, então o seis
        // natural da mão pode tomar o lugar dele e o jogo cresce outra vez.
        val comDez = CanastraGame.applyOrThrow(depoisDaPrimeira, CanastraMove.SwapWild(0, seis))
            .meldsOf(Seat.FIRST).first()
        assertEquals(10, comDez.cards.size, "a segunda troca leva a canastra a dez cartas")
        assertTrue(comDez.isClean, "coringa dentro não suja: a canastra segue limpa em dez cartas")
    }

    /**
     * Repartir o mesmo naipe em duas sequências é pior em duplas do que jogando individual:
     * o parceiro pode estar segurando exatamente as cartas que ligariam as duas pontas num
     * jogo só, e a mão dele é oculta — a IA não tem como conferir antes de decidir. Sozinha
     * não há parceiro para esperar, e o que sobra é só a perda de ter duas sequências curtas
     * onde cabia uma longa.
     */
    @Test
    fun `separar o naipe custa mais em duplas do que jogando individual`() {
        val naipe = Suit.CLUBS
        val seguidas = listOf(Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE)
            .map { carta(it, naipe) }
        val juntas = listOf(Meld(seguidas))
        val separadas = listOf(Meld(seguidas.take(3)), Meld(seguidas.drop(3)))

        val avaliador = CanastraEvaluatorImpl()
        fun folga(seats: Int): Int {
            val base = novo(seats = seats).copy(hands = List(seats) { emptyList() })
            val vazio = List(base.teams) { emptyList<Meld>() }
            val comJuntas = avaliador.evaluate(base.copy(melds = vazio.updatedFirst(juntas)), Seat.FIRST)
            val comSeparadas = avaliador.evaluate(base.copy(melds = vazio.updatedFirst(separadas)), Seat.FIRST)
            return comJuntas - comSeparadas
        }

        val emDuplas = folga(seats = 4)
        val individual = folga(seats = 3)
        assertTrue(individual > 0, "separar o naipe já é ruim sozinho, e custou $individual")
        assertTrue(
            emDuplas > individual,
            "em duplas devia custar mais caro que individual: $emDuplas contra $individual",
        )
    }

    private fun List<List<Meld>>.updatedFirst(jogos: List<Meld>): List<List<Meld>> =
        mapIndexed { index, atual -> if (index == 0) jogos else atual }

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

    // -------- refatoração: belowOpeningThreshold, checkNotNull diagnóstico, checkPendingDiscard imutável --------

    /**
     * A unificação da checagem `scores < 1500` em `belowOpeningThreshold` removeu, dentro de
     * `isOpeningPathPreserved`, uma linha que a análise apontou como redundante — coberta pela
     * checagem seguinte, que já fazia a mesma pergunta de outro jeito. Este teste crava o
     * limiar exatamente nos três pontos que provariam essa análise errada, se ela estivesse
     * errada: um ponto abaixo de 1500, em cima, e um ponto acima.
     */
    @Test
    fun `o limiar de 1500 continua exatamente no mesmo lugar depois da unificacao em belowOpeningThreshold`() {
        val baixo = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.FIVE, Suit.HEARTS), carta(Rank.SIX, Suit.HEARTS))
        assertTrue(
            baixo.sumOf { cardValue(it) } < CANASTRA_OPENING_MIN_VALUE,
            "o jogo de teste precisa valer menos que o mínimo, senão o teste não prova nada",
        )
        fun estadoCom(pontos: Int) = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(baixo + List(10) { carta(Rank.KING, Suit.CLUBS) }, emptyList(), emptyList(), emptyList()),
            scores = listOf(pontos, 0),
        )
        val lance = CanastraMove.Meld(baixo)

        assertTrue(
            CanastraGame.applyMove(estadoCom(CANASTRA_OPENING_THRESHOLD - 1), lance) is MoveResult.Ok,
            "com 1499 pontos o limiar ainda não vale: o jogo baixo devia passar",
        )
        assertTrue(
            CanastraGame.applyMove(estadoCom(CANASTRA_OPENING_THRESHOLD), lance) is MoveResult.Illegal,
            "com exatamente 1500 pontos o limiar já vale: o jogo baixo devia ser recusado",
        )
        assertTrue(
            CanastraGame.applyMove(estadoCom(CANASTRA_OPENING_THRESHOLD + 1), lance) is MoveResult.Illegal,
            "com 1501 pontos o limiar continua valendo — não é só o valor exato de 1500 que dispara a regra",
        )
    }

    /**
     * `applyKnownLegal` só é chamado depois que `applyMove` já validou o lance — é o contrato
     * do motor. Os cinco `!!` que viraram `checkNotNull`/`error` nesta rodada continuam sendo
     * crash em caso de bug de validação, só que agora com mensagem diagnosticável em vez de um
     * `NullPointerException` mudo. Este teste contorna `applyMove` de propósito — chamando
     * `applyKnownLegal` direto com um `Meld` que não fecha jogo nenhum — para cravar que o
     * crash continua acontecendo, e que ele explica o que quebrou.
     */
    @Test
    fun `applyKnownLegal com um meld invalido crava a mensagem diagnostica do checkNotNull`() {
        val soltas = listOf(carta(Rank.FOUR, Suit.HEARTS), carta(Rank.NINE, Suit.SPADES))
        val state = novo(seats = 4).copy(
            phase = CanastraPhase.PLAY,
            hands = listOf(soltas + List(9) { carta(Rank.KING, Suit.CLUBS) }, emptyList(), emptyList(), emptyList()),
        )
        val lanceInvalido = CanastraMove.Meld(soltas)
        assertTrue(
            CanastraGame.applyMove(state, lanceInvalido) is MoveResult.Illegal,
            "confirma que o lance é mesmo ilegal pela porta normal — o teste é sobre contornar essa porta",
        )

        val erro = assertFailsWith<IllegalStateException> {
            CanastraGame.applyKnownLegal(state, lanceInvalido)
        }
        assertTrue(
            erro.message.orEmpty().contains("não forma um jogo válido"),
            "a mensagem devia explicar o que quebrou, não só estourar nulo: ${erro.message}",
        )
    }

    /**
     * O maior ponto de risco da rodada: `checkPendingDiscard` deixou de mutar `mao`,
     * `vermelhos` e `memoria` por referência e passou a devolver um `PendingDiscardOutcome`
     * imutável. Este teste percorre o fluxo de ponta a ponta: o time pega o lixo com uma carta
     * devida e, atrás dela, uma pendência de duas cartas (um três vermelho e uma carta comum)
     * que ainda não pode entrar na mão porque a abertura não foi feita. Só quando a jogada
     * seguinte fecha a abertura (150 pontos, nesta mesma tacada, incluindo a própria carta
     * devida) é que a pendência devia ser entregue — o vermelho somando ao contador sem entrar
     * na mão, a carta comum indo para a mão e ficando registrada em `knownOpponentCards`.
     */
    @Test
    fun `pendencia do lixo com um vermelho e uma carta comum e entregue quando o meld fecha a abertura`() {
        val tresVermelho = carta(Rank.THREE, Suit.HEARTS)
        val pendente = carta(Rank.SEVEN, Suit.DIAMONDS)
        // Oito ases (20 pontos cada = 160) fecham os 150 da abertura numa jogada só, e o
        // primeiro deles é o que vai para o lixo como a carta devida.
        val todosAses = Suit.entries.flatMap { listOf(carta(Rank.ACE, it), carta(Rank.ACE, it)) }
        val devidaAs = todosAses.first()
        val asesNaMao = todosAses - devidaAs
        val fillers = List(2) { carta(Rank.NINE, Suit.CLUBS) }
        // Uma trinca de ás precisa de canastra já feita — como qualquer trinca na canastra.
        val jaTemCanastra = Meld(List(7) { carta(Rank.KING, Suit.DIAMONDS) })

        val inicial = novo(seats = 4).copy(
            phase = CanastraPhase.DRAW,
            scores = listOf(CANASTRA_OPENING_THRESHOLD, 0),
            firstMeldDone = listOf(false, false),
            openingProgress = listOf(0, 0),
            redThrees = listOf(0, 0),
            melds = listOf(listOf(jaTemCanastra), emptyList()),
            discard = listOf(pendente, tresVermelho, devidaAs),
            hands = listOf(asesNaMao + fillers, emptyList(), emptyList(), emptyList()),
            turn = Seat.FIRST,
        )

        val depoisPegar = CanastraGame.applyOrThrow(inicial, CanastraMove.TakeDiscard)
        assertEquals(devidaAs, depoisPegar.owedCard, "a carta do topo fica devida")
        assertEquals(
            listOf(pendente, tresVermelho),
            depoisPegar.pendingDiscard,
            "o resto do lixo fica pendente até a abertura se completar",
        )

        val depoisBaixar = CanastraGame.applyOrThrow(depoisPegar, CanastraMove.Meld(todosAses))

        assertTrue(depoisBaixar.firstMeldDone[0], "160 pontos nesta jogada fecham a abertura")
        assertNull(depoisBaixar.owedCard, "a carta devida foi cumprida pelo próprio meld")
        assertTrue(depoisBaixar.pendingDiscard.isEmpty(), "a pendência foi entregue, não ficou presa")

        assertEquals(1, depoisBaixar.redThrees[0], "o três vermelho pendente somou ao contador")
        assertTrue(tresVermelho !in depoisBaixar.hand(Seat.FIRST), "o três vermelho nunca entra na mão")
        assertEquals(
            fillers + listOf(pendente),
            depoisBaixar.hand(Seat.FIRST),
            "a mão final é só o que sobrou dos ases mais a carta comum entregue pela pendência",
        )
        assertEquals(
            listOf(devidaAs, pendente),
            depoisBaixar.knownOpponentCards[Seat.FIRST.index],
            "a carta devida (pega do lixo) e a carta comum da pendência ficam conhecidas dos adversários",
        )
    }
}
