package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import io.github.andre88br.newgame.core.games.dominoes.DominoesLayout
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
 * pronto do `core-game` — [handTiles] diz em que pontas cada peça encaixa, `DominoesLayout`
 * diz onde cada peça fica na mesa, e o estado já chega com a mão do adversário virada.
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

        // A mesa fica com todo o espaço que sobrar: é a parte que precisa ser vista.
        Table(
            line = state.line,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

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
 * A mesa, em duas dimensões.
 *
 * A linha **serpenteia**: vai até a borda, desce e volta na direção contrária, com as
 * carroças atravessadas — como numa mesa de verdade quando o espaço acaba. Em linha reta a
 * partida vira uma fita que só cabe rolando, e quem joga perde de vista as duas pontas, que
 * é exatamente o que precisa enxergar para decidir o lance.
 *
 * Onde cada peça fica é decidido em `DominoesLayout`, no `core-game`, onde os testes
 * conferem que nada sai da mesa e que nenhuma peça cai por cima de outra. Aqui só se
 * converte meia-peça em pixel.
 */
@Composable
private fun Table(
    line: List<PlacedTile>,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .heightIn(min = 120.dp)
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
            return@BoxWithConstraints
        }

        // Mesa larga acomoda mais peças por fileira; mesa estreita serpenteia mais cedo.
        val columns = DominoesLayout.columnsFor(maxWidth.value)
        val table = remember(line, columns) { DominoesLayout.table(line, columns) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val padding = size.minDimension * 0.03f
            val usableWidth = size.width - padding * 2
            val usableHeight = size.height - padding * 2

            // Cabe inteira, sempre: a mesa encolhe em vez de cortar peça ou pedir rolagem.
            // Com no máximo 28 peças o pior caso ainda fica legível.
            val unit = minOf(usableWidth / table.width, usableHeight / table.height)
            val originX = (size.width - table.width * unit) / 2f
            val originY = (size.height - table.height * unit) / 2f

            for (laid in table.tiles) {
                // Numa fileira que volta, a linha corre para a esquerda: o `a` da peça fica
                // à direita. Sem inverter, os números das pontas não bateriam com o vizinho.
                val first = if (laid.reversed) laid.tile.b else laid.tile.a
                val second = if (laid.reversed) laid.tile.a else laid.tile.b

                drawTileAt(
                    first = first,
                    second = second,
                    topLeft = Offset(originX + laid.x * unit, originY + laid.y * unit),
                    tileSize = Size(laid.width * unit, laid.height * unit),
                    // Em pé na curva e atravessada na carroça: as metades ficam empilhadas.
                    stacked = laid.stacked,
                    palette = palette,
                )
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
        drawTileAt(
            first = item.tile.low,
            second = item.tile.high,
            topLeft = Offset.Zero,
            tileSize = size,
            stacked = true,
            palette = palette,
        )
        if (hinted) {
            val width = size.minDimension * 0.09f
            drawRect(
                color = palette.hint,
                topLeft = Offset(width / 2f, width / 2f),
                size = Size(size.width - width, size.height - width),
                style = Stroke(width = width),
            )
        }
    }
}

/**
 * Uma peça, com as duas metades e os pontos.
 *
 * [Tile.HIDDEN] desenha o verso: é como a mão do adversário chega aqui, já sem valor
 * nenhum — a tela não teria como mostrar o que não recebeu.
 */
private fun DrawScope.drawTileAt(
    first: Int,
    second: Int,
    topLeft: Offset,
    tileSize: Size,
    /** As duas metades ficam uma sobre a outra, em vez de lado a lado. */
    stacked: Boolean,
    palette: BoardPalette,
) {
    val horizontal = !stacked
    val edge = minOf(tileSize.width, tileSize.height) * 0.08f
    drawRect(color = palette.firstPiece, topLeft = topLeft, size = tileSize)
    drawRect(
        color = palette.firstPieceEdge,
        topLeft = topLeft,
        size = tileSize,
        style = Stroke(width = edge),
    )

    if (first < 0 || second < 0) {
        // Verso: sem pontos, só a marca que diz "há uma peça aqui".
        drawRect(
            color = palette.darkSquare,
            topLeft = Offset(topLeft.x + edge * 2f, topLeft.y + edge * 2f),
            size = Size(tileSize.width - edge * 4f, tileSize.height - edge * 4f),
        )
        return
    }

    val halfSize = if (horizontal) {
        Size(tileSize.width / 2f, tileSize.height)
    } else {
        Size(tileSize.width, tileSize.height / 2f)
    }

    drawLine(
        color = palette.firstPieceEdge,
        start = if (horizontal) {
            Offset(topLeft.x + tileSize.width / 2f, topLeft.y)
        } else {
            Offset(topLeft.x, topLeft.y + tileSize.height / 2f)
        },
        end = if (horizontal) {
            Offset(topLeft.x + tileSize.width / 2f, topLeft.y + tileSize.height)
        } else {
            Offset(topLeft.x + tileSize.width, topLeft.y + tileSize.height / 2f)
        },
        strokeWidth = edge,
    )

    drawPips(first, topLeft, halfSize, palette)
    drawPips(
        second,
        if (horizontal) {
            Offset(topLeft.x + tileSize.width / 2f, topLeft.y)
        } else {
            Offset(topLeft.x, topLeft.y + tileSize.height / 2f)
        },
        halfSize,
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

    // Colunas e linhas em quartos: é a grade em que todo dado se desenha.
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
