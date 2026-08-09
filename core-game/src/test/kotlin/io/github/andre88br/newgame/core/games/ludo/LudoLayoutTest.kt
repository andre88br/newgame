package io.github.andre88br.newgame.core.games.ludo

import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * O desenho do tabuleiro é geometria, e geometria se verifica. Cada teste aqui corresponde a
 * um erro que, na tela, apareceria como peão andando para o lugar errado — e que ninguém
 * pegaria olhando o código.
 */
class LudoLayoutTest {

    @Test
    fun `a volta tem as 52 casas, todas distintas e dentro da grade`() {
        assertEquals(LUDO_TRACK, LudoLayout.ring.size)
        assertEquals(LUDO_TRACK, LudoLayout.ring.distinct().size, "casa repetida na volta")
        assertTrue(
            LudoLayout.ring.all { it.row in 0 until LUDO_GRID && it.column in 0 until LUDO_GRID },
            "casa fora da grade",
        )
    }

    @Test
    fun `a volta e um circuito fechado sem buraco`() {
        for (index in 0 until LUDO_TRACK) {
            val here = LudoLayout.ring[index]
            val next = LudoLayout.ring[(index + 1) % LUDO_TRACK]
            assertEquals(
                1,
                LudoLayout.stepsBetween(here, next),
                "salto entre a casa $index ($here) e a seguinte ($next)",
            )
        }
    }

    @Test
    fun `a saida de cada cor cai no seu braco da cruz`() {
        assertEquals(LudoLayout.ring[0], LudoLayout.trackCell(startSquare(Seat.FIRST)))
        assertEquals(LudoLayout.ring[26], LudoLayout.trackCell(startSquare(Seat.SECOND)))
        assertNotEquals(
            LudoLayout.trackCell(startSquare(Seat.FIRST)),
            LudoLayout.trackCell(startSquare(Seat.SECOND)),
            "as duas cores não podem sair da mesma casa",
        )
    }

    /**
     * O contrato que liga desenho e regra: depois de dar a volta inteira, a última casa da
     * volta precisa encostar na primeira do corredor final daquela cor. Se não encostar, o
     * peão some de um canto do tabuleiro e reaparece no outro.
     */
    @Test
    fun `o corredor final comeca colado na ultima casa da volta`() {
        for (seat in listOf(Seat.FIRST, Seat.SECOND)) {
            val ultima = LudoLayout.cellFor(seat, LUDO_TRACK - 1, token = 0)
            val primeira = LudoLayout.laneCell(seat, 0)
            assertEquals(
                1,
                LudoLayout.stepsBetween(ultima, primeira),
                "cadeira ${seat.index}: $ultima não encosta em $primeira",
            )
        }
    }

    @Test
    fun `o corredor final anda casa a casa ate a chegada`() {
        for (seat in listOf(Seat.FIRST, Seat.SECOND)) {
            for (step in 0 until LUDO_HOME_LANE) {
                assertEquals(
                    1,
                    LudoLayout.stepsBetween(LudoLayout.laneCell(seat, step), LudoLayout.laneCell(seat, step + 1)),
                    "cadeira ${seat.index}: buraco no corredor no passo $step",
                )
            }
            assertEquals(LudoCellKind.GOAL, LudoLayout.kindOf(LudoLayout.goalCell(seat)))
        }
    }

    @Test
    fun `os dois corredores finais nao se cruzam`() {
        val primeiro = (0..LUDO_HOME_LANE).map { LudoLayout.laneCell(Seat.FIRST, it) }
        val segundo = (0..LUDO_HOME_LANE).map { LudoLayout.laneCell(Seat.SECOND, it) }
        // Só a chegada é vizinha; nenhuma casa é a mesma.
        assertTrue(primeiro.none { it in segundo }, "corredores sobrepostos: $primeiro / $segundo")
    }

    @Test
    fun `cada peao no curral tem lugar so seu`() {
        for (seat in listOf(Seat.FIRST, Seat.SECOND)) {
            val lugares = (0 until LUDO_TOKENS).map { LudoLayout.yardCell(seat, it) }
            assertEquals(LUDO_TOKENS, lugares.distinct().size, "dois peões no mesmo lugar do curral")
            val esperado = if (seat == Seat.FIRST) LudoCellKind.YARD_FIRST else LudoCellKind.YARD_SECOND
            assertTrue(lugares.all { LudoLayout.kindOf(it) == esperado }, "peão fora do próprio curral")
        }
    }

    @Test
    fun `os currais ficam em cantos opostos`() {
        val primeiro = LudoLayout.yardCell(Seat.FIRST, 0)
        val segundo = LudoLayout.yardCell(Seat.SECOND, 0)
        assertTrue(primeiro.row < LUDO_GRID / 2 && primeiro.column < LUDO_GRID / 2)
        assertTrue(segundo.row > LUDO_GRID / 2 && segundo.column > LUDO_GRID / 2)
    }

    @Test
    fun `toda posicao possivel de peao tem uma casa no desenho`() {
        for (seat in listOf(Seat.FIRST, Seat.SECOND)) {
            for (token in 0 until LUDO_TOKENS) {
                val posicoes = listOf(LUDO_YARD) + (0..LUDO_GOAL).toList()
                for (progress in posicoes) {
                    val cell = LudoLayout.cellFor(seat, progress, token)
                    assertTrue(
                        cell.row in 0 until LUDO_GRID && cell.column in 0 until LUDO_GRID,
                        "progresso $progress caiu fora da grade: $cell",
                    )
                    assertTrue(
                        LudoLayout.kindOf(cell) != LudoCellKind.OUTSIDE,
                        "progresso $progress caiu fora da cruz: $cell",
                    )
                }
            }
        }
    }

    @Test
    fun `a cruz nao tem casa solta`() {
        // Toda casa que serve para alguma coisa está dentro da cruz: braços de três casas
        // mais o centro. O contrário — casa da cruz sem serventia — indicaria buraco no
        // desenho.
        for (row in 0 until LUDO_GRID) {
            for (column in 0 until LUDO_GRID) {
                val naCruz = (row in 6..8) || (column in 6..8)
                val kind = LudoLayout.kindOf(LudoCell(row, column))
                if (naCruz) {
                    assertTrue(
                        kind != LudoCellKind.OUTSIDE,
                        "buraco na cruz em ($row,$column)",
                    )
                } else {
                    assertTrue(
                        kind == LudoCellKind.YARD_FIRST ||
                            kind == LudoCellKind.YARD_SECOND ||
                            kind == LudoCellKind.OUTSIDE,
                        "($row,$column) devia ser canto, veio $kind",
                    )
                }
            }
        }
    }

    @Test
    fun `casa segura no desenho e casa segura na regra`() {
        for (absolute in 0 until LUDO_TRACK) {
            val esperado = if (isSafeSquare(absolute)) LudoCellKind.SAFE else LudoCellKind.TRACK
            assertEquals(
                esperado,
                LudoLayout.kindOf(LudoLayout.trackCell(absolute)),
                "casa $absolute discorda entre desenho e regra",
            )
        }
    }

    @Test
    fun `dois peoes na mesma casa absoluta caem no mesmo lugar do desenho`() {
        // Sem isto, uma captura aconteceria com os dois peões desenhados longe um do outro.
        val progressoAdversario = (0 - startSquare(Seat.SECOND) + LUDO_TRACK) % LUDO_TRACK
        assertEquals(
            LudoLayout.cellFor(Seat.FIRST, 0, token = 0),
            LudoLayout.cellFor(Seat.SECOND, progressoAdversario, token = 0),
        )
    }

    @Test
    fun `uma partida inteira nunca desenha peao fora da cruz`() {
        var state = LudoGame.initialState(MatchConfig(seed = 99))
        var guard = 0
        while (!LudoGame.outcome(state).isOver && guard++ < 600) {
            for (seat in listOf(Seat.FIRST, Seat.SECOND)) {
                state.tokensOf(seat).forEachIndexed { token, progress ->
                    val cell = LudoLayout.cellFor(seat, progress, token)
                    assertTrue(
                        LudoLayout.kindOf(cell) != LudoCellKind.OUTSIDE,
                        "peão $token da cadeira ${seat.index} em $progress caiu em $cell",
                    )
                }
            }
            state = LudoGame.applyOrThrow(state, LudoGame.legalMoves(state).first())
        }
    }
}
