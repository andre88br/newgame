package io.github.andre88br.newgame.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BotNamesTest {

    @Test
    fun `a mesma semente devolve os mesmos nomes`() {
        assertEquals(BotNames.pick(3, seed = 42L), BotNames.pick(3, seed = 42L))
    }

    @Test
    fun `sementes diferentes costumam devolver nomes diferentes`() {
        // Não é garantia absoluta — dois sorteios podem coincidir —, mas entre dez
        // sementes ao menos duas listas têm que diferir, ou o sorteio não está sorteando.
        val listas = (1L..10L).map { BotNames.pick(3, seed = it) }.toSet()
        assertTrue(listas.size > 1, "dez sementes deram sempre a mesma lista: $listas")
    }

    @Test
    fun `os nomes de uma mesa nunca se repetem`() {
        for (seed in 1L..50L) {
            val nomes = BotNames.pick(4, seed)
            assertEquals(4, nomes.size)
            assertEquals(4, nomes.toSet().size, "nomes repetidos na semente $seed: $nomes")
        }
    }

    @Test
    fun `o nome de quem joga não é dado a uma máquina`() {
        val meu = BotNames.ALL.first()
        val nomes = BotNames.pick(3, seed = 7L, exclude = listOf(meu.lowercase()))
        assertFalse(nomes.any { it.equals(meu, ignoreCase = true) }, "a máquina roubou o nome: $nomes")
    }

    @Test
    fun `pedir mais nomes do que a lista tem continua devolvendo nomes distintos`() {
        val nomes = BotNames.pick(BotNames.ALL.size + 5, seed = 3L)
        assertEquals(BotNames.ALL.size + 5, nomes.size)
        assertEquals(nomes.size, nomes.toSet().size)
        assertTrue(nomes.none { it.isBlank() })
    }

    @Test
    fun `pedir zero nomes devolve lista vazia`() {
        assertEquals(emptyList(), BotNames.pick(0, seed = 1L))
    }

    @Test
    fun `o nome digitado perde o excesso de espaço e o excesso de letras`() {
        assertEquals("Ana Maria", BotNames.sanitize("  Ana   Maria  "))
        assertEquals("", BotNames.sanitize("   "))
        assertTrue(BotNames.sanitize("A".repeat(200)).length <= BotNames.MAX_LENGTH)
    }
}
