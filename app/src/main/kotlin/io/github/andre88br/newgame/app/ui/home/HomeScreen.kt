package io.github.andre88br.newgame.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.data.MatchStore
import io.github.andre88br.newgame.app.ui.gameName
import io.github.andre88br.newgame.core.engine.GameCatalog
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    store: MatchStore,
    onPlay: (GameEntry) -> Unit,
    onResume: (GameId, String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Observa a lista para o botão "continuar" aparecer e sumir sozinho conforme as
    // partidas começam e terminam.
    val matches by store.matches.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    TextButton(onClick = onOpenHistory) {
                        Text(stringResource(R.string.home_history))
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.home_settings))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            items(GameCatalog.available, key = { it.id.name }) { entry ->
                val ongoing = matches.firstOrNull { it.gameId == entry.id && !it.finished }
                GameCard(
                    entry = entry,
                    ongoingMoves = ongoing?.record?.ply,
                    onPlay = { onPlay(entry) },
                    onResume = { ongoing?.let { onResume(entry.id, it.id) } },
                )
            }
        }
    }
}

@Composable
private fun GameCard(
    entry: GameEntry,
    ongoingMoves: Int?,
    onPlay: () -> Unit,
    onResume: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = gameName(entry), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.home_players_two),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (ongoingMoves != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.history_moves, ongoingMoves),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onResume) {
                        Text(stringResource(R.string.home_resume))
                    }
                }
            }
        }
    }
}
