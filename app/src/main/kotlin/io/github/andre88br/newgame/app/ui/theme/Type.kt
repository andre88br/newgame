package io.github.andre88br.newgame.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografia padrão do Material com dois ajustes: títulos um pouco mais pesados, e um
 * estilo tabular para a notação dos lances, em que os números precisam alinhar.
 */
val NewgameTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
    )
}

/** Notação de lances (`24x15x6`), em fonte monoespaçada para as colunas baterem. */
val MoveNotationStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
)
