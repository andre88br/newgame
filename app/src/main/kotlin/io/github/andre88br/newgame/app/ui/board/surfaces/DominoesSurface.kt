package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import io.github.andre88br.newgame.core.engine.opponent
import io.github.andre88br.newgame.core.games.dominoes.DominoesMove
import io.github.andre88br.newgame.core.games.dominoes.DominoesState
import io.github.andre88br.newgame.core.games.dominoes.HandTile
import io.github.andre88br.newgame.core.games.dominoes.LineEnd
import io.github.andre88br.newgame.core.games.dominoes.PlacedTile
import io.github.andre88br.newgame.core.games.dominoes.Tile
import io.github.andre88br.newgame.core.games.dominoes.handTiles

/**
 * A mesa do dominó.
 *
 * O dominó não cabe numa grade de casas, e por isso não tem `BoardInteractor`: aqui o lance
 * nasce de tocar numa peça da própria mão. Quase tudo o que decide o que a tela mostra vem
 * pronto do `core-game` — [handTiles] diz em que pontas cada peça encaixa, e o estado já
 * chega com a mão do adversário virada para baixo.
 */
@Composable
fun DominoesSurface(
    state: DominoesState,
    viewer: Seat,
    enabled: Boolean,
    hinted: Move?,
    modifier: Modifier = Modifier,
    onMove: (Move) -> Unit,
) {
    val palette = LocalBoardPalette.current
    val hand = remember(state, viewer) { handTiles(state, viewer) }
    var asking by remember { mutableStateOf<HandTile?>(null) }

    // Mudou o estado: qualquer pergunta pendente perdeu o contexto.
    LaunchedEffect(state) { asking = null }

    val hintTile = (hinted as? DominoesMove)?.tile

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OpponentHand(count = state.handSize(viewer.opponent()), boneyard = state.boneyard.size)

        Line(line = state.line, palette = palette)

        Text(
            text = stringResource(R.string.dominoes_your_hand),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (item in hand) {
                HandTileView(
                    item = item,
                    palette = palette,
                    hinted = item.tile == hintTile,
                    enabled = enabled,
                    onClick = {
                        val direct = item.onlyMove
                        when {
                            direct != null -> onMove(direct)
                            item.playable -> asking = item
                            // Peça que não encaixa: em vez de não responder ao toque, o
                            // lance vai ao motor e volta com a explicação escrita, como
                            // acontece nas damas quando a captura é obrigatória.
                            else -> onMove(DominoesMove(item.tile, LineEnd.RIGHT))
                        }
                    },
                )
            }
        }
    }

    // Peça que serve nas duas pontas: a escolha é da pessoa, não da tela.
    asking?.let { item ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.dominoes_pick_end_title)) },
            text = {
                Text(stringResource(R.string.dominoes_pick_end_message, item.tile.low, item.tile.high))
            },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    onMove(DominoesMove(item.tile, LineEnd.LEFT))
                }) {
                    Text(stringResource(R.string.dominoes_end_left, state.leftEnd ?: 0))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    asking = null
                    onMove(DominoesMove(item.tile, LineEnd.RIGHT))
                }) {
                    Text(stringResource(R.string.dominoes_end_right, state.rightEnd ?: 0))
                }
            },
        )
    }
}

/** Quantas peças o adversário tem e quantas restam no monte — informação que é do jogo. */
@Composable
private fun OpponentHand(count: Int, boneyard: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.dominoes_opponent_tiles, count),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.dominoes_boneyard, boneyard),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A linha na mesa, deitada e rolável.
 *
 * Deitada de propósito: uma partida de dominó chega a vinte e tantas peças, e o serpenteado
 * do tabuleiro de verdade só existe porque a mesa acaba. Numa tela que rola, a linha reta é
 * mais fácil de ler — as duas pontas ficam sempre nas duas extremidades.
 */
@Composable
private fun Line(line: List<PlacedTile>, palette: BoardPalette) {
    val scroll = rememberScrollState()

    // A ponta nova entra sempre num dos lados: acompanhar o fim mantém à vista o que acabou
    // de ser jogado na direita, que é o caso comum.
    LaunchedEffect(line.size) { scroll.animateScrollTo(scroll.maxValue) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.darkSquare.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) {
        if (line.isEmpty()) {
            Text(
                text = stringResource(R.string.dominoes_empty_table),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier
                    .horizontalScroll(scroll)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (placed in line) {
                    Canvas(modifier = Modifier.size(width = 60.dp, height = 30.dp)) {
                        drawTile(placed.a, placed.b, horizontal = true, palette = palette)
                    }
                }
            }
        }
    }
}

/**
 * Uma peça da mão.
 *
 * Não marca as peças que dá para jogar, pela mesma decisão tomada para as damas: quem joga
 * olha a mesa e escolhe. Tocar numa peça que não encaixa não fica sem resposta — o motor
 * devolve o motivo, e a tela mostra.
 */
@Composable
private fun HandTileView(
    item: HandTile,
    palette: BoardPalette,
    hinted: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // O que o leitor de tela lê: "peça 2 por 5, encaixa nas duas pontas". Sem isto a mão
    // inteira seria uma fileira de desenhos mudos.
    val description = speechText(BoardSpeech.tile(item.tile, item.ends))

    Canvas(
        modifier = Modifier
            .size(width = 44.dp, height = 84.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        drawTile(item.tile.low, item.tile.high, horizontal = false, palette = palette)
        if (hinted) drawRoundedOutline(palette.hint)
    }
}

private fun DrawScope.drawRoundedOutline(color: Color) {
    val width = size.minDimension * 0.09f
    drawRect(
        color = color,
        topLeft = Offset(width / 2f, width / 2f),
        size = Size(size.width - width, size.height - width),
        style = Stroke(width = width),
    )
}

/**
 * Uma peça, com as duas metades e os pontos.
 *
 * [Tile.HIDDEN] desenha o verso: é como a mão do adversário chega aqui, já sem valor
 * nenhum — a tela não teria como mostrar o que não recebeu.
 */
private fun DrawScope.drawTile(
    a: Int,
    b: Int,
    horizontal: Boolean,
    palette: BoardPalette,
) {
    val edge = size.minDimension * 0.06f
    drawRect(color = palette.firstPiece, size = size)
    drawRect(color = palette.firstPieceEdge, size = size, style = Stroke(width = edge))

    if (a < 0 || b < 0) {
        // Verso: sem pontos, só a marca que diz "há uma peça aqui".
        drawRect(
            color = palette.darkSquare,
            topLeft = Offset(edge * 2f, edge * 2f),
            size = Size(size.width - edge * 4f, size.height - edge * 4f),
        )
        return
    }

    val halfWidth = if (horizontal) size.width / 2f else size.width
    val halfHeight = if (horizontal) size.height else size.height / 2f

    drawLine(
        color = palette.firstPieceEdge,
        start = if (horizontal) Offset(size.width / 2f, 0f) else Offset(0f, size.height / 2f),
        end = if (horizontal) Offset(size.width / 2f, size.height) else Offset(size.width, size.height / 2f),
        strokeWidth = edge,
    )

    drawPips(a, Offset(0f, 0f), Size(halfWidth, halfHeight), palette)
    drawPips(
        b,
        if (horizontal) Offset(size.width / 2f, 0f) else Offset(0f, size.height / 2f),
        Size(halfWidth, halfHeight),
        palette,
    )
}

/** Os pontos de uma metade, na disposição de dado. */
private fun DrawScope.drawPips(
    value: Int,
    origin: Offset,
    half: Size,
    palette: BoardPalette,
) {
    if (value <= 0) return
    val radius = minOf(half.width, half.height) * 0.11f

    // Colunas e linhas em terços: é a grade em que todo dado se desenha.
    fun spot(column: Int, row: Int) = Offset(
        origin.x + half.width * (column + 1) / 4f,
        origin.y + half.height * (row + 1) / 4f,
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
