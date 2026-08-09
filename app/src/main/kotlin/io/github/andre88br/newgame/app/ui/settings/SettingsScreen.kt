package io.github.andre88br.newgame.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.data.AppPreferences
import io.github.andre88br.newgame.app.ui.components.ChoiceRow
import io.github.andre88br.newgame.app.ui.difficultyName
import io.github.andre88br.newgame.app.ui.theme.ThemeChoice
import io.github.andre88br.newgame.core.ai.Difficulty

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(preferences: AppPreferences, onBack: () -> Unit) {
    val settings by preferences.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
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
            ChoiceRow(
                label = stringResource(R.string.settings_theme),
                options = ThemeChoice.entries.toList(),
                selected = settings.theme,
                optionLabel = {
                    stringResource(
                        when (it) {
                            ThemeChoice.SYSTEM -> R.string.settings_theme_system
                            ThemeChoice.LIGHT -> R.string.settings_theme_light
                            ThemeChoice.DARK -> R.string.settings_theme_dark
                        },
                    )
                },
                onSelect = preferences::setTheme,
            )

            ChoiceRow(
                label = stringResource(R.string.settings_default_difficulty),
                options = Difficulty.entries.toList(),
                selected = settings.defaultDifficulty,
                optionLabel = { difficultyName(it) },
                onSelect = preferences::setDefaultDifficulty,
            )

            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
