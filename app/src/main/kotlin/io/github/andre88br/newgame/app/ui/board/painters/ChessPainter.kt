package io.github.andre88br.newgame.app.ui.board.painters

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import io.github.andre88br.newgame.app.ui.board.BoardPainter
import io.github.andre88br.newgame.app.ui.board.Cell
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.chess.CHESS_EMPTY
import io.github.andre88br.newgame.core.games.chess.ChessState
import io.github.andre88br.newgame.core.games.chess.chessCol
import io.github.andre88br.newgame.core.games.chess.chessRow
import io.github.andre88br.newgame.core.games.chess.pieceKind
import io.github.andre88br.newgame.core.games.chess.pieceSeat

/**
 * Peças de xadrez desenhadas com os símbolos Unicode.
 *
 * Usa sempre os glifos **cheios** (♚♛♜♝♞♟), pintando-os de claro ou escuro conforme a cor,
 * em vez dos glifos vazados para as brancas. Num tabuleiro de celular o contorno fino dos
 * vazados some, e as duas cores viram a mesma mancha.
 *
 * O desenho de texto passa pelo `Canvas` nativo porque é o caminho que funciona igual em
 * qualquer versão do Compose — a API de texto do próprio Compose para `DrawScope` mudou de
 * assinatura mais de uma vez.
 */
object ChessPainter : BoardPainter {

    private val GLYPHS = mapOf(
        'K' to "♚",
        'Q' to "♛",
        'R' to "♜",
        'B' to "♝",
        'N' to "♞",
        'P' to "♟",
    )

    // Um Paint só, reaproveitado: criar um por casa a cada quadro geraria lixo à toa.
    private val paint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    override fun drawSquare(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette) {
        // Pela casa, não pela posição na tela: com o tabuleiro girado as duas divergem.
        val dark = (chessRow(cell.square) + chessCol(cell.square)) % 2 == 1
        scope.drawRect(
            color = if (dark) palette.darkSquare else palette.lightSquare,
            topLeft = Offset(cell.left, cell.top),
            size = Size(cell.size, cell.size),
        )
    }

    override fun drawPiece(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette) {
        val piece = (state as ChessState).pieceAt(cell.square)
        if (piece == CHESS_EMPTY) return

        val owner = piece.pieceSeat() ?: return
        val glyph = GLYPHS[piece.pieceKind()] ?: return

        val fill = if (owner == Seat.FIRST) palette.firstPiece else palette.secondPiece
        val edge = if (owner == Seat.FIRST) palette.firstPieceEdge else palette.secondPieceEdge

        scope.drawIntoCanvas { canvas ->
            paint.textSize = cell.size * 0.78f
            // O glifo é centralizado na horizontal pelo Paint; na vertical, a linha de base
            // precisa ser calculada a partir das métricas da fonte.
            val baseline = cell.centerY - (paint.descent() + paint.ascent()) / 2f

            paint.style = Paint.Style.FILL
            paint.color = fill.toArgb()
            canvas.nativeCanvas.drawText(glyph, cell.centerX, baseline, paint)

            // Contorno: sem ele a peça clara desaparece na casa clara.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = cell.size * 0.035f
            paint.color = edge.toArgb()
            canvas.nativeCanvas.drawText(glyph, cell.centerX, baseline, paint)
        }
    }

    /** Marca o rei em xeque: é a informação mais urgente do tabuleiro. */
    override fun drawOverlay(
        scope: DrawScope,
        cells: List<Cell>,
        state: GameState,
        palette: BoardPalette,
    ) {
        val board = state as ChessState
        val seat = board.turn
        if (!board.inCheck(seat)) return

        val king = seat.let { if (it == Seat.FIRST) 'K' else 'k' }
        val square = board.board.indexOf(king)
        if (square < 0) return

        val cell = cells.firstOrNull { it.square == square } ?: return

        val inset = cell.size * 0.06f
        scope.drawRect(
            color = palette.check,
            topLeft = Offset(cell.left + inset, cell.top + inset),
            size = Size(cell.size - 2 * inset, cell.size - 2 * inset),
            style = Stroke(width = cell.size * 0.09f),
        )
    }
}
