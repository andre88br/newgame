package io.github.andre88br.newgame.core.games.pife

import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.deckOf
import io.github.andre88br.newgame.core.cards.hidden
import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.DrawReason
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

/** Dois baralhos com um curinga cada. */
const val PIFE_DECKS: Int = 2
const val PIFE_JOKERS_PER_DECK: Int = 1

/** Nove cartas na mão: três grupos de três. */
const val PIFE_HAND_SIZE: Int = 9

/** Cartas por grupo. */
const val PIFE_GROUP_SIZE: Int = 3

/** Grupos necessários para bater. */
const val PIFE_GROUPS: Int = PIFE_HAND_SIZE / PIFE_GROUP_SIZE

/**
 * Quantas vezes o lixo pode voltar a ser monte antes de a mão ser dada por morta.
 *
 * Na mesa isso não é regra escrita: alguém olha para o baralho passando pela terceira vez, o
 * jogo já não anda, e a mão é recomeçada. Aqui precisa ser número, porque comprar e descartar
 * não gastam carta nenhuma — sem um limite, uma mão em que ninguém fecha roda para sempre, e
 * o que na mesa é tédio no aparelho é travamento.
 *
 * Dois é folgado de propósito: são três passagens pelo baralho inteiro, e uma mão de pife se
 * decide em muito menos que isso.
 */
const val PIFE_MAX_RESHUFFLES: Int = 2

/**
 * Um grupo vale?
 *
 * Duas formas, e só: **trinca** (mesmo valor) ou **sequência** (mesmo naipe, valores
 * seguidos). O curinga entra em qualquer uma das duas, tapando o buraco.
 *
 * O ás é carta alta, e só: Q-K-A vale, A-2-3 não. É uma escolha, e a mais comum nas mesas —
 * deixar o ás valer dos dois lados criaria a sequência K-A-2, que não existe em pife nenhum.
 *
 * Grupo só de curinga não vale: seriam três cartas sem carta nenhuma.
 */
fun isGroup(cards: List<Card>): Boolean {
    if (cards.size < PIFE_GROUP_SIZE) return false

    val curingas = cards.count { it.isJoker }
    val naturais = cards.filterNot { it.isJoker }
    if (naturais.isEmpty()) return false

    // Trinca: todas as naturais do mesmo valor.
    if (naturais.all { it.rank == naturais.first().rank }) return true

    // Sequência: mesmo naipe, sem valor repetido, e os buracos cabendo nos curingas.
    if (naturais.any { it.suit != naturais.first().suit }) return false
    val ordens = naturais.map { it.rank.order }.sorted()
    if (ordens.distinct().size != ordens.size) return false

    val vao = ordens.last() - ordens.first() + 1
    if (vao > cards.size) return false
    return (vao - ordens.size) <= curingas
}

/**
 * A mão fecha em [PIFE_GROUPS] grupos?
 *
 * É uma partição, e não uma varredura: a mesma carta pode servir a dois grupos diferentes, e
 * escolher pelo caminho mais óbvio deixaria de fora mãos que fecham por outro arranjo. Fixar
 * a primeira carta e tentar todos os pares que a acompanham cobre todos os arranjos sem
 * repetir nenhum — com nove cartas são poucas centenas de tentativas.
 */
fun formsWinningHand(cards: List<Card>): Boolean =
    cards.size == PIFE_HAND_SIZE && podeParticionar(cards)

private fun podeParticionar(restantes: List<Card>): Boolean {
    if (restantes.isEmpty()) return true
    val primeira = restantes.first()
    val resto = restantes.drop(1)

    for (i in resto.indices) {
        for (j in i + 1 until resto.size) {
            if (!isGroup(listOf(primeira, resto[i], resto[j]))) continue
            val sobra = resto.filterIndexed { index, _ -> index != i && index != j }
            if (podeParticionar(sobra)) return true
        }
    }
    return false
}

/** Quantos grupos completos e disjuntos dá para tirar desta mão. Serve à avaliação da IA. */
fun bestGroupCount(cards: List<Card>): Int {
    if (cards.size < PIFE_GROUP_SIZE) return 0
    var melhor = 0
    for (i in cards.indices) {
        for (j in i + 1 until cards.size) {
            for (k in j + 1 until cards.size) {
                if (!isGroup(listOf(cards[i], cards[j], cards[k]))) continue
                val sobra = cards.filterIndexed { index, _ -> index != i && index != j && index != k }
                melhor = maxOf(melhor, 1 + bestGroupCount(sobra))
            }
        }
    }
    return melhor
}

/** Em que ponto da vez o jogo está. */
@Serializable
enum class PifePhase {
    /** Comprar uma carta: do monte ou a de cima do lixo. */
    DRAW,

    /** Descartar uma, o que fecha a vez. */
    DISCARD,
}

@Serializable
data class PifeState(
    /** A mão de cada cadeira. Some para quem não é dono — veja [PifeGame.redactFor]. */
    val hands: List<List<Card>> = emptyList(),
    /** O monte de compra, oculto. */
    val stock: List<Card> = emptyList(),
    /** O lixo. Só a de cima pode ser comprada, ao contrário da canastra. */
    val discard: List<Card> = emptyList(),
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    val phase: PifePhase = PifePhase.DRAW,
    val rng: Rng = Rng(0),
    /** Quem bateu, ou `-1` enquanto a mão corre. */
    val winner: Int = -1,
    /** Quantas vezes o lixo já voltou a ser monte. Veja [PIFE_MAX_RESHUFFLES]. */
    val reshuffles: Int = 0,
) : GameState {

    val seats: Int get() = hands.size

    fun hand(seat: Seat): List<Card> = hands.getOrElse(seat.index) { emptyList() }

    fun handSize(seat: Seat): Int = hand(seat).size

    /** A carta de cima do lixo: a única que se pode comprar de lá. */
    val discardTop: Card? get() = discard.lastOrNull()

    override fun toString(): String = buildString {
        append("vez=${turn.index} fase=$phase monte=${stock.size} lixo=${discard.size}\n")
        hands.forEachIndexed { index, mao -> append("mão $index: ${mao.joinToString(" ")}\n") }
    }
}

/** O que se pode fazer na vez. */
@Serializable
sealed interface PifeMove : Move {

    @Serializable
    data object DrawStock : PifeMove {
        override fun describe(): String = "compra"
    }

    @Serializable
    data object DrawDiscard : PifeMove {
        override fun describe(): String = "pega do lixo"
    }

    @Serializable
    data class Discard(val card: Card) : PifeMove {
        override fun describe(): String = "descarta $card"
    }
}

/**
 * Pife.
 *
 * Nove cartas, e o jogo inteiro é uma pergunta só: elas fecham em três grupos de três? Cada
 * vez é comprar uma e jogar fora uma, até a mão fechar — trincas e sequências, com curinga
 * tapando buraco.
 *
 * É o mais simples dos quatro jogos de carta, e o mais direto de todos: não há placar,
 * parceria nem fase. Quem fecha primeiro ganha, e acabou.
 *
 * A escolha que dá o jogo é o descarte. Guardar uma carta que serve a dois grupos diferentes
 * custa lentidão; jogá-la fora custa o grupo — e o adversário está olhando o que você
 * descarta, porque a de cima do lixo é comprável.
 */
object PifeGame : BoardGame<PifeState, PifeMove> {

    override val id: GameId = GameId.PIFE

    override val supportedSeats: IntRange = 2..4

    override fun seatsIn(state: PifeState): Int = state.seats

    override val hasHiddenInformation: Boolean = true

    override fun initialState(config: MatchConfig): PifeState {
        val embaralhado = config.rng().shuffle(deckOf(PIFE_DECKS, PIFE_JOKERS_PER_DECK))
        val cartas = embaralhado.value
        val maos = List(config.seats) { index ->
            cartas.subList(index * PIFE_HAND_SIZE, (index + 1) * PIFE_HAND_SIZE).toList()
        }
        val resto = cartas.drop(config.seats * PIFE_HAND_SIZE)

        return PifeState(
            hands = maos,
            stock = resto.drop(1),
            discard = listOf(resto.first()),
            turn = Seat.FIRST,
            phase = PifePhase.DRAW,
            rng = embaralhado.rng,
        )
    }

    override fun legalMoves(state: PifeState): List<PifeMove> {
        if (outcome(state).isOver) return emptyList()
        val mao = state.hand(state.turn)
        if (mao.any { it.isHidden }) return emptyList()

        if (state.phase == PifePhase.DRAW) {
            val saida = mutableListOf<PifeMove>()
            // O monte vazio se remonta com o lixo, então comprar continua valendo enquanto
            // houver o que remontar. Quem cuida disso é `drawFromStock`.
            if (!semCompra(state)) saida += PifeMove.DrawStock
            if (state.discard.isNotEmpty()) saida += PifeMove.DrawDiscard
            return saida
        }
        return mao.distinct().map { PifeMove.Discard(it) }
    }

    override fun applyMove(state: PifeState, move: PifeMove): MoveResult<PifeState> {
        if (outcome(state).isOver) return MoveResult.Illegal(ReasonKey.GAME_OVER)

        when (move) {
            PifeMove.DrawStock -> {
                if (state.phase != PifePhase.DRAW) {
                    return MoveResult.Illegal(ReasonKey.CARD_ALREADY_DREW)
                }
                if (semCompra(state)) return MoveResult.Illegal(ReasonKey.CARD_NOTHING_TO_DRAW)
            }

            PifeMove.DrawDiscard -> {
                if (state.phase != PifePhase.DRAW) {
                    return MoveResult.Illegal(ReasonKey.CARD_ALREADY_DREW)
                }
                if (state.discard.isEmpty()) return MoveResult.Illegal(ReasonKey.CARD_NOTHING_TO_DRAW)
            }

            is PifeMove.Discard -> {
                if (state.phase != PifePhase.DISCARD) {
                    return MoveResult.Illegal(ReasonKey.PIFE_MUST_DRAW_FIRST)
                }
                if (move.card !in state.hand(state.turn)) {
                    return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                }
            }
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    override fun applyKnownLegal(state: PifeState, move: PifeMove): PifeState = when (move) {
        PifeMove.DrawStock -> drawFromStock(state)
        PifeMove.DrawDiscard -> drawFromDiscard(state)
        is PifeMove.Discard -> applyDiscard(state, move)
    }

    /**
     * Compra do monte, remontando-o com o lixo se ele tiver acabado.
     *
     * A carta de cima do lixo fica onde está: ela é a que o próximo pode comprar, e recolhê-la
     * junto apagaria a única informação pública do jogo.
     */
    private fun drawFromStock(state: PifeState): PifeState {
        var monte = state.stock
        var lixo = state.discard
        var rng = state.rng
        var remontagens = state.reshuffles

        if (monte.isEmpty()) {
            val topo = lixo.last()
            val embaralhado = rng.shuffle(lixo.dropLast(1))
            monte = embaralhado.value
            rng = embaralhado.rng
            lixo = listOf(topo)
            remontagens += 1
        }

        return state.copy(
            hands = trocarMao(state, state.hand(state.turn) + monte.first()),
            stock = monte.drop(1),
            discard = lixo,
            rng = rng,
            reshuffles = remontagens,
            phase = PifePhase.DISCARD,
            ply = state.ply + 1,
        )
    }

    private fun drawFromDiscard(state: PifeState): PifeState = state.copy(
        hands = trocarMao(state, state.hand(state.turn) + state.discard.last()),
        discard = state.discard.dropLast(1),
        phase = PifePhase.DISCARD,
        ply = state.ply + 1,
    )

    /**
     * Descarta e, se o que sobrou fecha, bate.
     *
     * A conferência é feita **depois** do descarte, sobre as nove que ficaram: com dez cartas
     * na mão, fechar três grupos e sobrar uma é a mesma coisa que jogar a que sobra fora.
     */
    private fun applyDiscard(state: PifeState, move: PifeMove.Discard): PifeState {
        val mao = state.hand(state.turn).toMutableList()
        mao.remove(move.card)

        val depois = state.copy(
            hands = trocarMao(state, mao.toList()),
            discard = state.discard + move.card,
            ply = state.ply + 1,
        )
        if (formsWinningHand(mao)) return depois.copy(winner = state.turn.index)

        return depois.copy(turn = state.turn.next(state.seats), phase = PifePhase.DRAW)
    }

    private fun trocarMao(state: PifeState, mao: List<Card>): List<List<Card>> =
        state.hands.mapIndexed { index, atual -> if (index == state.turn.index) mao else atual }

    /**
     * Acabou o que comprar: o monte secou e o lixo não volta mais.
     *
     * A conta olha só o tamanho do monte, e não o que há nele — de propósito, para que a
     * resposta seja a mesma vista de qualquer cadeira: a redação vira as cartas do monte para
     * baixo, mas não muda quantas são.
     */
    private fun semCompra(state: PifeState): Boolean =
        state.stock.isEmpty() &&
            (state.reshuffles >= PIFE_MAX_RESHUFFLES || state.discard.size <= 1)

    override fun outcome(state: PifeState): Outcome = when {
        state.winner >= 0 -> Outcome.Win(Seat(state.winner))
        // Só no começo da vez: quem já comprou ainda tem de descartar, e é justamente esse
        // descarte que pode fechar a mão.
        state.phase == PifePhase.DRAW && semCompra(state) -> Outcome.Draw(DrawReason.BLOCKED)
        else -> Outcome.InProgress
    }

    /**
     * A mão dos outros e o monte viram; o lixo fica.
     *
     * O lixo inteiro é público de propósito: em pife o que já foi descartado é a informação
     * que sobra para deduzir a mão alheia, e escondê-la tiraria do jogo a única leitura que
     * ele oferece.
     */
    override fun redactFor(state: PifeState, viewer: Seat): PifeState = state.copy(
        hands = state.hands.mapIndexed { index, mao ->
            if (index == viewer.index) mao else mao.hidden()
        },
        stock = state.stock.hidden(),
    )

    override val stateSerializer: KSerializer<PifeState> = serializer()
    override val moveSerializer: KSerializer<PifeMove> = serializer()
}
