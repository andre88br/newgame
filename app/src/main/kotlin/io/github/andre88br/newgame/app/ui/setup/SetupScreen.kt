package io.github.andre88br.newgame.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.andre88br.newgame.app.R
import io.github.andre88br.newgame.app.ui.board.surfaces.ludoArmColor
import io.github.andre88br.newgame.app.ui.components.ChoiceRow
import io.github.andre88br.newgame.app.ui.difficultyName
import io.github.andre88br.newgame.app.ui.gameName
import io.github.andre88br.newgame.app.ui.rules.HowToPlayDialog
import io.github.andre88br.newgame.core.ai.Difficulty
import io.github.andre88br.newgame.core.engine.GameEntry
import io.github.andre88br.newgame.core.engine.GameId
import io.github.andre88br.newgame.core.engine.Seat
import io.github.andre88br.newgame.core.games.ludo.LUDO_ARMS
import io.github.andre88br.newgame.core.session.BotNames
import kotlin.random.Random

/** Modo de jogo escolhido antes de começar. */
enum class MatchMode {
    AGAINST_PHONE,
    PASS_AND_PLAY,
}

/**
 * Tudo o que a tela de configuração decide, num pacote só.
 *
 * Vira um pacote porque virou gente: com os nomes, a chamada de "começar" passaria de sete
 * parâmetros soltos, e trocar dois de lugar por engano seria um erro que compila.
 */
data class MatchSetup(
    val mode: MatchMode,
    val difficulty: Difficulty,
    val humanSeat: Seat,
    val seats: Int,
    val ludoFirstArm: Int,
    /** O nome de cada cadeira, na ordem das cadeiras. Nunca vazio: já vem resolvido. */
    val names: List<String>,
    /**
     * O que a pessoa digitou para si, cru — vazio se ela não digitou nada.
     *
     * Vai separado dos [names] porque só ele serve para lembrar: guardar o nome resolvido
     * faria "Você" e "Jogador 1" voltarem preenchidos na próxima partida, como se a pessoa
     * os tivesse escolhido.
     */
    val typedOwnName: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    entry: GameEntry,
    defaultDifficulty: Difficulty,
    /** O nome usado da última vez, para o campo já vir preenchido. Vazio se não houver. */
    savedPlayerName: String,
    hasOngoingMatch: Boolean,
    onBack: () -> Unit,
    onStart: (MatchSetup) -> Unit,
) {
    var mode by remember { mutableStateOf(MatchMode.AGAINST_PHONE) }
    var difficulty by remember { mutableStateOf(defaultDifficulty) }
    var showingRules by remember { mutableStateOf(false) }

    val mesasPossiveis = entry.rules.supportedSeats.toList()

    /**
     * Jogo de uma pessoa só — hoje, a paciência.
     *
     * Sai do próprio jogo, e não de uma lista de exceções aqui: quem declara a mesa é o
     * motor, e um jogo solitário novo entra sem tocar nesta tela. Quase tudo que esta tela
     * pergunta pressupõe um segundo lado — contra quem, em que nível, quem começa —, e numa
     * mesa de um não há pergunta nenhuma dessas para fazer.
     */
    val solitario = entry.rules.supportedSeats.last == 1
    val modoEfetivo = if (solitario) MatchMode.PASS_AND_PLAY else mode
    var seats by remember(entry.id) { mutableStateOf(mesasPossiveis.first()) }

    // Some quando o número de cadeiras muda: uma cadeira escolhida numa mesa de quatro pode
    // não existir mais numa mesa de dois.
    var humanSeat by remember(seats) { mutableStateOf(Seat.FIRST) }

    // A cor do ludo é independente da cadeira: gira qual braço a cadeira zero ocupa, e por
    // isso as quatro cores continuam disponíveis mesmo numa mesa de dois.
    var ludoColor by remember(entry.id) { mutableStateOf(0) }

    // Os nomes digitados, um por cadeira possível — a lista é do tamanho da maior mesa para
    // que diminuir e voltar a aumentar o número de jogadores não apague o que já foi escrito.
    val maiorMesa = mesasPossiveis.max()
    val digitados = remember(entry.id) {
        // A cadeira zero já vem com o nome de quem costuma segurar o aparelho.
        mutableStateListOf<String>().apply {
            repeat(maiorMesa) { add(if (it == 0) savedPlayerName else "") }
        }
    }

    // A semente dos nomes da máquina. Trocá-la é o que o botão "sortear outros" faz.
    var sementeDosNomes by remember(entry.id) { mutableLongStateOf(Random.nextLong()) }

    val meuNome = digitados.getOrElse(humanSeat.index) { "" }
    val nomePadrao = stringResource(R.string.setup_name_placeholder)
    val nomesPadrao = (0 until maiorMesa).map { stringResource(R.string.player_default, it + 1) }

    // Os nomes finais das cadeiras: o que foi digitado, ou o padrão; e nas cadeiras da
    // máquina, um nome sorteado que não colida com o de quem está jogando.
    val nomesDaMaquina = if (modoEfetivo == MatchMode.AGAINST_PHONE) {
        BotNames.pick(
            count = seats - 1,
            seed = sementeDosNomes,
            exclude = digitados.map { BotNames.sanitize(it) }.filter { it.isNotBlank() },
        )
    } else {
        emptyList()
    }

    val nomes = buildList {
        var proximoDaMaquina = 0
        for (index in 0 until seats) {
            val ehPessoa = modoEfetivo == MatchMode.PASS_AND_PLAY || index == humanSeat.index
            val digitado = BotNames.sanitize(digitados.getOrElse(index) { "" })
            add(
                when {
                    !ehPessoa -> nomesDaMaquina.getOrElse(proximoDaMaquina++) { nomesPadrao[index] }
                    digitado.isNotBlank() -> digitado
                    // Contra o celular — e sozinho na paciência — quem joga é "Você"; numa
                    // mesa de gente, todo mundo precisa de um nome que distinga um do outro.
                    modoEfetivo == MatchMode.AGAINST_PHONE || solitario -> nomePadrao
                    else -> nomesPadrao[index]
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(gameName(entry)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                },
                actions = {
                    TextButton(onClick = { showingRules = true }) {
                        Text(stringResource(R.string.how_to_play))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            // Só onde há escolha: os jogos de tabuleiro fixo aceitam dois e mais nada.
            if (mesasPossiveis.size > 1) {
                ChoiceRow(
                    label = stringResource(R.string.setup_players),
                    options = mesasPossiveis,
                    selected = seats,
                    optionLabel = { stringResource(R.string.setup_players_count, it) },
                    onSelect = { seats = it },
                )
            }

            // Numa mesa de um não há contra quem jogar, e a escolha some inteira.
            if (!solitario) {
                ChoiceRow(
                    label = stringResource(R.string.setup_mode),
                    options = MatchMode.entries.toList(),
                    selected = mode,
                    optionLabel = {
                        stringResource(
                            when (it) {
                                MatchMode.AGAINST_PHONE -> R.string.setup_mode_ai
                                MatchMode.PASS_AND_PLAY -> R.string.setup_mode_local
                            },
                        )
                    },
                    onSelect = { mode = it },
                )
            }

            // Nível e quem começa só fazem sentido contra o celular.
            if (modoEfetivo == MatchMode.AGAINST_PHONE) {
                if (seats > 2) {
                    Text(
                        text = stringResource(R.string.setup_many_opponents, seats - 1),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                ChoiceRow(
                    label = stringResource(R.string.setup_difficulty),
                    options = Difficulty.entries.toList(),
                    selected = difficulty,
                    optionLabel = { difficultyName(it) },
                    onSelect = { difficulty = it },
                )

                // Onde as regras é que decidem quem abre, oferecer a escolha seria mentir:
                // no dominó abre a maior carroça, no ludo o primeiro dado que serve.
                if (entry.rules.decidesWhoStarts) {
                    Text(
                        text = stringResource(R.string.setup_rules_decide_start),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    ChoiceRow(
                        label = stringResource(R.string.setup_who_starts),
                        options = listOf(Seat.FIRST, Seat.SECOND),
                        selected = humanSeat,
                        optionLabel = {
                            stringResource(
                                if (it == Seat.FIRST) R.string.setup_you_start else R.string.setup_phone_starts,
                            )
                        },
                        onSelect = { humanSeat = it },
                    )
                }

                // Quem começa é decidido pelo dado, mas a cor continua sendo escolha: as
                // quatro ficam sempre disponíveis, mesmo numa mesa de dois — só o braço da
                // cadeira zero gira, o espaçamento entre cadeiras não muda.
                if (entry.id == GameId.LUDO) {
                    LudoColorRow(
                        selected = ludoColor,
                        onSelect = { ludoColor = it },
                    )
                }
            }

            PlayerNames(
                mode = modoEfetivo,
                solo = solitario,
                seats = seats,
                humanSeat = humanSeat,
                typed = digitados,
                machineNames = nomesDaMaquina,
                onType = { index, value ->
                    // O corte acontece já na digitação: o campo não deixa passar um nome
                    // que depois seria cortado sem aviso na hora de começar.
                    if (index in digitados.indices) {
                        digitados[index] = value.take(BotNames.MAX_LENGTH)
                    }
                },
                onShuffle = { sementeDosNomes = Random.nextLong() },
            )

            if (hasOngoingMatch) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.setup_resume_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (showingRules) {
                HowToPlayDialog(entry = entry, onDismiss = { showingRules = false })
            }

            Button(
                onClick = {
                    onStart(
                        MatchSetup(
                            mode = modoEfetivo,
                            difficulty = difficulty,
                            humanSeat = humanSeat,
                            seats = seats,
                            ludoFirstArm = ludoColor,
                            names = nomes,
                            typedOwnName = BotNames.sanitize(
                                if (modoEfetivo == MatchMode.AGAINST_PHONE) {
                                    meuNome
                                } else {
                                    digitados.getOrElse(0) { "" }
                                },
                            ),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.setup_start))
            }
        }
    }
}

/**
 * Quem senta em cada cadeira.
 *
 * Contra o celular há um campo só — o da própria pessoa —, e os adversários aparecem com os
 * nomes que o sorteio deu, ao lado de um botão para sortear outros. No passa-e-joga há um
 * campo por cadeira: ali os nomes servem para saber de quem é a vez, que é a única coisa
 * que distingue um jogador do outro quando o aparelho passa de mão em mão.
 *
 * Campo vazio não impede começar. Quem não quer digitar nada joga como "Você" e "Jogador 2",
 * que é exatamente o que o app fazia antes de existirem nomes.
 */
@Composable
private fun PlayerNames(
    mode: MatchMode,
    /** Mesa de um: um campo só, e nenhum adversário para batizar. */
    solo: Boolean,
    seats: Int,
    humanSeat: Seat,
    typed: List<String>,
    machineNames: List<String>,
    onType: (index: Int, value: String) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_names),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (solo || mode == MatchMode.AGAINST_PHONE) {
            NameField(
                label = stringResource(R.string.setup_your_name),
                placeholder = stringResource(R.string.setup_name_placeholder),
                value = typed.getOrElse(humanSeat.index) { "" },
                onValueChange = { onType(humanSeat.index, it) },
            )

            // Os adversários da máquina, com o nome que o sorteio deu. É texto, e não campo:
            // batizar o adversário à mão tiraria a graça de encontrar um nome novo a cada
            // partida, e ninguém pediu para escolher o nome de quem joga contra.
            //
            // Na paciência não há adversário para nomear, e a fileira some junto.
            if (!solo) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.setup_phone_names) + ": " +
                            machineNames.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onShuffle) {
                        Text(stringResource(R.string.setup_shuffle_names))
                    }
                }
            }
        } else {
            for (index in 0 until seats) {
                NameField(
                    label = stringResource(R.string.setup_player_name, index + 1),
                    // Numa mesa de gente, "Você" não diria de quem é o campo: a sugestão é
                    // o nome que a cadeira teria se ninguém digitasse nada.
                    placeholder = stringResource(R.string.player_default, index + 1),
                    value = typed.getOrElse(index) { "" },
                    onValueChange = { onType(index, it) },
                )
            }
        }
    }
}

@Composable
private fun NameField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        // Nome é uma linha: com várias, o campo cresceria e empurraria o botão de começar
        // para fora da tela num aparelho baixo.
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * As quatro cores do ludo, para escolher antes de começar.
 *
 * Sempre as quatro, em qualquer tamanho de mesa — inclusive de dois, onde antes só vermelho
 * e amarelo apareciam prontos de fábrica. É o braço da cadeira zero que gira para caber a
 * cor escolhida; as outras cadeiras continuam em ordem a partir dali.
 *
 * Um botão de cada cor não bastaria: a mesma bolinha que aqui representa "eu sou o
 * vermelho" é a cor que aparece nos peões e no braço da cruz durante o jogo, e por isso o
 * desenho é a própria cor, não o nome dela — o nome só entra como descrição para quem usa
 * leitor de tela.
 */
@Composable
private fun LudoColorRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.setup_ludo_color),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (arm in 0 until LUDO_ARMS) {
                val name = ludoColorName(arm)
                val isSelected = arm == selected
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ludoArmColor(arm))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isSelected) 1f else 0.2f),
                            shape = CircleShape,
                        )
                        .clickable(onClickLabel = name) { onSelect(arm) }
                        .semantics { contentDescription = name },
                )
            }
        }
    }
}

@Composable
private fun ludoColorName(arm: Int): String = stringResource(
    when (arm) {
        0 -> R.string.ludo_color_red
        1 -> R.string.ludo_color_blue
        2 -> R.string.ludo_color_yellow
        else -> R.string.ludo_color_green
    },
)
