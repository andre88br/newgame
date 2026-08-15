package io.github.andre88br.newgame.core.games.klondike

import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.ai.GameAi
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit

/**
 * A dica da paciência.
 *
 * Não é busca, e é de propósito. Nos outros jogos a máquina é adversário e precisa enxergar
 * longe; aqui ela é lanterna. Quem pediu dica está olhando para uma mesa com trinta cartas e
 * não achou o lance — e o que resolve isso é apontar o melhor lance **agora**, na hora, e não
 * um plano de dez lances que a pessoa teria de reconstruir sozinha.
 *
 * Buscar também seria desonesto do jeito errado: metade do baralho está virada para baixo, e
 * uma busca só valeria a pena adivinhando o que há embaixo. A escolha aqui é feita só com o
 * que está à vista, que é exatamente o que quem joga tem.
 *
 * A ordem de preferência é a de qualquer manual de paciência: desvirar carta vem antes de
 * tudo, porque é a única coisa que abre jogo de verdade; subir para a casa só quando a carta
 * não vai mais fazer falta embaixo; e mexer coluna com coluna sem desvirar nada não é lance,
 * é rearrumar a mesa.
 */
object KlondikeAi : GameAi<KlondikeState, KlondikeMove> {

    // O peso é a ordem de preferência escrita em número; o valor absoluto não significa nada.
    private const val DESVIRA = 100
    private const val CASA_BAIXA = 90
    private const val CASA_SEGURA = 80
    private const val ABRE_COLUNA = 50
    private const val CASA_ARRISCADA = 40
    private const val DO_DESCARTE = 30
    private const val COMPRA = 10
    private const val VIRA_O_DESCARTE = 5
    private const val SEM_RUMO = -10
    private const val DESCE_DA_CASA = -50

    /**
     * O nível não muda nada, e não é esquecimento.
     *
     * Nos outros jogos o nível regula o quanto a máquina erra de propósito, porque errar
     * menos deixa o jogo difícil demais para quem está começando. Dica que erra de propósito
     * não é dica fácil: é dica errada.
     */
    override fun chooseMove(state: KlondikeState, difficulty: Difficulty, seed: Long): KlondikeMove? =
        KlondikeGame.legalMoves(state).maxByOrNull { valor(state, it) }

    private fun valor(state: KlondikeState, move: KlondikeMove): Int = when (move) {
        KlondikeMove.Draw -> COMPRA
        KlondikeMove.Recycle -> VIRA_O_DESCARTE

        KlondikeMove.WasteToFoundation ->
            state.wasteTop?.let { paraCasa(state, it) } ?: SEM_RUMO

        is KlondikeMove.WasteToPile -> DO_DESCARTE

        is KlondikeMove.PileToFoundation -> {
            val carta = state.ups[move.pile].last()
            // Subir a última carta de cima desvira a de baixo: vale o mesmo que qualquer
            // outro lance que desvira.
            val desvira = state.ups[move.pile].size == 1 && state.downs[move.pile].isNotEmpty()
            if (desvira) DESVIRA else paraCasa(state, carta)
        }

        is KlondikeMove.PileToPile -> {
            val levaTudo = move.count == state.ups[move.from].size
            when {
                levaTudo && state.downs[move.from].isNotEmpty() -> DESVIRA
                // Coluna que se esvazia de vez é espaço para um rei — o recurso mais escasso
                // da paciência.
                levaTudo -> ABRE_COLUNA
                // Partir uma sequência no meio sem desvirar nada só troca as cartas de lugar.
                else -> SEM_RUMO
            }
        }

        // Descer carta da casa desfaz progresso. Só entra quando é o único lance que resta,
        // e aí entra porque continuar é melhor do que empacar.
        is KlondikeMove.FoundationToPile -> DESCE_DA_CASA
    }

    private fun paraCasa(state: KlondikeState, card: Card): Int = when {
        card.rank == Rank.ACE -> CASA_BAIXA
        naoFazMaisFalta(state, card) -> CASA_SEGURA
        else -> CASA_ARRISCADA
    }

    /**
     * A carta pode subir sem risco?
     *
     * Uma carta na casa não volta fácil, e nas colunas ela ainda pode servir de apoio para a
     * de baixo da cor trocada. A regra clássica: só sobe sem pensar quando as duas casas da
     * cor oposta já passaram do valor que precisaria dela — aí não sobrou ninguém para
     * encostar nela.
     */
    private fun naoFazMaisFalta(state: KlondikeState, card: Card): Boolean {
        val precisaria = card.rank.klondikeOrder - 1
        if (precisaria <= 1) return true
        return Suit.entries
            .filter { it.isRed != card.isRed }
            .all { (state.foundationOf(it).lastOrNull()?.rank?.klondikeOrder ?: 0) >= precisaria }
    }
}
