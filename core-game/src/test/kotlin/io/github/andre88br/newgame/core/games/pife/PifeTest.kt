package io.github.andre88br.newgame.core.games.pife

import io.github.andre88br.newgame.core.ai.Difficulty
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
import kotlin.test.assertTrue

/**
 * O pife é uma pergunta só — as nove cartas fecham em três grupos? —, e por isso quase tudo
 * aqui é sobre [isGroup] e [formsWinningHand]. Errar a conta do grupo faz o jogo aceitar uma
 * batida que não vale, ou pior: recusar uma que vale, e aí não há como ganhar.
 */
class PifeTest {

    private fun c(rank: Rank, suit: Suit) = Card(rank, suit)
    private val coringa = c(Rank.JOKER, Suit.HEARTS)

    private fun novo(seed: Long = 7, seats: Int = 2) =
        PifeGame.initialState(MatchConfig(seed, seats = seats))

    // -------- o que é grupo --------

    @Test
    fun `trinca e tres do mesmo valor`() {
        assertTrue(isGroup(listOf(c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES))))
        assertTrue(!isGroup(listOf(c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.QUEEN, Suit.SPADES))))
    }

    @Test
    fun `sequencia e tres seguidas do mesmo naipe`() {
        assertTrue(isGroup(listOf(c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS))))
        // Fora de ordem continua sendo sequência: a mão não vem ordenada.
        assertTrue(isGroup(listOf(c(Rank.SEVEN, Suit.HEARTS), c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS))))
        assertTrue(
            !isGroup(listOf(c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.CLUBS), c(Rank.SEVEN, Suit.HEARTS))),
            "naipes diferentes não fazem sequência",
        )
        assertTrue(
            !isGroup(listOf(c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.EIGHT, Suit.HEARTS))),
            "com buraco e sem curinga não é sequência",
        )
    }

    @Test
    fun `o curinga tapa buraco na trinca e na sequencia`() {
        assertTrue(isGroup(listOf(c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), coringa)))
        assertTrue(isGroup(listOf(c(Rank.FIVE, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS), coringa)))
        // Na ponta também: o curinga vira o 4 ou o 7.
        assertTrue(isGroup(listOf(c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), coringa)))
        assertTrue(
            !isGroup(listOf(c(Rank.FIVE, Suit.HEARTS), c(Rank.NINE, Suit.HEARTS), coringa)),
            "um curinga não cobre um buraco de três",
        )
    }

    @Test
    fun `grupo so de curinga nao vale`() {
        val outro = c(Rank.JOKER, Suit.SPADES)
        assertTrue(!isGroup(listOf(coringa, outro, coringa)), "três curingas não são carta nenhuma")
    }

    /** O ás é carta alta, e só: Q-K-A fecha, A-2-3 não. */
    @Test
    fun `o as fecha por cima e nao por baixo`() {
        assertTrue(isGroup(listOf(c(Rank.QUEEN, Suit.SPADES), c(Rank.KING, Suit.SPADES), c(Rank.ACE, Suit.SPADES))))
        assertTrue(!isGroup(listOf(c(Rank.ACE, Suit.SPADES), c(Rank.TWO, Suit.SPADES), c(Rank.THREE, Suit.SPADES))))
    }

    // -------- a mão que bate --------

    @Test
    fun `nove cartas em tres grupos fecham a mao`() {
        val mao = listOf(
            c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
            c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS),
            c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.CLUBS),
        )
        assertTrue(formsWinningHand(mao))
        assertEquals(3, bestGroupCount(mao))
    }

    @Test
    fun `oito cartas nao fecham, por melhores que sejam`() {
        val mao = listOf(
            c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
            c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS),
            c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS),
        )
        assertTrue(!formsWinningHand(mao), "a mão fecha com nove, e não com oito")
    }

    /**
     * O caso que uma varredura gulosa erraria: a mesma carta serve a dois grupos, e o
     * arranjo óbvio deixa o resto sem fechar. Só uma partição de verdade encontra o certo.
     *
     * Aqui o seis de copas serve à trinca de seis e à sequência 4-5-6 de copas. Pela trinca,
     * que é o caminho que salta aos olhos, sobram o seis de ouros, o quatro e o cinco de
     * copas — três cartas que não fazem grupo nenhum. A mão só fecha voltando atrás.
     */
    @Test
    fun `a mao fecha mesmo quando o arranjo obvio nao fecha`() {
        val mao = listOf(
            c(Rank.SIX, Suit.HEARTS), c(Rank.SIX, Suit.CLUBS), c(Rank.SIX, Suit.SPADES),
            c(Rank.SIX, Suit.DIAMONDS), c(Rank.FOUR, Suit.HEARTS), c(Rank.FIVE, Suit.HEARTS),
            c(Rank.SEVEN, Suit.CLUBS), c(Rank.EIGHT, Suit.CLUBS), c(Rank.NINE, Suit.CLUBS),
        )
        assertTrue(formsWinningHand(mao), "existe arranjo que fecha: a partição precisa achá-lo")
        assertEquals(3, bestGroupCount(mao))
    }

    @Test
    fun `mao sem grupo nenhum nao fecha`() {
        val mao = listOf(
            c(Rank.TWO, Suit.CLUBS), c(Rank.FIVE, Suit.HEARTS), c(Rank.EIGHT, Suit.SPADES),
            c(Rank.JACK, Suit.DIAMONDS), c(Rank.FOUR, Suit.CLUBS), c(Rank.SEVEN, Suit.HEARTS),
            c(Rank.TEN, Suit.SPADES), c(Rank.KING, Suit.DIAMONDS), c(Rank.THREE, Suit.HEARTS),
        )
        assertTrue(!formsWinningHand(mao))
        assertEquals(0, bestGroupCount(mao))
    }

    // -------- a distribuição --------

    @Test
    fun `cada um recebe nove cartas, e sobra monte com uma no lixo`() {
        for (seats in 2..4) {
            val state = novo(seats = seats)
            assertEquals(seats, state.seats)
            assertTrue(state.hands.all { it.size == PIFE_HAND_SIZE }, "mão fora do tamanho")
            assertEquals(1, state.discard.size, "a mão abre com uma carta no lixo")

            val todas = state.hands.flatten() + state.stock + state.discard
            assertEquals(106, todas.size, "dois baralhos com um curinga cada dão 106 cartas")
        }
    }

    @Test
    fun `a mesma semente reparte a mesma mesa`() {
        assertEquals(novo(42).hands, novo(42).hands)
    }

    // -------- a vez --------

    @Test
    fun `a vez comeca comprando e so depois descarta`() {
        val state = novo()
        assertEquals(PifePhase.DRAW, state.phase)
        assertTrue(
            PifeGame.applyMove(state, PifeMove.Discard(state.hand(Seat.FIRST).first())) is MoveResult.Illegal,
            "descartar antes de comprar devia ser recusado",
        )

        val depois = PifeGame.applyOrThrow(state, PifeMove.DrawStock)
        assertEquals(PifePhase.DISCARD, depois.phase)
        assertEquals(PIFE_HAND_SIZE + 1, depois.handSize(Seat.FIRST), "compra-se para dez")
        assertTrue(
            PifeGame.applyMove(depois, PifeMove.DrawStock) is MoveResult.Illegal,
            "só se compra uma vez por vez",
        )
    }

    @Test
    fun `comprar do lixo tira a carta de cima`() {
        val state = novo()
        val topo = state.discardTop!!
        val depois = PifeGame.applyOrThrow(state, PifeMove.DrawDiscard)

        assertTrue(topo in depois.hand(Seat.FIRST), "a carta de cima do lixo foi para a mão")
        assertTrue(depois.discard.isEmpty(), "e saiu do lixo")
    }

    @Test
    fun `o descarte passa a vez e fica visivel no lixo`() {
        val state = PifeGame.applyOrThrow(novo(), PifeMove.DrawStock)
        val carta = state.hand(Seat.FIRST).first()
        val depois = PifeGame.applyOrThrow(state, PifeMove.Discard(carta))

        assertEquals(Seat(1), depois.turn)
        assertEquals(PifePhase.DRAW, depois.phase)
        assertEquals(carta, depois.discardTop)
        assertEquals(PIFE_HAND_SIZE, depois.handSize(Seat.FIRST), "a mão volta a nove")
    }

    /** Bater é ficar com nove cartas que fecham depois do descarte. */
    @Test
    fun `descartar a carta que sobra bate`() {
        val fechada = listOf(
            c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
            c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS),
            c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.CLUBS),
        )
        val sobrando = c(Rank.NINE, Suit.DIAMONDS)
        val state = novo().copy(
            hands = listOf(fechada + sobrando, novo().hand(Seat(1))),
            phase = PifePhase.DISCARD,
            turn = Seat.FIRST,
        )
        val depois = PifeGame.applyOrThrow(state, PifeMove.Discard(sobrando))

        assertEquals(Outcome.Win(Seat.FIRST), PifeGame.outcome(depois), "a mão fechada bate")
    }

    @Test
    fun `descartar a carta errada nao bate`() {
        val fechada = listOf(
            c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES),
            c(Rank.FIVE, Suit.HEARTS), c(Rank.SIX, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS),
            c(Rank.TWO, Suit.CLUBS), c(Rank.THREE, Suit.CLUBS), c(Rank.FOUR, Suit.CLUBS),
        )
        val sobrando = c(Rank.NINE, Suit.DIAMONDS)
        val state = novo().copy(
            hands = listOf(fechada + sobrando, novo().hand(Seat(1))),
            phase = PifePhase.DISCARD,
            turn = Seat.FIRST,
        )
        // Jogando fora um rei, a trinca se desfaz e a mão deixa de fechar.
        val depois = PifeGame.applyOrThrow(state, PifeMove.Discard(c(Rank.KING, Suit.CLUBS)))
        assertTrue(!PifeGame.outcome(depois).isOver, "sem os três grupos ninguém bate")
    }

    /** O monte acabando não trava a mão: o lixo volta embaralhado, menos a carta de cima. */
    @Test
    fun `o monte se remonta com o lixo`() {
        val state = novo().copy(
            stock = emptyList(),
            discard = List(6) { c(Rank.entries[it], Suit.CLUBS) },
            phase = PifePhase.DRAW,
        )
        val topo = state.discardTop!!
        val depois = PifeGame.applyOrThrow(state, PifeMove.DrawStock)

        assertEquals(PifePhase.DISCARD, depois.phase, "a compra aconteceu")
        assertEquals(listOf(topo), depois.discard, "a de cima do lixo fica onde está")
        assertTrue(depois.stock.isNotEmpty(), "o resto do lixo virou monte")
        assertEquals(1, depois.reshuffles, "a remontagem foi contada")
    }

    /**
     * Comprar e descartar não gastam carta: sem um limite, uma mão em que ninguém fecha
     * rodaria para sempre. Passado o limite, a mão morre empatada em vez de travar.
     */
    @Test
    fun `mao em que ninguem fecha termina empatada`() {
        val state = novo().copy(
            stock = emptyList(),
            discard = List(6) { c(Rank.entries[it], Suit.CLUBS) },
            phase = PifePhase.DRAW,
            reshuffles = PIFE_MAX_RESHUFFLES,
        )
        assertTrue(PifeGame.outcome(state) is Outcome.Draw, "o lixo já voltou vezes demais")
        assertTrue(PifeGame.legalMoves(state).isEmpty(), "mão morta não tem lance")
    }

    /** Enquanto o descarte ainda está por fazer, a mão não morre: é ele que pode fechá-la. */
    @Test
    fun `quem ja comprou ainda descarta, mesmo com o monte no fim`() {
        val state = novo().copy(
            stock = emptyList(),
            discard = emptyList(),
            phase = PifePhase.DISCARD,
            reshuffles = PIFE_MAX_RESHUFFLES,
        )
        assertTrue(!PifeGame.outcome(state).isOver)
        assertTrue(PifeGame.legalMoves(state).isNotEmpty(), "falta descartar")
    }

    // -------- a partida inteira --------

    @Test
    fun `uma partida inteira termina sem travar`() {
        for (seats in 2..4) {
            var state = PifeGame.initialState(MatchConfig(seed = 2026, seats = seats))
            var guard = 0
            while (!PifeGame.outcome(state).isOver && guard++ < 4_000) {
                val legais = PifeGame.legalMoves(state)
                assertTrue(legais.isNotEmpty(), "mesa de $seats travou no ply ${state.ply}: $state")
                state = PifeGame.applyOrThrow(state, legais.first())
            }
            assertTrue(
                PifeGame.outcome(state).isOver,
                "mesa de $seats não terminou em $guard lances",
            )
        }
    }

    @Test
    fun `nenhuma carta se perde ao longo da partida`() {
        var state = novo(seed = 55)
        var guard = 0
        while (!PifeGame.outcome(state).isOver && guard++ < 500) {
            val total = state.hands.flatten().size + state.stock.size + state.discard.size
            assertEquals(106, total, "sumiu ou sobrou carta no ply ${state.ply}")
            state = PifeGame.applyOrThrow(state, PifeGame.legalMoves(state).first())
        }
    }

    // -------- informação oculta --------

    @Test
    fun `a mao dos outros e o monte nao vazam, e o lixo fica`() {
        val state = novo(seats = 4)
        val visto = PifeGame.redactFor(state, Seat.FIRST)

        assertEquals(state.hand(Seat.FIRST), visto.hand(Seat.FIRST), "a própria mão fica")
        for (index in 1 until 4) {
            assertTrue(visto.hand(Seat(index)).all { it.isHidden }, "vazou mão da cadeira $index")
            assertEquals(state.handSize(Seat(index)), visto.handSize(Seat(index)), "contagem errada")
        }
        assertTrue(visto.stock.all { it.isHidden }, "o monte não pode ser visto")
        // O lixo é público de propósito: é a informação que sobra para ler a mão alheia.
        assertEquals(state.discard, visto.discard, "o lixo é de todos")
    }

    @Test
    fun `o mundo sorteado respeita as contagens`() {
        val state = novo(seed = 3, seats = 4)
        val visto = PifeGame.redactFor(state, Seat.FIRST)
        val mundo = completePife(visto, Rng.seeded(9))

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
    }

    @Test
    fun `a maquina joga so com o que enxerga, e sempre lance legal`() {
        var state = novo(seed = 11)
        var guard = 0
        while (!PifeGame.outcome(state).isOver && guard++ < 120) {
            val comoEleVe = PifeGame.redactFor(state, state.turn)
            val escolhido = PifeAi.chooseMove(comoEleVe, Difficulty.EASY, seed = guard.toLong())
            assertTrue(escolhido != null, "a IA não escolheu lance no ply ${state.ply}")
            assertTrue(
                PifeGame.applyMove(state, escolhido) is MoveResult.Ok,
                "a IA escolheu lance ilegal: ${escolhido.describe()}",
            )
            state = PifeGame.applyOrThrow(state, escolhido)
        }
    }
}
