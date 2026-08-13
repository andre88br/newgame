package io.github.andre88br.newgame.core.games.dominoes

/** Como a peça está posta na mesa. */
enum class TileFacing {
    /** Deitada, seguindo a fileira. */
    ALONG,

    /** Em pé: é a peça que faz a curva, ligando uma fileira à seguinte. */
    TURN,

    /** Carroça, atravessada na linha. */
    CROSS,
}

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
    val facing: TileFacing,
    /**
     * As duas metades ficam uma **sobre** a outra, em vez de lado a lado.
     *
     * Vale para a peça da curva e para a carroça; a peça deitada tem as metades lado a lado.
     */
    val stacked: Boolean,
    /**
     * O `a` da peça é desenhado na segunda metade.
     *
     * Numa fileira que corre da direita para a esquerda, o começo da peça fica à direita.
     * Desenhar sem isso faria os números das pontas não baterem com os das peças vizinhas —
     * e a linha pareceria errada, embora as regras estivessem certas.
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

    /** Fileiras encostadas: é a peça em pé da curva que liga uma à outra. */
    val height: Float get() = rows.toFloat()
}

/**
 * Onde cada peça fica na mesa.
 *
 * Uma partida de dominó chega a vinte e tantas peças. Em linha reta isso vira uma fita que
 * só cabe rolando, e quem joga perde as duas pontas de vista — que é exatamente o que
 * precisa enxergar para decidir. Aqui a linha **serpenteia**, como numa mesa quando o
 * espaço acaba.
 *
 * O desenho é um **caminho por células**, e não fileiras independentes. A mesa é uma grade
 * de meias-peças percorrida em bustrofédon — a primeira fileira da esquerda para a direita,
 * a seguinte de volta, e assim por diante — e cada peça ocupa duas células seguidas desse
 * caminho. Isso resolve sozinho a curva: quando as duas células caem em fileiras
 * diferentes, elas são vizinhas na vertical, e a peça sai **em pé**, ligando uma fileira à
 * outra. É como a curva acontece numa mesa de verdade.
 *
 * Fica no `core-game` pelo mesmo motivo de `LudoLayout`: é geometria pura com invariantes
 * que dá para conferir sozinho. Sobreposição de peça é o defeito clássico deste tipo de
 * desenho, e é invisível até alguém jogar a décima peça.
 */
object DominoesLayout {

    /** Comprimento de uma peça deitada, em meias-peças. */
    private const val TILE_LENGTH = 2

    /** A carroça entra atravessada e ocupa uma célula só. */
    private const val DOUBLE_LENGTH = 1

    /**
     * Distribui [line] numa mesa de [columns] meias-peças de largura.
     *
     * [columns] vem da tela, calculado a partir da largura disponível: mesa larga acomoda
     * mais peças por fileira, mesa estreita serpenteia mais cedo.
     */
    fun table(line: List<PlacedTile>, columns: Int): DominoesTable {
        require(columns >= MIN_COLUMNS) {
            "A mesa precisa de ao menos $MIN_COLUMNS meias-peças de largura: veio $columns"
        }
        if (line.isEmpty()) return DominoesTable(emptyList(), columns, rows = 1)

        val laid = ArrayList<LaidTile>(line.size)
        var row = 0
        var column = 0
        var direction = 1

        for ((index, placed) in line.withIndex()) {
            val double = placed.a == placed.b
            val faltamPecas = index < line.lastIndex

            if (double) {
                laid += LaidTile(
                    tile = placed,
                    x = column.toFloat(),
                    y = row.toFloat(),
                    width = 1f,
                    height = 1f,
                    facing = TileFacing.CROSS,
                    // Atravessada: as metades ficam uma sobre a outra, e não lado a lado.
                    stacked = true,
                    reversed = false,
                )
                column += direction
                // A carroça não tem como ficar em pé — ela mede uma célula. Chegando na
                // borda, a linha desce reta, e a peça seguinte fica logo abaixo desta.
                if (column !in 0 until columns) {
                    row++
                    direction = -direction
                    column += direction
                }
                continue
            }

            val cabeDeitada = column + direction in 0 until columns
            val terminaNaBorda = column + direction * TILE_LENGTH !in 0 until columns

            // **A curva.** Peça que termina na borda com a linha ainda por vir fica em pé,
            // ligando esta fileira à seguinte — é o que se faz numa mesa quando o espaço
            // acaba. Sem esta condição a peça ia parar deitada na fileira de baixo, e a
            // mesa virava quebra de linha de máquina de escrever.
            if (!cabeDeitada || (terminaNaBorda && faltamPecas)) {
                laid += LaidTile(
                    tile = placed,
                    x = column.toFloat(),
                    y = row.toFloat(),
                    width = 1f,
                    height = TILE_LENGTH.toFloat(),
                    facing = TileFacing.TURN,
                    stacked = true,
                    // Descendo: o começo da peça fica em cima.
                    reversed = false,
                )
                row++
                direction = -direction
                // A metade de baixo da peça em pé já ocupa esta coluna na fileira nova.
                column += direction
                continue
            }

            laid += LaidTile(
                tile = placed,
                x = minOf(column, column + direction).toFloat(),
                y = row.toFloat(),
                width = TILE_LENGTH.toFloat(),
                height = 1f,
                facing = TileFacing.ALONG,
                stacked = false,
                // Fileira que volta: o começo da peça fica à direita.
                reversed = direction < 0,
            )
            column += direction * TILE_LENGTH
        }

        val altura = laid.maxOf { it.y + it.height }
        return DominoesTable(laid, columns, rows = altura.toInt())
    }

    /**
     * Largura mínima que a serpentina precisa para não virar escadinha.
     *
     * Abaixo disso a fileira não comporta nem uma peça deitada mais a curva, e toda peça
     * acabaria em pé.
     */
    private const val MIN_COLUMNS = 4

    /**
     * Quantas meias-peças cabem numa mesa de [availableDp] de largura, sem a peça ficar
     * pequena demais para se enxergar o valor.
     */
    fun columnsFor(availableDp: Float, halfTileDp: Float = 26f): Int =
        (availableDp / halfTileDp).toInt().coerceIn(MIN_COLUMNS, 16)
}
