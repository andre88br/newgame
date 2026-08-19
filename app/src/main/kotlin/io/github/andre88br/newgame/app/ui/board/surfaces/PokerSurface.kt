package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
    /** Segue o ajuste de animações das Configurações: desligado, mãos e leques já nascem prontos. */
    animated: Boolean = true,
    roundJustEnded: Boolean,
    onAcknowledgeRoundEnd: () -> Unit,
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

    // Ver a nota equivalente na canastra: a mão que fecha o torneio não reparte mão nova, e
    // por isso o ViewModel não enxerga a mudança que dispararia [roundJustEnded] sozinho.
    var finalScoreDismissed by remember(state.handNumber) { mutableStateOf(false) }
    val showRoundDialog = state.lastResult != null && (roundJustEnded || (state.gameOver && !finalScoreDismissed))

    // A largura disponível decide o quanto a carta cresce — veja [cardScaleFor]. Numa tela
    // estreita o resultado é sempre 1 (o tamanho de sempre); numa tela deitada ou num
    // tablet, a mesa inteira — mão, adversários, fichas e comunitárias — cresce junto.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val scale = cardScaleFor(maxWidth)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardTable(
                seats = state.seats,
                viewer = viewer,
                names = names,
                // Sempre duas cartas para quem segue no torneio, mesmo tendo desistido
                // desta mão — só quem já foi eliminado (sem ficha e fora da mão) fica sem
                // nenhuma. Um tamanho que mudasse a cada desistência faria a mesa inteira
                // pular de lugar a cada rodada.
                handSize = { seat -> if (state.isAlive(seat)) 2 else 0 },
                palette = palette,
                // Quem foi all-in já mostra a carta virada para cima assim que
                // [PokerGame.redactFor] considera seguro revelar (a rodada em que ela foi
                // all-in fechou) — o motor já manda a carta de verdade em vez de oculta,
                // então basta reconhecer isso aqui e desenhar a face em vez do leque virado
                // para baixo de sempre.
                animated = animated,
                scale = scale,
                handContent = { seat, count, vertical ->
                    val maoAdversario = state.hand(seat)
                    if (maoAdversario.isNotEmpty() && maoAdversario.none { it.isHidden }) {
                        CardFan(cards = maoAdversario, palette = palette, animated = animated, scale = scale)
                    } else {
                        OpponentFan(count = count, palette = palette, animated = animated, scale = scale, vertical = vertical)
                    }
                },
                // As fichas de cada adversário aparecem junto do nome dela, embaixo da
                // própria mão — não numa faixa à parte lá em cima, onde ficariam longe das
                // cartas que decidem se vale a pena pagar aquela aposta.
                seatExtra = { seat -> ChipStack(amount = state.stack(seat), scale = scale, destaque = seat == state.turn) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                TableArea(state = state, palette = palette, viewer = viewer, names = names, scale = scale)
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
                ChipStack(amount = state.stack(viewer), scale = scale, destaque = minhaVez)
                CardFan(cards = mao, palette = palette, animated = animated, scale = scale)

                // O resultado da mão anterior fica ao lado da própria mão, não lá em cima
                // perto da mesa — é ali que os olhos já estão quando a mão termina e a
                // próxima começa. O peso evita que uma frase longa empurre a mão para fora
                // da tela.
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

            // Os botões de ação ficam embaixo da própria mão: é nela que se olha para
            // decidir o lance, não antes de vê-la.
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

    if (showRoundDialog) {
        val resultado = state.lastResult
        AlertDialog(
            onDismissRequest = { finalScoreDismissed = true; onAcknowledgeRoundEnd() },
            confirmButton = {
                TextButton(onClick = { finalScoreDismissed = true; onAcknowledgeRoundEnd() }) {
                    Text("Continuar")
                }
            },
            title = { Text("Fim da Mão") },
            text = {
                Text(
                    text = resultado?.let { lastResultText(it, viewer, names) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
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
private fun ChipStack(amount: Int, scale: Float = 1f, modifier: Modifier = Modifier, destaque: Boolean = false) {
    val cor = chipColorFor(amount)
    val chipSize = CHIP_SIZE * scale
    val chipStep = CHIP_STACK_STEP * scale
    Box(
        modifier = modifier.size(width = chipSize, height = chipSize + chipStep * (CHIP_STACK_LAYERS - 1)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (amount <= 0) return@Box
        for (i in 0 until CHIP_STACK_LAYERS) {
            val ehTopo = i == CHIP_STACK_LAYERS - 1
            Box(
                modifier = Modifier
                    .offset(y = -chipStep * i)
                    .size(chipSize)
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
                        fontSize = 8.sp * scale,
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

/** Quanto tempo, no mínimo, entre uma carta da mesa aparecer e a seguinte. */
private const val BOARD_CARD_REVEAL_DELAY_MS = 2_000L

/** O centro da mesa: a rua, o pote e as cartas comunitárias já reveladas. */
@Composable
private fun TableArea(state: PokerState, palette: BoardPalette, viewer: Seat, names: List<String>, scale: Float) {
    // O flop chega do motor como três cartas de uma vez só — e, num all-in, o turn e o river
    // podem chegar em sequência rápida logo atrás. Sem isto elas apareceriam todas juntas: a
    // contagem fica presa a esta mão (reseta quando [PokerState.handNumber] muda) e sobe uma
    // de cada vez, esperando ao menos [BOARD_CARD_REVEAL_DELAY_MS] entre uma carta e outra.
    var reveladas by remember(state.handNumber) { mutableStateOf(0) }
    LaunchedEffect(state.board.size) {
        while (reveladas < state.board.size) {
            if (reveladas > 0) delay(BOARD_CARD_REVEAL_DELAY_MS)
            reveladas++
        }
    }
    val cartasVisiveis = state.board.take(reveladas)

    // Só existe num all-in — veja a nota em [pokerAllInEquities] sobre por que fora dele a
    // conta nem tenta rodar. Calculada sobre [cartasVisiveis], e não sobre a mesa de verdade:
    // senão a porcentagem entregaria a próxima carta antes dela aparecer na tela.
    val equities = rememberPokerAllInEquities(state.copy(board = cartasVisiveis))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.darkSquare)
            .heightIn(min = CARD_HEIGHT * scale + 48.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChipStack(amount = state.pot, scale = scale)
            Text(
                text = stringResource(R.string.poker_pot, state.pot),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                // A rua mostrada segue o que já apareceu na mesa, não a rua de verdade do
                // motor — senão o rótulo diria "river" com só o flop à vista.
                text = streetLabel(streetForVisibleCount(cartasVisiveis.size)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (cartasVisiveis.isEmpty()) {
                Text(
                    text = stringResource(R.string.poker_board_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (carta in cartasVisiveis) {
                        CardFace(card = carta, palette = palette, scale = scale)
                    }
                }
            }
            if (equities != null) {
                EquityRow(equities = equities, viewer = viewer, names = names)
            }
        }
    }
}

/** A chance de vitória de cada cadeira ainda na mão, lado a lado — só aparece num all-in. */
@Composable
private fun EquityRow(equities: Map<Seat, Double>, viewer: Seat, names: List<String>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(R.string.poker_equity_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            equities.entries.sortedBy { it.key.index }.forEach { (seat, chance) ->
                Text(
                    text = stringResource(
                        R.string.poker_equity_percent,
                        seatLabel(seat.index, viewer, names),
                        (chance * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
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

/** A rua que corresponde a quantas cartas da mesa já apareceram na tela. */
private fun streetForVisibleCount(count: Int): PokerStreet = when {
    count >= 5 -> PokerStreet.RIVER
    count == 4 -> PokerStreet.TURN
    count >= 1 -> PokerStreet.FLOP
    else -> PokerStreet.PREFLOP
}

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
