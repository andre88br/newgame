package io.github.andre88br.newgame.core.games.hearts

import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.cards.deal
import io.github.andre88br.newgame.core.cards.hidden
import io.github.andre88br.newgame.core.cards.standardDeck
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
import io.github.andre88br.newgame.core.engine.next
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** Copas é sempre de quatro: as 52 cartas se dividem em treze para cada um. */
const val HEARTS_SEATS: Int = 4

/** Cartas na mão de cada um no começo da mão. */
const val HEARTS_HAND_SIZE: Int = 13

/** Cartas passadas antes de a mão começar. */
const val HEARTS_PASS_SIZE: Int = 3

/** Ponto em que a partida acaba: quem chegar aqui encerra, e ganha quem tiver menos. */
const val HEARTS_TARGET_SCORE: Int = 100

/** Pontos de correr todas: as treze copas mais a dama de espadas. */
const val HEARTS_MOON: Int = 26

/** A carta que abre a mão. Quem a tem sai, e sai com ela. */
val TWO_OF_CLUBS: Card = Card(Rank.TWO, Suit.CLUBS)

/** A carta cara do jogo: sozinha vale mais do que todas as copas juntas. */
val QUEEN_OF_SPADES: Card = Card(Rank.QUEEN, Suit.SPADES)

/** Quanto vale uma carta na contagem da mão. */
fun penaltyOf(card: Card): Int = when {
    card == QUEEN_OF_SPADES -> 13
    card.suit == Suit.HEARTS -> 1
    else -> 0
}

/**
 * Para onde as cartas do passe vão nesta mão.
 *
 * O rodízio é o que impede o passe de virar rotina: passar sempre para o mesmo vizinho
 * deixaria o jogo previsível, e a quarta mão sem passe existe para que a sorte da
 * distribuição apareça inteira de vez em quando.
 */
@Serializable
enum class PassDirection {
    LEFT,
    RIGHT,
    ACROSS,
    NONE,
    ;

    /** Quem recebe as cartas de [seat] numa mesa de [seats] cadeiras. */
    fun receiver(seat: Seat, seats: Int): Seat = when (this) {
        LEFT -> Seat((seat.index + 1) % seats)
        RIGHT -> Seat((seat.index + seats - 1) % seats)
        ACROSS -> Seat((seat.index + seats / 2) % seats)
        NONE -> seat
    }

    companion object {
        /** O rodízio se repete de quatro em quatro mãos. */
        fun forHand(hand: Int): PassDirection = entries[hand % entries.size]
    }
}

/** Em que ponto da mão o jogo está. */
@Serializable
enum class HeartsPhase {
    /** Cada um escolhe três cartas para passar. */
    PASSING,

    /** As vazas. */
    PLAYING,
}

/** Uma carta já jogada na vaza, com quem a jogou. */
@Serializable
data class PlayedCard(val seat: Seat, val card: Card)

@Serializable
data class HeartsState(
    /** A mão de cada cadeira. Some para quem não é dono — veja [HeartsGame.redactFor]. */
    val hands: List<List<Card>> = emptyList(),
    /** As cartas já escolhidas para passar, por cadeira. Também some para os outros. */
    val passing: List<List<Card>> = List(HEARTS_SEATS) { emptyList() },
    /** A vaza em andamento, na ordem em que foi jogada. */
    val trick: List<PlayedCard> = emptyList(),
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    val phase: HeartsPhase = HeartsPhase.PASSING,
    /** Qual mão da partida é esta, contando do zero. Decide o rodízio do passe. */
    val hand: Int = 0,
    /** Pontos acumulados na partida, por cadeira. */
    val scores: List<Int> = List(HEARTS_SEATS) { 0 },
    /** Pontos feitos nesta mão, que entram em [scores] quando ela acaba. */
    val handPoints: List<Int> = List(HEARTS_SEATS) { 0 },
    /** Copas já saiu: antes disso ninguém pode **puxar** copas. */
    val heartsBroken: Boolean = false,
    /** Gerador guardado no estado: é o que faz a partida salva repartir igual. */
    val rng: Rng = Rng(0),
) : GameState {

    val seats: Int get() = hands.size.coerceAtLeast(HEARTS_SEATS)

    fun hand(seat: Seat): List<Card> = hands.getOrElse(seat.index) { emptyList() }

    /** Quantas cartas a cadeira tem. Continua certo mesmo com a mão virada. */
    fun handSize(seat: Seat): Int = hand(seat).size

    fun score(seat: Seat): Int = scores.getOrElse(seat.index) { 0 }

    /** O naipe que a vaza pediu, ou `null` se ela ainda não começou. */
    val leadSuit: Suit? get() = trick.firstOrNull()?.card?.suit

    /** Quem puxou a vaza em andamento. */
    val leader: Seat? get() = trick.firstOrNull()?.seat

    /** A direção do passe desta mão. */
    val passDirection: PassDirection get() = PassDirection.forHand(hand)

    override fun toString(): String = buildString {
        append("mão $hand (${passDirection.name.lowercase()}) fase=$phase vez=${turn.index}\n")
        hands.forEachIndexed { index, cartas ->
            append("cadeira $index [${scores.getOrElse(index) { 0 }}]: ${cartas.joinToString(" ")}\n")
        }
        append("vaza: ${trick.joinToString(" ") { "${it.seat.index}:${it.card}" }}")
    }
}

/** Jogar [card]. No passe é escolher uma das três; nas vazas é a carta da vez. */
@Serializable
data class HeartsMove(val card: Card) : Move {
    override fun describe(): String = card.toString()
}

/**
 * Copas.
 *
 * Jogo de vaza em que ganhar é o que se evita: cada copas vale um ponto, a dama de espadas
 * vale treze, e vence quem tiver **menos** pontos quando alguém chegar a cem. Isso inverte
 * a intuição de todo jogo de carta — a carta alta, que em qualquer outro lugar é boa, aqui
 * é o problema.
 *
 * O que faz o jogo não ser só azar é o passe: antes de cada mão, três cartas mudam de dono,
 * e é aí que se decide se dá para tentar correr todas ou se o certo é se livrar do risco.
 *
 * Informação oculta: cada um vê a própria mão, e as dos outros aparecem viradas com a
 * contagem certa. A IA joga por amostragem de mundos possíveis, como no dominó — nunca
 * olhando a mão alheia.
 */
object HeartsGame : BoardGame<HeartsState, HeartsMove> {

    override val id: GameId = GameId.HEARTS

    /** Copas é de quatro, e só. */
    override val supportedSeats: IntRange = HEARTS_SEATS..HEARTS_SEATS

    override fun seatsIn(state: HeartsState): Int = HEARTS_SEATS

    override val hasHiddenInformation: Boolean = true

    /** Quem abre é quem tem o dois de paus, e isso só se sabe depois de repartir. */
    override val decidesWhoStarts: Boolean = true

    override fun initialState(config: MatchConfig): HeartsState =
        dealHand(hand = 0, scores = List(HEARTS_SEATS) { 0 }, rng = config.rng())

    /**
     * Reparte uma mão nova.
     *
     * Sem passe, a mão já começa nas vazas e quem sai é quem tem o dois de paus. Com passe,
     * começa na escolha das três cartas, e aí a ordem é a das cadeiras — não há por que
     * esperar ninguém.
     */
    private fun dealHand(hand: Int, scores: List<Int>, rng: Rng): HeartsState {
        val repartido = rng.deal(standardDeck(), hands = HEARTS_SEATS, size = HEARTS_HAND_SIZE)
        val maos = repartido.value.hands
        val semPasse = PassDirection.forHand(hand) == PassDirection.NONE

        return HeartsState(
            hands = maos,
            passing = List(HEARTS_SEATS) { emptyList() },
            trick = emptyList(),
            turn = if (semPasse) seatWithTwoOfClubs(maos) else Seat.FIRST,
            ply = 0,
            phase = if (semPasse) HeartsPhase.PLAYING else HeartsPhase.PASSING,
            hand = hand,
            scores = scores,
            handPoints = List(HEARTS_SEATS) { 0 },
            heartsBroken = false,
            rng = repartido.rng,
        )
    }

    private fun seatWithTwoOfClubs(hands: List<List<Card>>): Seat =
        Seat(hands.indexOfFirst { TWO_OF_CLUBS in it }.coerceAtLeast(0))

    override fun legalMoves(state: HeartsState): List<HeartsMove> {
        if (outcome(state).isOver) return emptyList()
        val mao = state.hand(state.turn)
        if (mao.any { it.isHidden }) return emptyList()

        return when (state.phase) {
            // No passe qualquer carta serve: o que se escolhe é do que se quer se livrar.
            HeartsPhase.PASSING -> mao.map { HeartsMove(it) }
            HeartsPhase.PLAYING -> playableCards(state, mao).map { HeartsMove(it) }
        }
    }

    /**
     * As cartas que a regra deixa jogar agora.
     *
     * São quatro restrições que se sobrepõem, e a ordem entre elas importa: seguir o naipe
     * vem antes de tudo, e as proibições da primeira vaza só valem enquanto sobrar carta
     * fora delas — quem só tem copas na mão joga copas, senão a mão travaria.
     */
    fun playableCards(state: HeartsState, mao: List<Card>): List<Card> {
        if (mao.isEmpty()) return emptyList()
        // As cartas da vaza em andamento já saíram das mãos, então o que falta nas mãos
        // conta tudo o que já foi jogado na mão — inclusive a vaza aberta.
        val jogadas = HEARTS_SEATS * HEARTS_HAND_SIZE - state.hands.sumOf { it.size }
        val primeiraVaza = jogadas < HEARTS_SEATS

        val pedido = state.leadSuit
        if (pedido != null) {
            val doNaipe = mao.filter { it.suit == pedido }
            if (doNaipe.isNotEmpty()) {
                // Segue-se o naipe. Na primeira vaza, nem assim se descarta ponto — a não
                // ser que só reste ponto do naipe pedido.
                if (!primeiraVaza) return doNaipe
                val semPonto = doNaipe.filter { penaltyOf(it) == 0 }
                return semPonto.ifEmpty { doNaipe }
            }
            // Sem o naipe pedido, entra qualquer carta — menos ponto na primeira vaza.
            if (!primeiraVaza) return mao
            val semPonto = mao.filter { penaltyOf(it) == 0 }
            return semPonto.ifEmpty { mao }
        }

        // Puxando a vaza. A primeira da mão é sempre com o dois de paus.
        if (TWO_OF_CLUBS in mao && state.trick.isEmpty() && primeiraVaza) {
            return listOf(TWO_OF_CLUBS)
        }
        // Copas travada: só se puxa copas depois de ela ter saído, ou se não houver outra.
        if (!state.heartsBroken) {
            val foraDeCopas = mao.filter { it.suit != Suit.HEARTS }
            if (foraDeCopas.isNotEmpty()) return foraDeCopas
        }
        return mao
    }

    override fun applyMove(state: HeartsState, move: HeartsMove): MoveResult<HeartsState> {
        if (outcome(state).isOver) return MoveResult.Illegal(ReasonKey.GAME_OVER)
        val mao = state.hand(state.turn)
        if (move.card !in mao) return MoveResult.Illegal(ReasonKey.HEARTS_NOT_IN_HAND)

        if (state.phase == HeartsPhase.PLAYING && move.card !in playableCards(state, mao)) {
            val pedido = state.leadSuit
            return when {
                pedido != null && mao.any { it.suit == pedido } ->
                    MoveResult.Illegal(ReasonKey.HEARTS_MUST_FOLLOW_SUIT)
                pedido == null && move.card.suit == Suit.HEARTS && !state.heartsBroken ->
                    MoveResult.Illegal(ReasonKey.HEARTS_NOT_BROKEN)
                pedido == null && TWO_OF_CLUBS in mao ->
                    MoveResult.Illegal(ReasonKey.HEARTS_MUST_LEAD_TWO)
                else -> MoveResult.Illegal(ReasonKey.HEARTS_NO_POINTS_FIRST_TRICK)
            }
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    override fun applyKnownLegal(state: HeartsState, move: HeartsMove): HeartsState =
        when (state.phase) {
            HeartsPhase.PASSING -> applyPass(state, move)
            HeartsPhase.PLAYING -> applyPlay(state, move)
        }

    /**
     * Escolhe mais uma carta para passar.
     *
     * A carta sai da mão na hora, e não no fim: assim ninguém escolhe duas vezes a mesma, e
     * a mão que a tela mostra já é a que vai sobrar. As cartas só trocam de dono quando as
     * quatro cadeiras terminaram de escolher, porque passar antes disso entregaria a
     * decisão de quem ainda não escolheu.
     */
    private fun applyPass(state: HeartsState, move: HeartsMove): HeartsState {
        val cadeira = state.turn
        val maos = state.hands.toMutableList()
        maos[cadeira.index] = maos[cadeira.index] - move.card

        val escolhidas = state.passing.toMutableList()
        escolhidas[cadeira.index] = escolhidas[cadeira.index] + move.card

        val parcial = state.copy(hands = maos, passing = escolhidas, ply = state.ply + 1)

        // Ainda faltam cartas para esta cadeira? Ela continua escolhendo.
        if (escolhidas[cadeira.index].size < HEARTS_PASS_SIZE) return parcial

        val proxima = cadeira.next(HEARTS_SEATS)
        if (escolhidas.any { it.size < HEARTS_PASS_SIZE }) {
            return parcial.copy(turn = firstSeatStillPassing(escolhidas, proxima))
        }

        // Todo mundo escolheu: as cartas trocam de dono e a mão começa.
        val direcao = state.passDirection
        val entregues = MutableList(HEARTS_SEATS) { maos[it].toMutableList() }
        for (index in 0 until HEARTS_SEATS) {
            val destino = direcao.receiver(Seat(index), HEARTS_SEATS)
            entregues[destino.index].addAll(escolhidas[index])
        }
        val finais = entregues.map { it.toList() }

        return parcial.copy(
            hands = finais,
            passing = List(HEARTS_SEATS) { emptyList() },
            phase = HeartsPhase.PLAYING,
            turn = seatWithTwoOfClubs(finais),
        )
    }

    private fun firstSeatStillPassing(escolhidas: List<List<Card>>, apartirDe: Seat): Seat {
        for (passo in 0 until HEARTS_SEATS) {
            val cadeira = Seat((apartirDe.index + passo) % HEARTS_SEATS)
            if (escolhidas[cadeira.index].size < HEARTS_PASS_SIZE) return cadeira
        }
        return apartirDe
    }

    /** Joga a carta na vaza e, se ela fechou, recolhe e conta. */
    private fun applyPlay(state: HeartsState, move: HeartsMove): HeartsState {
        val cadeira = state.turn
        val maos = state.hands.toMutableList()
        maos[cadeira.index] = maos[cadeira.index] - move.card

        val vaza = state.trick + PlayedCard(cadeira, move.card)
        val abriuCopas = state.heartsBroken || move.card.suit == Suit.HEARTS

        val comCarta = state.copy(
            hands = maos,
            trick = vaza,
            ply = state.ply + 1,
            heartsBroken = abriuCopas,
        )

        if (vaza.size < HEARTS_SEATS) {
            return comCarta.copy(turn = cadeira.next(HEARTS_SEATS))
        }

        // Vaza fechada: quem jogou a carta mais alta do naipe pedido leva, e leva os pontos.
        val pedido = vaza.first().card.suit
        val vencedor = vaza.filter { it.card.suit == pedido }.maxBy { it.card.rank.order }.seat
        val pontos = vaza.sumOf { penaltyOf(it.card) }

        val daMao = comCarta.handPoints.toMutableList()
        daMao[vencedor.index] = daMao[vencedor.index] + pontos

        val fechada = comCarta.copy(trick = emptyList(), turn = vencedor, handPoints = daMao)
        if (maos.any { it.isNotEmpty() }) return fechada

        return closeHand(fechada)
    }

    /**
     * Fecha a mão: soma os pontos na partida e reparte a seguinte, se ainda houver partida.
     *
     * **Correr todas.** Quem fizer todos os 26 pontos não leva nenhum: os outros três é que
     * levam 26 cada. É o que dá sentido a uma mão cheia de cartas altas — em vez de só
     * perder, dá para tentar ganhar tudo, e falhar por uma carta é o risco que faz a
     * tentativa valer alguma coisa.
     */
    private fun closeHand(state: HeartsState): HeartsState {
        val daMao = state.handPoints
        val correuTodas = daMao.indexOfFirst { it == HEARTS_MOON }

        val somados = if (correuTodas >= 0) {
            List(HEARTS_SEATS) { index ->
                state.scores[index] + if (index == correuTodas) 0 else HEARTS_MOON
            }
        } else {
            List(HEARTS_SEATS) { index -> state.scores[index] + daMao[index] }
        }

        val acabou = somados.any { it >= HEARTS_TARGET_SCORE }
        if (acabou) {
            return state.copy(scores = somados, handPoints = daMao, phase = HeartsPhase.PLAYING)
        }
        return dealHand(hand = state.hand + 1, scores = somados, rng = state.rng)
            .copy(ply = state.ply)
    }

    /**
     * A vaza vale ponto para quem a leva — e, em copas, levar ponto é ruim.
     *
     * A tela usa isto para dar destaque e som diferentes: recolher a dama de espadas não é
     * um lance qualquer, e merece a mesma atenção que uma captura em outro jogo.
     */
    override fun isCapture(state: HeartsState, move: HeartsMove): Boolean {
        if (state.phase != HeartsPhase.PLAYING) return false
        if (state.trick.size < HEARTS_SEATS - 1) return false
        val vaza = state.trick + PlayedCard(state.turn, move.card)
        return vaza.sumOf { penaltyOf(it.card) } > 0
    }

    override fun outcome(state: HeartsState): Outcome {
        if (state.scores.none { it >= HEARTS_TARGET_SCORE }) return Outcome.InProgress
        // Vence quem tem menos. Empate no topo é decidido pela cadeira mais baixa, que é a
        // convenção mais simples e não muda quem de fato jogou melhor.
        val menor = state.scores.min()
        return Outcome.Win(Seat(state.scores.indexOfFirst { it == menor }))
    }

    /**
     * A mão dos outros vira; a sua fica.
     *
     * Sem isto o estado inteiro chegaria à tela — e mesmo que nada o desenhasse, bastaria um
     * `toString` no lugar errado para entregar o jogo. A IA recebe o estado por este mesmo
     * caminho, e é o que a impede de ganhar por ver o que não devia.
     */
    override fun redactFor(state: HeartsState, viewer: Seat): HeartsState = state.copy(
        hands = state.hands.mapIndexed { index, mao ->
            if (index == viewer.index) mao else mao.hidden()
        },
        passing = state.passing.mapIndexed { index, cartas ->
            if (index == viewer.index) cartas else cartas.hidden()
        },
    )

    override val stateSerializer: KSerializer<HeartsState> = serializer()
    override val moveSerializer: KSerializer<HeartsMove> = serializer()
}
