package io.github.andre88br.newgame.app.ui.board.painters

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.andre88br.newgame.app.ui.board.BoardPainter
import io.github.andre88br.newgame.app.ui.board.Cell
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.games.tictactoe.EMPTY_CELL
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeState

/** X e O sobre uma grade de três por três. */
object TicTacToePainter : BoardPainter {

    override fun drawSquare(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette) {
        scope.drawRect(
            color = palette.lightSquare,
            topLeft = Offset(cell.left, cell.top),
            size = Size(cell.size, cell.size),
        )
        scope.drawRect(
            color = palette.border,
            topLeft = Offset(cell.left, cell.top),
            size = Size(cell.size, cell.size),
            style = Stroke(width = cell.size * 0.02f),
        )
    }

    override fun drawPiece(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette) {
        val board = state as TicTacToeState
        val owner = board.cells[cell.square]
        if (owner == EMPTY_CELL) return

        val inset = cell.size * 0.24f
        val thickness = cell.size * 0.11f

        if (owner == 0) {
            // X
            scope.drawLine(
                color = palette.markFirst,
                start = Offset(cell.left + inset, cell.top + inset),
                end = Offset(cell.left + cell.size - inset, cell.top + cell.size - inset),
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
            scope.drawLine(
                color = palette.markFirst,
                start = Offset(cell.left + cell.size - inset, cell.top + inset),
                end = Offset(cell.left + inset, cell.top + cell.size - inset),
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
        } else {
            // O
            scope.drawCircle(
                color = palette.markSecond,
                radius = (cell.size - 2 * inset) / 2f,
                center = Offset(cell.centerX, cell.centerY),
                style = Stroke(width = thickness),
            )
        }
    }

    /** Risca a linha vencedora, que é o retorno mais direto de que a partida acabou. */
    override fun drawOverlay(
        scope: DrawScope,
        cells: List<Cell>,
        state: GameState,
        palette: BoardPalette,
    ) {
        val line = (state as TicTacToeState).winningLine()
        if (line.isEmpty()) return

        val first = cells.firstOrNull { it.square == line.first() } ?: return
        val last = cells.firstOrNull { it.square == line.last() } ?: return
        scope.drawLine(
            color = palette.crown,
            start = Offset(first.centerX, first.centerY),
            end = Offset(last.centerX, last.centerY),
            strokeWidth = first.size * 0.09f,
            cap = StrokeCap.Round,
        )
    }
}
