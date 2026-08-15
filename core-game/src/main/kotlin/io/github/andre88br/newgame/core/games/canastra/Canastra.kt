package io.github.andre88br.newgame.core.games.canastra

import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.deckOf
import io.github.andre88br.newgame.core.cards.hidden
import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.ReasonKey
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.next
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** Dois baralhos com dois curingas cada: 108 cartas. */
const val CANASTRA_DECKS: Int = 2
const val CANASTRA_JOKERS_PER_DECK: Int = 2

/** Cartas na mão de cada um, e no morto. */
const val CANASTRA_HAND_SIZE: Int = 11

/** Dois mortos na mesa: cada dupla pega um antes de poder bater. */
const val CANASTRA_MORTOS: Int = 2

/** Um jogo tem no mínimo três cartas. */
const val CANASTRA_MIN_MELD: Int = 3

/** Sete cartas fazem canastra. */
const val CANASTRA_SIZE: Int = 7

/** Curingas por jogo. Mais do que isso vira jogo de curinga, não de carta. */
const val CANASTRA_MAX_WILDS: Int = 3

/** A partida vai até aqui. */
const val CANASTRA_TARGET: Int = 3_000

/** Bônus de bater. */
const val CANASTRA_GOING_OUT_BONUS: Int = 100

/** Vale um três vermelho; os quatro juntos valem o dobro disso cada. */
const val RED_THREE_VALUE: Int = 100

/**
 * **Curinga**: o coringa e o dois.
 *
 * O dois é curinga em canastra, e é por isso que ele vale vinte pontos apesar de ser a carta
 * mais baixa do baralho — o valor segue a utilidade, não a ordem.
 */
fun isWild(card: Card): Boolean = card.isJoker || card.rank == Rank.TWO

/** O três vermelho, que não se joga: vale ponto parado na mesa. */
fun isRedThree(card: Card): Boolean = card.rank == Rank.THREE && card.isRed

/** O três preto, que tranca o lixo. */
fun isBlackThree(card: Card): Boolean = card.rank == Rank.THREE && !card.isRed

/** Quanto a carta vale na contagem. */
fun cardValue(card: Card): Int = when {
    card.isJoker -> 50
    card.rank == Rank.TWO -> 20
    card.rank == Rank.ACE -> 20
    card.rank == Rank.THREE -> 5
    card.rank.order >= Rank.EIGHT.order -> 10
    else -> 5
}

/**
 * Um jogo na mesa: cartas do mesmo valor, com ou sem curinga.
 *
 * A dupla é dona do jogo, não a pessoa — em canastra de quatro, quem começou o jogo e quem o
 * completa costumam ser jogadores diferentes, e a canastra conta para os dois.
 */
@Serializable
data class Meld(val cards: List<Card> = emptyList()) {

    val wilds: List<Card> get() = cards.filter { isWild(it) }

    val naturals: List<Card> get() = cards.filterNot { isWild(it) }

    /** O valor de que é este jogo, ou `null` num jogo só de curinga — que não existe. */
    val rank: Rank? get() = naturals.firstOrNull()?.rank

    val isCanastra: Boolean get() = cards.size >= CANASTRA_SIZE

    /** Canastra sem curinga vale o dobro: é o prêmio de fazer na mão. */
    val isClean: Boolean get() = isCanastra && wilds.isEmpty()

    /** Pontos das cartas mais o prêmio da canastra. */
    val score: Int
        get() = cards.sumOf { cardValue(it) } + when {
            isClean -> 200
            isCanastra -> 100
            else -> 0
        }

    override fun toString(): String = cards.joinToString(" ")
}

/**
 * Um jogo é válido?
 *
 * Três exigências, e cada uma existe por um motivo: três cartas no mínimo, porque duas não
 * formam jogo; ao menos duas cartas naturais, porque um jogo sustentado por curinga não é um
 * jogo; e teto de curingas, senão o baralho de curingas viraria canastra sozinho.
 *
 * O três fica de fora: o vermelho vale ponto parado e o preto só entra na hora de bater.
 */
fun isValidMeld(cards: List<Card>): Boolean {
    if (cards.size < CANASTRA_MIN_MELD) return false
    val naturais = cards.filterNot { isWild(it) }
    val curingas = cards.filter { isWild(it) }
    if (naturais.size < 2) return false
    if (curingas.size > CANASTRA_MAX_WILDS) return false
    if (naturais.any { it.rank == Rank.THREE }) return false
    return naturais.map { it.rank }.distinct().size == 1
}

/** Em que ponto da vez o jogo está. */
@Serializable
enum class CanastraPhase {
    /** Comprar: do monte ou o lixo inteiro. */
    DRAW,

    /** Baixar jogos e descartar. O descarte fecha a vez. */
    PLAY,
}

@Serializable
data class CanastraState(
    /** A mão de cada cadeira. Some para quem não é dono — veja [CanastraGame.redactFor]. */
    val hands: List<List<Card>> = emptyList(),
    /** Os jogos de cada **dupla**, e não de cada pessoa. */
    val melds: List<List<Meld>> = emptyList(),
    /** O monte de compra, oculto. */
    val stock: List<Card> = emptyList(),
    /** O lixo. A última carta é a de cima, e é ela que tranca ou libera a compra. */
    val discard: List<Card> = emptyList(),
    /** Os mortos ainda na mesa, ocultos. */
    val mortos: List<List<Card>> = emptyList(),
    /** Quantos três vermelhos cada dupla tem na mesa. */
    val redThrees: List<Int> = emptyList(),
    /** Se a dupla já pegou um morto. Sem isso não se bate. */
    val tookMorto: List<Boolean> = emptyList(),
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    val phase: CanastraPhase = CanastraPhase.DRAW,
    /** Pontos da partida, por dupla. */
    val scores: List<Int> = emptyList(),
    /** Quantas cadeiras à mesa. Duas ou quatro. */
    val seats: Int = 2,
    val rng: Rng = Rng(0),
    /** A dupla que bateu, ou `-1` enquanto a mão corre. */
    val wentOut: Int = -1,
) : GameState {

    /** Em quatro, as duplas são as cadeiras opostas; em dois, cada um é a sua dupla. */
    fun teamOf(seat: Seat): Int = if (seats == 4) seat.index % 2 else seat.index

    val teams: Int get() = if (seats == 4) 2 else seats

    fun hand(seat: Seat): List<Card> = hands.getOrElse(seat.index) { emptyList() }

    fun handSize(seat: Seat): Int = hand(seat).size

    fun meldsOf(seat: Seat): List<Meld> = melds.getOrElse(teamOf(seat)) { emptyList() }

    /** A carta de cima do lixo, que decide se dá para comprar dali. */
    val discardTop: Card? get() = discard.lastOrNull()

    /** O lixo está trancado: um três preto em cima impede a próxima pessoa de pegá-lo. */
    val discardBlocked: Boolean get() = discardTop?.let { isBlackThree(it) } ?: false

    /** A dupla tem canastra? Sem uma, ninguém bate. */
    fun hasCanastra(team: Int): Boolean =
        melds.getOrElse(team) { emptyList() }.any { it.isCanastra }

    override fun toString(): String = buildString {
        append("vez=${turn.index} fase=$phase monte=${stock.size} lixo=${discard.size}\n")
        hands.forEachIndexed { index, mao -> append("mão $index: ${mao.joinToString(" ")}\n") }
        melds.forEachIndexed { time, jogos ->
            append("dupla $time [${scores.getOrElse(time) { 0 }}]: ${jogos.joinToString(" | ")}\n")
        }
    }
}

/** O que se pode fazer na vez. */
@Serializable
sealed interface CanastraMove : Move {

    /** Comprar do monte. */
    @Serializable
    data object DrawStock : CanastraMove {
        override fun describe(): String = "compra"
    }

    /** Pegar o lixo inteiro. */
    @Serializable
    data object TakeDiscard : CanastraMove {
        override fun describe(): String = "pega o lixo"
    }

    /**
     * Baixar [cards]: jogo novo se [into] for `null`, ou acrescentar a um jogo já na mesa.
     */
    @Serializable
    data class Meld(val cards: List<Card>, val into: Int? = null) : CanastraMove {
        override fun describe(): String =
            cards.joinToString(" ") + if (into != null) " →$into" else ""
    }

    /** Descartar, o que fecha a vez. */
    @Serializable
    data class Discard(val card: Card) : CanastraMove {
        override fun describe(): String = "descarta $card"
    }
}

/**
 * Canastra brasileira, de dois ou de quatro (em duplas).
 *
 * Dois baralhos, quatro curingas, onze cartas para cada um e dois mortos na mesa. A vez tem
 * três tempos: compra-se (do monte ou o lixo inteiro), baixa-se o que quiser, e descarta-se —
 * e é o descarte que passa a vez.
 *
 * **As duas regras do três**, que são o que separa canastra de qualquer outro jogo de
 * formar trincas:
 *
 * - O **três vermelho** não se joga. Ele vale cem pontos parado na mesa, vai para lá sozinho
 *   assim que aparece na mão, e quem o tira do monte compra outra carta no lugar. É ponto de
 *   graça — e é ponto do adversário se a dupla não fizer canastra nenhuma.
 * - O **três preto** tranca o lixo: descartado, impede a pessoa seguinte de pegar o monte de
 *   descarte. É a única carta que se joga contra alguém em vez de a favor de si, e por isso
 *   ela só pode ser baixada na hora de bater — guardá-la custa cinco pontos na mão.
 *
 * Bater exige duas coisas: uma canastra e ter pegado o morto. Ficar sem cartas antes disso
 * não acaba a mão — pega-se o morto e a vez continua.
 */
object CanastraGame : BoardGame<CanastraState, CanastraMove> {

    override val id: GameId = GameId.CANASTRA

    /** De dois (individual) ou de quatro (em duplas). Três não divide em duplas. */
    override val supportedSeats: IntRange = 2..4

    override fun seatsIn(state: CanastraState): Int = state.seats

    override val hasHiddenInformation: Boolean = true

    override fun initialState(config: MatchConfig): CanastraState {
        // Duplas só existem a quatro; a dois e a três joga-se individual, cada um por si.
        // É o que [CanastraState.teamOf] já diz, e o que mantém a promessa de que a mesa
        // pedida é a mesa montada.
        val seats = config.seats
        return dealHand(seats, List(if (seats == 4) 2 else seats) { 0 }, config.rng())
    }

    /** Reparte uma mão: onze para cada um, dois mortos, uma carta virada no lixo. */
    private fun dealHand(seats: Int, scores: List<Int>, rng: Rng): CanastraState {
        val teams = if (seats == 4) 2 else seats
        val embaralhado = rng.shuffle(deckOf(CANASTRA_DECKS, CANASTRA_JOKERS_PER_DECK))
        val cartas = embaralhado.value.toMutableList()

        fun tirar(quantas: Int): List<Card> {
            val saida = cartas.take(quantas)
            repeat(saida.size) { cartas.removeAt(0) }
            return saida
        }

        val maos = MutableList(seats) { tirar(CANASTRA_HAND_SIZE).toMutableList() }
        val mortos = List(CANASTRA_MORTOS) { tirar(CANASTRA_HAND_SIZE) }

        // Três vermelho na mão inicial vai direto para a mesa, e quem o tinha compra outra.
        val vermelhos = MutableList(teams) { 0 }
        for (index in 0 until seats) {
            val time = if (seats == 4) index % 2 else index
            while (true) {
                val achado = maos[index].indexOfFirst { isRedThree(it) }
                if (achado < 0) break
                maos[index].removeAt(achado)
                vermelhos[time] = vermelhos[time] + 1
                if (cartas.isNotEmpty()) maos[index].add(cartas.removeAt(0))
            }
        }

        // A primeira do lixo também não pode ser três vermelho: ele iria para a mesa de
        // quem o comprasse, e no começo não há de quem.
        var primeira = cartas.removeAt(0)
        while (isRedThree(primeira) && cartas.isNotEmpty()) {
            primeira = cartas.removeAt(0)
        }

        return CanastraState(
            hands = maos.map { it.toList() },
            melds = List(teams) { emptyList() },
            stock = cartas.toList(),
            discard = listOf(primeira),
            mortos = mortos,
            redThrees = vermelhos.toList(),
            tookMorto = List(teams) { false },
            turn = Seat.FIRST,
            phase = CanastraPhase.DRAW,
            scores = scores,
            seats = seats,
            rng = embaralhado.rng,
        )
    }

    override fun legalMoves(state: CanastraState): List<CanastraMove> {
        if (outcome(state).isOver || state.wentOut >= 0) return emptyList()
        val mao = state.hand(state.turn)
        if (mao.any { it.isHidden }) return emptyList()

        if (state.phase == CanastraPhase.DRAW) {
            val saida = mutableListOf<CanastraMove>()
            if (state.stock.isNotEmpty()) saida += CanastraMove.DrawStock
            if (state.discard.isNotEmpty() && !state.discardBlocked) saida += CanastraMove.TakeDiscard
            // Monte vazio e lixo trancado: a mão acaba, e `outcome` cuida disso.
            return saida
        }

        return meldMoves(state, mao) + discardMoves(state, mao)
    }

    /**
     * Os jogos que valem a pena oferecer, e não todos os que existem.
     *
     * Enumerar toda combinação de cartas que forma jogo estoura: onze cartas dão milhares de
     * subconjuntos, e a busca da IA morreria neles. Aqui saem os que uma pessoa jogaria — o
     * jogo inteiro de cada valor, e cada carta que encaixa num jogo já baixado. Quem valida
     * é [applyMove], que aceita qualquer jogo bem formado que a tela mandar.
     */
    private fun meldMoves(state: CanastraState, mao: List<Card>): List<CanastraMove> {
        val saida = mutableListOf<CanastraMove>()
        val curingas = mao.filter { isWild(it) }

        // Jogo novo: todas as naturais de um valor, com um curinga se faltar uma.
        val porValor = mao.filterNot { isWild(it) || it.rank == Rank.THREE }.groupBy { it.rank }
        for ((_, iguais) in porValor) {
            if (iguais.size >= CANASTRA_MIN_MELD) {
                saida += CanastraMove.Meld(iguais)
            } else if (iguais.size == 2 && curingas.isNotEmpty()) {
                saida += CanastraMove.Meld(iguais + curingas.first())
            }
        }

        // Acrescentar a um jogo da dupla: cada carta que serve, uma de cada vez.
        state.meldsOf(state.turn).forEachIndexed { index, jogo ->
            for (carta in mao.distinct()) {
                if (canExtend(jogo, carta)) saida += CanastraMove.Meld(listOf(carta), into = index)
            }
        }
        return saida
    }

    /** A carta serve neste jogo? */
    fun canExtend(meld: Meld, card: Card): Boolean {
        if (isRedThree(card) || isBlackThree(card)) return false
        if (isWild(card)) return meld.wilds.size < CANASTRA_MAX_WILDS
        return meld.rank == card.rank
    }

    /**
     * O que dá para descartar.
     *
     * Três vermelho nunca: ele não é carta de jogo, é ponto na mesa. Fora isso, tudo — e
     * descartar um três preto é o lance que tranca o lixo de quem vem a seguir.
     */
    private fun discardMoves(state: CanastraState, mao: List<Card>): List<CanastraMove> {
        // Sem canastra ou sem morto, descartar a última carta deixaria a mão vazia sem poder
        // bater; nesse caso ainda há morto para pegar, e quem cuida disso é `applyKnownLegal`.
        return mao.distinct().filterNot { isRedThree(it) }.map { CanastraMove.Discard(it) }
    }

    override fun applyMove(state: CanastraState, move: CanastraMove): MoveResult<CanastraState> {
        if (outcome(state).isOver) return MoveResult.Illegal(ReasonKey.GAME_OVER)
        val mao = state.hand(state.turn)

        when (move) {
            CanastraMove.DrawStock -> {
                if (state.phase != CanastraPhase.DRAW) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_ALREADY_DREW)
                }
                if (state.stock.isEmpty()) return MoveResult.Illegal(ReasonKey.CANASTRA_STOCK_EMPTY)
            }

            CanastraMove.TakeDiscard -> {
                if (state.phase != CanastraPhase.DRAW) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_ALREADY_DREW)
                }
                if (state.discard.isEmpty()) return MoveResult.Illegal(ReasonKey.CANASTRA_STOCK_EMPTY)
                if (state.discardBlocked) return MoveResult.Illegal(ReasonKey.CANASTRA_PILE_BLOCKED)
            }

            is CanastraMove.Meld -> {
                if (state.phase != CanastraPhase.PLAY) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_MUST_DRAW_FIRST)
                }
                if (!temTodas(mao, move.cards)) {
                    return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                }
                if (move.cards.any { isRedThree(it) }) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_RED_THREE_NOT_PLAYABLE)
                }
                if (move.cards.any { isBlackThree(it) }) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_BLACK_THREE_ONLY_OUT)
                }
                val jogos = state.meldsOf(state.turn)
                if (move.into != null) {
                    val jogo = jogos.getOrNull(move.into)
                        ?: return MoveResult.Illegal(ReasonKey.CANASTRA_NO_SUCH_MELD)
                    if (move.cards.any { !canExtend(jogo, it) }) {
                        return MoveResult.Illegal(ReasonKey.CANASTRA_DOES_NOT_FIT)
                    }
                } else if (!isValidMeld(move.cards)) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_INVALID_MELD)
                }
            }

            is CanastraMove.Discard -> {
                if (state.phase != CanastraPhase.PLAY) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_MUST_DRAW_FIRST)
                }
                if (move.card !in mao) return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                if (isRedThree(move.card)) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_RED_THREE_NOT_PLAYABLE)
                }
            }
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    /** A mão tem todas estas cartas, contando repetidas? O baralho é duplo. */
    private fun temTodas(mao: List<Card>, cartas: List<Card>): Boolean {
        val sobra = mao.toMutableList()
        for (carta in cartas) {
            if (!sobra.remove(carta)) return false
        }
        return true
    }

    override fun applyKnownLegal(state: CanastraState, move: CanastraMove): CanastraState =
        when (move) {
            CanastraMove.DrawStock -> drawFromStock(state)
            CanastraMove.TakeDiscard -> takeDiscard(state)
            is CanastraMove.Meld -> applyMeld(state, move)
            is CanastraMove.Discard -> applyDiscard(state, move)
        }

    /**
     * Compra do monte — e, se vier três vermelho, ele vai para a mesa e compra-se de novo.
     *
     * O laço é o que faz o três vermelho ser ponto de graça em vez de carta morta na mão:
     * ele nunca chega a ficar lá.
     */
    private fun drawFromStock(state: CanastraState): CanastraState {
        var monte = state.stock
        val mao = state.hand(state.turn).toMutableList()
        val vermelhos = state.redThrees.toMutableList()
        val time = state.teamOf(state.turn)

        while (monte.isNotEmpty()) {
            val carta = monte.first()
            monte = monte.drop(1)
            if (isRedThree(carta)) {
                vermelhos[time] = vermelhos[time] + 1
                continue
            }
            mao += carta
            break
        }

        return state.copy(
            hands = trocarMao(state, mao),
            stock = monte,
            redThrees = vermelhos,
            phase = CanastraPhase.PLAY,
            ply = state.ply + 1,
        )
    }

    /** Pega o lixo inteiro. Três vermelho que estiver ali também vai para a mesa. */
    private fun takeDiscard(state: CanastraState): CanastraState {
        val mao = state.hand(state.turn).toMutableList()
        val vermelhos = state.redThrees.toMutableList()
        val time = state.teamOf(state.turn)

        for (carta in state.discard) {
            if (isRedThree(carta)) vermelhos[time] = vermelhos[time] + 1 else mao += carta
        }

        return state.copy(
            hands = trocarMao(state, mao),
            discard = emptyList(),
            redThrees = vermelhos,
            phase = CanastraPhase.PLAY,
            ply = state.ply + 1,
        )
    }

    private fun applyMeld(state: CanastraState, move: CanastraMove.Meld): CanastraState {
        val mao = state.hand(state.turn).toMutableList()
        for (carta in move.cards) mao.remove(carta)

        val time = state.teamOf(state.turn)
        val jogos = state.melds[time].toMutableList()
        if (move.into != null) {
            val antigo = jogos[move.into]
            jogos[move.into] = Meld(antigo.cards + move.cards)
        } else {
            jogos += Meld(move.cards)
        }

        val mesa = state.melds.toMutableList()
        mesa[time] = jogos.toList()

        // Baixar pode esvaziar a mão, e aí pega-se o morto — mas a vez **continua**: ainda
        // falta descartar, agora com as cartas novas.
        return settle(
            semMao(
                state.copy(
                    hands = trocarMao(state, mao),
                    melds = mesa.toList(),
                    ply = state.ply + 1,
                ),
            ),
        )
    }

    /** O descarte fecha a vez — inclusive quando é ele que esvazia a mão e pega o morto. */
    private fun applyDiscard(state: CanastraState, move: CanastraMove.Discard): CanastraState {
        val mao = state.hand(state.turn).toMutableList()
        mao.remove(move.card)

        val depois = semMao(
            state.copy(
                hands = trocarMao(state, mao),
                discard = state.discard + move.card,
                ply = state.ply + 1,
            ),
        )
        if (depois.wentOut >= 0) return settle(depois)
        return settle(
            depois.copy(turn = state.turn.next(state.seats), phase = CanastraPhase.DRAW),
        )
    }

    /**
     * Ficou sem cartas: pega o morto, ou bate.
     *
     * É aqui que a canastra deixa de ser um jogo de formar trincas. Acabar as cartas não
     * termina a mão — se a dupla ainda não pegou morto, ela pega, e continua jogando com
     * onze cartas novas. Só quem já pegou o morto **e** tem canastra bate de verdade.
     */
    private fun semMao(state: CanastraState): CanastraState {
        val cadeira = state.turn
        if (state.hand(cadeira).isNotEmpty()) return state

        val time = state.teamOf(cadeira)
        if (!state.tookMorto[time] && state.mortos.isNotEmpty()) {
            // O morto também pode trazer três vermelho, e ele não vai para a mão: vai para a
            // mesa, com carta comprada no lugar. Sem isto uma mão podia ficar só com três
            // vermelho — que não se joga nem se descarta — e travar a partida sem lance.
            val recebida = absorverVermelhos(state.mortos.first(), state.stock, state.redThrees, time)
            val pegou = state.tookMorto.toMutableList()
            pegou[time] = true
            return state.copy(
                hands = trocarMao(state, recebida.hand),
                stock = recebida.stock,
                redThrees = recebida.redThrees,
                mortos = state.mortos.drop(1),
                tookMorto = pegou.toList(),
            )
        }

        // Sem cartas e sem morto para pegar, a mão acabou para todo mundo. Ter canastra
        // muda o quanto se ganha — quem bate leva bônus, e o três vermelho troca de sinal —,
        // e quem cuida disso é a contagem.
        return state.copy(wentOut = time)
    }

    /**
     * Fecha a mão quando não há mais o que jogar, soma na partida e reparte a seguinte.
     *
     * São duas maneiras de a mão acabar, e as duas passam por aqui: alguém ficou sem cartas,
     * ou o monte secou com o lixo trancado — situação em que ninguém consegue nem comprar, e
     * deixar o jogo assim travaria a tela num turno sem lance nenhum.
     */
    private fun settle(state: CanastraState): CanastraState {
        val travou = state.phase == CanastraPhase.DRAW &&
            state.stock.isEmpty() &&
            (state.discard.isEmpty() || state.discardBlocked)
        if (state.wentOut < 0 && !travou) return state

        val ganhos = scoreHand(state)
        val somados = List(state.teams) { state.scores.getOrElse(it) { 0 } + ganhos[it] }
        if (somados.any { it >= CANASTRA_TARGET }) return state.copy(scores = somados)

        return dealHand(state.seats, somados, state.rng).copy(ply = state.ply)
    }

    /** O resultado de tirar os três vermelhos de um punhado de cartas. */
    private data class Absorvido(
        val hand: List<Card>,
        val stock: List<Card>,
        val redThrees: List<Int>,
    )

    /**
     * Separa os três vermelhos de [cartas]: eles vão para a mesa, e cada um é trocado por uma
     * carta do monte.
     *
     * A troca também precisa de laço: a carta comprada pode ser outro três vermelho. É o
     * mesmo cuidado da compra do monte, e vale para qualquer punhado que chegue a uma mão —
     * a distribuição inicial, o lixo e o morto.
     */
    private fun absorverVermelhos(
        cartas: List<Card>,
        stock: List<Card>,
        redThrees: List<Int>,
        team: Int,
    ): Absorvido {
        val mao = mutableListOf<Card>()
        var monte = stock
        val vermelhos = redThrees.toMutableList()

        val fila = ArrayDeque(cartas)
        while (fila.isNotEmpty()) {
            val carta = fila.removeFirst()
            if (!isRedThree(carta)) {
                mao += carta
                continue
            }
            vermelhos[team] = vermelhos[team] + 1
            if (monte.isNotEmpty()) {
                fila.addLast(monte.first())
                monte = monte.drop(1)
            }
        }
        return Absorvido(mao.toList(), monte, vermelhos.toList())
    }

    private fun trocarMao(state: CanastraState, mao: List<Card>): List<List<Card>> =
        state.hands.mapIndexed { index, atual ->
            if (index == state.turn.index) mao else atual
        }

    /**
     * A mão acabou: conta os pontos e reparte a seguinte, se a partida continuar.
     *
     * A conta é da **dupla**: o que está na mesa soma, o que ficou na mão subtrai, e o três
     * vermelho entra por último — a favor de quem fez canastra, contra quem não fez. Essa
     * última regra é o que impede alguém de guardar três vermelho como ponto garantido sem
     * jogar.
     */
    fun scoreHand(state: CanastraState): List<Int> = List(state.teams) { time ->
        val naMesa = state.melds.getOrElse(time) { emptyList() }.sumOf { it.score }
        val naMao = (0 until state.seats)
            .filter { state.teamOf(Seat(it)) == time }
            .sumOf { state.hand(Seat(it)).sumOf { carta -> cardValue(carta) } }

        val quantos = state.redThrees.getOrElse(time) { 0 }
        // Os quatro na mesma dupla valem o dobro cada; é o prêmio de ter todos.
        val porVermelho = if (quantos == 4) RED_THREE_VALUE * 2 else RED_THREE_VALUE
        val vermelhos = quantos * porVermelho
        val comSinal = if (state.hasCanastra(time)) vermelhos else -vermelhos

        val bateu = if (state.wentOut == time) CANASTRA_GOING_OUT_BONUS else 0
        naMesa - naMao + comSinal + bateu
    }

    override fun outcome(state: CanastraState): Outcome {
        if (state.scores.none { it >= CANASTRA_TARGET }) return Outcome.InProgress
        val maior = state.scores.max()
        val campeao = state.scores.indexOfFirst { it == maior }
        // A cadeira que representa a dupla: em duplas, a primeira das duas.
        return Outcome.Win(Seat(campeao))
    }

    /** Baixar carta é o lance que muda a mesa; a tela dá destaque a ele. */
    override fun isCapture(state: CanastraState, move: CanastraMove): Boolean =
        move is CanastraMove.Meld

    /**
     * A mão dos outros vira, e o monte e os mortos também.
     *
     * Os jogos na mesa ficam abertos — eles são públicos, e esconder o que já foi baixado
     * seria esconder do jogador o próprio tabuleiro. O que some é o que ninguém pode ver.
     */
    override fun redactFor(state: CanastraState, viewer: Seat): CanastraState = state.copy(
        hands = state.hands.mapIndexed { index, mao ->
            if (index == viewer.index) mao else mao.hidden()
        },
        stock = state.stock.hidden(),
        mortos = state.mortos.map { it.hidden() },
    )

    override val stateSerializer: KSerializer<CanastraState> = serializer()
    override val moveSerializer: KSerializer<CanastraMove> = serializer()
}
