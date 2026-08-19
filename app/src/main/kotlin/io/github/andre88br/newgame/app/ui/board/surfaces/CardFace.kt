package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.DeckColorChoice
import io.github.andre88br.newgame.app.ui.theme.LocalDeckColor
import io.github.andre88br.newgame.app.ui.theme.Palette
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import kotlinx.coroutines.delay

/** Tamanho de uma carta na mão. Cabe sete numa tela de celular sem virar tira ilegível. */
val CARD_WIDTH = 46.dp
val CARD_HEIGHT = 66.dp

/** Quanto de cada carta aparece quando a mão está em leque. */
val CARD_FAN_STEP = 18.dp

/** Quanto a carta destacada sobe acima do leque, como quem puxa uma carta para fora. */
private val CARD_FAN_LIFT = 10.dp

/** Vermelho e preto de baralho, fixos e não vindos da paleta. */
private val SUIT_RED = Color(0xFFC62828)
private val SUIT_BLACK = Color(0xFF1B1B1B)

/** Largura abaixo da qual a carta fica no tamanho de sempre — a de um celular em pé. */
private const val CARD_SCALE_BASELINE_DP = 400

/** Teto do crescimento: uma tela bem larga não estica a carta a ponto de parecer um pôster. */
private const val CARD_SCALE_MAX = 1.4f

/**
 * Quanto ampliar o tamanho normal da carta, a partir da largura disponível na tela.
 *
 * Abaixo de [CARD_SCALE_BASELINE_DP] o resultado é sempre 1 — o tamanho de sempre, já
 * ajustado para caber num celular em pé; a carta nunca fica *menor* que isso, porque
 * legibilidade não é o que sobra quando a tela aperta. Acima disso — celular deitado,
 * tablet — ela cresce junto com a largura, até o teto de [CARD_SCALE_MAX]: o mesmo espírito
 * do teto que existe para a meia-peça do dominó, em [DominoesSurface], que impede a mesa de
 * virar dois tijolos gigantes quando sobra espaço demais.
 *
 * Cada mesa de carta calcula isto uma vez, a partir do próprio `BoxWithConstraints`, e passa
 * o resultado adiante como o parâmetro `scale` de [CardFan], [CardFace] e companhia.
 */
fun cardScaleFor(maxWidth: Dp): Float =
    (maxWidth.value / CARD_SCALE_BASELINE_DP).coerceIn(1f, CARD_SCALE_MAX)

/** Atraso entre uma carta e a seguinte começarem a deslizar, no efeito cascata de distribuir. */
private const val DEAL_STAGGER_MS = 40L

/**
 * Duração de cada animação de posição/opacidade de uma carta, com as animações ligadas.
 *
 * Não é `private`: [OpponentFan], em [CardTable], reaproveita o mesmo número para a
 * animação de tamanho do leque dos adversários, em vez de inventar outra duração ao lado.
 */
internal const val DEAL_ANIM_DURATION_MS = 220

/** Duração da animação de uma carta pousando na mesa ou no descarte, com animações ligadas. */
private const val LAND_ANIM_DURATION_MS = 180

/** De que tamanho uma carta nasce ao pousar, antes de crescer até o tamanho normal. */
private const val LAND_ANIM_MIN_SCALE = 0.82f

/** Posição e opacidade animadas de uma carta ou peça sendo distribuída. */
data class DealAnimationState(val x: Dp, val y: Dp, val alpha: Float)

/**
 * O estado de uma carta (ou peça) sendo "distribuída": nasce deslocada de [startX]/[startY] e
 * invisível, e desliza até [finalX]/[finalY] com um atraso proporcional a [index] — o efeito
 * cascata usado na mão ([CardFan]), no leque dos adversários ([OpponentFan] em [CardTable]) e
 * na mão do dominó ([FaceDownCard] com peças).
 *
 * Quem chama isto de dentro de um `key(...)` por carta (como [CardFan] faz) garante que uma
 * carta que já apareceu não recomeça a distribuição só porque a mão ao redor dela mudou de
 * tamanho — o [remember] fica preso à identidade da carta, não à posição dela na lista.
 *
 * Quando [animated] é falso — o ajuste de animações desligado nas Configurações —, a carta já
 * nasce na posição final: sem atraso, sem movimento, sem espera.
 */
@Composable
fun rememberDealAnimation(
    index: Int,
    animated: Boolean,
    finalX: Dp,
    finalY: Dp,
    startX: Dp = finalX,
    startY: Dp = finalY,
): DealAnimationState {
    var dealt by remember { mutableStateOf(!animated) }
    LaunchedEffect(animated) {
        if (!animated) {
            dealt = true
            return@LaunchedEffect
        }
        dealt = false
        delay(index * DEAL_STAGGER_MS) // As cartas deslizam com um atraso entre si.
        dealt = true
    }

    val duration = if (animated) DEAL_ANIM_DURATION_MS else 0
    val animX by animateDpAsState(if (dealt) finalX else startX, tween(duration), label = "deal_x")
    val animY by animateDpAsState(if (dealt) finalY else startY, tween(duration), label = "deal_y")
    val animAlpha by animateFloatAsState(if (dealt) 1f else 0f, tween(duration), label = "deal_alpha")
    return DealAnimationState(animX, animY, animAlpha)
}

/**
 * Um modificador para uma carta pousando na mesa: nasce um pouco menor e transparente, e
 * cresce até o tamanho normal — usado onde uma jogada ou um descarte de verdade acontece (a
 * rodada do truco, a vaza da copas, o descarte da paciência/canastra/pife), em vez de a carta
 * simplesmente aparecer pronta.
 *
 * Roda uma vez por identidade: chame isto de dentro de um `key(carta)` (ou equivalente) no
 * ponto onde a carta é desenhada, para que ela pouse uma vez só e não reanime a cada
 * recomposição motivada por outra coisa.
 *
 * Quando [animated] é falso, a carta já nasce no tamanho e na opacidade finais.
 */
@Composable
fun rememberLandAnimation(animated: Boolean): Modifier {
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { landed = true }

    val duration = if (animated) LAND_ANIM_DURATION_MS else 0
    val progress by animateFloatAsState(if (landed) 1f else 0f, tween(duration), label = "land_progress")

    return Modifier.graphicsLayer {
        alpha = progress
        val scale = LAND_ANIM_MIN_SCALE + (1f - LAND_ANIM_MIN_SCALE) * progress
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Uma mão em leque com animações fluidas de Distribuição (Dealing) e Reordenação.
 *
 * [animated] segue o ajuste de animações das Configurações: desligado, a mão inteira já
 * nasce na posição final, sem o efeito cascata.
 *
 * [scale] segue o espaço disponível na tela — veja [cardScaleFor]: em 1 (o padrão) a mão sai
 * do mesmo tamanho de sempre; maior que 1, cada carta e o passo do leque crescem juntos, sem
 * mudar a proporção entre eles.
 */
@Composable
fun CardFan(
    cards: List<Card>,
    palette: BoardPalette,
    animated: Boolean = true,
    scale: Float = 1f,
    modifier: Modifier = Modifier,
    isRaised: (Int, Card) -> Boolean = { _, _ -> false },
    isPlayable: (Int, Card) -> Boolean = { _, _ -> true },
    onClick: ((Int, Card) -> Unit)? = null,
) {
    if (cards.isEmpty()) return

    val cardWidth = CARD_WIDTH * scale
    val cardHeight = CARD_HEIGHT * scale
    val fanStep = CARD_FAN_STEP * scale
    val fanLift = CARD_FAN_LIFT * scale

    // O tamanho total do leque é animado para a mão encolher e crescer suavemente.
    val largura by animateDpAsState(
        targetValue = fanStep * (cards.size - 1) + cardWidth,
        animationSpec = tween(if (animated) DEAL_ANIM_DURATION_MS else 0),
        label = "fan_width",
    )
    val altura = cardHeight + fanLift

    Box(modifier = modifier.size(largura, altura)) {
        cards.forEachIndexed { index, carta ->
            val puxada = isRaised(index, carta)
            val occurrenceIndex = cards.take(index).count { it == carta }

            key(carta.rank, carta.suit, occurrenceIndex) {
                // Se ainda não foi dada, a carta começa escondida um pouco para baixo e para
                // a esquerda da posição em que vai ficar no leque.
                val posicao = rememberDealAnimation(
                    index = index,
                    animated = animated,
                    finalX = fanStep * index,
                    finalY = if (puxada) 0.dp else fanLift,
                    startX = fanStep * index - 30.dp * scale,
                    startY = cardHeight / 2,
                )

                CardFace(
                    card = carta,
                    palette = palette,
                    selected = puxada,
                    playable = isPlayable(index, carta),
                    scale = scale,
                    onClick = onClick?.let { acao -> { acao(index, carta) } },
                    modifier = Modifier
                        .offset(x = posicao.x, y = posicao.y)
                        .alpha(posicao.alpha)
                        .zIndex(index.toFloat() + if (puxada) 0.5f else 0f)
                )
            }
        }
    }
}

/**
 * Uma carta, virada para cima ou de costas.
 *
 * [scale] segue o espaço disponível na tela — veja [cardScaleFor]. O contorno fica com a
 * mesma espessura em qualquer tamanho: linha fina precisa continuar fina para marcar
 * seleção/dica sem virar moldura, e a diferença já é pequena demais para valer a conta.
 */
@Composable
fun CardFace(
    card: Card,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    hinted: Boolean = false,
    playable: Boolean = true,
    scale: Float = 1f,
    onClick: (() -> Unit)? = null,
) {
    if (card.isHidden) {
        FaceDownCard(palette = palette, modifier = modifier, width = CARD_WIDTH * scale, height = CARD_HEIGHT * scale)
        return
    }

    val nome = cardName(card)
    val tinta = if (card.isRed) SUIT_RED else SUIT_BLACK
    val contorno = when {
        hinted -> palette.hint
        selected -> palette.selection
        else -> palette.border
    }

    Box(
        modifier = modifier
            .size(CARD_WIDTH * scale, CARD_HEIGHT * scale)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.firstPiece)
            .border(
                width = if (hinted || selected) 3.dp else 1.dp,
                color = contorno,
                shape = RoundedCornerShape(6.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClickLabel = nome) { onClick() } else Modifier)
            .alpha(if (playable) 1f else 0.45f)
            .semantics { contentDescription = nome },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 4.dp, top = 2.dp),
        ) {
            Text(
                text = card.rank.short,
                color = tinta,
                fontSize = 13.sp * scale,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(text = card.suit.symbol, color = tinta, fontSize = 11.sp * scale)
        }
        Text(
            text = if (card.isJoker) "★" else card.suit.symbol,
            color = tinta,
            fontSize = 24.sp * scale,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 8.dp),
        )
    }
}

@Composable
fun FaceDownCard(
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    width: Dp = CARD_WIDTH,
    height: Dp = CARD_HEIGHT,
) {
    val (fundo, miolo) = when (LocalDeckColor.current) {
        DeckColorChoice.CLASSIC -> palette.secondPiece to palette.secondPieceEdge
        DeckColorChoice.RED -> Palette.DeckRed to Palette.DeckRedEdge
        DeckColorChoice.BLUE -> Palette.DeckBlue to Palette.DeckBlueEdge
        DeckColorChoice.PURPLE -> Palette.DeckPurple to Palette.DeckPurpleEdge
    }
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(6.dp))
            .background(fundo)
            .border(1.dp, palette.border, RoundedCornerShape(6.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(miolo),
        )
    }
}

@Composable
fun cardName(card: Card): String {
    if (card.isHidden) return stringResource(R.string.a11y_card_hidden)
    if (card.isJoker) return stringResource(R.string.a11y_card_joker)
    return stringResource(R.string.a11y_card, rankName(card.rank), suitName(card.suit))
}

@Composable
private fun rankName(rank: Rank): String = stringResource(
    when (rank) {
        Rank.ACE -> R.string.rank_ace
        Rank.JACK -> R.string.rank_jack
        Rank.QUEEN -> R.string.rank_queen
        Rank.KING -> R.string.rank_king
        else -> R.string.rank_number
    },
    rank.short,
)

@Composable
private fun suitName(suit: Suit): String = stringResource(
    when (suit) {
        Suit.CLUBS -> R.string.suit_clubs
        Suit.DIAMONDS -> R.string.suit_diamonds
        Suit.HEARTS -> R.string.suit_hearts
        Suit.SPADES -> R.string.suit_spades
    },
)
