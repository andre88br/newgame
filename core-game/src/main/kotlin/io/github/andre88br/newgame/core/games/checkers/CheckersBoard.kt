package io.github.andre88br.newgame.core.games.checkers

import io.github.andre88br.newgame.core.engine.Seat

/**
 * Representação do tabuleiro de damas.
 *
 * O tabuleiro é uma `String` de 64 caracteres, linha 0 no topo. Parece incomum para um
 * motor de jogo, mas resolve três coisas de uma vez: comparação e hash por valor (de
 * graça, e é o que a detecção de repetição vai precisar), JSON legível quando se abre uma
 * partida salva, e cópia barata — a busca da IA cria muitos tabuleiros por segundo.
 */
const val BOARD_SIZE: Int = 8
const val BOARD_CELLS: Int = BOARD_SIZE * BOARD_SIZE

const val EMPTY: Char = '.'
const val WHITE_MAN: Char = 'w'
const val WHITE_KING: Char = 'W'
const val BLACK_MAN: Char = 'b'
const val BLACK_KING: Char = 'B'

/** Cadeira dona da peça, ou `null` para casa vazia. */
fun Char.pieceOwner(): Seat? = when (this) {
    WHITE_MAN, WHITE_KING -> Seat.FIRST
    BLACK_MAN, BLACK_KING -> Seat.SECOND
    else -> null
}

fun Char.isKing(): Boolean = this == WHITE_KING || this == BLACK_KING

fun Char.isMan(): Boolean = this == WHITE_MAN || this == BLACK_MAN

/** A dama correspondente à pedra da mesma cor. */
fun Seat.kingChar(): Char = if (this == Seat.FIRST) WHITE_KING else BLACK_KING

fun Seat.manChar(): Char = if (this == Seat.FIRST) WHITE_MAN else BLACK_MAN

/**
 * Linha de promoção da cadeira. As brancas (cadeira 0) começam embaixo e sobem até a
 * linha 0; as pretas fazem o caminho inverso.
 */
fun Seat.promotionRow(): Int = if (this == Seat.FIRST) 0 else BOARD_SIZE - 1

/** Sentido de avanço das pedras: -1 para as brancas (sobem), +1 para as pretas. */
fun Seat.forward(): Int = if (this == Seat.FIRST) -1 else 1

fun rowOf(index: Int): Int = index / BOARD_SIZE

fun colOf(index: Int): Int = index % BOARD_SIZE

fun squareAt(row: Int, col: Int): Int = row * BOARD_SIZE + col

/** Casas jogáveis são as escuras — aquelas em que linha e coluna têm paridades diferentes. */
fun isPlayable(index: Int): Boolean = (rowOf(index) + colOf(index)) % 2 == 1

/**
 * Índice da casa deslocada em [dRow]/[dCol], ou `null` se sair do tabuleiro.
 * A checagem por linha e coluna evita o clássico bug de "dar a volta" na borda que
 * aparece quando se soma direto no índice.
 */
fun shift(index: Int, dRow: Int, dCol: Int): Int? {
    val row = rowOf(index) + dRow
    val col = colOf(index) + dCol
    return if (row in 0 until BOARD_SIZE && col in 0 until BOARD_SIZE) squareAt(row, col) else null
}

/** As quatro diagonais, como pares (linha, coluna). */
val DIAGONALS: List<Pair<Int, Int>> = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)

/**
 * Numeração PDN da casa (1 a 32, só casas escuras, do canto superior esquerdo).
 * É a notação usada nos registros de partida de damas, e o que aparece no histórico.
 */
fun pdnNumber(index: Int): Int = rowOf(index) * 4 + colOf(index) / 2 + 1

/** Tabuleiro inicial: 12 pedras de cada lado nas três fileiras mais próximas. */
fun initialBoard(): String {
    val cells = CharArray(BOARD_CELLS) { EMPTY }
    for (index in 0 until BOARD_CELLS) {
        if (!isPlayable(index)) continue
        when (rowOf(index)) {
            0, 1, 2 -> cells[index] = BLACK_MAN
            5, 6, 7 -> cells[index] = WHITE_MAN
        }
    }
    return String(cells)
}

/** Desenho do tabuleiro em texto, usado em mensagens de teste e depuração. */
fun renderBoard(board: String): String =
    (0 until BOARD_SIZE).joinToString("\n") { row ->
        board.substring(row * BOARD_SIZE, (row + 1) * BOARD_SIZE).toCharArray().joinToString(" ")
    }
