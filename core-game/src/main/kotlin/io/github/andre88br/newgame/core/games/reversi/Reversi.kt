package io.github.andre88br.newgame.core.games.reversi

import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.ReasonKey
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

const val REVERSI_SIZE: Int = 8
const val REVERSI_CELLS: Int = REVERSI_SIZE * REVERSI_SIZE

const val REVERSI_EMPTY: Char = '.'
const val REVERSI_DARK: Char = 'X' // primeira cadeira, começa jogando
const val REVERSI_LIGHT: Char = 'O'

fun Char.discOwner(): Seat? = when (this) {
    REVERSI_DARK -> Seat.FIRST
    REVERSI_LIGHT -> Seat.SECOND
    else -> null
}

fun Seat.discChar(): Char = if (this == Seat.FIRST) REVERSI_DARK else REVERSI_LIGHT

/** As oito direções em que uma peça pode cercar as do adversário. */
internal val REVERSI_DIRECTIONS: List<Pair<Int, Int>> = listOf(
    -1 to -1, -1 to 0, -1 to 1,
    0 to -1, 0 to 1,
    1 to -1, 1 to 0, 1 to 1,
)

internal fun reversiShift(index: Int, dRow: Int, dCol: Int): Int? {
    val row = index / REVERSI_SIZE + dRow
    val col = index % REVERSI_SIZE + dCol
    return if (row in 0 until REVERSI_SIZE && col in 0 until REVERSI_SIZE) {
        row * REVERSI_SIZE + col
    } else {
        null
    }
}

@Serializable
data class ReversiState(
    /** 64 caracteres, linha 0 no topo. */
    val board: String = initialReversiBoard(),
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
) : GameState {

    init {
        require(board.length == REVERSI_CELLS) {
            "O tabuleiro tem $REVERSI_CELLS casas, veio ${board.length}"
        }
    }

    fun discAt(index: Int): Char = board[index]

    fun discAt(row: Int, col: Int): Char = board[row * REVERSI_SIZE + col]

    fun count(seat: Seat): Int = board.count { it.discOwner() == seat }

    val emptyCount: Int get() = board.count { it == REVERSI_EMPTY }

    override fun toString(): String =
        (0 until REVERSI_SIZE).joinToString("\n") { row ->
            board.substring(row * REVERSI_SIZE, (row + 1) * REVERSI_SIZE).toCharArray()
                .joinToString(" ")
        }
}

@Serializable
data class ReversiMove(val square: Int) : Move {

    init {
        require(square in 0 until REVERSI_CELLS) { "Casa fora do tabuleiro: $square" }
    }

    /** Notação usual do reversi: coluna `a`–`h` e linha `1`–`8`, contando de baixo. */
    override fun describe(): String {
        val row = square / REVERSI_SIZE
        val col = square % REVERSI_SIZE
        return "${'a' + col}${REVERSI_SIZE - row}"
    }
}

fun initialReversiBoard(): String {
    val cells = CharArray(REVERSI_CELLS) { REVERSI_EMPTY }
    cells[3 * REVERSI_SIZE + 3] = REVERSI_LIGHT
    cells[3 * REVERSI_SIZE + 4] = REVERSI_DARK
    cells[4 * REVERSI_SIZE + 3] = REVERSI_DARK
    cells[4 * REVERSI_SIZE + 4] = REVERSI_LIGHT
    return String(cells)
}

/**
 * Reversi (Othello).
 *
 * O detalhe que molda o desenho é o **passe**: quem não tem lance perde a vez, e a partida
 * só acaba quando nenhum dos dois pode jogar. Em vez de tratar o passe como um lance
 * especial — que a tela teria de saber jogar sozinha —, [applyMove] já devolve o estado com
 * a vez de quem realmente pode jogar. Assim `legalMoves` nunca volta vazio numa partida em
 * andamento, e nenhuma camada acima precisa conhecer a regra.
 *
 * A busca da IA aguenta isso porque pontua sempre do ponto de vista de quem pediu a busca,
 * em vez de assumir que a vez alterna a cada lance.
 */
object ReversiGame : BoardGame<ReversiState, ReversiMove> {

    override val id: GameId = GameId.REVERSI

    override fun initialState(config: MatchConfig): ReversiState = ReversiState()

    override fun legalMoves(state: ReversiState): List<ReversiMove> =
        movesFor(state.board, state.turn)

    override fun applyMove(state: ReversiState, move: ReversiMove): MoveResult<ReversiState> {
        if (state.board[move.square] != REVERSI_EMPTY) {
            return MoveResult.Illegal(ReasonKey.SQUARE_TAKEN)
        }
        val flipped = flipsFor(state.board, state.turn, move.square)
        if (flipped.isEmpty()) {
            return MoveResult.Illegal(ReasonKey.REVERSI_NO_FLIP)
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    override fun applyKnownLegal(state: ReversiState, move: ReversiMove): ReversiState {
        val cells = state.board.toCharArray()
        val disc = state.turn.discChar()
        cells[move.square] = disc
        for (square in flipsFor(state.board, state.turn, move.square)) {
            cells[square] = disc
        }
        val board = String(cells)

        // Passa a vez para quem tem lance. Se o adversário não tiver, e quem jogou tiver,
        // joga de novo; se nenhum tiver, a partida acabou e a vez perde o sentido.
        val opponent = state.turn.opponent()
        val next = when {
            movesFor(board, opponent).isNotEmpty() -> opponent
            movesFor(board, state.turn).isNotEmpty() -> state.turn
            else -> opponent
        }

        return ReversiState(board = board, turn = next, ply = state.ply + 1)
    }

    override fun outcome(state: ReversiState): Outcome {
        val dark = state.count(Seat.FIRST)
        val light = state.count(Seat.SECOND)
        if (dark == 0) return Outcome.Win(Seat.SECOND)
        if (light == 0) return Outcome.Win(Seat.FIRST)

        val over = movesFor(state.board, Seat.FIRST).isEmpty() &&
            movesFor(state.board, Seat.SECOND).isEmpty()
        if (!over) return Outcome.InProgress

        return when {
            dark > light -> Outcome.Win(Seat.FIRST)
            light > dark -> Outcome.Win(Seat.SECOND)
            else -> Outcome.Draw(DrawReason.FULL_BOARD)
        }
    }

    override val stateSerializer: KSerializer<ReversiState> = serializer()
    override val moveSerializer: KSerializer<ReversiMove> = serializer()

    /** Casas em que [seat] pode jogar. */
    fun movesFor(board: String, seat: Seat): List<ReversiMove> {
        val found = ArrayList<ReversiMove>()
        for (square in 0 until REVERSI_CELLS) {
            if (board[square] != REVERSI_EMPTY) continue
            if (hasAnyFlip(board, seat, square)) found += ReversiMove(square)
        }
        return found
    }

    /** As peças que virariam se [seat] jogasse em [square]. Vazio quando o lance é inválido. */
    fun flipsFor(board: String, seat: Seat, square: Int): List<Int> {
        if (board[square] != REVERSI_EMPTY) return emptyList()
        val opponent = seat.opponent()
        val flipped = ArrayList<Int>()

        for ((dRow, dCol) in REVERSI_DIRECTIONS) {
            val line = ArrayList<Int>()
            var scan = reversiShift(square, dRow, dCol)
            while (scan != null && board[scan].discOwner() == opponent) {
                line += scan
                scan = reversiShift(scan, dRow, dCol)
            }
            // Só vira se a sequência de peças adversárias terminar numa peça própria.
            if (line.isNotEmpty() && scan != null && board[scan].discOwner() == seat) {
                flipped += line
            }
        }
        return flipped
    }

    private fun hasAnyFlip(board: String, seat: Seat, square: Int): Boolean {
        val opponent = seat.opponent()
        for ((dRow, dCol) in REVERSI_DIRECTIONS) {
            var scan = reversiShift(square, dRow, dCol) ?: continue
            if (board[scan].discOwner() != opponent) continue
            while (true) {
                scan = reversiShift(scan, dRow, dCol) ?: break
                when (board[scan].discOwner()) {
                    opponent -> continue
                    seat -> return true
                    else -> break
                }
            }
        }
        return false
    }
}
