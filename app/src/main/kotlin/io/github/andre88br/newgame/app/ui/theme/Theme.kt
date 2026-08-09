package io.github.andre88br.newgame.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Preferência de tema escolhida nos ajustes. */
enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK,
}

private val LightColors = lightColorScheme(
    primary = Palette.Green,
    onPrimary = Color.White,
    primaryContainer = Palette.GreenLight,
    onPrimaryContainer = Palette.GreenDark,
    secondary = Palette.Amber,
    onSecondary = Color.White,
    error = Palette.Red,
    onError = Color.White,
    background = Palette.LightBackground,
    onBackground = Palette.LightOnSurface,
    surface = Palette.LightSurface,
    onSurface = Palette.LightOnSurface,
    surfaceVariant = Palette.LightSurfaceVariant,
    onSurfaceVariant = Palette.LightOnSurfaceVariant,
)

private val DarkColors = darkColorScheme(
    primary = Palette.GreenLight,
    onPrimary = Palette.GreenDark,
    primaryContainer = Palette.Green,
    onPrimaryContainer = Color.White,
    secondary = Palette.Amber,
    onSecondary = Color.Black,
    error = Palette.RedLight,
    onError = Palette.RedDark,
    background = Palette.DarkBackground,
    onBackground = Palette.DarkOnSurface,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkOnSurface,
    surfaceVariant = Palette.DarkSurfaceVariant,
    onSurfaceVariant = Palette.DarkOnSurfaceVariant,
)

/**
 * Dá às telas do tabuleiro acesso às cores do jogo sem precisar passá-las de composable em
 * composable até lá embaixo.
 */
val LocalBoardPalette = staticCompositionLocalOf { BoardPalette.Light }

@Composable
fun NewgameTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }

    CompositionLocalProvider(
        LocalBoardPalette provides if (dark) BoardPalette.Dark else BoardPalette.Light,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = NewgameTypography,
            content = content,
        )
    }
}
