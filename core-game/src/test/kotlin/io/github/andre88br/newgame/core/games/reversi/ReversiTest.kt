package io.github.andre88br.newgame.core.games.reversi

import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import io.github.andre88br.newgame.core.engine.ReasonKey
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReversiTest {

    private fun boardOf(vararg rows: String): String {
        require(rows.size == REVERSI_SIZE)
        val cells = rows.joinToString("") { row -> row.filterNot { it.isWhitespace() } }
        require(cells.length == REVERSI_CELLS) { "vieram ${cells.length} casas" }
        return cells
    }

    private fun square(row: Int, col: Int) = row * REVERSI_SIZE + col

    private val start = ReversiGame.initialState(MatchConfig.DETERMINISTIC)

    @Test
    fun `a posicao inicial tem quatro pecas no centro e a vez das escuras`() {
        assertEquals(2, start.count(Seat.FIRST))
        assertEquals(2, start.count(Seat.SECOND))
        assertEquals(Seat.FIRST, start.turn)
        assertEquals(REVERSI_DARK, start.discAt(3, 4))
        assertEquals(REVERSI_DARK, start.discAt(4, 3))
        assertEquals(REVERSI_LIGHT, start.discAt(3, 3))
        assertEquals(REVERSI_LIGHT, start.discAt(4, 4))
    }

    @Test
    fun `as escuras abrem com quatro lances`() {
        val moves = ReversiGame.legalMoves(start).map { it.square }.toSet()
        assertEquals(
            setOf(square(2, 3), square(3, 2), square(4, 5), square(5, 4)),
            moves,
        )
    }

    @Test
    fun `jogar vira as pecas cercadas`() {
        val after = ReversiGame.applyOrThrow(start, ReversiMove(square(2, 3)))
        assertEquals(REVERSI_DARK, after.discAt(2, 3), "a peça jogada")
        assertEquals(REVERSI_DARK, after.discAt(3, 3), "a peça cercada deveria ter virado")
        assertEquals(4, after.count(Seat.FIRST))
        assertEquals(1, after.count(Seat.SECOND))
        assertEquals(Seat.SECOND, after.turn)
    }

    @Test
    fun `vira em varias direcoes de uma vez`() {
        // A casa do meio, jogada pelas escuras, cerca peças claras à esquerda, acima e na
        // diagonal ao mesmo tempo.
        val state = ReversiState(
            board = boardOf(
                ". . . . . . . .",
                ". X . . . . . .",
                ". . O . . . . .",
                ". X O . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
            ),
            turn = Seat.FIRST,
        )
        val after = ReversiGame.applyOrThrow(state, ReversiMove(square(3, 3)))
        assertEquals(REVERSI_DARK, after.discAt(3, 2), "virada na horizontal")
        assertEquals(REVERSI_DARK, after.discAt(2, 2), "virada na diagonal")
    }

    @Test
    fun `nao vale jogar sem cercar ninguem`() {
        val result = ReversiGame.applyMove(start, ReversiMove(square(0, 0)))
        assertIs<MoveResult.Illegal>(result)
        assertEquals(ReasonKey.REVERSI_NO_FLIP, result.reason.key)
    }

    @Test
    fun `nao vale jogar em casa ocupada`() {
        val result = ReversiGame.applyMove(start, ReversiMove(square(3, 3)))
        assertIs<MoveResult.Illegal>(result)
        assertEquals(ReasonKey.SQUARE_TAKEN, result.reason.key)
    }

    @Test
    fun `a sequencia de pecas so vira se terminar numa peca propria`() {
        // Fileira: escura, clara, clara, vazia. A ponta é vazia, então nada vira daquele lado.
        val state = ReversiState(
            board = boardOf(
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                "X O O . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
            ),
            turn = Seat.SECOND,
        )
        assertTrue(ReversiGame.flipsFor(state.board, Seat.SECOND, square(3, 3)).isEmpty())
    }

    @Test
    fun `quem nao tem lance perde a vez automaticamente`() {
        // Depois do lance das escuras não sobra peça clara nenhuma para as claras usarem
        // como âncora, então a vez volta para as escuras sem lance de passe explícito.
        val state = ReversiState(
            board = boardOf(
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                "X O . . . . . .",
            ),
            turn = Seat.FIRST,
        )
        val after = ReversiGame.applyOrThrow(state, ReversiMove(square(7, 2)))

        assertEquals(0, after.count(Seat.SECOND), "a única peça clara deveria ter virado")
        assertEquals(Outcome.Win(Seat.FIRST), ReversiGame.outcome(after))
    }

    @Test
    fun `a vez volta para quem jogou quando o adversario nao tem lance`() {
        // As claras não têm onde jogar depois deste lance, mas as escuras têm: a vez
        // continua com as escuras, e a partida não acabou.
        val state = ReversiState(
            board = boardOf(
                "X O . . . . . .",
                "O O . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
            ),
            turn = Seat.FIRST,
        )
        val moves = ReversiGame.legalMoves(state)
        assertTrue(moves.isNotEmpty(), "as escuras precisam ter lance nesta posição")

        val after = ReversiGame.applyOrThrow(state, moves.first())
        val clarasTemLance = ReversiGame.movesFor(after.board, Seat.SECOND).isNotEmpty()
        if (!clarasTemLance && !ReversiGame.outcome(after).isOver) {
            assertEquals(Seat.FIRST, after.turn, "sem lance para as claras, a vez volta")
        }
    }

    @Test
    fun `a partida acaba quando ninguem tem lance e vence quem tem mais pecas`() {
        val state = ReversiState(
            board = boardOf(
                "X X X X X X X X",
                "X X X X X X X X",
                "X X X X X X X X",
                "X X X X X X X X",
                "O O O O O O O O",
                "O O O O O O O O",
                "O O O O O O O O",
                "O O O O O X X X",
            ),
            turn = Seat.FIRST,
        )
        assertTrue(ReversiGame.legalMoves(state).isEmpty())
        assertEquals(Outcome.Win(Seat.FIRST), ReversiGame.outcome(state))
    }

    @Test
    fun `tabuleiro cheio e empatado da empate`() {
        val half = "X".repeat(32) + "O".repeat(32)
        val state = ReversiState(board = half, turn = Seat.FIRST)
        assertEquals(Outcome.Draw(DrawReason.FULL_BOARD), ReversiGame.outcome(state))
    }

    @Test
    fun `ficar sem pecas perde na hora`() {
        val state = ReversiState(
            board = boardOf(
                "O . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
            ),
            turn = Seat.FIRST,
        )
        assertEquals(Outcome.Win(Seat.SECOND), ReversiGame.outcome(state))
    }

    @Test
    fun `uma partida inteira entre duas IAs termina com o tabuleiro coerente`() {
        var state = start
        var guard = 0
        while (!ReversiGame.outcome(state).isOver && guard++ < 80) {
            val move = ReversiAi.chooseMove(state, Difficulty.EASY, seed = guard.toLong())
            assertTrue(move != null, "sem lance com a partida em andamento:\n$state")
            state = ReversiGame.applyOrThrow(state, move)
        }

        assertTrue(ReversiGame.outcome(state).isOver, "a partida não terminou:\n$state")
        assertEquals(
            REVERSI_CELLS,
            state.count(Seat.FIRST) + state.count(Seat.SECOND) + state.emptyCount,
            "as contas do tabuleiro não fecham",
        )
    }

    @Test
    fun `a notacao identifica a casa`() {
        assertEquals("a8", ReversiMove(square(0, 0)).describe())
        assertEquals("h1", ReversiMove(square(7, 7)).describe())
        assertEquals("d5", ReversiMove(square(3, 3)).describe())
    }
}

private typealias Difficulty = io.github.andre88br.newgame.core.ai.Difficulty
