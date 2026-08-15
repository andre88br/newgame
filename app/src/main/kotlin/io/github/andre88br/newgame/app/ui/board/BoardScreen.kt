package io.github.andre88br.newgame.app.ui.board

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.data.Settings
import io.github.andre88br.newgame.app.ui.feedback.rememberFeedback
import io.github.andre88br.newgame.app.ui.board.painters.painterFor
import io.github.andre88br.newgame.app.ui.board.surfaces.MoveSurface
import io.github.andre88br.newgame.app.ui.gameName
import io.github.andre88br.newgame.app.ui.reasonText
import io.github.andre88br.newgame.app.ui.rules.HowToPlayDialog
import io.github.andre88br.newgame.core.engine.GameCatalog
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    entry: GameEntry,
    viewModel: BoardViewModel,
    settings: Settings,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val painter = remember(entry.id) { painterFor(entry.id) }
    val feedback = rememberFeedback(settings.sound, settings.haptics)
    var showingRules by remember { mutableStateOf(false) }

    // Som e vibração do último lance. Preso ao contador, e não ao evento: dois lances
    // comuns seguidos são dois cliques, não um.
    LaunchedEffect(ui.eventId) {
        ui.event?.let(feedback::play)
    }

    // O destaque do último lance entra desvanecendo. É a única animação do tabuleiro, e
    // resolve um problema de verdade: quando a IA joga, a peça simplesmente aparece em
    // outro lugar, e sem o destaque surgindo o olho não pega o que mudou.
    val highlight by animateFloatAsState(
        targetValue = if (ui.lastMove.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = if (settings.animations) 260 else 0),
        label = "destaque do último lance",
    )

    val message = ui.message
    val messageText = when (message) {
        is BoardMessage.Rejected -> reasonText(message.reason)
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
                actions = {
                    // Alcançável no meio da partida, que é quando a dúvida aparece — em
                    // geral logo depois de um lance ser recusado.
                    TextButton(onClick = { showingRules = true }) {
                        Text(stringResource(R.string.how_to_play))
                    }
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
            // Região viva: quando a vez muda ou a partida acaba, o leitor de tela avisa
            // sozinho. Sem isto, quem não vê a tela só descobre o resultado se tocar nela.
            Text(
                text = statusText(ui, entry.id),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            val interactor = entry.interactor
            if (interactor != null && painter != null) {
                // Tabuleiro de grade é quadrado: a largura já determina a altura, e sobrar
                // espaço embaixo é o certo.
                Box(modifier = Modifier.fillMaxWidth()) {
                    GridBoard(
                        entry = entry,
                        state = ui.state,
                        interactor = interactor,
                        painter = painter,
                        modifier = Modifier.fillMaxWidth(),
                        selected = ui.selected,
                        highlighted = ui.hinted,
                        lastMove = ui.lastMove,
                        lastMoveAlpha = highlight,
                        flipped = ui.humanSeat == Seat.SECOND,
                        enabled = ui.canPlay,
                        onSquareTap = viewModel::onSquareTap,
                    )
                }
            } else {
                // Dominó, ludo e os jogos de carta: a tela do próprio jogo monta o lance e
                // o entrega pronto.
                //
                // Estes ficam com **toda** a altura que sobrar, e não com a que a largura
                // permitir: a mesa do dominó cresce a cada lance, e apertá-la numa faixa
                // faria a linha virar uma fita ilegível.
                MoveSurface(
                    gameId = entry.id,
                    state = ui.state,
                    viewer = ui.viewer,
                    names = ui.names,
                    enabled = ui.canPlay,
                    hinted = ui.hintedMove,
                    animated = settings.animations,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onMove = viewModel::onMoveChosen,
                )
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

    if (showingRules) {
        HowToPlayDialog(entry = entry, onDismiss = { showingRules = false })
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
 *
 * Com nomes, "jogador 2" some das frases e entra quem está de fato jogando — inclusive a
 * máquina, que ganhou nome próprio. O que a pessoa vê de si continua sendo "Sua vez": ela
 * sabe quem é, e "Vez de André" para o próprio dono do aparelho soa como se houvesse outra
 * pessoa na sala. Onde a cor da peça importa, o nome vem com ela entre parênteses: no
 * xadrez, saber que é a vez da Ana não diz de que lado ela joga.
 */
@Composable
private fun statusText(ui: BoardUiState, gameId: GameId): String = when (val status = ui.status) {
    BoardStatus.Thinking -> {
        // A dica também deixa a tela "pensando", e aí quem pensa é a pessoa: só a cadeira
        // da máquina ganha nome nesta frase.
        val thinker = ui.state.turn
        val name = ui.nameOf(thinker).takeIf { thinker !in ui.humanSeats }
        if (name != null) {
            stringResource(R.string.board_thinking_named, name)
        } else {
            stringResource(R.string.board_thinking)
        }
    }

    BoardStatus.HumanTurn -> stringResource(R.string.board_your_turn)

    is BoardStatus.SeatTurn -> {
        val name = ui.seatLabel(gameId, status.seat)
        if (name != null) {
            stringResource(R.string.board_turn_of, name)
        } else if (coloredPieces(gameId)) {
            stringResource(turnLabel(gameId, status.seat))
        } else {
            // Numa mesa de três ou quatro não há "primeiro" e "segundo": há jogador N.
            stringResource(
                R.string.board_turn_of,
                stringResource(R.string.player_default, status.seat.index + 1),
            )
        }
    }

    is BoardStatus.Finished -> when (val outcome = status.outcome) {
        // Na paciência não há com quem empatar: o empate do motor quer dizer que a mesa
        // empacou, e chamar aquilo de "empate" seria dizer a coisa errada.
        is Outcome.Draw ->
            if (soloGame(gameId)) stringResource(R.string.board_stuck) else stringResource(R.string.board_draw)

        is Outcome.Win -> {
            val name = ui.seatLabel(gameId, outcome.seat)
            when {
                // Mesa de um: quem venceu foi quem está segurando o aparelho, e não há
                // segunda leitura possível.
                soloGame(gameId) -> stringResource(R.string.board_you_won)

                // Ganhar continua sendo "você venceu": trocar por "André venceu" tiraria a
                // única frase do app que fala com quem está segurando o aparelho.
                ui.againstPhone && outcome.seat == ui.humanSeat ->
                    stringResource(R.string.board_you_won)

                name != null -> stringResource(R.string.board_named_won, name)

                ui.againstPhone -> stringResource(R.string.board_you_lost)

                coloredPieces(gameId) -> stringResource(winnerLabel(gameId, outcome.seat))

                else -> stringResource(
                    R.string.board_named_won,
                    stringResource(R.string.player_default, outcome.seat.index + 1),
                )
            }
        }

        Outcome.InProgress -> stringResource(R.string.board_your_turn)
    }
}

/** O nome da cadeira, ou `null` numa partida salva antes de existirem nomes. */
private fun BoardUiState.nameOf(seat: Seat): String? =
    names.getOrNull(seat.index)?.takeIf { it.isNotBlank() }

/** O nome da cadeira com a cor entre parênteses, onde a cor existe. */
@Composable
private fun BoardUiState.seatLabel(gameId: GameId, seat: Seat): String? {
    val name = nameOf(seat) ?: return null
    val side = sideLabel(gameId, seat) ?: return name
    return stringResource(R.string.board_name_with_side, name, stringResource(side))
}

/**
 * Jogo de uma pessoa só.
 *
 * Sai do motor, e não de uma lista escrita aqui: quem declara o tamanho da mesa é o jogo, e
 * um solitário novo entra sem esta tela precisar saber que ele existe.
 */
private fun soloGame(gameId: GameId): Boolean =
    GameCatalog.entry(gameId).rules.supportedSeats.last == 1

/**
 * "Brancas" e "pretas" só dizem alguma coisa onde as peças têm cor. No dominó as duas mãos
 * são iguais, no ludo o que distingue é o canto do tabuleiro, e na copas são quatro pessoas
 * com cartas — então nesses os lados são jogador 1, 2, 3 e 4.
 */
private fun coloredPieces(gameId: GameId): Boolean = when (gameId) {
    GameId.DOMINOES, GameId.LUDO, GameId.HEARTS, GameId.CANASTRA, GameId.PIFE,
    GameId.KLONDIKE,
    -> false
    else -> true
}

/**
 * A cor das peças de uma cadeira, para acompanhar o nome.
 *
 * O jogo da velha fica de fora: lá as marcas são xis e bola, e chamá-las de brancas e
 * pretas confundiria mais do que ajudaria. No reversi, quem abre é o **preto** — o oposto
 * dos outros dois, e o motivo de esta função receber o jogo em vez de olhar só a cadeira.
 */
private fun sideLabel(gameId: GameId, seat: Seat): Int? = when (gameId) {
    GameId.CHECKERS, GameId.CHESS ->
        if (seat == Seat.FIRST) R.string.board_side_first else R.string.board_side_second

    GameId.REVERSI ->
        if (seat == Seat.FIRST) R.string.board_side_second else R.string.board_side_first

    else -> null
}

private fun turnLabel(gameId: GameId, seat: Seat): Int = when (gameId) {
    GameId.REVERSI ->
        if (seat == Seat.FIRST) R.string.board_turn_second else R.string.board_turn_first

    else -> if (seat == Seat.FIRST) R.string.board_turn_first else R.string.board_turn_second
}

private fun winnerLabel(gameId: GameId, seat: Seat): Int = when (gameId) {
    GameId.REVERSI ->
        if (seat == Seat.FIRST) R.string.board_second_won else R.string.board_first_won

    else -> if (seat == Seat.FIRST) R.string.board_first_won else R.string.board_second_won
}
