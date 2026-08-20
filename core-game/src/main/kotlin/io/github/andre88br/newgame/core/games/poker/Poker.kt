package io.github.andre88br.newgame.core.games.poker

import io.github.andre88br.newgame.core.cards.Card
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** Fichas de compra padrão, quando a configuração não pede outro valor. */
const val POKER_DEFAULT_BUY_IN: Int = 1000

/** Big blind padrão. O small blind é sempre metade dele. */
const val POKER_DEFAULT_BIG_BLIND: Int = 20

/** Chaves de [MatchConfig.options] que este jogo entende. */
const val POKER_OPTION_BUY_IN: String = "poker.buyIn"
const val POKER_OPTION_BIG_BLIND: String = "poker.bigBlind"

/** As quatro rodadas de aposta de uma mão de Texas Hold'em. */
enum class PokerStreet { PREFLOP, FLOP, TURN, RIVER }

/** Quem levou um pote (principal ou lateral) do showdown, e quanto. */
@Serializable
data class PokerPotShare(val winners: List<Int>, val amount: Int)

/**
 * Quem levou a mão anterior, e quanto — para a tela mostrar depois que o motor já repartiu a
 * mão seguinte.
 *
 * O motor não para entre mãos: assim que uma fecha, a próxima já é repartida na mesma
 * resposta (o mesmo padrão do truco). Sem guardar isto em algum lugar, o resultado apareceria
 * e desapareceria no mesmo instante — a tela nunca teria a chance de mostrá-lo. Fica valendo
 * até a mão seguinte fechar e sobrescrever com o resultado dela.
 *
 * Normalmente [pots] tem um item só. Quando alguém foi all-in por menos do que os outros
 * apostaram depois, tem mais de um: um pote principal, que todo mundo ainda na mão disputa, e
 * um ou mais potes laterais, que só quem cobriu aquele valor disputa — veja a nota em
 * [PokerGame] sobre por que essa divisão existe.
 */
@Serializable
data class PokerHandResult(val pots: List<PokerPotShare>)

@Serializable
data class PokerState(
    /** As duas cartas de cada cadeira. Vazia para quem está fora do torneio ou fora desta mão. */
    val hands: List<List<Card>> = emptyList(),
    /** As cartas comunitárias já reveladas: três, quatro ou cinco, conforme a rodada. */
    val board: List<Card> = emptyList(),
    /** O que resta do baralho embaralhado, para as próximas cartas comunitárias. */
    val deck: List<Card> = emptyList(),
    /** Fichas de cada cadeira. Zero é eliminação: a cadeira não volta a ser servida. */
    val stacks: List<Int> = emptyList(),
    /** Quanto cada cadeira já colocou **nesta rodada de aposta**. Zera a cada rua nova. */
    val streetBet: List<Int> = emptyList(),
    /**
     * Quanto cada cadeira já colocou **nesta mão inteira**, somando todas as ruas. Nunca zera
     * durante a mão — é o que permite montar os potes laterais no showdown, comparando quanto
     * cada um efetivamente cobriu.
     */
    val contrib: List<Int> = emptyList(),
    /** Quem já desistiu nesta mão — ou nunca foi servido, por estar eliminado. */
    val folded: List<Boolean> = emptyList(),
    /** Quem ainda precisa agir nesta rodada antes dela poder fechar. */
    val toAct: List<Boolean> = emptyList(),
    /** Fichas já apostadas nesta mão, de todas as ruas somadas. */
    val pot: Int = 0,
    val street: PokerStreet = PokerStreet.PREFLOP,
    /** O aumento mínimo válido agora: o do último aumento desta rua, ou o big blind se nenhum houve. */
    val minRaise: Int = POKER_DEFAULT_BIG_BLIND,
    val button: Seat = Seat.FIRST,
    val smallBlind: Int = POKER_DEFAULT_BIG_BLIND / 2,
    val bigBlind: Int = POKER_DEFAULT_BIG_BLIND,
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    val seats: Int = 2,
    val rng: Rng = Rng(0),
    /**
     * O torneio já acabou — sobrou uma cadeira só com ficha.
     *
     * Não dá para derivar isto de "alguém está com [stacks] zerado agora": é assim que fica
     * durante uma mão, sempre que alguém vai all-in, mesmo que essa cadeira ainda possa
     * ganhar o showdown e voltar a ter ficha. O torneio só termina de verdade **entre mãos**,
     * quando [PokerGame] já repartiu o pote e decide se reparte outra mão ou para aqui — e é
     * só nesse momento que este campo vira `true`.
     */
    val gameOver: Boolean = false,
    /** O resultado da última mão fechada, ou `null` antes de a primeira mão terminar. */
    val lastResult: PokerHandResult? = null,
    /** Quantas mãos já foram repartidas nesta partida, contando do zero. Muda a cada mão nova. */
    val handNumber: Int = 0,
) : GameState {

    fun hand(seat: Seat): List<Card> = hands.getOrElse(seat.index) { emptyList() }

    fun stack(seat: Seat): Int = stacks.getOrElse(seat.index) { 0 }

    /** Ainda disputa esta mão — não desistiu e foi servido nela. */
    fun isIn(seat: Seat): Boolean = folded.getOrElse(seat.index) { true }.not()

    /** Continua no torneio: tem ficha, mesmo que já tenha desistido desta mão. */
    fun isAlive(seat: Seat): Boolean = stack(seat) > 0 || isIn(seat)

    /** Maior valor apostado nesta rua por qualquer cadeira, inclusive quem já desistiu. */
    val maxStreetBet: Int get() = streetBet.maxOrNull() ?: 0

    /**
     * A mão de [seat] já é pública de verdade: ela foi all-in e a rua em que isso aconteceu
     * já fechou. Veja [PokerGame.redactFor] para o porquê da segunda condição.
     *
     * Existe como função da própria regra do jogo, e não como "a carta está virada?", porque
     * [PokerAi] também precisa da mesma resposta — e uma cadeira ainda decidindo não vira
     * pública só porque, por acaso, quem chamou a IA esqueceu de redigir o estado primeiro.
     */
    fun allInRevealed(seat: Seat): Boolean = isIn(seat) && stack(seat) == 0 && maxStreetBet == 0

    /** Quanto falta a [seat] para igualar a aposta da rua. */
    fun toCall(seat: Seat): Int = maxStreetBet - streetBet.getOrElse(seat.index) { 0 }

    /** Alguém nesta mão já apostou tudo o que tinha. Trava novos aumentos — veja a nota em [PokerGame]. */
    val anyAllIn: Boolean get() = folded.indices.any { !folded[it] && stacks.getOrElse(it) { 0 } == 0 }

    override fun toString(): String = buildString {
        append("rua=$street pote=$pot vez=${turn.index}\n")
        append("mesa: ${board.joinToString(" ")}\n")
        hands.forEachIndexed { index, mao ->
            append("cadeira $index: ${mao.joinToString(" ")} fichas=${stack(Seat(index))}")
            if (!isIn(Seat(index))) append(" (fora)")
            append("\n")
        }
    }
}

@Serializable
sealed interface PokerMove : Move {

    @Serializable
    data object Fold : PokerMove {
        override fun describe(): String = "desisto"
    }

    @Serializable
    data object Check : PokerMove {
        override fun describe(): String = "passo"
    }

    @Serializable
    data object Call : PokerMove {
        override fun describe(): String = "pago"
    }

    /**
     * Aumenta a aposta da rua até [to] fichas — o total que a cadeira terá posto nesta rua,
     * não só o quanto está acrescentando agora. `to` igual às fichas que a cadeira tem mais o
     * que já apostou é ir all-in.
     */
    @Serializable
    data class Raise(val to: Int) : PokerMove {
        override fun describe(): String = "aumento para $to"
    }

    /**
     * Revela a próxima carta da mesa sozinha, sem decisão de ninguém.
     *
     * Existe para o all-in: quando ninguém mais tem lance a fazer (todo mundo que segue na
     * mão está all-in, ou só falta um com ficha), a mesa continua sendo revelada uma carta
     * de cada vez — como numa mesa de verdade — em vez de pular direto para o showdown. Quem
     * dirige esses lances é a camada de aplicação (veja [BoardGame.forcedMove]), no mesmo
     * compasso que ela já usa para as jogadas da IA.
     */
    @Serializable
    data object AdvanceStreet : PokerMove {
        override fun describe(): String = "revela carta"
    }
}

/**
 * Texas Hold'em, torneio freezeout com fichas de um bolso só: quem zera está fora, e a
 * partida termina quando resta uma cadeira com ficha.
 *
 * **Com side pot.** Assim que qualquer cadeira desta mão fica all-in, ninguém mais pode
 * aumentar — só pagar ou desistir (veja [PokerState.anyAllIn]). Isso não impede uma cadeira
 * de ficar all-in por menos do que a aposta corrente, se não tiver ficha para pagar inteiro; e
 * quem cobre mais do que um all-in curto pode acabar apostando, na mesma rua, mais do que
 * aquela cadeira curta colocou. Por isso o pote se divide em camadas no showdown (veja
 * [PokerState.contrib] e a divisão em [PokerGame]): quem foi all-in por menos só disputa até
 * onde apostou — o pote principal, que todos ainda na mão disputam — e o que os outros
 * apostaram a mais forma potes laterais, disputados só por quem cobriu aquele valor. Uma
 * cadeira curta nunca ganha mais do que o dobro do que tinha quando foi all-in contra um só
 * adversário: o excedente que ela não cobriu volta para quem apostou mais.
 *
 * Blinds fixos (não sobem com o tempo, como num torneio de verdade) — o buy-in e o big blind
 * vêm de [MatchConfig.options] (`poker.buyIn`, `poker.bigBlind`), com [POKER_DEFAULT_BUY_IN] e
 * [POKER_DEFAULT_BIG_BLIND] quando ausentes.
 */
object PokerGame : BoardGame<PokerState, PokerMove> {

    override val id: GameId = GameId.POKER

    override val supportedSeats: IntRange = 2..4

    override fun seatsIn(state: PokerState): Int = state.seats

    override val hasHiddenInformation: Boolean = true

    override fun initialState(config: MatchConfig): PokerState {
        val seats = config.seats
        val buyIn = config.option(POKER_OPTION_BUY_IN)?.toIntOrNull()?.takeIf { it > 0 } ?: POKER_DEFAULT_BUY_IN
        val bigBlind = config.option(POKER_OPTION_BIG_BLIND)?.toIntOrNull()?.takeIf { it > 0 } ?: POKER_DEFAULT_BIG_BLIND
        // O botão nasce na última cadeira para o primeiro small blind cair na cadeira zero,
        // por onde toda partida deste app começa — a mesma convenção do truco.
        return dealHand(
            seats = seats,
            stacks = List(seats) { buyIn },
            button = Seat(seats - 1),
            smallBlind = bigBlind / 2,
            bigBlind = bigBlind,
            rng = config.rng(),
        )
    }

    /** Reparte uma mão nova: duas cartas para cada cadeira viva, blinds postados. */
    private fun dealHand(
        seats: Int,
        stacks: List<Int>,
        button: Seat,
        smallBlind: Int,
        bigBlind: Int,
        rng: Rng,
    ): PokerState {
        val vivas = (0 until seats).filter { stacks[it] > 0 }
        check(vivas.size >= 2) { "Não dá para repartir uma mão com menos de duas cadeiras vivas" }

        val embaralhado = rng.shuffle(standardDeck())
        var baralho = embaralhado.value
        val maos = MutableList(seats) { emptyList<Card>() }
        for (s in vivas) {
            maos[s] = baralho.take(2)
            baralho = baralho.drop(2)
        }

        val botao = if (button.index in vivas) button else nextAlive(button, vivas, seats)
        val sbSeat = if (vivas.size == 2) botao else nextAlive(botao, vivas, seats)
        val bbSeat = nextAlive(sbSeat, vivas, seats)
        val primeiroAAgir = nextAlive(bbSeat, vivas, seats)

        val novasFichas = stacks.toMutableList()
        val streetBet = MutableList(seats) { 0 }
        val sbPago = minOf(smallBlind, novasFichas[sbSeat.index])
        val bbPago = minOf(bigBlind, novasFichas[bbSeat.index])
        novasFichas[sbSeat.index] -= sbPago
        novasFichas[bbSeat.index] -= bbPago
        streetBet[sbSeat.index] = sbPago
        streetBet[bbSeat.index] = bbPago
        val contrib = MutableList(seats) { 0 }
        contrib[sbSeat.index] = sbPago
        contrib[bbSeat.index] = bbPago

        val folded = List(seats) { it !in vivas }
        val toAct = List(seats) { it in vivas && novasFichas[it] > 0 }

        val repartido = PokerState(
            hands = maos,
            board = emptyList(),
            deck = baralho,
            stacks = novasFichas,
            streetBet = streetBet,
            contrib = contrib,
            folded = folded,
            toAct = toAct,
            pot = sbPago + bbPago,
            street = PokerStreet.PREFLOP,
            minRaise = bigBlind,
            button = botao,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            turn = primeiroAAgir,
            ply = 0,
            seats = seats,
            rng = embaralhado.rng,
        )
        // Só acontece com uma cadeira curtíssima de fichas (o blind já a deixou all-in): a
        // rodada pode ter fechado antes de qualquer lance — cai direto no mesmo caminho de
        // "ninguém mais decide nada" que uma aposta comum usaria depois de um lance.
        return if (rodadaFechou(repartido)) avancarRua(repartido) else repartido.copy(turn = firstToAct(toAct, primeiroAAgir))
    }

    /**
     * [from] se ainda precisa agir, senão a próxima cadeira (a partir dela, inclusive dando a
     * volta) que precisa — usado para não travar a vez numa cadeira all-in perto do botão.
     */
    private fun firstToAct(toAct: List<Boolean>, from: Seat): Seat {
        for (offset in 0 until toAct.size) {
            val idx = (from.index + offset) % toAct.size
            if (toAct[idx]) return Seat(idx)
        }
        return from
    }

    /** A próxima cadeira, depois de [from], que ainda precisa agir nesta rodada. */
    private fun nextToAct(state: PokerState, from: Seat): Seat {
        for (offset in 1..state.seats) {
            val idx = (from.index + offset) % state.seats
            if (state.toAct[idx]) return Seat(idx)
        }
        error("Lance aplicado sem a rodada ter fechado, mas ninguém precisa agir")
    }

    /** A próxima cadeira viva (com ficha) a partir de [from], sem contar ela mesma. */
    private fun nextAlive(from: Seat, vivas: List<Int>, seats: Int): Seat {
        for (offset in 1..seats) {
            val idx = (from.index + offset) % seats
            if (idx in vivas) return Seat(idx)
        }
        error("Nenhuma cadeira viva encontrada a partir de $from")
    }

    override fun legalMoves(state: PokerState): List<PokerMove> {
        if (outcome(state).isOver) return emptyList()
        if (awaitingAutoAdvance(state)) return listOf(PokerMove.AdvanceStreet)
        val seat = state.turn
        if (!state.isIn(seat)) return emptyList()

        val toCall = state.toCall(seat)
        val saida = mutableListOf<PokerMove>()
        if (toCall > 0) {
            saida += PokerMove.Fold
            saida += PokerMove.Call
        } else {
            saida += PokerMove.Check
        }

        if (!state.anyAllIn && state.stack(seat) > 0) {
            val allInTo = state.streetBet.getOrElse(seat.index) { 0 } + state.stack(seat)
            if (allInTo > state.maxStreetBet) {
                val minTo = state.maxStreetBet + state.minRaise
                val candidatos = sortedSetOf<Int>()
                if (minTo <= allInTo) candidatos += minTo
                val potRaiseTo = state.maxStreetBet + toCall + state.pot
                if (potRaiseTo in (minTo + 1) until allInTo) candidatos += potRaiseTo
                candidatos += allInTo
                candidatos.forEach { saida += PokerMove.Raise(it) }
            }
        }
        return saida
    }

    override fun applyMove(state: PokerState, move: PokerMove): MoveResult<PokerState> {
        if (outcome(state).isOver) return MoveResult.Illegal(ReasonKey.GAME_OVER)
        if (move is PokerMove.AdvanceStreet) {
            if (!awaitingAutoAdvance(state)) return MoveResult.Illegal(ReasonKey.POKER_NOT_AWAITING_REVEAL)
            return MoveResult.Ok(applyKnownLegal(state, move))
        }
        val seat = state.turn
        if (!state.isIn(seat)) return MoveResult.Illegal(ReasonKey.GAME_OVER)

        when (move) {
            PokerMove.AdvanceStreet -> error("tratado antes do bloco when")
            PokerMove.Fold -> {
                if (state.toCall(seat) == 0) return MoveResult.Illegal(ReasonKey.POKER_NOTHING_TO_CALL)
            }
            PokerMove.Check -> {
                if (state.toCall(seat) > 0) return MoveResult.Illegal(ReasonKey.POKER_CANNOT_CHECK)
            }
            PokerMove.Call -> {
                if (state.toCall(seat) == 0) return MoveResult.Illegal(ReasonKey.POKER_NOTHING_TO_CALL)
            }
            is PokerMove.Raise -> {
                if (state.anyAllIn) return MoveResult.Illegal(ReasonKey.POKER_NO_RAISE_AFTER_ALL_IN)
                val allInTo = state.streetBet.getOrElse(seat.index) { 0 } + state.stack(seat)
                if (move.to <= state.maxStreetBet) return MoveResult.Illegal(ReasonKey.POKER_RAISE_TOO_LOW)
                if (move.to > allInTo) return MoveResult.Illegal(ReasonKey.POKER_RAISE_TOO_HIGH)
                val minTo = state.maxStreetBet + state.minRaise
                if (move.to < minTo && move.to != allInTo) {
                    return MoveResult.Illegal(ReasonKey.POKER_RAISE_TOO_LOW)
                }
            }
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    /** Devolve a lista com a posição [index] trocada por [value] — o resto continua igual. */
    private fun <T> List<T>.replacing(index: Int, value: T): List<T> =
        toMutableList().also { it[index] = value }.toList()

    override fun applyKnownLegal(state: PokerState, move: PokerMove): PokerState {
        if (move is PokerMove.AdvanceStreet) return avancarRua(state)

        val seat = state.turn
        val depois = when (move) {
            PokerMove.AdvanceStreet -> error("tratado antes do bloco when")
            PokerMove.Fold -> state.copy(
                folded = state.folded.replacing(seat.index, true),
                toAct = state.toAct.replacing(seat.index, false),
                ply = state.ply + 1,
            )
            PokerMove.Check -> state.copy(
                toAct = state.toAct.replacing(seat.index, false),
                ply = state.ply + 1,
            )
            PokerMove.Call -> {
                val paga = minOf(state.toCall(seat), state.stack(seat))
                state.copy(
                    stacks = state.stacks.replacing(seat.index, state.stack(seat) - paga),
                    streetBet = state.streetBet.replacing(seat.index, state.streetBet.getOrElse(seat.index) { 0 } + paga),
                    contrib = state.contrib.replacing(seat.index, state.contrib.getOrElse(seat.index) { 0 } + paga),
                    pot = state.pot + paga,
                    toAct = state.toAct.replacing(seat.index, false),
                    ply = state.ply + 1,
                )
            }
            is PokerMove.Raise -> {
                val paga = move.to - state.streetBet.getOrElse(seat.index) { 0 }
                val aumento = move.to - state.maxStreetBet
                val novoToAct = state.toAct.toMutableList()
                for (i in 0 until state.seats) {
                    novoToAct[i] = i != seat.index && !state.folded[i] && state.stacks.getOrElse(i) { 0 } > 0
                }
                state.copy(
                    stacks = state.stacks.replacing(seat.index, state.stack(seat) - paga),
                    streetBet = state.streetBet.replacing(seat.index, move.to),
                    contrib = state.contrib.replacing(seat.index, state.contrib.getOrElse(seat.index) { 0 } + paga),
                    pot = state.pot + paga,
                    toAct = novoToAct,
                    minRaise = maxOf(state.minRaise, aumento),
                    ply = state.ply + 1,
                )
            }
        }

        // Desistência que deixa uma cadeira só de pé fecha a mão sem showdown: quem ficou leva
        // o pote inteiro, mesmo a parte que ela mesma não cobriu — não há mais ninguém para
        // disputar aquele excedente.
        val emJogo = (0 until depois.seats).filter { depois.folded[it].not() }
        if (move is PokerMove.Fold && emJogo.size == 1) {
            return concluirMao(depois, listOf(PokerPotShare(winners = emJogo, amount = depois.pot)))
        }

        if (rodadaFechou(depois)) return avancarRua(depois)
        return depois.copy(turn = nextToAct(depois, seat))
    }

    /** A rodada de apostas desta rua já pode fechar: ninguém que ainda pode agir está devendo ação. */
    private fun rodadaFechou(state: PokerState): Boolean =
        (0 until state.seats).none { state.toAct[it] }

    /**
     * Ninguém mais tem lance a fazer nesta mão (todo mundo que segue all-in, ou só falta um
     * com ficha) e a mesa ainda não chegou ao showdown.
     *
     * É a mesma condição que antes disparava revelar o resto da mesa de uma vez só; agora ela
     * só diz que falta revelar — quem revela, uma carta de cada vez, é [PokerMove.AdvanceStreet],
     * jogado sozinho pela camada de aplicação (veja [PokerGame.forcedMove]).
     */
    private fun awaitingAutoAdvance(state: PokerState): Boolean {
        if (outcome(state).isOver) return false
        val emJogo = (0 until state.seats).count { !state.folded[it] }
        if (emJogo < 2) return false
        val podemAgir = (0 until state.seats).count { !state.folded[it] && state.stacks[it] > 0 }
        val ninguemPrecisaAgir = (0 until state.seats).none { state.toAct[it] }
        return podemAgir <= 1 && ninguemPrecisaAgir
    }

    override fun forcedMove(state: PokerState): PokerMove? =
        if (awaitingAutoAdvance(state)) PokerMove.AdvanceStreet else null

    /** Avança para a rua seguinte — uma só, mesmo que ninguém mais tenha lance a fazer. */
    private fun avancarRua(state: PokerState): PokerState {
        if (state.street == PokerStreet.RIVER) return showdown(state)

        val cartas = when (state.street) {
            PokerStreet.PREFLOP -> 3
            PokerStreet.FLOP -> 1
            PokerStreet.TURN -> 1
            PokerStreet.RIVER -> 0
        }
        val novoBoard = state.board + state.deck.take(cartas)
        val novoDeck = state.deck.drop(cartas)
        val novaRua = when (state.street) {
            PokerStreet.PREFLOP -> PokerStreet.FLOP
            PokerStreet.FLOP -> PokerStreet.TURN
            PokerStreet.TURN -> PokerStreet.RIVER
            PokerStreet.RIVER -> PokerStreet.RIVER
        }

        val vivas = (0 until state.seats).filter { !state.folded[it] }
        val proximo = nextAlive(state.button, vivas, state.seats)
        // Com no máximo uma cadeira ainda com ficha, não sobra decisão nenhuma para ninguém
        // tomar nesta rua nova — nem para essa cadeira: ela não tem contra quem apostar, já
        // que o resto está all-in. Sem isto, ela ganharia um "passo" vazio a cada carta
        // revelada, em vez da mesa continuar sozinha até o showdown.
        val podemAgir = (0 until state.seats).count { !state.folded[it] && state.stacks[it] > 0 }
        val novoToAct = if (podemAgir <= 1) {
            List(state.seats) { false }
        } else {
            List(state.seats) { !state.folded[it] && state.stacks[it] > 0 }
        }
        val avancado = state.copy(
            board = novoBoard,
            deck = novoDeck,
            street = novaRua,
            streetBet = List(state.seats) { 0 },
            minRaise = state.bigBlind,
            toAct = novoToAct,
            // O primeiro a agir na rua nova é o próximo não desistente depois do botão — a
            // menos que ele já esteja all-in, caso em que não há nada para ele decidir e a
            // vez passa direto para quem ainda pode apostar.
            turn = firstToAct(novoToAct, proximo),
        )

        return avancado
    }

    /** Ninguém mais aposta: compara as mãos de quem sobrou e reparte o pote em camadas. */
    private fun showdown(state: PokerState): PokerState {
        val emJogo = (0 until state.seats).filter { !state.folded[it] }
        val valores = emJogo.associateWith { bestHand(state.hand(Seat(it)) + state.board) }
        val pots = potLayers(state).map { (valor, elegiveisNaCamada) ->
            // Nenhuma cadeira que ainda disputa a mão chegou a cobrir esta camada — não devia
            // acontecer (veja a nota em [potLayers]), mas se acontecer o pote fica com quem
            // continua na mão, em vez de travar sem dono.
            val elegiveis = elegiveisNaCamada.ifEmpty { emJogo }
            val melhor = elegiveis.maxOf { valores.getValue(it) }
            PokerPotShare(winners = elegiveis.filter { valores.getValue(it) == melhor }, amount = valor)
        }
        return concluirMao(state, pots)
    }

    /**
     * Divide [PokerState.contrib] em camadas de pote — do menor all-in ao maior contribuinte —
     * cada uma com quem ainda disputa aquele valor: quem entrou com menos fichas do que a
     * camada só aparece nas camadas até onde apostou, e quem já desistiu não é elegível em
     * nenhuma, mas a ficha que ele colocou continua contando para o tamanho da camada. É esta
     * divisão que garante que uma cadeira curta nunca ganha mais do que cobriu.
     *
     * Uma cadeira que segue na mão sempre cobriu (ou nunca enfrentou) qualquer aposta que
     * ainda esteja de pé — desistir é a única forma de não igualar uma, e isso trava a
     * contribuição dela onde parou. Por isso nenhuma camada devia ficar sem elegível; o
     * chamador ainda tem um fallback para esse caso, por segurança.
     */
    private fun potLayers(state: PokerState): List<Pair<Int, List<Int>>> {
        val restante = state.contrib.toMutableList()
        val camadas = mutableListOf<Pair<Int, List<Int>>>()
        while (restante.any { it > 0 }) {
            val ativos = restante.indices.filter { restante[it] > 0 }
            val menor = ativos.minOf { restante[it] }
            val valor = menor * ativos.size
            val elegiveis = ativos.filter { !state.folded[it] }
            camadas += valor to elegiveis
            for (i in ativos) restante[i] -= menor
        }
        return camadas
    }

    /** Reparte o pote entre [pots] (o resto de divisão ímpar de cada camada fica com o primeiro) e inicia a próxima mão. */
    private fun concluirMao(state: PokerState, pots: List<PokerPotShare>): PokerState {
        val resultado = PokerHandResult(pots = pots)
        val fichas = state.stacks.toMutableList()
        for (pote in pots) {
            val porCabeca = pote.amount / pote.winners.size
            val resto = pote.amount % pote.winners.size
            pote.winners.forEachIndexed { i, seat -> fichas[seat] += porCabeca + if (i == 0) resto else 0 }
        }

        val encerrado = state.copy(stacks = fichas, pot = 0, lastResult = resultado)
        val vivas = (0 until encerrado.seats).count { fichas[it] > 0 }
        if (vivas <= 1) {
            return encerrado.copy(folded = List(encerrado.seats) { fichas[it] <= 0 }, gameOver = true)
        }

        return dealHand(
            seats = encerrado.seats,
            stacks = fichas,
            button = nextAlive(encerrado.button, (0 until encerrado.seats).filter { fichas[it] > 0 }, encerrado.seats),
            smallBlind = encerrado.smallBlind,
            bigBlind = encerrado.bigBlind,
            rng = encerrado.rng,
        ).copy(ply = encerrado.ply, lastResult = resultado, handNumber = encerrado.handNumber + 1)
    }

    override fun outcome(state: PokerState): Outcome {
        if (!state.gameOver) return Outcome.InProgress
        val vencedor = state.stacks.indexOfFirst { it > 0 }
        return Outcome.Win(Seat(vencedor))
    }

    /**
     * A mão de cada um é dela, e a mesa é pública — o padrão de todo jogo de carta de
     * informação oculta. A exceção é quem já foi all-in: a cadeira que apostou tudo o que
     * tinha e segue na mão vira a carta para cima, como na mesa de verdade.
     *
     * Isso só acontece depois que a rodada em que ela foi all-in **fecha** — enquanto
     * [PokerState.maxStreetBet] ainda está de pé, mostrar a mão adiantado daria a quem falta
     * decidir (pagar ou desistir daquela aposta) uma informação que ela não teria numa mesa de
     * verdade. Uma vez fechada a rodada, não sobra mais decisão nenhuma que ver a carta possa
     * influenciar — o resto da mão é só passar até o showdown — e é seguro revelar.
     */
    override fun redactFor(state: PokerState, viewer: Seat): PokerState = state.copy(
        hands = state.hands.mapIndexed { index, mao ->
            val seat = Seat(index)
            if (index == viewer.index || mao.isEmpty() || state.allInRevealed(seat)) mao else mao.hidden()
        },
    )

    override val stateSerializer: KSerializer<PokerState> = serializer()
    override val moveSerializer: KSerializer<PokerMove> = serializer()
}
