package io.github.andre88br.newgame.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O catálogo de motivos é lido justamente quando alguma coisa deu errado — no momento em
 * que o lance foi recusado. Modelo com marcador a mais estoura ali, que é o pior lugar
 * possível para o app quebrar. Por isso o catálogo se confere sozinho.
 */
class ReasonTest {

    /** Quantos `%n${'$'}s` o modelo realmente usa. */
    private fun placeholders(template: String): Int =
        Regex("""%(\d+)[$]s""").findAll(template)
            .map { it.groupValues[1].toInt() }
            .maxOrNull() ?: 0

    @Test
    fun `todo modelo declara a quantidade de argumentos que usa`() {
        for (key in ReasonKey.entries) {
            assertEquals(
                placeholders(key.template),
                key.arity,
                "$key diz precisar de ${key.arity} argumento(s), mas o texto usa outra quantidade",
            )
        }
    }

    @Test
    fun `todo motivo vira frase sem estourar`() {
        for (key in ReasonKey.entries) {
            val args = List(key.arity) { "1" }
            val texto = Reason(key, args).text()
            assertTrue(texto.isNotBlank(), "$key produziu frase vazia")
            assertTrue(
                "%" !in texto,
                "$key deixou marcador sem preencher: $texto",
            )
        }
    }

    @Test
    fun `nenhum modelo esta vazio nem repetido`() {
        val textos = ReasonKey.entries.map { it.template }
        assertTrue(textos.none { it.isBlank() }, "modelo vazio no catálogo")
        assertEquals(
            textos.size,
            textos.distinct().size,
            "dois motivos com o mesmo texto: " +
                textos.groupBy { it }.filterValues { it.size > 1 }.keys,
        )
    }

    @Test
    fun `o nome do recurso segue a convencao que o app espera`() {
        assertEquals("reason_game_over", ReasonKey.GAME_OVER.resourceName)
        assertEquals("reason_capture_maximum", ReasonKey.CAPTURE_MAXIMUM.resourceName)
        assertEquals(
            ReasonKey.entries.size,
            ReasonKey.entries.map { it.resourceName }.distinct().size,
            "dois motivos disputando o mesmo recurso de texto",
        )
    }

    @Test
    fun `os argumentos entram na ordem certa`() {
        val reason = reasonOf(ReasonKey.CAPTURE_MAXIMUM, 3, 1)
        assertEquals("É obrigatório capturar o máximo: 3 peça(s), e este lance captura 1", reason.text())
    }

    @Test
    fun `pedir motivo sem argumento de uma chave que precisa deles falha cedo`() {
        val erro = runCatching { ReasonKey.CAPTURE_MAXIMUM.reason() }.exceptionOrNull()
        assertTrue(erro is IllegalArgumentException, "veio $erro")
    }

    @Test
    fun `o motivo sobrevive a ida e volta pelo JSON`() {
        // Vai junto com a partida salva quando o registro guarda o desfecho.
        val reason = reasonOf(ReasonKey.DOMINO_NOT_IN_HAND, "6-6")
        val json = GameJson.encodeToString(Reason.serializer(), reason)
        assertEquals(reason, GameJson.decodeFromString(Reason.serializer(), json))
    }
}
