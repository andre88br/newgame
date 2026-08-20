package io.github.andre88br.newgame.core.games.canastra

import io.github.andre88br.newgame.core.ai.DeterminizedAi
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.GameAi
import io.github.andre88br.newgame.core.ai.MoveOrdering
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.cards.deckOf
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat

/** Define o estilo de jogo da IA, alterando os pesos e punições das suas escolhas. */
enum class AiPersonality { AGRESSIVO, ACUMULADOR, BALANCEADO }

class CanastraEvaluatorImpl(private val personality: AiPersonality = AiPersonality.BALANCEADO) : Evaluator<CanastraState> {

    private val CANASTRA_WEIGHT = 250
    private val CLEAN_BONUS = 150
    private val MORTO_WEIGHT = 120

    override fun evaluate(state: CanastraState, seat: Seat): Int {
        val meu = state.teamOf(seat)
        val meus = teamScore(state, meu)
        val outros = (0 until state.teams).filter { it != meu }.maxOfOrNull { teamScore(state, it) } ?: 0
        return meus - outros
    }

    private fun teamScore(state: CanastraState, team: Int): Int {
        val jogos = state.melds.getOrElse(team) { emptyList() }
        var total = state.scores.getOrElse(team) { 0 }

        // PERSONALIDADE DA IA: Valoriza se a IA baixa jogos mais rápido ou espera
        val progressWeight = when(personality) {
            AiPersonality.AGRESSIVO -> 25 // Quer muito baixar qualquer coisa
            AiPersonality.ACUMULADOR -> 2 // Odeia baixar jogo incompleto
            AiPersonality.BALANCEADO -> 12
        }

        // MEMÓRIA DE NAIPES: Para evitar que a IA divida jogos do mesmo naipe
        val sequenceSuits = mutableSetOf<Suit>()
        var duplicateSuitPenalty = 0

        for (jogo in jogos) {
            total += jogo.cards.sumOf { cardValue(it) }
            if (jogo.isCanastra) total += CANASTRA_WEIGHT
            if (jogo.isClean) total += CLEAN_BONUS
            if (!jogo.isCanastra) total += (jogo.cards.size - CANASTRA_MIN_MELD) * progressWeight

            if (jogo.kind == MeldKind.SEQUENCE) {
                val naipe = jogo.naturals.firstOrNull()?.suit
                if (naipe != null) {
                    // Se a IA tentar fazer uma segunda sequência do mesmo naipe, aplica punição severa
                    if (sequenceSuits.contains(naipe)) {
                        duplicateSuitPenalty += 1000 
                    }
                    sequenceSuits.add(naipe)
                }
            }
        }

        val vermelhos = state.redThrees.getOrElse(team) { 0 } * RED_THREE_VALUE
        if (jogos.any { it.isCanastra }) total += vermelhos

        if (state.tookMorto.getOrElse(team) { false }) total += MORTO_WEIGHT

        val valorBrutoNaMao = (0 until state.seats)
            .filter { state.teamOf(Seat(it)) == team }
            .sumOf { seat ->
                state.hand(Seat(seat)).sumOf { carta ->
                    if (isWild(carta)) 3 else cardValue(carta) // 3 é a penalização para curingas na mão
                }
            }

        // PERSONALIDADE DA IA: Que medo a IA tem de segurar cartas na mão
        val multiplicadorDeDivida = when(personality) {
            AiPersonality.AGRESSIVO -> 1.5f // Fica desesperada com cartas na mão
            AiPersonality.ACUMULADOR -> 0.5f // Segura cartas calmamente
            AiPersonality.BALANCEADO -> 1.0f
        }

        val precisaAberturaAlta = state.scores.getOrElse(team) { 0 } >= CANASTRA_OPENING_THRESHOLD && 
                                  !state.firstMeldDone.getOrElse(team) { false }

        val penalidade = if (precisaAberturaAlta) {
            maxOf(0, (valorBrutoNaMao * multiplicadorDeDivida).toInt() - CANASTRA_OPENING_MIN_VALUE)
        } else {
            (valorBrutoNaMao * multiplicadorDeDivida).toInt()
        }

        return total - penalidade - duplicateSuitPenalty
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
                    CanastraMove.Pass -> 700
                    
                    is CanastraMove.Discard -> {
                        // MEMÓRIA DA IA: Identifica se o próximo a jogar é o adversário
                        val proximoJogador = (state.turn.index + 1) % state.seats
                        val timeProximo = state.teamOf(Seat(proximoJogador))
                        val meuTime = state.teamOf(state.turn)
                        val isProximoInimigo = timeProximo != meuTime
                        
                        // Procura o que o adversário apanhou do lixo na memória fotográfica
                        val cartasConhecidasDoProximo = if (isProximoInimigo) state.knownOpponentCards[proximoJogador] ?: emptyList() else emptyList()
                        
                        val daJogoProAdversario = cartasConhecidasDoProximo.any { 
                            it.suit == move.card.suit && Math.abs(it.rank.order - move.card.rank.order) <= 2 
                        }

                        when {
                            isWild(move.card) -> {
                                if (state.hand(state.turn).count { isWild(it) } > 1) -20 else -100
                            }
                            isBlackThree(move.card) -> 10
                            daJogoProAdversario -> -50 // 🚨 IA Maliciosa nunca deita fora uma carta próxima do que o inimigo apanhou
                            else -> 100 - cardValue(move.card)
                        }
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
    // Pode alterar entre AGRESSIVO, ACUMULADOR ou BALANCEADO aqui para ver o comportamento a mudar:
    evaluator = CanastraEvaluatorImpl(AiPersonality.BALANCEADO),
    ordering = CanastraOrdering,
    // Trocar o curinga de uma sequência já baixada pela carta exata é de graça — não custa
    // carta nenhuma da mão que já não fosse gasta, e sempre melhora o jogo. Nem o nível fácil
    // devia "esquecer" isso por sorteio de erro; ver a nota em [DeterminizedAi.neverMistaken].
    neverMistaken = { _, move -> move is CanastraMove.SwapWild },
    limits = { difficulty ->
        when (difficulty) {
            Difficulty.EASY -> SearchLimits(maxDepth = 1, timeBudgetMillis = 150)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 2, timeBudgetMillis = 400)
            Difficulty.HARD -> SearchLimits(maxDepth = 4, timeBudgetMillis = 1_000)
        }
    },
    complete = ::completeCanastra,
)
