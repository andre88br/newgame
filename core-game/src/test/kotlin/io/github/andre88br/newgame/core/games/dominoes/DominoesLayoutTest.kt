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

    // -------- a curva --------

    /**
     * Mesa de 8 meias-peças com peças comuns: três deitadas enchem a fileira e a quarta
     * chega na borda. Ela é a curva.
     */
    private val comCurva = DominoesLayout.table(
        linha(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 0, 0 to 1, 1 to 3),
        columns = 8,
    )

    /**
     * O defeito que este arquivo existe para não deixar voltar: a peça que chegava na borda
     * ia parar deitada na fileira de baixo, como quebra de linha de máquina de escrever. Numa
     * mesa de dominó ela fica **em pé**, ligando uma fileira à outra.
     */
    @Test
    fun `a peca que chega na borda fica em pe`() {
        val curva = comCurva.tiles.firstOrNull { it.facing == TileFacing.TURN }

        assertTrue(curva != null, "nenhuma peça fez a curva: a mesa voltou a quebrar linha")
        assertTrue(curva.height > curva.width, "em pé quer dizer mais alta do que larga")
        assertTrue(curva.stacked, "em pé, as metades ficam uma sobre a outra")
    }

    @Test
    fun `a peca da curva liga uma fileira a seguinte`() {
        val indice = comCurva.tiles.indexOfFirst { it.facing == TileFacing.TURN }
        val antes = comCurva.tiles[indice - 1]
        val curva = comCurva.tiles[indice]
        val depois = comCurva.tiles[indice + 1]

        assertEquals(antes.y, curva.y, "a curva começa na fileira de quem veio antes")
        assertEquals(depois.y, curva.y + curva.height - depois.height, "e termina na fileira de baixo")
        assertTrue(depois.y > antes.y, "a linha precisa ter descido")
    }

    @Test
    fun `a curva acontece no fim da fileira, e nao no meio`() {
        val curva = comCurva.tiles.first { it.facing == TileFacing.TURN }
        val mesmaFileira = comCurva.tiles.filter { it.y == curva.y && it !== curva }

        // Nada da fileira dela passa dela: a curva é o fim do caminho de ida.
        assertTrue(
            mesmaFileira.all { it.x + it.width <= curva.x + curva.width },
            "há peça além da curva na mesma fileira",
        )
        assertTrue(
            curva.x >= comCurva.columns - 2,
            "a curva devia estar na borda, e está em ${curva.x} de ${comCurva.columns}",
        )
    }

    @Test
    fun `a fileira seguinte corre na direcao contraria`() {
        val curva = comCurva.tiles.first { it.facing == TileFacing.TURN }
        // Só a fileira logo abaixo: duas abaixo a linha já virou de novo e corre no sentido
        // original, que é justamente o que serpentear quer dizer.
        val deBaixo = comCurva.tiles.filter { it.y == curva.y + 1f && it.facing == TileFacing.ALONG }
        val duasAbaixo = comCurva.tiles.filter { it.y == curva.y + 2f && it.facing == TileFacing.ALONG }

        assertTrue(deBaixo.isNotEmpty(), "o teste precisa de peça deitada na fileira de baixo")
        assertTrue(deBaixo.all { it.reversed }, "na fileira que volta a peça sai espelhada")
        assertTrue(duasAbaixo.isNotEmpty(), "o teste precisa de uma terceira fileira")
        assertTrue(duasAbaixo.none { it.reversed }, "duas fileiras abaixo a linha volta ao sentido de ida")
    }

    @Test
    fun `a ultima peca da partida nao fica em pe a toa`() {
        // Ficar em pé serve para virar. Sem linha depois dela, a peça deita como as outras.
        val mesa = DominoesLayout.table(linha(1 to 2, 2 to 3, 3 to 4, 4 to 5), columns = 8)
        assertEquals(TileFacing.ALONG, mesa.tiles.last().facing)
    }

    // -------- carroça --------

    @Test
    fun `a carroca entra atravessada`() {
        val mesa = DominoesLayout.table(linha(1 to 2, 3 to 3, 3 to 4), columns = 8)
        val carroca = mesa.tiles[1]

        assertEquals(TileFacing.CROSS, carroca.facing)
        assertTrue(carroca.stacked, "atravessada quer dizer metades uma sobre a outra")
        assertEquals(carroca.width, carroca.height, "atravessada, a carroça ocupa uma célula só")

        val comum = mesa.tiles[0]
        assertEquals(TileFacing.ALONG, comum.facing)
        assertTrue(comum.width > comum.height, "peça comum fica deitada")
    }

    @Test
    fun `a carroca ocupa menos comprimento e adianta a serpentina`() {
        val mesa = DominoesLayout.table(linha(1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5), columns = 5)
        assertEquals(1, mesa.rows, "cinco carroças cabem numa fileira de cinco meias-peças")
    }

    // -------- invariantes do desenho --------

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

    /**
     * O que faz a linha parecer uma linha: cada peça encosta na anterior. Se duas vizinhas
     * ficarem separadas, a mesa vira um monte de peças soltas — e nenhum teste de
     * sobreposição pegaria isso, porque estar longe demais também não é sobrepor.
     */
    @Test
    fun `cada peca encosta na anterior`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 3, 3 to 5, 5 to 6, 6 to 0, 0 to 4, 4 to 4)
        val mesa = DominoesLayout.table(linha, columns = 6)

        for (i in 1 until mesa.tiles.size) {
            val anterior = mesa.tiles[i - 1]
            val atual = mesa.tiles[i]
            val encosta = tocam(anterior, atual)
            assertTrue(
                encosta,
                "peça $i não encosta na anterior: $anterior / $atual",
            )
        }
    }

    /** Dois retângulos que dividem uma borda, ainda que só em parte. */
    private fun tocam(a: LaidTile, b: LaidTile): Boolean {
        val folga = 0.001f
        val cruzaEmX = a.x < b.x + b.width - folga && b.x < a.x + a.width - folga
        val cruzaEmY = a.y < b.y + b.height - folga && b.y < a.y + a.height - folga

        val coladoNaHorizontal = cruzaEmY &&
            (kotlin.math.abs(a.x + a.width - b.x) < folga || kotlin.math.abs(b.x + b.width - a.x) < folga)
        val coladoNaVertical = cruzaEmX &&
            (kotlin.math.abs(a.y + a.height - b.y) < folga || kotlin.math.abs(b.y + b.height - a.y) < folga)

        return coladoNaHorizontal || coladoNaVertical
    }

    @Test
    fun `mesa estreita ainda acomoda a peca mais comprida`() {
        val mesa = DominoesLayout.table(linha(1 to 2, 2 to 3, 3 to 4), columns = 4)
        for (peca in mesa.tiles) {
            assertTrue(peca.x + peca.width <= mesa.width)
            assertTrue(peca.y + peca.height <= mesa.height)
        }
    }

    @Test
    fun `uma partida inteira cabe na mesa, encostada e sem peca em cima de peca`() {
        // O caso de verdade: a linha crescendo lance a lance, em várias larguras de tela.
        for (columns in listOf(4, 6, 8, 12)) {
            var state = DominoesGame.initialState(MatchConfig(seed = 7))
            var guard = 0

            while (!DominoesGame.outcome(state).isOver && guard++ < 40) {
                val mesa = DominoesLayout.table(state.line, columns)
                assertEquals(state.line.size, mesa.tiles.size, "sumiu peça da mesa")

                for (i in mesa.tiles.indices) {
                    val peca = mesa.tiles[i]
                    assertTrue(
                        peca.x >= 0f && peca.x + peca.width <= mesa.width + 0.001f &&
                            peca.y >= 0f && peca.y + peca.height <= mesa.height + 0.001f,
                        "largura $columns: peça $i saiu da mesa — $peca",
                    )
                    if (i > 0) {
                        assertTrue(
                            tocam(mesa.tiles[i - 1], peca),
                            "largura $columns, lance ${state.ply}: peça $i soltou da linha",
                        )
                    }
                    for (j in i + 1 until mesa.tiles.size) {
                        assertTrue(
                            !sobrepoe(peca, mesa.tiles[j]),
                            "largura $columns, lance ${state.ply}: peças $i e $j se sobrepõem",
                        )
                    }
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
        assertTrue(
            DominoesLayout.columnsFor(50f) >= 4,
            "mesa minúscula ainda precisa caber uma peça deitada mais a curva",
        )
        assertTrue(DominoesLayout.columnsFor(4000f) <= 16, "mesa de tablet não vira peça microscópica")
    }
}
