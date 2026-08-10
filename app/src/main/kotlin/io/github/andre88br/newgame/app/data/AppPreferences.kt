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
    val sound: Boolean = true,
    val haptics: Boolean = true,
    /**
     * Ligadas por padrão, mas desligáveis.
     *
     * Não é só gosto: movimento na tela atrapalha quem tem sensibilidade a isso, e o
     * sistema tem um ajuste equivalente justamente por causa disso.
     */
    val animations: Boolean = true,
)

/**
 * As preferências do app, guardadas em `SharedPreferences`.
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

    fun setSound(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
        _settings.value = _settings.value.copy(sound = enabled)
    }

    fun setHaptics(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTICS, enabled).apply()
        _settings.value = _settings.value.copy(haptics = enabled)
    }

    fun setAnimations(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS, enabled).apply()
        _settings.value = _settings.value.copy(animations = enabled)
    }

    private fun read(): Settings = Settings(
        theme = prefs.getString(KEY_THEME, null).toEnum(ThemeChoice.SYSTEM),
        defaultDifficulty = prefs.getString(KEY_DIFFICULTY, null).toEnum(Difficulty.MEDIUM),
        sound = prefs.getBoolean(KEY_SOUND, true),
        haptics = prefs.getBoolean(KEY_HAPTICS, true),
        animations = prefs.getBoolean(KEY_ANIMATIONS, true),
    )

    /** Valor gravado por uma versão anterior que não exista mais volta ao padrão. */
    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DIFFICULTY = "default_difficulty"
        const val KEY_SOUND = "sound"
        const val KEY_HAPTICS = "haptics"
        const val KEY_ANIMATIONS = "animations"
    }
}
