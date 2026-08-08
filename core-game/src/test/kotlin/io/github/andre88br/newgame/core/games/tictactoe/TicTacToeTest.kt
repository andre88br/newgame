package io.github.andre88br.newgame.core.games.tictactoe

import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TicTacToeTest {

    private val start = TicTacToeGame.initialState(MatchConfig.DETERMINISTIC)

    /** Aplica uma sequência de casas, alternando as jogadoras. */
    private fun play(vararg cells: Int): TicTacToeState =
        cells.fold(start) { state, cell -> TicTacToeGame.applyOrThrow(state, TicTacToeMove(cell)) }

    @Test
    fun `o tabuleiro comeca vazio com nove lances e a vez da primeira cadeira`() {
        assertEquals(9, TicTacToeGame.legalMoves(start).size)
        assertEquals(Seat.FIRST, start.turn)
        assertEquals(0, start.ply)
        assertEquals(Outcome.InProgress, TicTacToeGame.outcome(start))
    }

    @Test
    fun `a vez alterna e as casas ocupadas somem dos lances legais`() {
        val after = play(4)
        assertEquals(Seat.SECOND, after.turn)
        assertEquals(1, after.ply)
        assertEquals(8, TicTacToeGame.legalMoves(after).size)
        assertTrue(TicTacToeGame.legalMoves(after).none { it.cell == 4 })
    }

    @Test
    fun `nao se joga em casa ocupada`() {
        val after = play(0)
        val result = TicTacToeGame.applyMove(after, TicTacToeMove(0))
        assertIs<MoveResult.Illegal>(result)
    }

    @Test
    fun `linha horizontal vence`() {
        val state = play(0, 3, 1, 4, 2)
        assertEquals(Outcome.Win(Seat.FIRST), TicTacToeGame.outcome(state))
        assertEquals(listOf(0, 1, 2), state.winningLine())
    }

    @Test
    fun `coluna vence`() {
        val state = play(0, 1, 3, 4, 6)
        assertEquals(Outcome.Win(Seat.FIRST), TicTacToeGame.outcome(state))
        assertEquals(listOf(0, 3, 6), state.winningLine())
    }

    @Test
    fun `diagonal vence para a segunda cadeira`() {
        val state = play(1, 0, 2, 4, 6, 8)
        assertEquals(Outcome.Win(Seat.SECOND), TicTacToeGame.outcome(state))
        assertEquals(listOf(0, 4, 8), state.winningLine())
    }

    @Test
    fun `tabuleiro cheio sem linha e empate`() {
        val state = play(0, 1, 2, 4, 3, 5, 7, 6, 8)
        assertEquals(Outcome.Draw(DrawReason.FULL_BOARD), TicTacToeGame.outcome(state))
        assertTrue(TicTacToeGame.legalMoves(state).isEmpty())
    }

    @Test
    fun `depois de fechar a linha nao se joga mais`() {
        val won = play(0, 3, 1, 4, 2)
        assertTrue(TicTacToeGame.legalMoves(won).isEmpty())
        assertIs<MoveResult.Illegal>(TicTacToeGame.applyMove(won, TicTacToeMove(5)))
    }

    @Test
    fun `a notacao do lance identifica a casa`() {
        assertEquals("a3", TicTacToeMove(0).describe())
        assertEquals("c1", TicTacToeMove(8).describe())
        assertEquals("b2", TicTacToeMove(4).describe())
    }
}
