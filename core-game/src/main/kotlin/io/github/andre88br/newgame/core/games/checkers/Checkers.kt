package io.github.andre88br.newgame.core.games.checkers

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

@Serializable
data class CheckersState(
    /** 64 caracteres, linha 0 no topo. Veja `CheckersBoard.kt`. */
    val board: String = initialBoard(),
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    /**
     * Meios-lances desde a última captura ou avanço de pedra.
     *
     * Só damas andando de um lado para o outro não leva a partida a lugar nenhum; a regra
     * brasileira declara empate depois de 20 lances de cada lado sem progresso.
     */
    val idlePlies: Int = 0,
) : GameState {

    init {
        require(board.length == BOARD_CELLS) {
            "O tabuleiro tem $BOARD_CELLS casas, veio ${board.length}"
        }
    }

    fun pieceAt(index: Int): Char = board[index]

    fun pieceAt(row: Int, col: Int): Char = board[squareAt(row, col)]

    /** Casas ocupadas por [seat], úteis para a tela destacar as peças de quem joga. */
    fun squaresOf(seat: Seat): List<Int> =
        (0 until BOARD_CELLS).filter { board[it].pieceOwner() == seat }

    fun countPieces(seat: Seat): Int = board.count { it.pieceOwner() == seat }

    fun countKings(seat: Seat): Int =
        board.count { it.pieceOwner() == seat && it.isKing() }

    override fun toString(): String = renderBoard(board)
}

@Serializable
data class CheckersMove(
    val from: Int,
    /** Casas em que a peça pousa, em ordem. Lance simples tem uma só. */
    val path: List<Int>,
    /** Casas das peças capturadas, na ordem em que foram saltadas. */
    val captured: List<Int> = emptyList(),
) : Move {

    init {
        require(path.isNotEmpty()) { "Um lance precisa de ao menos uma casa de destino" }
    }

    val to: Int get() = path.last()

    val isCapture: Boolean get() = captured.isNotEmpty()

    /** Notação PDN: `23-18` para lance simples, `23x18x9` para captura em sequência. */
    override fun describe(): String {
        val separator = if (isCapture) "x" else "-"
        return (listOf(from) + path).joinToString(separator) { pdnNumber(it).toString() }
    }
}

object CheckersGame : BoardGame<CheckersState, CheckersMove> {

    /** 20 lances de cada lado sem captura nem avanço de pedra encerram a partida em empate. */
    const val IDLE_PLY_LIMIT: Int = 40

    override val id: GameId = GameId.CHECKERS

    override fun initialState(config: MatchConfig): CheckersState = CheckersState()

    override fun legalMoves(state: CheckersState): List<CheckersMove> {
        if (state.idlePlies >= IDLE_PLY_LIMIT) return emptyList()
        return CheckersMoves.legal(state.board, state.turn)
    }

    override fun applyMove(state: CheckersState, move: CheckersMove): MoveResult<CheckersState> {
        val legal = legalMoves(state)
        if (legal.isEmpty()) return MoveResult.Illegal("A partida já terminou")
        if (move !in legal) return MoveResult.Illegal(rejectionReason(state, move))
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    override fun applyKnownLegal(state: CheckersState, move: CheckersMove): CheckersState {
        val cells = state.board.toCharArray()
        val piece = cells[move.from]
        cells[move.from] = EMPTY
        for (square in move.captured) cells[square] = EMPTY

        // A promoção só acontece quando o lance termina na última fileira: pedra que
        // apenas passa por lá no meio de uma sequência de capturas continua pedra.
        val landed = if (piece.isMan() && rowOf(move.to) == state.turn.promotionRow()) {
            state.turn.kingChar()
        } else {
            piece
        }
        cells[move.to] = landed

        val progress = move.isCapture || piece.isMan()
        return CheckersState(
            board = String(cells),
            turn = state.turn.opponent(),
            ply = state.ply + 1,
            idlePlies = if (progress) 0 else state.idlePlies + 1,
        )
    }

    override fun outcome(state: CheckersState): Outcome {
        if (state.countPieces(Seat.FIRST) == 0) return Outcome.Win(Seat.SECOND)
        if (state.countPieces(Seat.SECOND) == 0) return Outcome.Win(Seat.FIRST)
        if (state.idlePlies >= IDLE_PLY_LIMIT) return Outcome.Draw(DrawReason.NO_PROGRESS)
        // Nas damas, ficar sem lance é derrota — não empate como no xadrez.
        if (CheckersMoves.legal(state.board, state.turn).isEmpty()) {
            return Outcome.Win(state.turn.opponent())
        }
        return Outcome.InProgress
    }

    /**
     * Por que o lance foi recusado. Vale o trabalho: "captura é obrigatória" é a dúvida
     * número um de quem está aprendendo, e a tela pode mostrar o motivo direto.
     */
    private fun rejectionReason(state: CheckersState, move: CheckersMove): String {
        val piece = state.board.getOrNull(move.from) ?: return "Casa de origem inválida"
        if (piece.pieceOwner() != state.turn) return "Não há peça sua na casa ${pdnNumber(move.from)}"

        val captures = CheckersMoves.captures(state.board, state.turn)
        if (captures.isEmpty()) return "Lance não permitido para esta peça"

        val most = captures.maxOf { it.captured.size }
        if (!move.isCapture) {
            return "Captura é obrigatória: existe lance que captura $most peça(s)"
        }
        if (move.captured.size < most) {
            return "É obrigatório capturar o máximo: $most peça(s), e este lance captura " +
                "${move.captured.size}"
        }
        return "Sequência de captura inválida"
    }

    override val stateSerializer: KSerializer<CheckersState> = serializer()
    override val moveSerializer: KSerializer<CheckersMove> = serializer()
}

/** Conveniência para a tela: os lances legais que partem de [square]. */
fun CheckersGame.movesFrom(state: CheckersState, square: Int): List<CheckersMove> =
    legalMoves(state).filter { it.from == square }
