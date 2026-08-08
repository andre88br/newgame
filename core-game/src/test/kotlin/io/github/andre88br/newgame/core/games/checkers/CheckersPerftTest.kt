package io.github.andre88br.newgame.core.games.checkers

import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Seat
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `perft` — contagem de folhas da árvore de lances a uma dada profundidade. É o teste que
 * pega os erros que um punhado de posições escolhidas a dedo deixa passar: uma diagonal
 * que dá a volta na borda, uma sequência de captura que termina cedo demais, uma peça
 * capturada que sai do tabuleiro antes da hora.
 *
 * A parte que vale mais está em [gerador reproduz o perft publicado das damas inglesas]:
 * desligando as duas regras específicas do jogo brasileiro (captura para trás e obrigação
 * de capturar o máximo), a contagem tem que bater com valores publicados, que não saíram
 * daqui. Se o resto da máquina tivesse um erro, esses números não fechariam.
 */
class CheckersPerftTest {

    private val start = CheckersGame.initialState(MatchConfig.DETERMINISTIC)

    @Test
    fun `gerador reproduz o perft publicado das damas inglesas`() {
        // Valores clássicos do perft de damas 8x8 na regra inglesa.
        val published = listOf(7L, 49L, 302L, 1_469L, 7_361L, 36_768L)
        published.forEachIndexed { index, expected ->
            val depth = index + 1
            assertEquals(expected, perft(start, depth, CheckersMoves.Variant.ENGLISH), "perft($depth)")
        }
    }

    @Test
    fun `perft das regras brasileiras`() {
        // Até a profundidade 4 as regras coincidem com a inglesa: ainda não surgiu captura
        // para trás nem escolha entre capturas de tamanhos diferentes.
        assertEquals(7L, perft(start, 1))
        assertEquals(49L, perft(start, 2))
        assertEquals(302L, perft(start, 3))
        assertEquals(1_469L, perft(start, 4))

        // A partir daqui as regras divergem. Estes números são a referência de regressão
        // desta implementação: mudaram sem motivo, alguma regra foi alterada sem querer.
        assertEquals(7_473L, perft(start, 5))
        assertEquals(37_628L, perft(start, 6))
    }

    @Test
    fun `os dois lados abrem com sete lances`() {
        assertEquals(7, CheckersGame.legalMoves(start).size)
        assertEquals(7, CheckersGame.legalMoves(start.copy(turn = Seat.SECOND)).size)
    }

    @Test
    fun `a posicao inicial tem doze pedras de cada lado e nenhuma dama`() {
        assertEquals(12, start.countPieces(Seat.FIRST))
        assertEquals(12, start.countPieces(Seat.SECOND))
        assertEquals(0, start.countKings(Seat.FIRST))
        assertEquals(0, start.countKings(Seat.SECOND))
    }

    @Test
    fun `todas as pecas comecam em casas escuras`() {
        (0 until BOARD_CELLS)
            .filterNot { isPlayable(it) }
            .forEach { assertEquals(EMPTY, start.board[it], "casa clara $it ocupada") }
    }
}
