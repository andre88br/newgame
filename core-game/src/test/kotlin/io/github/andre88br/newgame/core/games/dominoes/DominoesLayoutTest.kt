package io.github.andre88br.newgame.core.games.dominoes

import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.applyOrThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sobreposição de peça é o defeito clássico deste tipo de desenho, e é invisível até
 * alguém jogar a décima peça — momento em que a mesa vira um borrão e ninguém sabe dizer
 * por quê. Aqui isso é conferido peça a peça, com a partida andando.
 */
class DominoesLayoutTest {

    /** Da mesa mais estreita que o desenho aceita à mais larga que faz sentido. */
    private val LARGURAS = listOf(5, 6, 8, 12)

    private fun linha(vararg pares: Pair<Int, Int>) = pares.map { PlacedTile(it.first, it.second) }

    /** Duas peças se sobrepõem se os retângulos delas se cruzam de verdade. */
    private fun sobrepoe(a: LaidTile, b: LaidTile): Boolean {
        val folga = 0.001f
        return a.x + a.width - folga > b.x &&
            b.x + b.width - folga > a.x &&
            a.y + a.height - folga > b.y &&
            b.y + b.height - folga > a.y
    }

    @Test
    fun `mesa vazia nao tem peca nem altura zero`() {
        val mesa = DominoesLayout.table(emptyList(), columns = 8)
        assertTrue(mesa.tiles.isEmpty())
        assertEquals(1, mesa.rows, "mesa vazia ainda ocupa uma fileira, para a tela ter o que medir")
    }

    @Test
    fun `a ordem da linha e preservada`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 4)
        val mesa = DominoesLayout.table(linha, columns = 8)
        assertEquals(linha, mesa.tiles.map { it.tile }, "a mesa não pode reordenar a linha")
    }

    // -------- a curva --------

    /**
     * Mesa de 8 meias-peças com peças comuns: três deitadas enchem a fileira e a quarta
     * chega na borda. Ela é a curva.
     *
     * A linha é longa o bastante para chegar à terceira fileira, que é onde se vê que o
     * serpenteio de fato volta ao sentido de ida.
     */
    private val comCurva = DominoesLayout.table(
        linha(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 0, 0 to 1, 1 to 3, 3 to 5, 5 to 0),
        columns = 8,
    )

    /**
     * O defeito que este arquivo existe para não deixar voltar: a peça que chegava na borda
     * ia parar deitada na fileira de baixo, como quebra de linha de máquina de escrever. Numa
     * mesa de dominó ela fica **em pé**, ligando uma fileira à outra.
     */
    @Test
    fun `a peca que chega na borda fica em pe`() {
        val curva = comCurva.tiles.firstOrNull { it.facing == TileFacing.TURN }

        assertTrue(curva != null, "nenhuma peça fez a curva: a mesa voltou a quebrar linha")
        assertTrue(curva.height > curva.width, "em pé quer dizer mais alta do que larga")
        assertTrue(curva.stacked, "em pé, as metades ficam uma sobre a outra")
    }

    @Test
    fun `a peca da curva liga uma fileira a seguinte`() {
        val indice = comCurva.tiles.indexOfFirst { it.facing == TileFacing.TURN }
        val antes = comCurva.tiles[indice - 1]
        val curva = comCurva.tiles[indice]
        val depois = comCurva.tiles[indice + 1]

        assertEquals(antes.y, curva.y, "a curva começa na fileira de quem veio antes")
        // A peça em pé vai do começo ao fim do vão entre as fileiras: onde ela acaba é
        // exatamente onde a fileira de baixo começa, e é assim que as duas se encostam.
        assertEquals(depois.y, curva.y + curva.height, "e termina onde a fileira de baixo começa")
        assertTrue(depois.y > antes.y, "a linha precisa ter descido")
    }

    @Test
    fun `a curva acontece no fim da fileira, e nao no meio`() {
        val curva = comCurva.tiles.first { it.facing == TileFacing.TURN }
        val mesmaFileira = comCurva.tiles.filter { it.y == curva.y && it !== curva }

        // Nada da fileira dela passa dela: a curva é o fim do caminho de ida.
        assertTrue(
            mesmaFileira.all { it.x + it.width <= curva.x + curva.width },
            "há peça além da curva na mesma fileira",
        )
        assertTrue(
            curva.x >= comCurva.columns - 2,
            "a curva devia estar na borda, e está em ${curva.x} de ${comCurva.columns}",
        )
    }

    @Test
    fun `a fileira seguinte corre na direcao contraria`() {
        val curva = comCurva.tiles.first { it.facing == TileFacing.TURN }
        // Só a fileira logo abaixo: duas abaixo a linha já virou de novo e corre no sentido
        // original, que é justamente o que serpentear quer dizer.
        val deBaixo = comCurva.tiles.filter { it.y == curva.y + 2f && it.facing == TileFacing.ALONG }
        val duasAbaixo = comCurva.tiles.filter { it.y == curva.y + 4f && it.facing == TileFacing.ALONG }

        assertTrue(deBaixo.isNotEmpty(), "o teste precisa de peça deitada na fileira de baixo")
        assertTrue(deBaixo.all { it.reversed }, "na fileira que volta a peça sai espelhada")
        assertTrue(duasAbaixo.isNotEmpty(), "o teste precisa de uma terceira fileira")
        assertTrue(duasAbaixo.none { it.reversed }, "duas fileiras abaixo a linha volta ao sentido de ida")
    }

    @Test
    fun `a ultima peca da partida nao fica em pe a toa`() {
        // Ficar em pé serve para virar. Sem linha depois dela, a peça deita como as outras.
        val mesa = DominoesLayout.table(linha(1 to 2, 2 to 3, 3 to 4, 4 to 5), columns = 8)
        assertEquals(TileFacing.ALONG, mesa.tiles.last().facing)
    }

    // -------- carroça --------

    @Test
    fun `a carroca entra atravessada`() {
        val mesa = DominoesLayout.table(linha(1 to 2, 3 to 3, 3 to 4), columns = 8)
        val carroca = mesa.tiles[1]
        val comum = mesa.tiles[0]

        assertEquals(TileFacing.CROSS, carroca.facing)
        assertTrue(carroca.stacked, "atravessada quer dizer metades uma sobre a outra")
        assertTrue(carroca.height > carroca.width, "atravessada, a carroça fica em pé — mais alta do que larga")

        // O ponto do defeito que este teste existe para pegar: a carroça é a MESMA peça
        // física de sempre, só virada. Ela não pode encolher para caber atravessada — o
        // comprimento de uma peça deitada vira a altura dela, e vice-versa.
        assertEquals(comum.width, carroca.height, "virada, a carroça mede o comprimento de uma peça comum")
        assertEquals(comum.height, carroca.width, "virada, a carroça mede a largura de uma peça comum")

        assertEquals(TileFacing.ALONG, comum.facing)
        assertTrue(comum.width > comum.height, "peça comum fica deitada")
    }

    @Test
    fun `a carroca ocupa menos comprimento mas nao encolhe em altura`() {
        // Cinco carroças cabem lado a lado na largura de cinco meias-peças — cada uma usa
        // só uma coluna, contra as duas de uma peça deitada. Mas, viradas, elas ficam altas
        // como uma peça inteira: a fileira que as recebe mede duas meias-peças de altura, e
        // não uma, porque a carroça não é mais curta do que as outras — é só mais estreita.
        val mesa = DominoesLayout.table(linha(1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5), columns = 5)
        assertTrue(mesa.tiles.all { it.facing == TileFacing.CROSS }, "todas as peças deviam ser carroças")
        assertEquals(2, mesa.rows, "cinco carroças em pé ocupam duas meias-peças de altura")
    }

    /**
     * **A peça nunca muda de tamanho.**
     *
     * É o defeito que voltou três vezes, e sempre pelo mesmo caminho: alguma peça em pé
     * ganhava altura emprestada da fileira (uma carroça noutra coluna esticava a curva, por
     * exemplo). Uma peça de dominó é um retângulo de dois por um, virado ou não — e conferir
     * isso na mesa inteira, peça a peça, é o único jeito de a conta não escapar de novo.
     */
    @Test
    fun `toda peca mede sempre dois por um, virada ou nao`() {
        for (columns in LARGURAS) {
            var state = DominoesGame.initialState(MatchConfig(seed = 7))
            var guard = 0

            while (!DominoesGame.outcome(state).isOver && guard++ < 40) {
                for (peca in DominoesLayout.table(state.line, columns).tiles) {
                    val deitada = peca.width == 2f && peca.height == 1f
                    val emPe = peca.width == 1f && peca.height == 2f
                    assertTrue(
                        deitada || emPe,
                        "largura $columns, lance ${state.ply}: peça fora do tamanho — $peca",
                    )
                }
                val move = DominoesGame.legalMoves(state).firstOrNull() ?: break
                state = DominoesGame.applyOrThrow(state, move)
            }
        }
    }

    /**
     * **Duas peças em pé nunca ficam lado a lado.**
     *
     * Numa mesa de verdade não existe: quem vem depois de uma carroça encosta na lateral
     * dela, deitada. Em pé e encostada, as duas ficam paralelas — o desenho perde o fio da
     * linha e parece que alguém empilhou peças sem jogar. Quando não há espaço para a
     * seguinte deitar, quem fecha a fileira é a própria carroça, e a linha desce por ela.
     *
     * Duas em pé na **mesma** coluna são outra coisa: essas estão em seguida, e é assim que
     * a linha faz a curva.
     */
    @Test
    fun `duas pecas em pe nunca ficam lado a lado`() {
        for (columns in LARGURAS) {
            var state = DominoesGame.initialState(MatchConfig(seed = 7))
            var guard = 0

            while (!DominoesGame.outcome(state).isOver && guard++ < 40) {
                val emPe = DominoesLayout.table(state.line, columns).tiles.filter { it.width == 1f }
                for (a in emPe) {
                    for (b in emPe) {
                        if (a === b) continue
                        val ladoALado = a.x + a.width == b.x
                        val mesmaAltura = a.y < b.y + b.height && b.y < a.y + a.height
                        assertTrue(
                            !(ladoALado && mesmaAltura),
                            "largura $columns, lance ${state.ply}: duas em pé paralelas — $a / $b",
                        )
                    }
                }
                val move = DominoesGame.legalMoves(state).firstOrNull() ?: break
                state = DominoesGame.applyOrThrow(state, move)
            }
        }
    }

    /**
     * **A carroça logo abaixo de uma peça em pé fica deitada, centrada nela.**
     *
     * Em pé ali ela encostaria na de cima e na fileira de baixo ao mesmo tempo, e a vizinha
     * encostaria na ponta dela em vez do meio — foi o defeito que apareceu na tela. Deitada
     * resolve as duas coisas de uma vez, e é o que a mesa de verdade faz: a linha chega
     * descendo, e a carroça é atravessada à linha, o que com a linha na vertical quer dizer
     * horizontal.
     */
    @Test
    fun `a carroca embaixo da peca em pe fica deitada e centrada nela`() {
        for (columns in LARGURAS) {
            var state = DominoesGame.initialState(MatchConfig(seed = 7))
            var guard = 0

            while (!DominoesGame.outcome(state).isOver && guard++ < 40) {
                val pecas = DominoesLayout.table(state.line, columns).tiles
                for ((i, peca) in pecas.withIndex()) {
                    if (peca.facing != TileFacing.CROSS || i == 0) continue
                    val acima = pecas[i - 1]
                    // Só quando a anterior desceu para cá; carroça no meio da fileira é
                    // outro caso, e esse tem teste próprio.
                    if (acima.width != 1f) continue

                    assertEquals(
                        1f,
                        peca.height,
                        "largura $columns, lance ${state.ply}: carroça em pé embaixo de $acima",
                    )
                    assertEquals(
                        acima.x + acima.width / 2f,
                        peca.x + peca.width / 2f,
                        "largura $columns, lance ${state.ply}: $acima não está no meio de $peca",
                    )
                    // Deitada, ela mede uma meia-peça de altura e não alcança a fileira de
                    // baixo — que é o "colado" que se via na tela.
                    val fileiraDeBaixo = pecas.filter { it.y > peca.y + peca.height - 0.001f }
                    assertTrue(
                        fileiraDeBaixo.none { it.y < peca.y + peca.height + 0.001f },
                        "largura $columns, lance ${state.ply}: a carroça colou na fileira de baixo",
                    )
                }
                val move = DominoesGame.legalMoves(state).firstOrNull() ?: break
                state = DominoesGame.applyOrThrow(state, move)
            }
        }
    }

    /**
     * A vizinha de uma carroça encosta no **meio** dela, e não na ponta: a carroça sobe e
     * desce meia peça em relação à fileira, que é como ela fica numa mesa de verdade.
     */
    @Test
    fun `a carroça no meio da fileira fica centrada nas vizinhas`() {
        val mesa = DominoesLayout.table(linha(1 to 4, 4 to 5, 5 to 5, 5 to 6, 6 to 3), columns = 8)
        val carroca = mesa.tiles.first { it.facing == TileFacing.CROSS }
        val antes = mesa.tiles[mesa.tiles.indexOf(carroca) - 1]
        val depois = mesa.tiles[mesa.tiles.indexOf(carroca) + 1]

        val meioDaCarroca = carroca.y + carroca.height / 2f
        assertEquals(meioDaCarroca, antes.y + antes.height / 2f, "a de antes não está no meio")
        assertEquals(meioDaCarroca, depois.y + depois.height / 2f, "a de depois não está no meio")
        assertTrue(carroca.y < antes.y, "a carroça precisa sobrar para cima da vizinha")
        assertTrue(carroca.y + carroca.height > antes.y + antes.height, "e para baixo também")
    }

    /**
     * A carroça cedo numa fileira era o gatilho do defeito antigo: a curva que fechava a
     * MESMA fileira somava a altura dela com a da seguinte e crescia por causa de uma peça
     * que nem está na coluna dela — e ainda se soltava da vizinha.
     */
    @Test
    fun `a curva continua do tamanho certo quando a propria fileira tem carroca`() {
        val mesa = DominoesLayout.table(
            linha(1 to 1, 2 to 3, 3 to 4, 4 to 5, 5 to 5, 5 to 6),
            columns = 5,
        )

        val carroca = mesa.tiles.first { it.facing == TileFacing.CROSS }
        val curva = mesa.tiles.first { it.facing == TileFacing.TURN }
        // A carroça fica centrada na fileira e a curva desce a partir dela, então os dois
        // `y` não batem — o que precisa bater é a fileira, e ela se vê na sobreposição.
        assertTrue(
            carroca.y < curva.y + curva.height && curva.y < carroca.y + carroca.height,
            "o teste precisa de carroça e curva na mesma fileira: $carroca / $curva",
        )
        assertEquals(carroca.height, curva.height, "as duas estão em pé: medem o mesmo")

        for (i in 1 until mesa.tiles.size) {
            assertTrue(
                tocam(mesa.tiles[i - 1], mesa.tiles[i]),
                "peça $i não encosta na anterior: ${mesa.tiles[i - 1]} / ${mesa.tiles[i]}",
            )
        }
    }

    // -------- invariantes do desenho --------

    @Test
    fun `nenhuma peca fica por cima de outra`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 3, 3 to 5, 5 to 5, 5 to 6, 6 to 0, 0 to 4)
        val mesa = DominoesLayout.table(linha, columns = 6)

        for (i in mesa.tiles.indices) {
            for (j in i + 1 until mesa.tiles.size) {
                assertTrue(
                    !sobrepoe(mesa.tiles[i], mesa.tiles[j]),
                    "peças $i e $j se sobrepõem: ${mesa.tiles[i]} / ${mesa.tiles[j]}",
                )
            }
        }
    }

    @Test
    fun `nenhuma peca sai da mesa`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 3, 3 to 5, 5 to 6, 6 to 0)
        val mesa = DominoesLayout.table(linha, columns = 5)

        for (peca in mesa.tiles) {
            assertTrue(peca.x >= 0f, "peça saiu pela esquerda: $peca")
            assertTrue(peca.x + peca.width <= mesa.width, "peça saiu pela direita: $peca")
            assertTrue(peca.y >= 0f, "peça saiu por cima: $peca")
            assertTrue(peca.y + peca.height <= mesa.height, "peça saiu por baixo: $peca")
        }
    }

    /**
     * O que faz a linha parecer uma linha: cada peça encosta na anterior. Se duas vizinhas
     * ficarem separadas, a mesa vira um monte de peças soltas — e nenhum teste de
     * sobreposição pegaria isso, porque estar longe demais também não é sobrepor.
     */
    @Test
    fun `cada peca encosta na anterior`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 3, 3 to 5, 5 to 6, 6 to 0, 0 to 4, 4 to 4)
        val mesa = DominoesLayout.table(linha, columns = 6)

        for (i in 1 until mesa.tiles.size) {
            val anterior = mesa.tiles[i - 1]
            val atual = mesa.tiles[i]
            val encosta = tocam(anterior, atual)
            assertTrue(
                encosta,
                "peça $i não encosta na anterior: $anterior / $atual",
            )
        }
    }

    /** Dois retângulos que dividem uma borda, ainda que só em parte. */
    private fun tocam(a: LaidTile, b: LaidTile): Boolean {
        val folga = 0.001f
        val cruzaEmX = a.x < b.x + b.width - folga && b.x < a.x + a.width - folga
        val cruzaEmY = a.y < b.y + b.height - folga && b.y < a.y + a.height - folga

        val coladoNaHorizontal = cruzaEmY &&
            (kotlin.math.abs(a.x + a.width - b.x) < folga || kotlin.math.abs(b.x + b.width - a.x) < folga)
        val coladoNaVertical = cruzaEmX &&
            (kotlin.math.abs(a.y + a.height - b.y) < folga || kotlin.math.abs(b.y + b.height - a.y) < folga)

        return coladoNaHorizontal || coladoNaVertical
    }

    @Test
    fun `mesa estreita ainda acomoda a peca mais comprida`() {
        val mesa = DominoesLayout.table(linha(1 to 2, 2 to 3, 3 to 4), columns = 5)
        for (peca in mesa.tiles) {
            assertTrue(peca.x + peca.width <= mesa.width)
            assertTrue(peca.y + peca.height <= mesa.height)
        }
    }

    @Test
    fun `uma partida inteira cabe na mesa, encostada e sem peca em cima de peca`() {
        // O caso de verdade: a linha crescendo lance a lance, em várias larguras de tela.
        for (columns in LARGURAS) {
            var state = DominoesGame.initialState(MatchConfig(seed = 7))
            var guard = 0

            while (!DominoesGame.outcome(state).isOver && guard++ < 40) {
                val mesa = DominoesLayout.table(state.line, columns)
                assertEquals(state.line.size, mesa.tiles.size, "sumiu peça da mesa")

                for (i in mesa.tiles.indices) {
                    val peca = mesa.tiles[i]
                    assertTrue(
                        peca.x >= 0f && peca.x + peca.width <= mesa.width + 0.001f &&
                            peca.y >= 0f && peca.y + peca.height <= mesa.height + 0.001f,
                        "largura $columns: peça $i saiu da mesa — $peca",
                    )
                    if (i > 0) {
                        assertTrue(
                            tocam(mesa.tiles[i - 1], peca),
                            "largura $columns, lance ${state.ply}: peça $i soltou da linha",
                        )
                    }
                    for (j in i + 1 until mesa.tiles.size) {
                        assertTrue(
                            !sobrepoe(peca, mesa.tiles[j]),
                            "largura $columns, lance ${state.ply}: peças $i e $j se sobrepõem",
                        )
                    }
                }

                val move = DominoesGame.legalMoves(state).firstOrNull() ?: break
                state = DominoesGame.applyOrThrow(state, move)
            }
            assertTrue(guard > 1, "a partida de teste precisa avançar")

            // Nenhuma peça **deitada** pode ser a última de uma fileira que tem fileira
            // depois: descer dali é a quebra de linha de máquina de escrever, e é o defeito
            // que fez esta mesa ser refeita. Descer só vale por peça em pé — ou por carroça,
            // que é quadrada e vira a linha sozinha.
            val mesaFinal = DominoesLayout.table(state.line, columns)
            for (i in 1 until mesaFinal.tiles.size) {
                val anterior = mesaFinal.tiles[i - 1]
                if (mesaFinal.tiles[i].y > anterior.y) {
                    assertTrue(
                        anterior.facing != TileFacing.ALONG,
                        "largura $columns: a linha desceu a partir de uma peça deitada — $anterior",
                    )
                }
            }
        }
    }

    // -------- escolha da largura --------

    /** O tamanho da peça que uma mesa de [columns] colunas produziria naquela área. */
    private fun tamanhoDaPeca(linha: List<PlacedTile>, columns: Int, w: Float, h: Float): Float {
        val mesa = DominoesLayout.table(linha, columns)
        return minOf(w / mesa.width, h / mesa.height)
    }

    @Test
    fun `a largura escolhida e a que da a maior peca`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 0, 0 to 1, 1 to 3, 3 to 6)
        val (largura, altura) = 400f to 300f

        val escolhida = DominoesLayout.bestColumns(linha, largura, altura)
        val melhorTamanho = tamanhoDaPeca(linha, escolhida, largura, altura)

        for (columns in 5..16) {
            assertTrue(
                tamanhoDaPeca(linha, columns, largura, altura) <= melhorTamanho + 0.001f,
                "largura $columns daria peça maior do que a escolhida ($escolhida)",
            )
        }
    }

    /**
     * O defeito da imagem: mesa larga e baixa, linha curta, e o desenho saía uma fita fina
     * no meio de uma área vazia. Com a altura disponível, a mesa deve preferir menos colunas
     * e mais fileiras — peça maior.
     */
    @Test
    fun `area alta aproveita a altura em vez de espremer tudo numa fileira`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 0, 0 to 1, 1 to 3)

        val emAreaBaixa = DominoesLayout.bestColumns(linha, boxWidth = 400f, boxHeight = 80f)
        val emAreaAlta = DominoesLayout.bestColumns(linha, boxWidth = 400f, boxHeight = 400f)

        assertTrue(
            emAreaAlta < emAreaBaixa,
            "com altura sobrando a mesa devia estreitar e usar mais fileiras " +
                "(baixa=$emAreaBaixa, alta=$emAreaAlta)",
        )
    }

    @Test
    fun `a peca sempre cabe na area escolhida`() {
        val linha = linha(1 to 2, 2 to 3, 3 to 3, 3 to 5, 5 to 6, 6 to 0, 0 to 4, 4 to 4, 4 to 2)
        for ((w, h) in listOf(400f to 300f, 200f to 500f, 1000f to 120f, 300f to 300f)) {
            val columns = DominoesLayout.bestColumns(linha, w, h)
            val mesa = DominoesLayout.table(linha, columns)
            val peca = minOf(w / mesa.width, h / mesa.height)

            assertTrue(peca > 0f, "área ${w}x$h: peça sem tamanho")
            assertTrue(mesa.width * peca <= w + 0.001f, "área ${w}x$h: a mesa passou da largura")
            assertTrue(mesa.height * peca <= h + 0.001f, "área ${w}x$h: a mesa passou da altura")
        }
    }

    @Test
    fun `mesa sem peca nenhuma nao quebra a escolha de largura`() {
        assertTrue(DominoesLayout.bestColumns(emptyList(), 400f, 300f) >= 4)
        assertTrue(DominoesLayout.bestColumns(linha(1 to 2), 0f, 0f) >= 4)
    }
}
