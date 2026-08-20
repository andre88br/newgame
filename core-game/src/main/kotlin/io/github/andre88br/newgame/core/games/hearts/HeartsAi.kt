package io.github.andre88br.newgame.core.games.hearts

import io.github.andre88br.newgame.core.ai.DeterminizedAi
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.GameAi
import io.github.andre88br.newgame.core.ai.MoveOrdering
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.cards.standardDeck
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat

/**
 * Avaliação de copas.
 *
 * O sinal é invertido em relação a qualquer outro jogo: ponto feito é ponto **perdido**. A
 * conta olha primeiro a partida (quem está mais longe dos cem) e depois a mão em andamento,
 * porque uma mão ruim não decide nada se o placar geral estiver confortável.
 *
 * Correr todas não entra na avaliação de propósito. É uma jogada de mão inteira, e uma busca
 * de poucas vazas que a perseguisse acabaria pegando pontos sem conseguir pegar todos — que
 * é o pior resultado possível. A máquina joga para não levar ponto, que é o certo na
 * imensa maioria das mãos.
 */
object HeartsEvaluator : Evaluator<HeartsState> {

    /** Ponto da partida pesa mais do que ponto da mão: é ele que decide quem perde. */
    private const val MATCH_WEIGHT = 12
    private const val HAND_WEIGHT = 10

    /** Carta alta na mão é risco de levar vaza; carta de copas, risco maior. */
    private const val HIGH_CARD_RISK = 2

    override fun evaluate(state: HeartsState, seat: Seat): Int {
        val meu = cost(state, seat)
        // Contra o melhor dos outros, e não contra a média: quem decide a partida é quem
        // está ganhando, e ficar em segundo confortável não vale nada em copas.
        val melhorDosOutros = (0 until HEARTS_SEATS)
            .map { Seat(it) }
            .filter { it != seat }
            .minOf { cost(state, it) }
        return melhorDosOutros - meu
    }

    /** Quanto esta cadeira está "devendo": pontos e risco. Menos é melhor. */
    private fun cost(state: HeartsState, seat: Seat): Int {
        val daPartida = state.scores.getOrElse(seat.index) { 0 } * MATCH_WEIGHT
        val daMao = state.handPoints.getOrElse(seat.index) { 0 } * HAND_WEIGHT
        return daPartida + daMao + risk(state.hand(seat))
    }

    /**
     * O risco que a mão ainda carrega.
     *
     * Não é ponto feito: é ponto que provavelmente vai ser feito. A dama de espadas
     * desacompanhada é o caso clássico — sem espadas baixas para segurá-la, ela cai na
     * primeira rodada de espadas.
     */
    private fun risk(mao: List<Card>): Int {
        if (mao.any { it.isHidden }) return 0
        var risco = 0
        for (carta in mao) {
            if (carta == QUEEN_OF_SPADES) risco += 20
            if (carta.suit == Suit.HEARTS && carta.rank.order >= 11) risco += HIGH_CARD_RISK
            if (carta.rank.order >= 13) risco += HIGH_CARD_RISK
        }
        // Espada alta sem espada baixa que a proteja é onde a dama costuma cair.
        val espadas = mao.filter { it.suit == Suit.SPADES }
        val altas = espadas.count { it.rank.order > QUEEN_OF_SPADES.rank.order }
        val baixas = espadas.count { it.rank.order < QUEEN_OF_SPADES.rank.order }
        if (altas > 0 && baixas < altas) risco += 8
        return risco
    }
}

/**
 * Primeiro o que se sabe que é bom: carta baixa quando se serve, carta alta quando se
 * descarta. Ajuda a busca a cortar cedo sem mudar o que ela decide.
 */
val HeartsOrdering: MoveOrdering<HeartsState, HeartsMove> =
    MoveOrdering<HeartsState, HeartsMove> { state, moves ->
        if (moves.size < 2) {
            moves
        } else {
            val pedido = state.leadSuit
            moves.sortedByDescending { move ->
                val carta = move.card
                when {
                    // Descartando: quanto mais cara a carta, melhor se livrar dela.
                    pedido != null && carta.suit != pedido -> 100 + penaltyOf(carta) * 5 + carta.rank.order
                    // Servindo: carta baixa é a que não leva a vaza.
                    else -> 50 - carta.rank.order
                }
            }
        }
    }

/**
 * Completa um mundo possível: reparte entre os adversários as cartas que ainda não
 * apareceram.
 *
 * O que se sabe é o que já foi jogado e o que está na própria mão; o resto é palpite. Repartir
 * ao acaso, várias vezes, e ver o que se sustenta na maioria dos mundos é a mesma ideia que
 * o dominó usa — e aqui vale mais ainda, porque em copas a distribuição alheia muda tudo.
 *
 * As **contagens** são respeitadas: cada adversário recebe exatamente o número de cartas que
 * a tela mostra que ele tem. Sem isso a busca resolveria uma mesa que não existe.
 */
fun completeHearts(state: HeartsState, rng: Rng): HeartsState {
    val visiveis = buildSet {
        state.hands.forEach { mao -> mao.filterNot { it.isHidden }.forEach { add(it) } }
        state.trick.forEach { add(it.card) }
        state.passing.forEach { cartas -> cartas.filterNot { it.isHidden }.forEach { add(it) } }
        // Vazas já fechadas nesta mão foram jogadas com a face para cima: são tão públicas
        // quanto a vaza em andamento, e sem isto o sorteio as devolveria à mão de alguém.
        state.playedTricks.forEach { add(it) }
    }
    // Só as que ninguém viu ainda. As já jogadas em vazas fechadas não voltam ao baralho,
    // mas também não estão em mão nenhuma — e é por isso que a conta usa o tamanho da mão.
    val desconhecidas = standardDeck().filterNot { it in visiveis }
    if (desconhecidas.isEmpty()) return state

    val embaralhadas = rng.shuffle(desconhecidas).value
    var proxima = 0
    val maos = state.hands.map { mao ->
        if (mao.none { it.isHidden }) {
            mao
        } else {
            val abertas = mao.filterNot { it.isHidden }
            val quantas = mao.size - abertas.size
            val sorteadas = embaralhadas.drop(proxima).take(quantas)
            proxima += quantas
            abertas + sorteadas
        }
    }
    return state.copy(hands = maos)
}

val HeartsAi: GameAi<HeartsState, HeartsMove> = DeterminizedAi(
    game = HeartsGame,
    evaluator = HeartsEvaluator,
    ordering = HeartsOrdering,
    limits = { difficulty ->
        when (difficulty) {
            Difficulty.EASY -> SearchLimits(maxDepth = 1, timeBudgetMillis = 120)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 3, timeBudgetMillis = 350)
            Difficulty.HARD -> SearchLimits(maxDepth = 5, timeBudgetMillis = 900)
        }
    },
    complete = ::completeHearts,
)
