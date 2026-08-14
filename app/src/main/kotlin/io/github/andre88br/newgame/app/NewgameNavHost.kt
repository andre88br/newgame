package io.github.andre88br.newgame.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.andre88br.newgame.app.ui.board.BoardScreen
import io.github.andre88br.newgame.app.ui.board.BoardViewModel
import io.github.andre88br.newgame.app.ui.history.HistoryScreen
import io.github.andre88br.newgame.app.ui.home.HomeScreen
import io.github.andre88br.newgame.app.ui.settings.SettingsScreen
import io.github.andre88br.newgame.app.ui.setup.MatchMode
import io.github.andre88br.newgame.app.ui.setup.MatchSetup
import io.github.andre88br.newgame.app.ui.setup.SetupScreen
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.GameCatalog
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.Seat
import java.util.UUID

private object Routes {
    const val HOME = "home"
    const val SETUP = "setup/{gameId}"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    /**
     * Uma rota só para partida nova e partida retomada: com `matchId` preenchido,
     * continua-se de onde parou; vazio, começa-se do zero. Os dois casos acabam num
     * `MatchSession`, então não vale a pena separar as telas.
     */
    const val BOARD = "board/{gameId}?matchId={matchId}&mode={mode}&difficulty={difficulty}" +
        "&humanSeat={humanSeat}&seats={seats}&ludoFirstArm={ludoFirstArm}&names={names}"

    fun setup(gameId: GameId) = "setup/${gameId.name}"

    fun newMatch(gameId: GameId, setup: MatchSetup) =
        "board/${gameId.name}?matchId=&mode=${setup.mode.name}" +
            "&difficulty=${setup.difficulty.name}&humanSeat=${setup.humanSeat.index}" +
            "&seats=${setup.seats}&ludoFirstArm=${setup.ludoFirstArm}" +
            "&names=${encodeNames(setup.names)}"

    // Ao retomar, o tamanho da mesa, a cor e os nomes vêm do registro salvo: o que estiver
    // aqui é ignorado.
    fun resumeMatch(gameId: GameId, matchId: String) =
        "board/${gameId.name}?matchId=$matchId&mode=${MatchMode.AGAINST_PHONE.name}" +
            "&difficulty=${Difficulty.MEDIUM.name}&humanSeat=0&seats=2&ludoFirstArm=0&names="
}

/**
 * Os nomes das cadeiras dentro de uma rota.
 *
 * A barra vertical separa um nome do outro, e o porcento é o que a codificação de URL usa —
 * os dois saem do nome antes de entrar aqui. Com eles fora, decodificar de novo do outro
 * lado devolve exatamente o que entrou, mesmo que a biblioteca de navegação já tenha
 * decodificado por conta própria; é o que faz um nome com acento ou espaço chegar inteiro.
 */
private const val NAME_SEPARATOR = "|"

private fun encodeNames(names: List<String>): String =
    Uri.encode(names.joinToString(NAME_SEPARATOR) { it.replace(NAME_SEPARATOR, " ").replace("%", "") })

private fun decodeNames(raw: String?): List<String> =
    Uri.decode(raw.orEmpty()).split(NAME_SEPARATOR).filter { it.isNotBlank() }

@Composable
fun NewgameNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                store = container.matchStore,
                animationsEnabled = container.preferences.settings.value.animations,
                onPlay = { entry -> navController.navigate(Routes.setup(entry.id)) },
                onResume = { gameId, matchId ->
                    navController.navigate(Routes.resumeMatch(gameId, matchId))
                },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.SETUP,
            arguments = listOf(navArgument("gameId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments.gameId()
            val entry = GameCatalog.entry(gameId)
            val settings = container.preferences.settings

            SetupScreen(
                entry = entry,
                defaultDifficulty = settings.value.defaultDifficulty,
                savedPlayerName = settings.value.playerName,
                hasOngoingMatch = container.matchStore.ongoing(gameId) != null,
                onBack = { navController.popBackStack() },
                onStart = { setup ->
                    // O nome digitado fica guardado para a próxima partida já vir
                    // preenchida — e só o digitado: "Você" não é escolha de ninguém.
                    if (setup.typedOwnName.isNotBlank()) {
                        container.preferences.setPlayerName(setup.typedOwnName)
                    }
                    navController.navigate(Routes.newMatch(gameId, setup)) {
                        // Terminada a configuração, voltar da partida deve levar ao menu,
                        // e não de volta a esta tela.
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.BOARD,
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("matchId") { type = NavType.StringType; defaultValue = "" },
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = MatchMode.AGAINST_PHONE.name
                },
                navArgument("difficulty") {
                    type = NavType.StringType
                    defaultValue = Difficulty.MEDIUM.name
                },
                navArgument("seats") { type = NavType.StringType; defaultValue = "2" },
                navArgument("humanSeat") { type = NavType.IntType; defaultValue = 0 },
                navArgument("ludoFirstArm") { type = NavType.IntType; defaultValue = 0 },
                navArgument("names") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            val gameId = arguments.gameId()
            val entry = GameCatalog.entry(gameId)

            val savedId = arguments?.getString("matchId").orEmpty()
            val saved = savedId.takeIf { it.isNotEmpty() }?.let(container.matchStore::find)

            // A sessão é criada uma vez por entrada na tela: girar o aparelho ou recompor
            // não pode recomeçar a partida.
            val matchId = remember(savedId) { saved?.id ?: UUID.randomUUID().toString() }

            // Retomando, os nomes são os que a partida já tinha; começando, os que vieram da
            // tela de configuração. Partida salva por uma versão sem nomes volta sem nome
            // nenhum, e a tela cai nos rótulos antigos ("jogador 1") em vez de ficar vazia.
            val names = remember(matchId) {
                saved?.playerNames?.takeIf { it.isNotEmpty() }
                    ?: decodeNames(arguments?.getString("names"))
            }

            val session = remember(matchId) {
                if (saved != null) {
                    BoardViewModel.resumedSession(entry, saved)
                } else {
                    BoardViewModel.newSession(
                        entry = entry,
                        againstPhone = arguments?.getString("mode") == MatchMode.AGAINST_PHONE.name,
                        difficulty = arguments?.getString("difficulty").toDifficulty(),
                        humanSeat = Seat(arguments?.getInt("humanSeat") ?: 0),
                        seats = arguments?.getString("seats")?.toIntOrNull() ?: 2,
                        ludoFirstArm = arguments?.getInt("ludoFirstArm") ?: 0,
                    )
                }
            }

            // Som, vibração e animação são preferências, e mudá-las nos ajustes precisa
            // valer na próxima partida sem reabrir o app.
            val settings by container.preferences.settings.collectAsState()

            BoardScreen(
                entry = entry,
                viewModel = viewModel(
                    factory = BoardViewModel.Factory(
                        entry = entry,
                        store = container.matchStore,
                        matchId = matchId,
                        session = session,
                        names = names,
                    ),
                ),
                settings = settings,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                store = container.matchStore,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                preferences = container.preferences,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun android.os.Bundle?.gameId(): GameId {
    val name = this?.getString("gameId")
    return GameId.entries.firstOrNull { it.name == name }
        ?: error("Rota sem jogo válido: $name")
}

private fun String?.toDifficulty(): Difficulty =
    Difficulty.entries.firstOrNull { it.name == this } ?: Difficulty.MEDIUM
