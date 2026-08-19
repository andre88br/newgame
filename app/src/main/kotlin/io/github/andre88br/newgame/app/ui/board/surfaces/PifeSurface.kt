package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.cards.sortedForHand
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.pife.PIFE_GROUPS
import io.github.andre88br.newgame.core.games.pife.PifeMove
import io.github.andre88br.newgame.core.games.pife.PifePhase
import io.github.andre88br.newgame.core.games.pife.PifeState
import io.github.andre88br.newgame.core.games.pife.bestGroupCount

/**
 * A mesa do pife.
 *
 * É a mais enxuta dos jogos de carta, porque o jogo é: comprar uma e jogar uma fora. A mesa
 * mostra os adversários sentados ao redor, de onde se compra no meio, e a mão embaixo — e os
 * botões trocam conforme o tempo da vez, como na canastra.
 *
 * O descarte é escolhido e só então confirmado, e não jogado no toque, de propósito: é o
 * único lance do jogo, é irreversível, e um toque torto jogaria fora a carta errada.
 */
@Composable
fun PifeSurface(
    state: PifeState,
    viewer: Seat,
    names: List<String>,
    enabled: Boolean,
    hinted: Move?,
    /** Segue o ajuste de animações das Configurações: desligado, cartas e descarte já nascem prontos. */
    animated: Boolean = true,
    modifier: Modifier = Modifier,
    onMove: (Move) -> Unit,
) {
    val palette = LocalBoardPalette.current
    val mao = remember(state, viewer) { state.hand(viewer).sortedForHand() }
    // Estado novo é vez nova: o que estava escolhido perdeu o sentido.
    var escolhida by remember(state) { mutableStateOf<Int?>(null) }

    val minhaVez = state.turn == viewer
    val carta = escolhida?.let { mao.getOrNull(it) }
    // A dica vem como lance pronto; aqui ela vira o realce da carta que ela descartaria.
    val sugerida = (hinted as? PifeMove.Discard)?.card

    // A largura disponível decide o quanto a carta cresce — veja [cardScaleFor]. Numa tela
    // estreita o resultado é sempre 1 (o tamanho de sempre); numa tela deitada ou num
    // tablet, a mesa inteira cresce junto.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val scale = cardScaleFor(maxWidth)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HandCounts(state = state, viewer = viewer)

            // Os adversários sentados ao redor, com o monte e o lixo no meio.
            CardTable(
                seats = state.seats,
                viewer = viewer,
                names = names,
                handSize = { seat -> state.handSize(seat) },
                palette = palette,
                animated = animated,
                scale = scale,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TableInfo(state = state, palette = palette, animated = animated, scale = scale)
            }

            Text(
                text = when {
                    !minhaVez -> stringResource(R.string.pife_wait)
                    state.phase == PifePhase.DRAW -> stringResource(R.string.pife_draw_prompt)
                    else -> stringResource(R.string.pife_discard_prompt)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.phase == PifePhase.DRAW) {
                    Button(
                        onClick = { onMove(PifeMove.DrawStock) },
                        enabled = enabled && minhaVez,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.pife_draw_stock))
                    }
                    OutlinedButton(
                        onClick = { onMove(PifeMove.DrawDiscard) },
                        enabled = enabled && minhaVez && state.discardTop != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.pife_take_discard))
                    }
                } else {
                    Button(
                        onClick = { carta?.let { onMove(PifeMove.Discard(it)) } },
                        enabled = enabled && minhaVez && carta != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.pife_discard))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                CardFan(
                    cards = mao,
                    palette = palette,
                    animated = animated,
                    scale = scale,
                    isRaised = { index, atual -> index == escolhida || atual == sugerida },
                    onClick = if (enabled && minhaVez && state.phase == PifePhase.DISCARD) {
                        { index, _ -> escolhida = if (index == escolhida) null else index }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

/**
 * Quantos grupos a sua mão já fecha.
 *
 * Não é informação escondida: quem olha a própria mão sabe o que já fechou. Mas com nove
 * cartas fora de ordem a conta escapa, e errar de menos aqui custa a partida — a pessoa
 * descarta a carta que fechava o grupo. A contagem alheia aparece na mesa, com os
 * adversários sentados ao redor.
 */
@Composable
private fun HandCounts(state: PifeState, viewer: Seat) {
    // A conta percorre todos os arranjos de nove cartas: barata uma vez, cara a cada
    // recomposição. Ela só muda quando a mão muda.
    val fechados = remember(state, viewer) { bestGroupCount(state.hand(viewer)) }

    Text(
        text = stringResource(R.string.pife_groups, fechados, PIFE_GROUPS),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** De onde se compra: o monte virado para baixo e a carta de cima do lixo, à vista. */
@Composable
private fun TableInfo(state: PifeState, palette: BoardPalette, animated: Boolean, scale: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.stock.isEmpty()) {
                Box(modifier = Modifier.padding(2.dp)) { Text("—") }
            } else {
                FaceDownCard(palette = palette, width = CARD_WIDTH * scale, height = CARD_HEIGHT * scale)
            }
            Text(
                text = stringResource(R.string.pife_stock, state.stock.size),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val topo = state.discardTop
            if (topo == null) {
                Box(modifier = Modifier.padding(2.dp)) { Text("—") }
            } else {
                // A carta descartada pousa uma vez: a chave é o tamanho da pilha, não a
                // carta em si, para não confundir duas cartas iguais em descartes seguidos.
                key(state.discard.size) {
                    CardFace(
                        card = topo,
                        palette = palette,
                        scale = scale,
                        modifier = rememberLandAnimation(animated),
                    )
                }
            }
            Text(
                text = stringResource(R.string.pife_pile, state.discard.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
