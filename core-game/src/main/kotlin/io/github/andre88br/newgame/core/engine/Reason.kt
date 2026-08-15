package io.github.andre88br.newgame.core.engine

import kotlinx.serialization.Serializable

/**
 * Um texto que o motor sabe **escolher** mas não sabe **escrever**.
 *
 * O motor conhece a regra e conhece o tabuleiro; o idioma do aparelho é assunto da tela.
 * Então o que atravessa a fronteira é uma chave com argumentos, e cada lado faz o que sabe.
 * O modelo em português fica junto da chave como rendição de referência — é o que os testes
 * leem e o que o app usa se faltar tradução.
 *
 * Os modelos usam os marcadores do Android (`%1${'$'}s`, `%2${'$'}s`) de propósito: o mesmo
 * texto serve, sem adaptação, como valor de `strings.xml`.
 */
interface TextTemplate {

    /** A frase em português, com os marcadores por preencher. */
    val template: String

    /** Quantos argumentos o modelo espera. Conferido por teste contra o próprio texto. */
    val arity: Int

    /** Nome do recurso de texto correspondente no app. */
    val resourceName: String
}

/** Preenche o modelo. Usado pelo motor e pelos testes; o app usa o recurso traduzido. */
fun TextTemplate.format(args: List<String>): String =
    if (args.isEmpty()) template else String.format(template, *args.toTypedArray())

/**
 * Por que um lance ou um toque não valeu. Veja [TextTemplate] para o desenho.
 *
 * Faltar tradução para uma chave vira frase em português, não vira aviso vazio: quem teve o
 * lance recusado continua sabendo o motivo.
 */
@Serializable
data class Reason(val key: ReasonKey, val args: List<String> = emptyList()) {

    /** A frase em português, com os argumentos no lugar. */
    fun text(): String = key.format(args)

    override fun toString(): String = text()
}

/** Atalho para montar um motivo com argumentos. */
fun reasonOf(key: ReasonKey, vararg args: Any): Reason =
    Reason(key, args.map { it.toString() })

/**
 * Todo motivo de recusa que o motor sabe dar.
 *
 * [arity] é quantos argumentos o modelo espera, e existe para poder ser conferido por
 * teste: modelo com marcador a mais estoura na hora de montar a frase, e é justamente na
 * hora de recusar um lance — o pior momento possível para o app quebrar.
 */
enum class ReasonKey(
    override val template: String,
    override val arity: Int = 0,
) : TextTemplate {

    // -------- comuns --------

    GAME_OVER("A partida já terminou"),
    SQUARE_TAKEN("Essa casa já está ocupada"),
    INVALID_SQUARE("Casa inválida"),
    NOT_YOUR_PIECE("Essa peça não é sua"),
    NO_PIECE_HERE("Não há peça nessa casa"),
    NO_PIECE_OF_YOURS_AT("Não há peça sua na casa %1\$s", arity = 1),
    PIECE_CANNOT_GO_THERE("Essa peça não pode ir para aí"),
    PIECE_HAS_NOWHERE_TO_GO("Essa peça não tem para onde ir"),
    PIECE_HAS_NO_MOVE("Essa peça não tem lance disponível"),
    MOVE_NOT_ALLOWED_FOR_PIECE("Lance não permitido para esta peça"),

    // -------- reversi --------

    REVERSI_NO_FLIP("Esse lance não cerca nenhuma peça do adversário"),
    REVERSI_MUST_FLIP("Só vale jogar onde se cerca alguma peça do adversário"),

    // -------- damas --------

    CAPTURE_MANDATORY("Captura é obrigatória: existe lance que captura %1\$s peça(s)", arity = 1),
    CAPTURE_MANDATORY_ELSEWHERE(
        "Captura é obrigatória: outra peça sua captura %1\$s peça(s)",
        arity = 1,
    ),
    CAPTURE_MAXIMUM(
        "É obrigatório capturar o máximo: %1\$s peça(s), e este lance captura %2\$s",
        arity = 2,
    ),
    CAPTURE_SEQUENCE_INVALID("Sequência de captura inválida"),

    // -------- xadrez --------

    KING_IN_CHECK_MUST_RESOLVE("Seu rei está em xeque: o lance precisa resolver isso"),
    KING_IN_CHECK_UNRESOLVED("Seu rei está em xeque: esse lance não resolve"),
    KING_IN_CHECK_ONLY_RESOLVING("Seu rei está em xeque: só valem lances que resolvam isso"),
    WOULD_EXPOSE_KING("Esse lance deixaria seu rei em xeque"),

    // -------- dominó --------

    DOMINO_NOT_IN_HAND("Você não tem a peça %1\$s", arity = 1),
    DOMINO_DOES_NOT_FIT("A peça %1\$s não encaixa nessa ponta", arity = 1),

    // -------- ludo --------

    LUDO_NO_SUCH_TOKEN("Esse peão não existe"),
    LUDO_NEEDS_SIX("Só um 6 tira peão do curral"),
    LUDO_ALREADY_HOME("Esse peão já chegou"),
    LUDO_EXACT_FINISH("A chegada é exata: esse peão precisa de %1\$s", arity = 1),
    LUDO_SQUARE_OCCUPIED("Já há um peão seu nessa casa"),

    // -------- jogos de carta --------

    CARD_NOT_IN_HAND("Você não tem essa carta"),
    CARD_ALREADY_DREW("Você já comprou nesta vez"),
    CARD_NOTHING_TO_DRAW("Não há de onde comprar"),

    // -------- copas --------

    HEARTS_MUST_FOLLOW_SUIT("É preciso servir o naipe pedido"),
    HEARTS_NOT_BROKEN("Copas ainda não saiu: não dá para puxar copas"),
    HEARTS_MUST_LEAD_TWO("Quem tem o 2 de paus abre a mão com ele"),
    HEARTS_NO_POINTS_FIRST_TRICK("Na primeira vaza não se descarta ponto"),

    // -------- canastra --------

    CANASTRA_MUST_DRAW_FIRST("Compre antes de baixar ou descartar"),
    CANASTRA_PILE_BLOCKED("O lixo está trancado"),
    CANASTRA_INVALID_MELD("Um jogo é uma sequência de três ou mais cartas seguidas do mesmo naipe, ou uma trinca do mesmo valor — com no máximo um curinga"),
    CANASTRA_DOES_NOT_FIT("Essa carta não encaixa nesse jogo"),
    CANASTRA_NO_SUCH_MELD("Esse jogo não existe na mesa"),
    CANASTRA_RED_THREE_NOT_PLAYABLE("O três vermelho vale ponto parado na mesa: não se joga nem se descarta"),
    CANASTRA_BLACK_THREE_NEVER_MELDS("O três preto nunca entra em jogo: ele só serve para trancar o lixo"),
    CANASTRA_NEEDS_CANASTRA_TO_GO_OUT("Sem canastra você não pode ficar sem cartas: guarde uma para descartar"),
    CANASTRA_TRINCA_NEEDS_CANASTRA("Trinca só pode ser baixada depois da primeira canastra da dupla"),
    CANASTRA_NO_WILD_TO_SWAP("Esse jogo não tem curinga para trocar"),
    CANASTRA_WILD_CANNOT_SWAP("Não há ponta livre para o curinga ir"),

    // -------- pife --------

    PIFE_MUST_DRAW_FIRST("Compre uma carta antes de descartar"),

    // -------- paciência --------

    KLONDIKE_STOCK_NOT_EMPTY("Ainda há carta no monte para comprar"),
    KLONDIKE_FOUNDATION_ORDER("A casa sobe do ás ao rei, no mesmo naipe"),
    KLONDIKE_TABLEAU_ORDER("Na coluna a carta desce uma e troca de cor"),
    KLONDIKE_EMPTY_PILE_KING_ONLY("Coluna vazia só recebe rei"),

    // -------- truco --------

    TRUCO_ANSWER_FIRST("Responda ao truco antes de jogar carta"),
    TRUCO_NOTHING_TO_ANSWER("Não há truco na mesa para responder"),
    TRUCO_NOT_YOUR_CALL("Quem trucou foi o seu lado: espere o outro aumentar"),
    TRUCO_AT_THE_TOP("Doze é o máximo: não há como aumentar"),
    ;

    /** `reason_game_over`, e assim por diante. */
    override val resourceName: String get() = "reason_" + name.lowercase()

    /** O motivo sem argumento nenhum. Só vale para chave de [arity] zero. */
    fun reason(): Reason {
        require(arity == 0) { "$name precisa de $arity argumento(s)" }
        return Reason(this)
    }
}
