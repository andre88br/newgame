package io.github.andre88br.newgame.core.engine

import kotlinx.serialization.Serializable

/** Um valor sorteado junto com o gerador já avançado. */
data class Roll<out T>(val value: T, val rng: Rng)

/**
 * Gerador pseudoaleatório **imutável e serializável** (splitmix64).
 *
 * Cada sorteio devolve o valor e um novo gerador, em vez de mudar o estado interno.
 * Isso mantém `applyMove` puro mesmo nos jogos com sorte: o estado do gerador vive
 * dentro do `GameState`, é salvo com a partida e reproduz a mesma sequência ao ser
 * recarregado.
 *
 * O algoritmo é implementado aqui de propósito, em vez de usar `kotlin.random.Random`:
 * a sequência precisa ser idêntica em qualquer plataforma e em qualquer versão da
 * biblioteca padrão, ou partidas salvas deixariam de reproduzir.
 */
@Serializable
@JvmInline
value class Rng(val state: Long) {

    fun nextLong(): Roll<Long> {
        val next = state + GOLDEN_GAMMA
        var z = next
        z = (z xor (z ushr 30)) * MIX_A
        z = (z xor (z ushr 27)) * MIX_B
        return Roll(z xor (z ushr 31), Rng(next))
    }

    /** Inteiro em `0 until bound`, sem viés. */
    fun nextInt(bound: Int): Roll<Int> {
        require(bound > 0) { "bound deve ser positivo, veio $bound" }
        var rng = this
        while (true) {
            val roll = rng.nextLong()
            rng = roll.rng
            // 31 bits altos: sempre não-negativo.
            val bits = (roll.value ushr 33).toInt()
            val value = bits % bound
            // Rejeita o resto que sobra do último bloco incompleto, que enviesaria o sorteio.
            if (bits - value + (bound - 1) >= 0) return Roll(value, rng)
        }
    }

    /** Face de um dado de [sides] lados, em `1..sides`. */
    fun nextDie(sides: Int = 6): Roll<Int> {
        val roll = nextInt(sides)
        return Roll(roll.value + 1, roll.rng)
    }

    /** Embaralhamento de Fisher-Yates. Não altera [items]. */
    fun <T> shuffle(items: List<T>): Roll<List<T>> {
        if (items.size < 2) return Roll(items.toList(), this)
        val out = items.toMutableList()
        var rng = this
        for (i in out.lastIndex downTo 1) {
            val roll = rng.nextInt(i + 1)
            rng = roll.rng
            val j = roll.value
            if (i != j) {
                val tmp = out[i]
                out[i] = out[j]
                out[j] = tmp
            }
        }
        return Roll(out, rng)
    }

    companion object {
        private const val GOLDEN_GAMMA = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        private const val MIX_A = -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        private const val MIX_B = -0x6b2fb644ecceee15L // 0x94D049BB133111EB

        /**
         * Gerador a partir de uma semente. A semente passa por uma rodada de mistura para
         * que sementes vizinhas (0, 1, 2…) não produzam sequências parecidas.
         */
        fun seeded(seed: Long): Rng = Rng(seed).nextLong().rng
    }
}
