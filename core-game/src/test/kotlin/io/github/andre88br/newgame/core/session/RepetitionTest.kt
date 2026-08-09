package io.github.andre88br.newgame.core.session

import io.github.andre88br.newgame.core.engine.DrawReason
import io.github.andre88br.newgame.core.engine.GameCatalog
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.chess.ChessGame
import io.github.andre88br.newgame.core.games.chess.ChessMove
import io.github.andre88br.newgame.core.games.chess.chessStateFromFen
import io.github.andre88br.newgame.core.games.chess.squareOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repetição tripla.
 *
 * A regra vive na sessão, e não no motor, por um motivo: olhando uma posição sozinha não há
 * como saber quantas vezes ela já apareceu. O motor só diz o que conta como "a mesma
 * posição" — a chave —, e quem guarda o histórico faz a conta.
 */
class RepetitionTest {

    private val xadrez = GameCatalog.entry(GameId.CHESS)

    private fun at(name: String) = squareOf(name)!!
    private fun move(from: String, to: String) = ChessMove(at(from), at(to))

    private fun novaPartida() = MatchSession(
        entry = xadrez,
        config = MatchConfig.DETERMINISTIC,
        players = mapOf(Seat.FIRST to Player.Human, Seat.SECOND to Player.Human),
    )

    @Test
    fun `a chave ignora contadores e enxerga a mesma posicao`() {
        val a = chessStateFromFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        val b = a.copy(halfmoveClock = 37, ply = 40)

        assertEquals(
            ChessGame.repetitionKey(a),
            ChessGame.repetitionKey(b),
            "relógio e número do lance não podem entrar na chave",
        )
    }

    @Test
    fun `a chave separa posicoes que diferem no direito de roque`() {
        val comRoque = chessStateFromFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        val semRoque = comRoque.copy(castling = "")

        assertNotEquals(ChessGame.repetitionKey(comRoque), ChessGame.repetitionKey(semRoque))
    }

    @Test
    fun `a chave separa posicoes que diferem na vez`() {
        val brancas = chessStateFromFen("4k3/8/8/8/8/8/8/4K2R w K - 0 1")
        val pretas = brancas.copy(turn = Seat.SECOND)

        assertNotEquals(ChessGame.repetitionKey(brancas), ChessGame.repetitionKey(pretas))
    }

    @Test
    fun `jogos sem repeticao relevante nao tem chave`() {
        for (entry in GameCatalog.available) {
            if (entry.id == GameId.CHESS) continue
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            assertNull(
                entry.rules.repetitionKey(state),
                "${entry.id} declarou chave de repetição sem precisar",
            )
        }
    }

    /**
     * Torres andando de um lado para o outro. A posição inicial da sequência aparece três
     * vezes — a primeira, e depois de cada ida e volta completa — e aí a partida empata.
     */
    @Test
    fun `tres repeticoes da mesma posicao empatam a partida`() {
        // Da posição inicial: os cavalos saem e voltam, e a posição se repete.
        val session = novaPartida()
        val vaiEVolta = listOf(
            move("g1", "f3"), move("g8", "f6"), move("f3", "g1"), move("f6", "g8"),
            move("g1", "f3"), move("g8", "f6"), move("f3", "g1"), move("f6", "g8"),
        )

        vaiEVolta.forEachIndexed { index, m ->
            val result = session.play(m)
            assertIs<PlayResult.Ok>(result, "lance ${index + 1} (${m.describe()}) foi recusado: $result")
        }

        assertEquals(
            Outcome.Draw(DrawReason.REPETITION),
            session.outcome,
            "a posição inicial apareceu três vezes e a partida deveria empatar",
        )
    }

    @Test
    fun `duas repeticoes ainda nao empatam`() {
        val session = novaPartida()
        listOf(
            move("g1", "f3"), move("g8", "f6"), move("f3", "g1"), move("f6", "g8"),
        ).forEach { session.play(it) }

        assertEquals(Outcome.InProgress, session.outcome, "só duas ocorrências: a partida segue")
    }

    @Test
    fun `desfazer devolve a contagem de repeticoes`() {
        val session = novaPartida()
        listOf(
            move("g1", "f3"), move("g8", "f6"), move("f3", "g1"), move("f6", "g8"),
            move("g1", "f3"), move("g8", "f6"), move("f3", "g1"), move("f6", "g8"),
        ).forEach { session.play(it) }
        assertEquals(Outcome.Draw(DrawReason.REPETITION), session.outcome)

        assertTrue(session.undo())
        assertEquals(
            Outcome.InProgress,
            session.outcome,
            "desfazer o lance que fechou a repetição precisa reabrir a partida",
        )
    }

    @Test
    fun `retomar do registro recupera a contagem`() {
        val original = novaPartida()
        listOf(
            move("g1", "f3"), move("g8", "f6"), move("f3", "g1"), move("f6", "g8"),
            move("g1", "f3"), move("g8", "f6"), move("f3", "g1"), move("f6", "g8"),
        ).forEach { original.play(it) }

        val retomada = MatchSession(
            entry = xadrez,
            config = original.config,
            players = original.players,
            record = original.record,
        )
        assertEquals(
            Outcome.Draw(DrawReason.REPETITION),
            retomada.outcome,
            "quem reabre a partida salva precisa ver o mesmo empate",
        )
    }
}
