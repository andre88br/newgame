package io.github.andre88br.newgame.core.games.chess

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChessAiTest {

    private fun at(name: String) = squareOf(name)!!

    @Test
    fun `no dificil a IA acha o mate em um`() {
        // Dama em a1 com a coluna livre, rei branco em g6 cobrindo a fuga: Da8 é mate.
        // A dama precisa estar fora da coluna g — em g1 o próprio rei bloquearia o caminho.
        val state = chessStateFromFen("6k1/8/6K1/8/8/8/8/Q7 w - - 0 1")
        val move = ChessAi.chooseMove(state, Difficulty.HARD, seed = 1L)

        assertNotNull(move)
        val after = ChessGame.applyOrThrow(state, move)
        assertEquals(
            Outcome.Win(Seat.FIRST),
            ChessGame.outcome(after),
            "esperava mate, veio ${move.describe()}\n$after",
        )
    }

    @Test
    fun `a IA captura a dama de graca`() {
        // A dama preta em d4 está atacada pela torre branca em d1 e não é defendida.
        val state = chessStateFromFen("4k3/8/8/8/3q4/8/8/3RK3 w - - 0 1")
        val move = ChessAi.chooseMove(state, Difficulty.HARD, seed = 1L)

        assertNotNull(move)
        assertEquals(at("d4"), move.to, "esperava a captura da dama, veio ${move.describe()}")
    }

    /**
     * O caso que justifica a busca de quiescência existir. Sem ela, a busca para logo
     * depois da primeira captura, vê "ganhei um peão" e não enxerga a recaptura que vem
     * em seguida.
     */
    @Test
    fun `a IA nao entra numa troca em que sai perdendo`() {
        // O peão preto em d5 está defendido pelo peão em c6. Capturar com a dama perde a dama.
        val state = chessStateFromFen("4k3/8/2p5/3p4/8/8/3Q4/4K3 w - - 0 1")
        val move = ChessAi.chooseMove(state, Difficulty.HARD, seed = 1L)

        assertNotNull(move)
        assertTrue(
            move.to != at("d5"),
            "a dama capturou um peão defendido e vai ser recapturada: ${move.describe()}",
        )
    }

    @Test
    fun `a IA escapa do xeque`() {
        val state = chessStateFromFen("4r3/8/8/8/8/8/8/4K3 w - - 0 1")
        val move = ChessAi.chooseMove(state, Difficulty.HARD, seed = 1L)

        assertNotNull(move)
        val after = ChessGame.applyOrThrow(state, move)
        assertTrue(!after.inCheck(Seat.FIRST), "o rei continuou em xeque depois de ${move.describe()}")
    }

    /**
     * Testa que a IA **chega** à promoção, não que ela promova neste lance exato.
     *
     * A primeira versão deste teste exigia a promoção imediata e reprovava um lance de rei
     * — que estava certo: com peão em e7 e rei preto no canto, adiantar o rei e promover
     * depois também ganha, e a busca chegou a achar mate por esse caminho. Exigir um lance
     * específico onde vários vencem testa o gosto da IA, não a regra.
     */
    @Test
    fun `a IA leva o peao ate a promocao`() {
        var state = chessStateFromFen("8/4P3/8/8/8/8/8/4K2k w - - 0 1")

        repeat(6) {
            if (ChessGame.outcome(state).isOver) return@repeat
            val move = if (state.turn == Seat.FIRST) {
                ChessAi.chooseMove(state, Difficulty.HARD, seed = 1L)
            } else {
                // As pretas jogam o primeiro lance legal: o que importa é a conduta das brancas.
                ChessGame.legalMoves(state).firstOrNull()
            }
            assertNotNull(move)
            state = ChessGame.applyOrThrow(state, move)
        }

        assertTrue(
            state.board.contains('Q'),
            "as brancas não promoveram em seis meios-lances:\n$state",
        )
    }

    @Test
    fun `todos os niveis devolvem lance legal`() {
        val state = ChessGame.initialState(MatchConfig.DETERMINISTIC)
        val legal = ChessGame.legalMoves(state)
        for (difficulty in Difficulty.entries) {
            for (seed in 1L..3L) {
                val move = ChessAi.chooseMove(state, difficulty, seed)
                assertTrue(move in legal, "$difficulty com semente $seed devolveu $move")
            }
        }
    }

    @Test
    fun `a IA respeita o orcamento de tempo na posicao inicial`() {
        val state = ChessGame.initialState(MatchConfig.DETERMINISTIC)
        val before = System.nanoTime()
        ChessAi.chooseMove(state, Difficulty.HARD, seed = 1L)
        val elapsed = (System.nanoTime() - before) / 1_000_000

        // O orçamento do difícil é 3,5 s; a margem cobre a profundidade que ainda estava
        // rodando quando o tempo acabou, além de máquinas lentas.
        assertTrue(elapsed < 20_000, "a busca levou ${elapsed}ms")
    }

    @Test
    fun `uma sequencia de lances da IA se mantem legal`() {
        var state = ChessGame.initialState(MatchConfig.DETERMINISTIC)
        repeat(12) { turn ->
            if (ChessGame.outcome(state).isOver) return@repeat
            val move = ChessAi.chooseMove(state, Difficulty.EASY, seed = turn.toLong())
            assertNotNull(move, "sem lance com a partida em andamento:\n$state")
            assertTrue(move in ChessGame.legalMoves(state), "lance ilegal: ${move.describe()}")
            state = ChessGame.applyOrThrow(state, move)
        }
    }
}
