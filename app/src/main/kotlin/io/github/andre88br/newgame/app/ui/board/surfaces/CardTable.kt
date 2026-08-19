package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
    /** Segue o ajuste de animações das Configurações: desligado, as mãos alheias já nascem completas. */
    animated: Boolean = true,
    /** Segue o espaço disponível na tela — veja [cardScaleFor]. Em 1 (o padrão), o tamanho de sempre. */
    scale: Float = 1f,
    modifier: Modifier = Modifier,
    handContent: @Composable (seat: Seat, count: Int, vertical: Boolean) -> Unit = { _, count, vertical ->
        OpponentFan(count = count, palette = palette, animated = animated, scale = scale, vertical = vertical)
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
    handContent: @Composable (seat: Seat, count: Int, vertical: Boolean) -> Unit,
    seatExtra: @Composable (Seat) -> Unit,
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val descricao = stringResource(R.string.a11y_opponent_hand, name, count)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.semantics { contentDescription = descricao }) {
            handContent(seat, count, vertical)
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
 *
 * Não é `private`: o pôquer chama isto direto para desenhar a mão virada para baixo de quem
 * ainda não mostrou as cartas, dentro do próprio [handContent] que decide, por cadeira, se
 * mostra a carta virada ou de costas.
 *
 * As cartas de um adversário não têm identidade própria — o estado chega redigido, e uma
 * costa de carta é igual à outra —, então a posição na fileira já é a chave certa: usar
 * [key] por índice deixa isso explícito, em vez de depender da ordem implícita do laço.
 *
 * [animated] segue o ajuste de animações das Configurações: desligado, a mão inteira já
 * nasce completa, sem o efeito cascata.
 *
 * [scale] segue o espaço disponível na tela — veja [cardScaleFor]. As costas dos adversários
 * crescem junto com a própria mão, para a mesa inteira parecer uma coisa só num tablet.
 */
@Composable
internal fun OpponentFan(
    count: Int,
    palette: BoardPalette,
    animated: Boolean = true,
    scale: Float = 1f,
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (count == 0) return

    val cardWidth = OPPONENT_CARD_WIDTH * scale
    val cardHeight = OPPONENT_CARD_HEIGHT * scale
    val fanStep = OPPONENT_FAN_STEP * scale

    val calcLargura = if (vertical) cardHeight else fanStep * (count - 1) + cardWidth
    val calcAltura = if (vertical) fanStep * (count - 1) + cardWidth else cardHeight
    val duration = if (animated) DEAL_ANIM_DURATION_MS else 0

    val animLargura by animateDpAsState(calcLargura, tween(duration), label = "opp_width")
    val animAltura by animateDpAsState(calcAltura, tween(duration), label = "opp_height")

    Box(modifier = modifier.size(animLargura, animAltura)) {
        for (index in 0 until count) {
            key(index) {
                val finalX = if (vertical) 0.dp else fanStep * index
                val finalY = if (vertical) fanStep * index else 0.dp

                // Se ainda não foi dada, a carta começa invisível e recolhida.
                val posicao = rememberDealAnimation(
                    index = index,
                    animated = animated,
                    finalX = finalX,
                    finalY = finalY,
                    startX = finalX - 15.dp,
                    startY = finalY + 15.dp,
                )

                FaceDownCard(
                    palette = palette,
                    width = if (vertical) cardHeight else cardWidth,
                    height = if (vertical) cardWidth else cardHeight,
                    modifier = Modifier
                        .offset(x = posicao.x, y = posicao.y)
                        .alpha(posicao.alpha)
                        .clearAndSetSemantics { }
                )
            }
        }
    }
}
