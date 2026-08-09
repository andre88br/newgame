package io.github.andre88br.newgame.app.data

import android.content.Context
import android.content.SharedPreferences
import io.github.andre88br.newgame.app.ui.theme.ThemeChoice
import io.github.andre88br.newgame.core.ai.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val defaultDifficulty: Difficulty = Difficulty.MEDIUM,
)

/**
 * Duas preferências guardadas em `SharedPreferences`.
 *
 * Sim, o DataStore é a recomendação atual. Para dois valores lidos na abertura do app, ele
 * traria uma dependência a mais em troca de nada perceptível — e leitura assíncrona de
 * preferência de tema significa o app abrir no tema errado e corrigir depois, com um
 * piscar. Aqui a leitura síncrona é a característica desejada, não uma limitação.
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("newgame_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun setTheme(choice: ThemeChoice) {
        prefs.edit().putString(KEY_THEME, choice.name).apply()
        _settings.value = _settings.value.copy(theme = choice)
    }

    fun setDefaultDifficulty(difficulty: Difficulty) {
        prefs.edit().putString(KEY_DIFFICULTY, difficulty.name).apply()
        _settings.value = _settings.value.copy(defaultDifficulty = difficulty)
    }

    private fun read(): Settings = Settings(
        theme = prefs.getString(KEY_THEME, null).toEnum(ThemeChoice.SYSTEM),
        defaultDifficulty = prefs.getString(KEY_DIFFICULTY, null).toEnum(Difficulty.MEDIUM),
    )

    /** Valor gravado por uma versão anterior que não exista mais volta ao padrão. */
    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DIFFICULTY = "default_difficulty"
    }
}
