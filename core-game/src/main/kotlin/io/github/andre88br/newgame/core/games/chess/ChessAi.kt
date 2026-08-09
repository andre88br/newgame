package io.github.andre88br.newgame.core.games.chess

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.MoveOrdering
import io.github.andre88br.newgame.core.ai.SearchBasedAi
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.engine.Seat

/**
 * Avaliação de posição do xadrez: material mais tabelas peça-casa.
 *
 * As tabelas dizem o quanto cada peça gosta de cada casa — cavalo no centro vale mais que
 * cavalo na borda, peão avançado vale mais que peão parado, rei quer canto na abertura e
 * centro no final. É o mínimo para a IA jogar de forma reconhecível: só material faria ela
 * mexer peças ao acaso enquanto ninguém captura nada.
 *
 * Os valores seguem a "simplified evaluation function" do Chess Programming Wiki, que é a
 * referência conhecida para este nível de motor. As tabelas são escritas do ponto de vista
 * das brancas e espelhadas para as pretas.
 */
object ChessEvaluator : Evaluator<ChessState> {

    private const val PAWN = 100
    private const val KNIGHT = 320
    private const val BISHOP = 330
    private const val ROOK = 500
    private const val QUEEN = 900

    /** Abaixo deste material sem peões, o rei deve sair do canto e ajudar. */
    private const val ENDGAME_MATERIAL = 1_300

    private val PAWN_TABLE = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0,
    )

    private val KNIGHT_TABLE = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    )

    private val BISHOP_TABLE = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20,
    )

    private val ROOK_TABLE = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0,
    )

    private val QUEEN_TABLE = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20,
    )

    private val KING_MIDDLE_TABLE = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20,
    )

    private val KING_END_TABLE = intArrayOf(
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50,
    )

    fun valueOf(kind: Char): Int = when (kind) {
        'P' -> PAWN
        'N' -> KNIGHT
        'B' -> BISHOP
        'R' -> ROOK
        'Q' -> QUEEN
        else -> 0
    }

    override fun evaluate(state: ChessState, seat: Seat): Int {
        var score = 0
        var nonPawnMaterial = 0
        var whiteBishops = 0
        var blackBishops = 0

        for (square in 0 until CHESS_CELLS) {
            val piece = state.board[square]
            if (piece == CHESS_EMPTY) continue
            val owner = piece.pieceSeat() ?: continue
            val kind = piece.pieceKind()
            if (kind != 'P' && kind != 'K') nonPawnMaterial += valueOf(kind)
            if (kind == 'B') {
                if (owner == Seat.FIRST) whiteBishops++ else blackBishops++
            }
        }
        val endgame = nonPawnMaterial <= ENDGAME_MATERIAL

        for (square in 0 until CHESS_CELLS) {
            val piece = state.board[square]
            if (piece == CHESS_EMPTY) continue
            val owner = piece.pieceSeat() ?: continue
            val kind = piece.pieceKind()

            // As tabelas são escritas para as brancas; para as pretas, espelha-se a linha.
            val view = if (owner == Seat.FIRST) square else mirrored(square)
            val positional = when (kind) {
                'P' -> PAWN_TABLE[view]
                'N' -> KNIGHT_TABLE[view]
                'B' -> BISHOP_TABLE[view]
                'R' -> ROOK_TABLE[view]
                'Q' -> QUEEN_TABLE[view]
                'K' -> if (endgame) KING_END_TABLE[view] else KING_MIDDLE_TABLE[view]
                else -> 0
            }

            val value = valueOf(kind) + positional
            score += if (owner == seat) value else -value
        }

        // Par de bispos: vale mais que a soma das partes, sobretudo com o tabuleiro aberto.
        val bishopPair = (if (whiteBishops >= 2) 30 else 0) - (if (blackBishops >= 2) 30 else 0)
        score += if (seat == Seat.FIRST) bishopPair else -bishopPair

        return score
    }

    private fun mirrored(square: Int): Int =
        chessSquare(CHESS_SIZE - 1 - chessRow(square), chessCol(square))
}

/**
 * Ordem de exame: promoções, depois capturas pela regra "vítima valiosa, agressor barato".
 *
 * Capturar a dama com um peão é o primeiro lance que vale a pena olhar; capturar um peão
 * com a dama, o último. Ordenar assim faz a poda cortar a maior parte da árvore antes de
 * chegar nela.
 */
val ChessOrdering: MoveOrdering<ChessState, ChessMove> =
    MoveOrdering<ChessState, ChessMove> { state, moves ->
        if (moves.size < 2) {
            moves
        } else {
            moves.sortedByDescending { move ->
                var score = 0
                move.promotion?.let { score += 900 + ChessEvaluator.valueOf(it) }
                val victim = state.board[move.to]
                if (victim != CHESS_EMPTY) {
                    val attacker = state.board[move.from]
                    score += 1_000 + ChessEvaluator.valueOf(victim.pieceKind()) -
                        ChessEvaluator.valueOf(attacker.pieceKind()) / 10
                } else if (state.board[move.from].isPawn() && move.to == state.enPassant) {
                    score += 1_000 + ChessEvaluator.valueOf('P')
                }
                score
            }
        }
    }

val ChessAi: SearchBasedAi<ChessState, ChessMove> = SearchBasedAi(
    game = ChessGame,
    evaluator = ChessEvaluator,
    ordering = ChessOrdering,
    limits = { difficulty ->
        when (difficulty) {
            Difficulty.EASY -> SearchLimits(maxDepth = 2, timeBudgetMillis = 500)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 4, timeBudgetMillis = 1_500)
            Difficulty.HARD -> SearchLimits(maxDepth = 20, timeBudgetMillis = 3_500)
        }
    },
    // Sem quiescência, a IA avalia a posição no meio de uma troca e "ganha" uma peça que
    // perde no lance seguinte. No xadrez isso não é um detalhe: é a diferença entre jogar e
    // entregar peça.
    isTactical = { state, move -> move.promotion != null || ChessMoves.isCapture(state, move) },
)
