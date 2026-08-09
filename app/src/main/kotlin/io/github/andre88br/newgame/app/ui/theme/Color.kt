package io.github.andre88br.newgame.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta fixa do app, em vez das cores dinâmicas do Material You.
 *
 * A escolha é deliberada e específica de um app de jogos: o tabuleiro precisa de contraste
 * previsível entre casa clara, casa escura e as duas cores de peça. Herdar as cores do
 * papel de parede de cada aparelho tornaria isso impossível de garantir — em alguns
 * celulares as peças ficariam quase iguais ao fundo.
 */
object Palette {

    // Verde de mesa de jogo, usado como cor principal nos dois temas.
    val Green = Color(0xFF2F6B58)
    val GreenLight = Color(0xFF9FD3C0)
    val GreenDark = Color(0xFF16362C)

    // Vermelho das peças e das ações destrutivas.
    val Red = Color(0xFFD93B3B)
    val RedLight = Color(0xFFFFB4AB)
    val RedDark = Color(0xFF5C1414)

    val Amber = Color(0xFFC8862A)

    val LightBackground = Color(0xFFF7F2EA)
    val LightSurface = Color(0xFFFFFBF4)
    val LightSurfaceVariant = Color(0xFFE6DDCC)
    val LightOnSurface = Color(0xFF1E1B16)
    val LightOnSurfaceVariant = Color(0xFF4C4639)

    val DarkBackground = Color(0xFF14171A)
    val DarkSurface = Color(0xFF1C2024)
    val DarkSurfaceVariant = Color(0xFF2C3238)
    val DarkOnSurface = Color(0xFFE6E1D9)
    val DarkOnSurfaceVariant = Color(0xFFC5C0B6)
}

/**
 * Cores do tabuleiro, separadas do esquema do Material.
 *
 * Ficam à parte porque não são cores de interface: são as cores do jogo. Casa clara, casa
 * escura e peça precisam manter o contraste entre si independentemente do tema — misturá-las
 * com `MaterialTheme.colorScheme` acabaria fazendo o tabuleiro mudar quando não deve.
 */
data class BoardPalette(
    val lightSquare: Color,
    val darkSquare: Color,
    val border: Color,
    val firstPiece: Color,
    val firstPieceEdge: Color,
    val secondPiece: Color,
    val secondPieceEdge: Color,
    val crown: Color,
    val selection: Color,
    val hint: Color,
    val lastMove: Color,
    /** Marca de quem começa no jogo da velha (o X). */
    val markFirst: Color,
    /** Marca de quem joga depois (o O). */
    val markSecond: Color,
) {
    companion object {
        val Light = BoardPalette(
            lightSquare = Color(0xFFF0E2C8),
            darkSquare = Color(0xFF4E8A74),
            border = Color(0xFF2B4A3E),
            firstPiece = Color(0xFFFAF6EE),
            firstPieceEdge = Color(0xFF9A8F7A),
            secondPiece = Color(0xFF23272B),
            secondPieceEdge = Color(0xFF000000),
            crown = Palette.Amber,
            selection = Color(0xFFFFC85C),
            hint = Color(0xFF4C9BE8),
            lastMove = Color(0x553D8BFF),
            markFirst = Color(0xFF1F5C4A),
            markSecond = Color(0xFFC02F2F),
        )

        val Dark = BoardPalette(
            lightSquare = Color(0xFFCBBB9B),
            darkSquare = Color(0xFF32604F),
            border = Color(0xFF16302A),
            firstPiece = Color(0xFFEFE9DD),
            firstPieceEdge = Color(0xFF8A8172),
            secondPiece = Color(0xFF15181B),
            secondPieceEdge = Color(0xFF000000),
            crown = Palette.Amber,
            selection = Color(0xFFFFC85C),
            hint = Color(0xFF6FB4F2),
            lastMove = Color(0x553D8BFF),
            markFirst = Color(0xFF2C7A62),
            markSecond = Color(0xFFE05656),
        )
    }
}
