package io.github.andre88br.newgame.app.data

import android.content.Context
import android.content.SharedPreferences
import io.github.andre88br.newgame.app.ui.theme.DeckColorChoice
import io.github.andre88br.newgame.app.ui.theme.ThemeChoice
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.session.BotNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quanto tempo a máquina espera, de propósito, entre um lance dela e o seguinte — e quanto
 * dura a pausa de fim de mão nos jogos de carta com rodada.
 *
 * O multiplicador anda sobre [io.github.andre88br.newgame.core.engine.GameEntry.aiPaceMillis],
 * que já é o compasso pensado por jogo: dobrar aqui em cima dobra o de todos igual, em vez de
 * fingir que um segundo vale o mesmo numa vaza de copas e numa jogada de xadrez.
 */
enum class GameSpeed(val multiplier: Double) {
    SLOW(2.2),
    NORMAL(1.6),
    FAST(1.0),
}

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val defaultDifficulty: Difficulty = Difficulty.MEDIUM,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    /** O ritmo da máquina: quanto mais lento, mais dá para acompanhar o lance dela. */
    val gameSpeed: GameSpeed = GameSpeed.NORMAL,
    /**
     * Ligadas por padrão, mas desligáveis.
     *
     * Não é só gosto: movimento na tela atrapalha quem tem sensibilidade a isso, e o
     * sistema tem um ajuste equivalente justamente por causa disso.
     */
    val animations: Boolean = true,
    /**
     * O nome de quem joga, lembrado entre partidas.
     *
     * Vazio quer dizer "ainda não disse": a tela de configuração mostra o campo em branco,
     * com "Você" como sugestão, em vez de inventar um nome que a pessoa nunca escolheu.
     */
    val playerName: String = "",
    /** As costas do baralho, nas telas de carta. */
    val deckColor: DeckColorChoice = DeckColorChoice.CLASSIC,
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

    fun setGameSpeed(speed: GameSpeed) {
        prefs.edit().putString(KEY_GAME_SPEED, speed.name).apply()
        _settings.value = _settings.value.copy(gameSpeed = speed)
    }

    /** Guarda o nome digitado para a próxima partida já vir preenchida. */
    fun setPlayerName(name: String) {
        val clean = BotNames.sanitize(name)
        prefs.edit().putString(KEY_PLAYER_NAME, clean).apply()
        _settings.value = _settings.value.copy(playerName = clean)
    }

    fun setAnimations(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS, enabled).apply()
        _settings.value = _settings.value.copy(animations = enabled)
    }

    fun setDeckColor(choice: DeckColorChoice) {
        prefs.edit().putString(KEY_DECK_COLOR, choice.name).apply()
        _settings.value = _settings.value.copy(deckColor = choice)
    }

    private fun read(): Settings = Settings(
        theme = prefs.getString(KEY_THEME, null).toEnum(ThemeChoice.SYSTEM),
        defaultDifficulty = prefs.getString(KEY_DIFFICULTY, null).toEnum(Difficulty.MEDIUM),
        sound = prefs.getBoolean(KEY_SOUND, true),
        haptics = prefs.getBoolean(KEY_HAPTICS, true),
        gameSpeed = prefs.getString(KEY_GAME_SPEED, null).toEnum(GameSpeed.NORMAL),
        animations = prefs.getBoolean(KEY_ANIMATIONS, true),
        playerName = prefs.getString(KEY_PLAYER_NAME, "").orEmpty(),
        deckColor = prefs.getString(KEY_DECK_COLOR, null).toEnum(DeckColorChoice.CLASSIC),
    )

    /** Valor gravado por uma versão anterior que não exista mais volta ao padrão. */
    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_DIFFICULTY = "default_difficulty"
        const val KEY_SOUND = "sound"
        const val KEY_HAPTICS = "haptics"
        const val KEY_GAME_SPEED = "game_speed"
        const val KEY_ANIMATIONS = "animations"
        const val KEY_PLAYER_NAME = "player_name"
        const val KEY_DECK_COLOR = "deck_color"
    }
}
