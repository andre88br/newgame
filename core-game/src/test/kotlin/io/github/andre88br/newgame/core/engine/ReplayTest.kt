package io.github.andre88br.newgame.core.engine

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.games.checkers.CheckersGame
import io.github.andre88br.newgame.core.games.checkers.CheckersState
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeGame
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeMove
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Uma partida é guardada como semente mais lista de lances. Estes testes garantem o que
 * essa escolha promete: reproduzir a lista devolve exatamente o mesmo estado. É disso que
 * dependem salvar e retomar a partida, o botão de voltar jogada e o histórico.
 */
class ReplayTest {

    private val velha = TicTacToeGame.asAny()
    private val damas = CheckersGame.asAny()

    @Test
    fun `reproduzir os lances chega ao mesmo estado que aplica-los direto`() {
        val cells = listOf(4, 0, 8, 2, 1, 7, 6)
        var direct: TicTacToeState = TicTacToeGame.initialState(MatchConfig.DETERMINISTIC)
        var record = MatchRecord(GameId.TIC_TAC_TOE, MatchConfig.DETERMINISTIC)

        for (cell in cells) {
            record = Replay.append(velha, record, direct, TicTacToeMove(cell))
            direct = TicTacToeGame.applyOrThrow(direct, TicTacToeMove(cell))
        }

        assertEquals(direct, Replay.state(velha, record))
        assertEquals(cells.size, record.ply)
    }

    @Test
    fun `o registro guarda todos os estados pelos quais a partida passou`() {
        var record = MatchRecord(GameId.TIC_TAC_TOE, MatchConfig.DETERMINISTIC)
        var state: GameState = velha.initialState(MatchConfig.DETERMINISTIC)
        for (cell in listOf(0, 4, 1)) {
            record = Replay.append(velha, record, state, TicTacToeMove(cell))
            state = velha.applyMove(state, TicTacToeMove(cell)).let { (it as MoveResult.Ok).state }
        }

        val history = Replay.states(velha, record)
        assertEquals(4, history.size, "três lances devem produzir quatro estados")
        assertEquals(velha.initialState(MatchConfig.DETERMINISTIC), history.first())
        assertEquals(state, history.last())
        history.forEachIndexed { index, snapshot -> assertEquals(index, snapshot.ply) }
    }

    @Test
    fun `voltar jogada remove o ultimo lance`() {
        var record = MatchRecord(GameId.TIC_TAC_TOE, MatchConfig.DETERMINISTIC)
        var state: GameState = velha.initialState(MatchConfig.DETERMINISTIC)
        for (cell in listOf(0, 4, 1, 8)) {
            record = Replay.append(velha, record, state, TicTacToeMove(cell))
            state = (velha.applyMove(state, TicTacToeMove(cell)) as MoveResult.Ok).state
        }

        val undone = Replay.undo(record, count = 2)
        assertEquals(2, undone.ply)
        assertEquals(2, Replay.state(velha, undone).ply)

        // Voltar mais do que existe simplesmente volta ao início, em vez de estourar.
        assertEquals(0, Replay.undo(record, count = 99).ply)
    }

    @Test
    fun `o resultado da partida acompanha o registro`() {
        var record = MatchRecord(GameId.TIC_TAC_TOE, MatchConfig.DETERMINISTIC)
        var state: GameState = velha.initialState(MatchConfig.DETERMINISTIC)
        for (cell in listOf(0, 3, 1, 4, 2)) {
            record = Replay.append(velha, record, state, TicTacToeMove(cell))
            state = (velha.applyMove(state, TicTacToeMove(cell)) as MoveResult.Ok).state
        }
        assertEquals(Outcome.Win(Seat.FIRST), record.outcome)
    }

    @Test
    fun `uma partida inteira de damas reproduz identica`() {
        val config = MatchConfig(seed = 20260808L)
        var state = CheckersGame.initialState(config)
        var record = MatchRecord(GameId.CHECKERS, config)
        val ai = GameCatalog.ai(GameId.CHECKERS)

        var turns = 0
        while (!CheckersGame.outcome(state).isOver && turns < 120) {
            val move = ai.chooseMove(state, Difficulty.EASY, seed = turns.toLong()) ?: break
            record = Replay.append(damas, record, state, move)
            state = (damas.applyMove(state, move) as MoveResult.Ok).state as CheckersState
            turns++
        }

        assertTrue(turns > 20, "a partida terminou cedo demais para valer como teste: $turns")
        assertEquals(state, Replay.state(damas, record))
    }

    @Test
    fun `um registro adulterado e recusado com mensagem clara`() {
        val record = MatchRecord(
            gameId = GameId.TIC_TAC_TOE,
            config = MatchConfig.DETERMINISTIC,
            // Duas marcas na mesma casa: impossível.
            moves = listOf("""{"cell":4}""", """{"cell":4}"""),
        )
        val failure = assertFailsWith<ReplayException> { Replay.state(velha, record) }
        assertTrue("ilegal" in failure.message.orEmpty(), "mensagem: ${failure.message}")
    }

    @Test
    fun `reproduzir com o jogo errado e recusado`() {
        val record = MatchRecord(GameId.CHECKERS, MatchConfig.DETERMINISTIC)
        assertFailsWith<IllegalArgumentException> { Replay.state(velha, record) }
    }

    @Test
    fun `estado e lance sobrevivem a ida e volta pelo JSON`() {
        val state = TicTacToeGame.applyOrThrow(
            TicTacToeGame.initialState(MatchConfig.DETERMINISTIC),
            TicTacToeMove(4),
        )
        assertEquals(state, velha.decodeState(velha.encodeState(state)))
        assertEquals(TicTacToeMove(4), velha.decodeMove(velha.encodeMove(TicTacToeMove(4))))

        val damasState = CheckersGame.initialState(MatchConfig.DETERMINISTIC)
        assertEquals(damasState, damas.decodeState(damas.encodeState(damasState)))
    }

    @Test
    fun `o registro inteiro sobrevive a ida e volta pelo JSON`() {
        val record = MatchRecord(
            gameId = GameId.CHECKERS,
            config = MatchConfig(seed = 99, options = mapOf("variante" to "brasileira")),
            moves = listOf("""{"from":42,"path":[35]}"""),
            outcome = Outcome.Draw(DrawReason.NO_PROGRESS),
        )
        val json = GameJson.encodeToString(MatchRecord.serializer(), record)
        assertEquals(record, GameJson.decodeFromString(MatchRecord.serializer(), json))
    }

    @Test
    fun `a mesma semente reproduz a mesma partida em execucoes diferentes`() {
        fun playOut(): List<String> {
            val config = MatchConfig(seed = 7L)
            var state: GameState = damas.initialState(config)
            val moves = ArrayList<String>()
            val ai = GameCatalog.ai(GameId.CHECKERS)
            repeat(40) { turn ->
                if (damas.outcome(state).isOver) return@repeat
                val move = ai.chooseMove(state, Difficulty.EASY, seed = turn.toLong()) ?: return@repeat
                moves += move.describe()
                state = (damas.applyMove(state, move) as MoveResult.Ok).state
            }
            return moves
        }
        assertEquals(playOut(), playOut())
    }
}
