package io.github.andre88br.newgame.core.games.poker

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.GameAi
import io.github.andre88br.newgame.core.ai.defaultMistakeChance
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.standardDeck
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat

/**
 * O adversário do pôquer não pode ser [io.github.andre88br.newgame.core.ai.SearchBasedAi]: a
 * busca alpha-beta enxerga a árvore inteira do jogo, e no pôquer isso significa enxergar a mão
 * escondida dos outros — a mesma armadilha que [io.github.andre88br.newgame.core.ai.DeterminizedAi]
 * já documenta não saber evitar (blefar só faz sentido para quem finge não saber o que sabe).
 *
 * Em vez disso, esta IA estima **a própria chance de vencer** por amostragem: embaralha o que
 * resta do baralho muitas vezes, completa mãos aleatórias para quem ainda está na mão e a mesa
 * até a quinta carta, e conta em quantas dessas mãos imaginadas [bestHand] a colocaria na
 * frente. É uma média sobre adversários de mão aleatória, não sobre o que eles realmente
 * jogariam — mais simples do que estimar o alcance de cada um pelas apostas já feitas, e
 * suficiente para uma decisão de apostar, pagar ou desistir.
 *
 * A dificuldade entra de dois jeitos: menos amostras deixa a estimativa mais ruidosa sozinha
 * (fácil vê mão errada com mais frequência, sem precisar de regra especial para isso), e por
 * cima disso o mesmo [defaultMistakeChance] dos outros jogos — de vez em quando joga qualquer
 * lance legal, não o que a conta indicaria.
 */
object PokerAi : GameAi<PokerState, PokerMove> {

    /** A partir daqui, sem ninguém apostado, vale apostar por valor em vez de só passar. */
    private const val LIMIAR_APOSTA = 0.55

    /** A partir daqui, respondendo a uma aposta, vale aumentar em vez de só pagar. */
    private const val LIMIAR_AUMENTO = 0.68

    /** Aumenta para o teto quando a mão está muito bem — o resto dos aumentos é sempre o mínimo. */
    private const val LIMIAR_AUMENTO_MAXIMO = 0.82

    override fun chooseMove(state: PokerState, difficulty: Difficulty, seed: Long): PokerMove? {
        val moves = PokerGame.legalMoves(state)
        if (moves.isEmpty()) return null
        if (moves.size == 1) return moves.first()

        var rng = Rng.seeded(seed)
        val chance = defaultMistakeChance(difficulty)
        if (chance > 0) {
            val sorteio = rng.nextInt(100)
            rng = sorteio.rng
            if (sorteio.value < chance) return moves[rng.nextInt(moves.size).value]
        }

        val amostras = when (difficulty) {
            Difficulty.EASY -> 30
            Difficulty.MEDIUM -> 100
            Difficulty.HARD -> 250
        }
        val equity = estimateEquity(state, rng, amostras)
        val aumentos = moves.filterIsInstance<PokerMove.Raise>()
        val paraPagar = state.toCall(state.turn)

        if (paraPagar == 0) {
            return if (equity >= LIMIAR_APOSTA && aumentos.isNotEmpty()) {
                escolheAumento(aumentos, equity)
            } else {
                moves.first { it is PokerMove.Check }
            }
        }

        val potOdds = paraPagar.toDouble() / (state.pot + paraPagar)
        return when {
            equity >= LIMIAR_AUMENTO && aumentos.isNotEmpty() -> escolheAumento(aumentos, equity)
            equity > potOdds -> moves.first { it is PokerMove.Call }
            else -> moves.first { it is PokerMove.Fold }
        }
    }

    private fun escolheAumento(aumentos: List<PokerMove.Raise>, equity: Double): PokerMove {
        val ordenados = aumentos.sortedBy { it.to }
        return if (equity >= LIMIAR_AUMENTO_MAXIMO) ordenados.last() else ordenados.first()
    }

    /**
     * A fração das [amostras] mãos aleatórias — completando o baralho para todo mundo que
     * ainda está na mão e ainda não mostrou carta — em que a cadeira da vez venceria o
     * showdown. Empate soma fração de ponto igual à divisão do pote entre os empatados, e não
     * um ponto inteiro: contar como vitória cheia inflaria a força de mãos que só empatam.
     *
     * `internal`, e não `private`: só para o teste poder cravar uma mão já revelada e conferir
     * o número exato, em vez de inferir da decisão final.
     */
    internal fun estimateEquity(state: PokerState, rng: Rng, amostras: Int): Double {
        val seat = state.turn
        val minhaMao = state.hand(seat)
        val oponentes = (0 until state.seats).filter { it != seat.index && !state.folded[it] }
        if (oponentes.isEmpty()) return 1.0

        // Quem já foi all-in e mostrou a mão (ver PokerGame.redactFor) tem carta conhecida,
        // não sorteada — mesa de verdade nenhuma esconde de novo uma carta que já virou. Sem
        // isto, a mesma carta podia ser sorteada duas vezes: uma na mão dele, de fato, e outra
        // por acidente na mão de outro oponente ou na mesa.
        val conhecidas = oponentes.associateWith { indice -> maoConhecida(state, indice) }
        val paraSortear = oponentes.filter { conhecidas[it] == null }

        val usadas = (minhaMao + state.board + conhecidas.values.filterNotNull().flatten()).toHashSet()
        val remanescente = standardDeck().filterNot { it in usadas }
        val faltamNaMesa = 5 - state.board.size

        var pontos = 0.0
        var atual = rng
        repeat(amostras) {
            val embaralhado = atual.shuffle(remanescente)
            atual = embaralhado.rng
            val baralho = embaralhado.value

            var indice = 0
            val sorteadas = paraSortear.associateWith { baralho.subList(indice, indice + 2).also { indice += 2 } }
            val maosOponentes = oponentes.map { conhecidas.getValue(it) ?: sorteadas.getValue(it) }
            val mesaCompleta = state.board + baralho.subList(indice, indice + faltamNaMesa)

            val minhaForca = bestHand(minhaMao + mesaCompleta)
            val forcasOponentes = maosOponentes.map { bestHand(it + mesaCompleta) }
            val melhorOponente = forcasOponentes.max()
            pontos += when {
                minhaForca > melhorOponente -> 1.0
                minhaForca == melhorOponente -> 1.0 / (1 + forcasOponentes.count { it == melhorOponente })
                else -> 0.0
            }
        }
        return pontos / amostras
    }

    /**
     * A mão da cadeira [indice], se [PokerState.allInRevealed] diz que ela é pública; `null`
     * se ainda é segredo.
     *
     * A pergunta é a mesma regra do jogo que [PokerGame.redactFor] usa para decidir o que
     * mostrar — não "a carta que está neste [state] por acaso não é [Card.isHidden]". Quem
     * ainda está decidindo não vira mão pública só porque, por algum motivo, chegou aqui um
     * estado que ninguém redigiu.
     */
    private fun maoConhecida(state: PokerState, indice: Int): List<Card>? {
        val seat = Seat(indice)
        return state.hand(seat).takeIf { it.isNotEmpty() && state.allInRevealed(seat) }
    }
}
