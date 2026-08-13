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
            // Quem abre não é sempre a primeira cadeira: no dominó abre quem tirou a maior
            // dupla, e no ludo abre quem tirou um dado que serve. O contrato é a cadeira
            // existir na mesa, não ser a de índice zero.
            assertTrue(
                state.turn.index in 0 until entry.rules.seatsIn(state),
                "${entry.id} começa numa cadeira que não existe: ${state.turn}",
            )
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
            if (entry.rules.hasHiddenInformation) continue
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            assertEquals(
                state,
                entry.rules.redactFor(state, Seat.FIRST),
                "${entry.id} redigiu um estado que não tem informação oculta",
            )
        }
    }

    /**
     * O contrário do teste acima, e o que de fato protege quem joga: um jogo que se declara
     * de informação oculta precisa esconder alguma coisa de verdade. Declarar a bandeira e
     * devolver o estado inteiro passaria despercebido — e a IA veria a mão do adversário.
     */
    @Test
    fun `jogo de informacao oculta esconde algo de quem olha`() {
        val ocultos = GameCatalog.available.filter { it.rules.hasHiddenInformation }
        assertTrue(ocultos.isNotEmpty(), "nenhum jogo de informação oculta no catálogo")

        for (entry in ocultos) {
            val state = entry.rules.initialState(MatchConfig(seed = 7))
            for (index in 0 until entry.rules.seatsIn(state)) {
                val viewer = Seat(index)
                val visto = entry.rules.redactFor(state, viewer)
                assertTrue(
                    visto != state,
                    "${entry.id} entregou o estado inteiro para a cadeira $index",
                )
                assertEquals(
                    visto,
                    entry.rules.redactFor(visto, viewer),
                    "${entry.id}: redigir duas vezes precisa dar no mesmo",
                )
            }
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
