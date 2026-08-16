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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

const val CANASTRA_DECKS: Int = 2
const val CANASTRA_JOKERS_PER_DECK: Int = 2
const val CANASTRA_HAND_SIZE: Int = 13
fun mortosFor(seats: Int): Int = if (seats == 4) 0 else 1
const val CANASTRA_MIN_MELD: Int = 3
const val CANASTRA_SIZE: Int = 7
const val CANASTRA_MAX_WILDS: Int = 1
const val CANASTRA_TARGET: Int = 3_000
const val CANASTRA_GOING_OUT_BONUS: Int = 50
const val RED_THREE_VALUE: Int = 100
const val CANASTRA_OPENING_THRESHOLD: Int = 1500
const val CANASTRA_OPENING_MIN_VALUE: Int = 150

fun isWild(card: Card): Boolean = card.isJoker || card.rank == Rank.TWO
fun isRedThree(card: Card): Boolean = card.rank == Rank.THREE && card.isRed
fun isBlackThree(card: Card): Boolean = card.rank == Rank.THREE && !card.isRed

fun cardValue(card: Card): Int = when {
    card.isJoker -> 50
    card.rank == Rank.TWO -> 50
    card.rank == Rank.THREE -> 100
    card.rank == Rank.ACE -> 20
    card.rank.order >= Rank.EIGHT.order -> 10
    else -> 5
}

val CANASTRA_SEQUENCE_RANKS: List<Rank> = listOf(
    Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
    Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE,
)

fun sequenceOrder(rank: Rank): Int = CANASTRA_SEQUENCE_RANKS.indexOf(rank)

@Serializable
enum class MeldKind { SEQUENCE, SET }

@Serializable
data class Meld(val cards: List<Card> = emptyList()) {
    val wilds: List<Card> get() = cards.filter { isWild(it) }
    val naturals: List<Card> get() = cards.filterNot { isWild(it) }
    val rank: Rank? get() = naturals.firstOrNull()?.rank
    val isCanastra: Boolean get() = cards.size >= CANASTRA_SIZE
    val isClean: Boolean get() = isCanastra && cards.none { it.rank == Rank.TWO }

    val kind: MeldKind
        get() = if (naturals.size >= 2 && naturals.map { it.rank }.distinct().size == 1) {
            MeldKind.SET
        } else {
            MeldKind.SEQUENCE
        }

    fun sequenceSpan(): IntRange {
        val indice = cards.indexOfFirst { !isWild(it) }
        val posicao = sequenceOrder(cards[indice].rank)
        val inicio = posicao - indice
        return inicio until (inicio + cards.size)
    }

    val score: Int
        get() = cards.sumOf { cardValue(it) } + when {
            isClean -> 200 + (cards.size - CANASTRA_SIZE) * 100
            isCanastra -> 100
            else -> 0
        }

    override fun toString(): String = cards.joinToString(" ")
}

fun isValidSet(cards: List<Card>): Boolean {
    if (cards.size < CANASTRA_MIN_MELD) return false
    val naturais = cards.filterNot { isWild(it) }
    val curingas = cards.filter { isWild(it) }
    if (naturais.size < 2) return false
    if (curingas.size > CANASTRA_MAX_WILDS) return false
    if (naturais.any { it.rank == Rank.THREE }) return false
    return naturais.map { it.rank }.distinct().size == 1
}

fun asSequence(cards: List<Card>): List<Card>? {
    if (cards.size < CANASTRA_MIN_MELD) return null
    val naturais = cards.filterNot { isWild(it) }
    val curingas = cards.filter { isWild(it) }
    if (curingas.size > CANASTRA_MAX_WILDS) return null
    if (naturais.size < 2) return null
    if (naturais.any { it.rank == Rank.THREE }) return null
    if (naturais.any { sequenceOrder(it.rank) < 0 }) return null
    val naipe = naturais.first().suit
    if (naturais.any { it.suit != naipe }) return null

    val ordens = naturais.map { sequenceOrder(it.rank) }
    if (ordens.distinct().size != ordens.size) return null
    val ordenadas = ordens.sorted()
    val menor = ordenadas.first()
    val maior = ordenadas.last()
    val vao = maior - menor + 1

    val curinga = curingas.firstOrNull()
    val posicoes: List<Int> = when {
        curinga == null -> {
            if (vao != naturais.size) return null
            ordenadas
        }
        vao == naturais.size + 1 -> {
            val buraco = (menor..maior).first { it !in ordenadas }
            ordenadas + buraco
        }
        vao == naturais.size -> {
            when {
                maior + 1 < CANASTRA_SEQUENCE_RANKS.size -> ordenadas + (maior + 1)
                menor - 1 >= 0 -> ordenadas + (menor - 1)
                else -> return null
            }
        }
        else -> return null
    }

    return posicoes.sorted().map { posicao ->
        naturais.firstOrNull { sequenceOrder(it.rank) == posicao } ?: curinga!!
    }
}

fun asMeld(cards: List<Card>): Meld? {
    asSequence(cards)?.let { return Meld(it) }
    if (isValidSet(cards)) return Meld(cards)
    return null
}

fun extendMeld(meld: Meld, card: Card): Meld? {
    if (isRedThree(card) || isBlackThree(card)) return null
    return when (meld.kind) {
        MeldKind.SET -> extendSet(meld, card)
        MeldKind.SEQUENCE -> extendSequence(meld, card)
    }
}

private fun extendSet(meld: Meld, card: Card): Meld? = when {
    isWild(card) -> if (meld.wilds.size < CANASTRA_MAX_WILDS) Meld(meld.cards + card) else null
    meld.rank == card.rank -> Meld(meld.cards + card)
    else -> null
}

private fun extendSequence(meld: Meld, card: Card): Meld? = asSequence(meld.cards + card)?.let { Meld(it) }

fun wildRepresents(meld: Meld): Card? {
    if (meld.kind != MeldKind.SEQUENCE || meld.wilds.isEmpty()) return null
    val indice = meld.cards.indexOfFirst { isWild(it) }
    val posicao = meld.sequenceSpan().first + indice
    val rank = CANASTRA_SEQUENCE_RANKS.getOrNull(posicao) ?: return null
    return Card(rank, meld.naturals.first().suit)
}

@Serializable
enum class CanastraPhase { DRAW, PLAY }

@Serializable
data class RoundScore(
    val pontosMesa: Int,
    val penalidadeMao: Int,
    val vermelhos: Int,
    val batida: Int,
    val totalRodada: Int
)

@Serializable
data class CanastraState(
    val hands: List<List<Card>> = emptyList(),
    val melds: List<List<Meld>> = emptyList(),
    val stock: List<Card> = emptyList(),
    val discard: List<Card> = emptyList(),
    val mortos: List<List<Card>> = emptyList(),
    val redThrees: List<Int> = emptyList(),
    val tookMorto: List<Boolean> = emptyList(),
    val batidas: List<Int> = emptyList(),
    val firstMeldDone: List<Boolean> = emptyList(),
    val openingProgress: List<Int> = emptyList(),
    val owedCard: Card? = null,
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    val phase: CanastraPhase = CanastraPhase.DRAW,
    val scores: List<Int> = emptyList(),
    val seats: Int = 2,
    val rng: Rng = Rng(0),
    val wentOut: Int = -1,
    val startingSeat: Seat = Seat.FIRST,
    val lastScores: List<RoundScore> = emptyList(),
    val passedEnd: Boolean = false,
    /** A carta que foi recém-comprada do monte, para ser destacada na tela. */
    val drawnCard: Card? = null,
) : GameState {

    fun teamOf(seat: Seat): Int = if (seats == 4) seat.index % 2 else seat.index
    val teams: Int get() = if (seats == 4) 2 else seats
    fun hand(seat: Seat): List<Card> = hands.getOrElse(seat.index) { emptyList() }
    fun handSize(seat: Seat): Int = hand(seat).size
    fun meldsOf(seat: Seat): List<Meld> = melds.getOrElse(teamOf(seat)) { emptyList() }

    val discardTop: Card? get() = discard.lastOrNull()
    val discardBlocked: Boolean get() = discardTop?.let { isBlackThree(it) || isWild(it) } ?: false

    fun hasCanastra(team: Int): Boolean = melds.getOrElse(team) { emptyList() }.any { it.isCanastra }

    override fun toString(): String = buildString {
        append("vez=${turn.index} fase=$phase monte=${stock.size} lixo=${discard.size}\n")
        hands.forEachIndexed { index, mao -> append("mão $index: ${mao.joinToString(" ")}\n") }
        melds.forEachIndexed { time, jogos ->
            append("dupla $time [${scores.getOrElse(time) { 0 }}]: ${jogos.joinToString(" | ")}\n")
        }
    }
}

@Serializable
sealed interface CanastraMove : Move {
    @Serializable
    data object DrawStock : CanastraMove { override fun describe(): String = "compra" }

    @Serializable
    data object TakeDiscard : CanastraMove { override fun describe(): String = "pega o lixo" }

    @Serializable
    data object Pass : CanastraMove { override fun describe(): String = "encerra a mão (sem monte)" }

    @Serializable
    data class Meld(val cards: List<Card>, val into: Int? = null) : CanastraMove {
        override fun describe(): String = cards.joinToString(" ") + if (into != null) " →$into" else ""
    }

    @Serializable
    data class SwapWild(val into: Int, val card: Card) : CanastraMove {
        override fun describe(): String = "$card troca o curinga em →$into"
    }

    @Serializable
    data class Discard(val card: Card) : CanastraMove {
        override fun describe(): String = "descarta $card"
    }
}

object CanastraGame : BoardGame<CanastraState, CanastraMove> {

    override val id: GameId = GameId.CANASTRA
    override val supportedSeats: IntRange = 2..4
    override fun seatsIn(state: CanastraState): Int = state.seats
    override val hasHiddenInformation: Boolean = true

    override fun initialState(config: MatchConfig): CanastraState {
        val seats = config.seats
        return dealHand(seats, List(if (seats == 4) 2 else seats) { 0 }, config.rng())
    }

    private fun dealHand(seats: Int, scores: List<Int>, rng: Rng, startingSeat: Seat = Seat.FIRST, lastScores: List<RoundScore> = emptyList()): CanastraState {
        val teams = if (seats == 4) 2 else seats
        val embaralhado = rng.shuffle(deckOf(CANASTRA_DECKS, CANASTRA_JOKERS_PER_DECK))
        val cartas = embaralhado.value.toMutableList()

        fun tirar(quantas: Int): List<Card> {
            val saida = cartas.take(quantas)
            repeat(saida.size) { cartas.removeAt(0) }
            return saida
        }

        val maos = MutableList(seats) { tirar(CANASTRA_HAND_SIZE).toMutableList() }
        val mortos = List(mortosFor(seats)) { tirar(CANASTRA_HAND_SIZE) }

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

        return CanastraState(
            hands = maos.map { it.toList() },
            melds = List(teams) { emptyList() },
            stock = cartas.toList(),
            discard = emptyList(),
            mortos = mortos,
            redThrees = vermelhos.toList(),
            tookMorto = List(teams) { false },
            batidas = List(teams) { 0 },
            firstMeldDone = List(teams) { false },
            openingProgress = List(teams) { 0 },
            turn = startingSeat,
            phase = CanastraPhase.DRAW,
            scores = scores,
            seats = seats,
            rng = embaralhado.rng,
            startingSeat = startingSeat,
            lastScores = lastScores,
            passedEnd = false,
            drawnCard = null // Limpa o destaque da carta na nova rodada
        )
    }

    override fun legalMoves(state: CanastraState): List<CanastraMove> {
        if (outcome(state).isOver || state.wentOut >= 0 || state.passedEnd) return emptyList()
        val mao = state.hand(state.turn)
        if (mao.any { it.isHidden }) return emptyList()

        if (state.phase == CanastraPhase.DRAW) {
            val saida = mutableListOf<CanastraMove>()
            if (state.stock.isNotEmpty()) {
                saida += CanastraMove.DrawStock
            } else {
                saida += CanastraMove.Pass
            }
            if (state.discard.isNotEmpty() && !state.discardBlocked && canTakeDiscard(state)) {
                saida += CanastraMove.TakeDiscard
            }
            return saida
        }

        val jogos = meldMoves(state, mao)
        val devida = state.owedCard
        if (devida != null) {
            return jogos.filter { move ->
                when (move) {
                    is CanastraMove.Meld -> devida in move.cards
                    is CanastraMove.SwapWild -> move.card == devida
                    else -> false
                }
            }
        }

        if (openingIncomplete(state, state.teamOf(state.turn))) {
            return jogos
        }
        
        return jogos + discardMoves(state, mao)
    }

    private fun openingIncomplete(state: CanastraState, team: Int): Boolean {
        if (state.firstMeldDone.getOrElse(team) { false }) return false
        if (state.scores.getOrElse(team) { 0 } < CANASTRA_OPENING_THRESHOLD) return false
        val progresso = state.openingProgress.getOrElse(team) { 0 }
        return progresso in 1 until CANASTRA_OPENING_MIN_VALUE
    }

    private fun meldMoves(state: CanastraState, mao: List<Card>): List<CanastraMove> {
        val saida = mutableListOf<CanastraMove>()
        saida += newMeldCandidates(state, mao).map { CanastraMove.Meld(it.cards) }

        state.meldsOf(state.turn).forEachIndexed { index, jogo ->
            for (carta in mao.distinct()) {
                if (extendMeld(jogo, carta) != null) saida += CanastraMove.Meld(listOf(carta), into = index)
            }
            wildRepresents(jogo)?.let { exata -> if (exata in mao) saida += CanastraMove.SwapWild(index, exata) }
        }

        return saida.filterNot { move ->
            when (move) {
                is CanastraMove.Meld ->
                    encurrala(state, mao.size, move.cards.size, teraCanastra(state, move)) ||
                        !isOpeningPathPreserved(state, mao, move)
                
                is CanastraMove.SwapWild -> {
                    val jogo = state.meldsOf(state.turn).getOrNull(move.into)
                    (jogo != null && encurrala(state, mao.size, 1, teraCanastraSwap(state, move))) ||
                        !isOpeningPathPreserved(state, mao, move)
                }
                else -> false
            }
        }
    }

    private fun newMeldCandidates(state: CanastraState, hand: List<Card>): List<Meld> {
        val saida = mutableListOf<Meld>()
        val curingas = hand.filter { isWild(it) }
        val naturaisMao = hand.filterNot { isWild(it) || it.rank == Rank.THREE }

        val porNaipe = naturaisMao.groupBy { it.suit }
        for ((_, cartasDoNaipe) in porNaipe) {
            val porOrdem = cartasDoNaipe.distinctBy { it.rank }.associateBy { sequenceOrder(it.rank) }
            val ordens = porOrdem.keys.sorted()

            var inicio = 0
            while (inicio < ordens.size) {
                var fim = inicio
                while (fim + 1 < ordens.size && ordens[fim + 1] == ordens[fim] + 1) fim++
                val corrida = ordens.subList(inicio, fim + 1).map { porOrdem.getValue(it) }
                if (corrida.size >= CANASTRA_MIN_MELD) saida += Meld(corrida)
                if (corrida.size >= 2 && curingas.isNotEmpty()) {
                    val comCuringa = corrida + curingas.first()
                    asSequence(comCuringa)?.let { saida += Meld(it) }
                }
                inicio = fim + 1
            }

            if (curingas.isNotEmpty()) {
                for (i in ordens.indices) {
                    for (j in i + 1 until ordens.size) {
                        if (ordens[j] - ordens[i] != 2) continue
                        val par = listOf(porOrdem.getValue(ordens[i]), porOrdem.getValue(ordens[j]), curingas.first())
                        asSequence(par)?.let { saida += Meld(it) }
                    }
                }
            }
        }

        if (state.hasCanastra(state.teamOf(state.turn))) {
            val porValor = naturaisMao.groupBy { it.rank }
            for ((_, iguais) in porValor) {
                if (iguais.size >= CANASTRA_MIN_MELD) {
                    saida += Meld(iguais)
                } else if (iguais.size == 2 && curingas.isNotEmpty()) {
                    saida += Meld(iguais + curingas.first())
                }
            }
        }

        return saida
    }

    private fun maxOpeningScore(state: CanastraState, hand: List<Card>, faltam: Int): Int {
        val candidates = newMeldCandidates(state, hand)
        if (candidates.isEmpty()) return 0
        var max = 0
        for (cand in candidates) {
            val nextHand = hand.toMutableList()
            var canForm = true
            for (c in cand.cards) {
                if (!nextHand.remove(c)) {
                    canForm = false
                    break
                }
            }
            if (canForm) {
                val score = cand.cards.sumOf { cardValue(it) }
                if (score >= faltam) return score
                
                val total = score + maxOpeningScore(state, nextHand, faltam - score)
                if (total >= faltam) return total
                if (total > max) max = total
            }
        }
        return max
    }

    private fun isOpeningPathPreserved(state: CanastraState, mao: List<Card>, move: CanastraMove): Boolean {
        val team = state.teamOf(state.turn)
        if (!openingIncomplete(state, team) && (state.scores.getOrElse(team) { 0 } >= CANASTRA_OPENING_THRESHOLD) == false) return true
        if (state.firstMeldDone.getOrElse(team) { false }) return true
        if (state.scores.getOrElse(team) { 0 } < CANASTRA_OPENING_THRESHOLD) return true
        
        val pontosAdicionais = when (move) {
            is CanastraMove.Meld -> move.cards.sumOf { cardValue(it) }
            is CanastraMove.SwapWild -> cardValue(move.card)
            else -> 0
        }
        
        val jaBaixado = state.openingProgress.getOrElse(team) { 0 }
        if (jaBaixado + pontosAdicionais >= CANASTRA_OPENING_MIN_VALUE) return true
        
        val faltam = CANASTRA_OPENING_MIN_VALUE - (jaBaixado + pontosAdicionais)
        val removedCards = when (move) {
            is CanastraMove.Meld -> move.cards
            is CanastraMove.SwapWild -> listOf(move.card)
            else -> emptyList()
        }
        
        val remainingHand = mao.toMutableList()
        for (c in removedCards) {
            remainingHand.remove(c)
        }
        
        val maxPossivel = maxOpeningScore(state, remainingHand, faltam)
        return maxPossivel >= faltam
    }

    private fun canPlayCard(state: CanastraState, hand: List<Card>, card: Card): Boolean =
        meldMoves(state, hand).any { move ->
            when (move) {
                is CanastraMove.Meld -> card in move.cards
                is CanastraMove.SwapWild -> move.card == card
                else -> false
            }
        }

    private fun canTakeDiscard(state: CanastraState): Boolean {
        val topo = state.discardTop ?: return false
        val maoDepois = state.hand(state.turn) + state.discard.filterNot { isRedThree(it) }
        return canPlayCard(state, maoDepois, topo)
    }

    private fun temMortoParaPegar(state: CanastraState, team: Int): Boolean =
        state.mortos.isNotEmpty() && !state.tookMorto.getOrElse(team) { false }

    private fun podeZerar(state: CanastraState, team: Int, teraCanastra: Boolean): Boolean =
        temMortoParaPegar(state, team) || teraCanastra

    private fun teraCanastra(state: CanastraState, move: CanastraMove.Meld): Boolean {
        val time = state.teamOf(state.turn)
        if (state.hasCanastra(time)) return true
        val tamanho = if (move.into != null) {
            (state.meldsOf(state.turn).getOrNull(move.into)?.cards?.size ?: 0) + move.cards.size
        } else {
            move.cards.size
        }
        return tamanho >= CANASTRA_SIZE
    }

    private fun teraCanastraSwap(state: CanastraState, move: CanastraMove.SwapWild): Boolean {
        val time = state.teamOf(state.turn)
        if (state.hasCanastra(time)) return true
        val tamanho = (state.meldsOf(state.turn).getOrNull(move.into)?.cards?.size ?: 0) + 1
        return tamanho >= CANASTRA_SIZE
    }

    private fun encurrala(state: CanastraState, handSize: Int, cartasRemovidas: Int, teraCanastra: Boolean): Boolean {
        val restante = handSize - cartasRemovidas
        if (restante >= 2) return false
        return !podeZerar(state, state.teamOf(state.turn), teraCanastra)
    }

    private fun discardMoves(state: CanastraState, mao: List<Card>): List<CanastraMove> {
        return mao.distinct().filterNot { isRedThree(it) }.map { CanastraMove.Discard(it) }
    }

    fun canMeld(state: CanastraState, cards: List<Card>): Boolean =
        cards.isNotEmpty() && applyMove(state, CanastraMove.Meld(cards)) is MoveResult.Ok

    override fun applyMove(state: CanastraState, move: CanastraMove): MoveResult<CanastraState> {
        if (outcome(state).isOver) return MoveResult.Illegal(ReasonKey.GAME_OVER)
        val mao = state.hand(state.turn)

        val devida = state.owedCard
        if (devida != null) {
            val cumpre = when (move) {
                is CanastraMove.Meld -> devida in move.cards
                is CanastraMove.SwapWild -> move.card == devida
                else -> false
            }
            if (!cumpre) return MoveResult.Illegal(ReasonKey.CANASTRA_OWED_CARD_FIRST)
        }

        when (move) {
            CanastraMove.DrawStock -> {
                if (state.phase != CanastraPhase.DRAW) return MoveResult.Illegal(ReasonKey.CARD_ALREADY_DREW)
                if (state.stock.isEmpty()) return MoveResult.Illegal(ReasonKey.CARD_NOTHING_TO_DRAW)
            }
            CanastraMove.Pass -> {
                if (state.phase != CanastraPhase.DRAW) return MoveResult.Illegal(ReasonKey.CARD_ALREADY_DREW)
                if (state.stock.isNotEmpty()) return MoveResult.Illegal(ReasonKey.CARD_ALREADY_DREW)
            }
            CanastraMove.TakeDiscard -> {
                if (state.phase != CanastraPhase.DRAW) return MoveResult.Illegal(ReasonKey.CARD_ALREADY_DREW)
                if (state.discard.isEmpty()) return MoveResult.Illegal(ReasonKey.CARD_NOTHING_TO_DRAW)
                if (state.discardBlocked) return MoveResult.Illegal(ReasonKey.CANASTRA_PILE_BLOCKED)
                if (!canTakeDiscard(state)) return MoveResult.Illegal(ReasonKey.CANASTRA_PILE_NEEDS_MELD)
            }
            is CanastraMove.Meld -> {
                if (state.phase != CanastraPhase.PLAY) return MoveResult.Illegal(ReasonKey.CANASTRA_MUST_DRAW_FIRST)
                if (!temTodas(mao, move.cards)) return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                if (move.cards.any { isRedThree(it) }) return MoveResult.Illegal(ReasonKey.CANASTRA_RED_THREE_NOT_PLAYABLE)
                if (move.cards.any { isBlackThree(it) }) return MoveResult.Illegal(ReasonKey.CANASTRA_BLACK_THREE_NEVER_MELDS)
                
                if (move.into != null) {
                    var atual = state.meldsOf(state.turn).getOrNull(move.into)
                        ?: return MoveResult.Illegal(ReasonKey.CANASTRA_NO_SUCH_MELD)
                    for (carta in move.cards) {
                        atual = extendMeld(atual, carta) ?: return MoveResult.Illegal(ReasonKey.CANASTRA_DOES_NOT_FIT)
                    }
                } else {
                    val jogo = asMeld(move.cards) ?: return MoveResult.Illegal(ReasonKey.CANASTRA_INVALID_MELD)
                    if (jogo.kind == MeldKind.SET && !state.hasCanastra(state.teamOf(state.turn))) {
                        return MoveResult.Illegal(ReasonKey.CANASTRA_TRINCA_NEEDS_CANASTRA)
                    }
                }
                
                if (!isOpeningPathPreserved(state, mao, move)) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_OPENING_MELD_TOO_LOW)
                }
                if (encurrala(state, mao.size, move.cards.size, teraCanastra(state, move))) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_NEEDS_CANASTRA_TO_GO_OUT)
                }
            }
            is CanastraMove.SwapWild -> {
                if (state.phase != CanastraPhase.PLAY) return MoveResult.Illegal(ReasonKey.CANASTRA_MUST_DRAW_FIRST)
                if (move.card !in mao) return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                val jogo = state.meldsOf(state.turn).getOrNull(move.into)
                    ?: return MoveResult.Illegal(ReasonKey.CANASTRA_NO_SUCH_MELD)
                val esperada = wildRepresents(jogo)
                    ?: return MoveResult.Illegal(ReasonKey.CANASTRA_NO_WILD_TO_SWAP)
                if (move.card != esperada) return MoveResult.Illegal(ReasonKey.CANASTRA_DOES_NOT_FIT)
                
                if (!isOpeningPathPreserved(state, mao, move)) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_OPENING_MELD_TOO_LOW)
                }
                if (encurrala(state, mao.size, 1, teraCanastraSwap(state, move))) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_NEEDS_CANASTRA_TO_GO_OUT)
                }
            }
            is CanastraMove.Discard -> {
                if (state.phase != CanastraPhase.PLAY) return MoveResult.Illegal(ReasonKey.CANASTRA_MUST_DRAW_FIRST)
                if (move.card !in mao) return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                if (isRedThree(move.card)) return MoveResult.Illegal(ReasonKey.CANASTRA_RED_THREE_NOT_PLAYABLE)
                if (openingIncomplete(state, state.teamOf(state.turn))) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_OPENING_MELD_INCOMPLETE)
                }
            }
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

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
            CanastraMove.Pass -> settle(state.copy(passedEnd = true))
            CanastraMove.TakeDiscard -> takeDiscard(state)
            is CanastraMove.Meld -> applyMeld(state, move)
            is CanastraMove.SwapWild -> applySwapWild(state, move)
            is CanastraMove.Discard -> applyDiscard(state, move)
        }

    private fun drawFromStock(state: CanastraState): CanastraState {
        var monte = state.stock
        val mao = state.hand(state.turn).toMutableList()
        val vermelhos = state.redThrees.toMutableList()
        val time = state.teamOf(state.turn)

        var drawn: Card? = null
        while (monte.isNotEmpty()) {
            val carta = monte.first()
            monte = monte.drop(1)
            if (isRedThree(carta)) {
                vermelhos[time] = vermelhos[time] + 1
                continue
            }
            mao += carta
            drawn = carta
            break
        }

        return state.copy(
            hands = trocarMao(state, mao),
            stock = monte,
            redThrees = vermelhos,
            phase = CanastraPhase.PLAY,
            ply = state.ply + 1,
            openingProgress = zerarProgresso(state, time),
            drawnCard = drawn, // Salva a carta recém-comprada para destacar na tela
        )
    }

    private fun zerarProgresso(state: CanastraState, team: Int): List<Int> =
        state.openingProgress.toMutableList().also {
            while (it.size <= team) it.add(0)
            it[team] = 0
        }.toList()

    private fun takeDiscard(state: CanastraState): CanastraState {
        val devida = state.discardTop
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
            owedCard = devida,
            openingProgress = zerarProgresso(state, time),
            drawnCard = null, // Ao pegar lixo, garantimos que a marcação de carta comprada zera
        )
    }

    private fun applyMeld(state: CanastraState, move: CanastraMove.Meld): CanastraState {
        val mao = state.hand(state.turn).toMutableList()
        for (carta in move.cards) mao.remove(carta)

        val time = state.teamOf(state.turn)
        val jogos = state.melds[time].toMutableList()
        if (move.into != null) {
            var atual = jogos[move.into]
            for (carta in move.cards) atual = extendMeld(atual, carta)!!
            jogos[move.into] = atual
        } else {
            jogos += asMeld(move.cards)!!
        }

        val mesa = state.melds.toMutableList()
        mesa[time] = jogos.toList()

        val abertura = updateOpening(state, time, move.cards.sumOf { cardValue(it) })
        val devida = if (state.owedCard != null && state.owedCard in move.cards) null else state.owedCard
        
        // Se a carta comprada for baixada na mesa, tiramos o destaque visual dela
        val novaComprada = if (state.drawnCard != null && state.drawnCard in move.cards) null else state.drawnCard

        return settle(
            semMao(
                state.copy(
                    hands = trocarMao(state, mao),
                    melds = mesa.toList(),
                    firstMeldDone = abertura.firstMeldDone,
                    openingProgress = abertura.openingProgress,
                    owedCard = devida,
                    drawnCard = novaComprada,
                    ply = state.ply + 1,
                ),
            ),
        )
    }

    private data class Abertura(val firstMeldDone: List<Boolean>, val openingProgress: List<Int>)

    private fun updateOpening(state: CanastraState, team: Int, pontos: Int): Abertura {
        if (state.firstMeldDone.getOrElse(team) { false }) {
            return Abertura(state.firstMeldDone, state.openingProgress)
        }
        val abaixoDoLimiar = state.scores.getOrElse(team) { 0 } < CANASTRA_OPENING_THRESHOLD
        val novoProgresso = state.openingProgress.getOrElse(team) { 0 } + pontos
        val completou = abaixoDoLimiar || novoProgresso >= CANASTRA_OPENING_MIN_VALUE

        val firstMeldDone = if (completou) {
            state.firstMeldDone.toMutableList().also {
                while (it.size <= team) it.add(false)
                it[team] = true
            }.toList()
        } else {
            state.firstMeldDone
        }
        val openingProgress = state.openingProgress.toMutableList().also {
            while (it.size <= team) it.add(0)
            it[team] = novoProgresso
        }.toList()
        return Abertura(firstMeldDone, openingProgress)
    }

    private fun applySwapWild(state: CanastraState, move: CanastraMove.SwapWild): CanastraState {
        val mao = state.hand(state.turn).toMutableList()
        mao.remove(move.card)

        val time = state.teamOf(state.turn)
        val jogos = state.melds[time].toMutableList()
        val antigo = jogos[move.into]
        val indiceCuringa = antigo.cards.indexOfFirst { isWild(it) }
        val curinga = antigo.cards[indiceCuringa]
        val semCuringa = antigo.cards.toMutableList().also { it[indiceCuringa] = move.card }
        jogos[move.into] = Meld(semCuringa + curinga)

        val mesa = state.melds.toMutableList()
        mesa[time] = jogos.toList()

        val devida = if (state.owedCard == move.card) null else state.owedCard
        // Se a carta trocada for a recém comprada, tiramos o destaque visual
        val novaComprada = if (state.drawnCard == move.card) null else state.drawnCard
        val abertura = updateOpening(state, time, cardValue(move.card))

        return settle(
            semMao(
                state.copy(
                    hands = trocarMao(state, mao),
                    melds = mesa.toList(),
                    firstMeldDone = abertura.firstMeldDone,
                    openingProgress = abertura.openingProgress,
                    owedCard = devida,
                    drawnCard = novaComprada,
                    ply = state.ply + 1,
                ),
            ),
        )
    }

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
        
        val proximoTurno = Seat((state.turn.index + state.seats - 1) % state.seats)
        return settle(
            depois.copy(
                turn = proximoTurno, 
                phase = CanastraPhase.DRAW,
                drawnCard = null // Zera a carta destacada ao passar a vez
            ),
        )
    }

    private fun semMao(state: CanastraState): CanastraState {
        val cadeira = state.turn
        if (state.hand(cadeira).isNotEmpty()) return state

        val time = state.teamOf(cadeira)
        val batidas = state.batidas.toMutableList()
        batidas[time] = batidas[time] + 1

        if (temMortoParaPegar(state, time)) {
            val recebida = absorverVermelhos(state.mortos.first(), state.stock, state.redThrees, time)
            val pegou = state.tookMorto.toMutableList()
            pegou[time] = true
            return state.copy(
                hands = trocarMao(state, recebida.hand),
                stock = recebida.stock,
                redThrees = recebida.redThrees,
                mortos = state.mortos.drop(1),
                tookMorto = pegou.toList(),
                batidas = batidas.toList(),
            )
        }

        return state.copy(wentOut = time, batidas = batidas.toList())
    }

    private fun settle(state: CanastraState): CanastraState {
        val travou = state.passedEnd || (state.phase == CanastraPhase.DRAW &&
            state.stock.isEmpty() &&
            (state.discard.isEmpty() || state.discardBlocked || !canTakeDiscard(state)))
        if (state.wentOut < 0 && !travou) return state

        val ganhosDetalhes = scoreHand(state)
        val somados = List(state.teams) { time -> state.scores.getOrElse(time) { 0 } + ganhosDetalhes[time].totalRodada }
        
        if (somados.any { it >= CANASTRA_TARGET }) return state.copy(scores = somados, lastScores = ganhosDetalhes)

        val proximoComecar = Seat((state.startingSeat.index + state.seats - 1) % state.seats)
        return dealHand(state.seats, somados, state.rng, startingSeat = proximoComecar, lastScores = ganhosDetalhes).copy(ply = state.ply)
    }

    private data class Absorvido(
        val hand: List<Card>,
        val stock: List<Card>,
        val redThrees: List<Int>,
    )

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

    fun scoreHand(state: CanastraState): List<RoundScore> = List(state.teams) { time ->
        val naMesa = state.melds.getOrElse(time) { emptyList() }.sumOf { it.score }
        val naMao = (0 until state.seats)
            .filter { state.teamOf(Seat(it)) == time }
            .sumOf { state.hand(Seat(it)).sumOf { carta -> cardValue(carta) } }

        val quantos = state.redThrees.getOrElse(time) { 0 }
        val vermelhos = if (state.hasCanastra(time)) quantos * RED_THREE_VALUE else 0

        val bateu = CANASTRA_GOING_OUT_BONUS * state.batidas.getOrElse(time) { 0 }

        RoundScore(
            pontosMesa = naMesa,
            penalidadeMao = naMao,
            vermelhos = vermelhos,
            batida = bateu,
            totalRodada = naMesa - naMao + vermelhos + bateu
        )
    }

    override fun outcome(state: CanastraState): Outcome {
        if (state.scores.none { it >= CANASTRA_TARGET }) return Outcome.InProgress
        val maior = state.scores.max()
        val campeao = state.scores.indexOfFirst { it == maior }
        return Outcome.Win(Seat(campeao))
    }

    override fun isCapture(state: CanastraState, move: CanastraMove): Boolean =
        move is CanastraMove.Meld || move is CanastraMove.SwapWild

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
