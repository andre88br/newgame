package io.github.andre88br.newgame.core.engine

import io.github.andre88br.newgame.core.ai.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * O catálogo é o único ponto em que a camada de aplicação toca nos jogos. Estes testes
 * valem para qualquer jogo que entre depois: se um jogo novo quebrar um destes contratos,
 * a tela do tabuleiro quebra junto.
 */
class GameCatalogTest {

    @Test
    fun `o catalogo nao tem jogo repetido nem chave de nome repetida`() {
        val ids = GameCatalog.available.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "jogo repetido: $ids")

        val keys = GameCatalog.available.map { it.nameKey }
        assertEquals(keys.size, keys.distinct().size, "chave de nome repetida: $keys")
    }

    @Test
    fun `todo jogo do catalogo comeca jogavel`() {
        for (entry in GameCatalog.available) {
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            assertEquals(0, state.ply, "${entry.id} não começa no lance zero")
            assertEquals(Seat.FIRST, state.turn, "${entry.id} não começa na primeira cadeira")
            assertEquals(
                Outcome.InProgress,
                entry.rules.outcome(state),
                "${entry.id} já começa terminado",
            )
            assertTrue(
                entry.rules.legalMoves(state).isNotEmpty(),
                "${entry.id} começa sem lances possíveis",
            )
        }
    }

    @Test
    fun `todo jogo do catalogo sobrevive a ida e volta pelo JSON`() {
        for (entry in GameCatalog.available) {
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            assertEquals(
                state,
                entry.rules.decodeState(entry.rules.encodeState(state)),
                "${entry.id} não sobreviveu à serialização do estado",
            )

            val move = entry.rules.legalMoves(state).first()
            assertEquals(
                move,
                entry.rules.decodeMove(entry.rules.encodeMove(move)),
                "${entry.id} não sobreviveu à serialização do lance",
            )
        }
    }

    @Test
    fun `a IA de todo jogo devolve lance legal em qualquer nivel`() {
        for (entry in GameCatalog.available) {
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            val legal = entry.rules.legalMoves(state)
            for (difficulty in Difficulty.entries) {
                val move = entry.ai.chooseMove(state, difficulty, seed = 42L)
                assertNotNull(move, "${entry.id} no $difficulty não escolheu lance")
                assertTrue(move in legal, "${entry.id} no $difficulty devolveu lance ilegal: $move")
            }
        }
    }

    @Test
    fun `aplicar um lance legal avanca a partida em todo jogo`() {
        for (entry in GameCatalog.available) {
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            val move = entry.rules.legalMoves(state).first()
            val result = entry.rules.applyMove(state, move)
            assertTrue(result is MoveResult.Ok, "${entry.id} recusou o próprio lance legal: $result")
            assertEquals(1, result.state.ply, "${entry.id} não avançou o contador de lances")
        }
    }

    @Test
    fun `jogos de informacao perfeita nao escondem nada`() {
        for (entry in GameCatalog.available) {
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            assertEquals(
                state,
                entry.rules.redactFor(state, Seat.FIRST),
                "${entry.id} redigiu um estado que não tem informação oculta",
            )
        }
    }

    @Test
    fun `pedir um jogo ainda nao implementado falha com mensagem clara`() {
        val naoImplementado = GameId.entries.firstOrNull { !GameCatalog.contains(it) }
        if (naoImplementado != null) {
            val failure = runCatching { GameCatalog.entry(naoImplementado) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue("não foi implementado" in failure.message.orEmpty())
        }
    }
}
