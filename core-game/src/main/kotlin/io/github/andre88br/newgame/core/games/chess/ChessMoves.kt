package io.github.andre88br.newgame.core.games.chess

import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent

/**
 * Geração de lances do xadrez.
 *
 * A estratégia é a mais simples que é correta: gerar os lances pseudo-legais de cada peça e
 * depois descartar os que deixam o próprio rei em xeque, aplicando o lance num tabuleiro
 * temporário. Existem formas mais rápidas — pinos e xeques calculados de antemão —, mas
 * elas são difíceis de acertar, e o `perft` deste projeto mostra que esta versão dá conta
 * do que um celular precisa.
 */
internal object ChessMoves {

    private val KNIGHT_JUMPS = listOf(
        -2 to -1, -2 to 1, -1 to -2, -1 to 2,
        1 to -2, 1 to 2, 2 to -1, 2 to 1,
    )
    private val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    private val ORTHOGONALS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    private val ALL_AROUND = DIAGONALS + ORTHOGONALS

    /** Lances legais: pseudo-legais menos os que deixam o rei em xeque. */
    fun legal(state: ChessState): List<ChessMove> =
        pseudoLegal(state).filter { move ->
            val after = boardAfter(state, move)
            val king = kingSquare(after, state.turn)
            king == NO_SQUARE || !isAttacked(after, king, state.turn.opponent())
        }

    /** Só os lances que capturam ou promovem — o que a busca de quiescência precisa ver. */
    fun tactical(state: ChessState): List<ChessMove> =
        legal(state).filter { it.promotion != null || isCapture(state, it) }

    fun isCapture(state: ChessState, move: ChessMove): Boolean =
        state.board[move.to] != CHESS_EMPTY ||
            (state.board[move.from].isPawn() && move.to == state.enPassant)

    fun pseudoLegal(state: ChessState): List<ChessMove> {
        val moves = ArrayList<ChessMove>(48)
        val board = state.board
        val seat = state.turn

        for (from in 0 until CHESS_CELLS) {
            val piece = board[from]
            if (piece.pieceSeat() != seat) continue
            when (piece.pieceKind()) {
                'P' -> pawnMoves(state, from, moves)
                'N' -> jumpMoves(board, seat, from, KNIGHT_JUMPS, moves)
                'B' -> slideMoves(board, seat, from, DIAGONALS, moves)
                'R' -> slideMoves(board, seat, from, ORTHOGONALS, moves)
                'Q' -> slideMoves(board, seat, from, ALL_AROUND, moves)
                'K' -> {
                    jumpMoves(board, seat, from, ALL_AROUND, moves)
                    castlingMoves(state, from, moves)
                }
            }
        }
        return moves
    }

    private fun pawnMoves(state: ChessState, from: Int, out: MutableList<ChessMove>) {
        val board = state.board
        val seat = state.turn
        val forward = seat.pawnForward()

        val ahead = chessShift(from, forward, 0)
        if (ahead != null && board[ahead] == CHESS_EMPTY) {
            addPawnMove(seat, from, ahead, out)
            // Avanço duplo: só da fileira inicial e só com as duas casas livres.
            if (chessRow(from) == seat.pawnStartRank()) {
                val twoAhead = chessShift(from, forward * 2, 0)
                if (twoAhead != null && board[twoAhead] == CHESS_EMPTY) {
                    out += ChessMove(from, twoAhead)
                }
            }
        }

        for (dCol in intArrayOf(-1, 1)) {
            val target = chessShift(from, forward, dCol) ?: continue
            val occupant = board[target]
            val isEnPassant = target == state.enPassant && occupant == CHESS_EMPTY
            if (occupant.pieceSeat() == seat.opponent() || isEnPassant) {
                addPawnMove(seat, from, target, out)
            }
        }
    }

    /** Um lance de peão que chega à última fileira vira quatro: dama, torre, bispo e cavalo. */
    private fun addPawnMove(seat: Seat, from: Int, to: Int, out: MutableList<ChessMove>) {
        if (chessRow(to) == seat.promotionRank()) {
            for (kind in charArrayOf('Q', 'R', 'B', 'N')) {
                out += ChessMove(from, to, kind)
            }
        } else {
            out += ChessMove(from, to)
        }
    }

    private fun jumpMoves(
        board: String,
        seat: Seat,
        from: Int,
        offsets: List<Pair<Int, Int>>,
        out: MutableList<ChessMove>,
    ) {
        for ((dRow, dCol) in offsets) {
            val to = chessShift(from, dRow, dCol) ?: continue
            if (board[to].pieceSeat() != seat) out += ChessMove(from, to)
        }
    }

    private fun slideMoves(
        board: String,
        seat: Seat,
        from: Int,
        directions: List<Pair<Int, Int>>,
        out: MutableList<ChessMove>,
    ) {
        for ((dRow, dCol) in directions) {
            var to = chessShift(from, dRow, dCol)
            while (to != null) {
                val occupant = board[to]
                if (occupant == CHESS_EMPTY) {
                    out += ChessMove(from, to)
                } else {
                    if (occupant.pieceSeat() != seat) out += ChessMove(from, to)
                    break
                }
                to = chessShift(to, dRow, dCol)
            }
        }
    }

    /**
     * Roque. As três condições que costumam ser esquecidas estão aqui: o caminho tem de
     * estar livre, o rei não pode estar em xeque, e não pode **passar** por casa atacada —
     * chegar numa casa segura não basta.
     */
    private fun castlingMoves(state: ChessState, kingSquare: Int, out: MutableList<ChessMove>) {
        val seat = state.turn
        val home = if (seat == Seat.FIRST) squareOf("e1")!! else squareOf("e8")!!
        if (kingSquare != home) return

        val enemy = seat.opponent()
        if (isAttacked(state.board, kingSquare, enemy)) return

        val (kingSide, queenSide) = if (seat == Seat.FIRST) 'K' to 'Q' else 'k' to 'q'

        if (state.castling.contains(kingSide)) {
            val f = kingSquare + 1
            val g = kingSquare + 2
            if (state.board[f] == CHESS_EMPTY && state.board[g] == CHESS_EMPTY &&
                !isAttacked(state.board, f, enemy) && !isAttacked(state.board, g, enemy)
            ) {
                out += ChessMove(kingSquare, g)
            }
        }

        if (state.castling.contains(queenSide)) {
            val d = kingSquare - 1
            val c = kingSquare - 2
            val b = kingSquare - 3
            // A casa `b` precisa estar vazia, mas pode estar atacada: o rei não passa por ela.
            if (state.board[d] == CHESS_EMPTY && state.board[c] == CHESS_EMPTY &&
                state.board[b] == CHESS_EMPTY &&
                !isAttacked(state.board, d, enemy) && !isAttacked(state.board, c, enemy)
            ) {
                out += ChessMove(kingSquare, c)
            }
        }
    }

    fun kingSquare(board: String, seat: Seat): Int {
        val king = seat.pieceOf('K')
        val index = board.indexOf(king)
        return if (index >= 0) index else NO_SQUARE
    }

    fun inCheck(state: ChessState, seat: Seat): Boolean {
        val king = kingSquare(state.board, seat)
        return king != NO_SQUARE && isAttacked(state.board, king, seat.opponent())
    }

    /** A casa [square] está sob ataque de alguma peça de [by]? */
    fun isAttacked(board: String, square: Int, by: Seat): Boolean {
        // Peões: atacam na diagonal, no sentido em que andam. Procura-se para trás.
        val back = -by.pawnForward()
        val pawn = by.pieceOf('P')
        for (dCol in intArrayOf(-1, 1)) {
            val origin = chessShift(square, back, dCol)
            if (origin != null && board[origin] == pawn) return true
        }

        val knight = by.pieceOf('N')
        for ((dRow, dCol) in KNIGHT_JUMPS) {
            val origin = chessShift(square, dRow, dCol)
            if (origin != null && board[origin] == knight) return true
        }

        val king = by.pieceOf('K')
        for ((dRow, dCol) in ALL_AROUND) {
            val origin = chessShift(square, dRow, dCol)
            if (origin != null && board[origin] == king) return true
        }

        val queen = by.pieceOf('Q')
        val bishop = by.pieceOf('B')
        for ((dRow, dCol) in DIAGONALS) {
            if (raySees(board, square, dRow, dCol, bishop, queen)) return true
        }
        val rook = by.pieceOf('R')
        for ((dRow, dCol) in ORTHOGONALS) {
            if (raySees(board, square, dRow, dCol, rook, queen)) return true
        }

        return false
    }

    private fun raySees(
        board: String,
        from: Int,
        dRow: Int,
        dCol: Int,
        slider: Char,
        queen: Char,
    ): Boolean {
        var scan = chessShift(from, dRow, dCol)
        while (scan != null) {
            val piece = board[scan]
            if (piece != CHESS_EMPTY) return piece == slider || piece == queen
            scan = chessShift(scan, dRow, dCol)
        }
        return false
    }

    /**
     * O tabuleiro depois do lance, já com os efeitos que não são o simples "peça sai daqui e
     * chega ali": a torre acompanha o rei no roque, o peão capturado en passant sai de uma
     * casa por onde ninguém passou, e o peão promovido troca de peça.
     */
    fun boardAfter(state: ChessState, move: ChessMove): String {
        val cells = state.board.toCharArray()
        val piece = cells[move.from]
        val seat = piece.pieceSeat() ?: return state.board

        cells[move.from] = CHESS_EMPTY

        if (piece.isPawn() && move.to == state.enPassant && state.board[move.to] == CHESS_EMPTY) {
            // O peão capturado está ao lado da casa de destino, não nela.
            val captured = chessSquare(chessRow(move.from), chessCol(move.to))
            cells[captured] = CHESS_EMPTY
        }

        if (piece.isKing() && kotlin.math.abs(chessCol(move.to) - chessCol(move.from)) == 2) {
            val row = chessRow(move.from)
            val kingSide = chessCol(move.to) > chessCol(move.from)
            val rookFrom = chessSquare(row, if (kingSide) 7 else 0)
            val rookTo = chessSquare(row, if (kingSide) 5 else 3)
            cells[rookTo] = cells[rookFrom]
            cells[rookFrom] = CHESS_EMPTY
        }

        cells[move.to] = move.promotion?.let { seat.pieceOf(it) } ?: piece
        return String(cells)
    }
}
