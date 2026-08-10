package io.github.andre88br.newgame.core.session

import io.github.andre88br.newgame.core.engine.GameCatalog
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.applyOrThrow
import io.github.andre88br.newgame.core.games.checkers.BOARD_CELLS
import io.github.andre88br.newgame.core.games.checkers.CheckersGame
import io.github.andre88br.newgame.core.games.checkers.CheckersInteractor
import io.github.andre88br.newgame.core.games.checkers.CheckersMove
import io.github.andre88br.newgame.core.games.checkers.positionOf
import io.github.andre88br.newgame.core.games.checkers.squareAt
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeGame
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeInteractor
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeMove
import io.github.andre88br.newgame.core.engine.ReasonKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoardInteractionTest {

    // -------- jogo da velha --------

    @Test
    fun `na velha um toque ja e o lance`() {
        val state = TicTacToeGame.initialState(MatchConfig.DETERMINISTIC)
        val result = TicTacToeInteractor.tap(state, selected = null, square = 4)
        assertEquals(TapResult.Play(TicTacToeMove(4)), result)
    }

    @Test
    fun `tocar em casa ocupada explica o motivo`() {
        val state = TicTacToeGame.applyOrThrow(
            TicTacToeGame.initialState(MatchConfig.DETERMINISTIC),
            TicTacToeMove(0),
        )
        val result = TicTacToeInteractor.tap(state, selected = null, square = 0)
        assertIs<TapResult.Rejected>(result)
        assertEquals(ReasonKey.SQUARE_TAKEN, result.reason.key)
    }

    @Test
    fun `depois da partida encerrada os toques nao fazem nada`() {
        var state = TicTacToeGame.initialState(MatchConfig.DETERMINISTIC)
        listOf(0, 3, 1, 4, 2).forEach { state = TicTacToeGame.applyOrThrow(state, TicTacToeMove(it)) }
        assertEquals(TapResult.Ignored, TicTacToeInteractor.tap(state, null, 8))
    }

    // -------- damas --------

    /**
     * Duas pedras brancas sem captura disponível. A preta no canto de cima existe só para
     * a partida não estar ganha — sem peça adversária o motor declara vitória e todo toque
     * vira [TapResult.Ignored].
     */
    private val simples = positionOf(
        Seat.FIRST,
        ". b . . . . . .",
        ". . . . . . . .",
        ". . . . . . . .",
        ". . . . . . . .",
        ". . . . . . . .",
        ". . w . . . . .",
        ". . . . . w . .",
        ". . . . . . . .",
    )

    @Test
    fun `o primeiro toque escolhe a peca e ja calcula os destinos`() {
        val result = CheckersInteractor.tap(simples, selected = null, square = squareAt(5, 2))
        assertIs<TapResult.Select>(result)
        assertEquals(squareAt(5, 2), result.square)
        assertEquals(setOf(squareAt(4, 1), squareAt(4, 3)), result.destinations.toSet())
    }

    @Test
    fun `o segundo toque no destino completa o lance`() {
        val from = squareAt(5, 2)
        val to = squareAt(4, 3)
        val result = CheckersInteractor.tap(simples, selected = from, square = to)

        assertIs<TapResult.Play>(result)
        val move = result.move as CheckersMove
        assertEquals(from, move.from)
        assertEquals(to, move.to)
        assertTrue(move in CheckersGame.legalMoves(simples))
    }

    @Test
    fun `tocar de novo na peca escolhida desfaz a escolha`() {
        val square = squareAt(5, 2)
        assertEquals(TapResult.Deselect, CheckersInteractor.tap(simples, selected = square, square = square))
    }

    @Test
    fun `tocar em outra peca sua troca a escolha`() {
        val result = CheckersInteractor.tap(simples, selected = squareAt(5, 2), square = squareAt(6, 5))
        assertIs<TapResult.Select>(result)
        assertEquals(squareAt(6, 5), result.square)
    }

    @Test
    fun `destino invalido e recusado sem perder a escolha`() {
        val result = CheckersInteractor.tap(simples, selected = squareAt(5, 2), square = squareAt(3, 0))
        assertIs<TapResult.Rejected>(result)
        assertEquals(ReasonKey.PIECE_CANNOT_GO_THERE, result.reason.key, "motivo: ${result.reason}")
    }

    @Test
    fun `tocar em casa vazia sem nada escolhido nao faz nada`() {
        assertEquals(TapResult.Ignored, CheckersInteractor.tap(simples, null, squareAt(3, 0)))
    }

    @Test
    fun `tocar em peca do adversario nao faz nada`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . b . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . w . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        assertEquals(TapResult.Ignored, CheckersInteractor.tap(state, null, squareAt(1, 2)))
    }

    /**
     * O caso que motiva a mensagem existir: com captura disponível em outra peça, tocar
     * numa peça qualquer não produz lance nenhum. Sem explicação, parece defeito do app.
     */
    @Test
    fun `peca sem lance por causa da captura obrigatoria explica isso`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . w . . . . .",
            ". . . . . w . .",
            ". . . . . . . .",
        )
        // A peça em (6,5) não tem lance: a de (5,2) é obrigada a capturar.
        val result = CheckersInteractor.tap(state, selected = null, square = squareAt(6, 5))
        assertIs<TapResult.Rejected>(result)
        assertEquals(ReasonKey.CAPTURE_MANDATORY_ELSEWHERE, result.reason.key, "motivo: ${result.reason}")

        // E a peça obrigada a capturar continua selecionável normalmente.
        val capture = CheckersInteractor.tap(state, selected = null, square = squareAt(5, 2))
        assertIs<TapResult.Select>(capture)
        assertEquals(listOf(squareAt(3, 4)), capture.destinations)
    }

    @Test
    fun `a captura em sequencia se joga tocando so na casa final`() {
        val state = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . . . . . . .",
            ". . . . . b . .",
            ". . . . . . w .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val from = squareAt(5, 6)
        val selection = CheckersInteractor.tap(state, null, from)
        assertIs<TapResult.Select>(selection)
        assertEquals(listOf(squareAt(1, 2)), selection.destinations, "só a casa final é destino")

        val result = CheckersInteractor.tap(state, selected = from, square = squareAt(1, 2))
        assertIs<TapResult.Play>(result)
        assertEquals(2, (result.move as CheckersMove).captured.size)
    }

    // -------- geometria, usada pela tela --------

    @Test
    fun `o interator informa o tamanho do tabuleiro`() {
        assertEquals(3, TicTacToeInteractor.rows)
        assertEquals(3, TicTacToeInteractor.columns)
        assertEquals(8, CheckersInteractor.rows)
        assertEquals(8, CheckersInteractor.columns)
    }

    @Test
    fun `linha e coluna viram indice de casa`() {
        assertEquals(4, TicTacToeInteractor.squareAt(1, 1))
        assertEquals(squareAt(5, 2), CheckersInteractor.squareAt(5, 2))
    }

    @Test
    fun `nas damas so as casas escuras sao jogaveis`() {
        assertEquals(32, (0 until BOARD_CELLS).count { CheckersInteractor.isPlayable(it) })
        assertTrue(CheckersInteractor.isPlayable(squareAt(0, 1)))
        assertTrue(!CheckersInteractor.isPlayable(squareAt(0, 0)))
    }

    @Test
    fun `na velha toda casa e jogavel`() {
        assertEquals(9, (0 until 9).count { TicTacToeInteractor.isPlayable(it) })
    }

    // -------- vale para todo jogo do catálogo --------

    @Test
    fun `todo jogo do catalogo tem um interator coerente com as regras`() {
        for (entry in GameCatalog.available) {
            // Dominó e ludo não se jogam tocando em casas: a tela deles monta o lance.
            val interactor = entry.interactor ?: continue
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)

            assertTrue(interactor.rows > 0 && interactor.columns > 0, "${entry.id} sem tamanho")

            // Percorrer o tabuleiro inteiro tocando em cada casa não pode estourar, e todo
            // lance produzido tem que ser legal.
            val legal = entry.rules.legalMoves(state)
            for (square in 0 until interactor.rows * interactor.columns) {
                when (val result = interactor.tap(state, null, square)) {
                    is TapResult.Play ->
                        assertTrue(result.move in legal, "${entry.id}: toque em $square gerou lance ilegal")

                    is TapResult.Select -> {
                        val destinations = legal.map { move ->
                            entry.rules.encodeMove(move)
                        }
                        assertTrue(
                            result.destinations.isNotEmpty(),
                            "${entry.id}: seleção sem destino em $square (${destinations.size} lances legais)",
                        )
                    }

                    else -> Unit
                }
            }
        }
    }

    @Test
    fun `squaresOf cobre tudo o que muda de aparencia no lance`() {
        assertEquals(listOf(4), TicTacToeInteractor.squaresOf(TicTacToeMove(4)))

        val sequencia = CheckersMove(
            from = squareAt(5, 6),
            path = listOf(squareAt(3, 4), squareAt(1, 2)),
            captured = listOf(squareAt(4, 5), squareAt(2, 3)),
        )
        assertEquals(
            setOf(squareAt(5, 6), squareAt(3, 4), squareAt(1, 2), squareAt(4, 5), squareAt(2, 3)),
            CheckersInteractor.squaresOf(sequencia).toSet(),
        )
    }

    @Test
    fun `squaresOf devolve casas dentro do tabuleiro em todo jogo`() {
        for (entry in GameCatalog.available) {
            val interactor = entry.interactor ?: continue
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            val limit = interactor.rows * interactor.columns
            for (move in entry.rules.legalMoves(state)) {
                val squares = interactor.squaresOf(move)
                assertTrue(squares.isNotEmpty(), "${entry.id}: lance sem casas — ${move.describe()}")
                assertTrue(
                    squares.all { it in 0 until limit },
                    "${entry.id}: casa fora do tabuleiro em ${move.describe()} — $squares",
                )
            }
        }
    }

    @Test
    fun `so o jogo da velha existe com id conhecido no catalogo`() {
        assertEquals(TicTacToeInteractor, GameCatalog.entry(GameId.TIC_TAC_TOE).interactor)
        assertEquals(CheckersInteractor, GameCatalog.entry(GameId.CHECKERS).interactor)
    }
}
