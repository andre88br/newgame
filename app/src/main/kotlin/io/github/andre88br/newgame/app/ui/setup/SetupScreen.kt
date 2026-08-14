package io.github.andre88br.newgame.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.board.surfaces.ludoArmColor
import io.github.andre88br.newgame.app.ui.components.ChoiceRow
import io.github.andre88br.newgame.app.ui.difficultyName
import io.github.andre88br.newgame.app.ui.gameName
import io.github.andre88br.newgame.app.ui.rules.HowToPlayDialog
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.ludo.LUDO_ARMS

/** Modo de jogo escolhido antes de começar. */
enum class MatchMode {
    AGAINST_PHONE,
    PASS_AND_PLAY,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    entry: GameEntry,
    defaultDifficulty: Difficulty,
    hasOngoingMatch: Boolean,
    onBack: () -> Unit,
    onStart: (
        mode: MatchMode,
        difficulty: Difficulty,
        humanSeat: Seat,
        seats: Int,
        ludoFirstArm: Int,
    ) -> Unit,
) {
    var mode by remember { mutableStateOf(MatchMode.AGAINST_PHONE) }
    var difficulty by remember { mutableStateOf(defaultDifficulty) }
    var showingRules by remember { mutableStateOf(false) }

    val mesasPossiveis = entry.rules.supportedSeats.toList()
    var seats by remember(entry.id) { mutableStateOf(mesasPossiveis.first()) }

    // Some quando o número de cadeiras muda: uma cadeira escolhida numa mesa de quatro pode
    // não existir mais numa mesa de dois.
    var humanSeat by remember(seats) { mutableStateOf(Seat.FIRST) }

    // A cor do ludo é independente da cadeira: gira qual braço a cadeira zero ocupa, e por
    // isso as quatro cores continuam disponíveis mesmo numa mesa de dois.
    var ludoColor by remember(entry.id) { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gameName(entry)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
                actions = {
                    TextButton(onClick = { showingRules = true }) {
                        Text(stringResource(R.string.how_to_play))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            // Só onde há escolha: os jogos de tabuleiro fixo aceitam dois e mais nada.
            if (mesasPossiveis.size > 1) {
                ChoiceRow(
                    label = stringResource(R.string.setup_players),
                    options = mesasPossiveis,
                    selected = seats,
                    optionLabel = { stringResource(R.string.setup_players_count, it) },
                    onSelect = { seats = it },
                )
            }

            ChoiceRow(
                label = stringResource(R.string.setup_mode),
                options = MatchMode.entries.toList(),
                selected = mode,
                optionLabel = {
                    stringResource(
                        when (it) {
                            MatchMode.AGAINST_PHONE -> R.string.setup_mode_ai
                            MatchMode.PASS_AND_PLAY -> R.string.setup_mode_local
                        },
                    )
                },
                onSelect = { mode = it },
            )

            // Nível e quem começa só fazem sentido contra o celular.
            if (mode == MatchMode.AGAINST_PHONE) {
                if (seats > 2) {
                    Text(
                        text = stringResource(R.string.setup_many_opponents, seats - 1),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                ChoiceRow(
                    label = stringResource(R.string.setup_difficulty),
                    options = Difficulty.entries.toList(),
                    selected = difficulty,
                    optionLabel = { difficultyName(it) },
                    onSelect = { difficulty = it },
                )

                // Onde as regras é que decidem quem abre, oferecer a escolha seria mentir:
                // no dominó abre a maior carroça, no ludo o primeiro dado que serve.
                if (entry.rules.decidesWhoStarts) {
                    Text(
                        text = stringResource(R.string.setup_rules_decide_start),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    ChoiceRow(
                        label = stringResource(R.string.setup_who_starts),
                        options = listOf(Seat.FIRST, Seat.SECOND),
                        selected = humanSeat,
                        optionLabel = {
                            stringResource(
                                if (it == Seat.FIRST) R.string.setup_you_start else R.string.setup_phone_starts,
                            )
                        },
                        onSelect = { humanSeat = it },
                    )
                }

                // Quem começa é decidido pelo dado, mas a cor continua sendo escolha: as
                // quatro ficam sempre disponíveis, mesmo numa mesa de dois — só o braço da
                // cadeira zero gira, o espaçamento entre cadeiras não muda.
                if (entry.id == GameId.LUDO) {
                    LudoColorRow(
                        selected = ludoColor,
                        onSelect = { ludoColor = it },
                    )
                }
            }

            if (hasOngoingMatch) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.setup_resume_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (showingRules) {
                HowToPlayDialog(entry = entry, onDismiss = { showingRules = false })
            }

            Button(
                onClick = { onStart(mode, difficulty, humanSeat, seats, ludoColor) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.setup_start))
            }
        }
    }
}

/**
 * As quatro cores do ludo, para escolher antes de começar.
 *
 * Sempre as quatro, em qualquer tamanho de mesa — inclusive de dois, onde antes só vermelho
 * e amarelo apareciam prontos de fábrica. É o braço da cadeira zero que gira para caber a
 * cor escolhida; as outras cadeiras continuam em ordem a partir dali.
 *
 * Um botão de cada cor não bastaria: a mesma bolinha que aqui representa "eu sou o
 * vermelho" é a cor que aparece nos peões e no braço da cruz durante o jogo, e por isso o
 * desenho é a própria cor, não o nome dela — o nome só entra como descrição para quem usa
 * leitor de tela.
 */
@Composable
private fun LudoColorRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.setup_ludo_color),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (arm in 0 until LUDO_ARMS) {
                val name = ludoColorName(arm)
                val isSelected = arm == selected
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ludoArmColor(arm))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 1f else 0.2f),
                            shape = CircleShape,
                        )
                        .clickable(onClickLabel = name) { onSelect(arm) }
                        .semantics { contentDescription = name },
                )
            }
        }
    }
}

@Composable
private fun ludoColorName(arm: Int): String = stringResource(
    when (arm) {
        0 -> R.string.ludo_color_red
        1 -> R.string.ludo_color_blue
        2 -> R.string.ludo_color_yellow
        else -> R.string.ludo_color_green
    },
)
