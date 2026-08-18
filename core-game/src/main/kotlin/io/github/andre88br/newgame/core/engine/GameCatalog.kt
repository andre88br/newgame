package io.github.andre88br.newgame.core.engine

import io.github.andre88br.newgame.core.ai.AnyAi
import io.github.andre88br.newgame.core.ai.asAnyAi
import io.github.andre88br.newgame.core.games.checkers.CheckersAi
import io.github.andre88br.newgame.core.games.checkers.CheckersGame
import io.github.andre88br.newgame.core.games.checkers.CheckersInteractor
import io.github.andre88br.newgame.core.games.chess.ChessAi
import io.github.andre88br.newgame.core.games.chess.ChessGame
import io.github.andre88br.newgame.core.games.chess.ChessInteractor
import io.github.andre88br.newgame.core.games.canastra.CanastraAi
import io.github.andre88br.newgame.core.games.canastra.CanastraGame
import io.github.andre88br.newgame.core.games.dominoes.DominoesAi
import io.github.andre88br.newgame.core.games.dominoes.DominoesGame
import io.github.andre88br.newgame.core.games.hearts.HEARTS_SEATS
import io.github.andre88br.newgame.core.games.hearts.HeartsAi
import io.github.andre88br.newgame.core.games.hearts.HeartsGame
import io.github.andre88br.newgame.core.games.klondike.KlondikeAi
import io.github.andre88br.newgame.core.games.klondike.KlondikeGame
import io.github.andre88br.newgame.core.games.pife.PifeAi
import io.github.andre88br.newgame.core.games.pife.PifeGame
import io.github.andre88br.newgame.core.games.poker.PokerAi
import io.github.andre88br.newgame.core.games.poker.PokerGame
import io.github.andre88br.newgame.core.games.ludo.LudoAi
import io.github.andre88br.newgame.core.games.ludo.LudoGame
import io.github.andre88br.newgame.core.games.reversi.ReversiAi
import io.github.andre88br.newgame.core.games.reversi.ReversiGame
import io.github.andre88br.newgame.core.games.reversi.ReversiInteractor
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeAi
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeGame
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeInteractor
import io.github.andre88br.newgame.core.games.truco.TrucoAi
import io.github.andre88br.newgame.core.games.truco.TrucoGame
import io.github.andre88br.newgame.core.session.BoardInteractor

/** Um jogo pronto para a interface: as regras, o adversário do aparelho e como se chama. */
data class GameEntry(
    val rules: AnyGame,
    val ai: AnyAi,
    /**
     * Como os toques no tabuleiro viram lances, ou `null` quando o jogo não é jogado
     * tocando em casas de uma grade — o dominó se joga pela mão, o ludo pelos peões.
     * Nesses casos a tela do jogo monta o lance e o entrega pronto.
     */
    val interactor: BoardInteractor?,
    /** Chave de tradução do nome, resolvida nos recursos do app. */
    val nameKey: String,
    /** Quantas pessoas jogam de fato (o ludo aceita 2 a 4; aqui é o padrão). */
    val players: Int = 2,
    /**
     * Quanto a máquina demora, de propósito, entre um lance dela e o seguinte.
     *
     * Não é regra de jogo: é ritmo de tela, e por isso mora aqui e não no `BoardGame`. O
     * padrão dá tempo de ver um peão andar ou um dado rolar. Nos jogos de carta o valor é
     * menor porque um lance da máquina é uma carta caindo — e porque há muitos deles
     * seguidos: no passe da copas são nove antes de a mão sequer começar, e um segundo em
     * cada um viraria dez segundos de tela parada escrito "pensando".
     */
    val aiPaceMillis: Long = 1_200L,
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
        GameEntry(
            rules = ReversiGame.asAny(),
            ai = ReversiAi.asAnyAi(),
            interactor = ReversiInteractor,
            nameKey = "game_reversi",
        ),
        GameEntry(
            rules = ChessGame.asAny(),
            ai = ChessAi.asAnyAi(),
            interactor = ChessInteractor,
            nameKey = "game_chess",
        ),
        GameEntry(
            rules = DominoesGame.asAny(),
            ai = DominoesAi.asAnyAi(),
            // Dominó não se joga tocando em casas de uma grade: a tela tem caminho próprio.
            interactor = null,
            nameKey = "game_dominoes",
        ),
        GameEntry(
            rules = LudoGame.asAny(),
            ai = LudoAi.asAnyAi(),
            interactor = null,
            nameKey = "game_ludo",
        ),
        GameEntry(
            rules = HeartsGame.asAny(),
            ai = HeartsAi.asAnyAi(),
            // Jogo de carta: a mão é a interface, e a tela do jogo monta o lance.
            interactor = null,
            nameKey = "game_hearts",
            players = HEARTS_SEATS,
            aiPaceMillis = 400L,
        ),
        GameEntry(
            rules = CanastraGame.asAny(),
            ai = CanastraAi.asAnyAi(),
            interactor = null,
            nameKey = "game_canastra",
            // A vez da canastra tem três tempos, e a máquina joga vários lances seguidos
            // antes de passar a vez: um compasso longo em cada um viraria espera demais.
            aiPaceMillis = 350L,
        ),
        GameEntry(
            rules = PifeGame.asAny(),
            ai = PifeAi.asAnyAi(),
            interactor = null,
            nameKey = "game_pife",
            // Comprar e descartar são dois lances por vez: metade do compasso dos outros.
            aiPaceMillis = 500L,
        ),
        GameEntry(
            rules = KlondikeGame.asAny(),
            // Aqui a "IA" nunca joga: numa mesa de uma pessoa não há cadeira da máquina.
            // Ela existe só para a dica, que é o mesmo botão dos outros jogos.
            ai = KlondikeAi.asAnyAi(),
            interactor = null,
            nameKey = "game_klondike",
            players = 1,
        ),
        GameEntry(
            rules = TrucoGame.asAny(),
            ai = TrucoAi.asAnyAi(),
            interactor = null,
            nameKey = "game_truco",
            // Trucar, responder e jogar carta são lances curtos e seguidos: o compasso longo
            // dos outros jogos deixaria a resposta a um truco parecendo travamento.
            aiPaceMillis = 450L,
        ),
        GameEntry(
            rules = PokerGame.asAny(),
            ai = PokerAi.asAnyAi(),
            interactor = null,
            nameKey = "game_poker",
            // Passar, pagar e as próprias cartas caindo são vários lances por mão: o mesmo
            // motivo do truco para um compasso curto.
            aiPaceMillis = 450L,
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
