package io.github.andre88br.newgame.core.ai

import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Rng

/**
 * IA para jogos em que não se vê tudo.
 *
 * A busca alpha-beta precisa de uma posição completa para funcionar, e num jogo de mão
 * oculta essa posição não existe: falta saber o que o adversário tem. A saída clássica é a
 * **determinização** — inventar um mundo possível, compatível com tudo o que se sabe,
 * resolvê-lo como se fosse informação perfeita, e repetir com outros mundos. O lance que
 * vence na maioria dos mundos é o escolhido.
 *
 * Não é o mesmo que jogar bem em jogo de blefe: a determinização acredita que o adversário
 * também enxerga tudo, e por isso não valoriza esconder informação. Para dominó de dois,
 * onde o que pesa é encaixe e contagem, é aproximação boa e barata.
 *
 * [complete] é o que cada jogo precisa fornecer: dado o estado redigido e um gerador,
 * devolver um estado completo plausível.
 */
class DeterminizedAi<S : GameState, M : Move>(
    private val game: BoardGame<S, M>,
    private val evaluator: Evaluator<S>,
    private val ordering: MoveOrdering<S, M> = MoveOrdering.none(),
    private val limits: (Difficulty) -> SearchLimits,
    private val samples: (Difficulty) -> Int = ::defaultSampleCount,
    private val mistakeChance: (Difficulty) -> Int = ::defaultMistakeChance,
    /**
     * Lances óbvios demais para o sorteio de erro escolher outra coisa no lugar deles — como
     * trocar de graça o curinga de uma sequência já baixada pela carta exata, na canastra, ou
     * descartar a carta que fecha a mão no pife. Ninguém que jogue, nem no nível fácil,
     * "esquece" um lance destes; o que o nível fácil erra é o resto, não isto. Recebe o
     * estado porque alguns lances só são óbvios à luz dele — fechar a mão depende do que mais
     * está nela, por exemplo, e não dá para saber olhando só o lance. Presente algum lance
     * assim entre os legais, o sorteio nem roda — a busca de baixo segue normalmente, e ela já
     * sabe valorizar o lance.
     */
    private val neverMistaken: (S, M) -> Boolean = { _, _ -> false },
    private val complete: (S, Rng) -> S,
) : GameAi<S, M> {

    override fun chooseMove(state: S, difficulty: Difficulty, seed: Long): M? {
        val moves = game.legalMoves(state)
        if (moves.isEmpty()) return null
        if (moves.size == 1) return moves.first()

        val chance = mistakeChance(difficulty)
        if (chance > 0 && moves.none { neverMistaken(state, it) }) {
            val roll = Rng.seeded(seed).nextInt(100)
            if (roll.value < chance) {
                return moves[roll.rng.nextInt(moves.size).value]
            }
        }

        // Um voto por mundo sorteado. Empate fica com o lance que apareceu primeiro na
        // ordem de geração, que é estável — a escolha da IA não pode variar entre execuções.
        val votes = LinkedHashMap<M, Int>()
        moves.forEach { votes[it] = 0 }

        val searchLimits = limits(difficulty)
        repeat(samples(difficulty)) { sample ->
            val world = complete(state, Rng.seeded(seed + sample * PRIME))
            val best = AlphaBetaSearch(game, evaluator, ordering).search(world, searchLimits).move
            // O mundo sorteado pode oferecer um lance que a mão real não tem; ignora-se.
            if (best != null && best in votes) votes[best] = (votes[best] ?: 0) + 1
        }

        return votes.maxByOrNull { it.value }?.key ?: moves.first()
    }

    private companion object {
        /** Espaça as sementes dos mundos para eles não saírem parecidos. */
        const val PRIME = 7_919L
    }
}

/** Quantos mundos sortear por lance. Mais mundos, decisão mais estável e mais lenta. */
fun defaultSampleCount(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.EASY -> 3
    Difficulty.MEDIUM -> 8
    Difficulty.HARD -> 20
}
