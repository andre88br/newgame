package io.github.andre88br.newgame.core.games.checkers

import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent

/**
 * Geração de lances das damas brasileiras.
 *
 * Regras que o gerador implementa e que costumam ser onde outros motores erram:
 *
 * - **Captura é obrigatória** e, havendo escolha, é obrigatório capturar a maior
 *   quantidade de peças. Na regra brasileira só conta a quantidade — capturar uma dama
 *   não vale mais do que capturar uma pedra.
 * - **Pedra captura para trás**, embora só ande para a frente.
 * - **Dama voadora**: anda e captura à distância pela diagonal, podendo pousar em
 *   qualquer casa livre depois da peça capturada.
 * - **Regra do sopro turco**: as peças capturadas só saem do tabuleiro no fim da
 *   sequência. Enquanto isso continuam ocupando a casa, bloqueando pousos, e não podem
 *   ser saltadas duas vezes.
 * - **Promoção só no fim do lance**: pedra que passa pela última fileira no meio de uma
 *   sequência de capturas e segue capturando continua pedra.
 */
internal object CheckersMoves {

    /**
     * As duas regras acima que são específicas do jogo brasileiro. São parâmetros, e não
     * constantes, por um motivo só: desligando as duas o gerador passa a jogar damas
     * inglesas, cujos números de `perft` são públicos e conhecidos. `CheckersPerftTest`
     * usa isso para conferir o resto da máquina — diagonais, bordas, sequências, sopro
     * turco — contra uma referência externa, em vez de contra si mesma.
     */
    data class Variant(
        val maximumCapture: Boolean = true,
        val backwardCaptures: Boolean = true,
    ) {
        companion object {
            val BRAZILIAN = Variant()
            val ENGLISH = Variant(maximumCapture = false, backwardCaptures = false)
        }
    }

    /** Lances legais de [seat], já filtrados pela obrigação de capturar o máximo. */
    fun legal(board: String, seat: Seat, variant: Variant = Variant.BRAZILIAN): List<CheckersMove> {
        val captures = captures(board, seat, variant)
        if (captures.isNotEmpty()) {
            if (!variant.maximumCapture) return captures
            val most = captures.maxOf { it.captured.size }
            return captures.filter { it.captured.size == most }
        }
        return simple(board, seat)
    }

    /** Todas as sequências de captura completas (que não podem mais ser estendidas). */
    fun captures(
        board: String,
        seat: Seat,
        variant: Variant = Variant.BRAZILIAN,
    ): List<CheckersMove> {
        val cells = board.toCharArray()
        val found = ArrayList<CheckersMove>()
        for (from in 0 until BOARD_CELLS) {
            val piece = cells[from]
            if (piece.pieceOwner() != seat) continue

            // Levanta a peça do tabuleiro: durante a sequência a casa de origem está
            // livre e pode até ser reusada como pouso.
            cells[from] = EMPTY
            val path = ArrayList<Int>()
            val captured = ArrayList<Int>()
            if (piece.isKing()) {
                kingCaptures(cells, seat, from, from, path, captured, found)
            } else {
                manCaptures(cells, seat, variant, from, from, path, captured, found)
            }
            cells[from] = piece
        }
        return found
    }

    private fun manCaptures(
        cells: CharArray,
        seat: Seat,
        variant: Variant,
        from: Int,
        current: Int,
        path: MutableList<Int>,
        captured: MutableList<Int>,
        found: MutableList<CheckersMove>,
    ) {
        var extended = false
        for ((dRow, dCol) in DIAGONALS) {
            if (!variant.backwardCaptures && dRow != seat.forward()) continue
            val over = shift(current, dRow, dCol) ?: continue
            if (cells[over].pieceOwner() != seat.opponent()) continue
            if (over in captured) continue // sopro turco: não salta a mesma peça duas vezes
            val landing = shift(over, dRow, dCol) ?: continue
            if (cells[landing] != EMPTY) continue

            captured += over
            path += landing
            extended = true
            manCaptures(cells, seat, variant, from, landing, path, captured, found)
            path.removeAt(path.lastIndex)
            captured.removeAt(captured.lastIndex)
        }
        if (!extended && path.isNotEmpty()) {
            found += CheckersMove(from, path.toList(), captured.toList())
        }
    }

    private fun kingCaptures(
        cells: CharArray,
        seat: Seat,
        from: Int,
        current: Int,
        path: MutableList<Int>,
        captured: MutableList<Int>,
        found: MutableList<CheckersMove>,
    ) {
        var extended = false
        for ((dRow, dCol) in DIAGONALS) {
            // Anda pela diagonal até achar a primeira casa ocupada.
            var scan = shift(current, dRow, dCol)
            while (scan != null && cells[scan] == EMPTY) {
                scan = shift(scan, dRow, dCol)
            }
            val target = scan ?: continue
            if (cells[target].pieceOwner() != seat.opponent()) continue
            if (target in captured) continue

            // Pode pousar em qualquer casa livre logo depois da peça capturada.
            var landing = shift(target, dRow, dCol)
            while (landing != null && cells[landing] == EMPTY) {
                captured += target
                path += landing
                extended = true
                kingCaptures(cells, seat, from, landing, path, captured, found)
                path.removeAt(path.lastIndex)
                captured.removeAt(captured.lastIndex)
                landing = shift(landing, dRow, dCol)
            }
        }
        if (!extended && path.isNotEmpty()) {
            found += CheckersMove(from, path.toList(), captured.toList())
        }
    }

    /** Lances sem captura. Só valem quando não há captura disponível. */
    fun simple(board: String, seat: Seat): List<CheckersMove> {
        val found = ArrayList<CheckersMove>()
        for (from in 0 until BOARD_CELLS) {
            val piece = board[from]
            if (piece.pieceOwner() != seat) continue

            if (piece.isKing()) {
                for ((dRow, dCol) in DIAGONALS) {
                    var to = shift(from, dRow, dCol)
                    while (to != null && board[to] == EMPTY) {
                        found += CheckersMove(from, listOf(to))
                        to = shift(to, dRow, dCol)
                    }
                }
            } else {
                val dRow = seat.forward()
                for (dCol in intArrayOf(-1, 1)) {
                    val to = shift(from, dRow, dCol) ?: continue
                    if (board[to] == EMPTY) found += CheckersMove(from, listOf(to))
                }
            }
        }
        return found
    }
}
