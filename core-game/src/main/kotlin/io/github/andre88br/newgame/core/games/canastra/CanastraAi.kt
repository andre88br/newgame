package io.github.andre88br.newgame.core.games.canastra

import io.github.andre88br.newgame.core.ai.DeterminizedAi
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.GameAi
import io.github.andre88br.newgame.core.ai.MoveOrdering
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.deckOf
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat

object CanastraEvaluator : Evaluator<CanastraState> {

    private const val CANASTRA_WEIGHT = 250
    private const val CLEAN_BONUS = 150
    private const val PROGRESS_WEIGHT = 12
    private const val MORTO_WEIGHT = 120
    private const val WILD_IN_HAND_PENALTY = 3

    override fun evaluate(state: CanastraState, seat: Seat): Int {
        val meu = state.teamOf(seat)
        val meus = teamScore(state, meu)
        val outros = (0 until state.teams).filter { it != meu }.maxOfOrNull { teamScore(state, it) } ?: 0
        return meus - outros
    }

    private fun teamScore(state: CanastraState, team: Int): Int {
        val jogos = state.melds.getOrElse(team) { emptyList() }
        var total = state.scores.getOrElse(team) { 0 }

        for (jogo in jogos) {
            total += jogo.cards.sumOf { cardValue(it) }
            if (jogo.isCanastra) total += CANASTRA_WEIGHT
            if (jogo.isClean) total += CLEAN_BONUS
            if (!jogo.isCanastra) total += (jogo.cards.size - CANASTRA_MIN_MELD) * PROGRESS_WEIGHT
        }

        val vermelhos = state.redThrees.getOrElse(team) { 0 } * RED_THREE_VALUE
        if (jogos.any { it.isCanastra }) total += vermelhos

        if (state.tookMorto.getOrElse(team) { false }) total += MORTO_WEIGHT

        val valorBrutoNaMao = (0 until state.seats)
            .filter { state.teamOf(Seat(it)) == team }
            .sumOf { seat ->
                state.hand(Seat(seat)).sumOf { carta ->
                    if (isWild(carta)) WILD_IN_HAND_PENALTY else cardValue(carta)
                }
            }

        val precisaAberturaAlta = state.scores.getOrElse(team) { 0 } >= CANASTRA_OPENING_THRESHOLD && 
                                  !state.firstMeldDone.getOrElse(team) { false }

        val penalidade = if (precisaAberturaAlta) {
            maxOf(0, valorBrutoNaMao - CANASTRA_OPENING_MIN_VALUE)
        } else {
            valorBrutoNaMao
        }

        return total - penalidade
    }
}

val CanastraOrdering: MoveOrdering<CanastraState, CanastraMove> =
    MoveOrdering<CanastraState, CanastraMove> { state, moves ->
        if (moves.size < 2) {
            moves
        } else {
            moves.sortedByDescending { move ->
                when (move) {
                    is CanastraMove.Meld -> {
                        if (move.into != null) {
                            2_000 + move.cards.sumOf { cardValue(it) }
                        } else {
                            1_000 + move.cards.sumOf { cardValue(it) }
                        }
                    }
                    is CanastraMove.SwapWild -> 2_500 + cardValue(move.card)
                    CanastraMove.TakeDiscard -> 900
                    CanastraMove.DrawStock -> 800
                    CanastraMove.Pass -> 700 // Prefere pegar o lixo se puder, mas aceita passar se for necessário.
                    is CanastraMove.Discard -> when {
                        isWild(move.card) -> {
                            if (state.hand(state.turn).count { isWild(it) } > 1) -20 else -100
                        }
                        isBlackThree(move.card) -> 10
                        else -> 100 - cardValue(move.card)
                    }
                }
            }
        }
    }

fun completeCanastra(state: CanastraState, rng: Rng): CanastraState {
    val vistas = mutableListOf<Card>()
    state.hands.forEach { mao -> vistas += mao.filterNot { it.isHidden } }
    state.melds.forEach { jogos -> jogos.forEach { vistas += it.cards } }
    vistas += state.discard

    val sobra = deckOf(CANASTRA_DECKS, CANASTRA_JOKERS_PER_DECK).toMutableList()
    for (carta in vistas) sobra.remove(carta)
    var aRemover = state.redThrees.sum()
    while (aRemover > 0) {
        val achado = sobra.indexOfFirst { isRedThree(it) }
        if (achado < 0) break
        sobra.removeAt(achado)
        aRemover--
    }

    val embaralhadas = rng.shuffle(sobra).value
    val paraMao = ArrayDeque(embaralhadas.filterNot { isRedThree(it) })
    val soParaMesa = embaralhadas.filter { isRedThree(it) }

    fun tirarParaMao(quantas: Int): List<Card> = List(minOf(quantas, paraMao.size)) { paraMao.removeFirst() }

    val maos = state.hands.map { mao ->
        if (mao.none { it.isHidden }) {
            mao
        } else {
            mao.filterNot { it.isHidden } + tirarParaMao(mao.count { it.isHidden })
        }
    }

    val paraMesa = ArrayDeque(paraMao.toList() + soParaMesa)
    fun tirarParaMesa(quantas: Int): List<Card> = List(minOf(quantas, paraMesa.size)) { paraMesa.removeFirst() }

    val monte = if (state.stock.none { it.isHidden }) state.stock else tirarParaMesa(state.stock.size)
    val mortos = state.mortos.map { morto ->
        if (morto.none { it.isHidden }) morto else tirarParaMesa(morto.size)
    }

    return state.copy(hands = maos, stock = monte, mortos = mortos)
}

val CanastraAi: GameAi<CanastraState, CanastraMove> = DeterminizedAi(
    game = CanastraGame,
    evaluator = CanastraEvaluator,
    ordering = CanastraOrdering,
    limits = { difficulty ->
        when (difficulty) {
            Difficulty.EASY -> SearchLimits(maxDepth = 1, timeBudgetMillis = 150)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 2, timeBudgetMillis = 400)
            Difficulty.HARD -> SearchLimits(maxDepth = 4, timeBudgetMillis = 1_000)
        }
    },
    complete = ::completeCanastra,
)
