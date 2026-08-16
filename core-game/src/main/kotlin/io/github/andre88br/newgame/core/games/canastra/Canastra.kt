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

/** Dois baralhos com dois curingas cada: 108 cartas. */
const val CANASTRA_DECKS: Int = 2
const val CANASTRA_JOKERS_PER_DECK: Int = 2

/** Cartas na mão de cada um, e no morto. */
const val CANASTRA_HAND_SIZE: Int = 13

/**
 * Quantos mortos a mesa tem.
 *
 * Um só, e só até três pessoas. Em duplas não há morto nenhum — e essa ausência muda o jogo
 * inteiro: sem a rede do morto, quem fica sem cartas bate na hora, e é por isso que
 * [CanastraGame] passa a exigir canastra antes de deixar alguém zerar a mão. Com morto na
 * mesa, zerar é só o começo da segunda metade da mão; sem morto, zerar é o fim dela.
 */
fun mortosFor(seats: Int): Int = if (seats == 4) 0 else 1

/** Um jogo tem no mínimo três cartas. */
const val CANASTRA_MIN_MELD: Int = 3

/** Sete cartas fazem canastra. */
const val CANASTRA_SIZE: Int = 7

/**
 * No máximo um curinga por jogo.
 *
 * Mais do que isso e o jogo deixa de ser de cartas — é a diferença entre tapar um buraco na
 * sequência e viver de curinga.
 */
const val CANASTRA_MAX_WILDS: Int = 1

/** A partida vai até aqui. */
const val CANASTRA_TARGET: Int = 3_000

/** Bônus de bater — e bater é qualquer vez que a mão fica vazia, inclusive ao pegar o morto. */
const val CANASTRA_GOING_OUT_BONUS: Int = 50

/** Vale um três vermelho, e só conta se a dupla tiver canastra. */
const val RED_THREE_VALUE: Int = 100

/** A partir daqui, o primeiro jogo da mão da dupla precisa valer [CANASTRA_OPENING_MIN_VALUE]. */
const val CANASTRA_OPENING_THRESHOLD: Int = 1500

/** O mínimo do primeiro jogo da mão, para quem já está em [CANASTRA_OPENING_THRESHOLD]. */
const val CANASTRA_OPENING_MIN_VALUE: Int = 150

/**
 * **Curinga**: o coringa e o dois.
 *
 * O dois é curinga em canastra, e é por isso que ele vale cinquenta pontos apesar de ser a
 * carta mais baixa do baralho — o valor segue a utilidade, não a ordem.
 */
fun isWild(card: Card): Boolean = card.isJoker || card.rank == Rank.TWO

/** O três vermelho, que não se joga: vale ponto parado na mesa, e só com canastra. */
fun isRedThree(card: Card): Boolean = card.rank == Rank.THREE && card.isRed

/** O três preto, que tranca o lixo. Não entra em jogo nenhum. */
fun isBlackThree(card: Card): Boolean = card.rank == Rank.THREE && !card.isRed

/** Quanto a carta vale na contagem. O três vale caro porque, na mão, ele é o que mais pesa. */
fun cardValue(card: Card): Int = when {
    card.isJoker -> 50
    card.rank == Rank.TWO -> 50
    card.rank == Rank.THREE -> 100
    card.rank == Rank.ACE -> 20
    card.rank.order >= Rank.EIGHT.order -> 10
    else -> 5
}

/**
 * Os valores que entram numa sequência, do quatro ao ás — onze degraus.
 *
 * O dois fica de fora porque é sempre curinga, e o três porque nunca entra em jogo nenhum.
 * A sequência não dá a volta: o ás fecha por cima, e não emenda de novo com o quatro.
 */
val CANASTRA_SEQUENCE_RANKS: List<Rank> = listOf(
    Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN,
    Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE,
)

/** A posição do valor na escala da sequência, ou `-1` se ele não entra numa. */
fun sequenceOrder(rank: Rank): Int = CANASTRA_SEQUENCE_RANKS.indexOf(rank)

/** Sequência (mesmo naipe, valores seguidos) ou trinca (mesmo valor)? */
@Serializable
enum class MeldKind { SEQUENCE, SET }

/**
 * Um jogo na mesa: uma sequência do mesmo naipe, ou uma trinca do mesmo valor — com ou sem
 * curinga.
 *
 * A dupla é dona do jogo, não a pessoa — em canastra de quatro, quem começou o jogo e quem o
 * completa costumam ser jogadores diferentes, e a canastra conta para os dois.
 *
 * [cards] fica em **ordem canônica**: numa sequência, do menor valor ao maior, com o curinga
 * já na posição exata que ele representa. É essa ordem que permite recuperar, só olhando o
 * índice de uma carta na lista, que degrau da escada ela ocupa — sem guardar mais nenhum
 * campo. Ver [sequenceSpan].
 */
@Serializable
data class Meld(val cards: List<Card> = emptyList()) {

    val wilds: List<Card> get() = cards.filter { isWild(it) }

    val naturals: List<Card> get() = cards.filterNot { isWild(it) }

    /** O valor de que é este jogo, ou `null` num jogo só de curinga — que não existe. Só faz sentido para trinca. */
    val rank: Rank? get() = naturals.firstOrNull()?.rank

    val isCanastra: Boolean get() = cards.size >= CANASTRA_SIZE

    /**
     * Canastra limpa: sem dois. Vale o dobro.
     *
     * O coringa não suja, o dois suja — e os dois são curinga do mesmo jeito na hora de
     * formar o jogo. A diferença é de prêmio, não de regra: o coringa é carta rara, tem
     * quatro no baralho duplo inteiro, e quem consegue fechar sete cartas com um deles fez
     * por merecer. O dois tem oito, aparece sempre, e fechar canastra com dois é o caminho
     * fácil que o jogo cobra mais barato.
     */
    val isClean: Boolean get() = isCanastra && cards.none { it.rank == Rank.TWO }

    /**
     * Sequência ou trinca? É derivado, e não guardado, porque a distinção nunca pode mentir:
     * duas cartas naturais do mesmo valor não formam sequência, e de valores diferentes não
     * formam trinca, então um jogo já validado nunca fica ambíguo.
     */
    val kind: MeldKind
        get() = if (naturals.size >= 2 && naturals.map { it.rank }.distinct().size == 1) {
            MeldKind.SET
        } else {
            MeldKind.SEQUENCE
        }

    /**
     * O intervalo de posições, na escala de [CANASTRA_SEQUENCE_RANKS], que este jogo ocupa.
     * Só faz sentido para [MeldKind.SEQUENCE].
     *
     * Basta achar uma carta natural qualquer e olhar o índice dela na lista: como [cards]
     * está em ordem canônica, a diferença entre a posição real do valor e o índice na lista é
     * constante — é o começo do intervalo, esteja o curinga onde estiver.
     */
    fun sequenceSpan(): IntRange {
        val indice = cards.indexOfFirst { !isWild(it) }
        val posicao = sequenceOrder(cards[indice].rank)
        val inicio = posicao - indice
        return inicio until (inicio + cards.size)
    }

    /**
     * Pontos das cartas, mais o prêmio da canastra.
     *
     * Só a canastra **limpa** rende por render: a partir da oitava carta, cada carta além
     * soma mais cem — o prêmio de uma sequência que não parou de crescer. A suja vale o
     * bônus fixo de sempre, do mesmo jeito que uma de sete cartas.
     */
    val score: Int
        get() = cards.sumOf { cardValue(it) } + when {
            isClean -> 200 + (cards.size - CANASTRA_SIZE) * 100
            isCanastra -> 100
            else -> 0
        }

    override fun toString(): String = cards.joinToString(" ")
}

/**
 * Uma trinca é válida? Três cartas do mesmo valor, ao menos duas naturais, no máximo um
 * curinga — e nunca um três, que não entra em jogo nenhum.
 */
fun isValidSet(cards: List<Card>): Boolean {
    if (cards.size < CANASTRA_MIN_MELD) return false
    val naturais = cards.filterNot { isWild(it) }
    val curingas = cards.filter { isWild(it) }
    if (naturais.size < 2) return false
    if (curingas.size > CANASTRA_MAX_WILDS) return false
    if (naturais.any { it.rank == Rank.THREE }) return false
    return naturais.map { it.rank }.distinct().size == 1
}

/**
 * O arranjo canônico de [cards] como sequência do mesmo naipe — com o curinga, se houver, já
 * na posição do valor que ele representa —, ou `null` se elas não formam uma.
 *
 * O algoritmo é determinístico e sem tentativa e erro, porque o teto de um curinga por jogo
 * torna isso possível: sem curinga, as naturais têm de fechar uma corrida sem buraco. Com um
 * curinga, só há dois jeitos de ele caber — tapando o único buraco no meio das naturais, ou
 * ocupando uma ponta livre quando as naturais já são uma corrida fechada — e nunca os dois ao
 * mesmo tempo. Quando o curinga vai para a ponta, a escolha é a cauda primeiro: é o que faz
 * `{4, 5, curinga}` fechar como 4-5-6, e nunca como o três, que a canastra proíbe em jogo.
 */
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
            // Um buraco só entre as naturais: o curinga o tapa, e a posição é fixa.
            val buraco = (menor..maior).first { it !in ordenadas }
            ordenadas + buraco
        }

        vao == naturais.size -> {
            // Corrida já fechada: o curinga vai numa ponta, cauda primeiro.
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

/** Um jogo novo, já no arranjo canônico — sequência ou trinca —, ou `null` se não fecha nenhuma. */
fun asMeld(cards: List<Card>): Meld? {
    asSequence(cards)?.let { return Meld(it) }
    if (isValidSet(cards)) return Meld(cards)
    return null
}

/**
 * O jogo depois de acrescentar [card], ou `null` se ela não encaixa.
 *
 * Numa sequência, o curinga não tem posição fixa — [wildRepresents] e [Meld.sequenceSpan] já
 * a calculam a partir da lista de cartas, nunca a guardam à parte —, então acrescentar uma
 * carta é recalcular o arranjo canônico do jogo inteiro (ver [extendSequence]), e não só
 * checar as duas pontas atuais: uma carta que abre um buraco mais perto do que a ponta onde
 * o curinga está pode empurrá-lo para lá. Numa trinca, qualquer carta do valor entra, e o
 * curinga só se ainda não houver um.
 */
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

/**
 * "Jogo mais uma carta" é só "que arranjo canônico existe para este conjunto de cartas" —
 * exatamente o que [asSequence] já calcula para formar um jogo novo, curinga incluso. Não há
 * heurística nova aqui: reaproveitar [asSequence] é o que permite ao curinga se reposicionar
 * (de uma ponta para um buraco mais perto, por exemplo) em vez de ficar preso onde entrou.
 */
private fun extendSequence(meld: Meld, card: Card): Meld? = asSequence(meld.cards + card)?.let { Meld(it) }

/**
 * A carta que o curinga deste jogo está representando, ou `null` se o jogo não tem curinga —
 * ou é trinca, onde o curinga não representa valor nenhum específico.
 */
fun wildRepresents(meld: Meld): Card? {
    if (meld.kind != MeldKind.SEQUENCE || meld.wilds.isEmpty()) return null
    val indice = meld.cards.indexOfFirst { isWild(it) }
    val posicao = meld.sequenceSpan().first + indice
    val rank = CANASTRA_SEQUENCE_RANKS.getOrNull(posicao) ?: return null
    return Card(rank, meld.naturals.first().suit)
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
    /** Quantas vezes a dupla ficou sem cartas nesta mão — pegando o morto ou batendo de vez. */
    val batidas: List<Int> = emptyList(),
    /** A dupla já baixou o primeiro jogo desta mão? Reseta a cada mão nova. */
    val firstMeldDone: List<Boolean> = emptyList(),
    /**
     * Quanto a dupla já baixou **nesta vez**, enquanto o primeiro jogo da mão ainda não fechou
     * o mínimo de [CANASTRA_OPENING_MIN_VALUE].
     *
     * O mínimo não precisa vir de um jogo só: baixar duas trincas de 80 na mesma vez soma 160,
     * e abre do mesmo jeito que uma sequência de 150 sozinha. Este contador é o que soma os
     * jogos (e as cartas que estendem um jogo já baixado nesta vez) até bater o mínimo — e
     * zera a cada vez nova, porque a régua é "nesta vez", não "na mão inteira".
     */
    val openingProgress: List<Int> = emptyList(),
    /**
     * A carta do lixo que se acabou de pegar, ainda sem entrar em jogo nenhum.
     *
     * Enquanto não for `null`, o único lance permitido é baixar (ou trocar curinga com)
     * exatamente esta carta — nada de descartar, nem baixar outra coisa primeiro. Ver
     * [CanastraGame.canPlayCard]: quem pega o lixo já provou, no instante de pegar, que existe
     * pelo menos um jeito de cumprir isso, então esta restrição nunca fecha todos os lances.
     */
    val owedCard: Card? = null,
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
    /** Quem é o primeiro a jogar nesta rodada. O próximo a dar as cartas rotaciona. */
    val startingSeat: Seat = Seat.FIRST,
) : GameState {

    /** Em quatro, as duplas são as cadeiras opostas; em dois, cada um é a sua dupla. */
    fun teamOf(seat: Seat): Int = if (seats == 4) seat.index % 2 else seat.index

    val teams: Int get() = if (seats == 4) 2 else seats

    fun hand(seat: Seat): List<Card> = hands.getOrElse(seat.index) { emptyList() }

    fun handSize(seat: Seat): Int = hand(seat).size

    fun meldsOf(seat: Seat): List<Meld> = melds.getOrElse(teamOf(seat)) { emptyList() }

    /** A carta de cima do lixo, que decide se dá para comprar dali. */
    val discardTop: Card? get() = discard.lastOrNull()

    /**
     * O lixo está trancado: um três preto ou um curinga em cima impede a próxima pessoa de
     * pegá-lo.
     *
     * O curinga tranca pelo mesmo motivo do três preto: é a carta que ninguém quer entregar
     * de bandeja, e descartá-lo é a única forma de trancar o lixo à custa de uma carta que
     * vale a pena guardar — o preço é o que torna a trava uma escolha de verdade, e não um
     * truque de graça.
     */
    val discardBlocked: Boolean get() = discardTop?.let { isBlackThree(it) || isWild(it) } ?: false

    /** A dupla tem canastra? Sem uma, ninguém bate, e nenhuma trinca pode ser baixada. */
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

    /**
     * Troca o curinga do jogo [into] pela carta natural exata que ele representa. O curinga
     * desce para depois da carta mais alta do jogo, e o jogo cresce em uma carta.
     */
    @Serializable
    data class SwapWild(val into: Int, val card: Card) : CanastraMove {
        override fun describe(): String = "$card troca o curinga em →$into"
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
 * Dois baralhos, quatro curingas e treze cartas para cada um. A vez tem três tempos:
 * compra-se (do monte ou o lixo inteiro), baixa-se o que quiser, e descarta-se — e é o
 * descarte que passa a vez.
 *
 * **O jogo normal é uma sequência do mesmo naipe**, do quatro ao ás, com no máximo um
 * curinga tapando um buraco ou ocupando uma ponta. **A trinca** (três ou mais cartas do
 * mesmo valor) existe, mas só pode ser baixada depois que a dupla já tiver uma canastra —
 * antes disso, só a sequência serve para abrir jogo.
 *
 * Com a carta natural exata na mão, dá para **trocar o curinga** de uma sequência já baixada:
 * ele desce para depois da carta mais alta do jogo, e o jogo cresce em uma carta — mesmo
 * quando a sequência já ocupa a escala inteira, do quatro ao ás, e essa posição não representa
 * carta nenhuma de verdade. O curinga trocado nunca volta para a mão de quem trocou.
 *
 * **O morto é um só, e nem sempre existe**: mesa de duas ou três pessoas tem um morto na
 * mesa, que é de quem chegar primeiro; mesa de duplas não tem morto nenhum. Ver [mortosFor].
 *
 * **As duas regras do três**, que são o que separa canastra de qualquer outro jogo de
 * formar sequências:
 *
 * - O **três vermelho** não se joga. Ele vale cem pontos parado na mesa — só se a dupla tiver
 *   canastra — e vai para lá sozinho assim que aparece na mão; quem o tira do monte compra
 *   outra carta no lugar.
 * - O **três preto** tranca o lixo: descartado, impede a pessoa seguinte de pegar o monte de
 *   descarte. Nunca entra em jogo nenhum — guardá-lo custa cem pontos na mão.
 *
 * Bater exige canastra. Ficar sem cartas com o morto ainda na mesa não é bater: pega-se o
 * morto e a vez continua — e as duas coisas valem cinquenta pontos de bônus.
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

    /** Reparte uma mão: treze para cada um, o morto (se houver) e uma carta virada no lixo. */
    private fun dealHand(seats: Int, scores: List<Int>, rng: Rng, startingSeat: Seat = Seat.FIRST): CanastraState {
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
            batidas = List(teams) { 0 },
            firstMeldDone = List(teams) { false },
            openingProgress = List(teams) { 0 },
            turn = startingSeat,
            phase = CanastraPhase.DRAW,
            scores = scores,
            seats = seats,
            rng = embaralhado.rng,
            startingSeat = startingSeat,
        )
    }

    override fun legalMoves(state: CanastraState): List<CanastraMove> {
        if (outcome(state).isOver || state.wentOut >= 0) return emptyList()
        val mao = state.hand(state.turn)
        if (mao.any { it.isHidden }) return emptyList()

        if (state.phase == CanastraPhase.DRAW) {
            val saida = mutableListOf<CanastraMove>()
            if (state.stock.isNotEmpty()) saida += CanastraMove.DrawStock
            if (state.discard.isNotEmpty() && !state.discardBlocked && canTakeDiscard(state)) {
                saida += CanastraMove.TakeDiscard
            }
            // Monte vazio e lixo trancado (ou sem jogo possível): a mão acaba, e `outcome`
            // cuida disso.
            return saida
        }

        val jogos = meldMoves(state, mao)
        val devida = state.owedCard
        if (devida != null) {
            // A carta que se acabou de pegar do lixo tem que entrar em jogo antes de
            // qualquer outra coisa — nada de descartar, nada de baixar outra coisa primeiro.
            return jogos.filter { move ->
                when (move) {
                    is CanastraMove.Meld -> devida in move.cards
                    is CanastraMove.SwapWild -> move.card == devida
                    else -> false
                }
            }
        }

        // Se já começou a abrir o jogo nesta rodada e ainda não bateu os 150 pontos,
        // o jogador ou a IA SÓ pode continuar baixando cartas. O descarte fica totalmente bloqueado.
        if (openingIncomplete(state, state.teamOf(state.turn))) {
            return jogos
        }
        
        return jogos + discardMoves(state, mao)
    }

    /**
     * A dupla já começou o primeiro jogo da mão nesta vez, mas ainda não bateu o mínimo de
     * [CANASTRA_OPENING_MIN_VALUE]? Enquanto isso for verdade, a vez não pode fechar em
     * descarte — ver [legalMoves] e [applyMove].
     */
    private fun openingIncomplete(state: CanastraState, team: Int): Boolean {
        if (state.firstMeldDone.getOrElse(team) { false }) return false
        if (state.scores.getOrElse(team) { 0 } < CANASTRA_OPENING_THRESHOLD) return false
        val progresso = state.openingProgress.getOrElse(team) { 0 }
        return progresso in 1 until CANASTRA_OPENING_MIN_VALUE
    }

    /**
     * Os jogos que valem a pena oferecer, e não todos os que existem.
     *
     * Enumerar toda combinação de cartas que forma jogo estoura: treze cartas dão milhares de
     * subconjuntos, e a busca da IA morreria neles. Aqui saem os que uma pessoa jogaria — as
     * corridas que já estão prontas na mão, as que um curinga fecha, as trincas (só depois da
     * primeira canastra), cada carta que estende um jogo já baixado e cada troca de curinga
     * possível. Quem valida de verdade é [applyMove] — e a tela pergunta com [canMeld] a um
     * conjunto exato de cartas, sem depender desta lista ser exaustiva.
     */
    private fun meldMoves(state: CanastraState, mao: List<Card>): List<CanastraMove> {
        val saida = mutableListOf<CanastraMove>()
        saida += newMeldCandidates(state, mao).map { CanastraMove.Meld(it.cards) }

        // Acrescentar a um jogo da dupla: cada carta que serve, uma de cada vez.
        state.meldsOf(state.turn).forEachIndexed { index, jogo ->
            for (carta in mao.distinct()) {
                if (extendMeld(jogo, carta) != null) saida += CanastraMove.Meld(listOf(carta), into = index)
            }
            // Trocar o curinga: só se a carta exata que ele representa estiver na mão.
            wildRepresents(jogo)?.let { exata -> if (exata in mao) saida += CanastraMove.SwapWild(index, exata) }
        }

        return saida.filterNot { move ->
            when (move) {
                is CanastraMove.Meld ->
                    encurrala(state, mao.size, move.cards.size, teraCanastra(state, move)) ||
                        !isOpeningPathPreserved(state, mao, move) // <-- TRAVA ATIVA AQUI
                
                is CanastraMove.SwapWild -> {
                    val jogo = state.meldsOf(state.turn).getOrNull(move.into)
                    (jogo != null && encurrala(state, mao.size, 1, teraCanastraSwap(state, move))) ||
                        !isOpeningPathPreserved(state, mao, move) // <-- TRAVA ATIVA AQUI TAMBÉM
                }
                else -> false
            }
        }
    }

    /**
     * Os jogos **novos** que [hand] permite formar — sem contar extensão de jogo já na mesa,
     * que depende de onde o jogo está, não só da mão.
     *
     * Enumerar toda combinação estoura (treze cartas dão milhares de subconjuntos); aqui saem
     * as corridas máximas de cada naipe, as que um curinga fecha (tapando um buraco ou
     * esticando a ponta de uma corrida já pronta) e as trincas (só depois da primeira
     * canastra). Não é exaustivo — é o que efetivamente aparece pronto na mão —, mas é o
     * bastante tanto para oferecer lance quanto para provar que uma carta específica tem como
     * entrar em algum jogo (ver [canPlayCard]).
     */
    private fun newMeldCandidates(state: CanastraState, hand: List<Card>): List<Meld> {
        val saida = mutableListOf<Meld>()
        val curingas = hand.filter { isWild(it) }
        val naturaisMao = hand.filterNot { isWild(it) || it.rank == Rank.THREE }

        // Sequências novas: por naipe, as corridas máximas de valores seguidos na mão.
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

            // Duas cartas com um buraco só entre elas: o curinga fecha o meio.
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

        // Trincas novas: só depois que a dupla já tem canastra.
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

    /**
     * Calcula o valor máximo que a mão consegue formar em jogos novos usando busca (DFS),
     * parando assim que atingir a pontuação que [faltam] para otimizar o desempenho.
     */
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

    /**
     * NOVA REGRA PROTETORA: Esta função simula o lance antes dele acontecer.
     * Ela subtrai as cartas que a IA quer jogar e verifica se o RESTO da mão
     * AINDA consegue bater a meta dos 150 pontos. Se não conseguir, a jogada é abortada,
     * impedindo que a IA "canibalize" a própria mão e fique presa sem poder descartar.
     */
    private fun isOpeningPathPreserved(state: CanastraState, mao: List<Card>, move: CanastraMove): Boolean {
        val team = state.teamOf(state.turn)
        
        // Se já abriu na mão ou se a equipe ainda não bateu 1500 pontos no campeonato, não precisa travar nada.
        if (!openingIncomplete(state, team) && (state.scores.getOrElse(team) { 0 } >= CANASTRA_OPENING_THRESHOLD) == false) return true
        if (state.firstMeldDone.getOrElse(team) { false }) return true
        if (state.scores.getOrElse(team) { 0 } < CANASTRA_OPENING_THRESHOLD) return true
        
        val pontosAdicionais = when (move) {
            is CanastraMove.Meld -> move.cards.sumOf { cardValue(it) }
            is CanastraMove.SwapWild -> cardValue(move.card)
            else -> 0
        }
        
        val jaBaixado = state.openingProgress.getOrElse(team) { 0 }
        
        // Se este movimento SOZINHO (somado ao que já está na mesa) já fecha os 150 pontos, libere imediatamente.
        if (jaBaixado + pontosAdicionais >= CANASTRA_OPENING_MIN_VALUE) return true
        
        val faltam = CANASTRA_OPENING_MIN_VALUE - (jaBaixado + pontosAdicionais)
        
        // Separa as cartas que estão sendo baixadas neste lance
        val removedCards = when (move) {
            is CanastraMove.Meld -> move.cards
            is CanastraMove.SwapWild -> listOf(move.card)
            else -> emptyList()
        }
        
        // Tira as cartas do cálculo virtual
        val remainingHand = mao.toMutableList()
        for (c in removedCards) {
            remainingHand.remove(c)
        }
        
        // Avalia de forma conservadora se as cartas que SOBRARAM na mão conseguem fechar o buraco
        val maxPossivel = maxOpeningScore(state, remainingHand, faltam)
        return maxPossivel >= faltam
    }

    /**
     * Existe algum jogo — novo, ou extensão de um já na mesa — que [card] participe, usando
     * [hand]? É a mesma pergunta de "dá para baixar isso", restrita a jogos que carregam esta
     * carta específica, e já filtrada pelas mesmas regras que valeriam para o lance de
     * verdade (não pode encurralar a mão, e o jogo novo precisa bater o mínimo de abertura).
     *
     * Reaproveita [meldMoves] inteiro, com [hand] no lugar da mão real do estado — é o que
     * garante que a resposta aqui e o lance realmente oferecido depois de pegar o lixo nunca
     * se contradizem: são a mesma conta, com a mesma mão.
     */
    private fun canPlayCard(state: CanastraState, hand: List<Card>, card: Card): Boolean =
        meldMoves(state, hand).any { move ->
            when (move) {
                is CanastraMove.Meld -> card in move.cards
                is CanastraMove.SwapWild -> move.card == card
                else -> false
            }
        }

    /**
     * Dá para pegar o lixo? Só se a carta do topo tiver como entrar em algum jogo, contando
     * com a mão de agora **mais** o resto do lixo — é tudo isso que se pega junto.
     */
    private fun canTakeDiscard(state: CanastraState): Boolean {
        val topo = state.discardTop ?: return false
        val maoDepois = state.hand(state.turn) + state.discard.filterNot { isRedThree(it) }
        return canPlayCard(state, maoDepois, topo)
    }

    /**
     * A dupla ainda tem morto para pegar?
     *
     * O morto é um só e é da mesa, não da dupla: quem chegar primeiro leva, e o outro lado
     * fica sem. Em duplas não há nenhum, e aí a resposta é sempre não.
     */
    private fun temMortoParaPegar(state: CanastraState, team: Int): Boolean =
        state.mortos.isNotEmpty() && !state.tookMorto.getOrElse(team) { false }

    /**
     * A dupla pode ficar sem cartas agora?
     *
     * Duas maneiras, e são bem diferentes: com morto na mesa, zerar a mão é pegá-lo e
     * continuar jogando — não custa nada e não exige nada. Sem morto, zerar é **bater**, e
     * bater exige canastra.
     *
     * A regra só ficou visível quando a mesa de duplas perdeu o morto. Antes, o morto sempre
     * aparecia primeiro e escondia a exigência atrás dele.
     */
    private fun podeZerar(state: CanastraState, team: Int, teraCanastra: Boolean): Boolean =
        temMortoParaPegar(state, team) || teraCanastra

    /** A dupla terá canastra depois deste lance de baixar? O próprio lance pode fechar a sétima carta. */
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

    /** O mesmo, para a troca do curinga: ela sempre cresce o jogo em exatamente uma carta. */
    private fun teraCanastraSwap(state: CanastraState, move: CanastraMove.SwapWild): Boolean {
        val time = state.teamOf(state.turn)
        if (state.hasCanastra(time)) return true
        val tamanho = (state.meldsOf(state.turn).getOrNull(move.into)?.cards?.size ?: 0) + 1
        return tamanho >= CANASTRA_SIZE
    }

    /**
     * Este lance deixaria a mão num beco sem saída?
     *
     * Baixar até sobrar uma carta é o mesmo que baixar tudo: a vez ainda tem de terminar com
     * um descarte, e esse descarte zeraria a mão. Então o corte é em duas cartas — quem não
     * pode zerar precisa guardar uma para descartar e outra para ficar. [cartasRemovidas] é
     * quantas cartas o lance tira da mão — três ou mais para baixar, uma para trocar curinga.
     *
     * [handSize] chega como parâmetro, e não vem de `state.hand(state.turn).size`, porque
     * [canPlayCard] usa esta mesma função para avaliar uma mão **hipotética** — a que se
     * teria depois de pegar o lixo inteiro, antes de ter pegado de verdade.
     */
    private fun encurrala(state: CanastraState, handSize: Int, cartasRemovidas: Int, teraCanastra: Boolean): Boolean {
        val restante = handSize - cartasRemovidas
        if (restante >= 2) return false
        return !podeZerar(state, state.teamOf(state.turn), teraCanastra)
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

    /** As cartas escolhidas formam, agora, um jogo novo válido para a vez de quem joga? */
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
                if (state.phase != CanastraPhase.DRAW) {
                    return MoveResult.Illegal(ReasonKey.CARD_ALREADY_DREW)
                }
                if (state.stock.isEmpty()) return MoveResult.Illegal(ReasonKey.CARD_NOTHING_TO_DRAW)
            }

            CanastraMove.TakeDiscard -> {
                if (state.phase != CanastraPhase.DRAW) {
                    return MoveResult.Illegal(ReasonKey.CARD_ALREADY_DREW)
                }
                if (state.discard.isEmpty()) return MoveResult.Illegal(ReasonKey.CARD_NOTHING_TO_DRAW)
                if (state.discardBlocked) return MoveResult.Illegal(ReasonKey.CANASTRA_PILE_BLOCKED)
                if (!canTakeDiscard(state)) return MoveResult.Illegal(ReasonKey.CANASTRA_PILE_NEEDS_MELD)
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
                    return MoveResult.Illegal(ReasonKey.CANASTRA_BLACK_THREE_NEVER_MELDS)
                }
                
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
                
                // Impede jogadas burras de 150 pontos a força
                if (!isOpeningPathPreserved(state, mao, move)) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_OPENING_MELD_TOO_LOW)
                }
                
                if (encurrala(state, mao.size, move.cards.size, teraCanastra(state, move))) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_NEEDS_CANASTRA_TO_GO_OUT)
                }
            }

            is CanastraMove.SwapWild -> {
                if (state.phase != CanastraPhase.PLAY) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_MUST_DRAW_FIRST)
                }
                if (move.card !in mao) return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                val jogo = state.meldsOf(state.turn).getOrNull(move.into)
                    ?: return MoveResult.Illegal(ReasonKey.CANASTRA_NO_SUCH_MELD)
                val esperada = wildRepresents(jogo)
                    ?: return MoveResult.Illegal(ReasonKey.CANASTRA_NO_WILD_TO_SWAP)
                if (move.card != esperada) return MoveResult.Illegal(ReasonKey.CANASTRA_DOES_NOT_FIT)
                
                // Impede trocas de curinga que prejudiquem a chegada nos 150 pontos
                if (!isOpeningPathPreserved(state, mao, move)) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_OPENING_MELD_TOO_LOW)
                }
                
                if (encurrala(state, mao.size, 1, teraCanastraSwap(state, move))) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_NEEDS_CANASTRA_TO_GO_OUT)
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
                // Se a abertura está incompleta, é absolutamente proibido descartar. Sem exceções.
                if (openingIncomplete(state, state.teamOf(state.turn))) {
                    return MoveResult.Illegal(ReasonKey.CANASTRA_OPENING_MELD_INCOMPLETE)
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
            is CanastraMove.SwapWild -> applySwapWild(state, move)
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
            openingProgress = zerarProgresso(state, time),
        )
    }

    /** O que já se baixou nesta vez não vale para a próxima: zera ao começar a vez de [team]. */
    private fun zerarProgresso(state: CanastraState, team: Int): List<Int> =
        state.openingProgress.toMutableList().also {
            while (it.size <= team) it.add(0)
            it[team] = 0
        }.toList()

    /**
     * Pega o lixo inteiro. Três vermelho que estiver ali também vai para a mesa.
     *
     * A carta que estava no topo vira [CanastraState.owedCard]: [canTakeDiscard] já provou,
     * antes de este lance ser aceito, que existe algum jogo para ela — e é esse jogo, e só
     * ele, que fica disponível a seguir.
     */
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
        )
    }

    private fun applyMeld(state: CanastraState, move: CanastraMove.Meld): CanastraState {
        val mao = state.hand(state.turn).toMutableList()
        for (carta in move.cards) mao.remove(carta)

        val time = state.teamOf(state.turn)
        val jogos = state.melds[time].toMutableList()
        if (move.into != null) {
            var atual = jogos[move.into]
            // Já validado por applyMove: cada carta encaixa numa ponta, uma de cada vez —
            // e é por isso que dá para crescer as duas pontas no mesmo lance.
            for (carta in move.cards) atual = extendMeld(atual, carta)!!
            jogos[move.into] = atual
        } else {
            // Já validado por applyMove: o arranjo canônico existe.
            jogos += asMeld(move.cards)!!
        }

        val mesa = state.melds.toMutableList()
        mesa[time] = jogos.toList()

        // O que este lance soma ao mínimo de abertura da vez — ver [updateOpening].
        val abertura = updateOpening(state, time, move.cards.sumOf { cardValue(it) })

        // A carta devida (regra do lixo que obriga a baixar) só se quita se estiver entre as
        // que este lance baixou.
        val devida = if (state.owedCard != null && state.owedCard in move.cards) null else state.owedCard

        // Baixar pode esvaziar a mão, e aí pega-se o morto — mas a vez **continua**: ainda
        // falta descartar, agora com as cartas novas.
        return settle(
            semMao(
                state.copy(
                    hands = trocarMao(state, mao),
                    melds = mesa.toList(),
                    firstMeldDone = abertura.firstMeldDone,
                    openingProgress = abertura.openingProgress,
                    owedCard = devida,
                    ply = state.ply + 1,
                ),
            ),
        )
    }

    /** O que [updateOpening] devolve: os dois campos que o mínimo de abertura mantém. */
    private data class Abertura(val firstMeldDone: List<Boolean>, val openingProgress: List<Int>)

    /**
     * Soma [pontos] ao que a dupla já baixou nesta vez, e fecha o primeiro jogo da mão se isso
     * bater o mínimo — ou na hora, para quem ainda não passou de [CANASTRA_OPENING_THRESHOLD].
     *
     * O mínimo pode vir de mais de um jogo: uma trinca de 80 e outra de 90, baixadas na mesma
     * vez, somam 170 e abrem do mesmo jeito que uma sequência de 150 sozinha — é por isso que
     * cada lance de baixar (ou de trocar curinga) soma aqui, em vez de cada um checar sozinho
     * se bate o mínimo. Depois que a dupla já tem o primeiro jogo feito, não há mais nada para
     * somar: o retorno é o estado como já estava.
     */
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

    /**
     * Encaixa a carta natural no lugar do curinga.
     *
     * O curinga nunca volta para a mão: ele sai do lugar antigo e desce para depois da carta
     * mais alta do jogo — o mesmo fim de fila de sempre —, e o jogo cresce de N para N+1
     * cartas. Quando o jogo já ocupa a escala inteira, do quatro ao ás, essa posição não
     * representa carta nenhuma de verdade ([wildRepresents] devolve `null` para ela) — o
     * curinga fica só encostado ali, contando para o tamanho do jogo, sem poder ser trocado
     * de novo.
     */
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
        val abertura = updateOpening(state, time, cardValue(move.card))

        return settle(
            semMao(
                state.copy(
                    hands = trocarMao(state, mao),
                    melds = mesa.toList(),
                    firstMeldDone = abertura.firstMeldDone,
                    openingProgress = abertura.openingProgress,
                    owedCard = devida,
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
        
        // Passa o turno no sentido anti-horário
        val proximoTurno = Seat((state.turn.index + state.seats - 1) % state.seats)
        return settle(
            depois.copy(turn = proximoTurno, phase = CanastraPhase.DRAW),
        )
    }

    /**
     * Ficou sem cartas: pega o morto, ou bate. Os dois casos valem cinquenta pontos de
     * bônus, e é por isso que a mesma mão pode marcá-lo duas vezes.
     *
     * Acabar as cartas não termina a mão enquanto houver morto: quem chega primeiro pega as
     * treze cartas dele e continua jogando. Só que o morto é um e só existe até três
     * pessoas — em duplas não há nenhum, e aí acabar as cartas é bater na hora.
     *
     * Quem cuida de não deixar alguém bater sem canastra é [encurrala], antes do lance. Aqui
     * o trabalho é só o de dizer que a mão fechou.
     */
    private fun semMao(state: CanastraState): CanastraState {
        val cadeira = state.turn
        if (state.hand(cadeira).isNotEmpty()) return state

        val time = state.teamOf(cadeira)
        val batidas = state.batidas.toMutableList()
        batidas[time] = batidas[time] + 1

        if (temMortoParaPegar(state, time)) {
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
                batidas = batidas.toList(),
            )
        }

        // Sem cartas e sem morto para pegar, a mão acabou para todo mundo. Ter canastra
        // muda o quanto se ganha — o três vermelho só conta a favor com ela —, e quem cuida
        // disso é a contagem.
        return state.copy(wentOut = time, batidas = batidas.toList())
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
            (state.discard.isEmpty() || state.discardBlocked || !canTakeDiscard(state))
        if (state.wentOut < 0 && !travou) return state

        val ganhos = scoreHand(state)
        val somados = List(state.teams) { state.scores.getOrElse(it) { 0 } + ganhos[it] }
        if (somados.any { it >= CANASTRA_TARGET }) return state.copy(scores = somados)

        // Rotaciona o jogador que começa a próxima mão no sentido anti-horário
        val proximoComecar = Seat((state.startingSeat.index + state.seats - 1) % state.seats)
        return dealHand(state.seats, somados, state.rng, startingSeat = proximoComecar).copy(ply = state.ply)
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
     * A conta é da **dupla**: o que está na mesa soma (com o prêmio de cada canastra e de
     * cada carta além da sétima), o que ficou na mão subtrai, o três vermelho entra só se a
     * dupla tem canastra, e cada vez que a dupla ficou sem cartas soma cinquenta — seja
     * pegando o morto, seja batendo de vez.
     */
    fun scoreHand(state: CanastraState): List<Int> = List(state.teams) { time ->
        val naMesa = state.melds.getOrElse(time) { emptyList() }.sumOf { it.score }
        val naMao = (0 until state.seats)
            .filter { state.teamOf(Seat(it)) == time }
            .sumOf { state.hand(Seat(it)).sumOf { carta -> cardValue(carta) } }

        val quantos = state.redThrees.getOrElse(time) { 0 }
        val vermelhos = if (state.hasCanastra(time)) quantos * RED_THREE_VALUE else 0

        val bateu = CANASTRA_GOING_OUT_BONUS * state.batidas.getOrElse(time) { 0 }

        naMesa - naMao + vermelhos + bateu
    }

    override fun outcome(state: CanastraState): Outcome {
        if (state.scores.none { it >= CANASTRA_TARGET }) return Outcome.InProgress
        val maior = state.scores.max()
        val campeao = state.scores.indexOfFirst { it == maior }
        // A cadeira que representa a dupla: em duplas, a primeira das duas.
        return Outcome.Win(Seat(campeao))
    }

    /** Baixar carta ou trocar o curinga são os lances que mudam a mesa; a tela dá destaque a eles. */
    override fun isCapture(state: CanastraState, move: CanastraMove): Boolean =
        move is CanastraMove.Meld || move is CanastraMove.SwapWild

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
