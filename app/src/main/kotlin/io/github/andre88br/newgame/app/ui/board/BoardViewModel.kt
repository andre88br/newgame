package io.github.andre88br.newgame.app.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.andre88br.newgame.app.data.MatchStore
import io.github.andre88br.newgame.app.data.SavedMatch
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.session.MatchSession
import io.github.andre88br.newgame.core.session.PlayResult
import io.github.andre88br.newgame.core.session.Player
import io.github.andre88br.newgame.core.session.TapResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** De quem é a vez, em termos que a tela consegue traduzir para texto. */
sealed interface BoardStatus {
    data object HumanTurn : BoardStatus

    data object Thinking : BoardStatus

    /** Passa-e-joga: é a vez de uma das duas pessoas. */
    data class SeatTurn(val seat: Seat) : BoardStatus

    data class Finished(val outcome: Outcome) : BoardStatus
}

/**
 * Aviso passageiro para a tela mostrar.
 *
 * O ViewModel não monta texto de interface: devolve o que aconteceu, e a tela resolve os
 * recursos. O único texto que atravessa pronto é o motivo de recusa, que vem do motor já
 * escrito para ser lido por gente ("Captura é obrigatória: …").
 */
sealed interface BoardMessage {
    data class Reason(val text: String) : BoardMessage

    data class Hint(val notation: String) : BoardMessage

    data object NoHint : BoardMessage
}

data class BoardUiState(
    val state: GameState,
    val status: BoardStatus,
    val selected: Int? = null,
    val hinted: Set<Int> = emptySet(),
    val lastMove: Set<Int> = emptySet(),
    val canUndo: Boolean = false,
    val canPlay: Boolean = true,
    val againstPhone: Boolean = true,
    val humanSeat: Seat? = null,
    val message: BoardMessage? = null,
    /** Cresce a cada aviso novo, para dois avisos iguais seguidos aparecerem duas vezes. */
    val messageId: Long = 0L,
)

/**
 * Liga a tela do tabuleiro à [MatchSession].
 *
 * Nenhuma regra de jogo mora aqui — a sessão e o interator, que vivem no `core-game` e são
 * testados lá, cuidam disso. O que este ViewModel faz é o que só pode ser feito no
 * Android: tirar a busca da IA da thread da interface e salvar a partida a cada lance.
 */
class BoardViewModel(
    private val entry: GameEntry,
    private val store: MatchStore,
    private val matchId: String,
    private val session: MatchSession,
) : ViewModel() {

    private var lastMoveSquares: Set<Int> = emptySet()
    private var messageCounter = 0L

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<BoardUiState> = _ui.asStateFlow()

    init {
        // A IA pode ocupar a primeira cadeira: nesse caso ela abre a partida sozinha.
        maybePlayAiTurn()
    }

    fun onSquareTap(square: Int) {
        if (session.isOver || session.awaitingAi || _ui.value.status == BoardStatus.Thinking) return

        when (val result = entry.interactor.tap(session.state, _ui.value.selected, square)) {
            is TapResult.Play -> commitHumanMove(result.move)
            is TapResult.Select -> _ui.value = snapshot(selected = result.square)
            TapResult.Deselect -> _ui.value = snapshot(selected = null)
            is TapResult.Rejected -> _ui.value = snapshot(
                selected = _ui.value.selected,
                message = BoardMessage.Reason(result.reason),
            )

            TapResult.Ignored -> Unit
        }
    }

    fun onUndo() {
        if (_ui.value.status == BoardStatus.Thinking) return
        if (session.undo()) {
            lastMoveSquares = emptySet()
            _ui.value = snapshot()
            persist()
        }
    }

    fun onRestart() {
        if (_ui.value.status == BoardStatus.Thinking) return
        session.restart()
        lastMoveSquares = emptySet()
        _ui.value = snapshot()
        persist()
        maybePlayAiTurn()
    }

    /** Roda a busca no nível difícil e destaca o lance sugerido, sem jogá-lo. */
    fun onHint() {
        if (session.isOver || session.awaitingAi) return
        _ui.value = snapshot(selected = _ui.value.selected, status = BoardStatus.Thinking)

        viewModelScope.launch {
            val suggestion = withContext(Dispatchers.Default) { session.hint() }
            _ui.value = if (suggestion == null) {
                snapshot(message = BoardMessage.NoHint)
            } else {
                snapshot(
                    hinted = entry.interactor.squaresOf(suggestion).toSet(),
                    message = BoardMessage.Hint(suggestion.describe()),
                )
            }
        }
    }

    private fun commitHumanMove(move: Move) {
        when (val result = session.play(move)) {
            is PlayResult.Ok -> {
                lastMoveSquares = entry.interactor.squaresOf(result.move).toSet()
                _ui.value = snapshot()
                persist()
                maybePlayAiTurn()
            }

            is PlayResult.Rejected ->
                _ui.value = snapshot(message = BoardMessage.Reason(result.reason))
            PlayResult.NotYourTurn, PlayResult.Finished -> Unit
        }
    }

    private fun maybePlayAiTurn() {
        if (!session.awaitingAi) return
        _ui.value = snapshot(status = BoardStatus.Thinking)

        viewModelScope.launch {
            // A busca do nível difícil leva segundos: fora da thread da interface, sempre.
            val move = withContext(Dispatchers.Default) { session.playAiTurn() }
            if (move != null) {
                lastMoveSquares = entry.interactor.squaresOf(move).toSet()
            }
            _ui.value = snapshot()
            persist()
        }
    }

    private fun persist() {
        val record = session.record
        viewModelScope.launch {
            store.put(
                SavedMatch(
                    id = matchId,
                    record = record,
                    humanSeats = session.players
                        .filterValues { it is Player.Human }
                        .keys.map { it.index }
                        .sorted(),
                    difficulty = session.players.values
                        .filterIsInstance<Player.Ai>()
                        .firstOrNull()?.difficulty,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun snapshot(
        selected: Int? = null,
        hinted: Set<Int> = emptySet(),
        message: BoardMessage? = null,
        status: BoardStatus? = null,
    ): BoardUiState {
        val humanSeats = session.players.filterValues { it is Player.Human }.keys
        val outcome = session.outcome
        val resolvedStatus = status ?: when {
            outcome.isOver -> BoardStatus.Finished(outcome)
            session.awaitingAi -> BoardStatus.Thinking
            humanSeats.size > 1 -> BoardStatus.SeatTurn(session.turn)
            else -> BoardStatus.HumanTurn
        }

        return BoardUiState(
            state = session.state,
            status = resolvedStatus,
            selected = selected,
            hinted = hinted,
            lastMove = lastMoveSquares,
            canUndo = session.canUndo,
            canPlay = !outcome.isOver && resolvedStatus != BoardStatus.Thinking,
            againstPhone = humanSeats.size == 1,
            humanSeat = humanSeats.singleOrNull(),
            message = message,
            // Só o contador entra aqui, nunca `_ui.value`: este método monta o estado
            // inicial do próprio `_ui`, e ler o campo durante a sua própria inicialização
            // derrubaria o app ao abrir o tabuleiro.
            messageId = if (message == null) messageCounter else ++messageCounter,
        )
    }

    /**
     * Cria o ViewModel com a partida já montada.
     *
     * Monta a sessão fora do ViewModel para que retomar uma partida salva e começar uma
     * nova sejam o mesmo caminho: as duas coisas são só um [MatchSession] diferente.
     */
    class Factory(
        private val entry: GameEntry,
        private val store: MatchStore,
        private val matchId: String,
        private val session: MatchSession,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BoardViewModel(entry, store, matchId, session) as T
    }

    companion object {
        /** Monta a sessão de uma partida nova. */
        fun newSession(
            entry: GameEntry,
            againstPhone: Boolean,
            difficulty: Difficulty,
            humanSeat: Seat,
        ): MatchSession {
            val players = if (againstPhone) {
                mapOf(
                    humanSeat to Player.Human,
                    Seat(1 - humanSeat.index) to Player.Ai(difficulty),
                )
            } else {
                mapOf(Seat.FIRST to Player.Human, Seat.SECOND to Player.Human)
            }
            return MatchSession(entry, MatchConfig.random(), players)
        }

        /** Retoma a partida guardada. */
        fun resumedSession(entry: GameEntry, saved: SavedMatch): MatchSession {
            val players = buildMap {
                for (index in 0 until entry.rules.seatCount) {
                    val seat = Seat(index)
                    put(
                        seat,
                        if (index in saved.humanSeats) {
                            Player.Human
                        } else {
                            Player.Ai(saved.difficulty ?: Difficulty.MEDIUM)
                        },
                    )
                }
            }
            return MatchSession(entry, saved.record.config, players, saved.record)
        }
    }
}
