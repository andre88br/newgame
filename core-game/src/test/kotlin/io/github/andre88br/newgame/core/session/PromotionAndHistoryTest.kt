package io.github.andre88br.newgame.core.session

import io.github.andre88br.newgame.core.engine.GameCatalog
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import io.github.andre88br.newgame.core.games.chess.ChessGame
import io.github.andre88br.newgame.core.games.chess.ChessInteractor
import io.github.andre88br.newgame.core.games.chess.ChessMove
import io.github.andre88br.newgame.core.games.chess.chessStateFromFen
import io.github.andre88br.newgame.core.games.chess.squareOf
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeMove
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PromotionAndHistoryTest {

    private fun at(name: String) = squareOf(name)!!

    // -------- escolha da peça na promoção --------

    /** Peão branco em e7, casa e8 livre, reis longe do caminho. */
    private val prestesAPromover = chessStateFromFen("8/4P3/8/8/8/8/8/4K2k w - - 0 1")

    @Test
    fun `o toque na casa de promocao pergunta em vez de jogar`() {
        val result = ChessInteractor.tap(prestesAPromover, selected = at("e7"), square = at("e8"))

        assertIs<TapResult.ChoosePromotion>(result)
        assertEquals(at("e7"), result.from)
        assertEquals(at("e8"), result.to)
        assertContentEquals(
            listOf('Q', 'R', 'B', 'N'),
            result.choices.map { it.kind },
            "as quatro peças, com a dama primeiro",
        )
    }

    @Test
    fun `cada opcao ja traz o lance pronto e legal`() {
        val result = ChessInteractor.tap(prestesAPromover, selected = at("e7"), square = at("e8"))
        assertIs<TapResult.ChoosePromotion>(result)

        val legais = ChessGame.legalMoves(prestesAPromover)
        for (choice in result.choices) {
            val move = choice.move as ChessMove
            assertEquals(choice.kind, move.promotion, "a opção ${choice.kind} trouxe o lance errado")
            assertTrue(move in legais, "lance ilegal na opção ${choice.kind}: ${move.describe()}")
        }
    }

    @Test
    fun `escolher a torre promove a torre`() {
        val result = ChessInteractor.tap(prestesAPromover, selected = at("e7"), square = at("e8"))
        assertIs<TapResult.ChoosePromotion>(result)

        val torre = result.choices.first { it.kind == 'R' }.move as ChessMove
        val depois = ChessGame.applyOrThrow(prestesAPromover, torre)
        assertEquals('R', depois.pieceAt(at("e8")))
    }

    @Test
    fun `lance comum continua sendo jogado direto`() {
        val meioDeTabuleiro = chessStateFromFen("4k3/8/8/8/8/8/4P3/4K3 w - - 0 1")
        val result = ChessInteractor.tap(meioDeTabuleiro, selected = at("e2"), square = at("e4"))
        assertIs<TapResult.Play>(result)
    }

    @Test
    fun `os outros jogos nunca pedem escolha de promocao`() {
        for (entry in GameCatalog.available) {
            if (entry.id == GameId.CHESS) continue
            val interactor = entry.interactor ?: continue
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            for (square in 0 until interactor.rows * interactor.columns) {
                val result = interactor.tap(state, null, square)
                assertTrue(
                    result !is TapResult.ChoosePromotion,
                    "${entry.id} pediu escolha de promoção na casa $square",
                )
            }
        }
    }

    // -------- histórico de lances --------

    private fun velha() = MatchSession(
        entry = GameCatalog.entry(GameId.TIC_TAC_TOE),
        config = MatchConfig.DETERMINISTIC,
        players = mapOf(Seat.FIRST to Player.Human, Seat.SECOND to Player.Human),
    )

    @Test
    fun `o historico registra lance, cadeira e ordem`() {
        val session = velha()
        session.play(TicTacToeMove(4))
        session.play(TicTacToeMove(0))
        session.play(TicTacToeMove(8))

        assertEquals(3, session.history.size)
        assertContentEquals(listOf(0, 1, 2), session.history.map { it.ply })
        assertContentEquals(
            listOf(Seat.FIRST, Seat.SECOND, Seat.FIRST),
            session.history.map { it.seat },
        )
        assertContentEquals(listOf("b2", "a3", "c1"), session.history.map { it.notation })
    }

    @Test
    fun `o historico comeca vazio`() {
        assertTrue(velha().history.isEmpty())
    }

    @Test
    fun `desfazer encurta o historico`() {
        val session = velha()
        session.play(TicTacToeMove(4))
        session.play(TicTacToeMove(0))
        assertEquals(2, session.history.size)

        session.undo()
        assertEquals(1, session.history.size)
        assertEquals("b2", session.history.single().notation)
    }

    @Test
    fun `recomecar limpa o historico`() {
        val session = velha()
        session.play(TicTacToeMove(4))
        session.restart()
        assertTrue(session.history.isEmpty())
    }

    @Test
    fun `retomar uma partida salva reconstroi o historico igual`() {
        val original = MatchSession(
            entry = GameCatalog.entry(GameId.CHESS),
            config = MatchConfig.DETERMINISTIC,
            players = mapOf(Seat.FIRST to Player.Human, Seat.SECOND to Player.Human),
        )
        listOf(
            ChessMove(at("e2"), at("e4")),
            ChessMove(at("e7"), at("e5")),
            ChessMove(at("g1"), at("f3")),
        ).forEach { original.play(it) }

        val retomada = MatchSession(
            entry = original.entry,
            config = original.config,
            players = original.players,
            record = original.record,
        )

        assertEquals(original.history, retomada.history)
        assertContentEquals(listOf("e2e4", "e7e5", "g1f3"), retomada.notation())
        assertContentEquals(
            listOf(Seat.FIRST, Seat.SECOND, Seat.FIRST),
            retomada.history.map { it.seat },
        )
    }

    @Test
    fun `o historico acompanha o reversi mesmo quando a vez nao alterna`() {
        // No reversi quem não tem lance perde a vez, então nem sempre as cadeiras se
        // revezam. O histórico guarda a cadeira de cada lance justamente por isso.
        val session = MatchSession(
            entry = GameCatalog.entry(GameId.REVERSI),
            config = MatchConfig.DETERMINISTIC,
            players = mapOf(Seat.FIRST to Player.Human, Seat.SECOND to Player.Human),
        )
        repeat(6) {
            val move = session.entry.rules.legalMoves(session.state).firstOrNull() ?: return@repeat
            session.play(move)
        }

        assertEquals(session.record.ply, session.history.size)
        session.history.forEachIndexed { index, played ->
            assertEquals(index, played.ply, "os lances precisam estar em ordem")
        }
    }
}
