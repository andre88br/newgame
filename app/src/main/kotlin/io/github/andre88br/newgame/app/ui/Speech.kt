package io.github.andre88br.newgame.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.andre88br.newgame.core.a11y.Speech
import io.github.andre88br.newgame.core.a11y.SpeechKey
import io.github.andre88br.newgame.core.a11y.SquareSpeech
import io.github.andre88br.newgame.core.engine.format

/**
 * A descrição falada no idioma do aparelho.
 *
 * O `core-game` diz *o que* há na casa — "e4, peão branco" em chaves; aqui isso vira a
 * frase que o TalkBack lê. Mesma divisão de trabalho de [reasonText], e pelo mesmo motivo.
 */
@Composable
fun speechText(speech: Speech): String = phrase(speech.key, speech.args)

/**
 * Uma casa descrita: o nome e o que está nela, juntados pelo modelo do idioma.
 *
 * A junção acontece aqui e não no motor de propósito. "e4, peão branco" em português é
 * "e4, white pawn" em inglês, e nada garante que toda língua ponha as duas partes na mesma
 * ordem — o modelo traduzido é que decide.
 */
@Composable
fun squareSpeechText(square: SquareSpeech): String {
    val occupant = square.occupant
    return if (occupant == null) {
        phrase(SpeechKey.SQUARE_EMPTY, listOf(square.name))
    } else {
        phrase(SpeechKey.SQUARE_WITH, listOf(square.name, phrase(occupant, emptyList())))
    }
}

@Composable
private fun phrase(key: SpeechKey, args: List<String>): String {
    val context = LocalContext.current
    val resourceId = remember(key) {
        context.resources.getIdentifier(key.resourceName, "string", context.packageName)
    }
    return when {
        resourceId == 0 -> key.format(args)
        args.isEmpty() -> stringResource(resourceId)
        else -> stringResource(resourceId, *args.toTypedArray())
    }
}
