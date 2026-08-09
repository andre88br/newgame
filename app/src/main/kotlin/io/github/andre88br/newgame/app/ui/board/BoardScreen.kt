package io.github.andre88br.newgame.app.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.board.painters.painterFor
import io.github.andre88br.newgame.app.ui.board.surfaces.MoveSurface
import io.github.andre88br.newgame.app.ui.gameName
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    entry: GameEntry,
    viewModel: BoardViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val painter = remember(entry.id) { painterFor(entry.id) }

    val message = ui.message
    val messageText = when (message) {
        is BoardMessage.Reason -> message.text
        is BoardMessage.Hint -> stringResource(R.string.board_hint_shown, message.notation)
        BoardMessage.NoHint -> stringResource(R.string.board_no_hint)
        null -> null
    }

    LaunchedEffect(ui.messageId) {
        if (!messageText.isNullOrBlank()) {
            snackbarHostState.showSnackbar(messageText)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gameName(entry)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = statusText(ui, entry.id),
                style = MaterialTheme.typography.titleMedium,
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                val interactor = entry.interactor
                if (interactor != null && painter != null) {
                    GridBoard(
                        state = ui.state,
                        interactor = interactor,
                        painter = painter,
                        modifier = Modifier.fillMaxWidth(),
                        selected = ui.selected,
                        highlighted = ui.hinted,
                        lastMove = ui.lastMove,
                        flipped = ui.humanSeat == Seat.SECOND,
                        enabled = ui.canPlay,
                        onSquareTap = viewModel::onSquareTap,
                    )
                } else {
                    // Dominó e ludo: a tela do próprio jogo monta o lance e o entrega pronto.
                    MoveSurface(
                        gameId = entry.id,
                        state = ui.state,
                        viewer = ui.viewer,
                        enabled = ui.canPlay,
                        hinted = ui.hintedMove,
                        modifier = Modifier.fillMaxWidth(),
                        onMove = viewModel::onMoveChosen,
                    )
                }
            }

            MoveHistory(history = ui.history)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::onUndo,
                    enabled = ui.canUndo && ui.status != BoardStatus.Thinking,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.board_undo))
                }

                OutlinedButton(
                    onClick = viewModel::onHint,
                    enabled = ui.canPlay,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.board_hint))
                }
            }

            Button(
                onClick = viewModel::onRestart,
                enabled = ui.status != BoardStatus.Thinking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.board_restart))
            }
        }
    }

    ui.promotion?.let { pending ->
        PromotionDialog(
            choices = pending.choices,
            onPick = viewModel::onPromotionChosen,
            onDismiss = viewModel::onPromotionCancelled,
        )
    }
}

/**
 * O texto de estado depende do modo: contra o celular a pessoa pensa em "eu" e "ele";
 * no passa-e-joga não há "você", e o certo é dizer de quem é a vez.
 */
@Composable
private fun statusText(ui: BoardUiState, gameId: GameId): String = when (val status = ui.status) {
    BoardStatus.Thinking -> stringResource(R.string.board_thinking)
    BoardStatus.HumanTurn -> stringResource(R.string.board_your_turn)
    is BoardStatus.SeatTurn -> stringResource(turnLabel(gameId, status.seat))

    is BoardStatus.Finished -> when (val outcome = status.outcome) {
        is Outcome.Draw -> stringResource(R.string.board_draw)
        is Outcome.Win ->
            if (ui.againstPhone) {
                stringResource(
                    if (outcome.seat == ui.humanSeat) R.string.board_you_won else R.string.board_you_lost,
                )
            } else {
                stringResource(winnerLabel(gameId, outcome.seat))
            }

        Outcome.InProgress -> stringResource(R.string.board_your_turn)
    }
}

/**
 * "Brancas" e "pretas" só dizem alguma coisa onde as peças têm cor. No dominó as duas mãos
 * são iguais e no ludo o que distingue é o canto do tabuleiro, então ali os dois lados são
 * jogador 1 e jogador 2.
 */
private fun coloredPieces(gameId: GameId): Boolean = when (gameId) {
    GameId.DOMINOES, GameId.LUDO -> false
    else -> true
}

private fun turnLabel(gameId: GameId, seat: Seat): Int = when {
    coloredPieces(gameId) && seat == Seat.FIRST -> R.string.board_turn_first
    coloredPieces(gameId) -> R.string.board_turn_second
    seat == Seat.FIRST -> R.string.board_turn_player_first
    else -> R.string.board_turn_player_second
}

private fun winnerLabel(gameId: GameId, seat: Seat): Int = when {
    coloredPieces(gameId) && seat == Seat.FIRST -> R.string.board_first_won
    coloredPieces(gameId) -> R.string.board_second_won
    seat == Seat.FIRST -> R.string.board_player_first_won
    else -> R.string.board_player_second_won
}
