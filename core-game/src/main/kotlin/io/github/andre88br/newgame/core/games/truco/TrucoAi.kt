package io.github.andre88br.newgame.core.games.truco

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.GameAi
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.engine.Rng
import io.github.andre88br.newgame.core.engine.Seat

/**
 * A máquina no truco.
 *
 * Não é busca, e a razão é a natureza do jogo. Nos outros jogos deste app a árvore de lances
 * contém a resposta: procurar fundo o bastante é jogar bem. No truco metade da partida
 * acontece fora da mesa — o valor da mão é decidido no truco e na resposta ao truco, e a
 * resposta certa depende do que o outro lado **acha** que você tem. Uma busca sobre as cartas
 * responderia com precisão a uma pergunta que não é a do jogo.
 *
 * O que está aqui é o que uma pessoa faz: mede a mão, olha as rodadas já jogadas, e decide.
 * Com carta boa, truca e aceita; com carta ruim, corre. E blefa de vez em quando — não por
 * capricho, mas porque um adversário que só truca com manilha é um adversário que se lê em
 * duas mãos, e jogar contra ele deixa de ser truco.
 */
object TrucoAi : GameAi<TrucoState, TrucoMove> {

    override fun chooseMove(state: TrucoState, difficulty: Difficulty, seed: Long): TrucoMove? {
        val legais = TrucoGame.legalMoves(state)
        if (legais.isEmpty()) return null

        if (state.answering) return responder(state, legais, difficulty, seed)

        // Erro de propósito: no nível fácil a máquina joga uma carta qualquer de vez em
        // quando. Vale só para a carta — errar na aposta não faz um adversário mais fácil,
        // faz um adversário sem sentido, que corre de mão boa e aguenta doze com um quatro.
        val cartas = legais.filterIsInstance<TrucoMove.Play>()
        val descuido = when (difficulty) {
            Difficulty.EASY -> 25
            Difficulty.MEDIUM -> 6
            Difficulty.HARD -> 0
        }
        val dado = Rng.seeded(seed).nextInt(100)

        if (TrucoMove.Call in legais && querTrucar(state, difficulty, dado.rng)) {
            return TrucoMove.Call
        }
        if (cartas.isEmpty()) return legais.first()
        if (descuido > 0 && dado.value < descuido) {
            return cartas[dado.rng.nextInt(cartas.size).value]
        }
        return TrucoMove.Play(escolherCarta(state, cartas.map { it.card }))
    }

    // -------- medir a mão --------

    /**
     * A nota de uma carta, de 0 a 100.
     *
     * A escala do truco não é linear para quem joga: entre o quatro e o três há dez degraus
     * quase iguais, e depois vêm quatro cartas que ganham de todas as outras. A nota estica
     * a escala inteira para o mesmo intervalo para que somar carta com carta signifique
     * alguma coisa.
     */
    private fun nota(card: Card): Int = (trucoStrength(card) - 1) * 100 / (trucoStrength(ZAP) - 1)

    /**
     * A força da mão, de 0 a 100.
     *
     * A maior carta pesa três vezes mais do que a menor, e não é arbitrário: no truco a mão
     * é, antes de tudo, a melhor carta dela. Duas manilhas e um quatro é mão de trucar; três
     * cartas medianas é mão de jogar quieto.
     *
     * A conta é dividida pelo peso usado, e não por um teto fixo, para que a nota continue
     * comparável quando só resta uma carta na mão.
     */
    private fun forca(state: TrucoState, seat: Seat): Int {
        val notas = state.hand(seat).map { nota(it) }.sortedDescending()
        if (notas.isEmpty()) return 0
        val pesos = listOf(3, 2, 1)
        var soma = 0
        var divisor = 0
        notas.forEachIndexed { indice, valor ->
            val peso = pesos.getOrElse(indice) { 1 }
            soma += valor * peso
            divisor += peso
        }
        return soma / divisor
    }

    /**
     * A força mais o que as rodadas já jogadas dizem.
     *
     * Ter feito a primeira vale muito: com ela, empatar a segunda já ganha a mão. Ter perdido
     * a primeira vale o contrário, e na mesma medida.
     */
    private fun confianca(state: TrucoState, seat: Seat): Int {
        val meu = state.teamOf(seat)
        val ajuste = 20 * state.roundsWon(meu) - 20 * state.roundsWon(1 - meu)
        return (forca(state, seat) + ajuste).coerceIn(0, 100)
    }

    // -------- a aposta --------

    private fun querTrucar(state: TrucoState, difficulty: Difficulty, rng: Rng): Boolean {
        val limiar = when (difficulty) {
            Difficulty.EASY -> 86
            Difficulty.MEDIUM -> 74
            Difficulty.HARD -> 68
        }
        if (confianca(state, state.turn) >= limiar) return true

        // O blefe só sai do primeiro degrau. Blefar um seis é pagar três para descobrir que
        // o outro lado tem carta, e isso não é blefe: é presente.
        if (state.stake != 1) return false
        val chance = when (difficulty) {
            Difficulty.EASY -> 2
            Difficulty.MEDIUM -> 8
            Difficulty.HARD -> 15
        }
        return rng.nextInt(100).value < chance
    }

    /**
     * A resposta ao truco: aceitar, aumentar ou correr.
     *
     * Quanto maior o pedido, melhor a carta precisa ser para valer a pena aguentar — é a
     * única assimetria que o truco tem, e é ela que dá sentido a trucar de mão fraca.
     */
    private fun responder(
        state: TrucoState,
        legais: List<TrucoMove>,
        difficulty: Difficulty,
        seed: Long,
    ): TrucoMove {
        val adversario = 1 - state.teamOf(state.turn)

        // Correr aqui entregaria a partida. Então não se corre: aguentar não pode sair mais
        // caro do que já está, e ainda há a chance de a carta ganhar.
        val correrPerdeTudo = state.score(adversario) + state.stake >= TRUCO_TARGET

        val nota = confianca(state, state.turn)
        val base = when (state.pending) {
            3 -> 38
            6 -> 50
            9 -> 60
            else -> 68
        }
        val aceite = base + when (difficulty) {
            // O nível fácil aguenta o que não devia, e é assim que ele perde: entrega mão
            // ruim a doze em vez de correr por um.
            Difficulty.EASY -> -20
            Difficulty.MEDIUM -> 0
            Difficulty.HARD -> 4
        }

        val podeAumentar = TrucoMove.Call in legais
        val aumento = when (difficulty) {
            Difficulty.EASY -> 200
            Difficulty.MEDIUM -> aceite + 34
            Difficulty.HARD -> aceite + 26
        }

        if (podeAumentar && nota >= aumento) return TrucoMove.Call
        if (nota >= aceite || correrPerdeTudo) return TrucoMove.Accept

        // Correr de mão fraca é o certo, mas correr **sempre** que a mão é fraca também se
        // lê. Uma pitada de teimosia deixa o adversário sem essa leitura.
        val teimosia = if (difficulty == Difficulty.HARD) 12 else 0
        if (teimosia > 0 && Rng.seeded(seed).nextInt(100).value < teimosia) {
            return TrucoMove.Accept
        }
        return TrucoMove.Run
    }

    // -------- a carta --------

    /**
     * Que carta jogar.
     *
     * Três regras, e todas dizem a mesma coisa de maneiras diferentes: **não gaste carta à
     * toa**.
     *
     * - Abrindo a rodada, vai a mais forte. Tomar a rodada — sobretudo a primeira — vale mais
     *   do que guardar carta, porque quem faz a primeira só precisa empatar depois.
     * - Com a rodada já ganha pelo parceiro, vai a mais fraca: cobrir carta que já está
     *   ganhando é jogar contra si mesmo.
     * - Com a rodada do adversário, vai a **menor carta que ganha**. Matar um quatro com o
     *   zap ganha a rodada e perde a mão.
     */
    private fun escolherCarta(state: TrucoState, mao: List<Card>): Card {
        val maisForte = mao.maxByOrNull { trucoStrength(it) } ?: mao.first()
        val maisFraca = mao.minByOrNull { trucoStrength(it) } ?: mao.first()
        if (state.table.isEmpty()) return maisForte

        val melhor = state.table.maxByOrNull { trucoStrength(it.card) } ?: return maisForte
        val meu = state.teamOf(state.turn)
        if (state.teamOf(Seat(melhor.seat)) == meu) return maisFraca

        val forcaAlvo = trucoStrength(melhor.card)
        val ganhadoras = mao.filter { trucoStrength(it) > forcaAlvo }
        return ganhadoras.minByOrNull { trucoStrength(it) } ?: maisFraca
    }
}
