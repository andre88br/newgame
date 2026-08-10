package io.github.andre88br.newgame.app.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.squareSpeechText
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.a11y.BoardSpeech
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.session.BoardInteractor

/**
 * O tabuleiro na tela: grade, toques e destaques.
 *
 * Não sabe que jogo está desenhando. O tamanho da grade vem do [BoardInteractor], o
 * desenho das peças vem do [BoardPainter], e a tradução de toque em lance também é do
 * interator — que mora no `core-game` e é testado lá. Aqui só tem geometria.
 *
 * O toque **não** sai do `Canvas`: sai de uma grade invisível de casas por cima dele. Um
 * `Canvas` é um retângulo só para o sistema, e um leitor de tela não tem como percorrer
 * casa a casa o que é um desenho — daria "tabuleiro" e nada mais. Com uma casa de verdade
 * em cada posição, o TalkBack anda pelo tabuleiro e lê "e4, peão branco". A geometria das
 * duas camadas é a mesma, então a casa tocada é sempre a casa vista.
 */
@Composable
fun GridBoard(
    entry: GameEntry,
    state: GameState,
    interactor: BoardInteractor,
    painter: BoardPainter,
    modifier: Modifier = Modifier,
    selected: Int? = null,
    highlighted: Set<Int> = emptySet(),
    lastMove: Set<Int> = emptySet(),
    /** Opacidade do destaque do último lance, animada por quem chama. */
    lastMoveAlpha: Float = 1f,
    /**
     * Gira o tabuleiro meia-volta, para quem joga com as peças de baixo ver o próprio lado
     * de frente. No xadrez isso não é conforto: com o tabuleiro de cabeça para baixo, a
     * pessoa erra o lado para onde os peões andam.
     */
    flipped: Boolean = false,
    enabled: Boolean = true,
    onSquareTap: (Int) -> Unit = {},
) {
    val palette = LocalBoardPalette.current

    // Desenho e toque passam pela MESMA conversão: é o que garante que a casa tocada seja a
    // casa vista, com ou sem giro.
    fun squareOfCell(row: Int, column: Int): Int = if (flipped) {
        interactor.squareAt(interactor.rows - 1 - row, interactor.columns - 1 - column)
    } else {
        interactor.squareAt(row, column)
    }

    Box(
        modifier = modifier.aspectRatio(interactor.columns.toFloat() / interactor.rows.toFloat()),
    ) {
        Canvas(
            // O desenho é decoração: quem descreve o tabuleiro é a grade de casas por cima.
            // Sem isto o leitor de tela anunciaria o Canvas inteiro mais cada casa.
            modifier = Modifier
                .matchParentSize()
                .clearAndSetSemantics { },
        ) {
            val cellSize = minOf(size.width / interactor.columns, size.height / interactor.rows)
            val boardWidth = cellSize * interactor.columns
            val boardHeight = cellSize * interactor.rows
            val originX = (size.width - boardWidth) / 2f
            val originY = (size.height - boardHeight) / 2f

            val cells = ArrayList<Cell>(interactor.rows * interactor.columns)
            for (row in 0 until interactor.rows) {
                for (column in 0 until interactor.columns) {
                    cells += Cell(
                        square = squareOfCell(row, column),
                        row = row,
                        column = column,
                        left = originX + column * cellSize,
                        top = originY + row * cellSize,
                        size = cellSize,
                    )
                }
            }

            cells.forEach { painter.drawSquare(this, it, state, palette) }

            // Destaques entre o fundo e a peça: assim a marcação emoldura a peça em vez de
            // cobri-la.
            val markWidth = cellSize * 0.07f
            cells.forEach { cell ->
                if (cell.square in lastMove && lastMoveAlpha > 0f) {
                    drawRect(
                        color = palette.lastMove.copy(
                            alpha = palette.lastMove.alpha * lastMoveAlpha,
                        ),
                        topLeft = Offset(cell.left, cell.top),
                        size = Size(cell.size, cell.size),
                    )
                }
                if (cell.square in highlighted) {
                    drawRect(
                        color = palette.hint,
                        topLeft = Offset(cell.left + markWidth / 2, cell.top + markWidth / 2),
                        size = Size(cell.size - markWidth, cell.size - markWidth),
                        style = Stroke(width = markWidth),
                    )
                }
                if (cell.square == selected) {
                    drawRect(
                        color = palette.selection,
                        topLeft = Offset(cell.left + markWidth / 2, cell.top + markWidth / 2),
                        size = Size(cell.size - markWidth, cell.size - markWidth),
                        style = Stroke(width = markWidth),
                    )
                }
            }

            cells.forEach { painter.drawPiece(this, it, state, palette) }
            painter.drawOverlay(this, cells, state, palette)
        }

        Column(modifier = Modifier.matchParentSize()) {
            for (row in 0 until interactor.rows) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    for (column in 0 until interactor.columns) {
                        SquareTarget(
                            entry = entry,
                            state = state,
                            square = squareOfCell(row, column),
                            selected = squareOfCell(row, column) == selected,
                            hinted = squareOfCell(row, column) in highlighted,
                            enabled = enabled,
                            onTap = onSquareTap,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Uma casa como alvo de toque e como coisa que o leitor de tela consegue ler.
 *
 * Transparente: o que aparece é o desenho do `Canvas` embaixo. `selectable` em vez de
 * `clickable` porque uma casa escolhida **fica** escolhida, e o TalkBack anuncia isso
 * sozinho a partir do estado.
 */
@Composable
private fun SquareTarget(
    entry: GameEntry,
    state: GameState,
    square: Int,
    selected: Boolean,
    hinted: Boolean,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speech = BoardSpeech.square(entry, state, square)
    val description = speech?.let { squareSpeechText(it) } ?: ""
    val hintLabel = stringResource(R.string.a11y_suggested)

    Box(
        modifier = modifier
            .selectable(
                selected = selected,
                enabled = enabled,
                onClick = { onTap(square) },
            )
            .semantics {
                contentDescription = description
                if (hinted) stateDescription = hintLabel
            },
    )
}
