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

    /**
     * Uma peça já decidida — em que coluna, virada como, em que fileira — mas ainda sem
     * altura final em pixel-unidade. A altura depende do que mais existe na fileira dela
     * (uma carroça no meio empurra a fileira inteira para cima em altura), e só dá para
     * saber isso depois de passar a linha inteira uma vez.
     */
    private data class Provisional(
        val tile: PlacedTile,
        val x: Float,
        val width: Float,
        val facing: TileFacing,
        val stacked: Boolean,
        val reversed: Boolean,
        val band: Int,
        /** Só a peça da curva liga a própria fileira à seguinte. */
        val bridgesToNextBand: Boolean,
    )

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

        // -------- primeira passada: em que fileira e coluna cada peça cai --------
        val provisional = ArrayList<Provisional>(line.size)
        var band = 0
        var column = 0
        var direction = 1

        for ((index, placed) in line.withIndex()) {
            val double = placed.a == placed.b
            val faltamPecas = index < line.lastIndex

            if (double) {
                // Atravessada: a peça vira de lado, e o que era comprimento vira altura. É
                // a MESMA peça física de sempre — duas meias-peças —, só girada 90°. Uma
                // carroça do tamanho de meia peça seria uma peça encolhendo ao virar, que é
                // exatamente o defeito que fica visível quando a peça muda de tamanho na mesa.
                provisional += Provisional(
                    tile = placed,
                    x = column.toFloat(),
                    width = 1f,
                    facing = TileFacing.CROSS,
                    // Atravessada: as metades ficam uma sobre a outra, e não lado a lado.
                    stacked = true,
                    reversed = false,
                    band = band,
                    bridgesToNextBand = false,
                )
                column += direction
                // Atravessada ela ocupa uma coluna só. Chegando na borda, a linha desce
                // reta, e a peça seguinte fica logo abaixo desta.
                if (column !in 0 until columns) {
                    band++
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
                provisional += Provisional(
                    tile = placed,
                    x = column.toFloat(),
                    width = 1f,
                    facing = TileFacing.TURN,
                    stacked = true,
                    // Descendo: o começo da peça fica em cima.
                    reversed = false,
                    band = band,
                    bridgesToNextBand = true,
                )
                band++
                direction = -direction
                // A metade de baixo da peça em pé já ocupa esta coluna na fileira nova.
                column += direction
                continue
            }

            provisional += Provisional(
                tile = placed,
                x = minOf(column, column + direction).toFloat(),
                width = TILE_LENGTH.toFloat(),
                facing = TileFacing.ALONG,
                stacked = false,
                // Fileira que volta: o começo da peça fica à direita.
                reversed = direction < 0,
                band = band,
                bridgesToNextBand = false,
            )
            column += direction * TILE_LENGTH
        }

        // -------- segunda passada: altura de cada fileira, depois a posição final --------
        //
        // Uma fileira comum mede uma meia-peça de altura. Uma fileira com carroça mede duas,
        // porque a carroça deitada de lado precisa do espaço de uma peça inteira — e é assim
        // que ela não encolhe. As peças deitadas dessa fileira continuam medindo uma: é só a
        // carroça que estica, saindo da fileira por baixo, como numa mesa de verdade.
        val bandCount = provisional.maxOf { if (it.bridgesToNextBand) it.band + 1 else it.band } + 1
        val bandHeight = IntArray(bandCount) { 1 }
        for (p in provisional) if (p.facing == TileFacing.CROSS) bandHeight[p.band] = 2

        val offsetY = FloatArray(bandCount + 1)
        for (b in 0 until bandCount) offsetY[b + 1] = offsetY[b] + bandHeight[b]

        val laid = provisional.map { p ->
            val height = when (p.facing) {
                TileFacing.ALONG -> 1f
                TileFacing.CROSS -> bandHeight[p.band].toFloat()
                TileFacing.TURN -> (bandHeight[p.band] + bandHeight[p.band + 1]).toFloat()
            }
            LaidTile(
                tile = p.tile,
                x = p.x,
                y = offsetY[p.band],
                width = p.width,
                height = height,
                facing = p.facing,
                stacked = p.stacked,
                reversed = p.reversed,
            )
        }

        return DominoesTable(laid, columns, rows = offsetY[bandCount].toInt())
    }

    /**
     * Largura mínima que a serpentina precisa para não virar escadinha.
     *
     * Abaixo disso a fileira não comporta nem uma peça deitada mais a curva, e toda peça
     * acabaria em pé.
     */
    private const val MIN_COLUMNS = 4

    /** Nenhuma serpentina razoável passa disto; mais do que isso vira peça microscópica. */
    private const val MAX_COLUMNS = 16

    /**
     * A largura de mesa que faz as peças saírem **maiores**.
     *
     * Escolher a largura só pela largura da tela desperdiça a altura: uma linha de dez peças
     * numa mesa larga vira uma fita fina no meio de uma área vazia — exatamente o que
     * acontecia antes. O que importa é quanto cada peça mede no fim, e isso depende das duas
     * dimensões ao mesmo tempo: menos colunas dá peça mais larga mas mais fileiras, e mais
     * fileiras podem estourar a altura.
     *
     * Não há fórmula fechada porque as carroças ocupam menos que as peças comuns e mudam a
     * conta. Como as opções são poucas — de [MIN_COLUMNS] a [MAX_COLUMNS] —, o jeito honesto
     * é montar a mesa em cada largura e ficar com a que der a maior peça.
     */
    fun bestColumns(line: List<PlacedTile>, boxWidth: Float, boxHeight: Float): Int {
        if (line.isEmpty() || boxWidth <= 0f || boxHeight <= 0f) return MIN_COLUMNS

        var melhor = MIN_COLUMNS
        var maiorPeca = 0f

        for (columns in MIN_COLUMNS..MAX_COLUMNS) {
            val mesa = table(line, columns)
            val peca = minOf(boxWidth / mesa.width, boxHeight / mesa.height)
            // Empate fica com a mesa mais larga: menos fileiras é mais fácil de ler.
            if (peca > maiorPeca) {
                maiorPeca = peca
                melhor = columns
            }
        }
        return melhor
    }
}
