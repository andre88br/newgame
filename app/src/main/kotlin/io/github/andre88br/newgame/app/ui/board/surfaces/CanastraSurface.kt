package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.cards.sortedForHand
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.canastra.CanastraGame
import io.github.andre88br.newgame.core.games.canastra.CanastraMove
import io.github.andre88br.newgame.core.games.canastra.CanastraPhase
import io.github.andre88br.newgame.core.games.canastra.CanastraState
import io.github.andre88br.newgame.core.games.canastra.Meld
import io.github.andre88br.newgame.core.games.canastra.mortosFor
import io.github.andre88br.newgame.core.games.canastra.wildRepresents

@Composable
fun CanastraSurface(
    state: CanastraState,
    viewer: Seat,
    names: List<String>,
    enabled: Boolean,
    hinted: Move?,
    modifier: Modifier = Modifier,
    onMove: (Move) -> Unit,
) {
    val palette = LocalBoardPalette.current
    
    // SISTEMA DE MEMÓRIA DE MÃO MANUAL
    val currentHand = state.hand(viewer)
    var customOrder by remember { mutableStateOf<List<Card>>(emptyList()) }
    var escolhidas by remember(state) { mutableStateOf(emptySet<Int>()) }

    // Reconcilia de forma inteligente a mão atual do motor com a ordem customizada que o jogador fez
    val displayHand = remember(currentHand, customOrder) {
        if (customOrder.isEmpty() && currentHand.isNotEmpty()) {
            currentHand.sortedForHand()
        } else {
            val newOrder = customOrder.toMutableList()
            val handCounts = currentHand.groupingBy { it }.eachCount().toMutableMap()
            val finalOrder = mutableListOf<Card>()
            
            for (card in newOrder) {
                val remaining = handCounts[card] ?: 0
                if (remaining > 0) {
                    finalOrder.add(card)
                    handCounts[card] = remaining - 1
                }
            }
            
            val extras = mutableListOf<Card>()
            for ((card, count) in handCounts) {
                repeat(count) { extras.add(card) }
            }
            finalOrder + extras.sortedForHand()
        }
    }

    LaunchedEffect(displayHand) {
        if (customOrder != displayHand) customOrder = displayHand
    }

    var roundScoreDismissed by remember(state.scores) { mutableStateOf(false) }

    val meuTime = state.teamOf(viewer)
    val minhaVez = state.turn == viewer
    val cartasEscolhidas = escolhidas.sorted().mapNotNull { displayHand.getOrNull(it) }

    if (state.lastScores.isNotEmpty() && !roundScoreDismissed) {
        AlertDialog(
            onDismissRequest = { roundScoreDismissed = true },
            confirmButton = {
                TextButton(onClick = { roundScoreDismissed = true }) {
                    Text("Continuar")
                }
            },
            title = { Text("Fim da Rodada") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    for (time in 0 until state.teams) {
                        val detalhes = state.lastScores.getOrNull(time)
                        if (detalhes != null) {
                            Column {
                                Text(
                                    text = teamLabel(state, time, viewer, names),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (time == meuTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text("Jogos na Mesa: +${detalhes.pontosMesa}")
                                Text("Cartas na Mão: -${detalhes.penalidadeMao}", color = MaterialTheme.colorScheme.error)
                                if (detalhes.vermelhos > 0) Text("Três Vermelhos: +${detalhes.vermelhos}")
                                if (detalhes.batida > 0) Text("Bônus de Batida/Morto: +${detalhes.batida}")
                                Text(
                                    text = "Saldo da Rodada: ${if (detalhes.totalRodada > 0) "+" else ""}${detalhes.totalRodada}",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (time < state.teams - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Scoreboard(state = state, viewer = viewer, names = names, palette = palette)

        CardTable(
            seats = state.seats,
            viewer = viewer,
            names = names,
            handSize = { seat -> state.handSize(seat) },
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TableInfo(state = state, palette = palette)
        }

        val meusJogos = state.melds.getOrElse(meuTime) { emptyList() }
        MeldRow(
            title = stringResource(R.string.canastra_your_melds),
            melds = meusJogos,
            palette = palette,
            onMeldClick = if (enabled && minhaVez && cartasEscolhidas.isNotEmpty()) {
                { index ->
                    val unica = cartasEscolhidas.singleOrNull()
                    val jogo = meusJogos.getOrNull(index)
                    if (jogo != null && unica != null && unica == wildRepresents(jogo)) {
                        onMove(CanastraMove.SwapWild(index, unica))
                    } else {
                        onMove(CanastraMove.Meld(cartasEscolhidas, into = index))
                    }
                }
            } else {
                null
            },
        )
        for (time in 0 until state.teams) {
            if (time == meuTime) continue
            MeldRow(
                title = stringResource(R.string.canastra_their_melds, teamLabel(state, time, viewer, names)),
                melds = state.melds.getOrElse(time) { emptyList() },
                palette = palette,
                onMeldClick = null,
            )
        }

        Text(
            text = when {
                !minhaVez -> stringResource(R.string.canastra_wait)
                state.pendingReplacements > 0 -> "Você tirou um 3 Vermelho! Compre uma carta de reposição."
                state.owedCard != null -> stringResource(R.string.canastra_owed_card_prompt, cardName(state.owedCard!!))
                state.phase == CanastraPhase.DRAW -> stringResource(R.string.canastra_draw_prompt)
                else -> stringResource(R.string.canastra_play_prompt)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.owedCard != null || state.pendingReplacements > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Actions(
            state = state,
            enabled = enabled && minhaVez,
            escolhidas = cartasEscolhidas,
            onMove = onMove
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            CardFan(
                cards = displayHand,
                palette = palette,
                isRaised = { index, carta -> index in escolhidas || carta == state.owedCard || carta == state.drawnCard },
                onClick = if (enabled && minhaVez && state.phase == CanastraPhase.PLAY) {
                    { index, _ ->
                        escolhidas = if (index in escolhidas) escolhidas - index else escolhidas + index
                    }
                } else {
                    null
                },
            )
        }

        // CONTROLES DE ORDENAÇÃO (Abaixo da mão)
        if (escolhidas.isNotEmpty() && enabled && minhaVez && state.phase == CanastraPhase.PLAY) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(
                    onClick = {
                        val list = displayHand.toMutableList()
                        val newEscolhidas = mutableSetOf<Int>()
                        val sortedSelected = escolhidas.sorted()
                        for (i in sortedSelected) {
                            if (i > 0 && (i - 1) !in newEscolhidas) {
                                val temp = list[i]
                                list[i] = list[i - 1]
                                list[i - 1] = temp
                                newEscolhidas.add(i - 1)
                            } else {
                                newEscolhidas.add(i) // Bateu no canto ou num bloco
                            }
                        }
                        customOrder = list
                        escolhidas = newEscolhidas
                    }
                ) {
                    Text("◀ Esquerda")
                }

                OutlinedButton(
                    onClick = {
                        val list = displayHand.toMutableList()
                        val newEscolhidas = mutableSetOf<Int>()
                        val sortedSelected = escolhidas.sortedDescending()
                        for (i in sortedSelected) {
                            if (i < list.size - 1 && (i + 1) !in newEscolhidas) {
                                val temp = list[i]
                                list[i] = list[i + 1]
                                list[i + 1] = temp
                                newEscolhidas.add(i + 1)
                            } else {
                                newEscolhidas.add(i) // Bateu no canto ou num bloco
                            }
                        }
                        customOrder = list
                        escolhidas = newEscolhidas
                    }
                ) {
                    Text("Direita ▶")
                }
            }
        }
    }
}

@Composable
private fun Scoreboard(state: CanastraState, viewer: Seat, names: List<String>, palette: BoardPalette) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (time in 0 until state.teams) {
            val meu = time == state.teamOf(viewer)
            val canastras = state.melds.getOrElse(time) { emptyList() }.count { it.isCanastra }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${teamLabel(state, time, viewer, names)}: ${state.scores.getOrElse(time) { 0 }} pts | $canastras canastras",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (meu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                
                val red3Count = state.redThrees.getOrElse(time) { 0 }
                if (red3Count > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                        repeat(red3Count) {
                            Box(modifier = Modifier.size(width = 16.dp, height = 24.dp)) {
                                CardFace(
                                    card = Card(Rank.THREE, Suit.DIAMONDS),
                                    palette = palette
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun teamLabel(state: CanastraState, time: Int, viewer: Seat, names: List<String>): String {
    val cadeiras = (0 until state.seats).filter { state.teamOf(Seat(it)) == time }
    val nomes = cadeiras.map { seatLabel(it, viewer, names) }
    return if (nomes.size == 2) stringResource(R.string.canastra_team_names, nomes[0], nomes[1]) else nomes.first()
}

@Composable
private fun TableInfo(state: CanastraState, palette: BoardPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.stock.isEmpty()) {
                Box(modifier = Modifier.padding(2.dp)) { Text("—") }
            } else {
                FaceDownCard(palette = palette)
            }
            Text(
                text = stringResource(R.string.canastra_stock, state.stock.size),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val topo = state.discardTop
            if (topo == null) {
                Box(modifier = Modifier.padding(2.dp)) { Text("—") }
            } else {
                CardFace(card = topo, palette = palette)
            }
            Text(
                text = if (state.discardBlocked) {
                    stringResource(R.string.canastra_pile_blocked, state.discard.size)
                } else {
                    stringResource(R.string.canastra_pile, state.discard.size)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (state.discardBlocked) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (mortosFor(state.seats) > 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.mortos.isNotEmpty()) {
                    MortoStack(palette = palette)
                }
                Text(
                    text = stringResource(
                        if (state.mortos.isEmpty()) {
                            R.string.canastra_morto_taken
                        } else {
                            R.string.canastra_morto_on_table
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private val MORTO_CARD_WIDTH = 26.dp
private val MORTO_CARD_HEIGHT = 38.dp
private val MORTO_STACK_STEP = 3.dp

@Composable
private fun MortoStack(palette: BoardPalette) {
    Box(
        modifier = Modifier.size(
            width = MORTO_CARD_WIDTH + MORTO_STACK_STEP * 2,
            height = MORTO_CARD_HEIGHT + MORTO_STACK_STEP * 2,
        ),
    ) {
        for (i in 0 until 3) {
            FaceDownCard(
                palette = palette,
                width = MORTO_CARD_WIDTH,
                height = MORTO_CARD_HEIGHT,
                modifier = Modifier.offset(x = MORTO_STACK_STEP * i, y = MORTO_STACK_STEP * i),
            )
        }
    }
}

@Composable
private fun MeldRow(
    title: String,
    melds: List<Meld>,
    palette: BoardPalette,
    onMeldClick: ((Int) -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (melds.isEmpty()) {
            Text(
                text = stringResource(R.string.canastra_no_melds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            melds.forEachIndexed { index, jogo ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (jogo.isCanastra) 2.dp else 0.dp,
                            color = if (jogo.isClean) palette.crown else palette.hint,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .then(
                            if (onMeldClick != null) {
                                Modifier.clickable { onMeldClick(index) }
                            } else {
                                Modifier
                            },
                        )
                        .padding(2.dp),
                ) {
                    CardFan(cards = jogo.cards, palette = palette)
                    Text(
                        text = if (jogo.isCanastra) {
                            stringResource(
                                if (jogo.isClean) R.string.canastra_clean else R.string.canastra_dirty,
                                jogo.cards.size,
                            )
                        } else {
                            stringResource(R.string.canastra_meld_size, jogo.cards.size)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Actions(
    state: CanastraState,
    enabled: Boolean,
    escolhidas: List<Card>,
    onMove: (Move) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.pendingReplacements > 0) {
            Button(
                onClick = { onMove(CanastraMove.DrawStock) },
                enabled = enabled && state.stock.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Comprar Reposição (3 Vermelho)")
            }
            return@Row
        }

        if (state.phase == CanastraPhase.DRAW) {
            if (state.stock.isNotEmpty()) {
                Button(
                    onClick = { onMove(CanastraMove.DrawStock) },
                    enabled = enabled && state.stock.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.canastra_draw_stock))
                }
            } else {
                Button(
                    onClick = { onMove(CanastraMove.Pass) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Encerrar Mão")
                }
            }
            
            OutlinedButton(
                onClick = { onMove(CanastraMove.TakeDiscard) },
                enabled = enabled && state.discard.isNotEmpty() && !state.discardBlocked,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.canastra_take_discard, state.discard.size))
            }
            return@Row
        }

        Button(
            onClick = { onMove(CanastraMove.Meld(escolhidas)) },
            enabled = enabled && CanastraGame.canMeld(state, escolhidas),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.canastra_meld, escolhidas.size))
        }
        
        OutlinedButton(
            onClick = { escolhidas.singleOrNull()?.let { onMove(CanastraMove.Discard(it)) } },
            enabled = enabled && escolhidas.size == 1,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.canastra_discard))
        }
    }
}
