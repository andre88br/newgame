package io.github.andre88br.newgame.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.data.MatchStore
import io.github.andre88br.newgame.app.data.SavedMatch
import io.github.andre88br.newgame.app.ui.difficultyName
import io.github.andre88br.newgame.app.ui.gameName
import io.github.andre88br.newgame.core.engine.GameCatalog
import io.github.andre88br.newgame.core.engine.Outcome
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(store: MatchStore, onBack: () -> Unit) {
    val matches by store.matches.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
                actions = {
                    if (matches.isNotEmpty()) {
                        TextButton(onClick = { scope.launch { store.clear() } }) {
                            Text(stringResource(R.string.history_clear))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (matches.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            // Um resumo por jogo, contando só as partidas contra o celular — comparar
            // vitórias no passa-e-joga não diria nada sobre ninguém.
            items(GameCatalog.available, key = { "stats-${it.id.name}" }) { entry ->
                val stats = store.statsAgainstPhone(entry.id)
                if (stats.total > 0) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(gameName(entry), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(
                                    R.string.history_stats,
                                    stats.wins,
                                    stats.losses,
                                    stats.draws,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(matches, key = { it.id }) { match ->
                MatchRow(match = match, onDelete = { scope.launch { store.delete(match.id) } })
            }
        }
    }
}

@Composable
private fun MatchRow(match: SavedMatch, onDelete: () -> Unit) {
    val entry = GameCatalog.entry(match.gameId)

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(gameName(entry), style = MaterialTheme.typography.titleMedium)

                Text(
                    text = outcomeLabel(match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Quem jogou. Só aparece nas partidas que têm nomes: as gravadas antes
                // disso continuam mostrando o que sempre mostraram.
                lineup(match)?.let { quem ->
                    Text(
                        text = quem,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val opponent = match.difficulty?.let { difficultyName(it) }
                    ?: stringResource(R.string.setup_mode_local)
                Text(
                    text = "$opponent · " + stringResource(R.string.history_moves, match.record.ply) +
                        " · " + DateFormat.getDateInstance(DateFormat.SHORT)
                            .format(Date(match.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TextButton(onClick = onDelete) { Text("×") }
        }
    }
}

@Composable
private fun outcomeLabel(match: SavedMatch): String = when (val outcome = match.record.outcome) {
    Outcome.InProgress -> stringResource(R.string.history_in_progress)
    is Outcome.Draw -> stringResource(R.string.board_draw)
    is Outcome.Win -> {
        val name = match.nameOf(outcome.seat)
        when {
            match.againstPhone && outcome.seat == match.humanSeat ->
                stringResource(R.string.board_you_won)

            name != null -> stringResource(R.string.board_named_won, name)

            match.againstPhone -> stringResource(R.string.board_you_lost)

            else -> stringResource(
                if (outcome.seat.index == 0) R.string.board_first_won else R.string.board_second_won,
            )
        }
    }
}

/** Quem jogou a partida, ou `null` se ela foi gravada antes de existirem nomes. */
@Composable
private fun lineup(match: SavedMatch): String? {
    val names = match.playerNames.filter { it.isNotBlank() }
    return when {
        names.size == 2 -> stringResource(R.string.history_versus, names[0], names[1])
        // Numa mesa de três ou quatro, "A vs B vs C" fica pior de ler do que a lista.
        names.size > 2 -> names.joinToString(" · ")
        else -> null
    }
}
