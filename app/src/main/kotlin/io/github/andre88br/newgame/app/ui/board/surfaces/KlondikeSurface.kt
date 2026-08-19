package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.klondike.KLONDIKE_PILES
import io.github.andre88br.newgame.core.games.klondike.KlondikeMove
import io.github.andre88br.newgame.core.games.klondike.KlondikeState

/** Quanto de cada carta aparece numa coluna empilhada. Cabe o valor e o naipe do canto. */
private val PILE_STEP = 20.dp

/** Quanto do dorso aparece: menos, porque não há nada para ler nele. */
private val PILE_STEP_DOWN = 8.dp

/** O que está esperando para se mover. */
private sealed interface Pegada {
    data object Descarte : Pegada
    data class Coluna(val pile: Int, val count: Int) : Pegada
}

/**
 * A mesa da paciência.
 *
 * O jogo é de arrastar carta, e a tela não arrasta: toca-se na carta e depois no destino. É
 * uma escolha, e a favor de quem joga — arrastar num celular exige acertar um alvo de meio
 * dedo com o dedo em cima dele, tapando justamente o que se precisa ver. Em dois toques a
 * carta escolhida fica marcada, dá para mudar de ideia, e o destino continua visível.
 *
 * Tocar numa carta do meio de uma coluna pega ela e tudo o que está por cima: numa paciência
 * a sequência anda junto, e obrigar a escolher quantas cartas levar seria perguntar o que a
 * regra já responde.
 */
@Composable
fun KlondikeSurface(
    state: KlondikeState,
    viewer: Seat,
    names: List<String>,
    enabled: Boolean,
    hinted: Move?,
    /** Segue o ajuste de animações das Configurações: desligado, o descarte já pousa pronto. */
    animated: Boolean = true,
    modifier: Modifier = Modifier,
    onMove: (Move) -> Unit,
) {
    val palette = LocalBoardPalette.current
    // Estado novo é mesa nova: a carta que estava na mão já foi jogada, ou o lance foi
    // recusado e insistir nela não vai adiantar.
    var pegada by remember(state) { mutableStateOf<Pegada?>(null) }

    /** Solta a carta escolhida no destino, ou escolhe outra se nada estava escolhido. */
    fun soltarEm(coluna: Int) {
        when (val atual = pegada) {
            null -> Unit
            Pegada.Descarte -> onMove(KlondikeMove.WasteToPile(coluna))
            is Pegada.Coluna ->
                if (atual.pile == coluna) pegada = null
                else onMove(KlondikeMove.PileToPile(atual.pile, atual.count, coluna))
        }
    }

    // A largura disponível decide o quanto a carta cresce — veja [cardScaleFor]. Numa tela
    // estreita o resultado é sempre 1 (o tamanho de sempre); numa tela deitada ou num
    // tablet, a mesa inteira — monte, fundações e colunas — cresce junto.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val scale = cardScaleFor(maxWidth)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Progress(state = state)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                StockAndWaste(
                    state = state,
                    palette = palette,
                    enabled = enabled,
                    animated = animated,
                    scale = scale,
                    escolhido = pegada == Pegada.Descarte,
                    onStock = {
                        pegada = null
                        onMove(if (state.stock.isEmpty()) KlondikeMove.Recycle else KlondikeMove.Draw)
                    },
                    onWaste = { pegada = if (pegada == Pegada.Descarte) null else Pegada.Descarte },
                )

                Foundations(
                    state = state,
                    palette = palette,
                    scale = scale,
                    onClick = if (enabled) {
                        {
                            when (val atual = pegada) {
                                null -> Unit
                                Pegada.Descarte -> onMove(KlondikeMove.WasteToFoundation)
                                is Pegada.Coluna -> onMove(KlondikeMove.PileToFoundation(atual.pile))
                            }
                        }
                    } else {
                        null
                    },
                )
            }

            Text(
                text = when {
                    !enabled -> stringResource(R.string.klondike_wait)
                    pegada == null -> stringResource(R.string.klondike_pick_prompt)
                    else -> stringResource(R.string.klondike_drop_prompt)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // A casa aceita uma carta de cada vez, e por isso ganha botão próprio: mirar na
            // pilha certa entre quatro pilhas pequenas é o toque mais fácil de errar da tela.
            Button(
                onClick = {
                    when (val atual = pegada) {
                        null -> Unit
                        Pegada.Descarte -> onMove(KlondikeMove.WasteToFoundation)
                        is Pegada.Coluna -> onMove(KlondikeMove.PileToFoundation(atual.pile))
                    }
                },
                enabled = enabled && pegada != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.klondike_to_foundation))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (coluna in 0 until KLONDIKE_PILES) {
                    PileColumn(
                        downs = state.downs[coluna].size,
                        ups = state.ups[coluna],
                        palette = palette,
                        scale = scale,
                        escolhidas = (pegada as? Pegada.Coluna)
                            ?.takeIf { it.pile == coluna }
                            ?.count
                            ?: 0,
                        sugerida = sugeridaNaColuna(hinted, coluna),
                        onEmpty = if (enabled) {
                            { soltarEm(coluna) }
                        } else {
                            null
                        },
                        onCard = if (enabled) {
                            { indice ->
                                if (pegada == null) {
                                    // Da carta tocada para cima: a sequência anda junto.
                                    pegada = Pegada.Coluna(coluna, state.ups[coluna].size - indice)
                                } else {
                                    soltarEm(coluna)
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** A dica, quando ela aponta para esta coluna: qual carta ela moveria. */
private fun sugeridaNaColuna(hinted: Move?, pile: Int): Int? = when (hinted) {
    is KlondikeMove.PileToPile -> if (hinted.from == pile) hinted.count else null
    is KlondikeMove.PileToFoundation -> if (hinted.pile == pile) 1 else null
    else -> null
}

/** Quantas cartas já subiram, e quantas voltas o monte já deu. */
@Composable
private fun Progress(state: KlondikeState) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.klondike_placed, state.placed, 52),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.klondike_redeals, state.redeals),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * O monte e o descarte, lado a lado.
 *
 * Monte vazio não some: vira o lugar de tocar para o descarte voltar. Uma casa vazia onde
 * antes havia cartas é a única pista de que ainda dá para girar o baralho de novo.
 */
@Composable
private fun StockAndWaste(
    state: KlondikeState,
    palette: BoardPalette,
    enabled: Boolean,
    animated: Boolean,
    scale: Float,
    escolhido: Boolean,
    onStock: () -> Unit,
    onWaste: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.stock.isEmpty()) {
                EmptySlot(
                    palette = palette,
                    label = "↻",
                    scale = scale,
                    onClick = if (enabled && state.waste.isNotEmpty()) onStock else null,
                )
            } else {
                FaceDownCard(
                    palette = palette,
                    width = CARD_WIDTH * scale,
                    height = CARD_HEIGHT * scale,
                    modifier = if (enabled) Modifier.clickable { onStock() } else Modifier,
                )
            }
            Text(
                text = state.stock.size.toString(),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val topo = state.wasteTop
            if (topo == null) {
                EmptySlot(palette = palette, label = "", scale = scale, onClick = null)
            } else {
                // A carta que acabou de virar pousa uma vez: a chave é o tamanho do
                // descarte, não a carta em si — duas cartas de mesmo valor em jogadas
                // seguidas (depois de um recycle, por exemplo) não podem ser confundidas
                // com "a mesma carta parada".
                key(state.waste.size) {
                    CardFace(
                        card = topo,
                        palette = palette,
                        selected = escolhido,
                        scale = scale,
                        onClick = if (enabled) onWaste else null,
                        modifier = rememberLandAnimation(animated),
                    )
                }
            }
            Text(
                text = state.waste.size.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** As quatro casas, uma por naipe, sempre nos mesmos lugares. */
@Composable
private fun Foundations(state: KlondikeState, palette: BoardPalette, scale: Float, onClick: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (naipe in Suit.entries) {
            val topo = state.foundationOf(naipe).lastOrNull()
            if (topo == null) {
                // A casa vazia mostra o naipe dela: quem olha sabe onde o ás vai cair antes
                // mesmo de ter o ás.
                EmptySlot(palette = palette, label = naipe.symbol, scale = scale, onClick = onClick)
            } else {
                CardFace(card = topo, palette = palette, scale = scale, onClick = onClick)
            }
        }
    }
}

/** Um lugar de carta sem carta: contorno tracejado não existe aqui, então é contorno fino. */
@Composable
private fun EmptySlot(palette: BoardPalette, label: String, scale: Float, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .size(CARD_WIDTH * scale, CARD_HEIGHT * scale)
            .clip(RoundedCornerShape(6.dp))
            // O verde da mesa: casa vazia é o pano, e não um buraco na tela.
            .background(palette.darkSquare)
            .border(1.dp, palette.border, RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Uma coluna: os dorsos embaixo, as cartas abertas em cima, sobrepostas.
 *
 * O dorso aparece menos do que a carta aberta porque não há nada para ler nele — e o que se
 * ganha em altura é o que faz uma coluna de treze cartas caber na tela.
 */
@Composable
private fun PileColumn(
    downs: Int,
    ups: List<Card>,
    palette: BoardPalette,
    scale: Float,
    /** Quantas cartas do fim da coluna estão escolhidas. Zero quando nenhuma está. */
    escolhidas: Int,
    /** Quantas cartas do fim da coluna a dica aponta, ou `null`. */
    sugerida: Int?,
    onEmpty: (() -> Unit)?,
    onCard: ((Int) -> Unit)?,
) {
    if (downs == 0 && ups.isEmpty()) {
        EmptySlot(palette = palette, label = "", scale = scale, onClick = onEmpty)
        return
    }

    Layout(
        content = {
            repeat(downs) { FaceDownCard(palette = palette, width = CARD_WIDTH * scale, height = CARD_HEIGHT * scale) }
            ups.forEachIndexed { index, carta ->
                val doFim = ups.size - index
                CardFace(
                    card = carta,
                    palette = palette,
                    selected = doFim <= escolhidas,
                    hinted = sugerida != null && doFim <= sugerida,
                    scale = scale,
                    onClick = onCard?.let { acao -> { acao(index) } },
                )
            }
        },
    ) { measurables, constraints ->
        val soltos = constraints.copy(minWidth = 0, minHeight = 0)
        val postas = measurables.map { it.measure(soltos) }
        val passoDorso = (PILE_STEP_DOWN * scale).roundToPx()
        val passoCarta = (PILE_STEP * scale).roundToPx()

        // A última carta aparece inteira; as de baixo, só a faixa do passo.
        val altura = passoDorso * downs +
            passoCarta * (ups.size - 1).coerceAtLeast(0) +
            (postas.lastOrNull()?.height ?: 0)
        val largura = postas.maxOfOrNull { it.width } ?: 0

        layout(largura, altura) {
            var y = 0
            postas.forEachIndexed { index, posta ->
                posta.placeRelative(x = 0, y = y)
                y += if (index < downs) passoDorso else passoCarta
            }
        }
    }
}
