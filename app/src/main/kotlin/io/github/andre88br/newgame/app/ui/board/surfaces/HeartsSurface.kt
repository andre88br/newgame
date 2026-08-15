package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import io.github.andre88br.newgame.core.games.hearts.HEARTS_PASS_SIZE
import io.github.andre88br.newgame.core.games.hearts.HEARTS_SEATS
import io.github.andre88br.newgame.core.games.hearts.HeartsGame
import io.github.andre88br.newgame.core.games.hearts.HeartsMove
import io.github.andre88br.newgame.core.games.hearts.HeartsPhase
import io.github.andre88br.newgame.core.games.hearts.HeartsState
import io.github.andre88br.newgame.core.games.hearts.PassDirection

/**
 * A mesa de copas.
 *
 * Não há tabuleiro: o que a pessoa precisa ver é a própria mão, o que já caiu na vaza e como
 * anda o placar — em copas o placar é metade do jogo, porque o objetivo não é ganhar a mão, é
 * não chegar aos cem antes dos outros.
 *
 * O estado chega redigido do motor, com as mãos alheias já viradas. O que se desenha das
 * outras cadeiras é [Card.HIDDEN] de verdade: não há valor escondido atrás do desenho.
 */
@Composable
fun HeartsSurface(
    state: HeartsState,
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
    // Quais cartas a regra deixa jogar agora. Vem do motor: a tela não sabe as regras de
    // copas, e duplicá-las aqui seria a forma mais rápida de a tela e o jogo discordarem.
    val jogaveis = remember(state, viewer) {
        if (state.turn == viewer) HeartsGame.legalMoves(state).map { it.card }.toSet() else emptySet()
    }
    val sugerida = (hinted as? HeartsMove)?.card
    val minhaVez = state.turn == viewer

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Scoreboard(state = state, viewer = viewer, names = names)

        // A vaza fica com o espaço que sobrar: é o que muda a cada lance.
        TrickArea(
            state = state,
            names = names,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Text(
            text = phaseText(state, viewer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // **As cartas que você já escolheu para passar.**
        //
        // Elas saem da mão assim que são tocadas — é o que impede escolher a mesma duas
        // vezes —, e sem mostrá-las aqui a pessoa veria três cartas sumirem sem saber quais
        // foram, justamente na hora em que precisa decidir a terceira.
        val escolhidas = state.passing.getOrElse(viewer.index) { emptyList() }
        if (state.phase == HeartsPhase.PASSING && escolhidas.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (carta in escolhidas) {
                    CardFace(card = carta, palette = palette, selected = true)
                }
            }
        }

        // A mão em leque, uma carta por cima da outra. Treze cartas lado a lado não caberiam
        // na largura de um celular, e uma mão que só se vê rolando não dá para avaliar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            CardFan(
                cards = mao,
                palette = palette,
                hinted = sugerida,
                // Fora da vez nada fica apagado — não está sendo pedido nada a você. Na sua
                // vez, apaga o que a regra não deixa: no passe tudo serve, nas vazas só as
                // cartas que servem o naipe (ou o que valer no momento).
                isPlayable = { carta ->
                    !minhaVez || state.phase == HeartsPhase.PASSING || carta in jogaveis
                },
                onClick = if (enabled) {
                    { carta -> onMove(HeartsMove(carta)) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * O placar, que em copas é o jogo todo.
 *
 * Cada cadeira mostra o total da partida e, entre parênteses, o que já levou nesta mão —
 * são coisas diferentes, e quem está com 92 pontos precisa saber que levou mais 8 agora.
 */
@Composable
private fun Scoreboard(
    state: HeartsState,
    viewer: Seat,
    names: List<String>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (index in 0 until HEARTS_SEATS) {
            val seat = Seat(index)
            val daMao = state.handPoints.getOrElse(index) { 0 }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (seat == viewer) {
                        stringResource(R.string.hearts_you)
                    } else {
                        names.getOrNull(index)?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.dominoes_opponent_seat, index + 1)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (seat == state.turn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        R.string.hearts_score,
                        state.score(seat),
                        daMao,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = stringResource(R.string.hearts_cards_left, state.handSize(seat)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A vaza em andamento.
 *
 * Cada carta aparece com quem a jogou embaixo. Sem o nome, quatro cartas soltas não dizem
 * quem está ganhando a vaza — que é justamente a conta que se faz antes de escolher a
 * própria carta.
 */
@Composable
private fun TrickArea(
    state: HeartsState,
    names: List<String>,
    palette: BoardPalette,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.darkSquare)
            .heightIn(min = CARD_HEIGHT + 32.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (state.trick.isEmpty()) {
            Text(
                text = if (state.phase == HeartsPhase.PASSING) {
                    stringResource(R.string.hearts_table_passing)
                } else {
                    stringResource(R.string.hearts_table_empty)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Box
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (jogada in state.trick) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CardFace(card = jogada.card, palette = palette)
                    Text(
                        text = names.getOrNull(jogada.seat.index)?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.dominoes_opponent_seat, jogada.seat.index + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** O que se espera da pessoa agora, em uma frase. */
@Composable
private fun phaseText(state: HeartsState, viewer: Seat): String {
    if (state.phase == HeartsPhase.PASSING) {
        val jaEscolhidas = state.passing.getOrElse(viewer.index) { emptyList() }.size
        return stringResource(
            R.string.hearts_pass_prompt,
            passDirectionName(state.passDirection),
            jaEscolhidas,
            HEARTS_PASS_SIZE,
        )
    }
    return if (state.heartsBroken) {
        stringResource(R.string.hearts_broken)
    } else {
        stringResource(R.string.hearts_not_broken_yet)
    }
}

@Composable
private fun passDirectionName(direction: PassDirection): String = stringResource(
    when (direction) {
        PassDirection.LEFT -> R.string.hearts_pass_left
        PassDirection.RIGHT -> R.string.hearts_pass_right
        PassDirection.ACROSS -> R.string.hearts_pass_across
        PassDirection.NONE -> R.string.hearts_pass_none
    },
)
