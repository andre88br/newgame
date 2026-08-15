package io.github.andre88br.newgame.core.games.pife

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
 * Avaliação do pife.
 *
 * A mão vale pelo que **falta** para fechar, e não pelas cartas que tem: em pife carta alta
 * não vale mais do que carta baixa, e a única pergunta é quantos grupos já estão prontos e
 * quanto do resto está a caminho.
 *
 * Grupo pronto pesa muito mais do que par encaminhado, e é de propósito: trocar um grupo
 * feito por dois pares na esperança de fechar os dois é o erro clássico de quem está
 * aprendendo, e a máquina não deve cometê-lo.
 */
object PifeEvaluator : Evaluator<PifeState> {

    private const val GROUP_WEIGHT = 100
    private const val PARTIAL_WEIGHT = 12

    override fun evaluate(state: PifeState, seat: Seat): Int {
        val meu = handScore(state.hand(seat))
        val melhorDosOutros = (0 until state.seats)
            .map { Seat(it) }
            .filter { it != seat }
            .maxOfOrNull { handScore(state.hand(it)) } ?: 0
        return meu - melhorDosOutros
    }

    private fun handScore(mao: List<Card>): Int {
        if (mao.isEmpty() || mao.any { it.isHidden }) return 0
        val grupos = bestGroupCount(mao)
        return grupos * GROUP_WEIGHT + parciais(mao) * PARTIAL_WEIGHT
    }

    /**
     * Pares e pontas de sequência: o que ainda não é grupo mas está a uma carta de ser.
     *
     * Conta só o que sobra depois dos grupos prontos serem retirados, senão as cartas de um
     * grupo feito seriam contadas duas vezes e a mão pareceria melhor do que é.
     */
    private fun parciais(mao: List<Card>): Int {
        var total = 0
        for (i in mao.indices) {
            for (j in i + 1 until mao.size) {
                val a = mao[i]
                val b = mao[j]
                val casam = when {
                    a.isJoker || b.isJoker -> true
                    a.rank == b.rank -> true
                    a.suit == b.suit -> kotlin.math.abs(a.rank.order - b.rank.order) <= 2
                    else -> false
                }
                if (casam) total++
            }
        }
        return total
    }
}

/**
 * Comprar do lixo antes de comprar às cegas, e descartar antes a carta que não conversa com
 * nenhuma outra. Não muda o que a busca decide; ajuda a cortar cedo.
 */
val PifeOrdering: MoveOrdering<PifeState, PifeMove> =
    MoveOrdering<PifeState, PifeMove> { state, moves ->
        if (moves.size < 2) {
            moves
        } else {
            val mao = state.hand(state.turn)
            moves.sortedByDescending { move ->
                when (move) {
                    PifeMove.DrawDiscard -> 100
                    PifeMove.DrawStock -> 90
                    // Descarte: a carta cuja falta menos machuca a mão é a melhor de jogar.
                    is PifeMove.Discard -> bestGroupCount(mao - move.card) * 10 -
                        if (move.card.isJoker) 100 else 0
                }
            }
        }
    }

/**
 * Completa um mundo possível: reparte entre as mãos alheias e o monte tudo o que não é
 * visível.
 *
 * O lixo não entra na conta porque é público — em pife ele é justamente a informação que
 * sobra —, e a própria mão também não. O resto é palpite, e as contagens são respeitadas.
 */
fun completePife(state: PifeState, rng: Rng): PifeState {
    val vistas = mutableListOf<Card>()
    state.hands.forEach { mao -> vistas += mao.filterNot { it.isHidden } }
    vistas += state.discard

    val sobra = deckOf(PIFE_DECKS, PIFE_JOKERS_PER_DECK).toMutableList()
    for (carta in vistas) sobra.remove(carta)

    val embaralhadas = ArrayDeque(rng.shuffle(sobra).value)
    fun tirar(quantas: Int): List<Card> = List(minOf(quantas, embaralhadas.size)) { embaralhadas.removeFirst() }

    val maos = state.hands.map { mao ->
        if (mao.none { it.isHidden }) mao else mao.filterNot { it.isHidden } + tirar(mao.count { it.isHidden })
    }
    val monte = if (state.stock.none { it.isHidden }) state.stock else tirar(state.stock.size)

    return state.copy(hands = maos, stock = monte)
}

val PifeAi: GameAi<PifeState, PifeMove> = DeterminizedAi(
    game = PifeGame,
    evaluator = PifeEvaluator,
    ordering = PifeOrdering,
    limits = { difficulty ->
        when (difficulty) {
            Difficulty.EASY -> SearchLimits(maxDepth = 1, timeBudgetMillis = 120)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 2, timeBudgetMillis = 350)
            Difficulty.HARD -> SearchLimits(maxDepth = 4, timeBudgetMillis = 900)
        }
    },
    complete = ::completePife,
)
