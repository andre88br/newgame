package io.github.andre88br.newgame.core.games.tictactoe

import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** Casa vazia no tabuleiro do jogo da velha. */
const val EMPTY_CELL: Int = -1

@Serializable
data class TicTacToeState(
    /** 9 casas, em linhas de cima para baixo. [EMPTY_CELL] ou o índice da cadeira dona da marca. */
    val cells: List<Int>,
    override val turn: Seat,
    override val ply: Int,
) : GameState {

    init {
        require(cells.size == 9) { "O tabuleiro tem 9 casas, veio ${cells.size}" }
    }

    fun at(row: Int, col: Int): Int = cells[row * 3 + col]

    fun isEmpty(cell: Int): Boolean = cells[cell] == EMPTY_CELL

    /** Cadeira que fechou uma linha, ou `null`. */
    fun winner(): Seat? {
        for (line in LINES) {
            val first = cells[line[0]]
            if (first != EMPTY_CELL && first == cells[line[1]] && first == cells[line[2]]) {
                return Seat(first)
            }
        }
        return null
    }

    /** As três casas da linha vencedora, para a tela destacar. Vazio se ninguém venceu. */
    fun winningLine(): List<Int> =
        LINES.firstOrNull { line ->
            val first = cells[line[0]]
            first != EMPTY_CELL && first == cells[line[1]] && first == cells[line[2]]
        }?.toList().orEmpty()

    override fun toString(): String =
        (0 until 3).joinToString("\n") { row ->
            (0 until 3).joinToString(" ") { col ->
                when (at(row, col)) {
                    0 -> "X"
                    1 -> "O"
                    else -> "."
                }
            }
        }

    companion object {
        /** As oito linhas que fecham o jogo: 3 horizontais, 3 verticais, 2 diagonais. */
        val LINES: List<IntArray> = listOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
        )
    }
}

@Serializable
data class TicTacToeMove(val cell: Int) : Move {
    init {
        require(cell in 0..8) { "Casa fora do tabuleiro: $cell" }
    }

    override fun describe(): String = "${'a' + (cell % 3)}${3 - cell / 3}"
}

object TicTacToeGame : BoardGame<TicTacToeState, TicTacToeMove> {

    override val id: GameId = GameId.TIC_TAC_TOE

    override fun initialState(config: MatchConfig): TicTacToeState =
        TicTacToeState(cells = List(9) { EMPTY_CELL }, turn = Seat.FIRST, ply = 0)

    override fun legalMoves(state: TicTacToeState): List<TicTacToeMove> {
        if (outcome(state).isOver) return emptyList()
        return (0..8).filter { state.isEmpty(it) }.map(::TicTacToeMove)
    }

    override fun applyMove(state: TicTacToeState, move: TicTacToeMove): MoveResult<TicTacToeState> {
        if (outcome(state).isOver) return MoveResult.Illegal("A partida já terminou")
        if (!state.isEmpty(move.cell)) return MoveResult.Illegal("A casa ${move.cell} já está ocupada")
        val cells = state.cells.toMutableList()
        cells[move.cell] = state.turn.index
        return MoveResult.Ok(
            state.copy(cells = cells, turn = state.turn.opponent(), ply = state.ply + 1),
        )
    }

    override fun outcome(state: TicTacToeState): Outcome {
        state.winner()?.let { return Outcome.Win(it) }
        return if (state.cells.none { it == EMPTY_CELL }) {
            Outcome.Draw(DrawReason.FULL_BOARD)
        } else {
            Outcome.InProgress
        }
    }

    override val stateSerializer: KSerializer<TicTacToeState> = serializer()
    override val moveSerializer: KSerializer<TicTacToeMove> = serializer()
}
