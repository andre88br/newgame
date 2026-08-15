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

/**
 * Avaliação de canastra.
 *
 * O que decide a partida não é o ponto solto na mesa: é a **canastra**, que sozinha vale
 * mais do que a maioria dos jogos baixados, e é a única coisa que permite bater. Por isso a
 * conta pesa canastra acima de tudo, e pesa jogo perto de virar canastra logo abaixo —
 * cinco cartas do mesmo valor na mesa valem muito mais do que a soma delas sugere.
 *
 * Carta parada na mão conta contra, e é o que empurra a máquina a baixar em vez de acumular:
 * quem termina a mão com cartas na mão paga por elas.
 */
object CanastraEvaluator : Evaluator<CanastraState> {

    /** O prêmio de uma canastra, em peso de avaliação. */
    private const val CANASTRA_WEIGHT = 250
    private const val CLEAN_BONUS = 150

    /** Jogo a caminho da canastra: cada carta além da terceira vale progresso. */
    private const val PROGRESS_WEIGHT = 12

    /** Ter pegado o morto é meio caminho para bater. */
    private const val MORTO_WEIGHT = 120

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
            // Progresso: quanto mais perto de sete, mais o jogo vale além das cartas.
            if (!jogo.isCanastra) total += (jogo.cards.size - CANASTRA_MIN_MELD) * PROGRESS_WEIGHT
        }

        // Três vermelho só vale alguma coisa com canastra — é a regra que impede tratá-lo
        // como ponto garantido, e a avaliação precisa enxergar isso.
        val vermelhos = state.redThrees.getOrElse(team) { 0 } * RED_THREE_VALUE
        if (jogos.any { it.isCanastra }) total += vermelhos

        if (state.tookMorto.getOrElse(team) { false }) total += MORTO_WEIGHT

        // Carta na mão é dívida: no fim da mão ela é descontada.
        val naMao = (0 until state.seats)
            .filter { state.teamOf(Seat(it)) == team }
            .sumOf { state.hand(Seat(it)).sumOf { carta -> cardValue(carta) } }
        return total - naMao
    }
}

/**
 * Primeiro o que quase sempre é bom: baixar antes de descartar, e descartar carta barata.
 *
 * Ajuda a busca a cortar cedo. O três preto sai por último de propósito — descartá-lo tranca
 * o lixo do adversário, mas gasta a carta, e só compensa quando não há descarte melhor.
 */
val CanastraOrdering: MoveOrdering<CanastraState, CanastraMove> =
    MoveOrdering<CanastraState, CanastraMove> { _, moves ->
        if (moves.size < 2) {
            moves
        } else {
            moves.sortedByDescending { move ->
                when (move) {
                    is CanastraMove.Meld -> 1_000 + move.cards.sumOf { cardValue(it) }
                    // Trocar o curinga libera ele para outro jogo e não gasta carta de mais:
                    // quase sempre vale a pena, tanto quanto baixar.
                    is CanastraMove.SwapWild -> 1_000 + cardValue(move.card)
                    CanastraMove.TakeDiscard -> 900
                    CanastraMove.DrawStock -> 800
                    // Descartar: quanto mais barata a carta, melhor. Curinga nunca.
                    is CanastraMove.Discard -> when {
                        isWild(move.card) -> -100
                        isBlackThree(move.card) -> 10
                        else -> 100 - cardValue(move.card)
                    }
                }
            }
        }
    }

/**
 * Completa um mundo possível: reparte as cartas que ninguém viu entre as mãos alheias, o
 * monte e os mortos.
 *
 * O que se sabe é a própria mão, o lixo e tudo que já foi baixado — o resto é palpite. As
 * contagens são respeitadas: cada mão recebe o número de cartas que a tela mostra, o monte
 * fica com o tamanho certo, e os mortos com onze cada. Sem isso a busca resolveria uma mesa
 * que não existe.
 */
fun completeCanastra(state: CanastraState, rng: Rng): CanastraState {
    val vistas = mutableListOf<Card>()
    state.hands.forEach { mao -> vistas += mao.filterNot { it.isHidden } }
    state.melds.forEach { jogos -> jogos.forEach { vistas += it.cards } }
    vistas += state.discard

    // O baralho inteiro menos o que já apareceu, contando repetidas: são dois baralhos, e
    // remover por valor perderia a segunda cópia de cada carta.
    val sobra = deckOf(CANASTRA_DECKS, CANASTRA_JOKERS_PER_DECK).toMutableList()
    for (carta in vistas) sobra.remove(carta)
    // Os três vermelhos já na mesa não voltam ao baralho.
    var aRemover = state.redThrees.sum()
    while (aRemover > 0) {
        val achado = sobra.indexOfFirst { isRedThree(it) }
        if (achado < 0) break
        sobra.removeAt(achado)
        aRemover--
    }

    val embaralhadas = rng.shuffle(sobra).value
    // **Três vermelho nunca cai em mão.** Ele sai da mão no instante em que aparece — na
    // distribuição, na compra, no lixo e no morto —, então um mundo que o pusesse na mão de
    // alguém seria um mundo impossível, e a busca decidiria em cima dele. Os que ainda não
    // apareceram estão no monte ou no morto, e é para lá que vão.
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

    // O que sobrou, mais os vermelhos, preenche monte e mortos — onde eles de fato podem estar.
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
            // A vez da canastra tem três tempos (comprar, baixar, descartar), então a árvore
            // cresce depressa: profundidade menor do que nos outros jogos rende o mesmo.
            Difficulty.EASY -> SearchLimits(maxDepth = 1, timeBudgetMillis = 150)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 2, timeBudgetMillis = 400)
            Difficulty.HARD -> SearchLimits(maxDepth = 4, timeBudgetMillis = 1_000)
        }
    },
    complete = ::completeCanastra,
)
