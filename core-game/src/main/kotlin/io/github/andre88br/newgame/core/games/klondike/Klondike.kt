package io.github.andre88br.newgame.core.games.klondike

import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit
import io.github.andre88br.newgame.core.cards.hidden
import io.github.andre88br.newgame.core.cards.standardDeck
import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.ReasonKey
import io.github.andre88br.newgame.core.engine.Seat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

/** As sete colunas da mesa. */
const val KLONDIKE_PILES: Int = 7

/** As quatro casas de saída, uma por naipe. */
const val KLONDIKE_FOUNDATIONS: Int = 4

/**
 * O valor da carta **nesta** paciência, onde o ás é a menor de todas.
 *
 * O baralho compartilhado dá ao ás a ordem 14, porque na canastra e no pife ele é carta
 * alta. Aqui ele abre a casa e fecha nada: a casa sobe A-2-3…K, e a coluna desce K-Q-J…A.
 * Converter num lugar só evita que a diferença vaze para o resto do jogo.
 */
val Rank.klondikeOrder: Int get() = if (this == Rank.ACE) 1 else order

/** A carta [card] encaixa em cima de [onto] numa coluna: uma abaixo, e de cor trocada. */
fun stacksOnTableau(card: Card, onto: Card): Boolean =
    card.rank.klondikeOrder == onto.rank.klondikeOrder - 1 && card.isRed != onto.isRed

/** A carta [card] entra na casa que já tem [foundation] (vazia é lista vazia). */
fun stacksOnFoundation(card: Card, foundation: List<Card>): Boolean {
    val topo = foundation.lastOrNull() ?: return card.rank == Rank.ACE
    return card.suit == topo.suit && card.rank.klondikeOrder == topo.rank.klondikeOrder + 1
}

/**
 * A mesa da paciência.
 *
 * As colunas vêm partidas em duas listas — [downs] e [ups] —, e não numa lista só com uma
 * marca de "virada". A regra distingue as duas coisas o tempo todo: só a parte virada para
 * cima se move, e desvirar a carta de baixo é o objetivo do jogo. Guardar isso na forma do
 * dado tira a pergunta de todo lugar que percorre a coluna.
 *
 * As cartas de [ups] vão de baixo para cima: a última é a que está por cima, e a única que
 * recebe outra carta.
 */
@Serializable
data class KlondikeState(
    /** O que ainda está virado para baixo em cada coluna, de baixo para cima. */
    val downs: List<List<Card>> = emptyList(),
    /** O que já está virado para cima em cada coluna, de baixo para cima. */
    val ups: List<List<Card>> = emptyList(),
    /** As quatro casas, na ordem de [Suit.entries]. */
    val foundations: List<List<Card>> = List(KLONDIKE_FOUNDATIONS) { emptyList() },
    /** O monte de compra, virado para baixo. A última é a próxima a sair. */
    val stock: List<Card> = emptyList(),
    /** As compradas, viradas para cima. Só a última se joga. */
    val waste: List<Card> = emptyList(),
    override val turn: Seat = Seat.FIRST,
    override val ply: Int = 0,
    /** Quantas vezes o descarte já voltou a ser monte. Aparece na tela; não limita nada. */
    val redeals: Int = 0,
) : GameState {

    /** A carta jogável do descarte, ou `null` se ele estiver vazio. */
    val wasteTop: Card? get() = waste.lastOrNull()

    /** Quantas cartas já subiram para as casas. Vencer é chegar a 52. */
    val placed: Int get() = foundations.sumOf { it.size }

    fun foundationOf(suit: Suit): List<Card> = foundations[suit.ordinal]

    override fun toString(): String = buildString {
        append("monte=${stock.size} descarte=${waste.size} casas=$placed\n")
        for (i in 0 until KLONDIKE_PILES) {
            append("coluna $i: ${downs[i].size} viradas + ${ups[i].joinToString(" ")}\n")
        }
    }
}

/** O que se pode fazer na paciência. */
@Serializable
sealed interface KlondikeMove : Move {

    /** Tira a de cima do monte e põe no descarte. */
    @Serializable
    data object Draw : KlondikeMove {
        override fun describe(): String = "compra"
    }

    /** Devolve o descarte ao monte, na ordem em que saiu. */
    @Serializable
    data object Recycle : KlondikeMove {
        override fun describe(): String = "vira o descarte"
    }

    /** Do descarte para uma coluna. */
    @Serializable
    data class WasteToPile(val pile: Int) : KlondikeMove {
        override fun describe(): String = "descarte → coluna ${pile + 1}"
    }

    /** Do descarte para a casa do naipe. */
    @Serializable
    data object WasteToFoundation : KlondikeMove {
        override fun describe(): String = "descarte → casa"
    }

    /** Da coluna para a casa do naipe. Só a de cima sobe. */
    @Serializable
    data class PileToFoundation(val pile: Int) : KlondikeMove {
        override fun describe(): String = "coluna ${pile + 1} → casa"
    }

    /**
     * De uma coluna para outra, levando [count] cartas de cima.
     *
     * Levar mais de uma é a mesma regra de levar uma: a parte virada para cima de uma coluna
     * é sempre uma sequência descendente de cores trocadas, então qualquer pedaço do topo
     * dela já é um bloco válido — não é preciso conferir o bloco, só onde ele vai pousar.
     */
    @Serializable
    data class PileToPile(val from: Int, val count: Int, val to: Int) : KlondikeMove {
        override fun describe(): String = "coluna ${from + 1} → coluna ${to + 1} ($count)"
    }

    /** Da casa de volta para uma coluna: às vezes é o único jeito de destravar. */
    @Serializable
    data class FoundationToPile(val suit: Suit, val pile: Int) : KlondikeMove {
        override fun describe(): String = "casa ${suit.symbol} → coluna ${pile + 1}"
    }
}

/**
 * Paciência (klondike), de uma pessoa só.
 *
 * Vinte e oito cartas em sete colunas, o resto no monte, e quatro casas para encher do ás ao
 * rei. Nas colunas as cartas descem trocando de cor; nas casas sobem por naipe.
 *
 * Compra de uma em uma, e o descarte volta ao monte quantas vezes for preciso. É a versão
 * mansa da paciência, e é escolha: a de três em três com passagens contadas ganha do jogador
 * na maioria das mãos, e quem abre uma paciência no celular não está procurando adversário.
 *
 * É o único jogo do app com uma cadeira só. Não há vez que passe, não há quem esteja
 * pensando do outro lado, e não há como perder — só ganhar ou empacar. O que existe é a
 * dica, e no jogo de uma pessoa ela deixa de ser conselho de adversário e vira lanterna.
 */
object KlondikeGame : BoardGame<KlondikeState, KlondikeMove> {

    override val id: GameId = GameId.KLONDIKE

    override val supportedSeats: IntRange = 1..1

    override fun seatsIn(state: KlondikeState): Int = 1

    /**
     * O que está virado para baixo é oculto — inclusive de quem joga.
     *
     * É diferente dos outros jogos de carta, onde esconder é esconder **do adversário**.
     * Aqui não há adversário: o que a redação protege é o próprio jogo, e é ela que impede
     * a dica de olhar as cartas viradas e sugerir um lance que quem joga não teria como
     * enxergar.
     */
    override val hasHiddenInformation: Boolean = true

    override fun initialState(config: MatchConfig): KlondikeState {
        val embaralhado = config.rng().shuffle(standardDeck())
        val cartas = embaralhado.value.toMutableList()

        val viradas = mutableListOf<List<Card>>()
        val abertas = mutableListOf<List<Card>>()
        for (coluna in 0 until KLONDIKE_PILES) {
            // A coluna n recebe n cartas para baixo e uma para cima: 1, 2, 3… 7 no total.
            val paraBaixo = List(coluna) { cartas.removeAt(0) }
            viradas += paraBaixo
            abertas += listOf(cartas.removeAt(0))
        }

        return KlondikeState(
            downs = viradas,
            ups = abertas,
            // O monte sai pelo fim da lista, então a ordem de compra é a ordem do baralho.
            stock = cartas.reversed(),
            waste = emptyList(),
        )
    }

    override fun legalMoves(state: KlondikeState): List<KlondikeMove> {
        if (outcome(state).isOver) return emptyList()

        val saida = mutableListOf<KlondikeMove>()

        if (state.stock.isNotEmpty()) saida += KlondikeMove.Draw
        if (state.stock.isEmpty() && state.waste.isNotEmpty()) saida += KlondikeMove.Recycle

        val doDescarte = state.wasteTop
        if (doDescarte != null) {
            if (stacksOnFoundation(doDescarte, state.foundationOf(doDescarte.suit))) {
                saida += KlondikeMove.WasteToFoundation
            }
            for (coluna in 0 until KLONDIKE_PILES) {
                if (aceita(state, coluna, doDescarte)) saida += KlondikeMove.WasteToPile(coluna)
            }
        }

        for (origem in 0 until KLONDIKE_PILES) {
            val abertas = state.ups[origem]
            if (abertas.isEmpty()) continue

            val topo = abertas.last()
            if (stacksOnFoundation(topo, state.foundationOf(topo.suit))) {
                saida += KlondikeMove.PileToFoundation(origem)
            }

            for (quantas in 1..abertas.size) {
                val movida = abertas[abertas.size - quantas]
                for (destino in 0 until KLONDIKE_PILES) {
                    if (destino == origem) continue
                    // Mudar uma coluna inteira de lugar não muda nada: só troca o buraco de
                    // coluna. Sem isto a dica ficaria empurrando o mesmo rei para os lados.
                    if (quantas == abertas.size && state.downs[origem].isEmpty() &&
                        state.ups[destino].isEmpty()
                    ) {
                        continue
                    }
                    if (aceita(state, destino, movida)) {
                        saida += KlondikeMove.PileToPile(origem, quantas, destino)
                    }
                }
            }
        }

        for (naipe in Suit.entries) {
            val carta = state.foundationOf(naipe).lastOrNull() ?: continue
            for (coluna in 0 until KLONDIKE_PILES) {
                if (aceita(state, coluna, carta)) {
                    saida += KlondikeMove.FoundationToPile(naipe, coluna)
                }
            }
        }

        return saida
    }

    /** A coluna [pile] recebe [card]? Vazia só recebe rei; cheia, uma abaixo e de outra cor. */
    private fun aceita(state: KlondikeState, pile: Int, card: Card): Boolean {
        val topo = state.ups[pile].lastOrNull()
            ?: return state.downs[pile].isEmpty() && card.rank == Rank.KING
        return stacksOnTableau(card, topo)
    }

    override fun applyMove(state: KlondikeState, move: KlondikeMove): MoveResult<KlondikeState> {
        if (outcome(state).isOver) return MoveResult.Illegal(ReasonKey.GAME_OVER)

        when (move) {
            KlondikeMove.Draw ->
                if (state.stock.isEmpty()) return MoveResult.Illegal(ReasonKey.CARD_NOTHING_TO_DRAW)

            KlondikeMove.Recycle -> {
                if (state.stock.isNotEmpty()) {
                    return MoveResult.Illegal(ReasonKey.KLONDIKE_STOCK_NOT_EMPTY)
                }
                if (state.waste.isEmpty()) return MoveResult.Illegal(ReasonKey.CARD_NOTHING_TO_DRAW)
            }

            KlondikeMove.WasteToFoundation -> {
                val carta = state.wasteTop ?: return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                if (!stacksOnFoundation(carta, state.foundationOf(carta.suit))) {
                    return MoveResult.Illegal(ReasonKey.KLONDIKE_FOUNDATION_ORDER)
                }
            }

            is KlondikeMove.WasteToPile -> {
                if (move.pile !in 0 until KLONDIKE_PILES) {
                    return MoveResult.Illegal(ReasonKey.INVALID_SQUARE)
                }
                val carta = state.wasteTop ?: return MoveResult.Illegal(ReasonKey.CARD_NOT_IN_HAND)
                if (!aceita(state, move.pile, carta)) {
                    return MoveResult.Illegal(recusaDeColuna(state, move.pile))
                }
            }

            is KlondikeMove.PileToFoundation -> {
                if (move.pile !in 0 until KLONDIKE_PILES) {
                    return MoveResult.Illegal(ReasonKey.INVALID_SQUARE)
                }
                val carta = state.ups[move.pile].lastOrNull()
                    ?: return MoveResult.Illegal(ReasonKey.NO_PIECE_HERE)
                if (!stacksOnFoundation(carta, state.foundationOf(carta.suit))) {
                    return MoveResult.Illegal(ReasonKey.KLONDIKE_FOUNDATION_ORDER)
                }
            }

            is KlondikeMove.PileToPile -> {
                if (move.from !in 0 until KLONDIKE_PILES || move.to !in 0 until KLONDIKE_PILES) {
                    return MoveResult.Illegal(ReasonKey.INVALID_SQUARE)
                }
                if (move.from == move.to) return MoveResult.Illegal(ReasonKey.INVALID_SQUARE)
                val abertas = state.ups[move.from]
                if (move.count !in 1..abertas.size) {
                    return MoveResult.Illegal(ReasonKey.NO_PIECE_HERE)
                }
                val movida = abertas[abertas.size - move.count]
                if (!aceita(state, move.to, movida)) {
                    return MoveResult.Illegal(recusaDeColuna(state, move.to))
                }
            }

            is KlondikeMove.FoundationToPile -> {
                if (move.pile !in 0 until KLONDIKE_PILES) {
                    return MoveResult.Illegal(ReasonKey.INVALID_SQUARE)
                }
                val carta = state.foundationOf(move.suit).lastOrNull()
                    ?: return MoveResult.Illegal(ReasonKey.NO_PIECE_HERE)
                if (!aceita(state, move.pile, carta)) {
                    return MoveResult.Illegal(recusaDeColuna(state, move.pile))
                }
            }
        }
        return MoveResult.Ok(applyKnownLegal(state, move))
    }

    /** Coluna vazia recusa por um motivo (só rei entra); coluna cheia, por outro. */
    private fun recusaDeColuna(state: KlondikeState, pile: Int): ReasonKey =
        if (state.ups[pile].isEmpty() && state.downs[pile].isEmpty()) {
            ReasonKey.KLONDIKE_EMPTY_PILE_KING_ONLY
        } else {
            ReasonKey.KLONDIKE_TABLEAU_ORDER
        }

    override fun applyKnownLegal(state: KlondikeState, move: KlondikeMove): KlondikeState {
        val depois = when (move) {
            KlondikeMove.Draw -> state.copy(
                stock = state.stock.dropLast(1),
                waste = state.waste + state.stock.last(),
            )

            KlondikeMove.Recycle -> state.copy(
                // O descarte volta na ordem em que saiu: a primeira comprada volta a ser a
                // primeira a sair. Embaralhar aqui seria outro jogo.
                stock = state.waste.reversed(),
                waste = emptyList(),
                redeals = state.redeals + 1,
            )

            KlondikeMove.WasteToFoundation -> {
                val carta = state.waste.last()
                state.copy(
                    waste = state.waste.dropLast(1),
                    foundations = comCasa(state, carta.suit, state.foundationOf(carta.suit) + carta),
                )
            }

            is KlondikeMove.WasteToPile -> state.copy(
                waste = state.waste.dropLast(1),
                ups = comColuna(state.ups, move.pile, state.ups[move.pile] + state.waste.last()),
            )

            is KlondikeMove.PileToFoundation -> {
                val carta = state.ups[move.pile].last()
                state.copy(
                    ups = comColuna(state.ups, move.pile, state.ups[move.pile].dropLast(1)),
                    foundations = comCasa(state, carta.suit, state.foundationOf(carta.suit) + carta),
                )
            }

            is KlondikeMove.PileToPile -> {
                val abertas = state.ups[move.from]
                val bloco = abertas.takeLast(move.count)
                var colunas = comColuna(state.ups, move.from, abertas.dropLast(move.count))
                colunas = comColuna(colunas, move.to, colunas[move.to] + bloco)
                state.copy(ups = colunas)
            }

            is KlondikeMove.FoundationToPile -> {
                val casa = state.foundationOf(move.suit)
                state.copy(
                    foundations = comCasa(state, move.suit, casa.dropLast(1)),
                    ups = comColuna(state.ups, move.pile, state.ups[move.pile] + casa.last()),
                )
            }
        }
        return desvirar(depois).copy(ply = state.ply + 1)
    }

    /**
     * Coluna que ficou sem carta para cima e ainda tem cartas embaixo desvira a de cima.
     *
     * É automático porque na mesa também é: ninguém escolhe deixar uma carta virada para
     * baixo quando ela ficou exposta. Fazer disso um lance encheria a partida de lances que
     * não são decisão nenhuma.
     */
    private fun desvirar(state: KlondikeState): KlondikeState {
        var viradas = state.downs
        var abertas = state.ups
        for (coluna in 0 until KLONDIKE_PILES) {
            if (abertas[coluna].isNotEmpty() || viradas[coluna].isEmpty()) continue
            abertas = comColuna(abertas, coluna, listOf(viradas[coluna].last()))
            viradas = comColuna(viradas, coluna, viradas[coluna].dropLast(1))
        }
        return state.copy(downs = viradas, ups = abertas)
    }

    private fun comColuna(colunas: List<List<Card>>, index: Int, nova: List<Card>): List<List<Card>> =
        colunas.mapIndexed { i, atual -> if (i == index) nova else atual }

    private fun comCasa(state: KlondikeState, suit: Suit, nova: List<Card>): List<List<Card>> =
        state.foundations.mapIndexed { i, atual -> if (i == suit.ordinal) nova else atual }

    /**
     * Ganhou, empacou, ou ainda dá.
     *
     * Não há derrota: na paciência ou as 52 cartas sobem, ou chega a hora em que não existe
     * lance nenhum. O empate por travamento não é consolo — é o motor dizendo que a mão
     * acabou, para a tela poder oferecer outra em vez de deixar a pessoa procurando um lance
     * que não existe.
     */
    override fun outcome(state: KlondikeState): Outcome = when {
        state.placed == 52 -> Outcome.Win(Seat.FIRST)
        semLance(state) -> Outcome.Draw(DrawReason.BLOCKED)
        else -> Outcome.InProgress
    }

    /**
     * A mão empacou.
     *
     * Não é "não há lance": enquanto sobrar carta no monte sempre dá para comprar mais uma.
     * É "não há lance que leve a nada" — nenhuma carta da mesa se move, e nenhuma das que
     * ainda vão passar pelo descarte tem onde pousar. Como comprar não muda a mesa, girar o
     * monte pela quarta vez daria exatamente na mesma. Dizer isso é melhor do que deixar
     * quem joga procurando um lance que não existe.
     *
     * Conta sem chamar [legalMoves] porque `outcome` é consultado a cada quadro da tela — e,
     * pior, `legalMoves` chamaria `outcome` de volta.
     */
    private fun semLance(state: KlondikeState): Boolean {
        // Visão redigida: com o monte virado para baixo não dá para afirmar nada sobre ele.
        // A dica enxerga daqui, e é melhor ela não decidir que a mão acabou.
        if (state.stock.any { it.isHidden }) return false

        for (carta in state.stock + state.waste) {
            if (stacksOnFoundation(carta, state.foundationOf(carta.suit))) return false
            for (coluna in 0 until KLONDIKE_PILES) {
                if (aceita(state, coluna, carta)) return false
            }
        }

        for (origem in 0 until KLONDIKE_PILES) {
            val abertas = state.ups[origem]
            if (abertas.isEmpty()) continue
            val topo = abertas.last()
            if (stacksOnFoundation(topo, state.foundationOf(topo.suit))) return false
            for (quantas in 1..abertas.size) {
                val movida = abertas[abertas.size - quantas]
                for (destino in 0 until KLONDIKE_PILES) {
                    if (destino == origem) continue
                    if (quantas == abertas.size && state.downs[origem].isEmpty() &&
                        state.ups[destino].isEmpty()
                    ) {
                        continue
                    }
                    if (aceita(state, destino, movida)) return false
                }
            }
        }

        // Descer carta da casa só destrava se houver onde encostá-la.
        for (naipe in Suit.entries) {
            val carta = state.foundationOf(naipe).lastOrNull() ?: continue
            for (coluna in 0 until KLONDIKE_PILES) {
                if (aceita(state, coluna, carta)) return false
            }
        }
        return true
    }

    /** Some o que está virado para baixo: as cartas do fundo das colunas e o monte. */
    override fun redactFor(state: KlondikeState, viewer: Seat): KlondikeState = state.copy(
        downs = state.downs.map { it.hidden() },
        stock = state.stock.hidden(),
    )

    override val stateSerializer: KSerializer<KlondikeState> = serializer()
    override val moveSerializer: KSerializer<KlondikeMove> = serializer()
}
