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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.cards.Card
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

/**
 * A mesa da canastra.
 *
 * É a tela mais cheia dos quatro jogos de carta, e por um motivo de regra: a vez tem três
 * tempos — comprar, baixar, descartar —, e cada um pede uma coisa diferente da pessoa. Em
 * vez de espalhar isso, a tela mostra sempre os mesmos blocos e só troca os botões de ação
 * conforme o tempo em que a vez está.
 *
 * Baixar exige escolher cartas antes de agir, e é a única tela do app em que um toque não é
 * um lance. A escolha é por posição na mão, e não por carta: com dois baralhos, dois reis de
 * paus iguais seriam escolhidos juntos se a conta fosse pelo valor.
 *
 * Tocar num jogo já baixado com **uma** carta escolhida pode ser duas coisas diferentes, e a
 * tela não pergunta qual: se aquela carta é a que o curinga do jogo está representando, é
 * troca de curinga; senão, é extensão comum. As duas nunca se confundem — o valor que o
 * curinga faz de conta que é está sempre dentro do jogo, nunca numa ponta livre.
 */
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
    val mao = remember(state, viewer) { state.hand(viewer).sortedForHand() }
    // Estado novo é vez nova (ou mão nova): o que estava escolhido perdeu o sentido, e o
    // `remember(state)` zera a escolha sozinho.
    var escolhidas by remember(state) { mutableStateOf(emptySet<Int>()) }

    val meuTime = state.teamOf(viewer)
    val minhaVez = state.turn == viewer
    val cartasEscolhidas = escolhidas.sorted().mapNotNull { mao.getOrNull(it) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Scoreboard(state = state, viewer = viewer, names = names)

        // Os adversários sentados ao redor, com o monte, o lixo e o morto no meio.
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

        // Os jogos da própria dupla vêm primeiro e são tocáveis: tocar num deles acrescenta
        // as cartas escolhidas. Os do adversário aparecem só para serem vistos — mas
        // aparecem, porque saber o que o outro lado já fez é metade da decisão.
        val meusJogos = state.melds.getOrElse(meuTime) { emptyList() }
        MeldRow(
            title = stringResource(R.string.canastra_your_melds),
            melds = meusJogos,
            palette = palette,
            onMeldClick = if (enabled && minhaVez && cartasEscolhidas.isNotEmpty()) {
                { index ->
                    // Uma carta só, e é justo a que o curinga daquele jogo está fazendo de
                    // conta que é: então é troca, e não extensão comum. Nunca é as duas
                    // coisas ao mesmo tempo — o valor que o curinga representa está sempre
                    // dentro do jogo, nunca numa ponta livre.
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
                state.owedCard != null -> stringResource(R.string.canastra_owed_card_prompt, cardName(state.owedCard!!))
                state.phase == CanastraPhase.DRAW -> stringResource(R.string.canastra_draw_prompt)
                else -> stringResource(R.string.canastra_play_prompt)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (state.owedCard != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Actions(
            state = state,
            enabled = enabled && minhaVez,
            escolhidas = cartasEscolhidas,
            onMove = onMove,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            CardFan(
                cards = mao,
                palette = palette,
                // A carta escolhida sai do leque: é o único retorno de que ela entrou na
                // conta, já que ela continua na mão até o lance acontecer.
                isRaised = { index, carta -> index in escolhidas || carta == state.owedCard },
                onClick = if (enabled && minhaVez && state.phase == CanastraPhase.PLAY) {
                    { index, _ ->
                        escolhidas = if (index in escolhidas) escolhidas - index else escolhidas + index
                    }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * O placar por dupla, com o que decide a mão além dos pontos.
 *
 * Três vermelho e morto aparecem aqui porque não são enfeite de contagem: o vermelho só vale
 * alguma coisa se a dupla tem canastra, e sem morto pego ninguém bate. Quem olha o placar
 * precisa saber as duas coisas para decidir se corre para bater ou se segura.
 */
@Composable
private fun Scoreboard(state: CanastraState, viewer: Seat, names: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (time in 0 until state.teams) {
            val meu = time == state.teamOf(viewer)
            val canastras = state.melds.getOrElse(time) { emptyList() }.count { it.isCanastra }
            Text(
                text = stringResource(
                    R.string.canastra_team_line,
                    teamLabel(state, time, viewer, names),
                    state.scores.getOrElse(time) { 0 },
                    canastras,
                    state.redThrees.getOrElse(time) { 0 },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (meu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * O nome de um time: o nome da pessoa, para time de uma cadeira só (a mesa de 2 e a de 3
 * nunca têm dupla de verdade); "Fulano e Sicrano", para o time de duas cadeiras que só existe
 * na mesa de 4. Nunca "Dupla N" — esse número não significa nada para quem está jogando.
 */
@Composable
private fun teamLabel(state: CanastraState, time: Int, viewer: Seat, names: List<String>): String {
    val cadeiras = (0 until state.seats).filter { state.teamOf(Seat(it)) == time }
    val nomes = cadeiras.map { seatLabel(it, viewer, names) }
    return if (nomes.size == 2) stringResource(R.string.canastra_team_names, nomes[0], nomes[1]) else nomes.first()
}

/** Monte, lixo e mortos — de onde as cartas vêm e para onde elas vão. */
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

        // Em duplas não há morto, e "Mortos: 0" seria contar uma coisa que a mesa nunca teve.
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

/** Tamanho de cada carta do montinho do morto — menor que a mão, porque aqui só interessa que ele existe. */
private val MORTO_CARD_WIDTH = 26.dp
private val MORTO_CARD_HEIGHT = 38.dp

/** Quanto uma carta do montinho desloca da anterior, por trás e para baixo. */
private val MORTO_STACK_STEP = 3.dp

/** O morto como um pequeno montinho de cartas viradas, empilhadas com leve deslocamento. */
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

/** Uma fileira de jogos baixados. Cada jogo é um leque curto, para caber. */
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
                        // A canastra ganha contorno: ela é o que decide a mão, e precisa
                        // saltar no meio dos jogos comuns.
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

/**
 * Os botões da vez, que trocam conforme o tempo em que ela está.
 *
 * Comprar e pegar o lixo só existem antes da compra; baixar e descartar, só depois. Mostrar
 * os quatro sempre deixaria metade deles inertes, e um botão que não faz nada é pior do que
 * um botão que não está lá.
 */
@Composable
private fun Actions(
    state: CanastraState,
    enabled: Boolean,
    escolhidas: List<Card>,
    onMove: (Move) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.phase == CanastraPhase.DRAW) {
            Button(
                onClick = { onMove(CanastraMove.DrawStock) },
                enabled = enabled && state.stock.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.canastra_draw_stock))
            }
            OutlinedButton(
                // Trancado ou vazio, o lance vai ao motor do mesmo jeito quando dá: o botão
                // desabilita só no que a tela sabe com certeza.
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
            // Sequência ou trinca, com trinca gated por já ter canastra: quem sabe dizer se
            // isto fecha jogo agora é o motor, não um número fixo de cartas na tela.
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
