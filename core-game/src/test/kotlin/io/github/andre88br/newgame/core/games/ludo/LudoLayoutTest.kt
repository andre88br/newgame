package io.github.andre88br.newgame.core.games.ludo

import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O desenho do tabuleiro é geometria, e geometria se verifica. Cada teste aqui corresponde a
 * um erro que, na tela, apareceria como peão andando para o lugar errado — e que ninguém
 * pegaria olhando o código.
 */
class LudoLayoutTest {

    /** As mesas possíveis: as que o próprio ludo aceita, de dois a quatro. */
    private val mesas = LudoGame.supportedSeats

    private fun cadeiras(seats: Int) = (0 until seats).map { Seat(it) }

    // -------- a volta --------

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
    fun `os quatro bracos saem de casas igualmente espacadas`() {
        val saidas = (0 until LUDO_ARMS).map { arm -> arm * (LUDO_TRACK / LUDO_ARMS) }
        assertEquals(listOf(0, 13, 26, 39), saidas)
        assertTrue(saidas.all { isSafeSquare(it) }, "casa de saída precisa ser segura")
    }

    @Test
    fun `cada saida da volta pertence ao braco de quem sai dali`() {
        for (arm in 0 until LUDO_ARMS) {
            val saida = arm * (LUDO_TRACK / LUDO_ARMS)
            assertEquals(arm, LudoLayout.startArmAt(saida), "a saída $saida não é do braço $arm")
        }
        // E casa comum não é saída de ninguém: pintá-la de alguma cor seria mentira.
        val comuns = (0 until LUDO_TRACK).filter { it % (LUDO_TRACK / LUDO_ARMS) != 0 }
        assertTrue(comuns.all { LudoLayout.startArmAt(it) == -1 }, "casa comum virou saída")
    }

    @Test
    fun `a saida de cada cadeira e a saida do braco dela`() {
        for (seats in mesas) {
            for (seat in cadeiras(seats)) {
                assertEquals(
                    armOf(seat, seats),
                    LudoLayout.startArmAt(startSquare(seat, seats)),
                    "mesa de $seats: a cadeira ${seat.index} sai de casa que não é do braço dela",
                )
            }
        }
    }

    // -------- quem senta em qual braço --------

    /**
     * A partida de dois usa braços **opostos**. Em braços vizinhos, a saída de um ficaria a
     * treze casas da do outro em vez de vinte e seis, e um dos dois passaria a partida
     * inteira andando na frente do adversário.
     */
    @Test
    fun `a mesa de dois usa bracos opostos`() {
        assertEquals(0, armOf(Seat.FIRST, 2))
        assertEquals(2, armOf(Seat.SECOND, 2))

        val distancia = startSquare(Seat.SECOND, 2) - startSquare(Seat.FIRST, 2)
        assertEquals(LUDO_TRACK / 2, distancia, "as saídas precisam ficar em lados opostos")
    }

    /**
     * `firstArm` gira a mesa inteira para a pessoa poder escolher qualquer uma das quatro
     * cores, mesmo numa mesa de dois — sem ele só dava para jogar de vermelho ou amarelo.
     * O giro não pode estragar o espaçamento: as cadeiras continuam nos mesmos braços
     * relativos entre si, só o ponto de partida muda.
     */
    @Test
    fun `firstArm gira a mesa sem estragar o espacamento entre cadeiras`() {
        for (firstArm in 0 until LUDO_ARMS) {
            for (seats in mesas) {
                val bracos = cadeiras(seats).map { armOf(it, seats, firstArm) }
                assertEquals(seats, bracos.distinct().size, "mesa de $seats, giro $firstArm: braços repetidos")
                assertEquals(firstArm, bracos.first(), "a cadeira zero fica no braço escolhido")
            }

            // Na mesa de dois o giro não pode transformar braços opostos em vizinhos.
            val distancia = startSquare(Seat.SECOND, 2, firstArm) - startSquare(Seat.FIRST, 2, firstArm)
            assertEquals(
                LUDO_TRACK / 2,
                Math.floorMod(distancia, LUDO_TRACK),
                "giro $firstArm: as saídas da mesa de dois deixaram de ficar opostas",
            )
        }
    }

    @Test
    fun `as mesas de tres e quatro ocupam bracos em ordem`() {
        for (seats in 3..LUDO_ARMS) {
            val bracos = cadeiras(seats).map { armOf(it, seats) }
            assertEquals((0 until seats).toList(), bracos, "mesa de $seats sentou fora de ordem")
        }
    }

    @Test
    fun `duas cadeiras nunca partem da mesma casa`() {
        for (seats in mesas) {
            val saidas = cadeiras(seats).map { startSquare(it, seats) }
            assertEquals(seats, saidas.distinct().size, "mesa de $seats: saídas repetidas em $saidas")
        }
    }

    // -------- corredor final --------

    /**
     * O contrato que liga desenho e regra: depois de dar a volta inteira, a última casa da
     * volta precisa encostar na primeira do corredor final daquela cor. Se não encostar, o
     * peão some de um canto do tabuleiro e reaparece no outro.
     */
    @Test
    fun `o corredor final comeca colado na ultima casa da volta`() {
        for (seats in mesas) {
            for (seat in cadeiras(seats)) {
                val ultima = LudoLayout.cellFor(seat, LUDO_TRACK - 1, token = 0, seats = seats)
                val primeira = LudoLayout.laneCell(armOf(seat, seats), 0)
                assertEquals(
                    1,
                    LudoLayout.stepsBetween(ultima, primeira),
                    "mesa de $seats, cadeira ${seat.index}: $ultima não encosta em $primeira",
                )
            }
        }
    }

    @Test
    fun `o corredor final anda casa a casa ate a chegada`() {
        for (arm in 0 until LUDO_ARMS) {
            for (step in 0 until LUDO_HOME_LANE) {
                assertEquals(
                    1,
                    LudoLayout.stepsBetween(LudoLayout.laneCell(arm, step), LudoLayout.laneCell(arm, step + 1)),
                    "braço $arm: buraco no corredor no passo $step",
                )
            }
            assertEquals(LudoCellKind.GOAL, LudoLayout.kindOf(LudoLayout.goalCell(arm)))
        }
    }

    @Test
    fun `os quatro corredores finais nao se cruzam`() {
        val corredores = (0 until LUDO_ARMS).map { arm ->
            (0 until LUDO_HOME_LANE).map { LudoLayout.laneCell(arm, it) }
        }
        val todas = corredores.flatten()
        assertEquals(
            todas.size,
            todas.distinct().size,
            "corredores sobrepostos: $corredores",
        )
    }

    // -------- currais --------

    @Test
    fun `cada peao no curral tem lugar so seu`() {
        for (arm in 0 until LUDO_ARMS) {
            val lugares = (0 until LUDO_TOKENS).map { LudoLayout.yardCell(arm, it) }
            assertEquals(LUDO_TOKENS, lugares.distinct().size, "dois peões no mesmo lugar do curral")
            assertTrue(
                lugares.all { LudoLayout.kindOf(it) == LudoCellKind.YARD },
                "braço $arm: peão fora de curral",
            )
            assertTrue(lugares.all { LudoLayout.armAt(it) == arm }, "braço $arm: peão no curral alheio")
        }
    }

    @Test
    fun `os quatro currais ficam em cantos diferentes`() {
        val cantos = (0 until LUDO_ARMS).map { LudoLayout.yardCell(it, 0) }
        assertEquals(LUDO_ARMS, cantos.distinct().size)
        // Em cima ou embaixo, à esquerda ou à direita: os quatro cantos, um para cada braço.
        val quadrantes = cantos.map { Pair(it.row < LUDO_GRID / 2, it.column < LUDO_GRID / 2) }
        assertEquals(LUDO_ARMS, quadrantes.distinct().size, "dois currais no mesmo canto: $cantos")
    }

    @Test
    fun `na mesa de dois os currais ficam em cantos opostos`() {
        val primeiro = LudoLayout.yardCell(armOf(Seat.FIRST, 2), 0)
        val segundo = LudoLayout.yardCell(armOf(Seat.SECOND, 2), 0)
        assertTrue(primeiro.row < LUDO_GRID / 2 && primeiro.column < LUDO_GRID / 2)
        assertTrue(segundo.row > LUDO_GRID / 2 && segundo.column > LUDO_GRID / 2)
    }

    // -------- o desenho inteiro --------

    @Test
    fun `toda posicao possivel de peao tem uma casa no desenho`() {
        for (seats in mesas) {
            for (seat in cadeiras(seats)) {
                for (token in 0 until LUDO_TOKENS) {
                    for (progress in listOf(LUDO_YARD) + (0..LUDO_GOAL).toList()) {
                        val cell = LudoLayout.cellFor(seat, progress, token, seats)
                        assertTrue(
                            cell.row in 0 until LUDO_GRID && cell.column in 0 until LUDO_GRID,
                            "mesa de $seats: progresso $progress caiu fora da grade: $cell",
                        )
                        assertTrue(
                            LudoLayout.kindOf(cell) != LudoCellKind.OUTSIDE,
                            "mesa de $seats: progresso $progress caiu fora da cruz: $cell",
                        )
                    }
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
                    assertTrue(kind != LudoCellKind.OUTSIDE, "buraco na cruz em ($row,$column)")
                } else {
                    assertEquals(
                        LudoCellKind.YARD,
                        kind,
                        "($row,$column) devia ser canto de curral, veio $kind",
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
        for (seats in mesas) {
            for (outra in cadeiras(seats).drop(1)) {
                val progresso = (0 - startSquare(outra, seats) + LUDO_TRACK) % LUDO_TRACK
                assertEquals(
                    LudoLayout.cellFor(Seat.FIRST, 0, token = 0, seats = seats),
                    LudoLayout.cellFor(outra, progresso, token = 0, seats = seats),
                    "mesa de $seats, cadeira ${outra.index}: mesma casa absoluta, lugares diferentes",
                )
            }
        }
    }

    @Test
    fun `uma partida inteira nunca desenha peao fora da cruz`() {
        for (seats in mesas) {
            var state = LudoGame.initialState(MatchConfig(seed = 99, seats = seats))
            var guard = 0
            while (!LudoGame.outcome(state).isOver && guard++ < 600) {
                for (index in 0 until seats) {
                    val seat = Seat(index)
                    state.tokensOf(seat).forEachIndexed { token, progress ->
                        val cell = LudoLayout.cellFor(seat, progress, token, seats)
                        assertTrue(
                            LudoLayout.kindOf(cell) != LudoCellKind.OUTSIDE,
                            "mesa de $seats: peão $token da cadeira $index em $progress caiu em $cell",
                        )
                    }
                }
                state = LudoGame.applyOrThrow(state, LudoGame.legalMoves(state).first())
            }
        }
    }
}
