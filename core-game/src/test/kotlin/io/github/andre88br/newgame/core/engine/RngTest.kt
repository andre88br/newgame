package io.github.andre88br.newgame.core.engine

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RngTest {

    @Test
    fun `a mesma semente produz a mesma sequencia`() {
        val a = generateSequence(Rng.seeded(42)) { it.nextInt(100).rng }.take(50)
            .map { it.nextInt(100).value }.toList()
        val b = generateSequence(Rng.seeded(42)) { it.nextInt(100).rng }.take(50)
            .map { it.nextInt(100).value }.toList()
        assertContentEquals(a, b)
    }

    @Test
    fun `sementes diferentes produzem sequencias diferentes`() {
        val a = Rng.seeded(1).nextInt(1_000_000).value
        val b = Rng.seeded(2).nextInt(1_000_000).value
        assertNotEquals(a, b)
    }

    @Test
    fun `sementes vizinhas nao produzem sequencias parecidas`() {
        // Sem a mistura da semente, splitmix64 com sementes 0,1,2 devolveria valores
        // quase idênticos no primeiro sorteio.
        val first = (0L..9L).map { Rng.seeded(it).nextInt(1000).value }
        assertEquals(first.size, first.distinct().size, "valores repetidos: $first")
    }

    @Test
    fun `o gerador e imutavel`() {
        val rng = Rng.seeded(7)
        val once = rng.nextInt(1000).value
        val again = rng.nextInt(1000).value
        assertEquals(once, again, "sortear duas vezes do mesmo gerador deve dar o mesmo valor")
    }

    @Test
    fun `nextInt respeita o limite`() {
        var rng = Rng.seeded(99)
        repeat(2_000) {
            val roll = rng.nextInt(6)
            assertTrue(roll.value in 0..5, "valor fora da faixa: ${roll.value}")
            rng = roll.rng
        }
    }

    @Test
    fun `o dado cobre todas as faces sem viés grosseiro`() {
        var rng = Rng.seeded(2024)
        val counts = IntArray(7)
        val rolls = 60_000
        repeat(rolls) {
            val roll = rng.nextDie()
            counts[roll.value]++
            rng = roll.rng
        }
        assertEquals(0, counts[0], "o dado nunca deve devolver 0")
        val expected = rolls / 6
        for (face in 1..6) {
            val deviation = kotlin.math.abs(counts[face] - expected).toDouble() / expected
            assertTrue(deviation < 0.05, "face $face saiu ${counts[face]} vezes, esperado ~$expected")
        }
    }

    @Test
    fun `o embaralhamento preserva os elementos e nao altera a lista original`() {
        val original = (1..28).toList()
        val shuffled = Rng.seeded(5).shuffle(original)
        assertEquals(original.sorted(), shuffled.value.sorted())
        assertContentEquals((1..28).toList(), original)
        assertNotEquals(original, shuffled.value, "28 elementos não deveriam sair na mesma ordem")
    }

    @Test
    fun `o embaralhamento e reproduzivel`() {
        val original = (1..28).toList()
        assertContentEquals(
            Rng.seeded(11).shuffle(original).value,
            Rng.seeded(11).shuffle(original).value,
        )
    }
}
