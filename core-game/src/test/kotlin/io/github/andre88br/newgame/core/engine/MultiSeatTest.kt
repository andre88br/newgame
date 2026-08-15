package io.github.andre88br.newgame.core.engine

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.games.dominoes.DOMINO_HAND_SIZE
import io.github.andre88br.newgame.core.games.dominoes.DominoesGame
import io.github.andre88br.newgame.core.games.dominoes.DominoesState
import io.github.andre88br.newgame.core.games.dominoes.Tile
import io.github.andre88br.newgame.core.games.ludo.LUDO_TOKENS
import io.github.andre88br.newgame.core.games.ludo.LudoGame
import io.github.andre88br.newgame.core.games.ludo.LudoState
import io.github.andre88br.newgame.core.session.MatchSession
import io.github.andre88br.newgame.core.session.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mesa de três e de quatro.
 *
 * Passar de dois para quatro jogadores mexe em tudo o que assumia "eu e o outro": de quem é
 * a vez seguinte, quem é capturado, quem ganha quando o jogo fecha e contra quem a máquina
 * se compara. Estes testes cobrem justamente as suposições que deixaram de valer.
 */
class MultiSeatTest {

    private val mesas = listOf(2, 3, 4)

    private fun jogosComMesaGrande() = GameCatalog.available.filter { it.rules.supportedSeats.last > 2 }

    /**
     * Quais jogos vão além de dois — e quais **escolhem** o tamanho da mesa.
     *
     * São coisas diferentes, e a copas mostra por quê: ela é de quatro e só de quatro, então
     * abre mesa grande mas não oferece escolha nenhuma. Confundir as duas faria a tela de
     * configuração perguntar o número de jogadores para um jogo que não tem alternativa.
     */
    @Test
    fun `mesa grande e mesa que se escolhe nao sao a mesma coisa`() {
        val grandes = jogosComMesaGrande().map { it.id }.toSet()
        assertEquals(setOf(GameId.DOMINOES, GameId.LUDO, GameId.HEARTS, GameId.CANASTRA), grandes)

        val escolhem = GameCatalog.available
            .filter { it.rules.supportedSeats.first != it.rules.supportedSeats.last }
            .map { it.id }
            .toSet()
        assertEquals(
            setOf(GameId.DOMINOES, GameId.LUDO, GameId.CANASTRA),
            escolhem,
            "copas é de quatro e só; os outros três deixam escolher o tamanho da mesa",
        )

        for (entry in GameCatalog.available) {
            val faixa = entry.rules.supportedSeats
            assertTrue(
                faixa.first in MatchConfig.MIN_SEATS..MatchConfig.MAX_SEATS &&
                    faixa.last in MatchConfig.MIN_SEATS..MatchConfig.MAX_SEATS,
                "${entry.id} declara mesa fora do que a configuração aceita: $faixa",
            )
        }
    }

    @Test
    fun `a mesa pedida e a mesa montada`() {
        for (entry in jogosComMesaGrande()) {
            for (seats in entry.rules.supportedSeats) {
                val state = entry.rules.initialState(MatchConfig(seed = 5, seats = seats))
                assertEquals(
                    seats,
                    entry.rules.seatsIn(state),
                    "${entry.id}: pedi mesa de $seats e veio outra",
                )
            }
        }
    }

    @Test
    fun `mesa fora da faixa e recusada na configuracao`() {
        for (seats in listOf(1, 5, 0, -1)) {
            val erro = runCatching { MatchConfig(seed = 1, seats = seats) }.exceptionOrNull()
            assertTrue(erro is IllegalArgumentException, "mesa de $seats devia ser recusada, veio $erro")
        }
    }

    // -------- dominó --------

    @Test
    fun `o domino distribui sete para cada um e guarda o resto`() {
        for (seats in mesas) {
            val state = DominoesGame.initialState(MatchConfig(seed = 11, seats = seats))

            assertEquals(seats, state.seats)
            for (index in 0 until seats) {
                assertEquals(
                    DOMINO_HAND_SIZE,
                    state.handSize(Seat(index)),
                    "mesa de $seats: cadeira $index recebeu mão errada",
                )
            }
            assertEquals(
                28 - DOMINO_HAND_SIZE * seats,
                state.boneyard.size,
                "mesa de $seats: monte com tamanho errado",
            )

            val todas = state.hands.flatten() + state.boneyard
            assertEquals(28, todas.size, "mesa de $seats: sumiu ou sobrou peça")
            assertEquals(28, todas.distinct().size, "mesa de $seats: peça repetida")
        }
    }

    /** Com quatro, as 28 peças acabam na distribuição: não existe compra. */
    @Test
    fun `a mesa de quatro nao tem monte`() {
        val state = DominoesGame.initialState(MatchConfig(seed = 3, seats = 4))
        assertTrue(state.boneyard.isEmpty())
    }

    @Test
    fun `a vez percorre a mesa inteira, e nao vai e volta`() {
        for (seats in mesas) {
            var state = DominoesGame.initialState(MatchConfig(seed = 21, seats = seats))
            val vistas = mutableSetOf(state.turn.index)
            var guard = 0

            while (!DominoesGame.outcome(state).isOver && guard++ < 30) {
                val move = DominoesGame.legalMoves(state).firstOrNull() ?: break
                state = DominoesGame.applyOrThrow(state, move)
                vistas += state.turn.index
            }
            assertTrue(
                vistas.size >= minOf(seats, 3),
                "mesa de $seats: a vez só passou por $vistas",
            )
            assertTrue(vistas.all { it in 0 until seats }, "mesa de $seats: vez em cadeira inexistente")
        }
    }

    @Test
    fun `uma partida inteira termina em qualquer tamanho de mesa`() {
        for (seats in mesas) {
            var state = DominoesGame.initialState(MatchConfig(seed = 2026, seats = seats))
            var guard = 0
            while (!DominoesGame.outcome(state).isOver && guard++ < 200) {
                state = DominoesGame.applyOrThrow(state, DominoesGame.legalMoves(state).first())
            }
            assertTrue(
                DominoesGame.outcome(state).isOver,
                "mesa de $seats: a partida não terminou em $guard lances",
            )
            val vencedor = DominoesGame.outcome(state)
            if (vencedor is Outcome.Win) {
                assertTrue(vencedor.seat.index in 0 until seats, "venceu uma cadeira que não existe")
            }
        }
    }

    /** Fechado, ganha quem tem menos pontos — e a conta agora olha a mesa toda. */
    @Test
    fun `no jogo fechado vence a menor mao entre todas`() {
        val fechado = DominoesState(
            line = listOf(io.github.andre88br.newgame.core.games.dominoes.PlacedTile(6, 6)),
            hands = listOf(
                listOf(Tile(0, 5)),
                listOf(Tile(0, 1)),
                listOf(Tile(4, 5)),
            ),
            boneyard = emptyList(),
            turn = Seat.FIRST,
            passes = 3,
        )
        assertEquals(Outcome.Win(Seat.SECOND), DominoesGame.outcome(fechado))
    }

    @Test
    fun `a mao de todos os outros fica escondida, e nao so a de um`() {
        val state = DominoesGame.initialState(MatchConfig(seed = 9, seats = 4))
        val visto = DominoesGame.redactFor(state, Seat.FIRST)

        assertEquals(state.hand(Seat.FIRST), visto.hand(Seat.FIRST), "a própria mão continua visível")
        for (index in 1 until 4) {
            val mao = visto.hand(Seat(index))
            assertEquals(
                state.handSize(Seat(index)),
                mao.size,
                "a contagem de peças da cadeira $index precisa continuar visível",
            )
            assertTrue(mao.all { it.isHidden }, "a cadeira $index vazou a mão")
        }
    }

    // -------- ludo --------

    @Test
    fun `o ludo monta quatro peoes por cadeira em qualquer mesa`() {
        for (seats in mesas) {
            val state = LudoGame.initialState(MatchConfig(seed = 7, seats = seats))
            assertEquals(seats, state.seats)
            for (index in 0 until seats) {
                assertEquals(LUDO_TOKENS, state.tokensOf(Seat(index)).size)
            }
        }
    }

    @Test
    fun `uma partida de ludo termina com qualquer numero de jogadores`() {
        for (seats in mesas) {
            var state = LudoGame.initialState(MatchConfig(seed = 1234, seats = seats))
            var guard = 0
            while (!LudoGame.outcome(state).isOver && guard++ < 2000) {
                state = LudoGame.applyOrThrow(state, LudoGame.legalMoves(state).first())
            }
            assertTrue(
                LudoGame.outcome(state).isOver,
                "mesa de $seats: a partida não terminou em $guard lances",
            )
        }
    }

    /**
     * Com três ou quatro, um peão pode cair numa casa onde há peão de **duas** cores
     * diferentes. Mandar só um deles para o curral seria o erro natural de quem escreveu a
     * regra pensando em dois jogadores.
     */
    @Test
    fun `a captura no ludo alcanca todos os adversarios daquela casa`() {
        val seats = 4
        val alvo = 3
        val absoluta = io.github.andre88br.newgame.core.games.ludo.absoluteSquare(Seat.FIRST, alvo, seats)!!
        assertTrue(!io.github.andre88br.newgame.core.games.ludo.isSafeSquare(absoluta))

        fun progressoDe(seat: Seat): Int =
            (absoluta - io.github.andre88br.newgame.core.games.ludo.startSquare(seat, seats) +
                io.github.andre88br.newgame.core.games.ludo.LUDO_TRACK) %
                io.github.andre88br.newgame.core.games.ludo.LUDO_TRACK

        val curral = io.github.andre88br.newgame.core.games.ludo.LUDO_YARD
        val antes = LudoState(
            tokens = listOf(
                listOf(0, curral, curral, curral),
                listOf(progressoDe(Seat(1)), curral, curral, curral),
                listOf(progressoDe(Seat(2)), curral, curral, curral),
                listOf(curral, curral, curral, curral),
            ),
            turn = Seat.FIRST,
            die = 3,
            rng = Rng.seeded(1),
        )

        assertTrue(LudoGame.isCapture(antes, io.github.andre88br.newgame.core.games.ludo.LudoMove(0)))
        val depois = LudoGame.applyOrThrow(antes, io.github.andre88br.newgame.core.games.ludo.LudoMove(0))

        assertEquals(curral, depois.tokensOf(Seat(1))[0], "a cadeira 1 devia ter voltado ao curral")
        assertEquals(curral, depois.tokensOf(Seat(2))[0], "a cadeira 2 devia ter voltado ao curral")
    }

    // -------- a mesa inteira, pela sessão --------

    @Test
    fun `uma pessoa contra tres maquinas joga do comeco ao fim`() {
        for (entry in jogosComMesaGrande()) {
            val config = MatchConfig(seed = 88, seats = 4)
            val session = MatchSession(
                entry = entry,
                config = config,
                players = buildMap {
                    put(Seat.FIRST, Player.Human)
                    for (index in 1 until 4) put(Seat(index), Player.Ai(Difficulty.EASY))
                },
            )

            var guard = 0
            while (!session.isOver && guard++ < 800) {
                if (session.awaitingAi) {
                    assertTrue(session.playAiTurn() != null, "${entry.id}: a máquina não jogou")
                } else {
                    val move = entry.rules.legalMoves(session.state).firstOrNull() ?: break
                    session.play(move)
                }
            }
            assertTrue(session.isOver, "${entry.id}: a partida de quatro não terminou")
        }
    }
}
