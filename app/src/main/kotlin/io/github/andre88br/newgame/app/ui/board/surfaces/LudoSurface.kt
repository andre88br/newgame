package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.speechText
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.a11y.BoardSpeech
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.ludo.LUDO_GRID
import io.github.andre88br.newgame.core.games.ludo.LUDO_TOKENS
import io.github.andre88br.newgame.core.games.ludo.LUDO_YARD
import io.github.andre88br.newgame.core.games.ludo.LudoCell
import io.github.andre88br.newgame.core.games.ludo.LudoCellKind
import io.github.andre88br.newgame.core.games.ludo.LudoGame
import io.github.andre88br.newgame.core.games.ludo.LudoLayout
import io.github.andre88br.newgame.core.games.ludo.LudoMove
import io.github.andre88br.newgame.core.games.ludo.LudoState
import kotlin.math.hypot

/** Um peão já posicionado em pixels, do jeito que o toque precisa encontrá-lo. */
private data class TokenSpot(
    val seat: Seat,
    val token: Int,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
)

/**
 * O tabuleiro do ludo.
 *
 * Não é uma grade de casas jogáveis — é uma cruz com uma volta, dois corredores e dois
 * currais —, então não passa pelo `BoardInteractor`. A geometria toda vem de `LudoLayout`,
 * no `core-game`, onde os testes conferem que a volta fecha, que os corredores encostam nela
 * e que nenhum peão cai fora do desenho. Aqui só se converte casa em pixel.
 *
 * O lance é escolher qual peão anda: o dado já foi rolado pelo motor e aparece ao lado.
 */
@Composable
fun LudoSurface(
    state: LudoState,
    viewer: Seat,
    enabled: Boolean,
    hinted: Move?,
    modifier: Modifier = Modifier,
    onMove: (Move) -> Unit,
) {
    val palette = LocalBoardPalette.current
    val hintToken = (hinted as? LudoMove)?.token

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Die(value = state.die, palette = palette)
            Column {
                Text(
                    text = stringResource(R.string.ludo_die, state.die),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.ludo_finished,
                        state.finished(viewer),
                        LUDO_TOKENS,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Board(
            state = state,
            palette = palette,
            enabled = enabled,
            hintToken = hintToken,
            viewer = viewer,
            onMove = onMove,
            modifier = Modifier.fillMaxWidth(),
        )

        // Os peões de quem está na vez, em botões de verdade.
        //
        // A cruz é um desenho, e mirar num peão de meio centímetro não é razoável nem para
        // quem enxerga bem. Esta fileira é o mesmo lance por outro caminho: cada botão diz
        // onde o peão está e o que acontece se ele andar.
        TokenButtons(
            state = state,
            enabled = enabled,
            hintToken = hintToken,
            onMove = onMove,
        )
    }
}

@Composable
private fun TokenButtons(
    state: LudoState,
    enabled: Boolean,
    hintToken: Int?,
    onMove: (Move) -> Unit,
) {
    val movable = remember(state) { LudoGame.legalMoves(state).map { it.token }.toSet() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.tokensOf(state.turn).forEachIndexed { token, progress ->
            val description = speechText(BoardSpeech.token(token, progress))
            OutlinedButton(
                onClick = { onMove(LudoMove(token)) },
                enabled = enabled && token in movable,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = description },
            ) {
                Text(
                    text = "${token + 1}" + if (token == hintToken) " ★" else "",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun Board(
    state: LudoState,
    palette: BoardPalette,
    enabled: Boolean,
    hintToken: Int?,
    viewer: Seat,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A grade é fixa e cara de recalcular a cada quadro; o que muda é onde estão os peões.
    val cells = remember {
        buildList {
            for (row in 0 until LUDO_GRID) {
                for (column in 0 until LUDO_GRID) add(LudoCell(row, column))
            }
        }
    }

    // Preenchido pelo desenho e lido pelo toque: os dois enxergam exatamente os mesmos peões
    // nos mesmos pontos, que é o que impede tocar num peão e mover outro.
    val spots = remember { mutableListOf<TokenSpot>() }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .clearAndSetSemantics { }
            .pointerInput(enabled, state) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    // Só os peões de quem está na vez respondem — os do outro lado estão
                    // no tabuleiro para serem vistos, não tocados.
                    val alvo = spots
                        .filter { it.seat == state.turn }
                        .minByOrNull { hypot(offset.x - it.centerX, offset.y - it.centerY) }
                        ?: return@detectTapGestures
                    val distancia = hypot(offset.x - alvo.centerX, offset.y - alvo.centerY)
                    // Um raio e meio de tolerância: peão é alvo pequeno num celular.
                    if (distancia <= alvo.radius * 1.5f) onMove(LudoMove(alvo.token))
                }
            },
    ) {
        val cellSize = size.minDimension / LUDO_GRID
        val originX = (size.width - cellSize * LUDO_GRID) / 2f
        val originY = (size.height - cellSize * LUDO_GRID) / 2f

        fun topLeft(cell: LudoCell) = Offset(
            originX + cell.column * cellSize,
            originY + cell.row * cellSize,
        )

        for (cell in cells) {
            val kind = LudoLayout.kindOf(cell)
            if (kind == LudoCellKind.OUTSIDE) continue
            drawRect(
                color = colorOf(kind, palette),
                topLeft = topLeft(cell),
                size = Size(cellSize, cellSize),
            )
            drawRect(
                color = palette.border,
                topLeft = topLeft(cell),
                size = Size(cellSize, cellSize),
                style = Stroke(width = cellSize * 0.04f),
            )
        }

        spots.clear()
        val radius = cellSize * 0.34f
        for (seat in listOf(Seat.FIRST, Seat.SECOND)) {
            state.tokensOf(seat).forEachIndexed { token, progress ->
                val cell = LudoLayout.cellFor(seat, progress, token)
                val corner = topLeft(cell)

                // Dois peões podem dividir uma casa: um leve deslocamento por assento faz os
                // dois aparecerem, em vez de um esconder o outro.
                val shift = if (seat == Seat.FIRST) -cellSize * 0.08f else cellSize * 0.08f
                val centerX = corner.x + cellSize / 2f + shift
                val centerY = corner.y + cellSize / 2f + shift

                drawCircle(
                    color = if (seat == Seat.FIRST) palette.firstPiece else palette.secondPiece,
                    radius = radius,
                    center = Offset(centerX, centerY),
                )
                drawCircle(
                    color = if (seat == Seat.FIRST) palette.firstPieceEdge else palette.secondPieceEdge,
                    radius = radius,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = cellSize * 0.07f),
                )
                if (seat == viewer && progress != LUDO_YARD) {
                    // Marca discreta de "este é seu", já que as duas cores ficam pequenas.
                    drawCircle(
                        color = palette.crown,
                        radius = radius * 0.28f,
                        center = Offset(centerX, centerY),
                    )
                }
                if (seat == state.turn && token == hintToken) {
                    drawCircle(
                        color = palette.hint,
                        radius = radius * 1.25f,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = cellSize * 0.09f),
                    )
                }

                spots += TokenSpot(seat, token, centerX, centerY, radius)
            }
        }
    }
}

private fun colorOf(kind: LudoCellKind, palette: BoardPalette): Color = when (kind) {
    LudoCellKind.TRACK -> palette.lightSquare
    LudoCellKind.SAFE -> palette.lastMove.copy(alpha = 1f)
    LudoCellKind.HOME_FIRST -> palette.firstPiece
    LudoCellKind.HOME_SECOND -> palette.secondPiece
    LudoCellKind.GOAL -> palette.crown
    LudoCellKind.YARD_FIRST -> palette.firstPiece.copy(alpha = 0.55f)
    LudoCellKind.YARD_SECOND -> palette.secondPiece.copy(alpha = 0.55f)
    LudoCellKind.LANE_UNUSED -> palette.darkSquare
    LudoCellKind.OUTSIDE -> Color.Transparent
}

/** O dado que o motor rolou, desenhado como dado mesmo. */
@Composable
private fun Die(value: Int, palette: BoardPalette) {
    Canvas(modifier = Modifier.size(48.dp)) {
        val edge = size.minDimension * 0.06f
        drawRect(color = palette.firstPiece, size = size)
        drawRect(color = palette.firstPieceEdge, size = size, style = Stroke(width = edge))

        val radius = size.minDimension * 0.09f
        fun spot(column: Int, row: Int) = Offset(
            size.width * (column + 1) / 4f,
            size.height * (row + 1) / 4f,
        )

        val spots = when (value) {
            1 -> listOf(spot(1, 1))
            2 -> listOf(spot(0, 0), spot(2, 2))
            3 -> listOf(spot(0, 0), spot(1, 1), spot(2, 2))
            4 -> listOf(spot(0, 0), spot(2, 0), spot(0, 2), spot(2, 2))
            5 -> listOf(spot(0, 0), spot(2, 0), spot(1, 1), spot(0, 2), spot(2, 2))
            else -> listOf(spot(0, 0), spot(2, 0), spot(0, 1), spot(2, 1), spot(0, 2), spot(2, 2))
        }
        spots.forEach { drawCircle(color = palette.secondPiece, radius = radius, center = it) }
    }
}
