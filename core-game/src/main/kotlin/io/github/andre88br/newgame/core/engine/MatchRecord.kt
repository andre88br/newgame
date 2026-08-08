package io.github.andre88br.newgame.core.engine

import kotlinx.serialization.Serializable

/**
 * Uma partida guardada como semente + lista de lances, e não como um tabuleiro congelado.
 *
 * Formato compacto (um registro de xadrez inteiro cabe em poucos KB), permite desfazer
 * lance, refazer a partida do começo e revisar o histórico. Como `applyMove` é puro e a
 * aleatoriedade sai de [MatchConfig.seed], reproduzir a lista devolve exatamente o mesmo
 * estado — em qualquer aparelho.
 */
@Serializable
data class MatchRecord(
    val gameId: GameId,
    val config: MatchConfig,
    /** Lances em JSON, na ordem em que foram jogados. */
    val moves: List<String> = emptyList(),
    val outcome: Outcome = Outcome.InProgress,
) {
    val ply: Int get() = moves.size
}

/** Erro ao reproduzir uma partida gravada: o registro não bate com as regras do jogo. */
class ReplayException(message: String) : IllegalStateException(message)

object Replay {

    /** Estado final de [record], reproduzindo os lances a partir do estado inicial. */
    fun state(game: AnyGame, record: MatchRecord): GameState =
        states(game, record).last()

    /**
     * Todos os estados por que a partida passou, do inicial ao atual.
     * Tem `moves.size + 1` elementos — é o que a tela usa para desfazer e navegar.
     */
    fun states(game: AnyGame, record: MatchRecord): List<GameState> {
        require(game.id == record.gameId) {
            "Registro é de ${record.gameId}, mas o jogo informado é ${game.id}"
        }
        val history = ArrayList<GameState>(record.moves.size + 1)
        var current = game.initialState(record.config)
        history += current
        record.moves.forEachIndexed { index, encoded ->
            val move = try {
                game.decodeMove(encoded)
            } catch (e: Exception) {
                throw ReplayException("Lance ${index + 1} de ${record.gameId} não pôde ser lido: $encoded")
            }
            when (val result = game.applyMove(current, move)) {
                is MoveResult.Ok -> current = result.state
                is MoveResult.Illegal ->
                    throw ReplayException(
                        "Lance ${index + 1} (${move.describe()}) é ilegal ao reproduzir " +
                            "${record.gameId}: ${result.reason}",
                    )
            }
            history += current
        }
        return history
    }

    /** Acrescenta um lance ao registro, já atualizando o resultado da partida. */
    fun append(game: AnyGame, record: MatchRecord, state: GameState, move: Move): MatchRecord {
        val next = when (val result = game.applyMove(state, move)) {
            is MoveResult.Ok -> result.state
            is MoveResult.Illegal -> throw ReplayException("Lance ilegal: ${result.reason}")
        }
        return record.copy(
            moves = record.moves + game.encodeMove(move),
            outcome = game.outcome(next),
        )
    }

    /** Desfaz os [count] últimos lances (usado pelo botão de voltar jogada). */
    fun undo(record: MatchRecord, count: Int = 1): MatchRecord {
        val keep = (record.moves.size - count).coerceAtLeast(0)
        return record.copy(moves = record.moves.take(keep), outcome = Outcome.InProgress)
    }
}
