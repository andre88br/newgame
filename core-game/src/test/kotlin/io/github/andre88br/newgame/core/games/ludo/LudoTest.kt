package io.github.andre88br.newgame.core.games.ludo

import io.github.andre88br.newgame.core.ai.Difficulty
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

class LudoTest {

    private fun state(
        first: List<Int>,
        second: List<Int>,
        die: Int,
        turn: Seat = Seat.FIRST,
    ) = LudoState(tokens = listOf(first, second), turn = turn, die = die, rng = Rng.seeded(1))

    private val noCurral = List(LUDO_TOKENS) { LUDO_YARD }

    // -------- saída do curral --------

    @Test
    fun `so o seis tira peao do curral`() {
        val comCinco = state(noCurral, noCurral, die = 5)
        assertTrue(LudoGame.movesFor(comCinco, Seat.FIRST, 5).isEmpty())

        val comSeis = state(noCurral, noCurral, die = 6)
        assertEquals(LUDO_TOKENS, LudoGame.movesFor(comSeis, Seat.FIRST, 6).size)
    }

    @Test
    fun `o peao sai para a casa zero`() {
        val antes = state(noCurral, noCurral, die = 6)
        val depois = LudoGame.applyOrThrow(antes, LudoMove(0))
        assertEquals(0, depois.tokensOf(Seat.FIRST)[0])
    }

    @Test
    fun `nao se tira peao para uma casa de saida ja ocupada por peao proprio`() {
        val comUmNaSaida = state(listOf(0, LUDO_YARD, LUDO_YARD, LUDO_YARD), noCurral, die = 6)
        val saidas = LudoGame.movesFor(comUmNaSaida, Seat.FIRST, 6).map { it.token }
        assertTrue(saidas.none { comUmNaSaida.tokensOf(Seat.FIRST)[it] == LUDO_YARD })
    }

    @Test
    fun `tentar sair sem seis e recusado com a explicacao certa`() {
        val comTres = state(noCurral, listOf(10, LUDO_YARD, LUDO_YARD, LUDO_YARD), die = 3)
        val result = LudoGame.applyMove(comTres, LudoMove(0))
        assertIs<MoveResult.Illegal>(result)
        assertEquals(ReasonKey.LUDO_NEEDS_SIX, result.reason.key)
    }

    // -------- lance extra --------

    @Test
    fun `tirar seis da direito a jogar de novo`() {
        val antes = state(listOf(3, LUDO_YARD, LUDO_YARD, LUDO_YARD), listOf(20, 21, 22, 23), die = 6)
        val depois = LudoGame.applyOrThrow(antes, LudoMove(0))
        assertEquals(Seat.FIRST, depois.turn, "com 6, a vez continua de quem jogou")
    }

    @Test
    fun `sem seis a vez passa`() {
        val antes = state(listOf(3, LUDO_YARD, LUDO_YARD, LUDO_YARD), listOf(20, 21, 22, 23), die = 4)
        val depois = LudoGame.applyOrThrow(antes, LudoMove(0))
        assertEquals(Seat.SECOND, depois.turn)
    }

    // -------- captura --------

    @Test
    fun `pisar em peao adversario manda ele para o curral`() {
        // A cadeira 0 anda 3 casas e cai onde está um peão da cadeira 1.
        val alvo = 3
        val absoluta = absoluteSquare(Seat.FIRST, alvo)!!
        assertTrue(!isSafeSquare(absoluta), "a casa do teste precisa ser comum, não segura")

        // Progresso do adversário que resulta na mesma casa absoluta.
        val progressoAdversario = (absoluta - startSquare(Seat.SECOND) + LUDO_TRACK) % LUDO_TRACK
        val antes = state(
            first = listOf(0, LUDO_YARD, LUDO_YARD, LUDO_YARD),
            second = listOf(progressoAdversario, LUDO_YARD, LUDO_YARD, LUDO_YARD),
            die = 3,
        )

        val depois = LudoGame.applyOrThrow(antes, LudoMove(0))
        assertEquals(LUDO_YARD, depois.tokensOf(Seat.SECOND)[0], "o peão pisado volta para o curral")
        assertEquals(alvo, depois.tokensOf(Seat.FIRST)[0])
    }

    @Test
    fun `peao em casa segura nao e capturado`() {
        // A casa de saída da cadeira 1 é segura por definição.
        val absoluta = startSquare(Seat.SECOND)
        assertTrue(isSafeSquare(absoluta))

        val passos = (absoluta - startSquare(Seat.FIRST) + LUDO_TRACK) % LUDO_TRACK
        val antes = state(
            first = listOf(passos - 2, LUDO_YARD, LUDO_YARD, LUDO_YARD),
            second = listOf(0, LUDO_YARD, LUDO_YARD, LUDO_YARD),
            die = 2,
        )

        val depois = LudoGame.applyOrThrow(antes, LudoMove(0))
        assertEquals(0, depois.tokensOf(Seat.SECOND)[0], "em casa segura o peão fica onde está")
    }

    @Test
    fun `peao no corredor final nao pode ser capturado`() {
        val noCorredor = LUDO_TRACK + 2
        assertEquals(null, absoluteSquare(Seat.SECOND, noCorredor), "corredor não tem casa absoluta")
    }

    // -------- chegada --------

    @Test
    fun `a chegada e exata`() {
        val faltandoDois = state(listOf(LUDO_GOAL - 2, LUDO_GOAL, LUDO_GOAL, LUDO_GOAL), listOf(5, 6, 7, 8), die = 5)
        assertTrue(
            LudoGame.movesFor(faltandoDois, Seat.FIRST, 5).isEmpty(),
            "faltando 2, um dado 5 passaria da chegada e não vale",
        )

        val comDois = faltandoDois.copy(die = 2)
        assertEquals(1, LudoGame.movesFor(comDois, Seat.FIRST, 2).size)
    }

    @Test
    fun `passar da chegada e recusado com a explicacao certa`() {
        val faltandoDois = state(listOf(LUDO_GOAL - 2, LUDO_GOAL, LUDO_GOAL, LUDO_GOAL), listOf(5, 6, 7, 8), die = 5)
        val result = LudoGame.applyMove(faltandoDois, LudoMove(0))
        assertIs<MoveResult.Illegal>(result)
        assertEquals(ReasonKey.LUDO_EXACT_FINISH, result.reason.key)
    }

    @Test
    fun `levar os quatro peoes a chegada vence`() {
        val quaseLa = state(
            first = listOf(LUDO_GOAL, LUDO_GOAL, LUDO_GOAL, LUDO_GOAL - 1),
            second = listOf(5, 6, 7, 8),
            die = 1,
        )
        val depois = LudoGame.applyOrThrow(quaseLa, LudoMove(3))
        assertEquals(LUDO_TOKENS, depois.finished(Seat.FIRST))
        assertEquals(Outcome.Win(Seat.FIRST), LudoGame.outcome(depois))
    }

    // -------- dados --------

    @Test
    fun `a partida comeca com um dado ja rolado`() {
        val inicial = LudoGame.initialState(MatchConfig(seed = 4))
        assertTrue(inicial.die in 1..6, "dado fora da faixa: ${inicial.die}")
    }

    @Test
    fun `a mesma semente da a mesma sequencia de dados`() {
        fun dados(): List<Int> {
            var state = LudoGame.initialState(MatchConfig(seed = 123))
            val saidas = ArrayList<Int>()
            repeat(15) {
                if (LudoGame.outcome(state).isOver) return@repeat
                saidas += state.die
                val move = LudoGame.legalMoves(state).firstOrNull() ?: return@repeat
                state = LudoGame.applyOrThrow(state, move)
            }
            return saidas
        }
        assertEquals(dados(), dados(), "a partida precisa reproduzir os mesmos dados")
    }

    @Test
    fun `sementes diferentes dao partidas diferentes`() {
        val a = LudoGame.initialState(MatchConfig(seed = 1))
        val b = LudoGame.initialState(MatchConfig(seed = 2))
        assertTrue(a.rng != b.rng)
    }

    @Test
    fun `quem nao tem lance perde a vez sozinho`() {
        // Todos no curral e um dado que não é 6: ninguém sai. O motor rola de novo até
        // alguém poder jogar, então quem receber a vez sempre tem lance.
        var state = LudoGame.initialState(MatchConfig(seed = 8))
        repeat(10) {
            if (LudoGame.outcome(state).isOver) return@repeat
            assertTrue(
                LudoGame.legalMoves(state).isNotEmpty(),
                "chegou uma vez sem lance nenhum:\n$state",
            )
            state = LudoGame.applyOrThrow(state, LudoGame.legalMoves(state).first())
        }
    }

    // -------- partida completa --------

    @Test
    fun `uma partida inteira entre duas IAs termina com o tabuleiro coerente`() {
        var state = LudoGame.initialState(MatchConfig(seed = 2026))
        var guard = 0

        while (!LudoGame.outcome(state).isOver && guard++ < 600) {
            val move = LudoAi.chooseMove(state, Difficulty.EASY, seed = guard.toLong())
            assertTrue(move != null, "sem lance com a partida em andamento:\n$state")
            state = LudoGame.applyOrThrow(state, move)
        }

        assertTrue(LudoGame.outcome(state).isOver, "a partida não terminou em $guard lances:\n$state")
        for (seat in listOf(Seat.FIRST, Seat.SECOND)) {
            assertEquals(LUDO_TOKENS, state.tokensOf(seat).size, "peão sumiu")
            assertTrue(
                state.tokensOf(seat).all { it == LUDO_YARD || it in 0..LUDO_GOAL },
                "peão fora do tabuleiro: ${state.tokensOf(seat)}",
            )
        }
    }

    @Test
    fun `a IA devolve lance legal em todos os niveis`() {
        val state = LudoGame.initialState(MatchConfig(seed = 77))
        val legal = LudoGame.legalMoves(state)
        for (difficulty in Difficulty.entries) {
            val move = LudoAi.chooseMove(state, difficulty, seed = 1L)
            assertTrue(move in legal, "$difficulty devolveu $move")
        }
    }
}
