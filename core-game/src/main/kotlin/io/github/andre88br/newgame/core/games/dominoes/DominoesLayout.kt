package io.github.andre88br.newgame.core.games.dominoes

/**
 * Uma peça já posicionada na mesa.
 *
 * As medidas são em **meias-peças**: uma peça deitada mede 2 de comprimento por 1 de
 * largura. A unidade é essa, e não pixels, porque quem escolhe o tamanho na tela é a tela —
 * aqui só se decide quem fica onde.
 */
data class LaidTile(
    val tile: PlacedTile,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** Carroça: entra atravessada na linha, como numa mesa de verdade. */
    val vertical: Boolean,
    /**
     * A fileira corre da direita para a esquerda.
     *
     * Importa para o desenho: a peça continua sendo `a|b` na ordem da linha, mas numa
     * fileira que anda para trás o `a` fica à direita. Desenhar sem isso faria os números
     * das pontas não baterem com os vizinhos.
     */
    val reversed: Boolean,
)

/** A mesa inteira, com o tamanho que ela ocupa em meias-peças. */
data class DominoesTable(
    val tiles: List<LaidTile>,
    val columns: Int,
    val rows: Int,
) {
    val width: Float get() = columns.toFloat()

    /** Cada fileira tem duas meias-peças de altura, para a carroça atravessada caber. */
    val height: Float get() = (rows * ROW_PITCH).toFloat()

    companion object {
        const val ROW_PITCH = 2
    }
}

/**
 * Onde cada peça fica na mesa.
 *
 * Uma partida de dominó chega a vinte e tantas peças. Em linha reta isso vira uma fita que
 * só cabe rolando, e quem joga perde as duas pontas de vista — que é exatamente o que
 * precisa enxergar para decidir. Aqui a linha **serpenteia**: vai até a borda, desce e volta
 * na direção contrária, como numa mesa quando o espaço acaba.
 *
 * Fica no `core-game` pelo mesmo motivo de `LudoLayout`: é geometria pura com invariantes
 * que dá para conferir sozinho — nenhuma peça sai da mesa, nenhuma fica por cima de outra, e
 * a ordem da linha é preservada. Sobreposição de peça é o defeito clássico deste tipo de
 * desenho, e é invisível até alguém jogar a décima peça.
 */
object DominoesLayout {

    /** Comprimento de uma peça deitada, em meias-peças. */
    private const val TILE_LENGTH = 2

    /** A carroça atravessada ocupa uma meia-peça de comprimento e duas de largura. */
    private const val DOUBLE_LENGTH = 1

    /**
     * Distribui [line] numa mesa de [columns] meias-peças de largura.
     *
     * [columns] vem da tela, calculado a partir da largura disponível: mesa larga acomoda
     * mais peças por fileira, mesa estreita serpenteia mais cedo.
     */
    fun table(line: List<PlacedTile>, columns: Int): DominoesTable {
        require(columns >= TILE_LENGTH) { "A mesa precisa caber ao menos uma peça: veio $columns" }
        if (line.isEmpty()) return DominoesTable(emptyList(), columns, rows = 1)

        val laid = ArrayList<LaidTile>(line.size)
        var row = 0
        var cursor = 0

        for (placed in line) {
            val double = placed.a == placed.b
            val length = if (double) DOUBLE_LENGTH else TILE_LENGTH

            // Não cabe no que sobrou da fileira: desce e recomeça do outro lado.
            if (cursor + length > columns) {
                row++
                cursor = 0
            }

            val reversed = row % 2 == 1
            // Nas fileiras que voltam, a mesma posição de percurso cai espelhada na tela.
            val x = if (reversed) (columns - cursor - length).toFloat() else cursor.toFloat()
            val top = (row * DominoesTable.ROW_PITCH).toFloat()

            laid += if (double) {
                LaidTile(
                    tile = placed,
                    x = x,
                    y = top,
                    width = DOUBLE_LENGTH.toFloat(),
                    height = DominoesTable.ROW_PITCH.toFloat(),
                    vertical = true,
                    reversed = reversed,
                )
            } else {
                LaidTile(
                    tile = placed,
                    x = x,
                    // Centrada na fileira: a carroça é que ocupa a altura inteira.
                    y = top + 0.5f,
                    width = TILE_LENGTH.toFloat(),
                    height = 1f,
                    vertical = false,
                    reversed = reversed,
                )
            }

            cursor += length
        }

        return DominoesTable(laid, columns, rows = row + 1)
    }

    /**
     * Quantas meias-peças cabem numa mesa de [availableDp] de largura, sem a peça ficar
     * pequena demais para se enxergar o valor.
     */
    fun columnsFor(availableDp: Float, halfTileDp: Float = 26f): Int =
        (availableDp / halfTileDp).toInt().coerceIn(TILE_LENGTH, 16)
}
