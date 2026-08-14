package io.github.andre88br.newgame.app.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.andre88br.newgame.app.data.MatchStore
import io.github.andre88br.newgame.app.data.SavedMatch
import io.github.andre88br.newgame.app.ui.feedback.GameEvent
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Reason
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.session.MatchSession
import io.github.andre88br.newgame.core.session.PlayResult
import io.github.andre88br.newgame.core.session.PlayedMove
import io.github.andre88br.newgame.core.session.Player
import io.github.andre88br.newgame.core.session.PromotionChoice
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
 * recursos. Nem o motivo de recusa escapa disso — ele viaja como chave, e quem escolhe o
 * idioma é a tela.
 */
sealed interface BoardMessage {
    /** Lance ou toque recusado; o motivo vem em chave, e a tela escolhe o idioma. */
    data class Rejected(val reason: Reason) : BoardMessage

    data class Hint(val notation: String) : BoardMessage

    data object NoHint : BoardMessage
}

/** Promoção esperando escolha. A tela mostra o diálogo enquanto isto não for nulo. */
data class PendingPromotion(val square: Int, val choices: List<PromotionChoice>)

data class BoardUiState(
    /**
     * O tabuleiro como quem está olhando pode vê-lo.
     *
     * No dominó isso não é detalhe: o estado completo traz a mão do adversário, e mandá-lo
     * para a tela entregaria o jogo mesmo que nada o desenhasse — bastaria um `toString` no
     * lugar errado. A tela recebe só o que é de direito.
     */
    val state: GameState,
    val status: BoardStatus,
    val selected: Int? = null,
    /** Lances já jogados, do primeiro ao último. */
    val history: List<PlayedMove> = emptyList(),
    val promotion: PendingPromotion? = null,
    val hinted: Set<Int> = emptySet(),
    val lastMove: Set<Int> = emptySet(),
    /** O lance sugerido pela dica, para as telas que não desenham grade. */
    val hintedMove: Move? = null,
    /** O último lance jogado, pelo mesmo motivo. */
    val lastPlayed: Move? = null,
    /** De quem é o ponto de vista de [state]. */
    val viewer: Seat = Seat.FIRST,
    val canUndo: Boolean = false,
    val canPlay: Boolean = true,
    val againstPhone: Boolean = true,
    val humanSeat: Seat? = null,
    val message: BoardMessage? = null,
    /** Cresce a cada aviso novo, para dois avisos iguais seguidos aparecerem duas vezes. */
    val messageId: Long = 0L,
    /** O que acabou de acontecer, para a tela dar som e vibração. */
    val event: GameEvent? = null,
    /** Cresce a cada evento, pelo mesmo motivo de [messageId]. */
    val eventId: Long = 0L,
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
    private var lastPlayedMove: Move? = null
    private var messageCounter = 0L
    private var pendingEvent: GameEvent? = null
    private var eventCounter = 0L

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<BoardUiState> = _ui.asStateFlow()

    init {
        // A IA pode ocupar a primeira cadeira: nesse caso ela abre a partida sozinha.
        maybePlayAiTurn()
    }

    fun onSquareTap(square: Int) {
        val interactor = entry.interactor ?: return
        if (session.isOver || session.awaitingAi || _ui.value.status == BoardStatus.Thinking) return
        // Com o diálogo de promoção aberto, o tabuleiro não responde: o lance está no meio.
        if (_ui.value.promotion != null) return

        when (val result = interactor.tap(session.state, _ui.value.selected, square)) {
            is TapResult.Play -> commitHumanMove(result.move)
            is TapResult.Select -> _ui.value = snapshot(selected = result.square)
            TapResult.Deselect -> _ui.value = snapshot(selected = null)
            is TapResult.Rejected -> _ui.value = snapshot(
                selected = _ui.value.selected,
                message = BoardMessage.Rejected(result.reason),
            )

            is TapResult.ChoosePromotion -> _ui.value = snapshot(
                selected = _ui.value.selected,
                promotion = PendingPromotion(result.to, result.choices),
            )

            TapResult.Ignored -> Unit
        }
    }

    /**
     * Joga um lance montado pela própria tela.
     *
     * O dominó e o ludo não se jogam tocando em casas de uma grade: a mão e os peões têm
     * telas próprias, que entregam o lance pronto. A validação continua sendo do motor —
     * esta porta não confia no que recebe, só encaminha.
     */
    fun onMoveChosen(move: Move) {
        if (session.isOver || session.awaitingAi || _ui.value.status == BoardStatus.Thinking) return
        commitHumanMove(move)
    }

    /** A pessoa escolheu a peça no diálogo de promoção. */
    fun onPromotionChosen(choice: PromotionChoice) {
        _ui.value = snapshot(selected = _ui.value.selected)
        commitHumanMove(choice.move)
    }

    /** Fechou o diálogo sem escolher: a peça continua selecionada, nada foi jogado. */
    fun onPromotionCancelled() {
        _ui.value = snapshot(selected = _ui.value.selected)
    }

    fun onUndo() {
        if (_ui.value.status == BoardStatus.Thinking) return
        if (session.undo()) {
            lastMoveSquares = emptySet()
            lastPlayedMove = null
            _ui.value = snapshot()
            persist()
        }
    }

    fun onRestart() {
        if (_ui.value.status == BoardStatus.Thinking) return
        session.restart()
        lastMoveSquares = emptySet()
        lastPlayedMove = null
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
                    hinted = entry.interactor?.squaresOf(suggestion).orEmpty().toSet(),
                    hintedMove = suggestion,
                    message = BoardMessage.Hint(suggestion.describe()),
                )
            }
        }
    }

    private fun commitHumanMove(move: Move) {
        val before = session.state
        when (val result = session.play(move)) {
            is PlayResult.Ok -> {
                lastMoveSquares = entry.interactor?.squaresOf(result.move).orEmpty().toSet()
                lastPlayedMove = result.move
                noteEvent(before, result.move)
                _ui.value = snapshot()
                persist()
                maybePlayAiTurn()
            }

            is PlayResult.Rejected ->
                _ui.value = snapshot(message = BoardMessage.Rejected(result.reason))
            PlayResult.NotYourTurn, PlayResult.Finished -> Unit
        }
    }

    private fun maybePlayAiTurn() {
        if (!session.awaitingAi) return
        _ui.value = snapshot(status = BoardStatus.Thinking)

        viewModelScope.launch {
            // A busca do nível difícil leva segundos: fora da thread da interface, sempre.
            val before = session.state
            val move = withContext(Dispatchers.Default) { session.playAiTurn() }
            if (move != null) {
                lastMoveSquares = entry.interactor?.squaresOf(move).orEmpty().toSet()
                lastPlayedMove = move
                noteEvent(before, move)
            }
            _ui.value = snapshot()
            persist()
        }
    }

    /**
     * Classifica o lance para o retorno da tela.
     *
     * A pergunta "foi captura?" é feita sobre o estado **anterior** ao lance: depois de
     * aplicado, a peça capturada já não está lá para ser contada.
     */
    private fun noteEvent(before: GameState, move: Move) {
        pendingEvent = when {
            session.isOver -> GameEvent.FINISH
            entry.rules.isCapture(before, move) -> GameEvent.CAPTURE
            else -> GameEvent.MOVE
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

    /**
     * De quem é o ponto de vista mostrado na tela.
     *
     * Contra o celular é sempre a pessoa, inclusive enquanto a máquina pensa — a mão dela
     * não pode piscar na tela no meio do turno do adversário. No passa-e-joga é de quem
     * está na vez, que é justamente quem tem o aparelho na mão.
     */
    private fun viewerSeat(): Seat {
        val humanSeats = session.players.filterValues { it is Player.Human }.keys
        return humanSeats.singleOrNull() ?: session.turn
    }

    private fun snapshot(
        selected: Int? = null,
        hinted: Set<Int> = emptySet(),
        hintedMove: Move? = null,
        message: BoardMessage? = null,
        status: BoardStatus? = null,
        promotion: PendingPromotion? = null,
    ): BoardUiState {
        val humanSeats = session.players.filterValues { it is Player.Human }.keys
        val outcome = session.outcome
        val resolvedStatus = status ?: when {
            outcome.isOver -> BoardStatus.Finished(outcome)
            session.awaitingAi -> BoardStatus.Thinking
            humanSeats.size > 1 -> BoardStatus.SeatTurn(session.turn)
            else -> BoardStatus.HumanTurn
        }

        val viewer = viewerSeat()
        // Acabada a partida, as mãos viram: no dominó fechado é a contagem dos pontos que
        // decide quem ganhou, e escondê-la deixaria o resultado sem explicação.
        val visible = if (outcome.isOver) {
            session.state
        } else {
            entry.rules.redactFor(session.state, viewer)
        }

        return BoardUiState(
            state = visible,
            status = resolvedStatus,
            selected = selected,
            history = session.history,
            promotion = promotion,
            hinted = hinted,
            lastMove = lastMoveSquares,
            hintedMove = hintedMove,
            lastPlayed = lastPlayedMove,
            viewer = viewer,
            canUndo = session.canUndo,
            canPlay = !outcome.isOver && resolvedStatus != BoardStatus.Thinking,
            againstPhone = humanSeats.size == 1,
            humanSeat = humanSeats.singleOrNull(),
            message = message,
            event = pendingEvent,
            eventId = if (pendingEvent == null) eventCounter else ++eventCounter,
            // Só o contador entra aqui, nunca `_ui.value`: este método monta o estado
            // inicial do próprio `_ui`, e ler o campo durante a sua própria inicialização
            // derrubaria o app ao abrir o tabuleiro.
            messageId = if (message == null) messageCounter else ++messageCounter,
        ).also {
            // Evento é de uma vez só: o próximo estado da tela já sai sem ele, para o som
            // não tocar de novo a cada recomposição.
            pendingEvent = null
        }
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
        /**
         * Monta a sessão de uma partida nova.
         *
         * Contra o celular, a pessoa ocupa [humanSeat] e **todas** as outras cadeiras são
         * da máquina — numa mesa de quatro isso são três adversários. No passa-e-joga a
         * mesa inteira é de gente.
         */
        fun newSession(
            entry: GameEntry,
            againstPhone: Boolean,
            difficulty: Difficulty,
            humanSeat: Seat,
            seats: Int = 2,
        ): MatchSession {
            val players = buildMap {
                for (index in 0 until seats) {
                    val seat = Seat(index)
                    put(
                        seat,
                        if (!againstPhone || seat == humanSeat) {
                            Player.Human
                        } else {
                            Player.Ai(difficulty)
                        },
                    )
                }
            }
            return MatchSession(entry, MatchConfig.random(seats), players)
        }

        /** Retoma a partida guardada. */
        fun resumedSession(entry: GameEntry, saved: SavedMatch): MatchSession {
            // O tamanho da mesa vem do registro, não do jogo: uma partida de dominó a três
            // guardada ontem tem que voltar a três, e não ao padrão de dois.
            val players = buildMap {
                for (index in 0 until saved.record.config.seats) {
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
