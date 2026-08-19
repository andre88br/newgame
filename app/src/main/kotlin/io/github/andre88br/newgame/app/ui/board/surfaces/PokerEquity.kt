package io.github.andre88br.newgame.app.ui.board.surfaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.standardDeck
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.poker.PokerState
import io.github.andre88br.newgame.core.games.poker.bestHand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Quantas mesas aleatórias a estimativa sorteia quando ainda falta carta na mesa. */
private const val EQUITY_SAMPLES = 300

/**
 * A chance de cada cadeira vencer o showdown agora, a partir só do que já está à vista.
 *
 * Não é a mesma conta que [io.github.andre88br.newgame.core.games.poker.PokerAi] usa para
 * decidir um lance: essa vê a mão de verdade, escondida da tela. Esta função só enxerga o que
 * [io.github.andre88br.newgame.core.games.poker.PokerGame.redactFor] já deixou visível — e é
 * exatamente por isso que só funciona (e só faz sentido mostrar) num all-in: é o único momento
 * em que a mão de quem segue na mão vira carta para cima. Sobrando alguém com a mão ainda
 * escondida, a conta não tem como incluí-lo sem inventar informação que a tela não tem, e por
 * isso devolve `null` — nenhuma cadeira mostra chance nenhuma nesse caso.
 *
 * Sem carta faltando na mesa (`board` já tem cinco), o resultado é exato: só há uma mesa
 * possível. Faltando carta, [EQUITY_SAMPLES] mesas aleatórias completam o baralho e a fração
 * de vitórias (dividida em caso de empate) é a estimativa — a mesma técnica de amostragem que a
 * IA usa, só que para todo mundo que segue na mão de uma vez, não para um só ponto de vista.
 */
fun pokerAllInEquities(state: PokerState): Map<Seat, Double>? {
    if (!state.anyAllIn) return null
    val contendores = (0 until state.seats).filter { state.isIn(Seat(it)) }
    if (contendores.size < 2) return null

    val maos = contendores.associateWith { state.hand(Seat(it)) }
    if (maos.values.any { mao -> mao.size != 2 || mao.any(Card::isHidden) }) return null

    val usadas = (maos.values.flatten() + state.board).toHashSet()
    val remanescente = standardDeck().filterNot { it in usadas }
    val faltam = 5 - state.board.size

    val pontos = contendores.associateWith { 0.0 }.toMutableMap()
    val iteracoes = if (faltam == 0) 1 else EQUITY_SAMPLES
    repeat(iteracoes) {
        val mesa = if (faltam == 0) state.board else state.board + remanescente.shuffled().take(faltam)
        val forcas = contendores.associateWith { seat -> bestHand(maos.getValue(seat) + mesa) }
        val melhor = forcas.values.max()
        val vencedores = forcas.filterValues { it == melhor }.keys
        vencedores.forEach { pontos[it] = pontos.getValue(it) + 1.0 / vencedores.size }
    }
    return contendores.associate { Seat(it) to (pontos.getValue(it) / iteracoes) }
}

/**
 * [pokerAllInEquities], recalculada fora da thread da interface a cada carta nova da mesa.
 *
 * A amostragem custa o bastante — cem e poucas avaliações de [bestHand] por mesa sorteada —
 * para não valer rodar no meio de uma recomposição: `produceState` joga o cálculo para
 * [Dispatchers.Default] e só publica o resultado quando ele termina.
 */
@Composable
fun rememberPokerAllInEquities(state: PokerState): Map<Seat, Double>? {
    val resultado by produceState<Map<Seat, Double>?>(initialValue = null, state.board, state.hands, state.folded) {
        value = withContext(Dispatchers.Default) { pokerAllInEquities(state) }
    }
    return resultado
}
