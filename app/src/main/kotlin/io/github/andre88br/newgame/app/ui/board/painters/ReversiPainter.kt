package io.github.andre88br.newgame.app.ui.board.painters

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.andre88br.newgame.app.ui.board.BoardPainter
import io.github.andre88br.newgame.app.ui.board.Cell
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.reversi.REVERSI_EMPTY
import io.github.andre88br.newgame.core.games.reversi.ReversiState
import io.github.andre88br.newgame.core.games.reversi.discOwner

/** Peças redondas sobre o pano verde. Todas as casas são jogáveis, sem xadrezado. */
object ReversiPainter : BoardPainter {

    override fun drawSquare(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette) {
        scope.drawRect(
            color = palette.darkSquare,
            topLeft = Offset(cell.left, cell.top),
            size = Size(cell.size, cell.size),
        )
        scope.drawRect(
            color = palette.border,
            topLeft = Offset(cell.left, cell.top),
            size = Size(cell.size, cell.size),
            style = Stroke(width = cell.size * 0.03f),
        )
    }

    override fun drawPiece(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette) {
        val disc = (state as ReversiState).discAt(cell.square)
        if (disc == REVERSI_EMPTY) return

        val owner = disc.discOwner() ?: return
        val center = Offset(cell.centerX, cell.centerY)
        val radius = cell.size * 0.40f

        scope.drawCircle(
            color = if (owner == Seat.FIRST) palette.secondPiece else palette.firstPiece,
            radius = radius,
            center = center,
        )
        scope.drawCircle(
            color = if (owner == Seat.FIRST) palette.secondPieceEdge else palette.firstPieceEdge,
            radius = radius,
            center = center,
            style = Stroke(width = cell.size * 0.04f),
        )
    }
}
