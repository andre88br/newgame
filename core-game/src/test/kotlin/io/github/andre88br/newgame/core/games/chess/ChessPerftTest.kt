package io.github.andre88br.newgame.core.games.chess

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `perft` do xadrez contra as posições de referência do Chess Programming Wiki.
 *
 * É o único jeito honesto de afirmar que a geração de lances está correta. Cada posição foi
 * escolhida por quem as publicou justamente para pegar uma classe de erro:
 *
 * - **Inicial** — o básico.
 * - **Kiwipete** — roque dos dois lados, com o rei e as torres sob pressão.
 * - **Posição 3** — en passant, promoção e xeques em final de peões e torre.
 * - **Posição 4** — promoção com captura e roque perdido por torre capturada.
 * - **Posição 5** — a que costuma pegar quem esqueceu de invalidar o en passant.
 *
 * Os números não saíram deste projeto. Se um deles não bater, a regra está errada aqui.
 */
class ChessPerftTest {

    private fun perft(state: ChessState, depth: Int): Long {
        if (depth == 0) return 1
        val moves = ChessGame.legalMoves(state)
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(ChessGame.applyKnownLegal(state, it), depth - 1) }
    }

    private fun check(fen: String, expected: List<Long>) {
        val state = chessStateFromFen(fen)
        expected.forEachIndexed { index, count ->
            val depth = index + 1
            assertEquals(count, perft(state, depth), "perft($depth) em $fen\n$state")
        }
    }

    @Test
    fun `posicao inicial`() {
        check(CHESS_START_FEN, listOf(20L, 400L, 8_902L, 197_281L, 4_865_609L))
    }

    @Test
    fun `kiwipete cobre os dois roques`() {
        check(
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            listOf(48L, 2_039L, 97_862L, 4_085_603L),
        )
    }

    @Test
    fun `posicao 3 cobre en passant e promocao`() {
        check(
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            listOf(14L, 191L, 2_812L, 43_238L, 674_624L),
        )
    }

    @Test
    fun `posicao 4 cobre promocao com captura`() {
        check(
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            listOf(6L, 264L, 9_467L, 422_333L),
        )
    }

    @Test
    fun `posicao 5 pega o en passant mal invalidado`() {
        check(
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            listOf(44L, 1_486L, 62_379L, 2_103_487L),
        )
    }

    @Test
    fun `o FEN sobrevive a ida e volta`() {
        val fens = listOf(
            CHESS_START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
        )
        for (fen in fens) {
            assertEquals(fen, chessStateFromFen(fen).toFen())
        }
    }
}
