package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit

/** Tamanho de uma carta na mão. Cabe sete numa tela de celular sem virar tira ilegível. */
val CARD_WIDTH = 46.dp
val CARD_HEIGHT = 66.dp

/**
 * Vermelho e preto de baralho, fixos e não vindos da paleta.
 *
 * A carta é sempre creme, no tema claro e no escuro — carta de baralho é branca, e um
 * baralho que trocasse de cor com o tema deixaria de parecer um baralho. Como o fundo não
 * muda, a tinta também não pode mudar.
 */
private val SUIT_RED = Color(0xFFC62828)
private val SUIT_BLACK = Color(0xFF1B1B1B)

/**
 * Uma carta virada para cima.
 *
 * O desenho é texto, e não figura: valor no canto e naipe grande no meio. É o que um
 * baralho de verdade faz, e é o que continua legível quando a carta mede meio dedo — uma
 * figura de dama desenhada neste tamanho vira borrão, e o que a pessoa precisa ler é
 * "dama de espadas", não o retrato dela.
 */
@Composable
fun CardFace(
    card: Card,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    /** Escolhida agora — a carta sobe um pouco e ganha contorno. */
    selected: Boolean = false,
    /** Sugerida pela dica. */
    hinted: Boolean = false,
    /** Não dá para jogar agora: fica apagada, mas continua tocável para explicar por quê. */
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
            // Carta que não serve continua respondendo ao toque: quem tocou recebe do motor
            // o motivo escrito, em vez de um toque que não faz nada.
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

/**
 * Costas de carta.
 *
 * Aparece na mão de quem está do outro lado. O que existe ali é literalmente [Card.HIDDEN]:
 * o estado já chegou redigido do motor, e não há valor nenhum guardado atrás deste desenho.
 */
@Composable
fun FaceDownCard(palette: BoardPalette, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(CARD_WIDTH, CARD_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.secondPiece)
            .border(1.dp, palette.border, RoundedCornerShape(6.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.secondPieceEdge),
        )
    }
}

/**
 * O nome falado da carta: "dama de espadas".
 *
 * Existe para o leitor de tela, e é o único jeito de um jogo de cartas ser jogável sem ver a
 * tela. O símbolo do naipe não serve: o TalkBack lê "♠" como "espada preta" ou não lê nada,
 * dependendo do aparelho.
 */
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
