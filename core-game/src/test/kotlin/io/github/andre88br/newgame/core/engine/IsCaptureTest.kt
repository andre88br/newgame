package io.github.andre88br.newgame.core.engine

import io.github.andre88br.newgame.core.games.checkers.CheckersGame
import io.github.andre88br.newgame.core.games.checkers.positionOf
import io.github.andre88br.newgame.core.games.checkers.squareAt
import io.github.andre88br.newgame.core.games.chess.ChessGame
import io.github.andre88br.newgame.core.games.chess.ChessMove
import io.github.andre88br.newgame.core.games.chess.chessStateFromFen
import io.github.andre88br.newgame.core.games.chess.squareOf
import io.github.andre88br.newgame.core.games.ludo.LUDO_TRACK
import io.github.andre88br.newgame.core.games.ludo.LUDO_YARD
import io.github.andre88br.newgame.core.games.ludo.LudoMove
import io.github.andre88br.newgame.core.games.ludo.LudoState
import io.github.andre88br.newgame.core.games.ludo.LudoGame
import io.github.andre88br.newgame.core.games.ludo.absoluteSquare
import io.github.andre88br.newgame.core.games.ludo.isSafeSquare
import io.github.andre88br.newgame.core.games.ludo.startSquare
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A tela toca um som diferente quando alguma coisa sai do tabuleiro. Quem sabe se saiu é o
 * motor — e é aqui que essa resposta é conferida, porque errá-la não quebra nada: só faz o
 * app soar igual o tempo todo, que é o tipo de defeito que ninguém abre um chamado.
 */
class IsCaptureTest {

    private fun at(name: String) = squareOf(name)!!

    @Test
    fun `nas damas so a captura conta como captura`() {
        val comCaptura = positionOf(
            Seat.FIRST,
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . b . . . .",
            ". . w . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val captura = CheckersGame.legalMoves(comCaptura).single()
        assertTrue(CheckersGame.isCapture(comCaptura, captura), "a captura obrigatória não foi vista")

        val semCaptura = positionOf(
            Seat.FIRST,
            ". b . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
            ". . w . . . . .",
            ". . . . . . . .",
            ". . . . . . . .",
        )
        val andar = CheckersGame.legalMoves(semCaptura).first { it.from == squareAt(5, 2) }
        assertFalse(CheckersGame.isCapture(semCaptura, andar))
    }

    @Test
    fun `no xadrez a captura comum conta`() {
        // Peão branco em e4, peão preto em d5: exd5 é captura, e4-e5 não é.
        val state = chessStateFromFen("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1")
        assertTrue(ChessGame.isCapture(state, ChessMove(at("e4"), at("d5"))))
        assertFalse(ChessGame.isCapture(state, ChessMove(at("e4"), at("e5"))))
    }

    /**
     * O caso que um `board[to] != vazio` sozinho erraria: na captura en passant a casa de
     * destino está **vazia**, e a peça capturada sai de uma casa vizinha.
     */
    @Test
    fun `no xadrez a captura en passant tambem conta`() {
        val state = chessStateFromFen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1")
        val enPassant = ChessMove(at("e5"), at("d6"))

        assertTrue(enPassant in ChessGame.legalMoves(state), "a posição de teste precisa permitir o lance")
        assertTrue(ChessGame.isCapture(state, enPassant), "en passant é captura mesmo com a casa vazia")
    }

    @Test
    fun `no ludo pisar em peao adversario conta, e casa segura nao`() {
        val alvo = 3
        val absoluta = absoluteSquare(Seat.FIRST, alvo)!!
        assertFalse(isSafeSquare(absoluta), "a casa do teste precisa ser comum")

        val progressoAdversario = (absoluta - startSquare(Seat.SECOND) + LUDO_TRACK) % LUDO_TRACK
        val comAlvo = LudoState(
            tokens = listOf(
                listOf(0, LUDO_YARD, LUDO_YARD, LUDO_YARD),
                listOf(progressoAdversario, LUDO_YARD, LUDO_YARD, LUDO_YARD),
            ),
            turn = Seat.FIRST,
            die = 3,
            rng = Rng.seeded(1),
        )
        assertTrue(LudoGame.isCapture(comAlvo, LudoMove(0)))

        // Mesma casa, sem ninguém nela: andar não é capturar.
        val vazio = comAlvo.copy(
            tokens = listOf(
                listOf(0, LUDO_YARD, LUDO_YARD, LUDO_YARD),
                listOf(LUDO_YARD, LUDO_YARD, LUDO_YARD, LUDO_YARD),
            ),
        )
        assertFalse(LudoGame.isCapture(vazio, LudoMove(0)))
    }

    @Test
    fun `os jogos em que nada sai do tabuleiro nunca dizem captura`() {
        // Reversi, velha e dominó: no reversi todo lance vira peça, e virar não é tirar.
        for (entry in GameCatalog.available) {
            if (entry.id in setOf(GameId.CHECKERS, GameId.CHESS, GameId.LUDO)) continue
            val state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            for (move in entry.rules.legalMoves(state)) {
                assertFalse(
                    entry.rules.isCapture(state, move),
                    "${entry.id} chamou de captura o lance ${move.describe()}",
                )
            }
        }
    }

    @Test
    fun `perguntar por captura nunca estoura, em nenhum jogo`() {
        // A tela chama isto a cada lance, inclusive em posições estranhas.
        for (entry in GameCatalog.available) {
            var state = entry.rules.initialState(MatchConfig.DETERMINISTIC)
            repeat(12) {
                val moves = entry.rules.legalMoves(state)
                if (moves.isEmpty()) return@repeat
                moves.forEach { entry.rules.isCapture(state, it) }
                state = entry.rules.applyOrThrow(state, moves.first())
            }
        }
    }
}
