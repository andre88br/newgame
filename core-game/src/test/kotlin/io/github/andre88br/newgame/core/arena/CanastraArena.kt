package io.github.andre88br.newgame.core.arena

import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.canastra.CanastraState

/**
 * A arena configurada para a canastra.
 *
 * [maos] limita a partida a um número de mãos em vez de deixá-la ir até os três mil pontos:
 * uma partida inteira leva minutos por causa da busca, e para comparar duas versões o que
 * importa é a diferença de pontos, que já aparece em duas mãos. O placar acumulado é o
 * próprio `scores` do estado, então nada precisa ser recontado aqui.
 */
fun arenaDaCanastra(cadeiras: Int = 4, maos: Int = 2): Arena<CanastraState, io.github.andre88br.newgame.core.games.canastra.CanastraMove> =
    Arena(
        game = io.github.andre88br.newgame.core.games.canastra.CanastraGame,
        cadeiras = cadeiras,
        timeDa = { assento: Seat -> if (cadeiras == 4) assento.index % 2 else assento.index },
        placar = { estado: CanastraState -> estado.scores },
        limite = { estado: CanastraState -> estado.handNumber >= maos },
    )
