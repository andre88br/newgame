package io.github.andre88br.newgame.app.data

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.GameJson
import io.github.andre88br.newgame.core.engine.MatchRecord
import io.github.andre88br.newgame.core.engine.Outcome
import io.github.andre88br.newgame.core.engine.Seat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** Uma partida guardada no aparelho. */
@Serializable
data class SavedMatch(
    val id: String,
    val record: MatchRecord,
    /** Cadeiras controladas por pessoas. Duas = passa-e-joga; uma = contra o celular. */
    val humanSeats: List<Int>,
    /** Nível do adversário do aparelho, ou `null` no passa-e-joga. */
    val difficulty: Difficulty? = null,
    val updatedAt: Long = 0L,
) {
    val gameId: GameId get() = record.gameId

    val finished: Boolean get() = record.outcome.isOver

    val againstPhone: Boolean get() = humanSeats.size == 1

    /** A cadeira da pessoa, quando se joga contra o celular. */
    val humanSeat: Seat? get() = humanSeats.singleOrNull()?.let(::Seat)
}

/**
 * Guarda as partidas num único arquivo JSON no diretório de dados do app.
 *
 * Um banco de dados seria exagero aqui, e não por preguiça: cada partida é uma semente
 * mais uma lista de lances — algumas centenas de bytes —, e nunca vão ser mais do que
 * algumas dezenas. Um arquivo evita duas dependências e um processador de anotações para
 * guardar menos dados do que cabem numa mensagem de texto. Se um dia o histórico crescer a
 * ponto de precisar de consulta, aí sim entra o Room.
 *
 * A escrita é feita em arquivo temporário e depois renomeada: se o app morrer no meio, o
 * histórico continua íntegro em vez de virar um JSON pela metade.
 */
class MatchStore(private val file: File) {

    private val mutex = Mutex()

    /**
     * A leitura inicial é síncrona, na criação do app.
     *
     * É leitura de disco na thread principal, o que normalmente se evita — mas são poucos
     * quilobytes lidos uma única vez, e a alternativa seria pior: ou uma tela de carregando
     * antes do menu, ou uma corrida em que abrir "continuar partida" encontra a lista ainda
     * vazia. As escritas, essas sim, vão para uma thread de entrada e saída.
     */
    private val _matches = MutableStateFlow(readFromDisk())

    /** Partidas da mais recente para a mais antiga. */
    val matches: StateFlow<List<SavedMatch>> = _matches.asStateFlow()

    /** Grava a partida, substituindo a de mesmo [SavedMatch.id] se já existir. */
    suspend fun put(match: SavedMatch) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = (listOf(match) + _matches.value.filterNot { it.id == match.id })
                .sortedByDescending { it.updatedAt }
                .take(MAX_MATCHES)
            _matches.value = updated
            writeToDisk(updated)
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = _matches.value.filterNot { it.id == id }
            _matches.value = updated
            writeToDisk(updated)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _matches.value = emptyList()
            writeToDisk(emptyList())
        }
    }

    /** A partida inacabada deste jogo, se houver — é o que alimenta o "continuar". */
    fun ongoing(gameId: GameId): SavedMatch? =
        _matches.value.firstOrNull { it.gameId == gameId && !it.finished }

    fun find(id: String): SavedMatch? = _matches.value.firstOrNull { it.id == id }

    /** Vitórias, derrotas e empates da pessoa contra o celular, por jogo. */
    fun statsAgainstPhone(gameId: GameId): MatchStats {
        var wins = 0
        var losses = 0
        var draws = 0
        for (match in _matches.value) {
            if (match.gameId != gameId || !match.finished || !match.againstPhone) continue
            when (val outcome = match.record.outcome) {
                is Outcome.Win -> if (outcome.seat == match.humanSeat) wins++ else losses++
                is Outcome.Draw -> draws++
                Outcome.InProgress -> Unit
            }
        }
        return MatchStats(wins, losses, draws)
    }

    private fun readFromDisk(): List<SavedMatch> {
        if (!file.exists()) return emptyList()
        return try {
            GameJson.decodeFromString(SERIALIZER, file.readText())
                .sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            // Arquivo corrompido ou de uma versão anterior do formato: começar limpo é
            // melhor do que o app não abrir. Só o histórico se perde, não a instalação.
            emptyList()
        }
    }

    private fun writeToDisk(matches: List<SavedMatch>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(GameJson.encodeToString(SERIALIZER, matches))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private companion object {
        const val MAX_MATCHES = 200
        val SERIALIZER = kotlinx.serialization.builtins.ListSerializer(SavedMatch.serializer())
    }
}

data class MatchStats(val wins: Int, val losses: Int, val draws: Int) {
    val total: Int get() = wins + losses + draws
}
