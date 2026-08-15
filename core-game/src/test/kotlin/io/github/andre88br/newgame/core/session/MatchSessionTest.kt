package io.github.andre88br.newgame.core.session

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.GameCatalog
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.checkers.CheckersState
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeMove
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeState
import io.github.andre88br.newgame.core.engine.ReasonKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchSessionTest {

    private val velha = GameCatalog.entry(GameId.TIC_TAC_TOE)
    private val damas = GameCatalog.entry(GameId.CHECKERS)

    private fun passAndPlay(entry: io.github.andre88br.newgame.core.engine.GameEntry, seed: Long = 1L) =
        MatchSession(
            entry = entry,
            config = MatchConfig(seed),
            players = mapOf(Seat.FIRST to Player.Human, Seat.SECOND to Player.Human),
        )

    private fun againstAi(
        entry: io.github.andre88br.newgame.core.engine.GameEntry,
        difficulty: Difficulty = Difficulty.EASY,
        humanSeat: Seat = Seat.FIRST,
        seed: Long = 1L,
    ) = MatchSession(
        entry = entry,
        config = MatchConfig(seed),
        players = mapOf(
            humanSeat to Player.Human,
            Seat(1 - humanSeat.index) to Player.Ai(difficulty),
        ),
    )

    // -------- o básico --------

    @Test
    fun `uma partida nova comeca no zero e com a vez da primeira cadeira`() {
        val session = passAndPlay(velha)
        assertEquals(0, session.record.ply)
        assertEquals(Seat.FIRST, session.turn)
        assertEquals(Outcome.InProgress, session.outcome)
        assertFalse(session.isOver)
        assertFalse(session.canUndo)
        assertFalse(session.awaitingAi)
    }

    @Test
    fun `jogar avanca o estado e o registro juntos`() {
        val session = passAndPlay(velha)
        val result = session.play(TicTacToeMove(4))

        assertIs<PlayResult.Ok>(result)
        assertEquals(1, session.record.ply)
        assertEquals(1, session.state.ply)
        assertEquals(Seat.SECOND, session.turn)
        assertEquals(listOf("b2"), session.notation())
    }

    @Test
    fun `um lance ilegal e recusado com o motivo do motor, sem mexer na partida`() {
        val session = passAndPlay(velha)
        session.play(TicTacToeMove(4))

        val result = session.play(TicTacToeMove(4))
        assertIs<PlayResult.Rejected>(result)
        assertEquals(ReasonKey.SQUARE_TAKEN, result.reason.key, "motivo: ${result.reason}")
        assertEquals(1, session.record.ply, "a partida não deveria ter avançado")
    }

    @Test
    fun `tocar fora da vez nao joga pela maquina`() {
        val session = againstAi(velha, humanSeat = Seat.FIRST)
        session.play(TicTacToeMove(0))

        assertTrue(session.awaitingAi)
        assertEquals(PlayResult.NotYourTurn, session.play(TicTacToeMove(1)))
        assertEquals(1, session.record.ply)
    }

    @Test
    fun `depois do fim nao se joga mais`() {
        val session = passAndPlay(velha)
        listOf(0, 3, 1, 4, 2).forEach { session.play(TicTacToeMove(it)) }

        assertTrue(session.isOver)
        assertEquals(Outcome.Win(Seat.FIRST), session.outcome)
        assertEquals(PlayResult.Finished, session.play(TicTacToeMove(5)))
    }

    @Test
    fun `o resultado fica gravado no registro`() {
        val session = passAndPlay(velha)
        listOf(0, 3, 1, 4, 2).forEach { session.play(TicTacToeMove(it)) }
        assertEquals(Outcome.Win(Seat.FIRST), session.record.outcome)
    }

    // -------- a IA --------

    @Test
    fun `playAiTurn so joga quando e a vez da maquina`() {
        val session = againstAi(velha, humanSeat = Seat.FIRST)
        assertNull(session.playAiTurn(), "não era a vez da IA")

        session.play(TicTacToeMove(4))
        val aiMove = session.playAiTurn()
        assertNotNull(aiMove)
        assertEquals(2, session.record.ply)
        assertFalse(session.awaitingAi)
    }

    @Test
    fun `uma partida inteira contra a IA chega ao fim`() {
        val session = againstAi(velha, difficulty = Difficulty.HARD, humanSeat = Seat.FIRST)
        var guard = 0
        while (!session.isOver && guard++ < 20) {
            if (session.awaitingAi) {
                assertNotNull(session.playAiTurn(), "a IA travou em:\n${session.state}")
            } else {
                val move = velha.rules.legalMoves(session.state).first()
                assertIs<PlayResult.Ok>(session.play(move))
            }
        }
        assertTrue(session.isOver, "a partida não terminou:\n${session.state}")
    }

    @Test
    fun `a IA joga tambem quando ocupa a primeira cadeira`() {
        val session = againstAi(velha, humanSeat = Seat.SECOND)
        assertTrue(session.awaitingAi, "a IA senta na primeira cadeira e deve abrir")
        assertNotNull(session.playAiTurn())
        assertEquals(Seat.SECOND, session.turn)
    }

    @Test
    fun `a mesma semente leva a maquina as mesmas escolhas`() {
        fun run(): List<String> {
            val session = againstAi(damas, difficulty = Difficulty.EASY, seed = 4242L)
            repeat(6) {
                if (session.awaitingAi) {
                    session.playAiTurn()
                } else {
                    session.play(damas.rules.legalMoves(session.state).first())
                }
            }
            return session.notation()
        }
        assertEquals(run(), run())
    }

    @Test
    fun `a dica devolve um lance legal sem jogar`() {
        val session = passAndPlay(damas)
        val hint = session.hint()

        assertNotNull(hint)
        assertTrue(hint in damas.rules.legalMoves(session.state), "dica ilegal: $hint")
        assertEquals(0, session.record.ply, "a dica não pode jogar sozinha")
    }

    @Test
    fun `nao ha dica depois do fim`() {
        val session = passAndPlay(velha)
        listOf(0, 3, 1, 4, 2).forEach { session.play(TicTacToeMove(it)) }
        assertNull(session.hint())
    }

    // -------- desfazer --------

    @Test
    fun `no passa-e-joga desfazer volta um lance`() {
        val session = passAndPlay(velha)
        session.play(TicTacToeMove(0))
        session.play(TicTacToeMove(4))

        assertTrue(session.undo())
        assertEquals(1, session.record.ply)
        assertEquals(Seat.SECOND, session.turn)
    }

    @Test
    fun `contra a maquina desfazer volta dois lances`() {
        // Voltar só um devolveria a vez para a IA, que jogaria de novo — e o botão
        // pareceria não ter feito nada.
        val session = againstAi(velha, humanSeat = Seat.FIRST)
        session.play(TicTacToeMove(4))
        session.playAiTurn()
        assertEquals(2, session.record.ply)

        assertTrue(session.undo())
        assertEquals(0, session.record.ply)
        assertEquals(Seat.FIRST, session.turn)
        assertFalse(session.awaitingAi, "depois de desfazer, a vez tem que ser da pessoa")
    }

    @Test
    fun `desfazer com a IA na primeira cadeira para na vez da pessoa`() {
        val session = againstAi(velha, humanSeat = Seat.SECOND)
        session.playAiTurn()
        val human = velha.rules.legalMoves(session.state).first()
        session.play(human)
        session.playAiTurn()
        assertEquals(3, session.record.ply)

        assertTrue(session.undo())
        assertEquals(Seat.SECOND, session.turn, "deveria ter parado na vez da pessoa")
        assertFalse(session.awaitingAi)
    }

    @Test
    fun `nao ha o que desfazer numa partida nova`() {
        val session = passAndPlay(velha)
        assertFalse(session.canUndo)
        assertFalse(session.undo())
        assertEquals(0, session.record.ply)
    }

    @Test
    fun `desfazer reabre uma partida ja encerrada`() {
        val session = passAndPlay(velha)
        listOf(0, 3, 1, 4, 2).forEach { session.play(TicTacToeMove(it)) }
        assertTrue(session.isOver)

        assertTrue(session.undo())
        assertFalse(session.isOver)
        assertEquals(Outcome.InProgress, session.outcome)
        assertEquals(Outcome.InProgress, session.record.outcome)
    }

    // -------- salvar e retomar --------

    @Test
    fun `retomar do registro devolve a partida no mesmo ponto`() {
        val original = againstAi(damas, humanSeat = Seat.FIRST, seed = 777L)
        repeat(5) {
            if (original.awaitingAi) {
                original.playAiTurn()
            } else {
                original.play(damas.rules.legalMoves(original.state).first())
            }
        }

        // O que iria para o banco é só isto: o registro serializado.
        val saved = original.record

        val resumed = MatchSession(
            entry = damas,
            config = original.config,
            players = original.players,
            record = saved,
        )

        assertEquals(original.state, resumed.state)
        assertEquals(original.turn, resumed.turn)
        assertEquals(original.notation(), resumed.notation())
        assertEquals(
            (original.state as CheckersState).board,
            (resumed.state as CheckersState).board,
        )
    }

    @Test
    fun `retomar no meio deixa a partida jogavel dali em diante`() {
        val original = passAndPlay(velha)
        listOf(0, 4, 1).forEach { original.play(TicTacToeMove(it)) }

        val resumed = MatchSession(velha, original.config, original.players, original.record)
        assertIs<PlayResult.Ok>(resumed.play(TicTacToeMove(8)))
        assertEquals(4, resumed.record.ply)
    }

    @Test
    fun `restoreFrom troca a partida em andamento`() {
        val session = passAndPlay(velha)
        session.play(TicTacToeMove(0))

        val other = passAndPlay(velha)
        listOf(4, 0, 8).forEach { other.play(TicTacToeMove(it)) }

        session.restoreFrom(other.record)
        assertEquals(3, session.record.ply)
        assertEquals(other.state, session.state)
    }

    @Test
    fun `recomecar limpa a partida sem trocar os jogadores`() {
        val session = againstAi(velha, humanSeat = Seat.FIRST)
        session.play(TicTacToeMove(4))
        session.playAiTurn()

        session.restart()
        assertEquals(0, session.record.ply)
        assertEquals(Seat.FIRST, session.turn)
        assertEquals(Outcome.InProgress, session.outcome)
        assertEquals(0, (session.state as TicTacToeState).cells.count { it != -1 })
    }

    /**
     * A mesa de um, de ponta a ponta.
     *
     * Tudo aqui foi escrito supondo dois lados — de quem é a vez seguinte, quando a máquina
     * pensa, até onde o desfazer volta. A paciência é a primeira mesa de uma cadeira só, e o
     * que este teste cobre não é a paciência: é a sessão continuar inteira quando não há
     * segundo lado nenhum.
     */
    @Test
    fun `a mesa de uma cadeira joga, desfaz e recomeca`() {
        val paciencia = GameCatalog.entry(GameId.KLONDIKE)
        val session = MatchSession(
            entry = paciencia,
            config = MatchConfig(seed = 20, seats = 1),
            players = mapOf(Seat.FIRST to Player.Human),
        )

        assertFalse(session.awaitingAi, "não há cadeira da máquina para esperar")
        assertEquals(Seat.FIRST, session.turn)
        assertNull(session.playAiTurn(), "ninguém joga pela máquina numa mesa de um")

        val lance = paciencia.rules.legalMoves(session.state).first()
        assertIs<PlayResult.Ok>(session.play(lance))
        assertEquals(1, session.record.ply)
        // A vez continua sendo da mesma pessoa: não há para quem passar.
        assertEquals(Seat.FIRST, session.turn)

        assertNotNull(session.hint(), "a dica funciona sem adversário")

        assertTrue(session.undo(), "desfazer volta um lance, e não dois")
        assertEquals(0, session.record.ply)

        session.play(lance)
        session.restart()
        assertEquals(0, session.record.ply)
    }

    @Test
    fun `sessao e registro de jogos diferentes nao se misturam`() {
        val alheio = io.github.andre88br.newgame.core.engine.MatchRecord(
            GameId.CHECKERS,
            MatchConfig.DETERMINISTIC,
        )
        val failure = runCatching {
            MatchSession(velha, MatchConfig.DETERMINISTIC, emptyMap(), alheio)
        }.exceptionOrNull()
        assertIs<IllegalArgumentException>(failure)
    }
}
