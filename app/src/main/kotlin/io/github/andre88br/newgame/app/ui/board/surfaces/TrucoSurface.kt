package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.theme.BoardPalette
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.cards.sortedForHand
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.truco.TRUCO_HAND_SIZE
import io.github.andre88br.newgame.core.games.truco.TRUCO_NOBODY
import io.github.andre88br.newgame.core.games.truco.TrucoGame
import io.github.andre88br.newgame.core.games.truco.TrucoMove
import io.github.andre88br.newgame.core.games.truco.TrucoState
import io.github.andre88br.newgame.core.games.truco.nextStake

/**
 * A mesa do truco.
 *
 * O jogo tem três cartas e cabe numa tela sem rolagem, mas o que ele pede da tela não é
 * espaço: é deixar claro, o tempo todo, **quanto a mão está valendo** e **como andam as
 * rodadas**. As duas coisas decidem cada lance, e nenhuma delas está nas cartas.
 *
 * Por isso o placar vem no alto com o valor da mão ao lado, e logo abaixo três marcas — uma
 * por rodada — dizendo quem levou cada uma. Quem fez a primeira só precisa empatar a segunda,
 * e uma pessoa que não vê isso na tela joga o truco errado sem saber por quê.
 *
 * O truco na mesa vira faixa, e não botão discreto. Enquanto ele está de pé não há carta a
 * jogar: as únicas saídas são aceitar, pedir mais ou correr, e a tela mostra o preço de cada
 * uma antes de a pessoa escolher.
 */
@Composable
fun TrucoSurface(
    state: TrucoState,
    viewer: Seat,
    /** O nome de cada cadeira. Vazia numa partida salva antes de existirem nomes. */
    names: List<String>,
    enabled: Boolean,
    hinted: Move?,
    modifier: Modifier = Modifier,
    onMove: (Move) -> Unit,
) {
    val palette = LocalBoardPalette.current
    val mao = remember(state, viewer) { state.hand(viewer).sortedForHand() }
    val minhaVez = state.turn == viewer
    // Os lances vêm do motor: a tela não sabe quando se pode trucar, e duplicar essa conta
    // aqui seria a forma mais rápida de a tela e o jogo discordarem.
    val legais = remember(state, viewer) {
        if (minhaVez) TrucoGame.legalMoves(state) else emptyList()
    }
    val sugerida = (hinted as? TrucoMove.Play)?.card

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Scoreboard(state = state, viewer = viewer)

        RoundMarkers(state = state, viewer = viewer, palette = palette)

        // Os adversários sentados ao redor, com a rodada corrente no meio.
        CardTable(
            seats = state.seats,
            viewer = viewer,
            names = names,
            handSize = { seat -> state.handSize(seat) },
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TableArea(state = state, viewer = viewer, names = names, palette = palette)
        }

        if (state.answering) {
            BetBanner(state = state, viewer = viewer, palette = palette)
        } else {
            Text(
                text = when {
                    !minhaVez -> stringResource(R.string.truco_wait)
                    else -> stringResource(R.string.truco_play_prompt)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Actions(
            state = state,
            legais = legais,
            enabled = enabled && minhaVez,
            onMove = onMove,
        )

        // A referência das manilhas. Não é ajuda escondida: no truco mineiro elas são fixas e
        // todo mundo à mesa sabe quais são. Quem está aprendendo é que não sabe, e descobrir
        // isso perdendo a mão não ensina nada.
        Text(
            text = stringResource(R.string.truco_manilhas),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            CardFan(
                cards = mao,
                palette = palette,
                // Só a sugerida sai do leque. Marcar as manilhas aqui também seria tentador
                // e erraria duas vezes: some com o realce da dica, e ensina pelo enfeite em
                // vez de pela linha acima, que diz quais são e vale para a mesa inteira.
                isRaised = { _, carta -> carta == sugerida },
                // Com truco na mesa nenhuma carta se joga: primeiro a resposta.
                isPlayable = { _, _ -> !minhaVez || !state.answering },
                onClick = if (enabled && minhaVez && !state.answering) {
                    { _, carta -> onMove(TrucoMove.Play(carta)) }
                } else {
                    null
                },
            )
        }
    }
}

/** O placar dos dois lados, com o valor da mão ao lado — que é metade da decisão. */
@Composable
private fun Scoreboard(state: TrucoState, viewer: Seat) {
    val meu = state.teamOf(viewer)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(
                R.string.truco_score_line,
                state.score(meu),
                state.score(1 - meu),
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.truco_stake, state.stake),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (state.stake > 1) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Tamanho da marca de rodada. Pequena, mas grande o bastante para se ler de relance. */
private val ROUND_MARK = 22.dp

/**
 * As três rodadas, uma marca cada.
 *
 * A regra que decide a mão não é "duas de três": é "manda quem fez a primeira". Uma pessoa
 * que perdeu de vista qual rodada foi de quem não tem como saber se ainda precisa ganhar ou
 * se já basta empatar — e essa é a diferença entre jogar a manilha agora e guardá-la.
 */
@Composable
private fun RoundMarkers(state: TrucoState, viewer: Seat, palette: BoardPalette) {
    val meu = state.teamOf(viewer)
    // Três marcas porque são três cartas: cada carta na mão é uma rodada, e as duas contas
    // são a mesma. Por isso o tamanho da mão serve aqui sem virar um número solto na tela.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (rodada in 0 until TRUCO_HAND_SIZE) {
            val resultado = state.rounds.getOrNull(rodada)
            val cor = when {
                resultado == null -> palette.border
                resultado == TRUCO_NOBODY -> palette.hint
                resultado == meu -> palette.crown
                else -> MaterialTheme.colorScheme.error
            }
            val descricao = stringResource(
                when {
                    resultado == null -> R.string.truco_round_open
                    resultado == TRUCO_NOBODY -> R.string.truco_round_tied
                    resultado == meu -> R.string.truco_round_won
                    else -> R.string.truco_round_lost
                },
                rodada + 1,
            )
            Box(
                modifier = Modifier
                    .size(ROUND_MARK)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (resultado == null) palette.darkSquare else cor)
                    .border(1.dp, cor, RoundedCornerShape(4.dp))
                    .semantics { contentDescription = descricao },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${rodada + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * As cartas da rodada corrente, com quem jogou cada uma.
 *
 * Sem o nome embaixo, duas cartas soltas não dizem quem está ganhando — e em duplas, com
 * quatro cartas na mesa, dizem menos ainda.
 */
@Composable
private fun TableArea(
    state: TrucoState,
    viewer: Seat,
    names: List<String>,
    palette: BoardPalette,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.darkSquare)
            .heightIn(min = CARD_HEIGHT + 32.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.table.isEmpty()) {
            Text(
                text = stringResource(R.string.truco_table_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (jogada in state.table) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CardFace(card = jogada.card, palette = palette)
                    Text(
                        text = if (jogada.seat == viewer.index) {
                            stringResource(R.string.truco_you)
                        } else {
                            names.getOrNull(jogada.seat)?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.dominoes_opponent_seat, jogada.seat + 1)
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
 * O truco na mesa, e o preço de cada saída.
 *
 * Correr custa o que a mão vale **agora**, e não o que foi pedido — é a conta que mais se
 * erra no truco, e a que decide se vale a pena aguentar.
 */
@Composable
private fun BetBanner(state: TrucoState, viewer: Seat, palette: BoardPalette) {
    val meuPedido = state.bettor == state.teamOf(viewer)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.darkSquare)
            .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = if (meuPedido) {
                    stringResource(R.string.truco_you_asked, state.pending)
                } else {
                    stringResource(R.string.truco_they_asked, state.pending)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.truco_run_cost, state.stake),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Os botões da vez: trucar, ou responder ao truco que está na mesa. */
@Composable
private fun Actions(
    state: TrucoState,
    legais: List<Move>,
    enabled: Boolean,
    onMove: (Move) -> Unit,
) {
    val podePedir = TrucoMove.Call in legais
    // O que se pede ao apertar: o degrau seguinte ao que já está de pé.
    val pedido = nextStake(if (state.answering) state.pending else state.stake)

    if (!state.answering) {
        if (!podePedir) return
        Button(
            onClick = { onMove(TrucoMove.Call) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(callLabel(pedido))
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = { onMove(TrucoMove.Accept) },
            enabled = enabled && TrucoMove.Accept in legais,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.truco_accept))
        }
        if (podePedir) {
            OutlinedButton(
                onClick = { onMove(TrucoMove.Call) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(callLabel(pedido))
            }
        }
        OutlinedButton(
            onClick = { onMove(TrucoMove.Run) },
            enabled = enabled && TrucoMove.Run in legais,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.truco_run))
        }
    }
}

/**
 * O grito do lance.
 *
 * Quem joga truco não diz "pedir seis": diz "seis". Cada degrau tem nome, e usar o nome é o
 * que faz o botão parecer o jogo em vez de um formulário.
 */
@Composable
private fun callLabel(stake: Int?): String = stringResource(
    when (stake) {
        3 -> R.string.truco_call_three
        6 -> R.string.truco_call_six
        9 -> R.string.truco_call_nine
        else -> R.string.truco_call_twelve
    },
)
