package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
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

/**
 * O nome de uma cadeira, para quem quer que olhe.
 *
 * A própria cadeira já chega com o nome que a pessoa digitou (o campo "Seu nome" da
 * configuração escreve exatamente no índice dela) — só cai no "Você"/"Jogador N" genérico
 * numa partida salva de antes de os nomes existirem. É por isso que a mesma função serve
 * para o viewer e para os adversários: a diferença de tratamento está só no texto de
 * reserva, não na fonte da informação.
 */
@Composable
fun seatLabel(index: Int, viewer: Seat, names: List<String>): String =
    names.getOrNull(index)?.takeIf { it.isNotBlank() }
        ?: if (index == viewer.index) {
            stringResource(R.string.player_you)
        } else {
            stringResource(R.string.dominoes_opponent_seat, index + 1)
        }

/**
 * A mesa com os adversários sentados ao redor, e o que estiver em jogo no meio — o monte, a
 * vaza, as cartas da rodada.
 *
 * Onde cada um senta segue a mesa de verdade: a cadeira seguinte à sua fica à **esquerda**, a
 * de duas adiante **em frente**, a de três à **direita**. Em quatro, isso põe o parceiro em
 * frente — a mesma dupla que joga junta na canastra e no truco é sempre a cadeira de índice
 * de mesma paridade, e a de duas adiante tem exatamente essa paridade. A dois só há a de
 * frente; a três, só as duas laterais, sem ninguém em frente.
 *
 * As mãos aparecem viradas, como as de verdade: o estado já chega redigido do motor
 * ([Card.HIDDEN][io.github.andre88br.newgame.core.cards.Card]), então não há nada escondido
 * demais aqui — só o desenho de uma mesa em vez de uma lista de números.
 */
@Composable
fun CardTable(
    seats: Int,
    viewer: Seat,
    /** O nome de cada cadeira. Vazia numa partida salva antes de existirem nomes. */
    names: List<String>,
    handSize: (Seat) -> Int,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    // A cadeira seguinte à sua, depois a de duas adiante, depois a de três — na ordem em que
    // se dá a volta na mesa a partir de quem olha.
    val outras = (1 until seats).map { offset -> Seat((viewer.index + offset) % seats) }
    val (esquerda, cima, direita) = when (seats) {
        2 -> Triple(null, outras.getOrNull(0), null)
        3 -> Triple(outras.getOrNull(0), null, outras.getOrNull(1))
        else -> Triple(outras.getOrNull(0), outras.getOrNull(1), outras.getOrNull(2))
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (cima != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OpponentHand(count = handSize(cima), name = seatLabel(cima.index, viewer, names), palette = palette)
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (esquerda != null) {
                OpponentHand(
                    count = handSize(esquerda),
                    name = seatLabel(esquerda.index, viewer, names),
                    palette = palette,
                    vertical = true,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Box(modifier = Modifier.weight(1f)) { center() }
            if (direita != null) {
                Spacer(modifier = Modifier.width(6.dp))
                OpponentHand(
                    count = handSize(direita),
                    name = seatLabel(direita.index, viewer, names),
                    palette = palette,
                    vertical = true,
                )
            }
        }
    }
}

/**
 * A mão de um adversário, virada para baixo.
 *
 * As cartas individuais saem da árvore de acessibilidade — ninguém precisa ouvir "carta
 * virada" treze vezes seguidas — e a fileira inteira leva uma descrição só, com o nome e a
 * contagem, no mesmo espírito do que o dominó já faz com a mão do outro lado.
 */
@Composable
private fun OpponentHand(
    count: Int,
    name: String,
    palette: BoardPalette,
    vertical: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val descricao = stringResource(R.string.a11y_opponent_hand, name, count)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        OpponentFan(
            count = count,
            palette = palette,
            vertical = vertical,
            modifier = Modifier.semantics { contentDescription = descricao },
        )
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * O leque de costas de carta do adversário, no mesmo molde de [CardFan]: passo fixo, cada
 * carta por cima da anterior. Aqui não há o que tocar nem o que destacar — é só a contagem.
 *
 * [vertical] é para quem senta à esquerda ou à direita: numa mesa de verdade essas duas
 * cadeiras estão viradas para o centro, de lado para quem olha, então o leque delas cresce
 * para baixo, não para o lado — e cada carta gira noventa graus junto (por isso [FaceDownCard]
 * recebe largura e altura trocadas: é a mesma carta, deitada).
 */
@Composable
private fun OpponentFan(count: Int, palette: BoardPalette, vertical: Boolean = false, modifier: Modifier = Modifier) {
    if (count == 0) return

    Layout(
        modifier = modifier,
        content = {
            repeat(count) {
                FaceDownCard(
                    palette = palette,
                    width = if (vertical) OPPONENT_CARD_HEIGHT else OPPONENT_CARD_WIDTH,
                    height = if (vertical) OPPONENT_CARD_WIDTH else OPPONENT_CARD_HEIGHT,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        },
    ) { measurables, constraints ->
        val soltos = constraints.copy(minWidth = 0, minHeight = 0)
        val postas = measurables.map { it.measure(soltos) }
        val passo = OPPONENT_FAN_STEP.roundToPx()

        if (vertical) {
            val largura = postas.maxOf { it.width }
            val altura = passo * (postas.size - 1) + postas.last().height
            layout(largura, altura) {
                postas.forEachIndexed { index, posta -> posta.placeRelative(x = 0, y = passo * index) }
            }
        } else {
            val largura = passo * (postas.size - 1) + postas.last().width
            val altura = postas.maxOf { it.height }
            layout(largura, altura) {
                postas.forEachIndexed { index, posta -> posta.placeRelative(x = passo * index, y = 0) }
            }
        }
    }
}
