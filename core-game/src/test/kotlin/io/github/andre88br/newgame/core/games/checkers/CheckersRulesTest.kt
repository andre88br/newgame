package io.github.andre88br.newgame.core.games.checkers

import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheckersRulesTest {

    @Test
    fun `captura e obrigatoria`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . w . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val moves = CheckersGame.legalMoves(state)
        assertEquals(1, moves.size, "só a captura deveria estar disponível, veio $moves")
        assertTrue(moves.single().isCapture)
        assertEquals(listOf(squareAt(4, 3)), moves.single().captured)
        assertEquals(squareAt(3, 4), moves.single().to)
    }

    @Test
    fun `um lance simples e recusado quando existe captura`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . w . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val simple = CheckersMove(from = squareAt(5, 2), path = listOf(squareAt(4, 1)))
        val result = CheckersGame.applyMove(state, simple)
        assertIs<MoveResult.Illegal>(result)
        assertContains(result.reason, "obrigatória")
    }

    @Test
    fun `e obrigatorio capturar o maximo de pecas`() {
        // A pedra em (5,6) captura duas; a de (7,0) captura só uma. Só a dupla é legal.
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . . . . . . .",
            ". . . . . b . .",
            ". . . . . . w .",
            ". b . . . . . .",
            "w . . . . . . .",
        )
        val moves = CheckersGame.legalMoves(state)
        assertEquals(1, moves.size, "esperava só a captura dupla, veio ${moves.map { it.describe() }}")

        val best = moves.single()
        assertEquals(2, best.captured.size)
        assertEquals(squareAt(5, 6), best.from)
        assertEquals(listOf(squareAt(3, 4), squareAt(1, 2)), best.path)
        assertEquals("24x15x6", best.describe())
    }

    @Test
    fun `a captura menor e recusada com a explicacao certa`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . . . . . . .",
            ". . . . . b . .",
            ". . . . . . w .",
            ". b . . . . . .",
            "w . . . . . . .",
        )
        val single = CheckersMove(
            from = squareAt(7, 0),
            path = listOf(squareAt(5, 2)),
            captured = listOf(squareAt(6, 1)),
        )
        val result = CheckersGame.applyMove(state, single)
        assertIs<MoveResult.Illegal>(result)
        assertContains(result.reason, "máximo")
    }

    @Test
    fun `pedra captura para tras`() {
        // A única captura disponível é para trás (a pedra branca anda para cima).
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . w . . . . .",
            ". . . b . . . .",
            ". . . . . . . .",
        )
        val moves = CheckersGame.legalMoves(state)
        assertEquals(1, moves.size)
        assertEquals(listOf(squareAt(6, 3)), moves.single().captured)
        assertEquals(squareAt(7, 4), moves.single().to)
    }

    @Test
    fun `pedra anda so para a frente`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . w . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val destinations = CheckersGame.legalMoves(state).map { it.to }.toSet()
        assertEquals(setOf(squareAt(4, 1), squareAt(4, 3)), destinations)
    }

    @Test
    fun `a dama voa pela diagonal e pousa em qualquer casa livre depois da peca capturada`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . b . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            "W . . . . . . .",
        )
        val moves = CheckersGame.legalMoves(state)
        assertEquals(3, moves.size, "a dama deveria ter três pousos, veio ${moves.map { it.describe() }}")
        assertTrue(moves.all { it.captured == listOf(squareAt(3, 4)) })
        assertEquals(
            setOf(squareAt(2, 5), squareAt(1, 6), squareAt(0, 7)),
            moves.map { it.to }.toSet(),
        )
    }

    @Test
    fun `a dama anda a distancia quando nao ha captura`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            "W . . . . . . .",
        )
        val destinations = CheckersGame.legalMoves(state).map { it.to }.toSet()
        assertEquals(
            setOf(squareAt(6, 1), squareAt(5, 2), squareAt(4, 3), squareAt(3, 4), squareAt(2, 5), squareAt(1, 6), squareAt(0, 7)),
            destinations,
        )
    }

    @Test
    fun `sopro turco - a peca capturada bloqueia e nao pode ser saltada duas vezes`() {
        // A pedra branca dá a volta capturando quatro pretas e volta à casa de origem.
        // Sem a regra do sopro turco a sequência entraria em laço, capturando de novo as
        // mesmas peças.
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . b . b . . .",
            ". . . . . . . .",
            ". . b . b . . .",
            ". . . w . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val moves = CheckersGame.legalMoves(state)

        assertEquals(2, moves.size, "esperava as duas voltas, veio ${moves.map { it.describe() }}")
        moves.forEach { move ->
            assertEquals(4, move.captured.size, "sequência de tamanho errado: ${move.describe()}")
            assertEquals(
                move.captured.size,
                move.captured.distinct().size,
                "a mesma peça foi capturada duas vezes em ${move.describe()}",
            )
            assertEquals(squareAt(4, 3), move.to, "a sequência deveria terminar na casa de origem")
        }
    }

    @Test
    fun `pedra que passa pela ultima fileira no meio da sequencia nao vira dama`() {
        // Mesma posição do teste anterior: o caminho passa por (0,3), fileira de promoção
        // das brancas, mas a sequência continua — então a pedra continua pedra.
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . b . b . . .",
            ". . . . . . . .",
            ". . b . b . . .",
            ". . . w . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val viaPromotionRow = CheckersGame.legalMoves(state)
            .first { squareAt(0, 3) in it.path }

        val after = CheckersGame.applyOrThrow(state, viaPromotionRow)
        assertEquals(WHITE_MAN, after.pieceAt(4, 3), "não deveria ter promovido:\n$after")
        assertEquals(0, after.countKings(Seat.FIRST))
    }

    @Test
    fun `pedra que termina o lance na ultima fileira vira dama`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . w . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val move = CheckersGame.legalMoves(state).first { it.to == squareAt(0, 1) }
        val after = CheckersGame.applyOrThrow(state, move)
        assertEquals(WHITE_KING, after.pieceAt(0, 1))
        assertEquals(1, after.countKings(Seat.FIRST))
    }

    @Test
    fun `a peca preta promove na fileira de baixo`() {
        val state = positionOf(
            Seat.SECOND,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . b . .",
            ". . . . . . . .",
        )
        val move = CheckersGame.legalMoves(state).first { it.to == squareAt(7, 4) }
        val after = CheckersGame.applyOrThrow(state, move)
        assertEquals(BLACK_KING, after.pieceAt(7, 4))
    }

    @Test
    fun `quem fica sem lance perde`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . b . . . . .",
            ". b . . . . . .",
            "w . . . . . . .",
        )
        assertTrue(CheckersGame.legalMoves(state).isEmpty())
        assertEquals(Outcome.Win(Seat.SECOND), CheckersGame.outcome(state))
    }

    @Test
    fun `ficar sem pecas perde`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        assertEquals(Outcome.Win(Seat.SECOND), CheckersGame.outcome(state))
    }

    @Test
    fun `vinte lances de cada lado sem progresso empatam`() {
        val board = boardOf(
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . B . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . W . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val almost = CheckersState(board, Seat.FIRST, ply = 39, idlePlies = CheckersGame.IDLE_PLY_LIMIT - 1)
        assertEquals(Outcome.InProgress, CheckersGame.outcome(almost))

        val reached = almost.copy(idlePlies = CheckersGame.IDLE_PLY_LIMIT)
        assertEquals(Outcome.Draw(DrawReason.NO_PROGRESS), CheckersGame.outcome(reached))
        assertTrue(CheckersGame.legalMoves(reached).isEmpty())
    }

    @Test
    fun `avancar pedra ou capturar zera o contador de inatividade`() {
        val kingsOnly = CheckersState(
            board = boardOf(
                ". . . . . . . .",
                ". . . . . . . .",
                ". . . B . . . .",
                ". . . . . . . .",
                ". . . . . . . .",
                ". . W . . . . .",
                ". . . . . . . .",
                ". . . . w . . .",
            ),
            turn = Seat.FIRST,
            idlePlies = 5,
        )

        val kingMove = CheckersGame.legalMoves(kingsOnly).first { it.from == squareAt(5, 2) }
        assertEquals(6, CheckersGame.applyOrThrow(kingsOnly, kingMove).idlePlies)

        val manMove = CheckersGame.legalMoves(kingsOnly).first { it.from == squareAt(7, 4) }
        assertEquals(0, CheckersGame.applyOrThrow(kingsOnly, manMove).idlePlies)
    }

    @Test
    fun `a notacao PDN numera as casas escuras de 1 a 32`() {
        assertEquals(1, pdnNumber(squareAt(0, 1)))
        assertEquals(4, pdnNumber(squareAt(0, 7)))
        assertEquals(5, pdnNumber(squareAt(1, 0)))
        assertEquals(32, pdnNumber(squareAt(7, 6)))

        val allDark = (0 until BOARD_CELLS).filter { isPlayable(it) }
        assertEquals(32, allDark.size)
        assertEquals((1..32).toSet(), allDark.map { pdnNumber(it) }.toSet())
    }

    @Test
    fun `lances de outra pessoa sao recusados`() {
        val start = CheckersGame.initialState(io.github.andre88br.newgame.core.engine.MatchConfig.DETERMINISTIC)
        val blackMove = CheckersMove(from = squareAt(2, 1), path = listOf(squareAt(3, 0)))
        val result = CheckersGame.applyMove(start, blackMove)
        assertIs<MoveResult.Illegal>(result)
        assertContains(result.reason, "peça sua")
    }
}
