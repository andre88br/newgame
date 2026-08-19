package io.github.andre88br.newgame.app.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.board.surfaces.CardFace
import io.github.andre88br.newgame.app.ui.theme.LocalBoardPalette
import io.github.andre88br.newgame.core.cards.Card
import io.github.andre88br.newgame.core.cards.Rank
import io.github.andre88br.newgame.core.cards.Suit

/**
 * As nove mãos do pôquer, da melhor para a pior, cada uma com um exemplo desenhado.
 *
 * O texto das regras já lista os nomes em prosa; aqui é a mesma ordem, mas com as cartas do
 * exemplo à vista — quem não sabe reconhecer um "full house" de olho reconhece pelo desenho.
 */
@Composable
fun PokerHandRankings() {
    val palette = LocalBoardPalette.current
    val maos = listOf(
        R.string.poker_hand_straight_flush to listOf(
            Card(Rank.NINE, Suit.SPADES), Card(Rank.TEN, Suit.SPADES), Card(Rank.JACK, Suit.SPADES),
            Card(Rank.QUEEN, Suit.SPADES), Card(Rank.KING, Suit.SPADES),
        ),
        R.string.poker_hand_four_of_a_kind to listOf(
            Card(Rank.ACE, Suit.CLUBS), Card(Rank.ACE, Suit.DIAMONDS), Card(Rank.ACE, Suit.HEARTS),
            Card(Rank.ACE, Suit.SPADES), Card(Rank.KING, Suit.CLUBS),
        ),
        R.string.poker_hand_full_house to listOf(
            Card(Rank.KING, Suit.CLUBS), Card(Rank.KING, Suit.DIAMONDS), Card(Rank.KING, Suit.HEARTS),
            Card(Rank.EIGHT, Suit.SPADES), Card(Rank.EIGHT, Suit.CLUBS),
        ),
        R.string.poker_hand_flush to listOf(
            Card(Rank.ACE, Suit.HEARTS), Card(Rank.JACK, Suit.HEARTS), Card(Rank.EIGHT, Suit.HEARTS),
            Card(Rank.SIX, Suit.HEARTS), Card(Rank.THREE, Suit.HEARTS),
        ),
        R.string.poker_hand_straight to listOf(
            Card(Rank.FIVE, Suit.CLUBS), Card(Rank.SIX, Suit.DIAMONDS), Card(Rank.SEVEN, Suit.HEARTS),
            Card(Rank.EIGHT, Suit.SPADES), Card(Rank.NINE, Suit.CLUBS),
        ),
        R.string.poker_hand_three_of_a_kind to listOf(
            Card(Rank.SEVEN, Suit.CLUBS), Card(Rank.SEVEN, Suit.DIAMONDS), Card(Rank.SEVEN, Suit.HEARTS),
            Card(Rank.KING, Suit.SPADES), Card(Rank.FOUR, Suit.CLUBS),
        ),
        R.string.poker_hand_two_pair to listOf(
            Card(Rank.JACK, Suit.CLUBS), Card(Rank.JACK, Suit.DIAMONDS), Card(Rank.FOUR, Suit.HEARTS),
            Card(Rank.FOUR, Suit.SPADES), Card(Rank.NINE, Suit.CLUBS),
        ),
        R.string.poker_hand_pair to listOf(
            Card(Rank.TEN, Suit.CLUBS), Card(Rank.TEN, Suit.DIAMONDS), Card(Rank.KING, Suit.HEARTS),
            Card(Rank.SIX, Suit.SPADES), Card(Rank.TWO, Suit.CLUBS),
        ),
        R.string.poker_hand_high_card to listOf(
            Card(Rank.ACE, Suit.CLUBS), Card(Rank.KING, Suit.DIAMONDS), Card(Rank.NINE, Suit.HEARTS),
            Card(Rank.SIX, Suit.SPADES), Card(Rank.THREE, Suit.CLUBS),
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.poker_hand_rankings_title),
            style = MaterialTheme.typography.titleSmall,
        )
        maos.forEach { (labelRes, cartas) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    cartas.forEach { carta -> CardFace(card = carta, palette = palette) }
                }
            }
        }
    }
}

/**
 * O valor de cada carta na canastra, com uma carta desenhada representando cada faixa.
 *
 * O texto das regras já diz os números; isto é só o mesmo dado de outro jeito, para quem
 * reconhece a carta mais rápido do que lê "do oito ao rei".
 */
@Composable
fun CanastraCardValues() {
    val palette = LocalBoardPalette.current
    val valores = listOf(
        R.string.canastra_value_wild to Card(Rank.TWO, Suit.HEARTS),
        R.string.canastra_value_three to Card(Rank.THREE, Suit.DIAMONDS),
        R.string.canastra_value_ace to Card(Rank.ACE, Suit.SPADES),
        R.string.canastra_value_high to Card(Rank.KING, Suit.CLUBS),
        R.string.canastra_value_low to Card(Rank.FIVE, Suit.CLUBS),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.canastra_card_values_title),
            style = MaterialTheme.typography.titleSmall,
        )
        valores.forEach { (labelRes, carta) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardFace(card = carta, palette = palette)
                Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
