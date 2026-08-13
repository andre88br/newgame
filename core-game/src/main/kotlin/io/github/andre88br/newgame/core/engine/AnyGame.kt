package io.github.andre88br.newgame.core.engine

import kotlinx.serialization.json.Json

/** Formato usado para persistir estados e lances. Um só, para o disco e o histórico casarem. */
val GameJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Fachada sem parâmetros de tipo sobre um [BoardGame].
 *
 * A camada de aplicação guarda jogos numa lista e recebe estados vindos do banco, onde os
 * tipos concretos não sobrevivem. Em vez de espalhar casts por telas e repositórios, eles
 * ficam confinados aqui: quem consome trabalha com [GameState] e [Move].
 */
interface AnyGame {
    val id: GameId
    /** Veja [BoardGame.supportedSeats]. */
    val supportedSeats: IntRange

    /** Veja [BoardGame.seatsIn]. */
    fun seatsIn(state: GameState): Int

    fun initialState(config: MatchConfig): GameState

    fun legalMoves(state: GameState): List<Move>

    fun applyMove(state: GameState, move: Move): MoveResult<GameState>

    fun outcome(state: GameState): Outcome

    /** Veja [BoardGame.isCapture]. */
    fun isCapture(state: GameState, move: Move): Boolean

    /** Veja [BoardGame.hasHiddenInformation]. */
    val hasHiddenInformation: Boolean

    /** Veja [BoardGame.decidesWhoStarts]. */
    val decidesWhoStarts: Boolean

    fun redactFor(state: GameState, viewer: Seat): GameState

    /** Veja [BoardGame.repetitionKey]. */
    fun repetitionKey(state: GameState): String?

    fun encodeState(state: GameState): String

    fun decodeState(json: String): GameState

    fun encodeMove(move: Move): String

    fun decodeMove(json: String): Move
}

/** Como [BoardGame.applyOrThrow], para quem só tem a fachada sem genéricos. */
fun AnyGame.applyOrThrow(state: GameState, move: Move): GameState =
    when (val result = applyMove(state, move)) {
        is MoveResult.Ok -> result.state
        is MoveResult.Illegal -> error("Lance ilegal em $id: ${move.describe()} — ${result.reason}")
    }

/** Envolve um jogo tipado na fachada sem genéricos. */
fun <S : GameState, M : Move> BoardGame<S, M>.asAny(): AnyGame = TypedFacade(this)

private class TypedFacade<S : GameState, M : Move>(
    private val game: BoardGame<S, M>,
) : AnyGame {

    override val id: GameId get() = game.id
    override val supportedSeats: IntRange get() = game.supportedSeats

    override fun seatsIn(state: GameState): Int = game.seatsIn(state.typed())
    override val hasHiddenInformation: Boolean get() = game.hasHiddenInformation
    override val decidesWhoStarts: Boolean get() = game.decidesWhoStarts

    override fun initialState(config: MatchConfig): GameState = game.initialState(config)

    override fun legalMoves(state: GameState): List<Move> = game.legalMoves(state.typed())

    override fun applyMove(state: GameState, move: Move): MoveResult<GameState> =
        when (val result = game.applyMove(state.typed(), move.typed())) {
            is MoveResult.Ok -> MoveResult.Ok(result.state)
            is MoveResult.Illegal -> result
        }

    override fun outcome(state: GameState): Outcome = game.outcome(state.typed())

    override fun isCapture(state: GameState, move: Move): Boolean =
        game.isCapture(state.typed(), move.typed())

    override fun redactFor(state: GameState, viewer: Seat): GameState =
        game.redactFor(state.typed(), viewer)

    override fun repetitionKey(state: GameState): String? = game.repetitionKey(state.typed())

    override fun encodeState(state: GameState): String =
        GameJson.encodeToString(game.stateSerializer, state.typed())

    override fun decodeState(json: String): GameState =
        GameJson.decodeFromString(game.stateSerializer, json)

    override fun encodeMove(move: Move): String =
        GameJson.encodeToString(game.moveSerializer, move.typed())

    override fun decodeMove(json: String): Move =
        GameJson.decodeFromString(game.moveSerializer, json)

    // Passar o estado de um jogo para outro é erro de programação, não entrada inválida:
    // falha alto e cedo em vez de produzir um lance sem sentido.
    @Suppress("UNCHECKED_CAST")
    private fun GameState.typed(): S = this as S

    @Suppress("UNCHECKED_CAST")
    private fun Move.typed(): M = this as M
}
