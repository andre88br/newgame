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
import kotlin.math.abs

/**
 * Estilo de jogo da IA: cada valor muda só os pesos e punições abaixo, nunca a legalidade —
 * quem decide o que pode ou não é sempre [CanastraGame].
 */
enum class AiPersonality { AGRESSIVO, ACUMULADOR, BALANCEADO }

/**
 * Avaliação de uma posição de canastra do ponto de vista de uma cadeira.
 *
 * A régua é [CanastraGame.scoreHand]: pontos de mesa vêm de [Meld.score] (nunca recalculados
 * à mão aqui — ver a nota na própria classe sobre a canastra limpa render cem por carta além
 * da sétima), cartas na mão pesam contra e os três vermelhos só contam com canastra na mesa.
 * Por cima disso entram termos que não aparecem no placar de verdade, mas preveem o que ele
 * vai valer daqui a um lance ou dois: o que a mão do time promete completar, o risco de
 * repartir um naipe em duas sequências, a utilidade extra de já ter uma canastra (destrava
 * trinca e a batida) e a urgência de fechar a própria quando o adversário já pode bater a
 * qualquer momento.
 */
class CanastraEvaluatorImpl(private val personality: AiPersonality = AiPersonality.BALANCEADO) : Evaluator<CanastraState> {

    /**
     * Ter uma canastra vale mais do que os pontos que ela soma sozinha: é o que libera a
     * trinca ([CanastraGame] só aceita `MeldKind.SET` depois da primeira canastra do time,
     * veja `CANASTRA_TRINCA_NEEDS_CANASTRA`) e o que permite zerar a mão de vez sem o morto
     * (veja `podeZerar`/`encurrala`). Fica de fora de [Meld.score] de propósito: aquele
     * número é o placar de verdade, este é o valor estratégico de tê-la, e somar os dois
     * separados evita que um vire reimplementação disfarçada do outro.
     */
    private val UTILIDADE_DA_CANASTRA = 200

    /**
     * Quanto vale a promessa de uma carta que o time ainda segura mas já encaixa num jogo
     * baixado — não é ponto de placar, é ponto provável na próxima vez. Numa canastra limpa
     * pesa mais porque, ali, cada carta nova rende os cem extras de [Meld.score].
     */
    private val PROMESSA_DE_CRESCIMENTO = 25
    private val PROMESSA_DE_CRESCIMENTO_LIMPA = 55

    /**
     * Custo de repartir o mesmo naipe em duas sequências em vez de uma só. Em duplas custa
     * mais: o parceiro pode estar segurando exatamente as cartas que ligariam as duas pontas
     * num jogo só, e a mão dele é oculta — não há como conferir antes de decidir, então o
     * risco vira custo. Jogando individual não existe parceiro para esperar, e o que sobra é
     * só a perda de ter duas sequências curtas onde cabia uma longa.
     */
    private val CUSTO_NAIPE_REPARTIDO = 1_000
    private val CUSTO_NAIPE_REPARTIDO_DUPLAS = 1_600

    /** O quanto guardar um curinga/dois na mão pesa contra o time — barato de propósito: ele raramente fica parado até o fim, é recurso, não lixo. */
    private val CUSTO_CURINGA_NA_MAO = 3

    /**
     * Quão perto um time rival já está de poder fechar a rodada de vez. Só existe se o rival
     * já tem canastra ([CanastraGame.hasCanastra] — sem ela, `encurrala` nem deixa zerar a
     * mão) e não tem mais morto para amortecer ([CanastraState.mortos] vazio ou
     * [CanastraState.tookMorto] já verdadeiro para ele — pegar o morto só recicla a mão, não
     * fecha nada). Com as duas condições, quanto menor a mão do rival, mais perto a rodada
     * está de acabar sem o meu time ter tido tempo de terminar a própria.
     */
    private val PESO_AMEACA_DE_BATIDA = 8

    override fun evaluate(state: CanastraState, seat: Seat): Int {
        val meuTime = state.teamOf(seat)
        val meuValor = avaliarTime(state, meuTime)
        val piorRival = (0 until state.teams)
            .filter { it != meuTime }
            .maxOfOrNull { avaliarTime(state, it) } ?: 0
        return meuValor - piorRival
    }

    private fun avaliarTime(state: CanastraState, time: Int): Int {
        val jogos = state.melds.getOrElse(time) { emptyList() }
        val maoDoTime = cadeirasDoTime(state, time).flatMap { state.hand(it) }

        var total = state.scores.getOrElse(time) { 0 }
        total += jogos.sumOf { it.score }
        total += jogos.count { it.isCanastra } * UTILIDADE_DA_CANASTRA
        total += jogos.sumOf { potencialDeCrescimento(it, maoDoTime.distinct()) }
        total += progressoDosJogosParciais(jogos)
        total -= custoDeNaipeRepartido(jogos, state.seats)

        val vermelhos = state.redThrees.getOrElse(time) { 0 } * RED_THREE_VALUE
        if (jogos.any { it.isCanastra }) total += vermelhos

        total += CANASTRA_GOING_OUT_BONUS * state.batidas.getOrElse(time) { 0 }

        total -= penalidadeDaMao(state, time, maoDoTime)
        total -= ameacaSofrida(state, time)

        return total
    }

    private fun cadeirasDoTime(state: CanastraState, time: Int): List<Seat> =
        (0 until state.seats).map { Seat(it) }.filter { state.teamOf(it) == time }

    /**
     * O que as cartas que o time ainda segura prometem somar a [jogo] mais adiante.
     *
     * É o que faz a IA enxergar que trocar o curinga não vale só a carta que entrou: ao sair
     * do buraco que tapava, o curinga vai para uma ponta e passa a representar **outro**
     * valor — que por sua vez pode ser trocado de novo, crescendo a canastra mais uma vez
     * (ver o teste que encadeia duas trocas). Como a promessa é recalculada sobre o jogo
     * **depois** de cada troca simulada pela própria busca, a segunda troca já aparece aqui
     * sem precisar de regra especial nem de mais um nível de profundidade.
     *
     * A pergunta "esta carta cabe?" vai sempre para [extendMeld] e [wildRepresents] — as
     * mesmas funções que o motor usa para gerar os lances legais.
     */
    private fun potencialDeCrescimento(jogo: Meld, maoDoTime: List<Card>): Int {
        if (maoDoTime.isEmpty()) return 0
        val exata = wildRepresents(jogo)
        val cabem = maoDoTime.count { carta -> carta == exata || extendMeld(jogo, carta) != null }
        return cabem * if (jogo.isClean) PROMESSA_DE_CRESCIMENTO_LIMPA else PROMESSA_DE_CRESCIMENTO
    }

    /**
     * Incentivo a baixar jogo mesmo sem canastra fechada — cada carta além do mínimo de três
     * pesa a favor, e o quanto pesa é a própria personalidade: o agressivo quer material na
     * mesa logo, o acumulador prefere esperar um jogo maior ou mais seguro.
     */
    private fun progressoDosJogosParciais(jogos: List<Meld>): Int {
        val peso = when (personality) {
            AiPersonality.AGRESSIVO -> 25
            AiPersonality.ACUMULADOR -> 2
            AiPersonality.BALANCEADO -> 12
        }
        return jogos.filterNot { it.isCanastra }.sumOf { (it.cards.size - CANASTRA_MIN_MELD) * peso }
    }

    private fun custoDeNaipeRepartido(jogos: List<Meld>, seats: Int): Int {
        val custo = if (seats == 4) CUSTO_NAIPE_REPARTIDO_DUPLAS else CUSTO_NAIPE_REPARTIDO
        val naipesVistos = mutableSetOf<Suit>()
        var repeticoes = 0
        for (jogo in jogos) {
            if (jogo.kind != MeldKind.SEQUENCE) continue
            val naipe = jogo.naturals.firstOrNull()?.suit ?: continue
            if (!naipesVistos.add(naipe)) repeticoes++
        }
        return repeticoes * custo
    }

    /**
     * O peso das cartas ainda na mão. Parte da conta de `scoreHand` (que cobra `cardValue` de
     * tudo o que sobrar na mão no fim), com **uma diferença deliberada**: aqui o curinga e o
     * dois custam só [CUSTO_CURINGA_NA_MAO] em vez dos vinte/cinquenta que o placar cobraria.
     * Não é descuido nem cópia errada da fórmula — é estratégia: seguir `scoreHand` ao pé da
     * letra faria a IA se desfazer de curinga cedo para aliviar a mão, e curinga na mão é
     * recurso, não dívida. Quem cobra o preço de verdade é o placar, no fim da mão.
     *
     * Por cima vêm o desconto de personalidade e o alívio de quem ainda não fez o primeiro
     * jogo da mão, que tem direito a gastar até [CANASTRA_OPENING_MIN_VALUE] sem que isso pese
     * contra ela (é o que `isOpeningPathPreserved` já garante ser jogável; a avaliação só
     * evita punir por uma obrigação que o motor não considera dívida real).
     */
    private fun penalidadeDaMao(state: CanastraState, time: Int, maoDoTime: List<Card>): Int {
        val bruto = maoDoTime.sumOf { carta -> if (isWild(carta)) CUSTO_CURINGA_NA_MAO else cardValue(carta) }
        val multiplicador = when (personality) {
            AiPersonality.AGRESSIVO -> 1.5
            AiPersonality.ACUMULADOR -> 0.5
            AiPersonality.BALANCEADO -> 1.0
        }
        val comMultiplicador = (bruto * multiplicador).toInt()

        val aindaPrecisaDaAberturaAlta = state.scores.getOrElse(time) { 0 } >= CANASTRA_OPENING_THRESHOLD &&
            !state.firstMeldDone.getOrElse(time) { false }
        return if (aindaPrecisaDaAberturaAlta) {
            maxOf(0, comMultiplicador - CANASTRA_OPENING_MIN_VALUE)
        } else {
            comMultiplicador
        }
    }

    /** A ameaça que os times rivais impõem a [time] — ver [PESO_AMEACA_DE_BATIDA]. */
    private fun ameacaSofrida(state: CanastraState, time: Int): Int =
        (0 until state.teams).filter { it != time }.maxOfOrNull { rival -> ameacaDeBatida(state, rival) } ?: 0

    private fun ameacaDeBatida(state: CanastraState, time: Int): Int {
        if (!state.hasCanastra(time)) return 0
        val aindaTemMortoParaEsseTime = state.mortos.isNotEmpty() && !state.tookMorto.getOrElse(time) { false }
        if (aindaTemMortoParaEsseTime) return 0

        val menorMao = cadeirasDoTime(state, time).minOfOrNull { state.handSize(it) } ?: return 0
        val cartasQueFaltam = (CANASTRA_HAND_SIZE - menorMao).coerceIn(0, CANASTRA_HAND_SIZE)
        return PESO_AMEACA_DE_BATIDA * cartasQueFaltam
    }
}

/**
 * Ordem em que os lances entram na busca — só afeta poda e desempate, nunca a decisão final
 * (quem decide é sempre o [CanastraEvaluatorImpl] lá na folha da busca).
 */
val CanastraOrdering: MoveOrdering<CanastraState, CanastraMove> =
    MoveOrdering<CanastraState, CanastraMove> { state, moves ->
        if (moves.size < 2) {
            moves
        } else {
            val mao = state.hand(state.turn)
            val curingasNaMao = mao.count { isWild(it) }
            val cartasConhecidasDoProximo = cartasConhecidasDoProximoAdversario(state)
            moves.sortedByDescending { move -> prioridadeDoLance(move, curingasNaMao, cartasConhecidasDoProximo) }
        }
    }

/**
 * O que a cadeira que joga **a seguir** (`turn + 1`, ver `proximoTurno` em `applyDiscard`)
 * já mostrou ter pego do lixo — e só se essa cadeira for de outro time. É sempre a próxima,
 * nunca a anterior: a anterior já jogou, e não é ela quem aproveita o descarte de agora.
 * `knownOpponentCards` é público (o lixo já era visível antes de ser pego — ver a nota em
 * [CanastraGame.redactFor]), então lê-lo aqui não vaza informação nenhuma.
 */
private fun cartasConhecidasDoProximoAdversario(state: CanastraState): List<Card> {
    val proximoJogador = (state.turn.index + 1) % state.seats
    val timeProximo = state.teamOf(Seat(proximoJogador))
    if (timeProximo == state.teamOf(state.turn)) return emptyList()
    return state.knownOpponentCards[proximoJogador].orEmpty()
}

private fun prioridadeDoLance(move: CanastraMove, curingasNaMao: Int, cartasConhecidasDoProximo: List<Card>): Int =
    when (move) {
        // Trocar o curinga de graça é sempre um ganho — nunca custa carta que já não fosse
        // gasta, então examinar primeiro poda mais.
        is CanastraMove.SwapWild -> 2_500 + cardValue(move.card)
        is CanastraMove.Meld -> (if (move.into != null) 2_000 else 1_000) + move.cards.sumOf { cardValue(it) }
        CanastraMove.TakeDiscard -> 900
        CanastraMove.DrawStock -> 800
        CanastraMove.Pass -> 700
        is CanastraMove.Discard -> prioridadeDoDescarte(move.card, curingasNaMao, cartasConhecidasDoProximo)
    }

private fun prioridadeDoDescarte(carta: Card, curingasNaMao: Int, cartasConhecidasDoProximo: List<Card>): Int = when {
    // Um curinga só na mão é recurso raro demais para descartar sem necessidade; com mais de
    // um, sobra folga e a ordenação relaxa (mas ainda deixa por último).
    isWild(carta) -> if (curingasNaMao > 1) -20 else -100
    // O três preto tranca o lixo de quem vem: é um bom descarte quando não há nada melhor.
    isBlackThree(carta) -> 10
    // Não alimenta de propósito o que o próximo adversário já mostrou estar catando.
    cartasConhecidasDoProximo.any { it.suit == carta.suit && abs(it.rank.order - carta.rank.order) <= 2 } -> -50
    else -> 100 - cardValue(carta)
}

/**
 * Sorteia um mundo completo e plausível a partir do que [state] (já redigido por
 * [CanastraGame.redactFor]) deixa ver: a própria mão, os jogos na mesa e o lixo são
 * públicos, e tudo o mais — as outras mãos, o monte, o morto — vem do que sobra do baralho,
 * embaralhado de novo.
 *
 * Os três vermelhos que já saíram (contados em [CanastraState.redThrees]) nunca voltam a
 * ser sorteados para lugar nenhum: eles não aparecem em `vistas` (saem da mão sem passar
 * pelo lixo ou pela mesa), então continuariam soltos no baralho restante se não fossem
 * removidos à parte aqui. Os que **ainda não saíram** de verdade podem, sim, aparecer no
 * mundo sorteado — só nunca dentro de uma mão, porque a regra do jogo nunca deixa um três
 * vermelho ficar lá (ver `drawFromStock` em `Canastra.kt`).
 */
fun completeCanastra(state: CanastraState, rng: Rng): CanastraState {
    val vistas = mutableListOf<Card>()
    state.hands.forEach { mao -> vistas += mao.filterNot { it.isHidden } }
    state.melds.forEach { jogos -> jogos.forEach { vistas += it.cards } }
    vistas += state.discard

    val restante = deckOf(CANASTRA_DECKS, CANASTRA_JOKERS_PER_DECK).toMutableList()
    for (carta in vistas) restante.remove(carta)

    var vermelhosJaContados = state.redThrees.sum()
    while (vermelhosJaContados > 0) {
        val indice = restante.indexOfFirst { isRedThree(it) }
        if (indice < 0) break
        restante.removeAt(indice)
        vermelhosJaContados--
    }

    val embaralhadas = rng.shuffle(restante).value
    val paraMaos = ArrayDeque(embaralhadas.filterNot { isRedThree(it) })
    val vermelhosRestantes = embaralhadas.filter { isRedThree(it) }

    fun tirarParaMao(quantas: Int): List<Card> = List(minOf(quantas, paraMaos.size)) { paraMaos.removeFirst() }

    val maos = state.hands.map { mao ->
        if (mao.none { it.isHidden }) {
            mao
        } else {
            mao.filterNot { it.isHidden } + tirarParaMao(mao.count { it.isHidden })
        }
    }

    // O que sobrou para mãos, mais os vermelhos que não podiam ir para lá, é o que resta para
    // completar monte e morto — nenhum dos dois é mão de ninguém, então a ordem não importa.
    val paraMesa = ArrayDeque(paraMaos.toList() + vermelhosRestantes)
    fun tirarParaMesa(quantas: Int): List<Card> = List(minOf(quantas, paraMesa.size)) { paraMesa.removeFirst() }

    val monte = if (state.stock.none { it.isHidden }) state.stock else tirarParaMesa(state.stock.size)
    val mortos = state.mortos.map { morto ->
        if (morto.none { it.isHidden }) morto else tirarParaMesa(morto.size)
    }

    return state.copy(hands = maos, stock = monte, mortos = mortos)
}

val CanastraAi: GameAi<CanastraState, CanastraMove> = DeterminizedAi(
    game = CanastraGame,
    // Pode trocar entre AGRESSIVO, ACUMULADOR ou BALANCEADO aqui para ver o comportamento mudar.
    evaluator = CanastraEvaluatorImpl(AiPersonality.BALANCEADO),
    ordering = CanastraOrdering,
    // Trocar o curinga pela carta exata gasta uma carta da mão mas sempre devolve mais do que
    // tira (ver [CanastraEvaluatorImpl.potencialDeCrescimento] e a nota em
    // [DeterminizedAi.neverMistaken]): nunca é o sorteio de erro que deve decidir isso, e sim
    // a busca, que já enxerga o jogo crescendo e o curinga se reposicionando.
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
