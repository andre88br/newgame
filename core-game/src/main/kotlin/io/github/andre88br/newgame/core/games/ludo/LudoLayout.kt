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

    /** Corredor final de um braço. Qual braço vem de [LudoLayout.armAt]. */
    HOME,

    /** O centro da cruz: é onde os peões chegam. */
    GOAL,

    /** Curral de um braço. */
    YARD,

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
     * O índice é a casa absoluta que [absoluteSquare] devolve: `ring[0]`, `ring[13]`,
     * `ring[26]` e `ring[39]` são as saídas dos quatro braços.
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
     * O braço a que a casa pertence, ou `-1` se ela for da volta, do centro ou de fora.
     *
     * Serve ao desenho: cada braço tem a sua cor, e o corredor de um braço que não está em
     * jogo aparece apagado em vez de somer.
     */
    fun armAt(cell: LudoCell): Int = when {
        cell.row == 7 && cell.column in 1..5 -> 0
        cell.column == 7 && cell.row in 1..5 -> 1
        cell.row == 7 && cell.column in 9..13 -> 2
        cell.column == 7 && cell.row in 9..13 -> 3
        cell.row in 0..5 && cell.column in 0..5 -> 0
        cell.row in 0..5 && cell.column in 9..14 -> 1
        cell.row in 9..14 && cell.column in 9..14 -> 2
        cell.row in 9..14 && cell.column in 0..5 -> 3
        else -> -1
    }

    /**
     * O braço cuja **saída** é esta casa da volta, ou `-1` se ela não for saída de ninguém.
     *
     * Serve à pintura: a casa de onde os peões de uma cor entram na volta é dessa cor, como
     * num tabuleiro de verdade. Sem isso todas as saídas saem iguais, e quem joga não tem
     * como saber de onde a própria cor parte.
     */
    fun startArmAt(absolute: Int): Int {
        val passo = LUDO_TRACK / LUDO_ARMS
        val casa = Math.floorMod(absolute, LUDO_TRACK)
        return if (casa % passo == 0) casa / passo else -1
    }

    /**
     * Casa do corredor final, com [step] indo de 0 (a primeira depois da volta) até
     * [LUDO_HOME_LANE] — que já é a chegada.
     *
     * Os dois corredores ocupam a linha do meio, um vindo de cada lado, e se encontram no
     * centro. Cada cor entra no seu logo depois de completar a volta.
     */
    fun laneCell(arm: Int, step: Int): LudoCell {
        require(step in 0..LUDO_HOME_LANE) { "Passo $step fora do corredor final" }
        // Cada braço entra no centro pelo seu lado: da esquerda, de cima, da direita, de
        // baixo. Todos terminam encostando no bloco do meio, que é a chegada.
        return when (arm) {
            0 -> LudoCell(7, 1 + step)
            1 -> LudoCell(1 + step, 7)
            2 -> LudoCell(7, 13 - step)
            else -> LudoCell(13 - step, 7)
        }
    }

    /** A chegada de cada braço, no centro do tabuleiro. */
    fun goalCell(arm: Int): LudoCell = laneCell(arm, LUDO_HOME_LANE)

    /** Onde fica o peão [token] enquanto está no curral do braço [arm]. */
    fun yardCell(arm: Int, token: Int): LudoCell {
        require(token in 0 until LUDO_TOKENS) { "Peão $token não existe" }
        val row = if (token < 2) 1 else 4
        val column = if (token % 2 == 0) 1 else 4
        // Os quatro cantos, no mesmo sentido em que os braços se sucedem na volta.
        return when (arm) {
            0 -> LudoCell(row, column)
            1 -> LudoCell(row, column + 9)
            2 -> LudoCell(row + 9, column + 9)
            else -> LudoCell(row + 9, column)
        }
    }

    /**
     * Onde desenhar o peão [token] de [seat], seja qual for a situação dele: curral, volta,
     * corredor final ou chegada.
     */
    fun cellFor(seat: Seat, progress: Int, token: Int, seats: Int, firstArm: Int = 0): LudoCell {
        val arm = armOf(seat, seats, firstArm)
        return when {
            progress == LUDO_YARD -> yardCell(arm, token)
            progress >= LUDO_TRACK -> laneCell(arm, progress - LUDO_TRACK)
            else -> trackCell(absoluteSquare(seat, progress, seats, firstArm)!!)
        }
    }

    /** Para que serve a casa — o desenho pinta cada tipo de um jeito. */
    fun kindOf(cell: LudoCell): LudoCellKind {
        val absolute = ring.indexOf(cell)
        if (absolute >= 0) return if (isSafeSquare(absolute)) LudoCellKind.SAFE else LudoCellKind.TRACK

        if (cell.row in 6..8 && cell.column in 6..8) return LudoCellKind.GOAL

        val naLinhaDoMeio = cell.row == 7 && (cell.column in 1..5 || cell.column in 9..13)
        val naColunaDoMeio = cell.column == 7 && (cell.row in 1..5 || cell.row in 9..13)
        if (naLinhaDoMeio || naColunaDoMeio) return LudoCellKind.HOME

        if (cell.row in 0..5 && cell.column in 0..5) return LudoCellKind.YARD
        if (cell.row in 0..5 && cell.column in 9..14) return LudoCellKind.YARD
        if (cell.row in 9..14 && cell.column in 9..14) return LudoCellKind.YARD
        if (cell.row in 9..14 && cell.column in 0..5) return LudoCellKind.YARD

        return LudoCellKind.OUTSIDE
    }

    /** Distância de rei entre duas casas — vizinhança usada pelo teste do circuito. */
    fun stepsBetween(a: LudoCell, b: LudoCell): Int =
        maxOf(abs(a.row - b.row), abs(a.column - b.column))
}
