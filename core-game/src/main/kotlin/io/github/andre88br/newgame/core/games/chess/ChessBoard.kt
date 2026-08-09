package io.github.andre88br.newgame.core.games.chess

import io.github.andre88br.newgame.core.engine.Seat

/**
 * Geometria e notação do xadrez.
 *
 * O tabuleiro é uma `String` de 64 caracteres com o índice 0 em **a8**, andando da esquerda
 * para a direita e de cima para baixo — a mesma ordem em que um FEN é escrito, o que faz a
 * conversão nos dois sentidos ser quase direta. Peças brancas em maiúsculas, pretas em
 * minúsculas, como manda a notação.
 */
const val CHESS_SIZE: Int = 8
const val CHESS_CELLS: Int = CHESS_SIZE * CHESS_SIZE
const val CHESS_EMPTY: Char = '.'

/** Casa inexistente — usada onde `-1` seria só um número mágico. */
const val NO_SQUARE: Int = -1

fun Char.pieceSeat(): Seat? = when {
    this == CHESS_EMPTY -> null
    isUpperCase() -> Seat.FIRST
    else -> Seat.SECOND
}

/** A peça sem a cor: sempre em maiúscula. */
fun Char.pieceKind(): Char = uppercaseChar()

fun Char.isPawn(): Boolean = this == 'P' || this == 'p'
fun Char.isKing(): Boolean = this == 'K' || this == 'k'

fun Seat.pieceOf(kind: Char): Char =
    if (this == Seat.FIRST) kind.uppercaseChar() else kind.lowercaseChar()

fun chessRow(square: Int): Int = square / CHESS_SIZE

fun chessCol(square: Int): Int = square % CHESS_SIZE

fun chessSquare(row: Int, col: Int): Int = row * CHESS_SIZE + col

/** Deslocamento com verificação de borda por linha e coluna, para não dar a volta no tabuleiro. */
fun chessShift(square: Int, dRow: Int, dCol: Int): Int? {
    val row = chessRow(square) + dRow
    val col = chessCol(square) + dCol
    return if (row in 0 until CHESS_SIZE && col in 0 until CHESS_SIZE) chessSquare(row, col) else null
}

/** Sentido de avanço dos peões: as brancas sobem no tabuleiro, isto é, a linha diminui. */
fun Seat.pawnForward(): Int = if (this == Seat.FIRST) -1 else 1

/** Linha em que o peão promove. */
fun Seat.promotionRank(): Int = if (this == Seat.FIRST) 0 else CHESS_SIZE - 1

/** Linha de onde o peão pode avançar duas casas. */
fun Seat.pawnStartRank(): Int = if (this == Seat.FIRST) 6 else 1

/** Notação algébrica da casa: `e4`, `a8`. */
fun squareName(square: Int): String =
    "${'a' + chessCol(square)}${CHESS_SIZE - chessRow(square)}"

/** Converte `e4` no índice correspondente, ou `null` se o texto não for uma casa. */
fun squareOf(name: String): Int? {
    if (name.length != 2) return null
    val col = name[0] - 'a'
    val row = CHESS_SIZE - (name[1] - '0')
    if (col !in 0 until CHESS_SIZE || row !in 0 until CHESS_SIZE) return null
    return chessSquare(row, col)
}

/** Posição inicial do xadrez. */
const val CHESS_START_FEN: String = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

/**
 * Lê um FEN.
 *
 * Serve para três coisas: montar a posição inicial, escrever testes de posições específicas
 * sem desenhar tabuleiro à mão, e — principalmente — rodar o `perft` contra as posições
 * publicadas, que são distribuídas nesse formato.
 */
fun chessStateFromFen(fen: String): ChessState {
    val parts = fen.trim().split(" ")
    require(parts.size >= 4) { "FEN incompleto: $fen" }

    val cells = CharArray(CHESS_CELLS) { CHESS_EMPTY }
    var index = 0
    for (char in parts[0]) {
        when {
            char == '/' -> Unit
            char.isDigit() -> index += char - '0'
            else -> {
                require(index < CHESS_CELLS) { "FEN com casas demais: $fen" }
                cells[index++] = char
            }
        }
    }
    require(index == CHESS_CELLS) { "FEN com $index casas, esperado $CHESS_CELLS: $fen" }

    val turn = if (parts[1] == "w") Seat.FIRST else Seat.SECOND
    val castling = parts[2].takeIf { it != "-" }.orEmpty()
    val enPassant = parts[3].takeIf { it != "-" }?.let(::squareOf) ?: NO_SQUARE
    val halfmove = parts.getOrNull(4)?.toIntOrNull() ?: 0
    val fullmove = parts.getOrNull(5)?.toIntOrNull() ?: 1

    // O contador de lances do FEN começa em 1 e conta lances completos; o `ply` do motor
    // conta meios-lances desde o início.
    val ply = (fullmove - 1) * 2 + if (turn == Seat.SECOND) 1 else 0

    return ChessState(
        board = String(cells),
        turn = turn,
        ply = ply,
        castling = castling,
        enPassant = enPassant,
        halfmoveClock = halfmove,
    )
}

/** Escreve o FEN da posição — útil em mensagens de teste e para exportar uma partida. */
fun ChessState.toFen(): String {
    val rows = (0 until CHESS_SIZE).joinToString("/") { row ->
        val line = StringBuilder()
        var empties = 0
        for (col in 0 until CHESS_SIZE) {
            val piece = board[chessSquare(row, col)]
            if (piece == CHESS_EMPTY) {
                empties++
            } else {
                if (empties > 0) {
                    line.append(empties)
                    empties = 0
                }
                line.append(piece)
            }
        }
        if (empties > 0) line.append(empties)
        line.toString()
    }

    val side = if (turn == Seat.FIRST) "w" else "b"
    val rights = castling.ifEmpty { "-" }
    val ep = if (enPassant == NO_SQUARE) "-" else squareName(enPassant)
    val fullmove = ply / 2 + 1
    return "$rows $side $rights $ep $halfmoveClock $fullmove"
}

/** Desenho em texto do tabuleiro, para mensagens de falha legíveis. */
fun renderChessBoard(board: String): String =
    (0 until CHESS_SIZE).joinToString("\n") { row ->
        "${CHESS_SIZE - row} " +
            board.substring(row * CHESS_SIZE, (row + 1) * CHESS_SIZE).toCharArray()
                .joinToString(" ")
    } + "\n  a b c d e f g h"
