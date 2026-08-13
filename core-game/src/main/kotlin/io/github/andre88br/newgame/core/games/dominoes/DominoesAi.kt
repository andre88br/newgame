package io.github.andre88br.newgame.core.games.dominoes

import io.github.andre88br.newgame.core.ai.DeterminizedAi
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.Evaluator
import io.github.andre88br.newgame.core.ai.MoveOrdering
import io.github.andre88br.newgame.core.ai.SearchLimits
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat

/**
 * Avaliação do dominó.
 *
 * O que decide a partida é sair das peças pesadas antes de ficar preso com elas,
 * e não deixar o adversário sem encaixe por acaso. Daí a conta: pontos na mão pesam
 * negativo — é isso que se perde ao fechar o jogo —, mão menor pesa positivo, e ter peça
 * para as duas pontas vale um bônus, porque a pior situação do dominó é comprar meio monte.
 */
object DominoesEvaluator : Evaluator<DominoesState> {

    /**
     * A comparação é sempre com quem está **melhor** entre os outros.
     *
     * Numa mesa de três ou quatro, medir contra a média deixaria a máquina tranquila
     * enquanto alguém está prestes a bater. Quem decide a partida é o mais adiantado.
     */
    override fun evaluate(state: DominoesState, seat: Seat): Int {
        val outros = state.others(seat)
        if (outros.isEmpty()) return 0

        // Quem está mais perto de bater: menos peças na mão, e a mão menor desempata.
        val lider = outros.minByOrNull { state.handSize(it) * 100 + state.pipsInHand(it) } ?: outros.first()

        // Ponto na mão é ponto que se entrega se o jogo fechar.
        val pips = (state.pipsInHand(lider) - state.pipsInHand(seat)) * 10

        // Estar mais perto de bater vale muito: bater é a vitória.
        val tiles = (state.handSize(lider) - state.handSize(seat)) * 40

        val flexibility = (playableCount(state, seat) - playableCount(state, lider)) * 8

        return pips + tiles + flexibility
    }

    /** Quantas peças da mão encaixam em alguma ponta agora. */
    private fun playableCount(state: DominoesState, seat: Seat): Int {
        val left = state.leftEnd ?: return state.handSize(seat)
        val right = state.rightEnd ?: return state.handSize(seat)
        return state.hand(seat).count { !it.isHidden && (it.matches(left) || it.matches(right)) }
    }
}

/** Peça pesada primeiro: livrar-se do que dói se o jogo fechar. */
val DominoesOrdering: MoveOrdering<DominoesState, DominoesMove> =
    MoveOrdering<DominoesState, DominoesMove> { _, moves ->
        if (moves.size < 2) moves else moves.sortedByDescending { it.tile.pips }
    }

/**
 * Inventa uma mão para o adversário e um monte, respeitando o que já se sabe.
 *
 * O que se sabe é: quais peças estão na mesa, quais estão na própria mão, e **quantas**
 * peças o adversário e o monte têm. Todo o resto é sorteado entre as peças que sobraram.
 * Cada mundo desses é uma partida possível e coerente — nunca aparece uma peça repetida
 * nem uma que já está na mesa.
 */
internal fun completeDominoes(state: DominoesState, rng: Rng): DominoesState {
    val onTable = state.line.map { it.tile }.toSet()
    val known = state.hands.flatten().filterNot { it.isHidden }.toSet()

    val unknownPool = Tile.fullSet().filterNot { it in onTable || it in known }
    val shuffled = rng.shuffle(unknownPool).value

    var index = 0
    val hands = state.hands.map { hand ->
        hand.map { tile ->
            if (tile.isHidden) shuffled[index++] else tile
        }
    }
    val boneyard = state.boneyard.map { tile ->
        if (tile.isHidden) shuffled[index++] else tile
    }

    return state.copy(hands = hands, boneyard = boneyard)
}

val DominoesAi: DeterminizedAi<DominoesState, DominoesMove> = DeterminizedAi(
    game = DominoesGame,
    evaluator = DominoesEvaluator,
    ordering = DominoesOrdering,
    limits = { difficulty ->
        // A profundidade é modesta de propósito: o custo aqui multiplica pelo número de
        // mundos sorteados, e vinte buscas rasas decidem melhor que uma busca funda sobre
        // um único palpite de mão adversária.
        when (difficulty) {
            Difficulty.EASY -> SearchLimits(maxDepth = 2, timeBudgetMillis = 120)
            Difficulty.MEDIUM -> SearchLimits(maxDepth = 4, timeBudgetMillis = 180)
            Difficulty.HARD -> SearchLimits(maxDepth = 6, timeBudgetMillis = 250)
        }
    },
    complete = ::completeDominoes,
)
