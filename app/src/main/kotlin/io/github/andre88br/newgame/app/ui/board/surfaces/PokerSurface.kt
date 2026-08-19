package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.cards.sortedForHand
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.poker.PokerGame
import io.github.andre88br.newgame.core.games.poker.PokerHandResult
import io.github.andre88br.newgame.core.games.poker.PokerMove
import io.github.andre88br.newgame.core.games.poker.PokerPotShare
import io.github.andre88br.newgame.core.games.poker.PokerState
import io.github.andre88br.newgame.core.games.poker.PokerStreet

/**
 * A mesa do pôquer.
 *
 * O que muda a cada lance aqui não são só as cartas: são as fichas. Por isso o pote e a pilha
 * de cada cadeira ficam sempre à vista, e as próprias cartas comunitárias entram no centro da
 * mesa em vez de disputar espaço com o placar — quem está decidindo pagar ou desistir está
 * olhando para o pote, não para o desenho da carta.
 *
 * Os botões de aumento não são um campo de texto: o motor já decide quais valores fazem
 * sentido agora (o mínimo, um tamanho de pote e o all-in, quando cabem entre o mínimo e o
 * all-in) e a tela só lista o que [PokerGame.legalMoves] devolve — o mesmo padrão usado nos
 * jogos de baixar carta, onde a tela nunca inventa um lance que o motor não ofereceu.
 */
@Composable
fun PokerSurface(
    state: PokerState,
    viewer: Seat,
    /** O nome de cada cadeira. Vazia numa partida salva antes de existirem nomes. */
    names: List<String>,
    enabled: Boolean,
    hinted: Move?,
    modifier: Modifier = Modifier,
    onMove: (Move) -> Unit,
) {
    val palette = LocalBoardPalette.current
    val minhaVez = state.turn == viewer
    val legais = remember(state, viewer) {
        if (minhaVez) PokerGame.legalMoves(state) else emptyList()
    }
    // Fichas zeradas e mão vazia é o único jeito de saber, olhando o estado, que esta cadeira
    // não volta a ser servida: enquanto a partida dura, quem só desistiu desta mão continua
    // com cartas guardadas para a próxima.
    val eliminado = state.stack(viewer) == 0 && state.hand(viewer).isEmpty()
    val mao = remember(state, viewer) { state.hand(viewer).sortedForHand() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CardTable(
            seats = state.seats,
            viewer = viewer,
            names = names,
            // Sempre duas cartas para quem segue no torneio, mesmo tendo desistido desta
            // mão — só quem já foi eliminado (sem ficha e fora da mão) fica sem nenhuma. Um
            // tamanho que mudasse a cada desistência faria a mesa inteira pular de lugar a
            // cada rodada.
            handSize = { seat -> if (state.isAlive(seat)) 2 else 0 },
            palette = palette,
            // As fichas de cada adversário aparecem junto do nome dela, embaixo da própria
            // mão — não numa faixa à parte lá em cima, onde ficariam longe das cartas que
            // decidem se vale a pena pagar aquela aposta.
            seatExtra = { seat -> ChipStack(amount = state.stack(seat), destaque = seat == state.turn) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            TableArea(state = state, palette = palette)
        }

        Text(
            text = when {
                eliminado -> stringResource(R.string.poker_eliminated)
                !state.isIn(viewer) -> stringResource(R.string.poker_folded_this_hand)
                minhaVez -> stringResource(R.string.poker_your_turn)
                else -> stringResource(R.string.poker_waiting_for, seatLabel(state.turn.index, viewer, names))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A própria pilha fica ao lado da própria mão, pelo mesmo motivo da de cada
            // adversário: é olhando as fichas que se decide pagar ou desistir.
            ChipStack(amount = state.stack(viewer), destaque = minhaVez)
            CardFan(cards = mao, palette = palette)

            // O resultado da mão anterior fica ao lado da própria mão, não lá em cima perto
            // da mesa — é ali que os olhos já estão quando a mão termina e a próxima começa.
            // O peso evita que uma frase longa empurre a mão para fora da tela.
            state.lastResult?.let { resultado ->
                Text(
                    text = lastResultText(resultado, viewer, names),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Os botões de ação ficam embaixo da própria mão: é nela que se olha para decidir o
        // lance, não antes de vê-la.
        if (!eliminado) {
            Actions(
                state = state,
                viewer = viewer,
                legais = legais,
                enabled = enabled && minhaVez,
                hinted = hinted,
                onMove = onMove,
            )
        }
    }
}

/** Uma cor de ficha física — cara de cima e o texto do valor, como as fichas de uma maleta de verdade. */
private data class ChipColor(val face: Color, val text: Color)

// As cinco cores de uma maleta de fichas física, da mais baixa à mais alta — não seguem o
// tema do app de propósito, porque uma ficha de pôquer não muda de cor com o modo claro/escuro.
private val CHIP_5 = ChipColor(face = Color(0xFFF2ECD9), text = Color(0xFF1B2A4A))
private val CHIP_10 = ChipColor(face = Color(0xFF8C1F2F), text = Color.White)
private val CHIP_20 = ChipColor(face = Color(0xFF1E5631), text = Color.White)
private val CHIP_50 = ChipColor(face = Color(0xFF17406B), text = Color.White)
private val CHIP_100 = ChipColor(face = Color(0xFF1B1B1B), text = Color.White)

/** A cor da ficha muda com a grandeza do valor — é só uma faixa, não uma troca exata por fichas. */
private fun chipColorFor(amount: Int): ChipColor = when {
    amount < 20 -> CHIP_5
    amount < 100 -> CHIP_10
    amount < 500 -> CHIP_20
    amount < 2000 -> CHIP_50
    else -> CHIP_100
}

private val CHIP_SIZE = 30.dp
private val CHIP_STACK_STEP = 5.dp

/** Quantas fichas a pilha sempre mostra, tenha o valor uma ou quatro casas. */
private const val CHIP_STACK_LAYERS = 4

/**
 * Uma pilha de fichas, com o valor desenhado na ficha de cima — como numa maleta física.
 *
 * O tamanho da pilha **nunca muda**: são sempre as mesmas [CHIP_STACK_LAYERS] fichas, o zero
 * incluído (só a cor da ficha de cima muda com a grandeza do valor). Uma pilha que crescesse
 * ou encolhesse a cada aposta faria a linha inteira de jogadores pular de lugar a cada lance —
 * é por isso que aqui o tamanho já nasce reservado, pronto para qualquer valor.
 */
@Composable
private fun ChipStack(amount: Int, modifier: Modifier = Modifier, destaque: Boolean = false) {
    val cor = chipColorFor(amount)
    Box(
        modifier = modifier.size(width = CHIP_SIZE, height = CHIP_SIZE + CHIP_STACK_STEP * (CHIP_STACK_LAYERS - 1)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (amount <= 0) return@Box
        for (i in 0 until CHIP_STACK_LAYERS) {
            val ehTopo = i == CHIP_STACK_LAYERS - 1
            Box(
                modifier = Modifier
                    .offset(y = -CHIP_STACK_STEP * i)
                    .size(CHIP_SIZE)
                    .clip(CircleShape)
                    .background(cor.face)
                    .border(
                        width = if (ehTopo && destaque) 2.5.dp else 1.5.dp,
                        color = if (ehTopo && destaque) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (ehTopo) {
                    Text(
                        text = "$amount",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = cor.text,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * "Fulano venceu 40 fichas", ou "Fulano, Sicrano dividiram 40 fichas" num empate.
 *
 * Quando a mão fechou com pote lateral (alguém foi all-in por menos do que os outros
 * cobriram), [PokerHandResult.pots] tem mais de um item — um por camada — e cada um vira sua
 * própria frase, juntas nesta linha.
 */
@Composable
private fun lastResultText(resultado: PokerHandResult, viewer: Seat, names: List<String>): String {
    val partes = resultado.pots.map { pote -> potShareText(pote, viewer, names) }
    return partes.joinToString(" • ")
}

@Composable
private fun potShareText(pote: PokerPotShare, viewer: Seat, names: List<String>): String {
    val nomes = pote.winners.map { seatLabel(it, viewer, names) }
    return if (nomes.size == 1) {
        stringResource(R.string.poker_last_hand_won, nomes.first(), pote.amount)
    } else {
        stringResource(R.string.poker_last_hand_split, nomes.joinToString(", "), pote.amount)
    }
}

/** O centro da mesa: a rua, o pote e as cartas comunitárias já reveladas. */
@Composable
private fun TableArea(state: PokerState, palette: BoardPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.darkSquare)
            .heightIn(min = CARD_HEIGHT + 48.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipStack(amount = state.pot)
            Text(
                text = stringResource(R.string.poker_pot, state.pot),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = streetLabel(state.street),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.board.isEmpty()) {
                Text(
                    text = stringResource(R.string.poker_board_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (carta in state.board) {
                        CardFace(card = carta, palette = palette)
                    }
                }
            }
        }
    }
}

@Composable
private fun streetLabel(street: PokerStreet): String = stringResource(
    when (street) {
        PokerStreet.PREFLOP -> R.string.poker_street_preflop
        PokerStreet.FLOP -> R.string.poker_street_flop
        PokerStreet.TURN -> R.string.poker_street_turn
        PokerStreet.RIVER -> R.string.poker_street_river
    },
)

/** Desistir, passar ou pagar, e os aumentos que o motor considerou fazer sentido agora. */
@Composable
private fun Actions(
    state: PokerState,
    viewer: Seat,
    legais: List<Move>,
    enabled: Boolean,
    hinted: Move?,
    onMove: (Move) -> Unit,
) {
    if (legais.isEmpty()) return

    val podeDesistir = PokerMove.Fold in legais
    val podePassar = PokerMove.Check in legais
    val podePagar = PokerMove.Call in legais
    val aumentos = legais.filterIsInstance<PokerMove.Raise>().sortedBy { it.to }
    val allInAte = state.streetBet.getOrElse(viewer.index) { 0 } + state.stack(viewer)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (podeDesistir) {
                OutlinedButton(
                    onClick = { onMove(PokerMove.Fold) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.poker_fold))
                }
            }
            if (podePassar) {
                ActionButton(
                    label = stringResource(R.string.poker_check),
                    destacado = hinted == PokerMove.Check,
                    enabled = enabled,
                    onClick = { onMove(PokerMove.Check) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (podePagar) {
                ActionButton(
                    label = stringResource(R.string.poker_call, state.toCall(viewer)),
                    destacado = hinted == PokerMove.Call,
                    enabled = enabled,
                    onClick = { onMove(PokerMove.Call) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (aumentos.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (aumento in aumentos) {
                    val ehAllIn = aumento.to == allInAte
                    ActionButton(
                        label = if (ehAllIn) {
                            stringResource(R.string.poker_all_in, aumento.to)
                        } else {
                            stringResource(R.string.poker_raise_to, aumento.to)
                        },
                        destacado = hinted == aumento,
                        enabled = enabled,
                        onClick = { onMove(aumento) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Um botão de ação — preenchido quando é a dica, contornado nos outros casos. */
@Composable
private fun ActionButton(
    label: String,
    destacado: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destacado) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    }
}
