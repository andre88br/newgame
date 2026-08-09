package io.github.andre88br.newgame.app.ui.board

import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.core.engine.GameState

/** Uma casa do tabuleiro já posicionada em pixels, pronta para desenhar. */
data class Cell(
    val square: Int,
    val row: Int,
    val column: Int,
    val left: Float,
    val top: Float,
    val size: Float,
) {
    val centerX: Float get() = left + size / 2f
    val centerY: Float get() = top + size / 2f
}

/**
 * Desenha as casas e as peças de um jogo.
 *
 * O [GridBoard] cuida da geometria, dos toques e dos destaques, que são iguais para todo
 * mundo; o que muda de jogo para jogo é só o desenho — e é só isso que fica aqui. Reversi
 * e Xadrez, na Fase 3, entram implementando esta interface e mais nada.
 *
 * Recebe o `DrawScope` como parâmetro em vez de usar receptor de contexto: fica mais
 * verboso, mas é Kotlin comum, sem recurso experimental.
 */
interface BoardPainter {

    /** Fundo da casa. Chamado para todas as casas, antes de qualquer peça. */
    fun drawSquare(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette)

    /** A peça que estiver na casa. Chamado depois dos fundos e dos destaques. */
    fun drawPiece(scope: DrawScope, cell: Cell, state: GameState, palette: BoardPalette)

    /**
     * Desenho final por cima de tudo — a risca sobre a linha vencedora, por exemplo.
     * Recebe o tabuleiro inteiro, e não uma casa.
     */
    fun drawOverlay(
        scope: DrawScope,
        cells: List<Cell>,
        state: GameState,
        palette: BoardPalette,
    ) = Unit
}
