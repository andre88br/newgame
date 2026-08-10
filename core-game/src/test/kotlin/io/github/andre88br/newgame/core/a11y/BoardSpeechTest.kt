package io.github.andre88br.newgame.core.a11y

import io.github.andre88br.newgame.core.engine.GameCatalog
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import io.github.andre88br.newgame.core.games.checkers.CheckersState
import io.github.andre88br.newgame.core.games.checkers.positionOf
import io.github.andre88br.newgame.core.games.checkers.squareAt
import io.github.andre88br.newgame.core.games.chess.CHESS_EMPTY
import io.github.andre88br.newgame.core.games.chess.ChessState
import io.github.andre88br.newgame.core.games.chess.chessStateFromFen
import io.github.andre88br.newgame.core.games.chess.squareOf
import io.github.andre88br.newgame.core.games.dominoes.LineEnd
import io.github.andre88br.newgame.core.games.dominoes.Tile
import io.github.andre88br.newgame.core.games.ludo.LUDO_GOAL
import io.github.andre88br.newgame.core.games.ludo.LUDO_YARD
import io.github.andre88br.newgame.core.games.reversi.ReversiGame
import io.github.andre88br.newgame.core.games.reversi.ReversiState
import io.github.andre88br.newgame.core.games.tictactoe.EMPTY_CELL
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeGame
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeState
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeMove
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Descrição de acessibilidade errada é o defeito mais fácil de não descobrir nunca: quem
 * escreve o código não usa leitor de tela, e a tela continua bonita. Estes testes são o
 * único lugar em que ela é conferida.
 */
class BoardSpeechTest {

    private fun entry(id: GameId) = GameCatalog.entry(id)

    // -------- xadrez --------

    @Test
    fun `no xadrez a casa e falada pelo nome que quem joga usa`() {
        val state = chessStateFromFen("8/8/8/8/8/8/4P3/4K2k w - - 0 1")
        val e2 = BoardSpeech.square(entry(GameId.CHESS), state, squareOf("e2")!!)

        assertNotNull(e2)
        assertEquals("e2", e2.name)
        assertEquals(SpeechKey.PAWN_WHITE, e2.occupant)
        assertEquals("e2, peão branco", e2.text())
    }

    @Test
    fun `casa vazia e falada como vazia`() {
        val state = chessStateFromFen("8/8/8/8/8/8/4P3/4K2k w - - 0 1")
        val d5 = BoardSpeech.square(entry(GameId.CHESS), state, squareOf("d5")!!)

        assertNotNull(d5)
        assertNull(d5.occupant)
        assertEquals("d5, vazia", d5.text())
    }

    @Test
    fun `cada peca do xadrez tem nome proprio nas duas cores`() {
        val state = chessStateFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
        val faladas = listOf(
            "a1" to SpeechKey.ROOK_WHITE,
            "b1" to SpeechKey.KNIGHT_WHITE,
            "c1" to SpeechKey.BISHOP_WHITE,
            "d1" to SpeechKey.QUEEN_WHITE,
            "e1" to SpeechKey.CHESS_KING_WHITE,
            "a2" to SpeechKey.PAWN_WHITE,
            "a8" to SpeechKey.ROOK_BLACK,
            "d8" to SpeechKey.QUEEN_BLACK,
            "e8" to SpeechKey.CHESS_KING_BLACK,
            "a7" to SpeechKey.PAWN_BLACK,
        )
        for ((casa, esperado) in faladas) {
            val fala = BoardSpeech.square(entry(GameId.CHESS), state, squareOf(casa)!!)
            assertEquals(esperado, fala?.occupant, "a casa $casa foi falada errado")
        }
    }

    // -------- damas --------

    @Test
    fun `nas damas a casa e falada pelo numero que se usa para ditar lance`() {
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
        val fala = BoardSpeech.square(entry(GameId.CHECKERS), state, squareAt(5, 2))

        assertNotNull(fala)
        assertEquals(SpeechKey.MAN_WHITE, fala.occupant)
        // A numeração PDN das damas conta só as casas escuras.
        assertEquals("22", fala.name)
    }

    @Test
    fun `a dama e falada como dama, e nao como pedra`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . W . . . . .",
            ". . . . . b . .",
            ". . . . . . . .",
        )
        assertEquals(
            SpeechKey.KING_WHITE,
            BoardSpeech.square(entry(GameId.CHECKERS), state, squareAt(5, 2))?.occupant,
        )
        assertEquals(
            SpeechKey.MAN_BLACK,
            BoardSpeech.square(entry(GameId.CHECKERS), state, squareAt(6, 5))?.occupant,
        )
    }

    // -------- reversi --------

    /**
     * O caso que motivou nomear as chaves pela cor e não pela cadeira: no reversi a primeira
     * cadeira é a **preta**, ao contrário das damas e do xadrez. Falar "branca" aqui
     * contradiria o que está desenhado na tela.
     */
    @Test
    fun `no reversi a primeira cadeira e falada como preta`() {
        val state = ReversiGame.initialState(MatchConfig.DETERMINISTIC)
        val casas = (0 until 64).mapNotNull { BoardSpeech.square(entry(GameId.REVERSI), state, it) }

        val pretas = casas.count { it.occupant == SpeechKey.DISC_DARK }
        val brancas = casas.count { it.occupant == SpeechKey.DISC_LIGHT }
        assertEquals(2, pretas, "o reversi começa com duas de cada")
        assertEquals(2, brancas)

        // E a cadeira que abre é a que tem as pretas.
        assertEquals(Seat.FIRST, state.turn)
    }

    // -------- jogo da velha --------

    @Test
    fun `na velha as marcas sao faladas como xis e bola`() {
        var state = TicTacToeGame.initialState(MatchConfig.DETERMINISTIC)
        state = TicTacToeGame.applyOrThrow(state, TicTacToeMove(0))
        state = TicTacToeGame.applyOrThrow(state, TicTacToeMove(4))

        assertEquals(SpeechKey.MARK_X, BoardSpeech.square(entry(GameId.TIC_TAC_TOE), state, 0)?.occupant)
        assertEquals(SpeechKey.MARK_O, BoardSpeech.square(entry(GameId.TIC_TAC_TOE), state, 4)?.occupant)
        assertNull(BoardSpeech.square(entry(GameId.TIC_TAC_TOE), state, 8)?.occupant)
    }

    // -------- dominó --------

    @Test
    fun `a peca do domino e falada com as duas metades e o que da para fazer`() {
        val peca = Tile(2, 5)
        assertEquals(
            "peça 2 por 5, encaixa nas duas pontas",
            BoardSpeech.tile(peca, listOf(LineEnd.LEFT, LineEnd.RIGHT)).text(),
        )
        assertEquals(
            "peça 2 por 5, encaixa na ponta direita",
            BoardSpeech.tile(peca, listOf(LineEnd.RIGHT)).text(),
        )
        assertEquals(
            "peça 2 por 5, não encaixa",
            BoardSpeech.tile(peca, emptyList()).text(),
        )
    }

    @Test
    fun `a peca virada nao entrega o valor nem para o leitor de tela`() {
        val fala = BoardSpeech.tile(Tile.HIDDEN, listOf(LineEnd.LEFT))
        assertEquals(SpeechKey.TILE_HIDDEN, fala.key)
        assertTrue(fala.args.isEmpty(), "a peça oculta não pode levar valor nenhum junto")
    }

    // -------- ludo --------

    @Test
    fun `o peao do ludo e falado pela situacao em que esta`() {
        assertEquals("peão 1, no curral", BoardSpeech.token(0, LUDO_YARD).text())
        assertEquals("peão 2, na chegada", BoardSpeech.token(1, LUDO_GOAL).text())
        assertEquals("peão 3, andou 10 casas", BoardSpeech.token(2, 10).text())
        assertEquals(
            "peão 4, no corredor final, 2 casas para chegar",
            BoardSpeech.token(3, LUDO_GOAL - 2).text(),
        )
    }

    // -------- vale para todo jogo --------

    @Test
    fun `todo jogo de grade descreve todas as suas casas`() {
        for (entry in GameCatalog.available) {
            val interactor = entry.interactor ?: continue
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            for (square in 0 until interactor.rows * interactor.columns) {
                val fala = BoardSpeech.square(entry, state, square)
                assertNotNull(fala, "${entry.id}: a casa $square ficou sem descrição")
                assertTrue(fala.name.isNotBlank(), "${entry.id}: casa $square sem nome")
                assertTrue(fala.text().isNotBlank(), "${entry.id}: casa $square sem frase")
            }
        }
    }

    @Test
    fun `duas casas diferentes nunca tem o mesmo nome`() {
        for (entry in GameCatalog.available) {
            val interactor = entry.interactor ?: continue
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            val nomes = (0 until interactor.rows * interactor.columns)
                .filter { interactor.isPlayable(it) }
                .mapNotNull { BoardSpeech.square(entry, state, it)?.name }
            assertEquals(
                nomes.size,
                nomes.distinct().size,
                "${entry.id}: duas casas com o mesmo nome falado",
            )
        }
    }

    /** Quantas peças o próprio estado diz ter, sem passar pela fala. */
    private fun piecesIn(state: GameState): Int = when (state) {
        is TicTacToeState -> state.cells.count { it != EMPTY_CELL }
        is CheckersState -> state.countPieces(Seat.FIRST) + state.countPieces(Seat.SECOND)
        is ReversiState -> state.count(Seat.FIRST) + state.count(Seat.SECOND)
        is ChessState -> state.board.count { it != CHESS_EMPTY }
        else -> error("estado sem contagem de peças: ${state::class.simpleName}")
    }

    @Test
    fun `o que a fala diz bate com o que o motor ve`() {
        // A fala precisa acompanhar o tabuleiro, não uma cópia dele. A contagem sai do
        // estado por um caminho e da descrição por outro; se as duas divergirem em qualquer
        // lance, alguma casa está sendo descrita errado.
        for (entry in GameCatalog.available) {
            val interactor = entry.interactor ?: continue
            var state = entry.rules.initialState(MatchConfig.DETERMINISTIC)

            repeat(8) {
                val faladas = (0 until interactor.rows * interactor.columns)
                    .count { BoardSpeech.square(entry, state, it)?.occupant != null }
                assertEquals(
                    piecesIn(state),
                    faladas,
                    "${entry.id} no lance ${state.ply}: a fala achou $faladas peças",
                )
                val move = entry.rules.legalMoves(state).firstOrNull() ?: return@repeat
                state = entry.rules.applyOrThrow(state, move)
            }
        }
    }
}
