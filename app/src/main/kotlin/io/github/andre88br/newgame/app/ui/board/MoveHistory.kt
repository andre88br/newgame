package io.github.andre88br.newgame.app.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.ui.theme.MoveNotationStyle
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.session.PlayedMove

/**
 * Os lances da partida numa faixa que rola na horizontal.
 *
 * Vertical seria mais parecido com uma planilha de xadrez, mas disputaria altura com o
 * tabuleiro — que é o que a pessoa precisa ver num celular. A faixa acompanha o último
 * lance sozinha, então quem está jogando nunca precisa arrastar.
 *
 * A numeração conta os lances da primeira cadeira, e não pares de lances. Parece a mesma
 * coisa no xadrez, mas não é no reversi: lá quem fica sem lance perde a vez, e contar de
 * dois em dois faria a numeração desandar depois do primeiro passe.
 */
@Composable
fun MoveHistory(history: List<PlayedMove>, modifier: Modifier = Modifier) {
    if (history.isEmpty()) return

    val listState = rememberLazyListState()

    LaunchedEffect(history.size) {
        if (history.isNotEmpty()) listState.animateScrollToItem(history.lastIndex)
    }

    var number = 0
    val labels = history.map { played ->
        if (played.seat == Seat.FIRST) number++
        val prefix = if (played.seat == Seat.FIRST) "$number." else ""
        prefix to played.notation
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(labels.size) { index ->
            val (prefix, notation) = labels[index]
            val isLast = index == labels.lastIndex
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isLast) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (prefix.isEmpty()) notation else "$prefix $notation",
                    style = MoveNotationStyle,
                    color = if (isLast) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
