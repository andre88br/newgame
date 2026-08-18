package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import io.github.andre88br.newgame.core.games.poker.PokerMove
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
        StacksRow(state = state, viewer = viewer, names = names, palette = palette)

        CardTable(
            seats = state.seats,
            viewer = viewer,
            names = names,
            handSize = { seat -> if (state.isIn(seat)) 2 else 0 },
            palette = palette,
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

        CardFan(cards = mao, palette = palette)
    }
}

/** As fichas de cada cadeira, numa faixa só — é o que decide se vale pagar ou desistir. */
@Composable
private fun StacksRow(state: PokerState, viewer: Seat, names: List<String>, palette: BoardPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        for (index in 0 until state.seats) {
            val seat = Seat(index)
            val foraDaMao = !state.isIn(seat)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = seatLabel(index, viewer, names),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (foraDaMao) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (seat == viewer) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    text = "${state.stack(seat)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (seat == state.turn) FontWeight.Bold else FontWeight.Normal,
                    color = if (foraDaMao) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
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
