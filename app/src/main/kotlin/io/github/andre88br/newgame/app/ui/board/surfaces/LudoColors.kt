package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.ui.graphics.Color

/**
 * A cor fixa de cada braço do ludo, na ordem dos braços: vermelho, azul, amarelo, verde.
 *
 * Fica num arquivo à parte porque tanto o tabuleiro (`LudoSurface`) quanto a tela de
 * configuração (`SetupScreen`, onde a pessoa escolhe a cor antes de começar) precisam da
 * mesma lista — duplicá-la deixaria as duas telas discordando assim que alguém mudasse uma
 * cor sem lembrar da outra.
 */
val LUDO_ARM_COLORS: List<Color> = listOf(
    Color(0xFFD93B3B), // vermelho
    Color(0xFF3E8FD9), // azul
    Color(0xFFE8C020), // amarelo
    Color(0xFF3FAF4A), // verde
)

/** A cor do braço [arm] (0 a 3). */
fun ludoArmColor(arm: Int): Color = LUDO_ARM_COLORS[arm % LUDO_ARM_COLORS.size]
