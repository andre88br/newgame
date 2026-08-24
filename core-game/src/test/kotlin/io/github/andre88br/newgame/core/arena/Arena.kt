package io.github.andre88br.newgame.core.arena

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.GameAi
import io.github.andre88br.newgame.core.engine.BoardGame
import io.github.andre88br.newgame.core.engine.GameState
import io.github.andre88br.newgame.core.engine.MatchConfig
import io.github.andre88br.newgame.core.engine.Move
import io.github.andre88br.newgame.core.engine.MoveResult
import io.github.andre88br.newgame.core.engine.Seat
import kotlin.math.sqrt

/**
 * Placar de um duelo entre duas versões da IA.
 *
 * [margemMedia] é a diferença de pontos por partida, do ponto de vista de A, já pareada —
 * ver [Arena.duelo]. [erroPadrao] é o erro padrão dessa média: sem ele o número não diz
 * nada, porque a variância de um jogo de cartas engole diferenças pequenas com facilidade.
 */
data class ResultadoDoDuelo(
    val pares: Int,
    val vitoriasDeA: Int,
    val vitoriasDeB: Int,
    val empates: Int,
    val margemMedia: Double,
    val erroPadrao: Double,
) {
    val partidas: Int get() = pares * 2

    /** Vitórias de A sobre o total, com empate valendo meio ponto. */
    val taxaDeVitoriaDeA: Double
        get() = if (partidas == 0) 0.0 else (vitoriasDeA + empates / 2.0) / partidas

    /**
     * A margem está a mais de dois erros padrão de zero, ou seja: dificilmente é sorte.
     *
     * Dois erros padrão é o de sempre — cerca de noventa e cinco por cento de confiança.
     */
    val significativo: Boolean get() = erroPadrao > 0 && kotlin.math.abs(margemMedia) > 2 * erroPadrao

    override fun toString(): String = buildString {
        append("A ${"%.1f".format(margemMedia)} ± ${"%.1f".format(2 * erroPadrao)} pontos/partida")
        append(" | vitórias ${"%.1f".format(taxaDeVitoriaDeA * 100)}%")
        append(" ($vitoriasDeA-$empates-$vitoriasDeB em $partidas)")
        append(if (significativo) " | significativo" else " | dentro do ruído")
    }
}

/**
 * Mede a força de uma IA contra outra jogando partidas de verdade.
 *
 * Sem isto não existe "melhorar a IA": não há como distinguir um ganho real de uma
 * sequência de sorte. Um jogo de cartas tem variância enorme — a mesma versão contra ela
 * mesma ganha por dezenas de pontos por acaso — então a arena usa duas defesas:
 *
 * 1. **Sementes pareadas.** Cada semente é jogada duas vezes, com A e B trocando de lado.
 *    O baralho é o mesmo nas duas, então a sorte da distribuição entra igual para os dois
 *    lados e sai da conta. É o que faz cem sementes valerem mais do que mil partidas soltas.
 * 2. **Erro padrão.** O resultado sempre vem com a barra de erro, e [ResultadoDoDuelo]
 *    diz na cara se a diferença passou dela ou não.
 *
 * [placar] devolve os pontos por time e [timeDa] diz de que time é cada cadeira: a arena
 * não conhece as regras de nenhum jogo, só a aritmética de comparar dois jogadores.
 *
 * [limite] encerra a partida antes do fim natural — numa canastra que vai a três mil pontos
 * uma partida inteira leva minutos, e para comparar duas versões duas mãos já bastam.
 */
class Arena<S : GameState, M : Move>(
    private val game: BoardGame<S, M>,
    private val cadeiras: Int,
    private val timeDa: (Seat) -> Int,
    private val placar: (S) -> List<Int>,
    private val limite: (S) -> Boolean = { false },
    private val tetoDeLances: Int = 20_000,
) {

    /**
     * Joga uma partida com [iaPorTime] nos comandos e devolve o placar por time.
     *
     * A IA nunca recebe o estado cru: passa sempre por `redactFor`, como na tela de verdade.
     * Assim a arena mede a IA que o jogador enfrenta, não uma que enxerga demais.
     */
    fun partida(iaPorTime: List<GameAi<S, M>>, semente: Long, dificuldade: Difficulty): List<Int> {
        var state = game.initialState(MatchConfig(semente, seats = cadeiras))
        var lances = 0
        while (!game.outcome(state).isOver && !limite(state) && lances < tetoDeLances) {
            val forcado = game.forcedMove(state)
            val lance = forcado ?: run {
                val vez = state.turn
                val visto = if (game.hasHiddenInformation) game.redactFor(state, vez) else state
                iaPorTime[timeDa(vez)].chooseMove(visto, dificuldade, seed = semente * 1_000_003L + lances)
            } ?: break
            state = (game.applyMove(state, lance) as? MoveResult.Ok)?.state ?: break
            lances++
        }
        return placar(state)
    }

    /**
     * Enfrenta [a] contra [b] em cada semente de [sementes], duas vezes por semente, com os
     * lados trocados na segunda. Cada semente vira **uma** amostra: a média das duas margens
     * de A. É esse pareamento que corta a variância do baralho.
     */
    fun duelo(
        a: GameAi<S, M>,
        b: GameAi<S, M>,
        sementes: LongRange,
        dificuldade: Difficulty = Difficulty.HARD,
    ): ResultadoDoDuelo {
        val amostras = mutableListOf<Double>()
        var venceuA = 0
        var venceuB = 0
        var empatou = 0

        for (semente in sementes) {
            var soma = 0.0
            for (aComecaComOTimeZero in listOf(true, false)) {
                val times = List(quantosTimes()) { time ->
                    if ((time == 0) == aComecaComOTimeZero) a else b
                }
                val pontos = partida(times, semente, dificuldade)
                val timeDeA = if (aComecaComOTimeZero) 0 else 1
                val margem = margemDe(pontos, timeDeA)
                soma += margem
                when {
                    margem > 0 -> venceuA++
                    margem < 0 -> venceuB++
                    else -> empatou++
                }
            }
            amostras += soma / 2.0
        }

        val media = amostras.average()
        val variancia = if (amostras.size < 2) {
            0.0
        } else {
            amostras.sumOf { (it - media) * (it - media) } / (amostras.size - 1)
        }
        return ResultadoDoDuelo(
            pares = amostras.size,
            vitoriasDeA = venceuA,
            vitoriasDeB = venceuB,
            empates = empatou,
            margemMedia = media,
            erroPadrao = if (amostras.size < 2) 0.0 else sqrt(variancia / amostras.size),
        )
    }

    private fun quantosTimes(): Int = (0 until cadeiras).map { timeDa(Seat(it)) }.distinct().size

    /** Quanto [time] fez a mais do que o melhor dos outros. */
    private fun margemDe(pontos: List<Int>, time: Int): Int {
        val meu = pontos.getOrElse(time) { 0 }
        val melhorDosOutros = pontos.filterIndexed { i, _ -> i != time }.maxOrNull() ?: 0
        return meu - melhorDosOutros
    }
}
