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
import io.github.andre88br.newgame.core.games.checkers.CheckersState
import io.github.andre88br.newgame.core.games.checkers.EMPTY
import io.github.andre88br.newgame.core.games.checkers.isKing
import io.github.andre88br.newgame.core.games.checkers.isPlayable
import io.github.andre88br.newgame.core.games.checkers.pieceOwner

/** Pedras e damas sobre o tabuleiro oito por oito. */
object CheckersPainter : BoardPainter {

    override fun drawSquare(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette) {
        scope.drawRect(
            color = if (isPlayable(cell.square)) palette.darkSquare else palette.lightSquare,
            topLeft = Offset(cell.left, cell.top),
            size = Size(cell.size, cell.size),
        )
    }

    override fun drawPiece(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette) {
        val piece = (state as CheckersState).pieceAt(cell.square)
        if (piece == EMPTY) return

        val owner = piece.pieceOwner() ?: return
        val fill = if (owner == Seat.FIRST) palette.firstPiece else palette.secondPiece
        val edge = if (owner == Seat.FIRST) palette.firstPieceEdge else palette.secondPieceEdge
        val center = Offset(cell.centerX, cell.centerY)
        val radius = cell.size * 0.38f

        scope.drawCircle(color = fill, radius = radius, center = center)
        scope.drawCircle(
            color = edge,
            radius = radius,
            center = center,
            style = Stroke(width = cell.size * 0.05f),
        )

        if (piece.isKing()) {
            // Dama: um anel dourado por dentro. Mais legível num tabuleiro pequeno do que
            // um desenho de coroa, que num celular vira um borrão.
            scope.drawCircle(
                color = palette.crown,
                radius = radius * 0.52f,
                center = center,
                style = Stroke(width = cell.size * 0.07f),
            )
        }
    }
}
