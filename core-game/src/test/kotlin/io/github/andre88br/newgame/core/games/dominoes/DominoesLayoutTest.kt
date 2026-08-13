package io.github.andre88br.newgame.core.games.dominoes

import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sobreposição de peça é o defeito clássico deste tipo de desenho, e é invisível até
 * alguém jogar a décima peça — momento em que a mesa vira um borrão e ninguém sabe dizer
 * por quê. Aqui isso é conferido peça a peça, com a partida andando.
 */
class DominoesLayoutTest {

    private fun linha(vararg pares: Pair<Int, Int>) = pares.map { PlacedTile(it.first, it.second) }

    /** Duas peças se sobrepõem se os retângulos delas se cruzam de verdade. */
    private fun sobrepoe(a: LaidTile, b: LaidTile): Boolean {
        val folga = 0.001f
        return a.x + a.width - folga > b.x &&
            b.x + b.width - folga > a.x &&
            a.y + a.height - folga > b.y &&
            b.y + b.height - folga > a.y
    }

    @Test
    fun `mesa vazia nao tem peca nem altura zero`() {
        val mesa = DominoesLayout.table(emptyList(), columns = 8)
        assertTrue(mesa.tiles.isEmpty())
        assertEquals(1, mesa.rows, "mesa vazia ainda ocupa uma fileira, para a tela ter o que medir")
    }

    @Test
    fun `a ordem da linha e preservada`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 4)
        val mesa = DominoesLayout.table(linha, columns = 8)
        assertEquals(linha, mesa.tiles.map { it.tile }, "a mesa não pode reordenar a linha")
    }

    @Test
    fun `nenhuma peca fica por cima de outra`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 3, 3 to 5, 5 to 5, 5 to 6, 6 to 0, 0 to 4)
        val mesa = DominoesLayout.table(linha, columns = 6)

        for (i in mesa.tiles.indices) {
            for (j in i + 1 until mesa.tiles.size) {
                assertTrue(
                    !sobrepoe(mesa.tiles[i], mesa.tiles[j]),
                    "peças $i e $j se sobrepõem: ${mesa.tiles[i]} / ${mesa.tiles[j]}",
                )
            }
        }
    }

    @Test
    fun `nenhuma peca sai da mesa`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 3, 3 to 5, 5 to 6, 6 to 0)
        val mesa = DominoesLayout.table(linha, columns = 5)

        for (peca in mesa.tiles) {
            assertTrue(peca.x >= 0f, "peça saiu pela esquerda: $peca")
            assertTrue(peca.x + peca.width <= mesa.width, "peça saiu pela direita: $peca")
            assertTrue(peca.y >= 0f, "peça saiu por cima: $peca")
            assertTrue(peca.y + peca.height <= mesa.height, "peça saiu por baixo: $peca")
        }
    }

    @Test
    fun `a carroca entra atravessada`() {
        val mesa = DominoesLayout.table(linha(1 to 2, 3 to 3, 3 to 4), columns = 8)
        val carroca = mesa.tiles[1]

        assertTrue(carroca.vertical, "carroça precisa entrar atravessada")
        assertTrue(carroca.height > carroca.width, "atravessada quer dizer mais alta do que larga")

        val comum = mesa.tiles[0]
        assertTrue(!comum.vertical && comum.width > comum.height, "peça comum fica deitada")
    }

    @Test
    fun `a linha desce e volta quando chega na borda`() {
        // Mesa de 4 meias-peças: cabem duas peças por fileira.
        val mesa = DominoesLayout.table(linha(1 to 2, 2 to 3, 3 to 4, 4 to 5), columns = 4)

        assertEquals(2, mesa.rows, "quatro peças em mesa de duas deviam ocupar duas fileiras")
        assertEquals(listOf(false, false, true, true), mesa.tiles.map { it.reversed })

        // A terceira peça começa embaixo, e não à direita da segunda.
        assertTrue(mesa.tiles[2].y > mesa.tiles[1].y, "a linha precisa descer")
    }

    @Test
    fun `na fileira que volta a peca fica espelhada`() {
        val mesa = DominoesLayout.table(linha(1 to 2, 2 to 3, 3 to 4, 4 to 5), columns = 4)

        // Fileira que vai: a primeira peça encosta na esquerda.
        assertEquals(0f, mesa.tiles[0].x)
        // Fileira que volta: a primeira peça dela encosta na direita.
        assertEquals(mesa.width - mesa.tiles[2].width, mesa.tiles[2].x)
    }

    @Test
    fun `a carroca ocupa menos comprimento e adianta a serpentina`() {
        // Cinco carroças cabem numa mesa em que só caberiam duas peças comuns e meia.
        val mesa = DominoesLayout.table(linha(1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5), columns = 5)
        assertEquals(1, mesa.rows, "cinco carroças cabem numa fileira de cinco meias-peças")
    }

    @Test
    fun `mesa estreita ainda acomoda a peca mais comprida`() {
        val mesa = DominoesLayout.table(linha(1 to 2, 2 to 3, 3 to 4), columns = 2)
        assertEquals(3, mesa.rows, "com uma peça por fileira, três peças são três fileiras")
        for (peca in mesa.tiles) {
            assertTrue(peca.x + peca.width <= mesa.width)
        }
    }

    @Test
    fun `uma partida inteira cabe na mesa sem peca em cima de peca`() {
        // O caso de verdade: a linha crescendo lance a lance, em várias larguras de tela.
        for (columns in listOf(4, 6, 8, 12)) {
            var state = DominoesGame.initialState(MatchConfig(seed = 7))
            var guard = 0

            while (!DominoesGame.outcome(state).isOver && guard++ < 40) {
                val mesa = DominoesLayout.table(state.line, columns)
                assertEquals(state.line.size, mesa.tiles.size, "sumiu peça da mesa")

                for (i in mesa.tiles.indices) {
                    for (j in i + 1 until mesa.tiles.size) {
                        assertTrue(
                            !sobrepoe(mesa.tiles[i], mesa.tiles[j]),
                            "largura $columns, lance ${state.ply}: peças $i e $j se sobrepõem",
                        )
                    }
                    assertTrue(
                        mesa.tiles[i].x + mesa.tiles[i].width <= mesa.width + 0.001f,
                        "largura $columns: peça $i saiu da mesa",
                    )
                }

                val move = DominoesGame.legalMoves(state).firstOrNull() ?: break
                state = DominoesGame.applyOrThrow(state, move)
            }
            assertTrue(guard > 1, "a partida de teste precisa avançar")
        }
    }

    @Test
    fun `a largura da mesa acompanha a largura da tela`() {
        assertTrue(DominoesLayout.columnsFor(360f) > DominoesLayout.columnsFor(200f))
        assertTrue(DominoesLayout.columnsFor(50f) >= 2, "mesa minúscula ainda precisa caber uma peça")
        assertTrue(DominoesLayout.columnsFor(4000f) <= 16, "mesa de tablet não vira peça microscópica")
    }
}
