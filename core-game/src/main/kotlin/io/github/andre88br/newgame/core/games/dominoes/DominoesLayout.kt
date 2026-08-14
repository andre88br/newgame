package io.github.andre88br.newgame.core.games.dominoes

import kotlin.math.ceil

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

    /** Fileiras a um comprimento de peça de distância: é a peça em pé que liga uma à outra. */
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
 * **Toda peça tem o mesmo tamanho, sempre**: [TILE_LENGTH] meias-peças de comprimento por
 * uma de largura. Deitada ela mede 2 × 1; em pé — na curva ou atravessada como carroça —
 * mede 1 × 2. É a mesma peça de madeira girada, e nunca uma peça que encolhe ou estica para
 * caber, que é o defeito que salta aos olhos assim que alguém olha a mesa.
 *
 * Disso sai o espaçamento das fileiras: como a peça em pé é o que liga uma fileira à
 * seguinte, e ela mede um comprimento de peça, as fileiras ficam a [ROW_PITCH] meias-peças
 * uma da outra. A fileira ocupa a meia-peça de cima desse vão; a de baixo é por onde as
 * peças em pé descem — e é também para onde as carroças se estendem, sem esbarrar em nada.
 *
 * A linha serpenteia: a primeira fileira da esquerda para a direita, a seguinte de volta, e
 * assim por diante. Chegando na borda com peça ainda por jogar, a peça fica em pé e desce —
 * como se faz numa mesa quando o espaço acaba. Depois de uma peça em pé a linha continua na
 * **mesma coluna**, encostada por baixo dela, e não uma coluna adiante: quem desceu a
 * fileira inteira foi a própria peça em pé.
 *
 * Fica no `core-game` pelo mesmo motivo de `LudoLayout`: é geometria pura com invariantes
 * que dá para conferir sozinho. Sobreposição de peça é o defeito clássico deste tipo de
 * desenho, e é invisível até alguém jogar a décima peça.
 */
object DominoesLayout {

    /** Comprimento de uma peça, em meias-peças. Vale deitada e em pé: é a mesma peça. */
    private const val TILE_LENGTH = 2

    /**
     * Distância entre uma fileira e a seguinte, em meias-peças.
     *
     * É o comprimento de uma peça, e não podia ser outra coisa: quem liga duas fileiras é
     * uma peça em pé, que mede exatamente isso. Fileiras mais juntas obrigariam a peça da
     * curva a encolher; mais afastadas, a esticar.
     */
    private const val ROW_PITCH = TILE_LENGTH

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

        /**
         * Uma peça comum posta em [col] andando para [dir] fica deitada, sem virar.
         *
         * É a mesma pergunta em dois lugares: na hora de pôr a peça, e antes dela, para
         * saber se a carroça anterior pode ter vizinha ao lado ou se fecha a fileira ali.
         */
        fun deitaSemVirar(col: Int, dir: Int, aindaVemMais: Boolean): Boolean {
            val cabeDeitada = col + dir in 0 until columns
            val terminaNaBorda = col + dir * TILE_LENGTH !in 0 until columns
            return cabeDeitada && !(terminaNaBorda && aindaVemMais)
        }

        val laid = ArrayList<LaidTile>(line.size)
        var band = 0
        var column = 0
        var direction = 1
        // Verdadeiro quando a peça anterior desceu para cá — peça em pé, ou carroça que
        // fechou a fileira. A peça que vem logo abaixo dela encosta nela, e por isso não
        // pode ser centrada na fileira: subiria por cima da vizinha de cima.
        var vemDeCima = false

        for ((index, placed) in line.withIndex()) {
            val double = placed.a == placed.b
            val faltamPecas = index < line.lastIndex
            val y = (band * ROW_PITCH).toFloat()

            if (double) {
                // **A carroça pode fechar a fileira.** Se a peça seguinte não couber deitada
                // ao lado dela, ela teria de ficar em pé — e duas peças em pé lado a lado
                // ficam paralelas, coisa que não se vê em mesa nenhuma. Então a linha desce
                // pela própria carroça, e a peça seguinte encosta por baixo dela.
                val proximaEhCarroca = faltamPecas && line[index + 1].let { it.a == it.b }
                val fechaFileira = faltamPecas && if (proximaEhCarroca) {
                    // Carroça ocupa uma coluna só: basta haver coluna.
                    column + direction !in 0 until columns
                } else {
                    !deitaSemVirar(column + direction, direction, index + 1 < line.lastIndex)
                }

                // **Centrada na fileira.** As vizinhas deitadas encostam no meio da carroça,
                // e não na ponta dela — é assim que a carroça aparece numa mesa de verdade.
                //
                // Três coisas impedem: ter peça encostada em cima (a linha desceu para cá)
                // ou embaixo (é ela que desce), porque aí ela precisa alcançar a vizinha; e
                // ter peça em pé na coluna ao lado, porque centrar a faria subir meia peça
                // e ficar paralela justamente a essa vizinha.
                val yCentrada = y - (TILE_LENGTH - 1) / 2f
                val proximaCairiaAoLado = proximaEhCarroca && !fechaFileira
                val emPeAoLado = laid.any { outra ->
                    outra.width == 1f &&
                        (outra.x + outra.width == column.toFloat() || outra.x == column + 1f) &&
                        outra.y < yCentrada + TILE_LENGTH && yCentrada < outra.y + outra.height
                }
                val centrada = !vemDeCima && !fechaFileira && !emPeAoLado && !proximaCairiaAoLado

                laid += LaidTile(
                    tile = placed,
                    x = column.toFloat(),
                    y = if (centrada) yCentrada else y,
                    width = 1f,
                    height = TILE_LENGTH.toFloat(),
                    facing = TileFacing.CROSS,
                    // Atravessada: as metades ficam uma sobre a outra, e não lado a lado.
                    stacked = true,
                    reversed = false,
                )

                if (fechaFileira) {
                    band++
                    direction = -direction
                    vemDeCima = true
                } else {
                    column += direction
                    vemDeCima = false
                }
                continue
            }

            // **A curva.** Peça que termina na borda com a linha ainda por vir fica em pé,
            // ligando esta fileira à seguinte — é o que se faz numa mesa quando o espaço
            // acaba. Sem esta condição a peça ia parar deitada na fileira de baixo, e a
            // mesa virava quebra de linha de máquina de escrever.
            if (!deitaSemVirar(column, direction, faltamPecas)) {
                laid += LaidTile(
                    tile = placed,
                    x = column.toFloat(),
                    y = y,
                    width = 1f,
                    height = TILE_LENGTH.toFloat(),
                    facing = TileFacing.TURN,
                    stacked = true,
                    // Descendo: o começo da peça fica em cima.
                    reversed = false,
                )
                band++
                direction = -direction
                // A coluna **não** anda: a peça em pé vai do começo ao fim do vão entre as
                // duas fileiras, e a próxima peça encosta por baixo dela, nesta coluna.
                vemDeCima = true
                continue
            }

            laid += LaidTile(
                tile = placed,
                x = minOf(column, column + direction).toFloat(),
                y = y,
                width = TILE_LENGTH.toFloat(),
                height = 1f,
                facing = TileFacing.ALONG,
                stacked = false,
                // Fileira que volta: o começo da peça fica à direita.
                reversed = direction < 0,
            )
            column += direction * TILE_LENGTH
            vemDeCima = false
        }

        // Carroça centrada na primeira fileira sobe meia peça acima do zero. A mesa não
        // começa em número negativo: desce tudo junto, que é só onde ela é desenhada.
        val topo = laid.minOf { it.y }
        val ajustadas = if (topo < 0f) laid.map { it.copy(y = it.y - topo) } else laid

        return DominoesTable(ajustadas, columns, rows = ceil(ajustadas.maxOf { it.y + it.height }).toInt())
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
