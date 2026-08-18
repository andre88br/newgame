package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.cards.sortedForHand
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.canastra.CANASTRA_TARGET
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
    
    val currentHand = state.hand(viewer)
    var customOrder by remember { mutableStateOf<List<Card>>(emptyList()) }
    var escolhidas by remember(state) { mutableStateOf(emptySet<Int>()) }

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
    val isGameOver = state.scores.any { it >= CANASTRA_TARGET }

    val meuTime = state.teamOf(viewer)
    val minhaVez = state.turn == viewer
    val cartasEscolhidas = escolhidas.sorted().mapNotNull { displayHand.getOrNull(it) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                hasSelectedCards = cartasEscolhidas.isNotEmpty(),
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
                    hasSelectedCards = cartasEscolhidas.isNotEmpty(),
                    onMeldClick = null,
                )
            }

            Text(
                text = when {
                    !minhaVez -> stringResource(R.string.canastra_wait)
                    state.pendingReplacements > 0 -> "Tirou um 3 Vermelho! Compre uma carta de reposição."
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

            // CORREÇÃO: A carta só ganha destaque se for a SUA VEZ de jogar.
            val drawnCardIndex = if (minhaVez) displayHand.lastIndexOf(state.drawnCard) else -1
            val owedCardIndex = if (minhaVez) displayHand.lastIndexOf(state.owedCard) else -1

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                CardFan(
                    cards = displayHand,
                    palette = palette,
                    isRaised = { index, _ -> 
                        index in escolhidas || index == drawnCardIndex || index == owedCardIndex 
                    },
                    onClick = if (enabled && minhaVez && state.phase == CanastraPhase.PLAY) {
                        { index, _ ->
                            escolhidas = if (index in escolhidas) escolhidas - index else escolhidas + index
                        }
                    } else {
                        null
                    }
                )
            }

            if (enabled && minhaVez && state.phase == CanastraPhase.PLAY) {
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
                                    newEscolhidas.add(i)
                                }
                            }
                            customOrder = list
                            escolhidas = newEscolhidas
                        },
                        enabled = escolhidas.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("◀")
                    }

                    OutlinedButton(
                        onClick = {
                            customOrder = emptyList()
                            escolhidas = emptySet()
                        },
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text("✨ Ordenar")
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
                                    newEscolhidas.add(i)
                                }
                            }
                            customOrder = list
                            escolhidas = newEscolhidas
                        },
                        enabled = escolhidas.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("▶")
                    }
                }
            }
        }

        // Camada de Diálogos e Fim de Jogo
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
        } else if (isGameOver) {
            EpicVictoryOverlay(state = state, viewer = viewer, names = names)
        }
    }
}

/** TELA ÉPICA DE FIM DE JOGO COM PÓDIO */
@Composable
private fun EpicVictoryOverlay(state: CanastraState, viewer: Seat, names: List<String>) {
    val maxScore = state.scores.maxOrNull() ?: 0
    val winnerTeam = state.scores.indexOf(maxScore)
    val isMe = state.teamOf(viewer) == winnerTeam

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { /* Consome toques para bloquear o jogo no fundo */ },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = if (isMe) "👑 VITÓRIA ÉPICA! 👑" else "FIM DE JOGO",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isMe) Color(0xFFFFD700) else Color.White,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "A pontuação final foi definida!",
                color = Color.LightGray,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Desenha o Pódio
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (time in 0 until state.teams) {
                    val isWinner = time == winnerTeam
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = teamLabel(state, time, viewer, names).take(12),
                            color = if (isWinner) Color(0xFFFFD700) else Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${state.scores.getOrElse(time) { 0 }}",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = if (isWinner) 150.dp else 100.dp)
                                .background(
                                    if (isWinner) Color(0xFFFFD700).copy(alpha = 0.9f)
                                    else Color.DarkGray.copy(alpha = 0.8f),
                                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                ),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = if (isWinner) "1º" else "2º", 
                                fontSize = if (isWinner) 48.sp else 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
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
private fun CompactCanastra(jogo: Meld, palette: BoardPalette) {
    val baseCard = jogo.naturals.firstOrNull() ?: jogo.cards.first()
    val isClean = jogo.isClean

    val badgeColor = if (isClean) palette.crown else palette.hint

    Box(
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        CardFace(card = baseCard, palette = palette, modifier = Modifier.offset(x = 6.dp, y = 6.dp))
        CardFace(card = baseCard, palette = palette, modifier = Modifier.offset(x = 3.dp, y = 3.dp))
        CardFace(card = baseCard, palette = palette)

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(percent = 50))
                .border(1.5.dp, badgeColor, RoundedCornerShape(percent = 50))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "👑",
                fontSize = 12.sp,
                modifier = Modifier.alpha(if (isClean) 1f else 0.4f)
            )
        }
    }
}

@Composable
private fun MeldRow(
    title: String,
    melds: List<Meld>,
    palette: BoardPalette,
    hasSelectedCards: Boolean,
    onMeldClick: ((Int) -> Unit)?,
) {
    var expandedMelds by remember { mutableStateOf<Set<Int>>(emptySet()) }

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
                val isExpanded = expandedMelds.contains(index)
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (jogo.isCanastra) 2.dp else 0.dp,
                            color = if (jogo.isClean) palette.crown else palette.hint,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .clickable {
                            if (hasSelectedCards && onMeldClick != null) {
                                onMeldClick(index) 
                            } else if (jogo.isCanastra) {
                                expandedMelds = if (isExpanded) expandedMelds - index else expandedMelds + index
                            }
                        }
                        .padding(2.dp),
                ) {
                    if (jogo.isCanastra && !isExpanded) {
                        CompactCanastra(jogo = jogo, palette = palette)
                    } else {
                        CardFan(cards = jogo.cards, palette = palette)
                    }
                    
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
