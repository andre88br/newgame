package io.github.andre88br.newgame.core.games.chess

import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * O `perft` já garante que a **geração** de lances está certa. Aqui ficam as regras que ele
 * não cobre: como a partida termina, o que acontece com o tabuleiro depois de um lance
 * especial e o que o motor responde quando alguém tenta o impossível.
 */
class ChessRulesTest {

    private fun at(name: String) = squareOf(name)!!

    private fun move(from: String, to: String, promotion: Char? = null) =
        ChessMove(at(from), at(to), promotion)

    private val start = ChessGame.initialState(MatchConfig.DETERMINISTIC)

    // -------- fim de partida --------

    @Test
    fun `mate do pastor termina a partida`() {
        // Posição depois de 1.e4 e5 2.Bc4 Nc6 3.Qh5 Nf6?? 4.Qxf7#
        val state = chessStateFromFen(
            "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4",
        )
        assertTrue(state.inCheck(Seat.SECOND))
        assertTrue(ChessGame.legalMoves(state).isEmpty())
        assertEquals(Outcome.Win(Seat.FIRST), ChessGame.outcome(state))
    }

    @Test
    fun `afogamento e empate, nao derrota`() {
        // Rei preto no canto sem lance nenhum, mas sem estar em xeque.
        val state = chessStateFromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertFalse(state.inCheck(Seat.SECOND), "o rei não pode estar em xeque no afogamento")
        assertTrue(ChessGame.legalMoves(state).isEmpty())
        assertEquals(Outcome.Draw(DrawReason.STALEMATE), ChessGame.outcome(state))
    }

    @Test
    fun `cinquenta lances sem progresso empatam`() {
        val quase = chessStateFromFen("8/8/4k3/8/8/4K3/8/4R3 w - - 99 80")
        assertEquals(Outcome.InProgress, ChessGame.outcome(quase))

        val atingido = quase.copy(halfmoveClock = ChessGame.HALFMOVE_LIMIT)
        assertEquals(Outcome.Draw(DrawReason.NO_PROGRESS), ChessGame.outcome(atingido))
    }

    @Test
    fun `rei contra rei e empate por material insuficiente`() {
        val state = chessStateFromFen("8/8/4k3/8/8/4K3/8/8 w - - 0 1")
        assertEquals(Outcome.Draw(DrawReason.INSUFFICIENT_MATERIAL), ChessGame.outcome(state))
    }

    @Test
    fun `rei e bispo contra rei e empate`() {
        val state = chessStateFromFen("8/8/4k3/8/8/4KB2/8/8 w - - 0 1")
        assertEquals(Outcome.Draw(DrawReason.INSUFFICIENT_MATERIAL), ChessGame.outcome(state))
    }

    @Test
    fun `bispos de cores diferentes ainda nao e empate automatico`() {
        // Bispos em casas de cores opostas: as regras não declaram empate automático.
        val state = chessStateFromFen("8/8/3bk3/8/8/4KB2/8/8 w - - 0 1")
        assertEquals(Outcome.InProgress, ChessGame.outcome(state))
    }

    @Test
    fun `um peao no tabuleiro impede o empate por material`() {
        val state = chessStateFromFen("8/8/4k3/8/8/4K3/4P3/8 w - - 0 1")
        assertEquals(Outcome.InProgress, ChessGame.outcome(state))
    }

    // -------- lances especiais --------

    @Test
    fun `o roque pequeno leva a torre junto`() {
        val state = chessStateFromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val after = ChessGame.applyOrThrow(state, move("e1", "g1"))

        assertEquals('K', after.pieceAt(at("g1")))
        assertEquals('R', after.pieceAt(at("f1")), "a torre precisa pular junto")
        assertEquals(CHESS_EMPTY, after.pieceAt(at("h1")))
        assertEquals(CHESS_EMPTY, after.pieceAt(at("e1")))
        assertEquals("kq", after.castling, "as brancas perdem os dois direitos")
    }

    @Test
    fun `o roque grande leva a torre junto`() {
        val state = chessStateFromFen("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1")
        val after = ChessGame.applyOrThrow(state, move("e8", "c8"))

        assertEquals('k', after.pieceAt(at("c8")))
        assertEquals('r', after.pieceAt(at("d8")))
        assertEquals("KQ", after.castling)
    }

    @Test
    fun `nao se roca saindo do xeque`() {
        // Torre preta em e8 ataca o rei branco na coluna e.
        val state = chessStateFromFen("4r3/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertTrue(state.inCheck(Seat.FIRST))
        assertFalse(ChessGame.legalMoves(state).any { it.from == at("e1") && it.to == at("g1") })
        assertFalse(ChessGame.legalMoves(state).any { it.from == at("e1") && it.to == at("c1") })
    }

    @Test
    fun `nao se roca passando por casa atacada`() {
        // Torre preta em f8 ataca f1, casa por onde o rei passaria no roque pequeno.
        val state = chessStateFromFen("5r2/8/8/8/8/8/8/R3K2R w KQ - 0 1")
        assertFalse(state.inCheck(Seat.FIRST), "o rei em si não está atacado")
        assertFalse(
            ChessGame.legalMoves(state).any { it.from == at("e1") && it.to == at("g1") },
            "o roque pequeno passa por f1, que está atacada",
        )
        assertTrue(
            ChessGame.legalMoves(state).any { it.from == at("e1") && it.to == at("c1") },
            "o roque grande continua permitido",
        )
    }

    @Test
    fun `torre capturada no canto tira o direito de roque`() {
        // Bispo branco em a3 captura a torre preta em... monta-se direto: torre em h8 cai.
        val state = chessStateFromFen("rnbqk2r/pppppppp/8/8/8/8/PPPPPPPP/RNBQK2R w KQkq - 0 1")
        val comTorreH8Capturada = state.copy(
            board = state.board.toCharArray().also { it[at("h8")] = 'R' }.concatToString(),
        )
        // O que importa é o mecanismo: mover para h8 derruba o direito 'k'.
        val after = ChessGame.applyKnownLegal(
            comTorreH8Capturada.copy(board = state.board),
            ChessMove(at("h1"), at("h8")),
        )
        assertFalse(after.castling.contains('k'), "as pretas perdem o roque pequeno: ${after.castling}")
        assertFalse(after.castling.contains('K'), "as brancas perdem o seu ao mover a torre")
    }

    @Test
    fun `en passant remove o peao que ficou ao lado`() {
        // Peão branco em e5, peão preto acabou de avançar de d7 para d5.
        val state = chessStateFromFen("8/8/8/3pP3/8/8/8/4K2k w - d6 0 1")
        val after = ChessGame.applyOrThrow(state, move("e5", "d6"))

        assertEquals('P', after.pieceAt(at("d6")))
        assertEquals(CHESS_EMPTY, after.pieceAt(at("d5")), "o peão capturado sai de d5, não de d6")
        assertEquals(CHESS_EMPTY, after.pieceAt(at("e5")))
    }

    @Test
    fun `o avanco duplo abre a casa de en passant e ela dura um lance so`() {
        val after = ChessGame.applyOrThrow(start, move("e2", "e4"))
        assertEquals(at("e3"), after.enPassant)

        val depois = ChessGame.applyOrThrow(after, move("e7", "e5"))
        assertEquals(at("e6"), depois.enPassant, "a casa nova substitui a anterior")

        val maisUm = ChessGame.applyOrThrow(depois, move("g1", "f3"))
        assertEquals(NO_SQUARE, maisUm.enPassant, "sem avanço duplo, não há en passant")
    }

    @Test
    fun `promocao troca o peao pela peca escolhida`() {
        val state = chessStateFromFen("8/4P3/8/8/8/8/8/4K2k w - - 0 1")

        val dama = ChessGame.applyOrThrow(state, move("e7", "e8", 'Q'))
        assertEquals('Q', dama.pieceAt(at("e8")))

        val cavalo = ChessGame.applyOrThrow(state, move("e7", "e8", 'N'))
        assertEquals('N', cavalo.pieceAt(at("e8")))
    }

    @Test
    fun `o peao preto promove em minuscula`() {
        // O rei branco fica em g1: em e1 ele bloquearia a casa de promoção.
        val state = chessStateFromFen("4k3/8/8/8/8/8/4p3/6K1 b - - 0 1")
        val after = ChessGame.applyOrThrow(state, move("e2", "e1", 'Q'))
        assertEquals('q', after.pieceAt(at("e1")))
    }

    // -------- contadores --------

    @Test
    fun `o relogio dos cinquenta lances zera em captura e avanco de peao`() {
        val state = chessStateFromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 10 20")

        val avanco = ChessGame.applyOrThrow(state, move("e4", "e5"))
        assertEquals(0, avanco.halfmoveClock, "avanço de peão zera")

        val captura = ChessGame.applyOrThrow(state, move("e4", "d5"))
        assertEquals(0, captura.halfmoveClock, "captura zera")

        val reiAndou = ChessGame.applyOrThrow(state, move("e1", "d1"))
        assertEquals(11, reiAndou.halfmoveClock, "lance comum incrementa")
    }

    // -------- recusas --------

    @Test
    fun `nao se joga com peca do adversario`() {
        val result = ChessGame.applyMove(start, move("e7", "e5"))
        assertIs<MoveResult.Illegal>(result)
        assertContains(result.reason, "não é sua")
    }

    @Test
    fun `lance que expoe o proprio rei e recusado com a explicacao certa`() {
        // Cavalo em e2 é o único bloqueio entre o rei branco em e1 e a torre preta em e8.
        val state = chessStateFromFen("4r3/8/8/8/8/8/4N3/4K3 w - - 0 1")
        val result = ChessGame.applyMove(state, move("e2", "g3"))

        assertIs<MoveResult.Illegal>(result)
        assertContains(result.reason, "xeque")
    }

    @Test
    fun `em xeque, so valem lances que resolvem`() {
        val state = chessStateFromFen("4r3/8/8/8/8/8/8/4K3 w - - 0 1")
        assertTrue(state.inCheck(Seat.FIRST))

        val legais = ChessGame.legalMoves(state)
        assertTrue(legais.isNotEmpty(), "o rei tem para onde fugir")
        assertTrue(
            legais.none { it.to == at("e2") },
            "e2 continua na coluna atacada e não pode estar na lista",
        )
    }

    @Test
    fun `a posicao inicial tem vinte lances e a vez das brancas`() {
        assertEquals(20, ChessGame.legalMoves(start).size)
        assertEquals(Seat.FIRST, start.turn)
        assertEquals("KQkq", start.castling)
        assertEquals(NO_SQUARE, start.enPassant)
    }

    @Test
    fun `a notacao do lance e algebrica longa`() {
        assertEquals("e2e4", move("e2", "e4").describe())
        assertEquals("e7e8q", move("e7", "e8", 'Q').describe())
    }
}
