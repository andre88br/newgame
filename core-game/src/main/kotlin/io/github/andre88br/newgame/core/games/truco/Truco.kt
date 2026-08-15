package io.github.andre88br.newgame.core.games.truco

import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
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

/** Três cartas para cada um, e a mão inteira cabe em três rodadas. */
const val TRUCO_HAND_SIZE: Int = 3

/** A partida vai a doze. */
const val TRUCO_TARGET: Int = 12

/** Rodada empatada, ou mão que não foi de ninguém. */
const val TRUCO_NOBODY: Int = -1

/**
 * O baralho do truco: quarenta cartas.
 *
 * Saem o oito, o nove e o dez. Não é economia de carta — é o que faz a ordem do truco caber
 * em dez degraus e sobrar espaço para as manilhas por cima de todos eles.
 */
val TRUCO_RANKS: List<Rank> = listOf(
    Rank.FOUR,
    Rank.FIVE,
    Rank.SIX,
    Rank.SEVEN,
    Rank.QUEEN,
    Rank.JACK,
    Rank.KING,
    Rank.ACE,
    Rank.TWO,
    Rank.THREE,
)

fun trucoDeck(): List<Card> = standardDeck().filter { it.rank in TRUCO_RANKS }

/**
 * As quatro manilhas do truco mineiro, da mais forte para a mais fraca.
 *
 * São **fixas**, e é isso que separa o mineiro do paulista: não há carta virada decidindo
 * quem manda na mão. Quem senta à mesa já sabe, antes de olhar as cartas, que o zap é o
 * quatro de paus — e o jogo inteiro se organiza em torno dessas quatro certezas.
 */
val ZAP: Card = Card(Rank.FOUR, Suit.CLUBS)
val COPAS: Card = Card(Rank.SEVEN, Suit.HEARTS)
val ESPADILHA: Card = Card(Rank.ACE, Suit.SPADES)
val OURITO: Card = Card(Rank.SEVEN, Suit.DIAMONDS)

/** Na ordem de força, do zap para baixo. */
val MANILHAS: List<Card> = listOf(ZAP, COPAS, ESPADILHA, OURITO)

fun isManilha(card: Card): Boolean = card in MANILHAS

/**
 * Quanto a carta vale numa disputa.
 *
 * Duas escalas coladas: as dez cartas comuns, do quatro ao três, e as quatro manilhas por
 * cima de todas elas. Naipe não conta em lugar nenhum a não ser dentro das manilhas — e lá
 * ele conta porque a manilha **é** uma carta específica, não um valor.
 */
fun trucoStrength(card: Card): Int {
    val manilha = MANILHAS.indexOf(card)
    if (manilha >= 0) return TRUCO_RANKS.size + (MANILHAS.size - manilha)
    return TRUCO_RANKS.indexOf(card.rank) + 1
}

/**
 * O próximo degrau da aposta, ou `null` quando já se está no teto.
 *
 * Um, três, seis, nove, doze. O primeiro salto é o maior de todos — trucar triplica a mão —
 * e os seguintes vão de três em três até a partida inteira caber num lance só.
 */
fun nextStake(stake: Int): Int? = when (stake) {
    1 -> 3
    3 -> 6
    6 -> 9
    9 -> 12
    else -> null
}

/** Uma carta na mesa, e de quem ela é. */
@Serializable
data class OnTable(val seat: Int, val card: Card)

@Serializable
data class TrucoState(
    /** A mão de cada cadeira. Some para quem não é dono — veja [TrucoGame.redactFor]. */
    val hands: List<List<Card>> = emptyList(),
    /** As cartas da rodada corrente, na ordem em que caíram. */
    val table: List<OnTable> = emptyList(),
    /** Quem levou cada rodada já fechada: o time, ou [TRUCO_NOBODY] se empatou. */
    val rounds: List<Int> = emptyList(),
    /** Pontos da partida, por time. */
    val scores: List<Int> = listOf(0, 0),
    /** Quanto a mão vale agora, já combinado: 1, 3, 6, 9 ou 12. */
    val stake: Int = 1,
    /** O valor proposto e ainda sem resposta, ou zero quando não há truco na mesa. */
    val pending: Int = 0,
    /** O time que propôs o valor pendente, ou [TRUCO_NOBODY]. */
    val bettor: Int = TRUCO_NOBODY,
    /** O time do último truco aceito. Ele não pode aumentar de novo; a vez é do outro lado. */
    val lastRaiser: Int = TRUCO_NOBODY,
    /** Quem abriu a rodada corrente. */
    val leader: Seat = Seat.FIRST,
    /** Quem volta a jogar carta depois de o truco ser respondido. */
    val resume: Seat = Seat.FIRST,
    /** Quem deu as cartas desta mão. Roda a cada mão. */
    val dealer: Seat = Seat.FIRST,
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    /** Duas cadeiras ou quatro. Três não divide em duplas. */
    val seats: Int = 2,
    val rng: Rng = Rng(0),
) : GameState {

    /** Em quatro as duplas são as cadeiras opostas; a dois, cada um é o seu time. */
    fun teamOf(seat: Seat): Int = seat.index % 2

    fun hand(seat: Seat): List<Card> = hands.getOrElse(seat.index) { emptyList() }

    fun handSize(seat: Seat): Int = hand(seat).size

    fun score(team: Int): Int = scores.getOrElse(team) { 0 }

    /** Há truco na mesa esperando resposta? */
    val answering: Boolean get() = pending > 0

    /** Quantas rodadas cada time levou, sem contar as empatadas. */
    fun roundsWon(team: Int): Int = rounds.count { it == team }

    override fun toString(): String = buildString {
        append("vez=${turn.index} valendo=$stake")
        if (pending > 0) append(" truco=$pending(time $bettor)")
        append(" placar=$scores rodadas=$rounds\n")
        append("mesa: ${table.joinToString(" ") { "${it.seat}:${it.card}" }}\n")
        hands.forEachIndexed { index, mao -> append("mão $index: ${mao.joinToString(" ")}\n") }
    }
}

/** O que se pode fazer na vez. */
@Serializable
sealed interface TrucoMove : Move {

    /** Jogar uma carta. */
    @Serializable
    data class Play(val card: Card) : TrucoMove {
        override fun describe(): String = card.toString()
    }

    /**
     * Trucar, ou aumentar o truco que está na mesa.
     *
     * Um lance só para as duas coisas porque são a mesma coisa: pedir o degrau seguinte da
     * escada. Quem pede de mão limpa está trucando; quem pede em resposta a um truco está
     * aumentando, e nesse caso o valor anterior já fica valendo — é por isso que aumentar
     * também é uma forma de aceitar.
     */
    @Serializable
    data object Call : TrucoMove {
        override fun describe(): String = "truco"
    }

    /** Aceitar o truco. */
    @Serializable
    data object Accept : TrucoMove {
        override fun describe(): String = "aceito"
    }

    /** Correr: o outro lado leva o que a mão valia antes do truco. */
    @Serializable
    data object Run : TrucoMove {
        override fun describe(): String = "corri"
    }
}

/**
 * Truco mineiro, de dois ou de quatro (em duplas).
 *
 * Quarenta cartas, três para cada um, e a mão se decide em melhor de três rodadas. As
 * **manilhas são fixas** — zap (4♣), copas (7♥), espadilha (A♠) e ourito (7♦), nessa ordem —
 * e é essa a diferença que separa o mineiro do paulista, onde a carta virada sorteia a
 * manilha a cada mão.
 *
 * O que faz o truco não ser um jogo de cartas comum é que **a mão vale o que os dois lados
 * combinarem que ela vale**. Ela nasce valendo um; qualquer um pode trucar e propor três, e a
 * resposta é aceitar, correr ou pedir mais — seis, nove, doze. Quem corre entrega ao
 * adversário o valor de antes do pedido, e é aí que está o jogo: correr de um truco custa um
 * ponto, e aguentar até o fim com a mão fraca pode custar doze.
 *
 * **Os empates** são a outra regra que não existe em outro lugar. Rodada empatada não se
 * repete: ela fica empatada, e o desempate vem de quem fez a primeira. Quem faz a primeira e
 * empata a segunda ganha a mão; quem empata a primeira precisa fazer a segunda; e mão com as
 * três rodadas empatadas não é de ninguém.
 *
 * Não estão aqui, por enquanto: a mão de onze e a carta virada para baixo.
 */
object TrucoGame : BoardGame<TrucoState, TrucoMove> {

    override val id: GameId = GameId.TRUCO

    override val supportedSeats: IntRange = 2..4

    /** Dois ou quatro. Três não divide em duplas, e truco sem dupla certa não é truco. */
    override val seatOptions: List<Int> = listOf(2, 4)

    override fun seatsIn(state: TrucoState): Int = state.seats

    override val hasHiddenInformation: Boolean = true

    override fun initialState(config: MatchConfig): TrucoState {
        // Recusar em vez de arredondar. Uma mesa de três aqui só chega por engano de quem
        // chamou, e transformá-la em dois calado montaria uma partida que ninguém pediu.
        require(config.seats in seatOptions) {
            "Truco é de dois ou de quatro: veio ${config.seats}"
        }
        val seats = config.seats
        // O dealer é a última cadeira para que a primeira mão comece na cadeira zero, que é
        // por onde toda partida deste app começa.
        return dealHand(seats, listOf(0, 0), Seat(seats - 1), config.rng())
    }

    /** Reparte uma mão: três cartas para cada um, e quem começa é quem está depois de quem deu. */
    private fun dealHand(seats: Int, scores: List<Int>, dealer: Seat, rng: Rng): TrucoState {
        val embaralhado = rng.shuffle(trucoDeck())
        val cartas = embaralhado.value
        val maos = List(seats) { index ->
            cartas.subList(index * TRUCO_HAND_SIZE, (index + 1) * TRUCO_HAND_SIZE).toList()
        }
        val mao = dealer.next(seats)
        return TrucoState(
            hands = maos,
            table = emptyList(),
            rounds = emptyList(),
            scores = scores,
            stake = 1,
            pending = 0,
            bettor = TRUCO_NOBODY,
            lastRaiser = TRUCO_NOBODY,
            leader = mao,
            resume = mao,
            dealer = dealer,
            turn = mao,
            seats = seats,
            rng = embaralhado.rng,
        )
    }

    override fun legalMoves(state: TrucoState): List<TrucoMove> {
        if (outcome(state).isOver) return emptyList()

        if (state.answering) {
            val saida = mutableListOf<TrucoMove>(TrucoMove.Accept)
            if (nextStake(state.pending) != null) saida += TrucoMove.Call
            saida += TrucoMove.Run
            return saida
        }

        val mao = state.hand(state.turn)
        // Mão redigida: quem olha de fora não sabe que cartas são, e não pode escolher por lá.
        if (mao.any { it.isHidden }) return emptyList()

        val saida = mao.map<Card, TrucoMove> { TrucoMove.Play(it) }.toMutableList()
        if (podeTrucar(state)) saida += TrucoMove.Call
        return saida
    }

    /**
     * Este lado pode trucar agora?
     *
     * Duas condições: a mão não pode já valer doze, e o último truco aceito não pode ter sido
     * deste mesmo lado. A segunda é a que impede alguém de subir a escada sozinho: quem trucou
     * e foi aceito passou a palavra, e só volta a pedir depois de o outro lado pedir.
     */
    private fun podeTrucar(state: TrucoState): Boolean =
        nextStake(state.stake) != null && state.lastRaiser != state.teamOf(state.turn)

    override fun applyMove(state: TrucoState, move: TrucoMove): MoveResult<TrucoState> {
        if (outcome(state).isOver) return MoveResult.Illegal(ReasonKey.GAME_OVER)

        when (move) {
            is TrucoMove.Play -> {
                if (state.answering) return MoveResult.Illegal(ReasonKey.TRUCO_ANSWER_FIRST)
                if (move.card !in state.hand(state.turn)) {
                    return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                }
            }

            TrucoMove.Call -> {
                if (state.answering) {
                    if (nextStake(state.pending) == null) {
                        return MoveResult.Illegal(ReasonKey.TRUCO_AT_THE_TOP)
                    }
                } else {
                    if (nextStake(state.stake) == null) {
                        return MoveResult.Illegal(ReasonKey.TRUCO_AT_THE_TOP)
                    }
                    if (state.lastRaiser == state.teamOf(state.turn)) {
                        return MoveResult.Illegal(ReasonKey.TRUCO_NOT_YOUR_CALL)
                    }
                }
            }

            TrucoMove.Accept, TrucoMove.Run -> {
                if (!state.answering) return MoveResult.Illegal(ReasonKey.TRUCO_NOTHING_TO_ANSWER)
            }
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    override fun applyKnownLegal(state: TrucoState, move: TrucoMove): TrucoState = when (move) {
        is TrucoMove.Play -> jogarCarta(state, move.card)
        TrucoMove.Call -> trucar(state)
        TrucoMove.Accept -> aceitar(state)
        TrucoMove.Run -> correr(state)
    }

    // -------- as cartas --------

    private fun jogarCarta(state: TrucoState, card: Card): TrucoState {
        val mao = state.hand(state.turn).toMutableList()
        mao.remove(card)
        val depois = state.copy(
            hands = trocarMao(state, mao),
            table = state.table + OnTable(state.turn.index, card),
            ply = state.ply + 1,
        )

        if (depois.table.size < depois.seats) {
            return depois.copy(turn = depois.turn.next(depois.seats))
        }
        return fecharRodada(depois)
    }

    /**
     * A rodada fechou: quem levou, e o que isso decide.
     *
     * Levar a rodada é ter a carta mais forte da mesa. Quando a carta mais forte aparece nos
     * dois lados — dois setes de ouros não existem, mas dois reis existem —, a rodada empata,
     * e o empate no truco não se repete: ele fica e vale como resultado.
     */
    private fun fecharRodada(state: TrucoState): TrucoState {
        val maior = state.table.maxOf { trucoStrength(it.card) }
        val melhores = state.table.filter { trucoStrength(it.card) == maior }
        val times = melhores.map { state.teamOf(Seat(it.seat)) }.distinct()

        val vencedor = if (times.size == 1) times.first() else TRUCO_NOBODY
        val rodadas = state.rounds + vencedor

        val daMao = handWinner(rodadas)
        if (daMao != null) return encerrarMao(state.copy(rounds = rodadas), daMao)

        // Abre a rodada seguinte quem levou esta. Empatou, abre quem já tinha aberto: não
        // houve ninguém para tomar a mão de quem a tinha.
        val proximo = if (vencedor == TRUCO_NOBODY) state.leader else Seat(melhores.first().seat)
        return state.copy(
            table = emptyList(),
            rounds = rodadas,
            leader = proximo,
            turn = proximo,
        )
    }

    /**
     * De quem é a mão, à luz das rodadas já jogadas.
     *
     * `null` enquanto ainda há o que jogar, [TRUCO_NOBODY] quando as três empataram, e o
     * time em qualquer outro caso. Os empates são o que dá trabalho aqui, e a regra que os
     * governa é uma só, dita de três jeitos: **manda quem fez a primeira**.
     */
    fun handWinner(rounds: List<Int>): Int? {
        val primeira = rounds.getOrNull(0) ?: return null

        if (rounds.size >= 2) {
            val segunda = rounds[1]
            // Duas rodadas para o mesmo lado fecham a mão sem precisar da terceira.
            if (primeira >= 0 && primeira == segunda) return primeira
            // Fez a primeira e empatou a segunda: ganha quem fez a primeira.
            if (primeira >= 0 && segunda == TRUCO_NOBODY) return primeira
            // Empatou a primeira: passa a mandar quem fizer a segunda.
            if (primeira == TRUCO_NOBODY && segunda >= 0) return segunda
        }

        if (rounds.size >= 3) {
            val terceira = rounds[2]
            if (terceira >= 0) return terceira
            // Empatou a terceira: volta a valer quem fez a primeira. Se nem a primeira teve
            // dono, as três empataram e a mão não é de ninguém.
            return if (primeira >= 0) primeira else TRUCO_NOBODY
        }
        return null
    }

    // -------- a aposta --------

    /**
     * Trucar, ou aumentar.
     *
     * Aumentar em resposta a um truco **aceita o valor anterior de passagem**: o seis só
     * existe porque o três já está de pé. É por isso que aqui o valor pendente vira o valor
     * combinado antes de o próximo degrau ser proposto — e é o que faz quem corre de um seis
     * pagar três, e não um.
     */
    private fun trucar(state: TrucoState): TrucoState {
        val time = state.teamOf(state.turn)
        val base = if (state.answering) state.pending else state.stake
        val degrau = nextStake(base) ?: error("Truco no teto não devia chegar aqui: $base")
        return state.copy(
            stake = base,
            pending = degrau,
            bettor = time,
            // Quem trucou de mão limpa ainda tem uma carta para jogar depois da resposta.
            resume = if (state.answering) state.resume else state.turn,
            turn = state.turn.next(state.seats),
            ply = state.ply + 1,
        )
    }

    private fun aceitar(state: TrucoState): TrucoState = state.copy(
        stake = state.pending,
        pending = 0,
        lastRaiser = state.bettor,
        bettor = TRUCO_NOBODY,
        turn = state.resume,
        ply = state.ply + 1,
    )

    /** Correr entrega ao outro lado o que a mão valia **antes** do pedido. */
    private fun correr(state: TrucoState): TrucoState =
        encerrarMao(state.copy(ply = state.ply + 1), 1 - state.teamOf(state.turn))

    // -------- o placar --------

    /** Soma a mão no placar e reparte a seguinte, se a partida continuar. */
    private fun encerrarMao(state: TrucoState, winner: Int): TrucoState {
        val somados = List(2) { time ->
            state.score(time) + if (time == winner) state.stake else 0
        }
        if (somados.any { it >= TRUCO_TARGET }) return state.copy(scores = somados)
        return dealHand(state.seats, somados, state.dealer.next(state.seats), state.rng)
            .copy(ply = state.ply)
    }

    override fun outcome(state: TrucoState): Outcome {
        if (state.scores.none { it >= TRUCO_TARGET }) return Outcome.InProgress
        val maior = state.scores.max()
        return Outcome.Win(Seat(state.scores.indexOfFirst { it == maior }))
    }

    /**
     * A carta que fecha a rodada a favor de quem a jogou.
     *
     * Não há captura no truco, mas há o instante equivalente: a carta que toma a rodada. É
     * o que a tela quer destacar, e quem sabe dizer isso é quem conhece a força das cartas.
     */
    override fun isCapture(state: TrucoState, move: TrucoMove): Boolean {
        if (move !is TrucoMove.Play) return false
        if (state.table.size != state.seats - 1) return false
        val maior = state.table.maxOfOrNull { trucoStrength(it.card) } ?: 0
        return trucoStrength(move.card) > maior
    }

    private fun trocarMao(state: TrucoState, mao: List<Card>): List<List<Card>> =
        state.hands.mapIndexed { index, atual -> if (index == state.turn.index) mao else atual }

    /**
     * A mão dos outros vira — inclusive a do parceiro.
     *
     * Em duplas a tentação é mostrar a mão de quem joga com você, e seria errado: metade do
     * truco de dupla é justamente não saber o que o parceiro tem e ter de deduzir pelo que
     * ele joga. A mesa é pública, o placar é público, as cartas de cada um são de cada um.
     */
    override fun redactFor(state: TrucoState, viewer: Seat): TrucoState = state.copy(
        hands = state.hands.mapIndexed { index, mao ->
            if (index == viewer.index) mao else mao.hidden()
        },
    )

    override val stateSerializer: KSerializer<TrucoState> = serializer()
    override val moveSerializer: KSerializer<TrucoMove> = serializer()
}
