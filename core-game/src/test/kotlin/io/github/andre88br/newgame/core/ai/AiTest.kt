package io.github.andre88br.newgame.core.ai

import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import io.github.andre88br.newgame.core.engine.opponent
import io.github.andre88br.newgame.core.games.checkers.CheckersAi
import io.github.andre88br.newgame.core.games.checkers.CheckersGame
import io.github.andre88br.newgame.core.games.checkers.CheckersMove
import io.github.andre88br.newgame.core.games.checkers.CheckersState
import io.github.andre88br.newgame.core.games.checkers.positionOf
import io.github.andre88br.newgame.core.games.checkers.squareAt
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeAi
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeGame
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeMove
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiTest {

    // -------- jogo da velha --------

    /**
     * O jogo da velha é pequeno o bastante para verificar a IA por exaustão, em vez de por
     * amostragem: joga-se **toda** linha possível do adversário contra ela. Com jogo
     * perfeito o resultado é sempre empate, então perder uma vez só já reprova.
     */
    @Test
    fun `no dificil a IA nunca perde, jogando com qualquer das duas cadeiras`() {
        for (aiSeat in listOf(Seat.FIRST, Seat.SECOND)) {
            val start = TicTacToeGame.initialState(MatchConfig.DETERMINISTIC)
            finishedGames = 0
            exploreEveryOpponentLine(start, aiSeat)

            // Sem esta conferência o teste passaria de graça se a exploração parasse na
            // primeira jogada — nunca perder é fácil quando não se joga. Jogando primeiro
            // a IA fecha a partida mais cedo, daí a diferença entre as duas contagens.
            val expected = if (aiSeat == Seat.FIRST) 76 else 457
            assertEquals(
                expected,
                finishedGames,
                "a exploração cobriu $finishedGames partidas com a IA em $aiSeat",
            )
        }
    }

    private var finishedGames = 0

    private fun exploreEveryOpponentLine(state: TicTacToeState, aiSeat: Seat) {
        val outcome = TicTacToeGame.outcome(state)
        if (outcome.isOver) {
            finishedGames++
            assertNotEquals(
                Outcome.Win(aiSeat.opponent()),
                outcome,
                "a IA perdeu jogando com $aiSeat:\n$state",
            )
            return
        }
        if (state.turn == aiSeat) {
            val move = TicTacToeAi.chooseMove(state, Difficulty.HARD, seed = 1L)
            assertNotNull(move, "a IA não escolheu lance em:\n$state")
            exploreEveryOpponentLine(TicTacToeGame.applyOrThrow(state, move), aiSeat)
        } else {
            for (move in TicTacToeGame.legalMoves(state)) {
                exploreEveryOpponentLine(TicTacToeGame.applyOrThrow(state, move), aiSeat)
            }
        }
    }

    @Test
    fun `a IA fecha a linha quando pode vencer no lance`() {
        // X em 0 e 1, faltando o 2. O centro está com O.
        val state = TicTacToeState(
            cells = listOf(0, 0, -1, -1, 1, -1, -1, 1, -1),
            turn = Seat.FIRST,
            ply = 4,
        )
        assertEquals(TicTacToeMove(2), TicTacToeAi.chooseMove(state, Difficulty.HARD, seed = 1L))
    }

    @Test
    fun `a IA bloqueia a vitoria do adversario`() {
        // O ameaça fechar em 2; X não tem vitória imediata, então tem que bloquear.
        val state = TicTacToeState(
            cells = listOf(1, 1, -1, -1, 0, -1, -1, -1, -1),
            turn = Seat.FIRST,
            ply = 3,
        )
        assertEquals(TicTacToeMove(2), TicTacToeAi.chooseMove(state, Difficulty.HARD, seed = 1L))
    }

    @Test
    fun `todos os niveis devolvem lance legal`() {
        val start = TicTacToeGame.initialState(MatchConfig.DETERMINISTIC)
        val legal = TicTacToeGame.legalMoves(start)
        for (difficulty in Difficulty.entries) {
            for (seed in 1L..25L) {
                val move = TicTacToeAi.chooseMove(start, difficulty, seed)
                assertTrue(move in legal, "$difficulty com semente $seed devolveu $move")
            }
        }
    }

    @Test
    fun `a escolha da IA e reproduzivel pela semente`() {
        val start = TicTacToeGame.initialState(MatchConfig.DETERMINISTIC)
        repeat(10) { seed ->
            assertEquals(
                TicTacToeAi.chooseMove(start, Difficulty.EASY, seed.toLong()),
                TicTacToeAi.chooseMove(start, Difficulty.EASY, seed.toLong()),
            )
        }
    }

    @Test
    fun `o nivel facil erra as vezes e o dificil nunca`() {
        // Posição com uma vitória imediata evidente em 2.
        val state = TicTacToeState(
            cells = listOf(0, 0, -1, -1, 1, -1, -1, 1, -1),
            turn = Seat.FIRST,
            ply = 4,
        )
        val seeds = (1L..200L)
        val easyMisses = seeds.count { TicTacToeAi.chooseMove(state, Difficulty.EASY, it) != TicTacToeMove(2) }
        val hardMisses = seeds.count { TicTacToeAi.chooseMove(state, Difficulty.HARD, it) != TicTacToeMove(2) }

        assertEquals(0, hardMisses, "o nível difícil deixou de vencer")
        assertTrue(easyMisses > 0, "o nível fácil deveria errar de vez em quando")
        assertTrue(easyMisses < seeds.count() / 2, "o nível fácil está errando demais: $easyMisses")
    }

    // -------- damas --------

    @Test
    fun `a IA de damas escolhe a captura obrigatoria`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . w . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val move = CheckersAi.chooseMove(state, Difficulty.HARD, seed = 1L)
        assertNotNull(move)
        assertTrue(move.isCapture)
    }

    @Test
    fun `a IA de damas prefere a captura dupla`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . . . . . . .",
            ". . . . . b . .",
            ". . . . . . w .",
            ". b . . . . . .",
            "w . . . . . . .",
        )
        val move = CheckersAi.chooseMove(state, Difficulty.MEDIUM, seed = 3L)
        assertEquals(2, move?.captured?.size)
    }

    @Test
    fun `a IA de damas enxerga que pode promover`() {
        // A pedra em (1,2) promove em um lance; a busca deve preferir isso a mexer a outra.
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . w . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . w . .",
            ". . . . . . . .",
        )
        val move = CheckersAi.chooseMove(state, Difficulty.HARD, seed = 1L)
        assertNotNull(move)
        assertEquals(squareAt(1, 2), move.from, "esperava a promoção, veio ${move.describe()}")
        val after = CheckersGame.applyOrThrow(state, move)
        assertEquals(1, after.countKings(Seat.FIRST))
    }

    @Test
    fun `a IA respeita o orcamento de tempo`() {
        val start = CheckersGame.initialState(MatchConfig.DETERMINISTIC)
        val before = System.nanoTime()
        CheckersAi.chooseMove(start, Difficulty.HARD, seed = 1L)
        val elapsedMillis = (System.nanoTime() - before) / 1_000_000

        // O orçamento do nível difícil é de 2,5 s; a margem cobre a última profundidade
        // que ainda estava rodando quando o tempo acabou, além de máquinas lentas.
        assertTrue(elapsedMillis < 15_000, "a busca levou ${elapsedMillis}ms")
    }

    @Test
    fun `uma partida completa de damas entre duas IAs termina`() {
        var state: CheckersState = CheckersGame.initialState(MatchConfig.DETERMINISTIC)
        var moves = 0
        val played = ArrayList<CheckersMove>()

        while (!CheckersGame.outcome(state).isOver && moves < 300) {
            val move = CheckersAi.chooseMove(state, Difficulty.EASY, seed = moves.toLong())
            assertNotNull(move, "sem lance com a partida em andamento:\n$state")
            played += move
            state = CheckersGame.applyOrThrow(state, move)
            moves++
        }

        assertTrue(
            CheckersGame.outcome(state).isOver,
            "a partida não terminou em $moves lances:\n$state\n" +
                played.joinToString(" ") { it.describe() },
        )
    }
}
