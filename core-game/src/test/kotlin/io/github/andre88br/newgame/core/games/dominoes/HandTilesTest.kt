package io.github.andre88br.newgame.core.games.dominoes

import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A tela do dominó mostra a mão e deixa tocar na peça. Quem decide se o toque vira lance
 * direto ou pergunta a ponta é [handTiles] — e é aqui que isso se verifica, não no app.
 */
class HandTilesTest {

    private fun tile(low: Int, high: Int) = Tile(minOf(low, high), maxOf(low, high))

    private fun mesa(
        line: List<PlacedTile>,
        hand: List<Tile>,
    ) = DominoesState(
        line = line,
        hands = listOf(hand, listOf(tile(6, 6))),
        turn = Seat.FIRST,
    )

    @Test
    fun `a mao sai na ordem em que esta, inclusive as pecas que nao jogam`() {
        val mao = listOf(tile(0, 1), tile(3, 4), tile(5, 5))
        val state = mesa(listOf(PlacedTile(1, 2)), mao)

        val tiles = handTiles(state, Seat.FIRST)
        assertContentEquals(mao, tiles.map { it.tile }, "a mão não pode ser reordenada na tela")
        assertEquals(3, tiles.size)
    }

    @Test
    fun `mesa vazia deixa jogar qualquer peca, e numa ponta so`() {
        val mao = listOf(tile(0, 1), tile(3, 4))
        val tiles = handTiles(mesa(emptyList(), mao), Seat.FIRST)

        assertTrue(tiles.all { it.playable })
        assertTrue(
            tiles.all { it.ends == listOf(LineEnd.RIGHT) },
            "com a mesa vazia não há esquerda nem direita: uma ponta basta",
        )
        assertTrue(tiles.all { it.onlyMove != null }, "abertura é sempre toque único")
    }

    @Test
    fun `peca que encaixa nas duas pontas pede escolha`() {
        // Pontas 2 e 5: o 2-5 entra dos dois lados.
        val state = mesa(listOf(PlacedTile(2, 3), PlacedTile(3, 5)), listOf(tile(2, 5)))
        val tiles = handTiles(state, Seat.FIRST)

        assertEquals(setOf(LineEnd.LEFT, LineEnd.RIGHT), tiles.single().ends.toSet())
        assertNull(tiles.single().onlyMove, "com duas pontas a tela precisa perguntar")
    }

    @Test
    fun `peca que encaixa numa ponta so vira lance direto`() {
        // Pontas 2 e 5: o 5-6 só entra na direita.
        val state = mesa(listOf(PlacedTile(2, 3), PlacedTile(3, 5)), listOf(tile(5, 6)))
        val tiles = handTiles(state, Seat.FIRST)

        assertEquals(listOf(LineEnd.RIGHT), tiles.single().ends)
        assertEquals(DominoesMove(tile(5, 6), LineEnd.RIGHT), tiles.single().onlyMove)
    }

    @Test
    fun `com as duas pontas iguais nao ha o que escolher`() {
        // Pontas 3 e 3: encostar de um lado ou do outro dá exatamente no mesmo.
        val state = mesa(listOf(PlacedTile(3, 4), PlacedTile(4, 3)), listOf(tile(3, 6)))
        val tiles = handTiles(state, Seat.FIRST)

        assertEquals(listOf(LineEnd.RIGHT), tiles.single().ends)
        assertEquals(DominoesMove(tile(3, 6), LineEnd.RIGHT), tiles.single().onlyMove)
    }

    @Test
    fun `peca sem encaixe aparece na mao marcada como impossivel`() {
        val state = mesa(listOf(PlacedTile(2, 3), PlacedTile(3, 5)), listOf(tile(0, 1)))
        val tiles = handTiles(state, Seat.FIRST)

        assertTrue(!tiles.single().playable)
        assertNull(tiles.single().onlyMove)
    }

    @Test
    fun `a mao oculta do adversario nao oferece lance nenhum`() {
        val state = DominoesGame.initialState(MatchConfig(seed = 5))
        val escondido = DominoesGame.redactFor(state, state.turn)
        val outro = Seat(1 - state.turn.index)

        val tiles = handTiles(escondido, outro)
        assertEquals(state.handSize(outro), tiles.size, "a contagem de peças continua visível")
        assertTrue(tiles.all { it.tile.isHidden }, "as peças do outro ficam viradas")
        assertTrue(tiles.none { it.playable }, "não se joga pela mão que não se vê")
    }

    @Test
    fun `todo lance oferecido pela mao e aceito pelo motor`() {
        var state = DominoesGame.initialState(MatchConfig(seed = 2026))
        var guard = 0

        while (!DominoesGame.outcome(state).isOver && guard++ < 60) {
            val tiles = handTiles(state, state.turn)
            val oferecidos = tiles.flatMap { hand -> hand.ends.map { DominoesMove(hand.tile, it) } }
            assertContentEquals(
                DominoesGame.legalMoves(state).sortedBy { it.toString() },
                oferecidos.sortedBy { it.toString() },
                "a mão da tela discorda dos lances legais",
            )
            state = DominoesGame.applyOrThrow(state, oferecidos.first())
        }
        assertTrue(guard > 1, "a partida de teste precisa avançar pelo menos um lance")
    }
}
