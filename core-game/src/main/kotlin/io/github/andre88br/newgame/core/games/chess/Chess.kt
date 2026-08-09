package io.github.andre88br.newgame.core.games.chess

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
import kotlin.math.abs

@Serializable
data class ChessState(
    /** 64 caracteres começando em a8. Maiúsculas são brancas. Veja `ChessBoard.kt`. */
    val board: String,
    override val turn: Seat,
    override val ply: Int,
    /** Direitos de roque ainda existentes, em `KQkq`. Vazio quando não há nenhum. */
    val castling: String,
    /** Casa que um peão pularia ao capturar en passant, ou [NO_SQUARE]. */
    val enPassant: Int,
    /** Meios-lances desde a última captura ou avanço de peão — a regra dos 50 lances. */
    val halfmoveClock: Int,
) : GameState {

    init {
        require(board.length == CHESS_CELLS) {
            "O tabuleiro tem $CHESS_CELLS casas, veio ${board.length}"
        }
    }

    fun pieceAt(square: Int): Char = board[square]

    fun pieceAt(row: Int, col: Int): Char = board[chessSquare(row, col)]

    /** O rei de [seat] está atacado? */
    fun inCheck(seat: Seat): Boolean = ChessMoves.inCheck(this, seat)

    override fun toString(): String = renderChessBoard(board) + "\n" + toFen()
}

@Serializable
data class ChessMove(
    val from: Int,
    val to: Int,
    /** Peça escolhida na promoção, sempre em maiúscula (`Q`, `R`, `B`, `N`). */
    val promotion: Char? = null,
) : Move {

    init {
        require(from in 0 until CHESS_CELLS && to in 0 until CHESS_CELLS) {
            "Casa fora do tabuleiro: $from -> $to"
        }
        require(promotion == null || promotion in "QRBN") {
            "Promoção inválida: $promotion"
        }
    }

    /**
     * Notação algébrica longa: `e2e4`, `e7e8q`.
     *
     * Não é a notação curta que se vê nos livros (`Cf3`, `exd5`), porque essa depende da
     * posição para saber se precisa desambiguar — e um lance, aqui, não carrega o tabuleiro
     * junto. A longa é sem ambiguidade e serve para conferir uma partida.
     */
    override fun describe(): String =
        squareName(from) + squareName(to) + (promotion?.lowercaseChar() ?: "")
}

object ChessGame : BoardGame<ChessState, ChessMove> {

    /** 50 lances de cada lado sem captura nem avanço de peão. */
    const val HALFMOVE_LIMIT: Int = 100

    override val id: GameId = GameId.CHESS

    override fun initialState(config: MatchConfig): ChessState =
        chessStateFromFen(CHESS_START_FEN)

    override fun legalMoves(state: ChessState): List<ChessMove> = ChessMoves.legal(state)

    override fun applyMove(state: ChessState, move: ChessMove): MoveResult<ChessState> {
        val legal = ChessMoves.legal(state)
        if (move !in legal) return MoveResult.Illegal(rejectionReason(state, move, legal))
        if (outcome(state).isOver) return MoveResult.Illegal("A partida já terminou")
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    override fun applyKnownLegal(state: ChessState, move: ChessMove): ChessState {
        val piece = state.board[move.from]
        val captured = ChessMoves.isCapture(state, move)
        val board = ChessMoves.boardAfter(state, move)

        // Só o avanço duplo do peão cria uma casa de en passant, e ela vale por um lance só.
        val enPassant = if (piece.isPawn() && abs(chessRow(move.to) - chessRow(move.from)) == 2) {
            chessSquare((chessRow(move.from) + chessRow(move.to)) / 2, chessCol(move.from))
        } else {
            NO_SQUARE
        }

        return ChessState(
            board = board,
            turn = state.turn.opponent(),
            ply = state.ply + 1,
            castling = updatedCastling(state, move, piece),
            enPassant = enPassant,
            halfmoveClock = if (piece.isPawn() || captured) 0 else state.halfmoveClock + 1,
        )
    }

    override fun outcome(state: ChessState): Outcome {
        if (ChessMoves.legal(state).isEmpty()) {
            // Sem lance com o rei atacado é mate; sem lance com o rei seguro é afogamento,
            // que é empate. A diferença entre ganhar e empatar mora nesta linha.
            return if (state.inCheck(state.turn)) {
                Outcome.Win(state.turn.opponent())
            } else {
                Outcome.Draw(DrawReason.STALEMATE)
            }
        }
        if (state.halfmoveClock >= HALFMOVE_LIMIT) return Outcome.Draw(DrawReason.NO_PROGRESS)
        if (insufficientMaterial(state.board)) {
            return Outcome.Draw(DrawReason.INSUFFICIENT_MATERIAL)
        }
        return Outcome.InProgress
    }

    /**
     * Chave de posição para a repetição tripla.
     *
     * A repetição é propriedade da partida, não da posição: só olhando um estado não dá
     * para saber quantas vezes ele já apareceu. Por isso o motor devolve a chave e quem
     * guarda o histórico — a `MatchSession` — faz a contagem. Ficam de fora o relógio dos
     * 50 lances e o número do lance, que mudam sempre e impediriam qualquer repetição de
     * ser reconhecida.
     */
    override fun repetitionKey(state: ChessState): String =
        "${state.board}|${state.turn.index}|${state.castling}|${state.enPassant}"

    override val stateSerializer: KSerializer<ChessState> = serializer()
    override val moveSerializer: KSerializer<ChessMove> = serializer()

    /** Lances legais que saem de [square] — o que a tela precisa ao escolher uma peça. */
    fun movesFrom(state: ChessState, square: Int): List<ChessMove> =
        ChessMoves.legal(state).filter { it.from == square }

    private fun rejectionReason(
        state: ChessState,
        move: ChessMove,
        legal: List<ChessMove>,
    ): String {
        val piece = state.board.getOrNull(move.from) ?: return "Casa de origem inválida"
        if (piece == CHESS_EMPTY) return "Não há peça nessa casa"
        if (piece.pieceSeat() != state.turn) return "Essa peça não é sua"

        // O caso que mais confunde: o lance seria natural, mas deixaria o rei em xeque.
        val pseudo = ChessMoves.pseudoLegal(state).any { it.from == move.from && it.to == move.to }
        if (pseudo) {
            return if (state.inCheck(state.turn)) {
                "Seu rei está em xeque: o lance precisa resolver isso"
            } else {
                "Esse lance deixaria seu rei em xeque"
            }
        }

        if (legal.none { it.from == move.from }) return "Essa peça não tem lance disponível"
        return "Essa peça não pode ir para aí"
    }

    /**
     * Direitos de roque depois do lance.
     *
     * Some o direito quando o rei anda, quando a torre sai do canto e — o caso esquecido com
     * frequência — quando a torre é **capturada** no canto, que também tira o direito do
     * adversário.
     */
    private fun updatedCastling(state: ChessState, move: ChessMove, piece: Char): String {
        if (state.castling.isEmpty()) return ""
        var rights = state.castling

        if (piece.isKing()) {
            val movingWhite = state.turn == Seat.FIRST
            rights = rights.filterNot { it.isUpperCase() == movingWhite }
        }
        cornerRight(move.from)?.let { rights = rights.filterNot { right -> right == it } }
        cornerRight(move.to)?.let { rights = rights.filterNot { right -> right == it } }

        return rights
    }

    /** O direito de roque associado a cada canto do tabuleiro. */
    private fun cornerRight(square: Int): Char? = when (square) {
        0 -> 'q' // a8
        7 -> 'k' // h8
        56 -> 'Q' // a1
        63 -> 'K' // h1
        else -> null
    }

    /**
     * Material insuficiente para dar mate: rei contra rei, rei e peça menor contra rei, e
     * dois bispos de casas da mesma cor. Dois cavalos contra rei ficam de fora de propósito
     * — não dá mate forçado, mas as regras não declaram empate automático.
     */
    private fun insufficientMaterial(board: String): Boolean {
        var minors = 0
        val bishopSquareColors = ArrayList<Int>(2)

        for (square in 0 until CHESS_CELLS) {
            val piece = board[square]
            if (piece == CHESS_EMPTY) continue
            when (piece.pieceKind()) {
                'K' -> Unit
                'B' -> {
                    minors++
                    bishopSquareColors += (chessRow(square) + chessCol(square)) % 2
                }
                'N' -> minors++
                // Peão, torre ou dama: sempre há como dar mate.
                else -> return false
            }
        }

        if (minors <= 1) return true
        if (minors == 2 && bishopSquareColors.size == 2) {
            return bishopSquareColors[0] == bishopSquareColors[1]
        }
        return false
    }
}
