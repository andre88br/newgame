package io.github.andre88br.newgame.core.games.dominoes

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import io.github.andre88br.newgame.core.engine.ReasonKey
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DominoesTest {

    private fun tile(low: Int, high: Int) = Tile(minOf(low, high), maxOf(low, high))

    // -------- distribuição --------

    @Test
    fun `o conjunto tem 28 pecas sem repetir`() {
        val set = Tile.fullSet()
        assertEquals(28, set.size)
        assertEquals(28, set.distinct().size)
        assertEquals(7, set.count { it.isDouble })
    }

    @Test
    fun `a distribuicao da sete pecas para cada um e catorze no monte`() {
        val state = DominoesGame.initialState(MatchConfig(seed = 42))

        assertEquals(DOMINO_HAND_SIZE, state.handSize(Seat.FIRST))
        assertEquals(DOMINO_HAND_SIZE, state.handSize(Seat.SECOND))
        assertEquals(14, state.boneyard.size)

        val todas = state.hands.flatten() + state.boneyard
        assertEquals(28, todas.size, "nenhuma peça pode sumir")
        assertEquals(28, todas.distinct().size, "nenhuma peça pode aparecer duas vezes")
    }

    @Test
    fun `a mesma semente distribui as mesmas maos`() {
        val a = DominoesGame.initialState(MatchConfig(seed = 7))
        val b = DominoesGame.initialState(MatchConfig(seed = 7))
        assertEquals(a, b)
    }

    @Test
    fun `quem abre e quem tem a maior carroca`() {
        // Cadeira 1 tem o 6-6; cadeira 0 não tem carroça nenhuma.
        val state = DominoesState(
            hands = listOf(
                listOf(tile(0, 1), tile(2, 3), tile(4, 5)),
                listOf(tile(6, 6), tile(0, 2)),
            ),
        )
        // O estado montado à mão não passa pelo sorteio, então confere-se a regra pelo
        // caminho de verdade: uma distribuição em que se sabe quem tem a maior carroça.
        val real = DominoesGame.initialState(MatchConfig(seed = 99))
        val quemAbre = real.turn
        val maiorCarroca = { seat: Seat ->
            real.hand(seat).filter { it.isDouble }.maxOfOrNull { it.pips } ?: -1
        }
        assertTrue(
            maiorCarroca(quemAbre) >= maiorCarroca(quemAbre.let { Seat(1 - it.index) }),
            "quem abriu não tinha a maior carroça:\n$real",
        )
        assertTrue(state.handSize(Seat.SECOND) == 2)
    }

    // -------- encaixe --------

    private val mesaAberta = DominoesState(
        line = listOf(PlacedTile(3, 5)),
        hands = listOf(
            listOf(tile(1, 3), tile(5, 6), tile(2, 2)),
            listOf(tile(0, 4)),
        ),
        boneyard = emptyList(),
        turn = Seat.FIRST,
    )

    @Test
    fun `so encaixa quem casa com a ponta`() {
        val moves = DominoesGame.legalMoves(mesaAberta)
        assertEquals(
            setOf(tile(1, 3), tile(5, 6)),
            moves.map { it.tile }.toSet(),
            "o 2-2 não casa com ponta nenhuma",
        )
    }

    @Test
    fun `a peca entra na ponta certa e a linha continua encadeada`() {
        val depois = DominoesGame.applyOrThrow(mesaAberta, DominoesMove(tile(5, 6), LineEnd.RIGHT))

        assertEquals(listOf(PlacedTile(3, 5), PlacedTile(5, 6)), depois.line)
        assertEquals(3, depois.leftEnd)
        assertEquals(6, depois.rightEnd)
        linhaEncadeada(depois)
    }

    @Test
    fun `encostar na esquerda inverte a peca quando precisa`() {
        val depois = DominoesGame.applyOrThrow(mesaAberta, DominoesMove(tile(1, 3), LineEnd.LEFT))

        assertEquals(PlacedTile(1, 3), depois.line.first())
        assertEquals(1, depois.leftEnd)
        linhaEncadeada(depois)
    }

    private fun linhaEncadeada(state: DominoesState) {
        state.line.zipWithNext { esquerda, direita ->
            assertEquals(esquerda.b, direita.a, "linha desencadeada: ${state.line}")
        }
    }

    @Test
    fun `com as duas pontas iguais nao se oferece o mesmo lance duas vezes`() {
        val state = DominoesState(
            line = listOf(PlacedTile(4, 4)),
            hands = listOf(listOf(tile(4, 5)), listOf(tile(0, 0))),
            turn = Seat.FIRST,
        )
        assertEquals(1, DominoesGame.legalMoves(state).size)
    }

    @Test
    fun `qualquer peca abre a mesa vazia`() {
        val state = DominoesState(
            hands = listOf(listOf(tile(1, 2), tile(3, 4)), listOf(tile(0, 0))),
            turn = Seat.FIRST,
        )
        assertEquals(2, DominoesGame.legalMoves(state).size)
    }

    @Test
    fun `jogar peca que nao se tem e recusado`() {
        val result = DominoesGame.applyMove(mesaAberta, DominoesMove(tile(6, 6), LineEnd.RIGHT))
        assertIs<MoveResult.Illegal>(result)
        assertEquals(ReasonKey.DOMINO_NOT_IN_HAND, result.reason.key)
    }

    @Test
    fun `jogar peca que nao encaixa e recusado`() {
        val result = DominoesGame.applyMove(mesaAberta, DominoesMove(tile(2, 2), LineEnd.RIGHT))
        assertIs<MoveResult.Illegal>(result)
        assertEquals(ReasonKey.DOMINO_DOES_NOT_FIT, result.reason.key)
    }

    // -------- compra e passe --------

    @Test
    fun `quem nao encaixa compra do monte automaticamente`() {
        // Depois do lance das brancas, a cadeira 1 fica sem encaixe e precisa comprar o 0-6.
        // A cadeira 0 fica com peça na mão: se ficasse sem, teria batido e a partida
        // acabaria antes de qualquer compra.
        val state = DominoesState(
            line = listOf(PlacedTile(3, 3)),
            hands = listOf(listOf(tile(3, 6), tile(0, 0)), listOf(tile(0, 1))),
            boneyard = listOf(tile(0, 6), tile(2, 2)),
            turn = Seat.FIRST,
        )
        val depois = DominoesGame.applyOrThrow(state, DominoesMove(tile(3, 6), LineEnd.RIGHT))

        assertEquals(Seat.SECOND, depois.turn)
        assertEquals(2, depois.handSize(Seat.SECOND), "deveria ter comprado uma peça")
        assertContains(depois.hand(Seat.SECOND), tile(0, 6))
        assertEquals(1, depois.boneyard.size)
        assertTrue(DominoesGame.legalMoves(depois).isNotEmpty())
    }

    @Test
    fun `com o monte vazio e sem encaixe a vez passa`() {
        // Depois do lance as pontas ficam 3 e 6. A cadeira 1 não encaixa e o monte está
        // vazio, então passa; a cadeira 0 ainda tem o 2-6, e o jogo continua com ela.
        val state = DominoesState(
            line = listOf(PlacedTile(3, 3)),
            hands = listOf(listOf(tile(3, 6), tile(2, 6)), listOf(tile(0, 1))),
            boneyard = emptyList(),
            turn = Seat.FIRST,
        )
        val depois = DominoesGame.applyOrThrow(state, DominoesMove(tile(3, 6), LineEnd.RIGHT))

        assertEquals(Seat.FIRST, depois.turn)
        assertEquals(1, depois.passes)
    }

    @Test
    fun `dois passes seguidos fecham o jogo e ganha quem tem menos pontos`() {
        val state = DominoesState(
            line = listOf(PlacedTile(3, 3)),
            hands = listOf(listOf(tile(3, 4), tile(6, 6)), listOf(tile(0, 1))),
            boneyard = emptyList(),
            turn = Seat.FIRST,
        )
        val depois = DominoesGame.applyOrThrow(state, DominoesMove(tile(3, 4), LineEnd.RIGHT))

        // Ninguém mais encaixa: 6-6 = 12 pontos contra 0-1 = 1 ponto.
        assertEquals(2, depois.passes)
        assertEquals(Outcome.Win(Seat.SECOND), DominoesGame.outcome(depois))
    }

    @Test
    fun `jogo fechado com a mesma pontuacao e empate`() {
        val state = DominoesState(
            line = listOf(PlacedTile(3, 3)),
            hands = listOf(listOf(tile(2, 2)), listOf(tile(1, 3))),
            boneyard = emptyList(),
            turn = Seat.FIRST,
            passes = 2,
        )
        assertEquals(Outcome.Draw(DrawReason.BLOCKED), DominoesGame.outcome(state))
    }

    @Test
    fun `bater e vencer`() {
        val state = DominoesState(
            line = listOf(PlacedTile(3, 3)),
            hands = listOf(listOf(tile(3, 6)), listOf(tile(0, 1), tile(2, 2))),
            boneyard = emptyList(),
            turn = Seat.FIRST,
        )
        val depois = DominoesGame.applyOrThrow(state, DominoesMove(tile(3, 6), LineEnd.RIGHT))

        assertEquals(0, depois.handSize(Seat.FIRST))
        assertEquals(Outcome.Win(Seat.FIRST), DominoesGame.outcome(depois))
    }

    // -------- informação oculta --------

    @Test
    fun `a mao do adversario e o monte somem para quem olha`() {
        val state = DominoesGame.initialState(MatchConfig(seed = 3))
        val visto = DominoesGame.redactFor(state, Seat.FIRST)

        assertEquals(state.hand(Seat.FIRST), visto.hand(Seat.FIRST), "a própria mão continua")
        assertTrue(
            visto.hand(Seat.SECOND).all { it.isHidden },
            "a mão do adversário não pode vazar: ${visto.hand(Seat.SECOND)}",
        )
        assertTrue(visto.boneyard.all { it.isHidden }, "o monte também não")
    }

    @Test
    fun `a redacao preserva a quantidade de pecas`() {
        val state = DominoesGame.initialState(MatchConfig(seed = 3))
        val visto = DominoesGame.redactFor(state, Seat.FIRST)

        // Quantas peças o outro tem é informação pública, e faz parte do jogo.
        assertEquals(state.handSize(Seat.SECOND), visto.handSize(Seat.SECOND))
        assertEquals(state.boneyard.size, visto.boneyard.size)
    }

    @Test
    fun `os lances legais nao mudam com a redacao`() {
        val state = DominoesGame.initialState(MatchConfig(seed = 11))
        val visto = DominoesGame.redactFor(state, state.turn)

        assertEquals(
            DominoesGame.legalMoves(state).toSet(),
            DominoesGame.legalMoves(visto).toSet(),
            "esconder a mão alheia não pode mudar o que se pode jogar",
        )
    }

    // -------- determinização --------

    @Test
    fun `completar um mundo respeita o que ja se sabe`() {
        val state = DominoesGame.initialState(MatchConfig(seed = 5))
        val visto = DominoesGame.redactFor(state, Seat.FIRST)
        val mundo = completeDominoes(visto, Rng.seeded(1))

        assertEquals(visto.hand(Seat.FIRST), mundo.hand(Seat.FIRST), "a própria mão não muda")
        assertTrue(mundo.hands.flatten().none { it.isHidden }, "não pode sobrar peça virada")
        assertTrue(mundo.boneyard.none { it.isHidden })

        val todas = mundo.hands.flatten() + mundo.boneyard + mundo.line.map { it.tile }
        assertEquals(todas.size, todas.distinct().size, "o mundo inventou peça repetida: $todas")
        assertEquals(28, todas.size)
    }

    @Test
    fun `mundos diferentes saem de sementes diferentes`() {
        val visto = DominoesGame.redactFor(DominoesGame.initialState(MatchConfig(seed = 5)), Seat.FIRST)
        val a = completeDominoes(visto, Rng.seeded(1))
        val b = completeDominoes(visto, Rng.seeded(2))
        assertTrue(a != b, "dois sorteios deram exatamente o mesmo mundo")
    }

    // -------- IA --------

    @Test
    fun `a IA joga so com o que enxerga`() {
        val state = DominoesGame.initialState(MatchConfig(seed = 21))
        val visto = DominoesGame.redactFor(state, state.turn)

        for (difficulty in Difficulty.entries) {
            val move = DominoesAi.chooseMove(visto, difficulty, seed = 4L)
            assertTrue(move != null, "$difficulty não escolheu lance")
            assertContains(
                state.hand(state.turn),
                move.tile,
                "$difficulty jogou uma peça que não está na mão",
            )
            assertContains(DominoesGame.legalMoves(state), move)
        }
    }

    @Test
    fun `uma partida inteira entre duas IAs termina com as contas fechadas`() {
        var state = DominoesGame.initialState(MatchConfig(seed = 2026))
        var guard = 0

        while (!DominoesGame.outcome(state).isOver && guard++ < 60) {
            val visto = DominoesGame.redactFor(state, state.turn)
            val move = DominoesAi.chooseMove(visto, Difficulty.EASY, seed = guard.toLong())
            assertTrue(move != null, "sem lance com a partida em andamento:\n$state")
            state = DominoesGame.applyOrThrow(state, move)
        }

        assertTrue(DominoesGame.outcome(state).isOver, "a partida não terminou:\n$state")

        val todas = state.hands.flatten() + state.boneyard + state.line.map { it.tile }
        assertEquals(28, todas.size, "peça sumiu ou foi duplicada no meio da partida")
        assertEquals(28, todas.distinct().size)
    }
}
