package io.github.andre88br.newgame.core.session

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MatchRecord
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Replay
import io.github.andre88br.newgame.core.engine.Seat

/** Quem ocupa uma cadeira da mesa. */
sealed interface Player {
    data object Human : Player

    data class Ai(val difficulty: Difficulty) : Player
}

/** Um lance já jogado, com quem o jogou. */
data class PlayedMove(
    /** Quantos lances vieram antes deste. */
    val ply: Int,
    val seat: Seat,
    val notation: String,
)

/** O que aconteceu com a tentativa de jogar. */
sealed interface PlayResult {
    data class Ok(val move: Move, val state: GameState) : PlayResult

    /** O lance existe mas não vale; [reason] já vem em português, pronto para a tela. */
    data class Rejected(val reason: String) : PlayResult

    /** Não é a vez de quem tocou (a IA está pensando, por exemplo). */
    data object NotYourTurn : PlayResult

    data object Finished : PlayResult
}

/**
 * Uma partida em andamento: quem joga em cada cadeira, o estado atual e o registro.
 *
 * É deliberadamente **síncrona**. A busca da IA é a única parte cara, e quem decide em que
 * thread rodá-la é o app — o que deixa esta classe testável sem corrotinas e sem relógio.
 *
 * O estado por dentro nunca é editado: cada lance passa por [Replay.append], que devolve um
 * registro novo. A sessão só troca a referência. É o que faz "fechar o app e voltar onde
 * parou" ser uma consequência do desenho, e não um recurso a mais.
 */
class MatchSession(
    val entry: GameEntry,
    val config: MatchConfig,
    val players: Map<Seat, Player>,
    record: MatchRecord = MatchRecord(entry.id, config),
) {

    init {
        require(record.gameId == entry.id) {
            "O registro é de ${record.gameId} e a sessão é de ${entry.id}"
        }
    }

    var record: MatchRecord = record
        private set

    var state: GameState = Replay.state(entry.rules, record)
        private set

    /**
     * Quantas vezes cada posição já apareceu nesta partida.
     *
     * Contado aqui, e não recalculado a cada consulta, porque `outcome` é lido várias vezes
     * por lance — uma vez por quadro da tela, no limite — e refazer a partida inteira toda
     * vez travaria a interface no meio de um jogo longo.
     */
    private val repetitions: MutableMap<String, Int> = countRepetitions(record)

    val outcome: Outcome
        get() {
            val declared = entry.rules.outcome(state)
            if (declared.isOver) return declared
            val key = entry.rules.repetitionKey(state)
            if (key != null && (repetitions[key] ?: 0) >= THREEFOLD) {
                return Outcome.Draw(DrawReason.REPETITION)
            }
            return declared
        }

    val isOver: Boolean get() = outcome.isOver

    val turn: Seat get() = state.turn

    /** Quem controla a cadeira da vez. Cadeira sem dono cai como humana. */
    val currentPlayer: Player get() = players[turn] ?: Player.Human

    /** A vez é da máquina: a tela deve mostrar "pensando" e chamar [playAiTurn]. */
    val awaitingAi: Boolean get() = !isOver && currentPlayer is Player.Ai

    /** Há lance humano para desfazer. */
    val canUndo: Boolean
        get() = record.ply > 0 && players.values.any { it is Player.Human }

    /**
     * Os lances já jogados, com quem os jogou.
     *
     * Mantido junto com a partida em vez de recalculado sob demanda: descobrir de quem foi
     * cada lance exige refazer o jogo do começo, e a tela pede este histórico a cada
     * atualização. Numa partida de xadrez longa, recalcular toda vez seria refazer centenas
     * de lances por quadro.
     */
    var history: List<PlayedMove> = rebuildHistory(this.record)
        private set

    /** Lances da partida em notação, do primeiro ao último. */
    fun notation(): List<String> = history.map { it.notation }

    /** Joga [move] pela pessoa sentada na cadeira da vez. */
    fun play(move: Move): PlayResult {
        if (isOver) return PlayResult.Finished
        if (currentPlayer !is Player.Human) return PlayResult.NotYourTurn
        return commit(move)
    }

    /**
     * Joga pela IA, se for a vez dela. Devolve o lance escolhido, ou `null` se não era a
     * vez da máquina ou a partida já acabou. Esta é a chamada cara: rode-a fora da thread
     * da interface.
     */
    fun playAiTurn(): Move? {
        val player = currentPlayer
        if (isOver || player !is Player.Ai) return null
        val move = entry.ai.chooseMove(state, player.difficulty, seedForCurrentPly()) ?: return null
        return when (commit(move)) {
            is PlayResult.Ok -> move
            else -> null
        }
    }

    /**
     * Sugere um lance para a cadeira da vez, sempre no nível difícil — uma dica fraca não
     * ajudaria ninguém. Não joga: só devolve.
     */
    fun hint(): Move? {
        if (isOver) return null
        return entry.ai.chooseMove(state, Difficulty.HARD, seedForCurrentPly())
    }

    /**
     * Volta a partida até a vez de uma pessoa.
     *
     * Contra a máquina isso significa desfazer dois lances, não um: voltar só o último
     * devolveria a vez para a IA, que jogaria de novo e daria a impressão de que o botão
     * não fez nada.
     */
    fun undo(): Boolean {
        if (!canUndo) return false
        var candidate = record
        do {
            candidate = Replay.undo(candidate, 1)
        } while (candidate.ply > 0 && players[Replay.state(entry.rules, candidate).turn] !is Player.Human)

        adopt(candidate)
        return true
    }

    /** Recomeça a partida do zero, mantendo jogadores e configuração. */
    fun restart() {
        adopt(MatchRecord(entry.id, config))
    }

    /** Substitui a partida pela guardada em [saved] — o caminho de volta do banco. */
    fun restoreFrom(saved: MatchRecord) {
        require(saved.gameId == entry.id) {
            "O registro é de ${saved.gameId} e a sessão é de ${entry.id}"
        }
        adopt(saved)
    }

    private fun commit(move: Move): PlayResult {
        val mover = state.turn
        val playedAt = state.ply
        return when (val result = entry.rules.applyMove(state, move)) {
            is MoveResult.Ok -> {
                state = result.state
                history = history + PlayedMove(playedAt, mover, move.describe())
                entry.rules.repetitionKey(result.state)?.let { key ->
                    repetitions[key] = (repetitions[key] ?: 0) + 1
                }
                record = record.copy(
                    moves = record.moves + entry.rules.encodeMove(move),
                    // `outcome` já leva a repetição em conta; `rules.outcome` não saberia.
                    outcome = outcome,
                )
                PlayResult.Ok(move, result.state)
            }

            is MoveResult.Illegal -> PlayResult.Rejected(result.reason)
        }
    }

    private fun adopt(newRecord: MatchRecord) {
        state = Replay.state(entry.rules, newRecord)
        repetitions.clear()
        repetitions.putAll(countRepetitions(newRecord))
        history = rebuildHistory(newRecord)
        record = newRecord.copy(outcome = outcome)
    }

    /**
     * Refaz o histórico a partir do registro. Só acontece ao desfazer, recomeçar ou retomar
     * uma partida salva — nunca durante o jogo, em que a lista só cresce de um em um.
     */
    private fun rebuildHistory(source: MatchRecord): List<PlayedMove> {
        val states = Replay.states(entry.rules, source)
        return source.moves.mapIndexed { index, encoded ->
            val before = states[index]
            PlayedMove(
                ply = before.ply,
                seat = before.turn,
                notation = entry.rules.decodeMove(encoded).describe(),
            )
        }
    }

    private fun countRepetitions(source: MatchRecord): MutableMap<String, Int> {
        val counts = HashMap<String, Int>()
        // Sem chave de repetição — a maioria dos jogos — nem vale percorrer o histórico.
        if (entry.rules.repetitionKey(entry.rules.initialState(config)) == null) return counts
        for (snapshot in Replay.states(entry.rules, source)) {
            val key = entry.rules.repetitionKey(snapshot) ?: continue
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts
    }

    /**
     * Semente da IA para o lance atual.
     *
     * Sai da semente da partida misturada com o número do lance: varia a cada jogada, para
     * o nível fácil não repetir sempre o mesmo erro, e ao mesmo tempo mantém a partida
     * reproduzível — reabrir um jogo salvo leva a máquina às mesmas escolhas.
     */
    private companion object {
        /** Três ocorrências da mesma posição empatam a partida. */
        const val THREEFOLD = 3
    }

    private fun seedForCurrentPly(): Long = config.seed * 0x9E3779B97F4A7C15uL.toLong() + state.ply
}
