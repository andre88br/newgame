package io.github.andre88br.newgame.core.engine

import io.github.andre88br.newgame.core.ai.AnyAi
import io.github.andre88br.newgame.core.ai.asAnyAi
import io.github.andre88br.newgame.core.games.checkers.CheckersAi
import io.github.andre88br.newgame.core.games.checkers.CheckersGame
import io.github.andre88br.newgame.core.games.checkers.CheckersInteractor
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeAi
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeGame
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeInteractor
import io.github.andre88br.newgame.core.session.BoardInteractor

/** Um jogo pronto para a interface: as regras, o adversário do aparelho e como se chama. */
data class GameEntry(
    val rules: AnyGame,
    val ai: AnyAi,
    /** Como os toques no tabuleiro viram lances. */
    val interactor: BoardInteractor,
    /** Chave de tradução do nome, resolvida nos recursos do app. */
    val nameKey: String,
    /** Quantas pessoas jogam de fato (o ludo aceita 2 a 4; aqui é o padrão). */
    val players: Int = 2,
) {
    val id: GameId get() = rules.id
}

/**
 * Tudo o que o app oferece. Acrescentar um jogo é acrescentar uma linha aqui — nenhuma
 * tela precisa saber quais jogos existem.
 */
object GameCatalog {

    private val entries: List<GameEntry> = listOf(
        GameEntry(
            rules = TicTacToeGame.asAny(),
            ai = TicTacToeAi.asAnyAi(),
            interactor = TicTacToeInteractor,
            nameKey = "game_tic_tac_toe",
        ),
        GameEntry(
            rules = CheckersGame.asAny(),
            ai = CheckersAi.asAnyAi(),
            interactor = CheckersInteractor,
            nameKey = "game_checkers",
        ),
    )

    private val byId: Map<GameId, GameEntry> = entries.associateBy { it.id }

    /** Na ordem em que aparecem na tela inicial. */
    val available: List<GameEntry> get() = entries

    fun entry(id: GameId): GameEntry =
        byId[id] ?: error("Jogo $id ainda não foi implementado")

    fun rules(id: GameId): AnyGame = entry(id).rules

    fun ai(id: GameId): AnyAi = entry(id).ai

    fun contains(id: GameId): Boolean = id in byId
}
