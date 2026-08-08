package io.github.andre88br.newgame.core.games.checkers

import io.github.andre88br.newgame.core.engine.Seat

/**
 * Monta um tabuleiro a partir de oito linhas desenhadas, para as posições de teste ficarem
 * legíveis. Espaços são ignorados, então dá para escrever `". . w . . . . ."`.
 */
fun boardOf(vararg rows: String): String {
    require(rows.size == BOARD_SIZE) { "São ${BOARD_SIZE} linhas, vieram ${rows.size}" }
    val cells = rows.joinToString("") { row -> row.filterNot { it.isWhitespace() } }
    require(cells.length == BOARD_CELLS) {
        "São $BOARD_CELLS casas, vieram ${cells.length}:\n${rows.joinToString("\n")}"
    }
    return cells
}

/** Estado de teste com o tabuleiro desenhado e a vez de [turn]. */
fun positionOf(turn: Seat = Seat.FIRST, vararg rows: String): CheckersState =
    CheckersState(board = boardOf(*rows), turn = turn)

/** Conta as folhas da árvore de lances até [depth] — o `perft` das damas. */
internal fun perft(
    state: CheckersState,
    depth: Int,
    variant: CheckersMoves.Variant = CheckersMoves.Variant.BRAZILIAN,
): Long {
    if (depth == 0) return 1
    val moves = CheckersMoves.legal(state.board, state.turn, variant)
    if (moves.isEmpty()) return 0
    if (depth == 1) return moves.size.toLong()
    return moves.sumOf { perft(CheckersGame.applyKnownLegal(state, it), depth - 1, variant) }
}
