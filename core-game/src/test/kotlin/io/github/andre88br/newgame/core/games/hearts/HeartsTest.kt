package io.github.andre88br.newgame.core.games.hearts

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
import kotlin.test.assertTrue

/**
 * Copas tem poucas regras e todas com exceção: segue-se o naipe, **menos** quem não o tem;
 * não se puxa copas, **menos** depois de ela sair; não se descarta ponto na primeira vaza,
 * **menos** quem só tem ponto. É nas exceções que uma implementação trava a mão, e é isso
 * que estes testes procuram.
 */
class HeartsTest {

    private fun novo(seed: Long = 7) = HeartsGame.initialState(MatchConfig(seed, seats = HEARTS_SEATS))

    /** Leva a partida do começo até as vazas, escolhendo sempre o primeiro lance legal. */
    private fun depoisDoPasse(seed: Long = 7): HeartsState {
        var state = novo(seed)
        var guard = 0
        while (state.phase == HeartsPhase.PASSING && guard++ < 40) {
            state = HeartsGame.applyOrThrow(state, HeartsGame.legalMoves(state).first())
        }
        return state
    }

    // -------- a distribuição --------

    @Test
    fun `a mesa e de quatro, com treze cartas cada`() {
        val state = novo()
        assertEquals(HEARTS_SEATS, state.hands.size)
        assertTrue(state.hands.all { it.size == HEARTS_HAND_SIZE }, "mão com tamanho errado")
        assertEquals(52, state.hands.flatten().distinct().size, "carta repetida entre as mãos")
    }

    @Test
    fun `a mesma semente reparte a mesma mesa`() {
        assertEquals(novo(42).hands, novo(42).hands)
        assertTrue(novo(1).hands != novo(2).hands, "sementes diferentes deviam dar mesas diferentes")
    }

    // -------- o passe --------

    @Test
    fun `a primeira mao passa tres cartas para a esquerda`() {
        val state = novo()
        assertEquals(HeartsPhase.PASSING, state.phase)
        assertEquals(PassDirection.LEFT, state.passDirection)
        assertEquals(Seat(1), PassDirection.LEFT.receiver(Seat(0), HEARTS_SEATS))
    }

    /** O rodízio existe para o passe não virar rotina; a quarta mão joga sem passe. */
    @Test
    fun `o rodizio do passe da a volta em quatro maos`() {
        assertEquals(PassDirection.LEFT, PassDirection.forHand(0))
        assertEquals(PassDirection.RIGHT, PassDirection.forHand(1))
        assertEquals(PassDirection.ACROSS, PassDirection.forHand(2))
        assertEquals(PassDirection.NONE, PassDirection.forHand(3))
        assertEquals(PassDirection.LEFT, PassDirection.forHand(4), "o rodízio recomeça")
    }

    @Test
    fun `cada cadeira passa exatamente tres cartas, e a mao volta a treze`() {
        val state = depoisDoPasse()
        assertEquals(HeartsPhase.PLAYING, state.phase)
        assertTrue(
            state.hands.all { it.size == HEARTS_HAND_SIZE },
            "depois do passe a mão volta a treze: ${state.hands.map { it.size }}",
        )
        assertEquals(52, state.hands.flatten().distinct().size, "o passe duplicou ou perdeu carta")
    }

    /**
     * O passe só acontece quando as quatro cadeiras escolheram. Entregar antes disso daria a
     * quem ainda não escolheu uma informação que ele não devia ter.
     */
    @Test
    fun `as cartas so trocam de dono quando todos escolheram`() {
        var state = novo()
        // Uma cadeira inteira escolhe suas três cartas.
        repeat(HEARTS_PASS_SIZE) {
            state = HeartsGame.applyOrThrow(state, HeartsGame.legalMoves(state).first())
        }
        assertEquals(HeartsPhase.PASSING, state.phase, "a mão não podia ter começado")
        assertEquals(
            HEARTS_HAND_SIZE - HEARTS_PASS_SIZE,
            state.handSize(Seat.FIRST),
            "quem escolheu fica com dez até o passe acontecer",
        )
        assertEquals(HEARTS_HAND_SIZE, state.handSize(Seat(1)), "quem não escolheu não recebeu nada")
    }

    // -------- as vazas --------

    @Test
    fun `quem tem o dois de paus abre, e abre com ele`() {
        val state = depoisDoPasse()
        assertTrue(TWO_OF_CLUBS in state.hand(state.turn), "quem sai precisa ter o 2 de paus")
        assertEquals(
            listOf(HeartsMove(TWO_OF_CLUBS)),
            HeartsGame.legalMoves(state),
            "a primeira carta da mão é o 2 de paus, e mais nada",
        )
    }

    @Test
    fun `servir o naipe pedido e obrigatorio`() {
        var state = depoisDoPasse()
        state = HeartsGame.applyOrThrow(state, HeartsMove(TWO_OF_CLUBS))

        val mao = state.hand(state.turn)
        val paus = mao.filter { it.suit == Suit.CLUBS }
        if (paus.isEmpty()) return // esta mesa não serve ao teste; o das mil partidas cobre

        val legais = HeartsGame.legalMoves(state).map { it.card }
        assertEquals(paus.toSet(), legais.toSet(), "com paus na mão, só paus podem sair")

        val fora = mao.firstOrNull { it.suit != Suit.CLUBS } ?: return
        val recusa = HeartsGame.applyMove(state, HeartsMove(fora))
        assertTrue(recusa is MoveResult.Illegal, "descartar com naipe na mão devia ser recusado")
    }

    @Test
    fun `na primeira vaza ninguem descarta ponto`() {
        var state = depoisDoPasse()
        state = HeartsGame.applyOrThrow(state, HeartsMove(TWO_OF_CLUBS))

        // Enquanto a primeira vaza corre, nenhum lance legal vale ponto — a não ser que a
        // mão só tenha ponto, exceção que este teste também aceita.
        while (state.trick.isNotEmpty()) {
            val mao = state.hand(state.turn)
            val legais = HeartsGame.legalMoves(state).map { it.card }
            val soTemPonto = mao.all { penaltyOf(it) > 0 }
            if (!soTemPonto) {
                assertTrue(
                    legais.none { penaltyOf(it) > 0 },
                    "ponto liberado na primeira vaza: $legais",
                )
            }
            state = HeartsGame.applyOrThrow(state, HeartsGame.legalMoves(state).first())
        }
    }

    @Test
    fun `nao se puxa copas antes de ela sair`() {
        val state = depoisDoPasse()
        assertTrue(!state.heartsBroken, "copas começa travada")

        // Numa mão com carta fora de copas, puxar copas não pode estar entre os lances.
        val comCopasEOutras = state.copy(
            trick = emptyList(),
            hands = List(HEARTS_SEATS) {
                listOf(Card(Rank.ACE, Suit.HEARTS), Card(Rank.FIVE, Suit.CLUBS))
            },
        )
        val legais = HeartsGame.legalMoves(comCopasEOutras).map { it.card }
        assertEquals(listOf(Card(Rank.FIVE, Suit.CLUBS)), legais, "copas travada devia estar fora")
    }

    @Test
    fun `so com copas na mao, copas pode ser puxada`() {
        val state = depoisDoPasse().copy(
            trick = emptyList(),
            heartsBroken = false,
            hands = List(HEARTS_SEATS) { listOf(Card(Rank.ACE, Suit.HEARTS)) },
        )
        assertEquals(
            listOf(Card(Rank.ACE, Suit.HEARTS)),
            HeartsGame.legalMoves(state).map { it.card },
            "quem só tem copas joga copas: a mão não pode travar",
        )
    }

    // -------- a contagem --------

    @Test
    fun `cada copas vale um e a dama de espadas vale treze`() {
        assertEquals(1, penaltyOf(Card(Rank.TWO, Suit.HEARTS)))
        assertEquals(1, penaltyOf(Card(Rank.ACE, Suit.HEARTS)))
        assertEquals(13, penaltyOf(QUEEN_OF_SPADES))
        assertEquals(0, penaltyOf(Card(Rank.ACE, Suit.SPADES)))
        assertEquals(0, penaltyOf(Card(Rank.KING, Suit.DIAMONDS)))

        val total = io.github.andre88br.newgame.core.cards.standardDeck().sumOf { penaltyOf(it) }
        assertEquals(HEARTS_MOON, total, "a mão inteira vale 26 pontos, e é o que faz correr todas")
    }

    @Test
    fun `a vaza vai para a carta mais alta do naipe pedido`() {
        var state = depoisDoPasse()
        state = HeartsGame.applyOrThrow(state, HeartsMove(TWO_OF_CLUBS))
        val puxou = state.leader!!

        val naVaza = mutableListOf<PlayedCard>()
        while (state.trick.isNotEmpty()) {
            naVaza += PlayedCard(state.turn, HeartsGame.legalMoves(state).first().card)
            state = HeartsGame.applyOrThrow(state, HeartsGame.legalMoves(state).first())
        }

        val comPuxada = listOf(PlayedCard(puxou, TWO_OF_CLUBS)) + naVaza
        val doNaipe = comPuxada.filter { it.card.suit == Suit.CLUBS }
        assertEquals(
            doNaipe.maxBy { it.card.rank.order }.seat,
            state.turn,
            "quem levou a vaza é quem puxa a seguinte",
        )
    }

    /**
     * Correr todas inverte a mão: quem faz os 26 não leva nenhum, e os outros levam tudo.
     * É a única jogada em que fazer ponto é bom, e sem ela mão de carta alta seria só azar.
     */
    @Test
    fun `correr todas manda os 26 pontos para os outros`() {
        val state = novo().copy(
            phase = HeartsPhase.PLAYING,
            hands = List(HEARTS_SEATS) { emptyList() },
            handPoints = listOf(HEARTS_MOON, 0, 0, 0),
            scores = listOf(0, 0, 0, 0),
        )
        // Fecha a mão pela mesma porta que o jogo usa: a última carta da última vaza.
        val fechada = fecharMaoPor(state, quemCorreu = 0)

        assertEquals(0, fechada.scores[0], "quem correu todas não leva ponto")
        assertTrue(
            (1 until HEARTS_SEATS).all { fechada.scores[it] == HEARTS_MOON },
            "os outros três levam 26 cada: ${fechada.scores}",
        )
    }

    /** Chama o fechamento de mão do próprio motor, sem duplicar a regra no teste. */
    private fun fecharMaoPor(state: HeartsState, quemCorreu: Int): HeartsState {
        val umaCarta = Card(Rank.THREE, Suit.CLUBS)
        val comUltima = state.copy(
            hands = List(HEARTS_SEATS) { if (it == quemCorreu) listOf(umaCarta) else emptyList() },
            trick = (0 until HEARTS_SEATS - 1).map { PlayedCard(Seat(it + 1), Card(Rank.entries[it], Suit.DIAMONDS)) },
            turn = Seat(quemCorreu),
            handPoints = state.handPoints,
        )
        return HeartsGame.applyKnownLegal(comUltima, HeartsMove(umaCarta))
    }

    // -------- a partida inteira --------

    @Test
    fun `uma partida inteira termina, e quem tem menos pontos vence`() {
        var state = novo(seed = 2026)
        var guard = 0
        while (!HeartsGame.outcome(state).isOver && guard++ < 4_000) {
            val legais = HeartsGame.legalMoves(state)
            assertTrue(legais.isNotEmpty(), "partida travou no lance ${state.ply}: $state")
            state = HeartsGame.applyOrThrow(state, legais.first())
        }

        val resultado = HeartsGame.outcome(state)
        assertTrue(resultado.isOver, "a partida devia ter acabado em $guard lances")
        assertTrue(
            state.scores.any { it >= HEARTS_TARGET_SCORE },
            "a partida só acaba quando alguém chega a $HEARTS_TARGET_SCORE: ${state.scores}",
        )
        val vencedor = (resultado as Outcome.Win).seat
        assertEquals(
            state.scores.min(),
            state.scores[vencedor.index],
            "vence quem tem menos pontos: ${state.scores}",
        )
    }

    @Test
    fun `nenhuma mao perde nem inventa carta ao longo da partida`() {
        var state = novo(seed = 55)
        var guard = 0
        while (!HeartsGame.outcome(state).isOver && guard++ < 1_500) {
            if (state.phase == HeartsPhase.PLAYING) {
                val naMesa = state.hands.sumOf { it.size } + state.trick.size
                assertTrue(naMesa <= 52, "apareceu carta a mais no lance ${state.ply}")
                assertEquals(
                    state.hands.flatten().size,
                    state.hands.flatten().distinct().size,
                    "carta repetida entre as mãos no lance ${state.ply}",
                )
            }
            state = HeartsGame.applyOrThrow(state, HeartsGame.legalMoves(state).first())
        }
    }

    // -------- informação oculta --------

    @Test
    fun `a mao dos outros nao vaza para quem olha`() {
        val state = depoisDoPasse()
        val visto = HeartsGame.redactFor(state, Seat.FIRST)

        assertEquals(state.hand(Seat.FIRST), visto.hand(Seat.FIRST), "a própria mão fica")
        for (index in 1 until HEARTS_SEATS) {
            val outra = visto.hand(Seat(index))
            assertTrue(outra.all { it.isHidden }, "vazou carta da cadeira $index")
            assertEquals(
                state.handSize(Seat(index)),
                outra.size,
                "a contagem de cartas precisa continuar certa",
            )
        }
        assertTrue(HeartsGame.hasHiddenInformation, "copas é jogo de informação oculta")
    }

    @Test
    fun `a maquina joga so com o que enxerga`() {
        var state = depoisDoPasse(seed = 11)
        var guard = 0
        while (!HeartsGame.outcome(state).isOver && guard++ < 60) {
            val comoEleVe = HeartsGame.redactFor(state, state.turn)
            val escolhido = HeartsAi.chooseMove(comoEleVe, Difficulty.MEDIUM, seed = guard.toLong())
            assertTrue(escolhido != null, "a IA não escolheu lance no ply ${state.ply}")
            assertTrue(
                escolhido in HeartsGame.legalMoves(state),
                "a IA escolheu lance ilegal: ${escolhido.describe()}",
            )
            state = HeartsGame.applyOrThrow(state, escolhido)
        }
    }

    /** O mundo inventado precisa respeitar as contagens, ou a busca resolve uma mesa falsa. */
    @Test
    fun `o mundo sorteado devolve a cada um o numero certo de cartas`() {
        val state = depoisDoPasse(seed = 3)
        val visto = HeartsGame.redactFor(state, Seat.FIRST)
        val mundo = completeHearts(visto, io.github.andre88br.newgame.core.engine.Rng.seeded(9))

        for (index in 0 until HEARTS_SEATS) {
            assertEquals(
                state.handSize(Seat(index)),
                mundo.handSize(Seat(index)),
                "a cadeira $index ficou com o número errado de cartas",
            )
        }
        assertTrue(mundo.hands.flatten().none { it.isHidden }, "sobrou carta virada no mundo")
        assertEquals(
            mundo.hands.flatten().size,
            mundo.hands.flatten().distinct().size,
            "o mundo sorteado repetiu carta",
        )
        assertEquals(
            state.hand(Seat.FIRST),
            mundo.hand(Seat.FIRST),
            "o mundo não pode mexer na mão de quem está olhando",
        )
    }
}
