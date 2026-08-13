package io.github.andre88br.newgame.app.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.components.ChoiceRow
import io.github.andre88br.newgame.app.ui.difficultyName
import io.github.andre88br.newgame.app.ui.gameName
import io.github.andre88br.newgame.app.ui.rules.HowToPlayDialog
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.Seat

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
    onStart: (mode: MatchMode, difficulty: Difficulty, humanSeat: Seat) -> Unit,
) {
    var mode by remember { mutableStateOf(MatchMode.AGAINST_PHONE) }
    var difficulty by remember { mutableStateOf(defaultDifficulty) }
    var humanSeat by remember { mutableStateOf(Seat.FIRST) }
    var showingRules by remember { mutableStateOf(false) }

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
                onClick = { onStart(mode, difficulty, humanSeat) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.setup_start))
            }
        }
    }
}
