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

    data class Illegal(val reason: String) : MoveResult<Nothing>
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
    val options: Map<String, String> = emptyMap(),
) {
    fun rng(): Rng = Rng.seeded(seed)

    fun option(key: String): String? = options[key]

    companion object {
        /** Configuração sem aleatoriedade relevante — útil para jogos determinísticos e testes. */
        val DETERMINISTIC: MatchConfig = MatchConfig(seed = 0L)

        fun random(): MatchConfig = MatchConfig(seed = java.security.SecureRandom().nextLong())
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

    /** Quantidade de cadeiras da mesa (2 para a maioria; o ludo aceita até 4). */
    val seatCount: Int get() = 2

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
     * Remove de [state] a informação que [viewer] não tem direito de ver (a mão do
     * adversário no dominó, por exemplo). Jogos de informação perfeita não escondem nada.
     */
    fun redactFor(state: S, viewer: Seat): S = state

    val stateSerializer: KSerializer<S>

    val moveSerializer: KSerializer<M>
}

/** Atalho: aplica o lance e devolve o novo estado, lançando se for ilegal. */
fun <S : GameState, M : Move> BoardGame<S, M>.applyOrThrow(state: S, move: M): S =
    when (val result = applyMove(state, move)) {
        is MoveResult.Ok -> result.state
        is MoveResult.Illegal -> error("Lance ilegal em ${id}: ${move.describe()} — ${result.reason}")
    }
