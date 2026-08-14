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

        val largura = columns.toFloat()

        /** Uma peça de [comprimento] posta a partir de [ponta], andando para [dir], cabe. */
        fun cabe(ponta: Float, dir: Int, comprimento: Int): Boolean {
            val fim = ponta + dir * comprimento
            return fim >= -FOLGA && fim <= largura + FOLGA
        }

        /**
         * Uma peça comum posta a partir de [ponta] fica deitada, sem virar.
         *
         * Não basta ela caber: se depois dela não sobrar nem meia peça na fileira, quem vem
         * a seguir teria de virar — e então é esta que vira, no fim da fileira.
         */
        fun deitaSemVirar(ponta: Float, dir: Int, aindaVemMais: Boolean): Boolean =
            cabe(ponta, dir, TILE_LENGTH) &&
                (!aindaVemMais || cabe(ponta + dir * TILE_LENGTH, dir, 1))

        val laid = ArrayList<LaidTile>(line.size)
        var band = 0
        // A ponta livre da linha, em meias-peças. Anda com a linha, e não é índice de
        // coluna: uma carroça deitada deixa a ponta em meia coluna, e o resto da fileira
        // segue a partir dali.
        var ponta = 0f
        var direction = 1
        // Verdadeiro quando a peça anterior desceu para cá — peça em pé, ou carroça que
        // fechou a fileira. Quem vem depois dela encosta nela por baixo.
        var vemDeCima = false

        for ((index, placed) in line.withIndex()) {
            val double = placed.a == placed.b
            val faltamPecas = index < line.lastIndex
            val y = (band * ROW_PITCH).toFloat()

            // **Carroça logo abaixo de uma peça em pé: deitada, e centrada nela.**
            //
            // Em pé ali ela ficaria encostada na de cima e na fileira de baixo ao mesmo
            // tempo, e a vizinha encostaria na ponta dela em vez do meio. Deitada resolve as
            // duas coisas: a linha vem descendo, e a carroça é atravessada à linha — o que,
            // com a linha vertical, quer dizer horizontal.
            if (double && vemDeCima) {
                val centro = ponta + direction * 0.5f
                laid += LaidTile(
                    tile = placed,
                    x = centro - TILE_LENGTH / 2f,
                    y = y,
                    width = TILE_LENGTH.toFloat(),
                    height = 1f,
                    facing = TileFacing.CROSS,
                    // Deitada: as metades ficam lado a lado.
                    stacked = false,
                    reversed = false,
                )
                ponta = centro + direction * (TILE_LENGTH / 2f)
                vemDeCima = false
                continue
            }

            if (double) {
                // **A carroça pode fechar a fileira.** Se a peça seguinte não couber deitada
                // ao lado dela, ela teria de ficar em pé — e duas peças em pé lado a lado
                // ficam paralelas, coisa que não se vê em mesa nenhuma. Então a linha desce
                // pela própria carroça, e a peça seguinte encosta por baixo dela.
                val proximaEhCarroca = faltamPecas && line[index + 1].let { it.a == it.b }
                val fechaFileira = faltamPecas && if (proximaEhCarroca) {
                    // Carroça ocupa uma coluna só: basta haver coluna.
                    !cabe(ponta + direction, direction, 1)
                } else {
                    !deitaSemVirar(ponta + direction, direction, index + 1 < line.lastIndex)
                }

                // **Centrada na fileira.** As vizinhas deitadas encostam no meio da carroça,
                // e não na ponta dela — é assim que a carroça aparece numa mesa de verdade.
                //
                // Não centra quando é ela que desce para a fileira seguinte (aí precisa
                // alcançá-la), nem quando há peça em pé na coluna ao lado: centrar a faria
                // subir meia peça e ficar paralela justamente a essa vizinha.
                val x = minOf(ponta, ponta + direction)
                val yCentrada = y - (TILE_LENGTH - 1) / 2f
                val emPeAoLado = laid.any { outra ->
                    outra.width == 1f &&
                        (encostam(outra.x + outra.width, x) || encostam(outra.x, x + 1f)) &&
                        outra.y < yCentrada + TILE_LENGTH && yCentrada < outra.y + outra.height
                }
                val centrada = !fechaFileira && !emPeAoLado && !proximaEhCarroca

                laid += LaidTile(
                    tile = placed,
                    x = x,
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
                    // A peça seguinte encosta por baixo desta, na mesma coluna.
                    ponta += direction
                    direction = -direction
                    vemDeCima = true
                } else {
                    ponta += direction
                    vemDeCima = false
                }
                continue
            }

            // **A curva.** Peça que termina na borda com a linha ainda por vir fica em pé,
            // ligando esta fileira à seguinte — é o que se faz numa mesa quando o espaço
            // acaba. Sem esta condição a peça ia parar deitada na fileira de baixo, e a
            // mesa virava quebra de linha de máquina de escrever.
            if (!deitaSemVirar(ponta, direction, faltamPecas)) {
                laid += LaidTile(
                    tile = placed,
                    x = minOf(ponta, ponta + direction),
                    y = y,
                    width = 1f,
                    height = TILE_LENGTH.toFloat(),
                    facing = TileFacing.TURN,
                    stacked = true,
                    // Descendo: o começo da peça fica em cima.
                    reversed = false,
                )
                band++
                // A peça em pé vai do começo ao fim do vão entre as duas fileiras, e a
                // próxima encosta por baixo dela, nesta mesma coluna.
                ponta += direction
                direction = -direction
                vemDeCima = true
                continue
            }

            laid += LaidTile(
                tile = placed,
                x = minOf(ponta, ponta + direction * TILE_LENGTH),
                y = y,
                width = TILE_LENGTH.toFloat(),
                height = 1f,
                facing = TileFacing.ALONG,
                stacked = false,
                // Fileira que volta: o começo da peça fica à direita.
                reversed = direction < 0,
            )
            ponta += direction * TILE_LENGTH
            vemDeCima = false
        }

        // Carroça centrada na primeira fileira sobe meia peça acima do zero, e carroça
        // deitada pode sobrar meia coluna à esquerda. A mesa não começa em número negativo:
        // desce e empurra tudo junto, que isso é só onde ela é desenhada.
        val topo = minOf(laid.minOf { it.y }, 0f)
        val esquerda = minOf(laid.minOf { it.x }, 0f)
        val postas = if (topo < 0f || esquerda < 0f) {
            laid.map { it.copy(x = it.x - esquerda, y = it.y - topo) }
        } else {
            laid
        }

        return DominoesTable(
            tiles = postas,
            // A carroça deitada pode passar meia coluna da largura pedida; a mesa cresce
            // para caber, em vez de deixar peça pendurada para fora.
            columns = maxOf(columns, ceil(postas.maxOf { it.x + it.width }).toInt()),
            rows = ceil(postas.maxOf { it.y + it.height }).toInt(),
        )
    }

    /** Meia-coluna é medida de verdade aqui; comparar float pede uma folga. */
    private const val FOLGA = 0.001f

    private fun encostam(a: Float, b: Float): Boolean = kotlin.math.abs(a - b) < FOLGA

    /**
     * Largura mínima que a serpentina precisa para não virar escadinha.
     *
     * Com quatro meias-peças a fileira comporta **uma** peça deitada e a curva, e a mesa
     * vira uma escada de peças em pé. Pior: uma carroça logo depois de uma curva não tem
     * para onde ir, porque não sobra fileira para ela cair no meio — o desenho perde a
     * saída que existe em qualquer largura maior.
     */
    private const val MIN_COLUMNS = 5

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
