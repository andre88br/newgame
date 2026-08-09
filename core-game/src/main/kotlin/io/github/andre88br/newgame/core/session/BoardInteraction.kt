package io.github.andre88br.newgame.core.session

import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move

/** O que um toque no tabuleiro produziu. */
sealed interface TapResult {

    /** O toque completou um lance. */
    data class Play(val move: Move) : TapResult

    /**
     * O toque escolheu uma peça. [destinations] são as casas para onde ela pode ir —
     * a tela ainda não as destaca, mas a informação já vem calculada.
     */
    data class Select(val square: Int, val destinations: List<Int>) : TapResult

    /** O toque desfez a escolha anterior. */
    data object Deselect : TapResult

    /** O toque não vale, e [reason] explica por quê, em português, para mostrar na tela. */
    data class Rejected(val reason: String) : TapResult

    /** Toque sem efeito (casa vazia sem nada escolhido, partida encerrada). */
    data object Ignored : TapResult
}

/**
 * Traduz toques em casas para lances.
 *
 * Cada jogo tem um jeito diferente de ser jogado com o dedo — na velha um toque basta, nas
 * damas é escolher a peça e depois o destino — e é justamente aí que interface costuma
 * errar. Deixando essa máquina de estados aqui, no módulo sem Android, ela é testada de
 * verdade, e a tela fica só com desenho.
 */
interface BoardInteractor {

    val rows: Int

    val columns: Int

    /** Índice da casa a partir da linha e da coluna. A contagem é por linhas, de cima para baixo. */
    fun squareAt(row: Int, column: Int): Int = row * columns + column

    /** Casas que fazem parte do jogo. Nas damas, só as escuras. */
    fun isPlayable(square: Int): Boolean = true

    /**
     * Processa um toque na casa [square], sabendo que [selected] é a casa escolhida antes
     * (ou `null` se nenhuma).
     */
    fun tap(state: GameState, selected: Int?, square: Int): TapResult

    /**
     * As casas por onde [move] passa, para a tela poder destacá-lo.
     *
     * Serve para mostrar a dica e para marcar o lance que o adversário acabou de fazer —
     * sem isso, num tabuleiro 8×8, a peça simplesmente aparece em outro lugar e a pessoa
     * não vê o que aconteceu.
     */
    fun squaresOf(move: Move): List<Int>
}
