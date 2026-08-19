package io.github.andre88br.newgame.app.ui.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.andre88br.newgame.app.data.GameSpeed
import io.github.andre88br.newgame.app.data.MatchStore
import io.github.andre88br.newgame.app.data.SavedMatch
import io.github.andre88br.newgame.app.ui.feedback.GameEvent
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameId
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    /**
     * O nome de cada cadeira, na ordem das cadeiras.
     *
     * Pode vir vazia — partida salva antes de existirem nomes —, e por isso a tela lê com
     * `getOrNull` e cai nos rótulos antigos quando não há nome.
     */
    val names: List<String> = emptyList(),
    /** As cadeiras ocupadas por gente. O resto é máquina. */
    val humanSeats: Set<Seat> = emptySet(),
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
    /**
     * Uma mão acabou de fechar e ainda não foi reconhecida.
     *
     * Só existe nos jogos de várias mãos por partida (canastra, truco, copas, pôquer — veja
     * [GameEntry.handOf]). Enquanto isto for `true` o tabuleiro fica bloqueado e a IA não
     * joga: a tela do jogo mostra o resumo da mão que fechou, e só [BoardViewModel.acknowledgeRoundEnd]
     * destrava a partida, quando a pessoa aperta "continuar".
     */
    val roundJustEnded: Boolean = false,
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
    /** O nome de cada cadeira, escolhido antes de começar ou lido da partida salva. */
    private val names: List<String> = emptyList(),
    /** O ritmo escolhido nos ajustes, fixo pela partida inteira. */
    private val gameSpeed: GameSpeed = GameSpeed.NORMAL,
) : ViewModel() {

    private var lastMoveSquares: Set<Int> = emptySet()
    private var lastPlayedMove: Move? = null
    private var messageCounter = 0L
    private var pendingEvent: GameEvent? = null
    private var eventCounter = 0L

    /** Quanto a máquina espera entre lances, com o ritmo dos ajustes já aplicado. */
    private val aiPaceMillis: Long = (entry.aiPaceMillis * gameSpeed.multiplier).toLong()

    /**
     * A última mão já reconhecida pela pessoa.
     *
     * Começa na mão em que a partida (ou a retomada) já está: a primeira mão de uma partida
     * nova não é "o fim de mão anterior" — não há resumo nenhum para mostrar antes dela.
     */
    private var acknowledgedHand: Int = entry.handOf?.invoke(session.state) ?: 0

    private val _ui = MutableStateFlow(snapshot())
    val ui: StateFlow<BoardUiState> = _ui.asStateFlow()

    init {
        // A IA pode ocupar a primeira cadeira: nesse caso ela abre a partida sozinha.
        maybePlayAiTurn()
    }

    fun onSquareTap(square: Int) {
        val interactor = entry.interactor ?: return
        if (session.isOver || session.awaitingAi || _ui.value.status == BoardStatus.Thinking) return
        if (_ui.value.roundJustEnded) return
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
        if (_ui.value.roundJustEnded) return
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
        if (_ui.value.status == BoardStatus.Thinking || _ui.value.roundJustEnded) return
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
        acknowledgedHand = entry.handOf?.invoke(session.state) ?: 0
        _ui.value = snapshot()
        persist()
        maybePlayAiTurn()
    }

    /**
     * A pessoa viu o resumo da mão que fechou e apertou "continuar".
     *
     * Destrava o tabuleiro e, se a mão nova começar na vez da máquina, retoma o loop da IA —
     * que parou exatamente para esperar este aceno.
     */
    fun acknowledgeRoundEnd() {
        val handOf = entry.handOf ?: return
        val current = handOf(session.state)
        if (current == acknowledgedHand) return
        acknowledgedHand = current
        _ui.value = snapshot()
        maybePlayAiTurn()
    }

    /** Roda a busca no nível difícil e destaca o lance sugerido, sem jogá-lo. */
    fun onHint() {
        if (session.isOver || session.awaitingAi || _ui.value.roundJustEnded) return
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
        val handBefore = entry.handOf?.invoke(before)
        when (val result = session.play(move)) {
            is PlayResult.Ok -> {
                lastMoveSquares = entry.interactor?.squaresOf(result.move).orEmpty().toSet()
                lastPlayedMove = result.move
                noteEvent(before, result.move)
                _ui.value = snapshot()
                persist()
                // Se o próprio lance da pessoa fechou a mão, a vez da IA espera: só depois
                // que ela reconhecer o resumo é que faz sentido a máquina seguir para a
                // próxima mão (que pode começar já na vez dela).
                val handAfter = entry.handOf?.invoke(result.state)
                if (handAfter == null || handAfter == handBefore) {
                    maybePlayAiTurn()
                }
            }

            is PlayResult.Rejected ->
                _ui.value = snapshot(message = BoardMessage.Rejected(result.reason))
            PlayResult.NotYourTurn, PlayResult.Finished -> Unit
        }
    }

    /**
     * Joga a IA até a vez voltar para uma pessoa.
     *
     * Um só [MatchSession.playAiTurn] joga um lance só. No ludo, tirar 6 dá o dado de novo
     * para quem tirou — inclusive a máquina —, e numa mesa de mais de duas cadeiras uma IA
     * pode jogar logo depois da outra. Chamar a busca uma única vez deixaria a tela presa em
     * "pensando" sem ninguém para tirar dali: nada dispara um novo lance sozinho.
     */
    private fun maybePlayAiTurn() {
        if (!session.awaitingAi) return
        _ui.value = snapshot(status = BoardStatus.Thinking)

        viewModelScope.launch {
            try {
                while (session.awaitingAi) {
                    // A pausa vem **antes** do lance, e não depois, porque o que a tela
                    // precisa mostrar acontece antes dele: no ludo o dado da vez da máquina
                    // já está neste estado, e é rolando agora. Jogar na hora trocaria o
                    // tabuleiro no meio da rolagem, e ninguém chegaria a ver com quanto ela
                    // andou — que é exatamente o que parecia "a IA joga rápido demais".
                    delay(aiPaceMillis)

                    // A busca do nível difícil leva segundos: fora da thread da interface, sempre.
                    val before = session.state
                    val handBefore = entry.handOf?.invoke(before)
                    val move = withContext(Dispatchers.Default) { session.playAiTurn() } ?: break
                    lastMoveSquares = entry.interactor?.squaresOf(move).orEmpty().toSet()
                    lastPlayedMove = move
                    noteEvent(before, move)
                    // Atualiza a cada lance, e não só no final: com várias jogadas da IA em
                    // fila — tirar 6 no ludo, ou três cadeiras de máquina numa mesa de
                    // quatro —, é assim que cada uma aparece na tela com seu som, em vez de
                    // a partida pular direto para o resultado da última.
                    _ui.value = snapshot()
                    persist()

                    // Esta jogada da IA fechou uma mão: para aqui, mesmo que a mão nova já
                    // comece na vez da própria máquina. É o resumo da mão que acabou de
                    // fechar que tem que aparecer agora, e só continua quando a pessoa
                    // reconhecer — não antes, escondido atrás dele.
                    val handAfter = entry.handOf?.invoke(session.state)
                    if (handAfter != null && handAfter != handBefore) break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // A busca não devia falhar, mas travar a tela em "pensando" pra sempre —
                // obrigando quem joga a fechar e abrir o app de novo — é pior do que deixar
                // a partida como está e a pessoa tentar outro lance.
            }
            _ui.value = snapshot()
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
                    playerNames = names,
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
        val currentHand = entry.handOf?.invoke(session.state)
        val roundJustEnded = currentHand != null && currentHand != acknowledgedHand
        val resolvedStatus = status ?: when {
            outcome.isOver -> BoardStatus.Finished(outcome)
            // Pausado esperando o aceno da mão: a máquina não está pensando, e dizer que
            // está enquanto o resumo cobre a tela ia contra o que se vê.
            roundJustEnded -> if (humanSeats.size > 1) BoardStatus.SeatTurn(session.turn) else BoardStatus.HumanTurn
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
            names = names,
            humanSeats = humanSeats,
            canUndo = session.canUndo,
            canPlay = !outcome.isOver && resolvedStatus != BoardStatus.Thinking && !roundJustEnded,
            roundJustEnded = roundJustEnded,
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
        private val names: List<String> = emptyList(),
        private val gameSpeed: GameSpeed = GameSpeed.NORMAL,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BoardViewModel(entry, store, matchId, session, names, gameSpeed) as T
    }

    companion object {
        /**
         * Monta a sessão de uma partida nova.
         *
         * Contra o celular, a pessoa ocupa [humanSeat] e **todas** as outras cadeiras são
         * da máquina — numa mesa de quatro isso são três adversários. No passa-e-joga a
         * mesa inteira é de gente.
         *
         * [ludoFirstArm] é só do ludo: qual cor a cadeira zero joga, escolhida antes de
         * começar. Em outros jogos não faz sentido, e vai ignorado.
         */
        fun newSession(
            entry: GameEntry,
            againstPhone: Boolean,
            difficulty: Difficulty,
            humanSeat: Seat,
            seats: Int = 2,
            ludoFirstArm: Int = 0,
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
            val config = MatchConfig.random(seats).let {
                if (entry.id == GameId.LUDO) {
                    it.copy(options = mapOf("ludo.firstArm" to ludoFirstArm.toString()))
                } else {
                    it
                }
            }
            return MatchSession(entry, config, players)
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
