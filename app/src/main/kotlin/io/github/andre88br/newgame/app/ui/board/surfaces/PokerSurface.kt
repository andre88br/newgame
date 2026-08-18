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
import androidx.compose.ui.unit.dp
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
            handSize = { seat -> if (state.isIn(seat)) 2 else 0 },
            palette = palette,
            // As fichas de cada adversário aparecem junto do nome dela, embaixo da própria
            // mão — não numa faixa à parte lá em cima, onde ficariam longe das cartas que
            // decidem se vale a pena pagar aquela aposta.
            seatExtra = { seat -> PlayerChips(amount = state.stack(seat), destaque = seat == state.turn) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            TableArea(state = state, palette = palette)
        }

        state.lastResult?.let { resultado ->
            Text(
                text = lastResultText(resultado, viewer, names),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A própria pilha fica ao lado da própria mão, pelo mesmo motivo da de cada
            // adversário: é olhando as fichas que se decide pagar ou desistir.
            PlayerChips(amount = state.stack(viewer), destaque = minhaVez)
            CardFan(cards = mao, palette = palette)
        }
    }
}

/** A pilha de fichas e o valor de uma cadeira — usado ao lado da mão dela, própria ou adversária. */
@Composable
private fun PlayerChips(amount: Int, destaque: Boolean, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        ChipStack(amount = amount)
        Text(
            text = "$amount",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (destaque) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Cores fixas de ficha de pôquer, como vêm numa maleta física — não seguem o tema do app. */
private val CHIP_COLORS = listOf(Color(0xFFC62828), Color(0xFF2E7D32), Color(0xFF1565C0))
private val CHIP_SIZE = 20.dp
private val CHIP_STACK_STEP = 4.dp

/**
 * Uma pilha de fichas.
 *
 * Não é enfeite: é o que faz "quantas fichas" parecer dinheiro em jogo, e não só mais um
 * número ao lado do nome. A altura da pilha é só uma faixa grosseira de grandeza (pouco,
 * médio, muito) — não uma conta exata de fichas físicas, que ninguém ia contar de olho.
 */
@Composable
private fun ChipStack(amount: Int, modifier: Modifier = Modifier) {
    val camadas = when {
        amount <= 0 -> 0
        amount < 100 -> 1
        amount < 500 -> 2
        else -> 3
    }
    if (camadas == 0) return
    Box(
        modifier = modifier.size(width = CHIP_SIZE, height = CHIP_SIZE + CHIP_STACK_STEP * (camadas - 1)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        for (i in 0 until camadas) {
            Box(
                modifier = Modifier
                    .offset(y = -CHIP_STACK_STEP * i)
                    .size(CHIP_SIZE)
                    .clip(CircleShape)
                    .background(CHIP_COLORS[i % CHIP_COLORS.size])
                    .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            )
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
