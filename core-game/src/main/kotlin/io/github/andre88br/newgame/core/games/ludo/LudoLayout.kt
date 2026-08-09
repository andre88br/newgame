package io.github.andre88br.newgame.core.games.ludo

import io.github.andre88br.newgame.core.engine.Seat
import kotlin.math.abs

/** Lado do tabuleiro em cruz, em casas. */
const val LUDO_GRID = 15

/** Uma casa do desenho, em linha e coluna da grade [LUDO_GRID] × [LUDO_GRID]. */
data class LudoCell(val row: Int, val column: Int)

/** Para que serve cada casa da grade — é o que dá a cor no desenho. */
enum class LudoCellKind {
    /** Volta principal, comum às duas cores. */
    TRACK,

    /** Casa marcada da volta: peão parado ali não é capturado. */
    SAFE,

    /** Corredor final da primeira cadeira. */
    HOME_FIRST,

    /** Corredor final da segunda cadeira. */
    HOME_SECOND,

    /** O centro da cruz: é onde os peões chegam. */
    GOAL,

    /** Curral da primeira cadeira. */
    YARD_FIRST,

    /** Curral da segunda cadeira. */
    YARD_SECOND,

    /**
     * Corredor das duas cores que não jogam nesta partida.
     *
     * A cruz tem quatro braços porque o ludo tem quatro cores; a partida de dois usa duas.
     * Desenhar os outros dois corredores em cinza é mais honesto do que deixar um buraco no
     * meio do braço.
     */
    LANE_UNUSED,

    /** Fora da cruz: não faz parte do jogo. */
    OUTSIDE,
}

/**
 * Onde cada casa do ludo fica desenhada.
 *
 * Mora no `core-game`, e não na tela, por um motivo prático: é geometria pura, com invariantes
 * que dá para verificar sozinho — as 52 casas da volta são distintas, formam um circuito
 * fechado sem buraco, e o corredor final de cada cor encosta na última casa da volta daquela
 * cor. Errar um número aqui deslocaria peões pelo tabuleiro inteiro, e é o tipo de erro que a
 * inspeção visual perde. No módulo do app não haveria como testar isso.
 *
 * A grade é a clássica cruz de 15 × 15: braços de três casas de largura, corredores finais na
 * linha e na coluna do meio, currais nos quatro cantos.
 */
object LudoLayout {

    /**
     * As 52 casas da volta, na ordem em que se anda.
     *
     * O índice é a casa absoluta que [absoluteSquare] devolve: `ring[0]` é a saída da primeira
     * cadeira e `ring[26]` a da segunda, diagonalmente oposta.
     */
    val ring: List<LudoCell> = buildList {
        add(LudoCell(6, 0))
        for (column in 1..5) add(LudoCell(6, column))
        for (row in 5 downTo 0) add(LudoCell(row, 6))
        add(LudoCell(0, 7))
        for (row in 0..5) add(LudoCell(row, 8))
        for (column in 9..14) add(LudoCell(6, column))
        add(LudoCell(7, 14))
        for (column in 14 downTo 9) add(LudoCell(8, column))
        for (row in 9..14) add(LudoCell(row, 8))
        add(LudoCell(14, 7))
        for (row in 14 downTo 9) add(LudoCell(row, 6))
        for (column in 5 downTo 0) add(LudoCell(8, column))
        add(LudoCell(7, 0))
    }

    /** Casa da volta de índice absoluto [absolute]. */
    fun trackCell(absolute: Int): LudoCell = ring[Math.floorMod(absolute, LUDO_TRACK)]

    /**
     * Casa do corredor final, com [step] indo de 0 (a primeira depois da volta) até
     * [LUDO_HOME_LANE] — que já é a chegada.
     *
     * Os dois corredores ocupam a linha do meio, um vindo de cada lado, e se encontram no
     * centro. Cada cor entra no seu logo depois de completar a volta.
     */
    fun laneCell(seat: Seat, step: Int): LudoCell {
        require(step in 0..LUDO_HOME_LANE) { "Passo $step fora do corredor final" }
        return if (seat == Seat.FIRST) {
            LudoCell(7, 1 + step)
        } else {
            LudoCell(7, 13 - step)
        }
    }

    /** A chegada de cada cor, no centro do tabuleiro. */
    fun goalCell(seat: Seat): LudoCell = laneCell(seat, LUDO_HOME_LANE)

    /** Onde fica o peão [token] enquanto está no curral. */
    fun yardCell(seat: Seat, token: Int): LudoCell {
        require(token in 0 until LUDO_TOKENS) { "Peão $token não existe" }
        val row = if (token < 2) 1 else 4
        val column = if (token % 2 == 0) 1 else 4
        return if (seat == Seat.FIRST) {
            LudoCell(row, column)
        } else {
            LudoCell(row + 9, column + 9)
        }
    }

    /**
     * Onde desenhar o peão [token] de [seat], seja qual for a situação dele: curral, volta,
     * corredor final ou chegada.
     */
    fun cellFor(seat: Seat, progress: Int, token: Int): LudoCell = when {
        progress == LUDO_YARD -> yardCell(seat, token)
        progress >= LUDO_TRACK -> laneCell(seat, progress - LUDO_TRACK)
        else -> trackCell(absoluteSquare(seat, progress)!!)
    }

    /** Para que serve a casa — o desenho pinta cada tipo de um jeito. */
    fun kindOf(cell: LudoCell): LudoCellKind {
        val absolute = ring.indexOf(cell)
        if (absolute >= 0) return if (isSafeSquare(absolute)) LudoCellKind.SAFE else LudoCellKind.TRACK

        if (cell.row in 6..8 && cell.column in 6..8) return LudoCellKind.GOAL

        if (cell.row == 7 && cell.column in 1..5) return LudoCellKind.HOME_FIRST
        if (cell.row == 7 && cell.column in 9..13) return LudoCellKind.HOME_SECOND
        if (cell.column == 7 && (cell.row in 1..5 || cell.row in 9..13)) return LudoCellKind.LANE_UNUSED

        if (cell.row in 0..5 && cell.column in 0..5) return LudoCellKind.YARD_FIRST
        if (cell.row in 9..14 && cell.column in 9..14) return LudoCellKind.YARD_SECOND

        return LudoCellKind.OUTSIDE
    }

    /** Distância de rei entre duas casas — vizinhança usada pelo teste do circuito. */
    fun stepsBetween(a: LudoCell, b: LudoCell): Int =
        maxOf(abs(a.row - b.row), abs(a.column - b.column))
}
