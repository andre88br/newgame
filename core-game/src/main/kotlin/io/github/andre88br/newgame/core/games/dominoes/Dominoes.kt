package io.github.andre88br.newgame.core.games.dominoes

import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.opponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** Maior número numa peça: dominó de bater até seis. */
const val DOMINO_MAX_PIP: Int = 6

/** Peças na mão de cada jogador no começo. */
const val DOMINO_HAND_SIZE: Int = 7

/**
 * Uma peça, sempre guardada com o menor número primeiro para que `4-2` e `2-4` sejam a
 * mesma peça — como são de verdade.
 */
@Serializable
data class Tile(val low: Int, val high: Int) {

    init {
        require(low <= high) { "A peça guarda o menor número primeiro: veio $low-$high" }
    }

    val pips: Int get() = low + high

    val isDouble: Boolean get() = low == high

    /** É uma peça de adversário, cujo valor não se conhece. Veja [DominoesGame.redactFor]. */
    val isHidden: Boolean get() = low < 0

    fun matches(value: Int): Boolean = low == value || high == value

    /** O outro número da peça, dado um deles. */
    fun other(value: Int): Int = if (low == value) high else low

    override fun toString(): String = if (isHidden) "??" else "$low-$high"

    companion object {
        /** Peça virada para baixo: o adversário tem uma peça ali, mas não se sabe qual. */
        val HIDDEN: Tile = Tile(-1, -1)

        /** As 28 peças do dominó de bater. */
        fun fullSet(): List<Tile> = buildList {
            for (low in 0..DOMINO_MAX_PIP) {
                for (high in low..DOMINO_MAX_PIP) add(Tile(low, high))
            }
        }
    }
}

/** Uma peça já na mesa, com a orientação em que foi encostada. */
@Serializable
data class PlacedTile(val a: Int, val b: Int) {
    val tile: Tile get() = Tile(minOf(a, b), maxOf(a, b))

    override fun toString(): String = "[$a|$b]"
}

/** Em qual ponta da linha a peça entra. */
@Serializable
enum class LineEnd {
    LEFT,
    RIGHT,
}

@Serializable
data class DominoesState(
    /** A linha na mesa. Invariante: `line[i].b == line[i+1].a`. */
    val line: List<PlacedTile> = emptyList(),
    /** A mão de cada cadeira. Some para quem não é o dono — veja [DominoesGame.redactFor]. */
    val hands: List<List<Tile>> = emptyList(),
    /** O monte de compra, também oculto. */
    val boneyard: List<Tile> = emptyList(),
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    /** Passes seguidos. Dois passes fecham o jogo. */
    val passes: Int = 0,
) : GameState {

    val leftEnd: Int? get() = line.firstOrNull()?.a
    val rightEnd: Int? get() = line.lastOrNull()?.b

    fun hand(seat: Seat): List<Tile> = hands.getOrElse(seat.index) { emptyList() }

    /** Quantas peças a cadeira tem. Continua correto mesmo com a mão oculta. */
    fun handSize(seat: Seat): Int = hand(seat).size

    /** Soma dos pontos na mão. Só faz sentido numa mão visível. */
    fun pipsInHand(seat: Seat): Int = hand(seat).sumOf { it.pips }

    override fun toString(): String = buildString {
        append(line.joinToString("") { it.toString() })
        append("\n")
        hands.forEachIndexed { index, hand ->
            append("cadeira $index: ${hand.joinToString(" ")}\n")
        }
        append("monte: ${boneyard.size} | vez: ${turn.index} | passes: $passes")
    }
}

@Serializable
data class DominoesMove(val tile: Tile, val end: LineEnd) : Move {
    override fun describe(): String = when {
        end == LineEnd.LEFT -> "${tile.low}-${tile.high}←"
        else -> "→${tile.low}-${tile.high}"
    }
}

/**
 * Uma peça da mão como a tela precisa mostrá-la: a peça e as pontas em que ela encaixa
 * agora. Lista vazia quer dizer peça sem serventia neste momento.
 */
data class HandTile(val tile: Tile, val ends: List<LineEnd>) {
    val playable: Boolean get() = ends.isNotEmpty()

    /** Lance pronto quando só há uma ponta possível — o toque único da tela. */
    val onlyMove: DominoesMove? get() = ends.singleOrNull()?.let { DominoesMove(tile, it) }
}

/**
 * A mão de [seat] na ordem em que ela está, com o que dá para fazer com cada peça.
 *
 * Existe aqui, e não na tela, porque é regra: saber se a peça encaixa à esquerda, à direita
 * ou nas duas é a mesma pergunta que [DominoesGame.movesFor] responde, e responder duas
 * vezes em lugares diferentes é o caminho para as duas respostas divergirem.
 */
fun handTiles(state: DominoesState, seat: Seat): List<HandTile> {
    val byTile = DominoesGame.movesFor(state, seat).groupBy { it.tile }
    return state.hand(seat).map { tile ->
        HandTile(tile, byTile[tile].orEmpty().map { it.end })
    }
}

/**
 * Dominó de bater, dois jogadores.
 *
 * É o primeiro jogo do projeto com **informação oculta**, e por isso o primeiro a usar
 * `redactFor`: a mão do adversário e o monte saem do estado antes de ele chegar à tela ou
 * à IA. Quem quiser ver a mão alheia teria de burlar o motor, não só a interface.
 *
 * Duas coisas acontecem sozinhas, pelo mesmo motivo do passe no reversi — para nenhuma
 * camada acima precisar conhecer a regra:
 *
 * - **A compra.** Quem não tem peça para jogar compra do monte até conseguir. Isso é
 *   obrigatório, não uma escolha, então não vira lance.
 * - **O passe.** Monte vazio e ainda sem peça: passa a vez. Dois passes seguidos fecham o
 *   jogo.
 *
 * Assim `legalMoves` só devolve peças que dá para jogar, e nunca volta vazio numa partida
 * em andamento.
 */
object DominoesGame : BoardGame<DominoesState, DominoesMove> {

    override val id: GameId = GameId.DOMINOES

    override val hasHiddenInformation: Boolean = true

    /** Abre quem tirou a maior carroça — veja [openingSeat]. */
    override val decidesWhoStarts: Boolean = true

    override fun initialState(config: MatchConfig): DominoesState {
        // O embaralhamento é a única aleatoriedade do jogo: dali em diante a compra é só
        // tirar a próxima peça do monte, o que mantém tudo reproduzível pela semente.
        val tiles = config.rng().shuffle(Tile.fullSet()).value

        val first = tiles.take(DOMINO_HAND_SIZE)
        val second = tiles.drop(DOMINO_HAND_SIZE).take(DOMINO_HAND_SIZE)
        val boneyard = tiles.drop(DOMINO_HAND_SIZE * 2)

        return DominoesState(
            hands = listOf(first, second),
            boneyard = boneyard,
            turn = openingSeat(first, second),
        )
    }

    /**
     * Quem abre: quem tiver a maior carroça; sem carroça na mesa, quem tiver a maior peça.
     * É a regra de mesa, e evita que abrir seja sempre da mesma cadeira.
     */
    private fun openingSeat(first: List<Tile>, second: List<Tile>): Seat {
        fun best(hand: List<Tile>): Pair<Int, Int> {
            val doubles = hand.filter { it.isDouble }
            return if (doubles.isNotEmpty()) 1 to doubles.maxOf { it.pips } else 0 to hand.maxOf { it.pips }
        }

        val (firstHasDouble, firstScore) = best(first)
        val (secondHasDouble, secondScore) = best(second)
        return when {
            firstHasDouble != secondHasDouble ->
                if (firstHasDouble > secondHasDouble) Seat.FIRST else Seat.SECOND
            firstScore >= secondScore -> Seat.FIRST
            else -> Seat.SECOND
        }
    }

    override fun legalMoves(state: DominoesState): List<DominoesMove> {
        if (outcomeWithoutMoves(state) != null) return emptyList()
        return movesFor(state, state.turn)
    }

    /** As jogadas possíveis de [seat] com a mão que ela tem agora. */
    fun movesFor(state: DominoesState, seat: Seat): List<DominoesMove> {
        val hand = state.hand(seat).filterNot { it.isHidden }
        val left = state.leftEnd
        val right = state.rightEnd

        // Mesa vazia: qualquer peça abre, e a ponta não importa.
        if (left == null || right == null) {
            return hand.distinct().map { DominoesMove(it, LineEnd.RIGHT) }
        }

        val moves = ArrayList<DominoesMove>()
        for (tile in hand.distinct()) {
            if (tile.matches(right)) moves += DominoesMove(tile, LineEnd.RIGHT)
            // Com as duas pontas iguais, encostar de um lado ou do outro dá no mesmo.
            if (left != right && tile.matches(left)) moves += DominoesMove(tile, LineEnd.LEFT)
        }
        return moves
    }

    override fun applyMove(state: DominoesState, move: DominoesMove): MoveResult<DominoesState> {
        val legal = legalMoves(state)
        if (legal.isEmpty()) return MoveResult.Illegal("A partida já terminou")
        if (move !in legal) {
            if (state.hand(state.turn).none { it == move.tile }) {
                return MoveResult.Illegal("Você não tem a peça ${move.tile}")
            }
            return MoveResult.Illegal("A peça ${move.tile} não encaixa nessa ponta")
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    override fun applyKnownLegal(state: DominoesState, move: DominoesMove): DominoesState {
        val hands = state.hands.toMutableList()
        val hand = hands[state.turn.index].toMutableList()
        hand.remove(move.tile)
        hands[state.turn.index] = hand

        val line = place(state, move)

        val played = state.copy(
            line = line,
            hands = hands,
            turn = state.turn.opponent(),
            ply = state.ply + 1,
            passes = 0,
        )

        // Quem recebe a vez compra o que precisar, e passa se não der. Resolver isso aqui
        // mantém a promessa de que `legalMoves` nunca volta vazio com o jogo em andamento.
        return settleTurn(played)
    }

    private fun place(state: DominoesState, move: DominoesMove): List<PlacedTile> {
        val left = state.leftEnd
        val right = state.rightEnd
        if (left == null || right == null) {
            return listOf(PlacedTile(move.tile.low, move.tile.high))
        }
        return if (move.end == LineEnd.LEFT) {
            listOf(PlacedTile(move.tile.other(left), left)) + state.line
        } else {
            state.line + PlacedTile(right, move.tile.other(right))
        }
    }

    /**
     * Compra e passe automáticos, até a vez cair em alguém que tenha o que jogar.
     *
     * O laço termina sempre: cada volta ou tira uma peça do monte, que é finito, ou soma um
     * passe, e dois passes encerram a partida.
     */
    private fun settleTurn(start: DominoesState): DominoesState {
        var state = start
        while (state.passes < 2) {
            // Alguém bateu: a partida acabou, não há o que comprar.
            if (state.hands.any { it.isEmpty() }) return state
            if (movesFor(state, state.turn).isNotEmpty()) return state

            if (state.boneyard.isNotEmpty()) {
                state = draw(state)
                continue
            }

            state = state.copy(turn = state.turn.opponent(), passes = state.passes + 1)
        }
        return state
    }

    private fun draw(state: DominoesState): DominoesState {
        val boneyard = state.boneyard.toMutableList()
        val tile = boneyard.removeAt(0)
        val hands = state.hands.toMutableList()
        hands[state.turn.index] = hands[state.turn.index] + tile
        return state.copy(hands = hands, boneyard = boneyard)
    }

    override fun outcome(state: DominoesState): Outcome =
        outcomeWithoutMoves(state) ?: Outcome.InProgress

    /**
     * O desfecho, se houver. Separado de [outcome] porque [legalMoves] precisa dele sem
     * poder chamar `outcome` — que chamaria `legalMoves` de volta.
     */
    private fun outcomeWithoutMoves(state: DominoesState): Outcome? {
        // Bateu: quem fica sem peça na mão vence.
        for (index in state.hands.indices) {
            if (state.hands[index].isEmpty()) return Outcome.Win(Seat(index))
        }

        if (state.passes < 2) return null

        // Jogo fechado: ninguém tem o que jogar. Vence quem tiver menos pontos na mão.
        val firstPips = state.pipsInHand(Seat.FIRST)
        val secondPips = state.pipsInHand(Seat.SECOND)
        return when {
            firstPips < secondPips -> Outcome.Win(Seat.FIRST)
            secondPips < firstPips -> Outcome.Win(Seat.SECOND)
            else -> Outcome.Draw(DrawReason.BLOCKED)
        }
    }

    /**
     * Esconde o que [viewer] não pode ver: a mão do adversário e o monte viram peças
     * viradas para baixo.
     *
     * As peças continuam **contadas** — some o valor, não a quantidade —, porque saber
     * quantas peças o outro tem faz parte do jogo. É esta função que a sessão aplica antes
     * de entregar o estado à IA, então nem a máquina joga sabendo o que não deveria.
     */
    override fun redactFor(state: DominoesState, viewer: Seat): DominoesState {
        val hands = state.hands.mapIndexed { index, hand ->
            if (index == viewer.index) hand else List(hand.size) { Tile.HIDDEN }
        }
        return state.copy(
            hands = hands,
            boneyard = List(state.boneyard.size) { Tile.HIDDEN },
        )
    }

    override val stateSerializer: KSerializer<DominoesState> = serializer()
    override val moveSerializer: KSerializer<DominoesMove> = serializer()
}
