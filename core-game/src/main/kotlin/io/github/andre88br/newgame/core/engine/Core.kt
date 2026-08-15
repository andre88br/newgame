package io.github.andre88br.newgame.core.engine

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Identificador estável de cada jogo. O nome é persistido, então não renomeie constantes. */
@Serializable
enum class GameId {
    TIC_TAC_TOE,
    CHECKERS,
    CHESS,
    REVERSI,
    DOMINOES,
    LUDO,
    HEARTS,
    CANASTRA,
}

/**
 * Uma cadeira da partida, identificada pelo índice (0 é sempre quem começa).
 *
 * Cadeira é uma posição na mesa, não uma pessoa: quem ocupa cada uma (humano local,
 * IA ou, no futuro, jogador remoto) é decisão da camada de aplicação, e o motor não
 * precisa saber.
 */
@Serializable
@JvmInline
value class Seat(val index: Int) {
    override fun toString(): String = "Seat($index)"

    companion object {
        val FIRST: Seat = Seat(0)
        val SECOND: Seat = Seat(1)
    }
}

/** Alterna entre as duas cadeiras de um jogo de dois jogadores. */
fun Seat.opponent(): Seat = Seat(1 - index)

/** Próxima cadeira num jogo com [seatCount] participantes. */
fun Seat.next(seatCount: Int): Seat = Seat((index + 1) % seatCount)

/**
 * Estado completo de uma partida em determinado momento.
 *
 * Implementações devem ser **imutáveis** e `@Serializable`: o motor nunca altera um
 * estado no lugar, sempre devolve um novo. É isso que dá de graça o desfazer-lance,
 * a busca da IA sobre estados especulativos e o replay de partida salva.
 */
interface GameState {
    /** Cadeira que deve jogar agora. Sem significado quando a partida já terminou. */
    val turn: Seat

    /** Quantos lances já foram aplicados desde o estado inicial. */
    val ply: Int
}

/** Um lance. Implementações devem ser imutáveis, `@Serializable` e comparáveis por valor. */
interface Move {
    /** Texto curto para histórico e depuração (ex.: "e2e4", "18x27"). */
    fun describe(): String
}

/** Resultado de aplicar um lance. */
sealed interface MoveResult<out S : GameState> {
    data class Ok<S : GameState>(val state: S) : MoveResult<S>

    data class Illegal(val reason: Reason) : MoveResult<Nothing> {
        constructor(key: ReasonKey) : this(key.reason())
    }
}

/** Situação da partida. */
@Serializable
sealed interface Outcome {
    @Serializable
    data object InProgress : Outcome

    @Serializable
    data class Win(val seat: Seat) : Outcome

    @Serializable
    data class Draw(val reason: DrawReason) : Outcome

    val isOver: Boolean get() = this !is InProgress
}

@Serializable
enum class DrawReason {
    /** Sem lances legais e sem derrota definida (ex.: afogamento no xadrez). */
    STALEMATE,

    /** Mesma posição repetida o número de vezes previsto nas regras. */
    REPETITION,

    /** Limite de lances sem progresso (sem captura e sem avanço de peão/pedra). */
    NO_PROGRESS,

    /** Nenhum dos lados tem material suficiente para vencer. */
    INSUFFICIENT_MATERIAL,

    /** Jogo travado sem vencedor (ex.: dominó fechado com empate na contagem). */
    BLOCKED,

    /** Todas as casas ocupadas sem vencedor (ex.: velha). */
    FULL_BOARD,

    /** Acordado pelos jogadores. */
    AGREEMENT,
}

/**
 * Parâmetros escolhidos na criação da partida.
 *
 * A [seed] alimenta toda a aleatoriedade do motor (embaralhamento do dominó, dados do
 * ludo). Guardar a semente junto da partida é o que torna o replay reproduzível — e é
 * também o que permitiria, mais adiante, sincronizar sorteios entre dois aparelhos.
 */
@Serializable
data class MatchConfig(
    val seed: Long,
    /**
     * Quantas pessoas sentam à mesa.
     *
     * A maioria dos jogos só existe com duas; o dominó e o ludo aceitam até quatro. Fica
     * na configuração, e não numa opção de texto, porque o número de cadeiras muda a
     * distribuição inicial — e o registro da partida precisa dele para reconstruir o mesmo
     * tabuleiro ao ser reaberto.
     */
    val seats: Int = 2,
    val options: Map<String, String> = emptyMap(),
) {

    init {
        require(seats in MIN_SEATS..MAX_SEATS) { "Cadeiras fora da faixa: $seats" }
    }
    fun rng(): Rng = Rng.seeded(seed)

    fun option(key: String): String? = options[key]

    companion object {
        const val MIN_SEATS: Int = 2
        const val MAX_SEATS: Int = 4

        /** Configuração sem aleatoriedade relevante — útil para jogos determinísticos e testes. */
        val DETERMINISTIC: MatchConfig = MatchConfig(seed = 0L)

        fun random(seats: Int = MIN_SEATS): MatchConfig =
            MatchConfig(seed = java.security.SecureRandom().nextLong(), seats = seats)
    }
}

/**
 * As regras de um jogo.
 *
 * Toda a camada de aplicação (tela do tabuleiro, IA, persistência) conversa só com esta
 * interface, então cada jogo novo entra sem tocar em nada acima dele.
 */
interface BoardGame<S : GameState, M : Move> {
    val id: GameId

    /**
     * Quantas pessoas este jogo aceita à mesa.
     *
     * A maioria só existe com duas; o dominó e o ludo vão até quatro. Quem monta a tela de
     * configuração lê isto para decidir se oferece a escolha.
     */
    val supportedSeats: IntRange get() = 2..2

    /**
     * Quantas cadeiras esta partida tem.
     *
     * Sai do estado, e não de um campo fixo: uma partida de dominó a três e outra a quatro
     * são o mesmo jogo com tabuleiros diferentes, e quem recebe um estado guardado precisa
     * descobrir isso olhando para ele.
     */
    fun seatsIn(state: S): Int = 2

    fun initialState(config: MatchConfig): S

    /** Lances legais para [GameState.turn]. Vazio quando a partida acabou. */
    fun legalMoves(state: S): List<M>

    /**
     * Aplica [move] a [state]. Não altera [state]: devolve o estado seguinte.
     * Deve rejeitar lance ilegal em vez de confiar em quem chamou.
     */
    fun applyMove(state: S, move: M): MoveResult<S>

    /**
     * Aplica um lance que **já se sabe** legal, por ter vindo de [legalMoves].
     *
     * Existe para a busca da IA: em jogos como as damas, verificar a legalidade exige
     * gerar todos os lances da posição de novo, e pagar isso em cada nó da árvore
     * dobraria o custo da busca. Quem recebe lance de fora (tela, partida salva) usa
     * [applyMove], que valida.
     */
    fun applyKnownLegal(state: S, move: M): S = applyOrThrow(state, move)

    fun outcome(state: S): Outcome

    /**
     * Se [move] tira peça do adversário do tabuleiro.
     *
     * Serve à camada de apresentação: captura merece som e destaque diferentes de um lance
     * comum, e quem sabe dizer se houve captura é quem conhece a regra. Nos jogos em que a
     * pergunta não faz sentido — no reversi todo lance vira peça, no dominó nada sai da
     * mesa — a resposta é `false`, e a tela trata tudo como lance comum.
     */
    fun isCapture(state: S, move: M): Boolean = false

    /**
     * Se este jogo tem informação oculta — mão do adversário, monte de compra.
     *
     * É declarado em vez de deduzido de [redactFor] porque quem consome precisa saber
     * **antes** de olhar um estado: a IA de informação imperfeita joga por amostragem de
     * mundos possíveis, e a tela precisa decidir se mostra a mão do outro lado.
     */
    val hasHiddenInformation: Boolean get() = false

    /**
     * Se são as regras que decidem quem abre, e não quem está configurando a partida.
     *
     * No dominó abre quem tirou a maior carroça; no ludo, quem tirar um dado que sirva.
     * A tela de configuração precisa saber disso para não oferecer uma escolha que ela não
     * tem como cumprir.
     */
    val decidesWhoStarts: Boolean get() = false

    /**
     * Remove de [state] a informação que [viewer] não tem direito de ver (a mão do
     * adversário no dominó, por exemplo). Jogos de informação perfeita não escondem nada.
     */
    fun redactFor(state: S, viewer: Seat): S = state

    /**
     * Identidade da posição para efeito de repetição, ou `null` no jogo em que repetir não
     * significa nada.
     *
     * Repetição é propriedade da **partida**, não da posição: olhando um estado sozinho não
     * há como saber quantas vezes ele já apareceu. Por isso o motor só diz o que conta como
     * "a mesma posição", e quem guarda o histórico faz a contagem. A chave deve ignorar o
     * que muda a cada lance sem mudar a posição — contadores, número do lance.
     */
    fun repetitionKey(state: S): String? = null

    val stateSerializer: KSerializer<S>

    val moveSerializer: KSerializer<M>
}

/** Atalho: aplica o lance e devolve o novo estado, lançando se for ilegal. */
fun <S : GameState, M : Move> BoardGame<S, M>.applyOrThrow(state: S, move: M): S =
    when (val result = applyMove(state, move)) {
        is MoveResult.Ok -> result.state
        is MoveResult.Illegal -> error("Lance ilegal em ${id}: ${move.describe()} — ${result.reason}")
    }
