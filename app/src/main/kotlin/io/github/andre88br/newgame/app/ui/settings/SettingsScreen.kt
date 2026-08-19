package io.github.andre88br.newgame.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.data.AppPreferences
import io.github.andre88br.newgame.app.data.GameSpeed
import io.github.andre88br.newgame.app.ui.components.ChoiceRow
import io.github.andre88br.newgame.app.ui.difficultyName
import io.github.andre88br.newgame.app.ui.theme.DeckColorChoice
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

            ChoiceRow(
                label = stringResource(R.string.settings_deck_color),
                options = DeckColorChoice.entries.toList(),
                selected = settings.deckColor,
                optionLabel = {
                    stringResource(
                        when (it) {
                            DeckColorChoice.CLASSIC -> R.string.settings_deck_color_classic
                            DeckColorChoice.RED -> R.string.settings_deck_color_red
                            DeckColorChoice.BLUE -> R.string.settings_deck_color_blue
                            DeckColorChoice.PURPLE -> R.string.settings_deck_color_purple
                        },
                    )
                },
                onSelect = preferences::setDeckColor,
            )

            ChoiceRow(
                label = stringResource(R.string.settings_game_speed),
                options = GameSpeed.entries.toList(),
                selected = settings.gameSpeed,
                optionLabel = {
                    stringResource(
                        when (it) {
                            GameSpeed.SLOW -> R.string.settings_game_speed_slow
                            GameSpeed.NORMAL -> R.string.settings_game_speed_normal
                            GameSpeed.FAST -> R.string.settings_game_speed_fast
                        },
                    )
                },
                onSelect = preferences::setGameSpeed,
            )

            SwitchRow(
                label = stringResource(R.string.settings_sound),
                summary = stringResource(R.string.settings_sound_summary),
                checked = settings.sound,
                onChange = preferences::setSound,
            )

            SwitchRow(
                label = stringResource(R.string.settings_haptics),
                summary = stringResource(R.string.settings_haptics_summary),
                checked = settings.haptics,
                onChange = preferences::setHaptics,
            )

            SwitchRow(
                label = stringResource(R.string.settings_animations),
                summary = stringResource(R.string.settings_animations_summary),
                checked = settings.animations,
                onChange = preferences::setAnimations,
            )

            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Um ajuste de liga-desliga, com uma linha explicando o que ele faz.
 *
 * A linha de resumo não é enfeite: "Vibração" sozinho não diz se vibra a cada toque ou só
 * no fim da partida, e quem está decidindo se desliga precisa saber disso.
 */
@Composable
private fun SwitchRow(
    label: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // O toque é da linha inteira, e não só do interruptor: alvo maior, e o leitor de
        // tela anuncia uma coisa só em vez de rótulo e controle separados.
        Switch(checked = checked, onCheckedChange = null)
    }
}
