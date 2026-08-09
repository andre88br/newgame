package io.github.andre88br.newgame.core.ai

import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Rng

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
}

/** Adversário controlado pelo aparelho. */
interface GameAi<S : GameState, M : Move> {
    /**
     * Escolhe o lance para `state.turn`, ou `null` se não houver lance possível.
     *
     * A [seed] deixa a escolha reproduzível: o mesmo estado com a mesma semente devolve
     * sempre o mesmo lance, o que torna a IA testável. Em jogo de verdade a tela passa
     * uma semente diferente a cada vez, para o nível fácil não repetir sempre os mesmos
     * erros.
     */
    fun chooseMove(state: S, difficulty: Difficulty, seed: Long): M?
}

/**
 * IA padrão: busca alpha-beta com um pouco de erro proposital nos níveis mais baixos.
 *
 * Reduzir só a profundidade não basta para fazer um nível fácil agradável — uma busca
 * rasa ainda joga certinho e ganha da maioria dos iniciantes. Errar de propósito de vez
 * em quando produz um adversário que dá para vencer sem parecer quebrado.
 */
class SearchBasedAi<S : GameState, M : Move>(
    private val game: BoardGame<S, M>,
    private val evaluator: Evaluator<S>,
    private val ordering: MoveOrdering<S, M> = MoveOrdering.none(),
    private val limits: (Difficulty) -> SearchLimits,
    private val mistakeChance: (Difficulty) -> Int = ::defaultMistakeChance,
    /** Veja [AlphaBetaSearch.isTactical]: só faz diferença em jogo com troca de peças. */
    private val isTactical: ((S, M) -> Boolean)? = null,
) : GameAi<S, M> {

    override fun chooseMove(state: S, difficulty: Difficulty, seed: Long): M? {
        val moves = game.legalMoves(state)
        if (moves.isEmpty()) return null
        if (moves.size == 1) return moves.first()

        val chance = mistakeChance(difficulty)
        if (chance > 0) {
            val roll = Rng.seeded(seed).nextInt(100)
            if (roll.value < chance) {
                return moves[roll.rng.nextInt(moves.size).value]
            }
        }

        val search = AlphaBetaSearch(game, evaluator, ordering, isTactical)
        return search.search(state, limits(difficulty)).move ?: moves.first()
    }

    /** Diagnóstico para testes e depuração: expõe profundidade, nós e pontuação. */
    fun analyse(state: S, difficulty: Difficulty): SearchResult<M> =
        AlphaBetaSearch(game, evaluator, ordering, isTactical).search(state, limits(difficulty))
}

/** Chance, em porcentagem, de a IA jogar um lance qualquer em vez do melhor. */
fun defaultMistakeChance(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.EASY -> 30
    Difficulty.MEDIUM -> 8
    Difficulty.HARD -> 0
}

/** Fachada sem genéricos, para a camada de aplicação guardar IAs numa lista. */
interface AnyAi {
    fun chooseMove(state: GameState, difficulty: Difficulty, seed: Long): Move?
}

fun <S : GameState, M : Move> GameAi<S, M>.asAnyAi(): AnyAi = object : AnyAi {
    @Suppress("UNCHECKED_CAST")
    override fun chooseMove(state: GameState, difficulty: Difficulty, seed: Long): Move? =
        this@asAnyAi.chooseMove(state as S, difficulty, seed)
}
