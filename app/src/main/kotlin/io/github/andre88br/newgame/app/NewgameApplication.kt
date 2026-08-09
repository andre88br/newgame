package io.github.andre88br.newgame.app

import android.app.Application
import android.content.Context
import io.github.andre88br.newgame.app.data.AppPreferences
import io.github.andre88br.newgame.app.data.MatchStore
import java.io.File

/**
 * As duas dependências do app, criadas na mão.
 *
 * São só duas, e nenhuma precisa de escopo ou ciclo de vida especial — uma biblioteca de
 * injeção aqui seria mais configuração do que benefício.
 */
class AppContainer(context: Context) {
    val preferences: AppPreferences = AppPreferences(context)
    val matchStore: MatchStore = MatchStore(File(context.filesDir, "matches.json"))
}

class NewgameApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Recupera o container a partir de qualquer `Context`. */
val Context.appContainer: AppContainer
    get() = (applicationContext as NewgameApplication).container
