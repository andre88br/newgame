package io.github.andre88br.newgame.app.ui.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.andre88br.newgame.app.R

/** O que acabou de acontecer na partida, em termos de retorno para quem está jogando. */
enum class GameEvent {
    /** Lance comum. */
    MOVE,

    /** Lance que tirou peça do adversário — mais grave, para se distinguir do comum. */
    CAPTURE,

    /** A partida acabou. */
    FINISH,
}

/**
 * Som e vibração.
 *
 * Existe porque o tabuleiro é silencioso e a IA joga sozinha: sem retorno nenhum, quem
 * está olhando para outro lado não percebe que o adversário respondeu. Um clique curto
 * resolve isso melhor do que qualquer animação.
 *
 * As duas coisas são desligáveis nos ajustes, e desligadas elas não custam nada — o
 * [SoundPool] só é criado quando o som está ligado.
 */
class Feedback(
    private val context: Context,
    private val haptics: HapticFeedback,
    private val soundEnabled: Boolean,
    private val hapticsEnabled: Boolean,
) {

    private var pool: SoundPool? = null
    private val ids = HashMap<GameEvent, Int>()

    fun play(event: GameEvent) {
        if (hapticsEnabled) {
            // `LongPress` e nada mais: é o único tipo que existe em toda versão do Compose
            // que este projeto atravessa. Os tipos mais expressivos entraram e saíram da
            // API entre versões, e trocar de constante não vale um build quebrado.
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        if (!soundEnabled) return

        val active = pool ?: load().also { pool = it }
        ids[event]?.let { active.play(it, VOLUME, VOLUME, 1, 0, 1f) }
    }

    private fun load(): SoundPool {
        val created = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // GAME, e não MUSIC: o sistema abaixa isto junto com os outros jogos e
                    // não interrompe quem está ouvindo música enquanto joga.
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()

        ids[GameEvent.MOVE] = created.load(context, R.raw.move, 1)
        ids[GameEvent.CAPTURE] = created.load(context, R.raw.capture, 1)
        ids[GameEvent.FINISH] = created.load(context, R.raw.finish, 1)
        return created
    }

    fun release() {
        pool?.release()
        pool = null
        ids.clear()
    }

    private companion object {
        const val VOLUME = 0.7f
    }
}

/**
 * Monta o [Feedback] e o solta quando a tela sai.
 *
 * O `SoundPool` segura memória e um canal de áudio; deixá-lo vivo depois que a tela do
 * tabuleiro fechou seria vazamento silencioso — daqueles que só aparecem depois de trocar
 * de jogo umas dez vezes.
 */
@Composable
fun rememberFeedback(soundEnabled: Boolean, hapticsEnabled: Boolean): Feedback {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val feedback = remember(soundEnabled, hapticsEnabled) {
        Feedback(context.applicationContext, haptics, soundEnabled, hapticsEnabled)
    }
    DisposableEffect(feedback) {
        onDispose { feedback.release() }
    }
    return feedback
}
