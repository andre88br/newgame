package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.core.engine.Seat
import kotlinx.coroutines.delay

/** Tamanho da carta de um adversário: menor que a sua, porque o que importa é contar, não ler. */
private val OPPONENT_CARD_WIDTH = 32.dp
private val OPPONENT_CARD_HEIGHT = 46.dp

/** Quanto de cada carta aparece no leque do adversário — o mesmo espírito de [CARD_FAN_STEP]. */
private val OPPONENT_FAN_STEP = 9.dp

@Composable
fun seatLabel(index: Int, viewer: Seat, names: List<String>): String =
    names.getOrNull(index)?.takeIf { it.isNotBlank() }
        ?: if (index == viewer.index) {
            stringResource(R.string.player_you)
        } else {
            stringResource(R.string.dominoes_opponent_seat, index + 1)
        }

/**
 * A mesa com os adversários sentados ao redor, e o que estiver em jogo no meio.
 *
 * [handContent] desenha o que cada mão adversária mostra — o leque de costas de carta, por
 * padrão — mas quem senta ao redor é sempre a mesma conta, esteja a mão em jogo feita de
 * cartas ou de peças de dominó: é essa disposição (e a animação de distribuir) que o dominó
 * ganha de graça ao passar seu próprio desenho de peça aqui, em vez de duplicar a mesa.
 */
@Composable
fun CardTable(
    seats: Int,
    viewer: Seat,
    names: List<String>,
    handSize: (Seat) -> Int,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    handContent: @Composable (count: Int, vertical: Boolean) -> Unit = { count, vertical ->
        OpponentFan(count = count, palette = palette, vertical = vertical)
    },
    /**
     * O que mostrar junto do nome de cada adversário — vazio por padrão. O pôquer usa isto
     * para colocar a pilha de fichas de cada um ao lado da própria mão, em vez de só numa
     * faixa separada em cima da mesa; os outros jogos de carta não passam nada aqui.
     */
    seatExtra: @Composable (Seat) -> Unit = {},
    center: @Composable () -> Unit,
) {
    val outras = (1 until seats).map { offset -> Seat((viewer.index + offset) % seats) }
    val (esquerda, cima, direita) = when (seats) {
        2 -> Triple(null, outras.getOrNull(0), null)
        3 -> Triple(outras.getOrNull(0), null, outras.getOrNull(1))
        else -> Triple(outras.getOrNull(0), outras.getOrNull(1), outras.getOrNull(2))
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (cima != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OpponentHand(
                    seat = cima,
                    count = handSize(cima),
                    name = seatLabel(cima.index, viewer, names),
                    handContent = handContent,
                    seatExtra = seatExtra,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (esquerda != null) {
                OpponentHand(
                    seat = esquerda,
                    count = handSize(esquerda),
                    name = seatLabel(esquerda.index, viewer, names),
                    handContent = handContent,
                    seatExtra = seatExtra,
                    vertical = true,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Box(modifier = Modifier.weight(1f)) { center() }
            if (direita != null) {
                Spacer(modifier = Modifier.width(6.dp))
                OpponentHand(
                    seat = direita,
                    count = handSize(direita),
                    name = seatLabel(direita.index, viewer, names),
                    handContent = handContent,
                    seatExtra = seatExtra,
                    vertical = true,
                )
            }
        }
    }
}

@Composable
private fun OpponentHand(
    seat: Seat,
    count: Int,
    name: String,
    handContent: @Composable (count: Int, vertical: Boolean) -> Unit,
    seatExtra: @Composable (Seat) -> Unit,
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val descricao = stringResource(R.string.a11y_opponent_hand, name, count)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.semantics { contentDescription = descricao }) {
            handContent(count, vertical)
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
        )
        seatExtra(seat)
    }
}

/**
 * Animação fluida da mão dos adversários com distribuição das cartas.
 */
@Composable
private fun OpponentFan(count: Int, palette: BoardPalette, vertical: Boolean = false, modifier: Modifier = Modifier) {
    if (count == 0) return

    val calcLargura = if (vertical) OPPONENT_CARD_HEIGHT else OPPONENT_FAN_STEP * (count - 1) + OPPONENT_CARD_WIDTH
    val calcAltura = if (vertical) OPPONENT_FAN_STEP * (count - 1) + OPPONENT_CARD_WIDTH else OPPONENT_CARD_HEIGHT

    val animLargura by animateDpAsState(targetValue = calcLargura, label = "opp_width")
    val animAltura by animateDpAsState(targetValue = calcAltura, label = "opp_height")

    Box(modifier = modifier.size(animLargura, animAltura)) {
        for (index in 0 until count) {
            
            // Controle da animação de distribuição para os adversários
            var cardDealt by remember { mutableStateOf(false) }
            LaunchedEffect(index) {
                delay(index * 40L)
                cardDealt = true
            }

            val finalX = if (vertical) 0.dp else OPPONENT_FAN_STEP * index
            val finalY = if (vertical) OPPONENT_FAN_STEP * index else 0.dp

            // Se ainda não foi dada, a carta começa invisível e recolhida
            val targetX = if (cardDealt) finalX else finalX - 15.dp
            val targetY = if (cardDealt) finalY else finalY + 15.dp
            val targetAlpha = if (cardDealt) 1f else 0f

            val animX by animateDpAsState(targetValue = targetX, label = "opp_x")
            val animY by animateDpAsState(targetValue = targetY, label = "opp_y")
            val animAlpha by animateFloatAsState(targetValue = targetAlpha, label = "opp_alpha")

            FaceDownCard(
                palette = palette,
                width = if (vertical) OPPONENT_CARD_HEIGHT else OPPONENT_CARD_WIDTH,
                height = if (vertical) OPPONENT_CARD_WIDTH else OPPONENT_CARD_HEIGHT,
                modifier = Modifier
                    .offset(x = animX, y = animY)
                    .alpha(animAlpha)
                    .clearAndSetSemantics { }
            )
        }
    }
}
