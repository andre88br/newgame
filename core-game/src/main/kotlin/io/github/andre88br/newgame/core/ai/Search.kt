package io.github.andre88br.newgame.core.ai

import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import kotlin.math.abs

/**
 * Quanto uma posição vale para uma cadeira.
 *
 * Positivo é bom para [seat], negativo é bom para o adversário, e a escala é a mesma em
 * todos os jogos: use [PIECE] como referência de "uma peça de vantagem".
 */
fun interface Evaluator<S : GameState> {
    fun evaluate(state: S, seat: Seat): Int

    companion object {
        /** Valor de referência de uma peça simples. */
        const val PIECE: Int = 100

        /** Vitória. Fica bem acima de qualquer avaliação posicional possível. */
        const val WIN: Int = 1_000_000

        /** Acima disso, a pontuação representa vitória forçada e não estimativa. */
        const val WIN_THRESHOLD: Int = WIN - 10_000
    }
}

/**
 * Ordem em que os lances entram na busca. Examinar primeiro os lances promissores faz a
 * poda alpha-beta cortar muito mais cedo — na prática vale mais que otimizar a avaliação.
 */
fun interface MoveOrdering<S : GameState, M : Move> {
    fun order(state: S, moves: List<M>): List<M>

    companion object {
        fun <S : GameState, M : Move> none(): MoveOrdering<S, M> = MoveOrdering { _, moves -> moves }
    }
}

data class SearchLimits(
    /** Teto de profundidade. O orçamento de tempo costuma cortar antes em jogos grandes. */
    val maxDepth: Int,
    /**
     * Tempo máximo por lance. A busca é feita por aprofundamento iterativo e devolve o
     * melhor lance da última profundidade concluída, então estourar o tempo degrada a
     * qualidade em vez de travar a interface.
     */
    val timeBudgetMillis: Long,
)

data class SearchResult<M : Move>(
    val move: M?,
    val score: Int,
    val depthReached: Int,
    val nodes: Long,
    /** `true` quando o orçamento de tempo interrompeu a última profundidade. */
    val timedOut: Boolean,
)

/**
 * Busca minimax com poda alpha-beta e aprofundamento iterativo, escrita uma vez para
 * todos os jogos.
 *
 * Em vez do negamax clássico, a pontuação é sempre do ponto de vista da cadeira que pediu
 * a busca. Fica um pouco mais verboso, mas suporta jogos em que a vez **não** alterna a
 * cada lance — passe obrigatório no reversi, lance extra ao tirar 6 no ludo — que o
 * negamax avaliaria com o sinal trocado.
 */
class AlphaBetaSearch<S : GameState, M : Move>(
    private val game: BoardGame<S, M>,
    private val evaluator: Evaluator<S>,
    private val ordering: MoveOrdering<S, M> = MoveOrdering.none(),
    /**
     * Quais lances são "barulhentos" — capturas, promoções.
     *
     * Quando informado, a busca não para de repente ao acabar a profundidade: segue só
     * pelos lances barulhentos até a poeira baixar. Sem isso a avaliação é feita no meio de
     * uma troca de peças e enxerga uma vantagem que o lance seguinte desfaz — o *efeito
     * horizonte*, que no xadrez faz a IA entregar peça atrás de peça.
     */
    private val isTactical: ((S, M) -> Boolean)? = null,
    private val nanoTime: () -> Long = System::nanoTime,
) {

    private var deadline: Long = Long.MAX_VALUE
    private var nodes: Long = 0
    private var aborted: Boolean = false

    fun search(state: S, limits: SearchLimits): SearchResult<M> {
        nodes = 0
        aborted = false
        deadline = nanoTime() + limits.timeBudgetMillis * 1_000_000

        var candidates = ordering.order(state, game.legalMoves(state))
        if (candidates.isEmpty()) {
            return SearchResult(null, 0, 0, 0, false)
        }

        var best = candidates.first()
        var bestScore = 0
        var depthReached = 0
        val root = state.turn

        for (depth in 1..limits.maxDepth) {
            var alpha = -INFINITY
            var iterationBest: M? = null

            for (move in candidates) {
                val child = game.applyKnownLegal(state, move)
                val score = value(child, depth - 1, alpha, INFINITY, root)
                if (aborted) break
                if (iterationBest == null || score > alpha) {
                    alpha = score
                    iterationBest = move
                }
            }

            if (iterationBest != null) {
                best = iterationBest
                bestScore = alpha
                depthReached = depth
                // O melhor lance desta profundidade abre a próxima: é o que mais poda.
                candidates = listOf(iterationBest) + candidates.filter { it != iterationBest }
            }
            if (aborted) break
            // Vitória forçada já encontrada: aprofundar não muda a decisão.
            if (abs(bestScore) >= Evaluator.WIN_THRESHOLD) break
        }

        return SearchResult(best, bestScore, depthReached, nodes, aborted)
    }

    private fun value(state: S, depth: Int, alphaIn: Int, betaIn: Int, root: Seat): Int {
        nodes++
        if (nodes and TIME_CHECK_MASK == 0L && nanoTime() > deadline) {
            aborted = true
            return 0
        }

        val outcome = game.outcome(state)
        if (outcome.isOver) return terminalScore(outcome, root, state.ply)
        if (depth <= 0) {
            return if (isTactical == null) {
                evaluator.evaluate(state, root)
            } else {
                quiescence(state, alphaIn, betaIn, root, QUIESCENCE_DEPTH)
            }
        }

        val moves = ordering.order(state, game.legalMoves(state))
        // Sem lances mas sem desfecho definido: o jogo deveria ter declarado o resultado.
        // Avaliar a posição é mais seguro do que devolver um valor inventado.
        if (moves.isEmpty()) return evaluator.evaluate(state, root)

        var alpha = alphaIn
        var beta = betaIn

        return if (state.turn == root) {
            var best = -INFINITY
            for (move in moves) {
                val child = game.applyKnownLegal(state, move)
                val score = value(child, depth - 1, alpha, beta, root)
                if (aborted) return if (best == -INFINITY) alpha else best
                if (score > best) best = score
                if (best > alpha) alpha = best
                if (alpha >= beta) break
            }
            best
        } else {
            var best = INFINITY
            for (move in moves) {
                val child = game.applyKnownLegal(state, move)
                val score = value(child, depth - 1, alpha, beta, root)
                if (aborted) return if (best == INFINITY) beta else best
                if (score < best) best = score
                if (best < beta) beta = best
                if (alpha >= beta) break
            }
            best
        }
    }

    /**
     * Continua a busca só pelos lances barulhentos, até a posição ficar quieta.
     *
     * O `standPat` é a avaliação de não fazer nada: quem está na vez quase sempre pode
     * parar por aí, então ele serve de piso (ou teto) e permite podar antes de olhar
     * captura nenhuma. Só os lances marcados por [isTactical] são examinados, e a
     * profundidade é limitada porque uma sequência de capturas pode ser longa.
     */
    private fun quiescence(state: S, alphaIn: Int, betaIn: Int, root: Seat, depthLeft: Int): Int {
        nodes++
        if (nodes and TIME_CHECK_MASK == 0L && nanoTime() > deadline) {
            aborted = true
            return 0
        }

        val moves = game.legalMoves(state)
        if (moves.isEmpty()) return terminalScore(game.outcome(state), root, state.ply)

        val standPat = evaluator.evaluate(state, root)
        if (depthLeft <= 0) return standPat

        val tactical = isTactical ?: return standPat
        val noisy = moves.filter { tactical(state, it) }
        if (noisy.isEmpty()) return standPat

        var alpha = alphaIn
        var beta = betaIn

        return if (state.turn == root) {
            if (standPat >= beta) return standPat
            var best = standPat
            if (best > alpha) alpha = best
            for (move in ordering.order(state, noisy)) {
                val child = game.applyKnownLegal(state, move)
                val score = quiescence(child, alpha, beta, root, depthLeft - 1)
                if (aborted) return best
                if (score > best) best = score
                if (best > alpha) alpha = best
                if (alpha >= beta) break
            }
            best
        } else {
            if (standPat <= alpha) return standPat
            var best = standPat
            if (best < beta) beta = best
            for (move in ordering.order(state, noisy)) {
                val child = game.applyKnownLegal(state, move)
                val score = quiescence(child, alpha, beta, root, depthLeft - 1)
                if (aborted) return best
                if (score < best) best = score
                if (best < beta) beta = best
                if (alpha >= beta) break
            }
            best
        }
    }

    private fun terminalScore(outcome: Outcome, root: Seat, ply: Int): Int = when (outcome) {
        is Outcome.Win ->
            // Descontar o lance faz a IA preferir ganhar rápido e demorar para perder.
            if (outcome.seat == root) Evaluator.WIN - ply else -Evaluator.WIN + ply
        is Outcome.Draw -> 0
        Outcome.InProgress -> 0
    }

    private companion object {
        const val INFINITY = Int.MAX_VALUE / 2

        /** Consulta o relógio a cada 1024 nós: barato o bastante para não pesar na busca. */
        const val TIME_CHECK_MASK = 1023L

        /** Teto de meios-lances da busca de quiescência, para trocas longas não escaparem. */
        const val QUIESCENCE_DEPTH = 6
    }
}
