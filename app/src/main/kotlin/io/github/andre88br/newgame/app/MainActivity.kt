package io.github.andre88br.newgame.app

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.andre88br.newgame.app.ui.theme.NewgameTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Antes do super, e antes do setContent: é o que troca o tema de abertura pelo tema
        // do app no momento certo. Chamar depois deixaria a janela com o fundo errado.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = appContainer

        // A abertura sai subindo e diminuindo, como se o app entrasse por baixo dela. Sem
        // isto ela some de um quadro para o outro, e o corte aparece.
        //
        // Respeita o ajuste de animações: desligado, a abertura simplesmente sai.
        if (container.preferences.settings.value.animations) {
            splash.setOnExitAnimationListener { screen ->
                val icon = screen.iconView
                val subir = ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, -icon.height.toFloat())
                val sumir = ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0f)

                listOf(subir, sumir).forEach {
                    it.interpolator = AnticipateInterpolator()
                    it.duration = EXIT_MILLIS
                }
                // Tirar a tela de abertura é responsabilidade de quem assume a saída: sem o
                // `remove`, ela fica pendurada por cima do app para sempre.
                sumir.doOnEnd { screen.remove() }
                subir.start()
                sumir.start()
            }
        }

        setContent {
            val settings by container.preferences.settings.collectAsState()

            NewgameTheme(choice = settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NewgameNavHost(container)
                }
            }
        }
    }

    private companion object {
        /** Curto de propósito: abertura é espera, e espera longa vira incômodo. */
        const val EXIT_MILLIS = 320L
    }
}
