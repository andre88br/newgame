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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.DeckColorChoice
import io.github.andre88br.newgame.app.ui.theme.LocalDeckColor
import io.github.andre88br.newgame.app.ui.theme.Palette
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit

/** Tamanho de uma carta na mão. Cabe sete numa tela de celular sem virar tira ilegível. */
val CARD_WIDTH = 46.dp
val CARD_HEIGHT = 66.dp

/**
 * Quanto de cada carta aparece quando a mão está em leque.
 *
 * É o suficiente para o canto — valor e naipe — e mais nada, que é exatamente o que se vê
 * numa mão segurada de verdade. Treze cartas assim ocupam pouco mais de um terço da largura
 * que ocupariam lado a lado, e é o que faz uma mão de copas caber na tela inteira sem
 * rolagem: quem joga precisa ver a mão toda de uma vez para decidir.
 */
val CARD_FAN_STEP = 18.dp

/** Quanto a carta destacada sobe acima do leque, como quem puxa uma carta para fora. */
private val CARD_FAN_LIFT = 10.dp

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
 * Uma mão em leque: cada carta por cima da anterior, mostrando só o canto das de baixo.
 *
 * É como se segura uma mão de cartas, e não é só enfeite: lado a lado, treze cartas não
 * cabem na largura de um celular e precisariam de rolagem — e uma mão que só se vê aos
 * pedaços não dá para avaliar. Em leque a mão inteira aparece de uma vez.
 *
 * A ordem de desenho é a ordem da lista: a última carta fica por cima. Como cada uma começa
 * [CARD_FAN_STEP] à direita da anterior, a faixa visível de cada carta não é coberta por
 * ninguém — e o toque cai na carta certa sem precisar de conta nenhuma.
 */
@Composable
fun CardFan(
    cards: List<Card>,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    /**
     * Quais cartas saem do leque, puxadas para fora — a sugerida pela dica, ou as escolhidas
     * para baixar.
     *
     * Vem por **posição**, e não por carta: a canastra joga com dois baralhos, e uma mão
     * com dois reis de paus iguais teria as duas escolhidas de uma vez se a conta fosse pelo
     * valor da carta.
     */
    isRaised: (Int, Card) -> Boolean = { _, _ -> false },
    /** Quais cartas a regra deixa jogar agora; as outras aparecem apagadas. */
    isPlayable: (Int, Card) -> Boolean = { _, _ -> true },
    onClick: ((Int, Card) -> Unit)? = null,
) {
    if (cards.isEmpty()) return

    Layout(
        modifier = modifier,
        content = {
            cards.forEachIndexed { index, carta ->
                CardFace(
                    card = carta,
                    palette = palette,
                    selected = isRaised(index, carta),
                    playable = isPlayable(index, carta),
                    onClick = onClick?.let { acao -> { acao(index, carta) } },
                )
            }
        },
    ) { measurables, constraints ->
        val soltos = constraints.copy(minWidth = 0, minHeight = 0)
        val postas = measurables.map { it.measure(soltos) }
        val passo = CARD_FAN_STEP.roundToPx()
        val alto = CARD_FAN_LIFT.roundToPx()

        val largura = passo * (postas.size - 1) + postas.last().width
        val altura = postas.maxOf { it.height } + alto

        layout(largura, altura) {
            postas.forEachIndexed { index, posta ->
                // A puxada encosta no topo; as outras descem, e é essa diferença que a faz
                // parecer tirada da mão.
                val puxada = isRaised(index, cards[index])
                posta.placeRelative(x = passo * index, y = if (puxada) 0 else alto)
            }
        }
    }
}

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
 *
 * [width] e [height] têm o tamanho de uma carta normal como padrão, mas quem desenha a mesa
 * inteira — com os adversários sentados ao redor — passa um tamanho menor: o que importa ali
 * é quantas cartas há, não lê-las, e um baralho de verdade visto de longe também encolhe.
 */
@Composable
fun FaceDownCard(
    palette: BoardPalette,
    modifier: Modifier = Modifier,
    width: Dp = CARD_WIDTH,
    height: Dp = CARD_HEIGHT,
) {
    // CLÁSSICA segue a paleta do tabuleiro (varia com claro/escuro, como sempre foi); as
    // outras são cores fixas de baralho, do jeito que um baralho físico realmente é.
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
