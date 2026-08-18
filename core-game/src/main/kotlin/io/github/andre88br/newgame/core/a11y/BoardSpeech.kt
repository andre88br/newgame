package io.github.andre88br.newgame.core.a11y

import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.engine.TextTemplate
import io.github.andre88br.newgame.core.engine.format
import io.github.andre88br.newgame.core.games.checkers.CheckersState
import io.github.andre88br.newgame.core.games.checkers.EMPTY
import io.github.andre88br.newgame.core.games.checkers.isKing
import io.github.andre88br.newgame.core.games.checkers.pdnNumber
import io.github.andre88br.newgame.core.games.checkers.pieceOwner
import io.github.andre88br.newgame.core.games.chess.CHESS_EMPTY
import io.github.andre88br.newgame.core.games.chess.ChessState
import io.github.andre88br.newgame.core.games.chess.pieceSeat
import io.github.andre88br.newgame.core.games.chess.squareName
import io.github.andre88br.newgame.core.games.dominoes.LineEnd
import io.github.andre88br.newgame.core.games.dominoes.Tile
import io.github.andre88br.newgame.core.games.ludo.LUDO_GOAL
import io.github.andre88br.newgame.core.games.ludo.LUDO_YARD
import io.github.andre88br.newgame.core.games.reversi.REVERSI_SIZE
import io.github.andre88br.newgame.core.games.reversi.ReversiState
import io.github.andre88br.newgame.core.games.reversi.discOwner
import io.github.andre88br.newgame.core.games.tictactoe.TicTacToeState

/**
 * O vocabulário que descreve um tabuleiro em voz alta.
 *
 * Cada combinação de peça e cor é uma chave própria — `PAWN_FIRST` e `PAWN_SECOND`, e não
 * "peão" mais "branco". Parece repetitivo e não é: em português o adjetivo concorda com o
 * substantivo ("peão branco", mas "torre branca", "dama branca"), então montar a frase
 * juntando dois pedaços produziria concordância errada em metade dos casos. Cada idioma
 * escreve a frase inteira do jeito que a língua dele pede.
 */
enum class SpeechKey(
    override val template: String,
    override val arity: Int = 0,
) : TextTemplate {

    // -------- moldura --------

    SQUARE_EMPTY("%1\$s, vazia", arity = 1),
    SQUARE_WITH("%1\$s, %2\$s", arity = 2),

    // -------- jogo da velha --------

    MARK_X("xis"),
    MARK_O("bola"),

    // -------- damas --------

    MAN_WHITE("pedra branca"),
    MAN_BLACK("pedra preta"),
    KING_WHITE("dama branca"),
    KING_BLACK("dama preta"),

    // -------- reversi --------

    DISC_DARK("peça preta"),
    DISC_LIGHT("peça branca"),

    // -------- xadrez --------

    PAWN_WHITE("peão branco"),
    KNIGHT_WHITE("cavalo branco"),
    BISHOP_WHITE("bispo branco"),
    ROOK_WHITE("torre branca"),
    QUEEN_WHITE("dama branca"),
    CHESS_KING_WHITE("rei branco"),
    PAWN_BLACK("peão preto"),
    KNIGHT_BLACK("cavalo preto"),
    BISHOP_BLACK("bispo preto"),
    ROOK_BLACK("torre preta"),
    QUEEN_BLACK("dama preta"),
    CHESS_KING_BLACK("rei preto"),

    // -------- dominó --------

    TILE("peça %1\$s por %2\$s", arity = 2),
    TILE_HIDDEN("peça virada para baixo"),
    TILE_FITS_BOTH("%1\$s, encaixa nas duas pontas", arity = 1),
    TILE_FITS_LEFT("%1\$s, encaixa na ponta esquerda", arity = 1),
    TILE_FITS_RIGHT("%1\$s, encaixa na ponta direita", arity = 1),
    TILE_FITS_NOWHERE("%1\$s, não encaixa", arity = 1),

    // -------- ludo --------

    TOKEN_IN_YARD("peão %1\$s, no curral", arity = 1),
    TOKEN_HOME("peão %1\$s, na chegada", arity = 1),
    TOKEN_ON_LANE("peão %1\$s, no corredor final, %2\$s casas para chegar", arity = 2),
    TOKEN_ON_TRACK("peão %1\$s, andou %2\$s casas", arity = 2),
    ;

    /** `speech_square_empty`, e assim por diante. */
    override val resourceName: String get() = "speech_" + name.lowercase()
}

/** Uma frase falada: a chave e os argumentos já resolvidos. */
data class Speech(val key: SpeechKey, val args: List<String> = emptyList()) {
    fun text(): String = key.format(args)
    override fun toString(): String = text()
}

private fun speechOf(key: SpeechKey, vararg args: Any) = Speech(key, args.map { it.toString() })

/**
 * Uma casa descrita em duas partes: como ela se chama e o que está nela.
 *
 * Separado porque a montagem final depende do idioma — o app junta as duas com
 * [SpeechKey.SQUARE_WITH] traduzido, em vez de receber a frase pronta em português.
 */
data class SquareSpeech(
    /** O nome da casa, já em texto: "e4", "casa 12". Número e letra não se traduzem. */
    val name: String,
    /** O que ocupa a casa, ou `null` se estiver vazia. */
    val occupant: SpeechKey?,
) {
    /** A frase inteira em português — referência para os testes e reserva do app. */
    fun text(): String = when (occupant) {
        null -> speechOf(SpeechKey.SQUARE_EMPTY, name).text()
        else -> speechOf(SpeechKey.SQUARE_WITH, name, occupant.template).text()
    }
}

/**
 * Descreve o tabuleiro para quem não o está vendo.
 *
 * Isto existe no `core-game`, e não na tela, porque é conhecimento sobre o jogo, não sobre
 * Android: saber que a casa 12 tem uma dama branca é a mesma pergunta que o desenhista faz
 * para pintar a casa. O que a tela faz com a resposta — virar `contentDescription`, mandar
 * para o TalkBack — é assunto dela.
 *
 * E, principalmente: aqui isto é testável. Descrição de acessibilidade errada é o tipo de
 * defeito que passa despercebido para sempre, porque quem desenvolve não usa leitor de tela.
 */
object BoardSpeech {

    /** A casa [square] do jogo [entry], descrita. `null` quando a casa não é do jogo. */
    fun square(entry: GameEntry, state: GameState, square: Int): SquareSpeech? =
        when (entry.id) {
            GameId.TIC_TAC_TOE -> ticTacToe(state as TicTacToeState, square)
            GameId.CHECKERS -> checkers(state as CheckersState, square)
            GameId.REVERSI -> reversi(state as ReversiState, square)
            GameId.CHESS -> chess(state as ChessState, square)
            // Dominó, ludo e os jogos de carta não têm casas de grade: veja [tile], [token]
            // e, nas cartas, a descrição que a própria tela monta a partir da mão.
            GameId.DOMINOES, GameId.LUDO, GameId.HEARTS, GameId.CANASTRA, GameId.PIFE,
            GameId.KLONDIKE, GameId.TRUCO, GameId.POKER,
            -> null
        }

    // -------- jogos de grade --------

    private fun ticTacToe(state: TicTacToeState, square: Int): SquareSpeech? {
        if (square !in state.cells.indices) return null
        val occupant = when (state.cells[square]) {
            Seat.FIRST.index -> SpeechKey.MARK_X
            Seat.SECOND.index -> SpeechKey.MARK_O
            else -> null
        }
        return SquareSpeech(algebraic(square, columns = 3, rows = 3), occupant)
    }

    private fun checkers(state: CheckersState, square: Int): SquareSpeech? {
        if (square !in state.board.indices) return null
        val piece = state.board[square]
        val occupant = when {
            piece == EMPTY -> null
            piece.pieceOwner() == Seat.FIRST && piece.isKing() -> SpeechKey.KING_WHITE
            piece.pieceOwner() == Seat.FIRST -> SpeechKey.MAN_WHITE
            piece.isKing() -> SpeechKey.KING_BLACK
            else -> SpeechKey.MAN_BLACK
        }
        // A numeração PDN é a que quem joga damas usa para ditar lance.
        return SquareSpeech(pdnNumber(square).toString(), occupant)
    }

    private fun reversi(state: ReversiState, square: Int): SquareSpeech? {
        if (square !in state.board.indices) return null
        // No reversi quem abre é o preto, e a primeira cadeira é justamente ele. Falar
        // "branca" aqui contradiria o que está desenhado na tela.
        val occupant = when (state.board[square].discOwner()) {
            Seat.FIRST -> SpeechKey.DISC_DARK
            Seat.SECOND -> SpeechKey.DISC_LIGHT
            else -> null
        }
        return SquareSpeech(algebraic(square, columns = REVERSI_SIZE, rows = REVERSI_SIZE), occupant)
    }

    private fun chess(state: ChessState, square: Int): SquareSpeech? {
        if (square !in state.board.indices) return null
        val piece = state.board[square]
        if (piece == CHESS_EMPTY) return SquareSpeech(squareName(square), null)

        val first = piece.pieceSeat() == Seat.FIRST
        val occupant = when (piece.uppercaseChar()) {
            'P' -> if (first) SpeechKey.PAWN_WHITE else SpeechKey.PAWN_BLACK
            'N' -> if (first) SpeechKey.KNIGHT_WHITE else SpeechKey.KNIGHT_BLACK
            'B' -> if (first) SpeechKey.BISHOP_WHITE else SpeechKey.BISHOP_BLACK
            'R' -> if (first) SpeechKey.ROOK_WHITE else SpeechKey.ROOK_BLACK
            'Q' -> if (first) SpeechKey.QUEEN_WHITE else SpeechKey.QUEEN_BLACK
            else -> if (first) SpeechKey.CHESS_KING_WHITE else SpeechKey.CHESS_KING_BLACK
        }
        return SquareSpeech(squareName(square), occupant)
    }

    /**
     * Nome de casa em letra e número, contando de baixo para cima como num tabuleiro de
     * verdade: o índice zero é o canto de cima à esquerda, mas quem joga chama aquilo de a8.
     */
    private fun algebraic(square: Int, columns: Int, rows: Int): String {
        val column = square % columns
        val row = square / columns
        return "${'a' + column}${rows - row}"
    }

    // -------- dominó --------

    /** Uma peça da mão, com o que dá para fazer com ela. */
    fun tile(tile: Tile, ends: List<LineEnd>): Speech {
        if (tile.isHidden) return Speech(SpeechKey.TILE_HIDDEN)
        val name = speechOf(SpeechKey.TILE, tile.low, tile.high).text()
        return when {
            ends.size >= 2 -> speechOf(SpeechKey.TILE_FITS_BOTH, name)
            ends.singleOrNull() == LineEnd.LEFT -> speechOf(SpeechKey.TILE_FITS_LEFT, name)
            ends.singleOrNull() == LineEnd.RIGHT -> speechOf(SpeechKey.TILE_FITS_RIGHT, name)
            else -> speechOf(SpeechKey.TILE_FITS_NOWHERE, name)
        }
    }

    // -------- ludo --------

    /** Um peão, pela posição que ocupa. [token] é o índice, falado a partir de 1. */
    fun token(token: Int, progress: Int): Speech {
        val numero = token + 1
        return when {
            progress == LUDO_YARD -> speechOf(SpeechKey.TOKEN_IN_YARD, numero)
            progress >= LUDO_GOAL -> speechOf(SpeechKey.TOKEN_HOME, numero)
            progress >= LUDO_GOAL - 5 -> speechOf(SpeechKey.TOKEN_ON_LANE, numero, LUDO_GOAL - progress)
            else -> speechOf(SpeechKey.TOKEN_ON_TRACK, numero, progress)
        }
    }
}
