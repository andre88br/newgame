# newgame — jogos de tabuleiro para Android

App Android com vários jogos de tabuleiro, jogáveis contra o aparelho ou entre duas
pessoas no mesmo celular.

> **Status: Fase 5 concluída — o projeto está completo.** Onze jogos: Jogo da Velha, Damas,
> Reversi, Xadrez, Dominó, Ludo, Copas, Canastra, Pife, Paciência e Truco. Contra o celular,
> passa-e-joga ou sozinho, com desfazer, dica, som,
> leitor de tela, português e inglês, e a partida sobrevive a fechar o app.
> Para instalar sem montar ambiente, veja [baixar o APK do GitHub](#sem-instalar-nada-baixar-o-apk-do-github).

## Como está organizado

```
core-game/   Kotlin/JVM puro — regras, IA, orquestração da partida e toques. Sem Android.
app/         Aplicativo Android com Jetpack Compose. Só desenho e encanamento.
```

A separação não é enfeite, e a divisa foi puxada de propósito bem para dentro do
`core-game`: além das regras e da IA, moram lá a **sessão de partida** (de quem é a vez,
desfazer, dica, retomar) e a **máquina de toques** do tabuleiro (escolher peça, escolher
destino, recusar com explicação). São as partes onde erro de lógica se esconde, e assim
elas são testadas por `./gradlew :core-game:test`, em segundos, sem emulador e sem SDK.

No módulo `app` sobra o que só existe no Android: desenhar o tabuleiro num `Canvas`, tirar
a busca da IA da thread da interface e gravar a partida em disco.

## Rodando os testes

```bash
./gradlew :core-game:test
```

Funciona mesmo sem o SDK do Android instalado: nesse caso o módulo `:app` sai do build
automaticamente, com um aviso explicando por quê. Para forçar,
`-Pnewgame.androidModule=true|false`.

## Compilando o app

```bash
./gradlew :app:assembleDebug
```

Precisa do SDK do Android (o Android Studio configura sozinho).

### Sem instalar nada: baixar o APK do GitHub

Cada push dispara o fluxo em `.github/workflows/build.yml`, que roda os testes e compila o
APK de depuração num servidor do GitHub — que já tem o SDK do Android. Para instalar no
celular sem montar ambiente:

1. Aba **Actions** do repositório → o build mais recente da sua branch.
2. Seção **Artifacts**, no fim da página → baixe **`newgame-debug-apk`** (vem num `.zip`).
3. Descompacte, passe o `.apk` para o celular e abra. O Android vai pedir permissão para
   instalar de fonte desconhecida.

É um APK de depuração, assinado com a chave de debug: serve para testar, não para publicar.

## O motor

Todo jogo implementa a mesma interface, então tela, IA e persistência são escritas uma vez
só e valem para todos:

```kotlin
interface BoardGame<S : GameState, M : Move> {
    fun initialState(config: MatchConfig): S
    fun legalMoves(state: S): List<M>
    fun applyMove(state: S, move: M): MoveResult<S>
    fun outcome(state: S): Outcome
}
```

Três decisões sustentam o resto:

- **Estados imutáveis, `applyMove` puro.** Dão de graça o desfazer-lance, o replay da
  partida e a busca da IA sobre posições especulativas.
- **Aleatoriedade explícita.** O gerador (`Rng`) é imutável e serializável, e a semente
  fica no `MatchConfig`. Nada de `Random.Default` escondido: partida salva reproduz igual.
- **Partida = semente + lista de lances** (`MatchRecord`), não um tabuleiro congelado.
  Formato compacto, permite navegar pelo histórico e refazer a partida do começo.

Acrescentar um jogo é implementar `BoardGame`, um `Evaluator`, um `BoardInteractor` e um
`BoardPainter`, e somar uma linha ao `GameCatalog`. Nenhuma tela precisa saber quais jogos
existem.

Jogo que não se joga tocando em casas de uma grade entra com `interactor = null` e uma tela
própria em `app/.../ui/board/surfaces` — é o caminho do dominó, que se joga pela mão, e do
ludo, que se joga pelos peões.

### Jogos prontos

| Jogo | Regras implementadas |
|---|---|
| Jogo da Velha | completo |
| Damas brasileiras | captura obrigatória e máxima, captura para trás, dama voadora, sopro turco, promoção só no fim do lance, empate por 20 lances sem progresso |
| Reversi | viradas nas oito direções, passe automático de quem não tem lance, fim quando nenhum dos dois pode jogar |
| Xadrez | roque (com as três condições), en passant, promoção com escolha da peça, xeque-mate, afogamento, regra dos 50 lances, material insuficiente, repetição tripla |
| Dominó | dominó de bater, mão oculta, abre a maior carroça, compra e passe automáticos, jogo fechado decidido na contagem de pontos |
| Ludo | dado rolado pelo motor, saída só com 6, lance extra no 6, captura, casas seguras, chegada exata |
| Copas | passe de três cartas com rodízio (esquerda, direita, frente, sem passe), abertura obrigatória no 2 de paus, servir o naipe, copas trancada até sair, sem ponto na primeira vaza, correr todas, partida até 100 |
| Canastra | brasileira, de dois a quatro (a quatro em duplas), dois baralhos e quatro curingas, treze cartas, morto único e só até três jogadores, canastra limpa é canastra sem **dois** (o coringa não suja), **três vermelho** (100 parados na mesa, contra sem canastra, reposição automática) e **três preto** (tranca o lixo, só baixa ao bater), bater exige canastra, partida até 3000 |
| Pife | dois baralhos com um curinga cada, nove cartas, compra do monte ou do lixo, bate quem fecha três grupos de três (trinca ou sequência), ás só por cima, monte remontado com o lixo e empate se ninguém fechar |
| Paciência | klondike de uma pessoa só: sete colunas, quatro casas do ás ao rei, compra de uma em uma com o descarte voltando ao monte, coluna vazia só para rei, sequência que anda junto, carta que desvira sozinha e volta da casa para a coluna |
| Truco | **mineiro**: manilhas fixas (4♣ zap, 7♥ copas, A♠ espadilha, 7♦ ourito), baralho de 40, três cartas, melhor de três rodadas com as regras de empate (manda quem fez a primeira; três empates não dão ponto a ninguém), truco/6/9/12 com aceitar, correr e aumentar, de dois ou de quatro em duplas, partida até 12 |

### A IA

Busca alpha-beta com aprofundamento iterativo, compartilhada por todos os jogos
(`core-game/.../ai/Search.kt`). O que limita é o **tempo por lance**, não a profundidade:
a busca devolve o melhor lance da última profundidade concluída, então em aparelho lento a
IA fica mais fraca em vez de travar a tela.

Jogos com troca de peças ganham **busca de quiescência**: ao acabar a profundidade, a busca
segue só pelas capturas até a posição ficar quieta. Sem isso a avaliação acontece no meio de
uma troca e a IA "ganha" uma peça que perde no lance seguinte — no xadrez, é a diferença
entre jogar e entregar peça.

Nos níveis mais baixos ela erra de propósito de vez em quando. Só diminuir a profundidade
não funciona — uma busca rasa continua jogando certinho e ganhando de quem está aprendendo.

### Informação oculta e sorteio

O dominó trouxe o primeiro problema que a busca sozinha não resolve: a mão do adversário não
se conhece. A resposta está em duas peças que valem para qualquer jogo assim:

- **`redactFor(estado, quem)`** apaga do estado o que aquele lado não tem direito de ver. A
  sessão aplica isso **antes de entregar o estado à IA** — nem a máquina joga sabendo o que
  não deveria. As peças continuam contadas, porque saber quantas o outro tem faz parte do
  jogo; só o valor some.
- **`DeterminizedAi`** sorteia mundos possíveis compatíveis com o que se vê, roda a busca em
  cada um e vota no lance que mais vezes saiu melhor.

O ludo tem o problema oposto: informação completa, mas o próximo lance depende do dado.
Quem rola é o motor, não o jogador — quando a vez chega, o valor já está no estado e a
decisão é só qual peão anda. Rolar dado não é decisão, e não vira lance. O gerador mora
dentro do estado, e não num campo estático, porque é isso que faz a partida salva reproduzir
exatamente os mesmos dados ao ser reaberta.

### O app

Cinco telas: início, configuração da partida, tabuleiro, histórico e ajustes. Tema claro e
escuro com paleta própria — e não Material You, porque o tabuleiro precisa de contraste
previsível entre casa clara, casa escura e as duas cores de peça, coisa que herdar as cores
do papel de parede de cada aparelho não garante.

O `GridBoard` é um único composable que serve qualquer jogo de grade: o tamanho vem do
`BoardInteractor`, o desenho das peças vem de um `BoardPainter`, e a tradução de toque em
lance nem passa por aqui. Reversi e Xadrez, na Fase 3, entraram só implementando o painter.

Dominó e ludo não cabem nesse molde, e não foram forçados a caber. Cada um tem sua tela: a
mão deitada, com a linha rolando na horizontal; a cruz de 15 × 15, com o dado ao lado. Mas o
que decide o que aparece continua vindo do `core-game` — `handTiles` diz em que pontas cada
peça encaixa, e `LudoLayout` diz em que casa da cruz cada peão se desenha. **A geometria do
ludo é testada**, não conferida no olho: os testes verificam que a volta de 52 casas fecha
sem buraco, que os dois corredores finais encostam na última casa da volta da sua cor, e que
nenhum peão de uma partida inteira cai fora do desenho.

Quem joga tem nome. Na tela de configuração, cada cadeira de gente ganha um campo — um só
contra o celular, um por cadeira no passa-e-joga —, e as cadeiras da máquina recebem nomes
sorteados, com um botão para sortear outros. O nome digitado fica guardado nas preferências
e volta preenchido na partida seguinte; o sorteio dos adversários mora no `core-game`
(`BotNames`), porque "nomes distintos e estáveis para a mesma semente" é regra, e regra tem
teste. Os nomes viajam na rota da partida e são gravados junto com ela, de modo que retomar
um jogo salvo traz de volta os mesmos adversários. Partida gravada antes disso não tem nome
nenhum, e a tela cai nos rótulos de antes ("Vez das brancas", "Jogador 2") em vez de
aparecer vazia.

Os lances aparecem numa faixa que rola na horizontal e acompanha o último lance sozinha —
vertical competiria com o tabuleiro, que é o que a pessoa precisa ver num celular. A
numeração conta os lances da primeira cadeira em vez de pares: no reversi, quem fica sem
lance perde a vez, e contar de dois em dois desandaria depois do primeiro passe.

As partidas ficam num arquivo JSON no diretório do app, não num banco. Cada uma é uma
semente mais uma lista de lances — algumas centenas de bytes — e nunca serão mais que
algumas dezenas; um banco custaria duas dependências e um processador de anotações para
guardar menos dados do que cabem numa mensagem de texto.

## Como isso é testado

318 testes, mais uma conferência de textos que roda fora do Gradle. Os que realmente
seguram o projeto:

- **`perft` do xadrez contra as cinco posições de referência** do Chess Programming Wiki —
  12 milhões de posições conferidas contra números publicados, cobrindo roque, en passant e
  promoção. Passou de primeira, e é o que sustenta a afirmação de que a geração de lances
  está certa.
- **`perft` das damas contra referência externa.** Desligando as duas regras específicas
  do jogo brasileiro, o gerador vira damas inglesas e tem que reproduzir os números
  publicados de contagem de posições (7, 49, 302, 1469, 7361, 36768). Ele reproduz. Isso
  confere diagonais, bordas, sequências de captura e promoção contra algo que não saiu
  daqui.
- **IA do jogo da velha por exaustão.** Toda linha possível do adversário é jogada contra
  ela, nas duas cadeiras. Ela nunca perde.
- **Replay determinístico.** Uma partida inteira de damas é reproduzida a partir da lista
  de lances e tem que chegar ao mesmo estado, caractere por caractere.
- Posições montadas à mão para cada regra que costuma ser implementada errado — sopro
  turco, promoção no meio da sequência, obrigação de capturar o máximo.
- **Desfazer contra o celular volta dois lances**, não um. Voltar só o último devolveria a
  vez para a IA, que jogaria de novo, e o botão pareceria não ter feito nada.
- **Retomar uma partida salva** chega ao mesmo tabuleiro, caractere por caractere.
- **A IA do dominó não vê a mão do adversário.** O teste confere que o estado que chega até
  ela já veio redigido, e que redigir duas vezes dá no mesmo.
- **A cruz do ludo fecha.** Circuito de 52 casas sem salto, corredores colados na volta,
  currais em cantos opostos, e uma partida inteira sem peão fora do desenho.
- **A descrição falada bate com o tabuleiro.** A contagem de peças sai do estado por um
  caminho e da descrição de acessibilidade por outro, e as duas têm que fechar a cada lance.
- **Todo motivo de recusa vira frase sem estourar.** Modelo com marcador a mais quebraria o
  app exatamente na hora de explicar por que o lance não valeu.
- **`scripts/check_strings.py`, no CI.** Confere que toda chave do motor tem texto em
  português e inglês e que os marcadores batem entre os dois. Chave sem tradução compila,
  instala e só aparece na mão de quem está jogando — por isso é conferido por fora.

## Acessibilidade, som e idioma

Um tabuleiro desenhado num `Canvas` é, para o sistema, um retângulo — um leitor de tela
anunciaria "tabuleiro" e nada mais. Por isso o toque **não sai do desenho**: sai de uma
grade invisível de casas de verdade por cima dele. Com isso o TalkBack percorre o tabuleiro
casa a casa e lê "e4, peão branco"; a linha de estado é região viva, então a troca de vez e
o resultado são anunciados sem ninguém precisar procurar. No ludo, onde mirar num peão de
meio centímetro não é razoável para ninguém, há uma fileira de botões que joga o mesmo lance
por outro caminho.

**O que se fala é decidido no `core-game`**, não na tela: `BoardSpeech` diz que a casa 12 tem
uma dama branca, e a tela só resolve o idioma. É o que permite testar a descrição — defeito
de acessibilidade é o mais fácil de nunca descobrir, porque quem escreve o código não usa
leitor de tela e a tela continua bonita. Foi assim que apareceu um erro real: no reversi quem
abre é o **preto**, e a tela vinha dizendo "vez das brancas" desde a Fase 3.

A tradução segue a mesma divisão. O motor sabe *por que* o lance foi recusado, mas não sabe
em que idioma o aparelho está — então o que atravessa a fronteira é uma chave com
argumentos (`CAPTURE_MANDATORY`, `["2"]`), e o texto vive em `strings.xml`. O português é o
idioma de referência e é **gerado** a partir dos enums do motor; o inglês é escrito à mão e
conferido contra ele. Faltar tradução para uma chave cai no português em vez de virar aviso
vazio: uma frase no idioma errado ainda explica o lance.

Os efeitos sonoros são sintetizados por `scripts/make_sounds.py`, e não baixados de um banco
de efeitos: `.wav` no diff é um blob opaco, e um seno somado a um ruído que decai é do
projeto e não tem termos de licença. O CI roda o script e confere que os arquivos no
repositório são exatamente o que ele produz.

Animação tem uma só, e ela resolve um problema concreto: quando a IA joga, a peça
simplesmente aparece noutro lugar, e o destaque surgindo é o que faz o olho pegar o que
mudou. Som, vibração e animação são desligáveis nos ajustes.

## Sobre a cadeia de ferramentas

Este projeto foi escrito num ambiente sem o SDK do Android e **sem acesso ao Google Maven**
(`dl.google.com` bloqueado por política de rede). O módulo `:app` só encontrou um
compilador quando o fluxo do GitHub Actions entrou no ar — e as versões deste projeto são
o resultado disso, não escolha de gosto:

| | versão | por quê |
|---|---|---|
| AGP | 8.13.2 | o AGP 9 ainda não aceita o plugin `kotlin-android` 2.4.10 |
| Gradle | 8.14.4 | o que o AGP 8.13 pede |
| core-ktx | 1.17.0 | a partir de 1.18 o AndroidX exige AGP 9.1+ |
| lifecycle | 2.9.4 | a partir de 2.10, idem |

O teto do AndroidX está fixado por `resolutionStrategy` em `app/build.gradle.kts`, para
que nenhuma dependência transitiva o ultrapasse e produza um erro que não aponta o culpado.
**Subir qualquer uma dessas versões sem subir o AGP quebra o build.** Quando o
`kotlin-android` passar a suportar o AGP 9, a trava sai e todas sobem juntas.

Um detalhe do `build.gradle.kts` da raiz que parece estranho e não é: o AGP entra pelo
classpath do `buildscript`, não pelo bloco `plugins`. Ele precisa ficar no mesmo
classloader do plugin Kotlin — declará-lo só dentro do `:app` faz o build morrer com
`NoClassDefFoundError` em `BaseExtension`, em qualquer versão. E precisa ser condicional,
para o `:core-game:test` continuar rodando sem SDK; `buildscript` aceita `if`, `plugins`
não.

## Roteiro

- [x] **Fase 1** — motor, IA, Jogo da Velha, Damas
- [x] **Fase 2** — app Android jogável (Compose, contra a IA e passa-e-joga, desfazer, dica)
- [x] **Fase 3** — Reversi e Xadrez
- [x] **Fase 4** — Dominó e Ludo
- [x] **Fase 5** — acabamento (animações, som, acessibilidade, tradução, CI)

O modo online ficou fora por decisão de escopo. O motor já está preparado para recebê-lo
sem reescrita — lances serializáveis, aplicação determinística e semente explícita são
exatamente o que uma sincronização entre aparelhos precisa —, mas nenhuma dependência de
rede entra no projeto por enquanto.

## Requisitos

- JDK 17 ou mais novo
- Gradle vem pelo wrapper (`./gradlew`)
- Fase 2 em diante: Android Studio com o SDK do Android
