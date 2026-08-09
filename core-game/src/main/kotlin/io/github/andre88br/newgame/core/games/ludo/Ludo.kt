package io.github.andre88br.newgame.core.games.ludo

import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** Casas da volta principal, comuns aos dois jogadores. */
const val LUDO_TRACK = 52

/** Casas do corredor final, particulares de cada cor. */
const val LUDO_HOME_LANE = 5

/** Peões de cada jogador. */
const val LUDO_TOKENS = 4

/** Face do dado que tira peão do curral e dá lance extra. */
const val LUDO_ENTRY_ROLL = 6

/**
 * Onde um peão está.
 *
 * A posição é contada **do ponto de vista do dono**, e não em casas absolutas do tabuleiro:
 * `0` é a casa de saída da própria cor, e o percurso vai até [LUDO_TRACK] + [LUDO_HOME_LANE].
 * Isso deixa a regra igual para as duas cores — quem converte para casa absoluta é
 * [absoluteSquare], usado só para saber quem pisa em quem.
 */
const val LUDO_YARD = -1

/** Passos até chegar: 52 da volta mais o corredor final. */
const val LUDO_GOAL = LUDO_TRACK + LUDO_HOME_LANE

/** Casas de saída de cada cadeira, na numeração absoluta da volta. */
fun startSquare(seat: Seat): Int = if (seat == Seat.FIRST) 0 else LUDO_TRACK / 2

/**
 * Casa absoluta de um peão, ou `null` se ele estiver no curral ou no corredor final —
 * lugares onde ninguém pode ser capturado.
 */
fun absoluteSquare(seat: Seat, progress: Int): Int? {
    if (progress < 0 || progress >= LUDO_TRACK) return null
    return (startSquare(seat) + progress) % LUDO_TRACK
}

/**
 * Casas seguras: as duas saídas e as quatro casas marcadas a cada oito da volta. Peão
 * parado numa delas não é capturado.
 */
fun isSafeSquare(square: Int): Boolean =
    square % (LUDO_TRACK / 4) == 0 || square % (LUDO_TRACK / 4) == 8

@Serializable
data class LudoState(
    /** Progresso de cada peão, por cadeira. [LUDO_YARD] no curral, [LUDO_GOAL] na chegada. */
    val tokens: List<List<Int>> = listOf(
        List(LUDO_TOKENS) { LUDO_YARD },
        List(LUDO_TOKENS) { LUDO_YARD },
    ),
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    /** O dado já rolado, à espera de um peão para mover. */
    val die: Int = 1,
    /** Gerador guardado no estado: é o que faz a partida salva reproduzir os mesmos dados. */
    val rng: Rng = Rng(0),
    /** Passes seguidos por falta de lance, para a partida não travar para sempre. */
    val idleTurns: Int = 0,
) : GameState {

    fun tokensOf(seat: Seat): List<Int> = tokens[seat.index]

    fun finished(seat: Seat): Int = tokensOf(seat).count { it >= LUDO_GOAL }

    fun inYard(seat: Seat): Int = tokensOf(seat).count { it == LUDO_YARD }

    override fun toString(): String = buildString {
        append("dado=$die vez=${turn.index} parados=$idleTurns\n")
        tokens.forEachIndexed { index, list ->
            append("cadeira $index: ${list.joinToString(" ")}\n")
        }
    }
}

/** Mover o peão de índice [token] com o dado que já está na mesa. */
@Serializable
data class LudoMove(val token: Int) : Move {
    override fun describe(): String = "peão ${token + 1}"
}

/**
 * Ludo para dois.
 *
 * O dado é rolado **pelo motor**, não pelo jogador: quando a vez chega, o valor já está em
 * [LudoState.die], e o lance é escolher qual peão anda. É o que permite manter a promessa
 * do projeto de que um lance é uma decisão — rolar o dado não é decisão nenhuma.
 *
 * O gerador mora dentro do estado, e não num campo estático, porque é isso que faz a
 * partida salva reproduzir exatamente os mesmos dados ao ser reaberta. Sem isso, `applyMove`
 * deixaria de ser puro e o replay do registro chegaria a outro tabuleiro.
 */
object LudoGame : BoardGame<LudoState, LudoMove> {

    /**
     * Teto de vezes seguidas sem ninguém poder mover.
     *
     * Não deveria acontecer — basta um 6 para destravar —, mas `applyMove` é uma função
     * pura e não pode depender de sorte para terminar. Com o teto, o pior caso é um empate
     * declarado em vez de um laço infinito.
     */
    const val IDLE_LIMIT: Int = 60

    override val id: GameId = GameId.LUDO

    /** Abre quem tirar um dado que sirva, e isso é o primeiro sorteio da partida. */
    override val decidesWhoStarts: Boolean = true

    /**
     * O primeiro dado já vem rolado, e vem com lance garantido.
     *
     * Não basta rolar uma vez: com todos os peões no curral, qualquer face que não seja 6
     * abriria a partida sem lance nenhum — a tela mostraria um tabuleiro travado no lance
     * zero. [rollFor] é a mesma rolagem usada entre lances, que passa a vez e rola de novo
     * até alguém poder jogar.
     */
    override fun initialState(config: MatchConfig): LudoState =
        rollFor(LudoState(rng = config.rng()), Seat.FIRST)

    override fun legalMoves(state: LudoState): List<LudoMove> {
        if (state.idleTurns >= IDLE_LIMIT) return emptyList()
        if (state.finished(Seat.FIRST) == LUDO_TOKENS) return emptyList()
        if (state.finished(Seat.SECOND) == LUDO_TOKENS) return emptyList()
        return movesFor(state, state.turn, state.die)
    }

    /** Peões que podem andar [die] casas. */
    fun movesFor(state: LudoState, seat: Seat, die: Int): List<LudoMove> {
        val tokens = state.tokensOf(seat)
        val moves = ArrayList<LudoMove>(LUDO_TOKENS)

        for (index in tokens.indices) {
            val progress = tokens[index]
            when {
                // Sair do curral custa um 6, e a casa de saída não pode estar bloqueada
                // por peão próprio.
                progress == LUDO_YARD ->
                    if (die == LUDO_ENTRY_ROLL && tokens.none { it == 0 }) moves += LudoMove(index)

                progress >= LUDO_GOAL -> Unit

                else -> {
                    val target = progress + die
                    // Chegada é exata: passar da casa final não vale lance.
                    if (target <= LUDO_GOAL && tokens.none { it == target && target < LUDO_GOAL }) {
                        moves += LudoMove(index)
                    }
                }
            }
        }
        return moves
    }

    override fun applyMove(state: LudoState, move: LudoMove): MoveResult<LudoState> {
        // Lista vazia aqui não quer dizer partida encerrada: quer dizer que este dado não
        // serve para peão nenhum. Confundir as duas coisas engolia o motivo verdadeiro da
        // recusa, que é justamente o que a tela mostra a quem tocou no peão errado.
        if (outcome(state).isOver) return MoveResult.Illegal("A partida já terminou")
        val legal = legalMoves(state)
        if (move !in legal) {
            if (move.token !in 0 until LUDO_TOKENS) return MoveResult.Illegal("Esse peão não existe")
            val progress = state.tokensOf(state.turn)[move.token]
            return when {
                progress == LUDO_YARD ->
                    MoveResult.Illegal("Só um 6 tira peão do curral")
                progress >= LUDO_GOAL ->
                    MoveResult.Illegal("Esse peão já chegou")
                progress + state.die > LUDO_GOAL ->
                    MoveResult.Illegal("A chegada é exata: esse peão precisa de ${LUDO_GOAL - progress}")
                else ->
                    MoveResult.Illegal("Já há um peão seu nessa casa")
            }
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    override fun applyKnownLegal(state: LudoState, move: LudoMove): LudoState {
        val seat = state.turn
        val mine = state.tokensOf(seat).toMutableList()
        val progress = mine[move.token]

        val target = if (progress == LUDO_YARD) 0 else progress + move.step(state)
        mine[move.token] = target

        val tokens = state.tokens.toMutableList()
        tokens[seat.index] = mine

        // Captura: peão adversário sozinho numa casa comum volta para o curral.
        val landing = absoluteSquare(seat, target)
        if (landing != null && !isSafeSquare(landing)) {
            val opponent = seat.opponent()
            tokens[opponent.index] = state.tokensOf(opponent).map { theirs ->
                if (absoluteSquare(opponent, theirs) == landing) LUDO_YARD else theirs
            }
        }

        val afterMove = state.copy(tokens = tokens, ply = state.ply + 1, idleTurns = 0)

        // Tirar 6 dá direito a jogar de novo.
        val next = if (state.die == LUDO_ENTRY_ROLL) seat else seat.opponent()
        return rollFor(afterMove, next)
    }

    private fun LudoMove.step(state: LudoState): Int = state.die

    /**
     * Rola o dado para [seat] e, se essa cadeira não tiver lance, passa adiante rolando de
     * novo — até alguém poder jogar ou o teto de rodadas paradas ser atingido.
     */
    private fun rollFor(state: LudoState, seat: Seat): LudoState {
        var current = state
        var who = seat
        var idle = 0

        while (idle < IDLE_LIMIT) {
            val roll = current.rng.nextDie()
            current = current.copy(die = roll.value, rng = roll.rng, turn = who)
            if (movesFor(current, who, roll.value).isNotEmpty()) {
                return current.copy(idleTurns = 0)
            }
            // Sem lance com este dado: a vez passa e rola-se outro.
            who = who.opponent()
            idle++
        }
        return current.copy(idleTurns = IDLE_LIMIT)
    }

    override fun outcome(state: LudoState): Outcome {
        if (state.finished(Seat.FIRST) == LUDO_TOKENS) return Outcome.Win(Seat.FIRST)
        if (state.finished(Seat.SECOND) == LUDO_TOKENS) return Outcome.Win(Seat.SECOND)
        if (state.idleTurns >= IDLE_LIMIT) return Outcome.Draw(DrawReason.BLOCKED)
        return Outcome.InProgress
    }

    override val stateSerializer: KSerializer<LudoState> = serializer()
    override val moveSerializer: KSerializer<LudoMove> = serializer()
}
