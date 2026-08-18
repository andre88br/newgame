package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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

/**
 * Uma mão em leque com animações fluidas de Distribuição (Dealing) e Reordenação.
 */
@Composable
fun CardFan(
    cards: List<Card>,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    isRaised: (Int, Card) -> Boolean = { _, _ -> false },
    isPlayable: (Int, Card) -> Boolean = { _, _ -> true },
    onClick: ((Int, Card) -> Unit)? = null,
) {
    if (cards.isEmpty()) return

    // O tamanho total do leque é animado para a mão encolher e crescer suavemente.
    val largura by animateDpAsState(targetValue = CARD_FAN_STEP * (cards.size - 1) + CARD_WIDTH, label = "fan_width")
    val altura = CARD_HEIGHT + CARD_FAN_LIFT

    Box(modifier = modifier.size(largura, altura)) {
        cards.forEachIndexed { index, carta ->
            val puxada = isRaised(index, carta)
            val occurrenceIndex = cards.take(index).count { it == carta }

            key(carta.rank, carta.suit, occurrenceIndex) {
                
                // Variável de controle para a animação de entrada (dar as cartas)
                var cardDealt by remember { mutableStateOf(false) }
                
                // Efeito cascata: a carta espera o seu momento para aparecer
                LaunchedEffect(Unit) {
                    delay(index * 40L) // As cartas deslizam com um atraso de 40ms entre si
                    cardDealt = true
                }

                // Cálculo da posição. Se ainda não foi dada, ela começa escondida um pouco para baixo e para a esquerda
                val targetX = if (cardDealt) CARD_FAN_STEP * index else (CARD_FAN_STEP * index) - 30.dp
                val targetY = if (cardDealt) (if (puxada) 0.dp else CARD_FAN_LIFT) else CARD_HEIGHT / 2
                val targetAlpha = if (cardDealt) 1f else 0f

                val animX by animateDpAsState(targetValue = targetX, label = "animX")
                val animY by animateDpAsState(targetValue = targetY, label = "animY")
                val animAlpha by animateFloatAsState(targetValue = targetAlpha, label = "animAlpha")

                CardFace(
                    card = carta,
                    palette = palette,
                    selected = puxada,
                    playable = isPlayable(index, carta),
                    onClick = onClick?.let { acao -> { acao(index, carta) } },
                    modifier = Modifier
                        .offset(x = animX, y = animY)
                        .alpha(animAlpha)
                        .zIndex(if (puxada) 1f else 0f) 
                )
            }
        }
    }
}

@Composable
fun CardFace(
    card: Card,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    hinted: Boolean = false,
    playable: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    if (card.isHidden) {
        FaceDownCard(palette = palette, modifier = modifier)
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
            .size(CARD_WIDTH, CARD_HEIGHT)
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
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(text = card.suit.symbol, color = tinta, fontSize = 11.sp)
        }
        Text(
            text = if (card.isJoker) "★" else card.suit.symbol,
            color = tinta,
            fontSize = 24.sp,
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
