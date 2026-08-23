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

    /**
     * Cada carta além da sétima numa canastra **limpa** vale mais cem pontos no placar de
     * verdade — veja [Meld.score], que é o que [CanastraGame.scoreHand] soma no fim da mão.
     *
     * Está aqui porque o avaliador reimplementava a pontuação em vez de acompanhá-la, e tinha
     * esquecido justamente esta parcela: crescer uma canastra já pronta valia, para a IA, só o
     * valor solto da carta (dez pontos por uma dama) em vez dos cento e dez de verdade.
     */
    private val CLEAN_EXTRA_CARD = 100

    /**
     * Quanto vale a promessa de uma carta que ainda está na mão do time mas já encaixa num
     * jogo baixado. Não é ponto no placar — é ponto provável na próxima vez —, então entra
     * descontado; numa canastra limpa vale mais, porque lá cada carta nova rende os cem de
     * [CLEAN_EXTRA_CARD].
     */
    private val GROWTH_PROMISE = 25
    private val CLEAN_GROWTH_PROMISE = 55

    /**
     * O custo de deixar o mesmo naipe repartido em duas sequências em vez de uma só.
     *
     * Em duplas o custo é maior: o parceiro pode estar segurando exatamente as cartas que
     * ligariam as duas pontas num jogo só, e a mão dele é oculta — a IA não tem como
     * conferir antes de decidir, então trata a possibilidade como custo em vez de apostar
     * contra ela. Jogando individual não há parceiro para esperar, e o que sobra é só a
     * perda de ter duas sequências curtas onde cabia uma longa.
     */
    private val SPLIT_SUIT_PENALTY = 1_000
    private val SPLIT_SUIT_PENALTY_DUPLAS = 1_600

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

        // A mão do time inteiro, lida uma vez só: [potencialDeCrescimento] pergunta dela a
        // cada jogo, e remontá-la por jogo sairia caro dentro da busca.
        val maoDoTime = (0 until state.seats)
            .filter { state.teamOf(Seat(it)) == team }
            .flatMap { state.hand(Seat(it)) }
            .distinct()

        val splitPenalty = if (state.seats == 4) SPLIT_SUIT_PENALTY_DUPLAS else SPLIT_SUIT_PENALTY

        for (jogo in jogos) {
            total += jogo.cards.sumOf { cardValue(it) }
            if (jogo.isCanastra) total += CANASTRA_WEIGHT
            if (jogo.isClean) total += CLEAN_BONUS + (jogo.cards.size - CANASTRA_SIZE) * CLEAN_EXTRA_CARD
            if (!jogo.isCanastra) total += (jogo.cards.size - CANASTRA_MIN_MELD) * progressWeight
            total += potencialDeCrescimento(jogo, maoDoTime)

            if (jogo.kind == MeldKind.SEQUENCE) {
                val naipe = jogo.naturals.firstOrNull()?.suit
                if (naipe != null) {
                    // Se a IA tentar fazer uma segunda sequência do mesmo naipe, aplica punição severa
                    if (sequenceSuits.contains(naipe)) {
                        duplicateSuitPenalty += splitPenalty
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

    /**
     * O que as cartas que o time ainda segura na mão prometem somar a [jogo] mais adiante.
     *
     * É o que faz a IA enxergar que trocar o curinga não vale só a carta que entrou: o
     * curinga sai do buraco que tapava, vai para uma ponta e passa a representar **outro**
     * valor — e esse valor novo pode ser trocado de novo, crescendo a canastra mais uma vez.
     * Como a conta é refeita sobre o jogo **depois** da troca, a segunda troca já aparece
     * aqui como promessa, sem precisar de regra especial nem de mais um nível de busca.
     *
     * A pergunta "esta carta cabe?" é feita a [extendMeld] e [wildRepresents], que são as
     * mesmas funções que o motor usa para gerar os lances legais — reimplementá-las aqui
     * seria repetir o erro que este avaliador já cometeu com a pontuação da canastra limpa.
     */
    private fun potencialDeCrescimento(jogo: Meld, maoDoTime: List<Card>): Int {
        if (maoDoTime.isEmpty()) return 0
        val exata = wildRepresents(jogo)
        val cabem = maoDoTime.count { carta -> carta == exata || extendMeld(jogo, carta) != null }
        return cabem * if (jogo.isClean) CLEAN_GROWTH_PROMISE else GROWTH_PROMISE
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
    // Trocar o curinga pela carta exata gasta uma carta da mão, mas devolve mais do que
    // tira: o jogo cresce em uma carta (cem pontos, numa canastra limpa), o curinga vai
    // para a ponta e pode abrir posição para outra carta entrar depois. Qual das duas
    // pontas compensa mais é conta da busca, que enxerga tudo isso — o que não pode é o
    // sorteio de erro atropelar a decisão e descartar a carta sem ninguém ter avaliado
    // nada. Ver a nota em [DeterminizedAi.neverMistaken].
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
