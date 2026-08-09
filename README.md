# newgame — jogos de tabuleiro para Android

App Android com vários jogos de tabuleiro, jogáveis contra o aparelho ou entre duas
pessoas no mesmo celular.

> **Status: Fase 2 concluída.** O app está inteiro: escolher o jogo, jogar contra o
> celular ou passa-e-joga, desfazer, pedir dica, fechar o app e voltar onde parou.
> Veja o [roteiro](#roteiro) e a ressalva sobre [compilação](#sobre-a-compilação-do-módulo-android).

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

### Jogos prontos

| Jogo | Regras implementadas |
|---|---|
| Jogo da Velha | completo |
| Damas brasileiras | captura obrigatória e máxima, captura para trás, dama voadora, sopro turco, promoção só no fim do lance, empate por 20 lances sem progresso |

### A IA

Busca alpha-beta com aprofundamento iterativo, compartilhada por todos os jogos
(`core-game/.../ai/Search.kt`). O que limita é o **tempo por lance**, não a profundidade:
a busca devolve o melhor lance da última profundidade concluída, então em aparelho lento a
IA fica mais fraca em vez de travar a tela.

Nos níveis mais baixos ela erra de propósito de vez em quando. Só diminuir a profundidade
não funciona — uma busca rasa continua jogando certinho e ganhando de quem está aprendendo.

### O app

Cinco telas: início, configuração da partida, tabuleiro, histórico e ajustes. Tema claro e
escuro com paleta própria — e não Material You, porque o tabuleiro precisa de contraste
previsível entre casa clara, casa escura e as duas cores de peça, coisa que herdar as cores
do papel de parede de cada aparelho não garante.

O `GridBoard` é um único composable que serve qualquer jogo de grade: o tamanho vem do
`BoardInteractor`, o desenho das peças vem de um `BoardPainter`, e a tradução de toque em
lance nem passa por aqui. Reversi e Xadrez, na Fase 3, entram só implementando o painter.

As partidas ficam num arquivo JSON no diretório do app, não num banco. Cada uma é uma
semente mais uma lista de lances — algumas centenas de bytes — e nunca serão mais que
algumas dezenas; um banco custaria duas dependências e um processador de anotações para
guardar menos dados do que cabem numa mensagem de texto.

## Como isso é testado

110 testes. Os que realmente seguram o projeto:

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

## Sobre a compilação do módulo Android

Este projeto foi escrito num ambiente sem o SDK do Android e **sem acesso ao Google Maven**
(`dl.google.com` bloqueado por política de rede). Consequência honesta: o módulo `:app`
nunca passou por um compilador antes de chegar aqui.

O que foi feito para reduzir o risco em vez de torcer:

- A lógica que dava para testar foi movida para o `core-game`, e está coberta pelos 110
  testes.
- As fontes do `:app` passaram pelo compilador Kotlin isolado: **nenhum erro de sintaxe**.
  O único arquivo do módulo sem dependência de Android (`MatchStore.kt`) compila limpo.
- Só API estável do Material 3 — nada de `SegmentedButton`, `FilterChip` ou ícones —
  justamente para diminuir a chance de o projeto não abrir de primeira.

O que continua sem verificação: as versões de AGP, Compose BOM, AndroidX e Navigation em
`gradle/libs.versions.toml`, escritas de memória. Se o Gradle Sync reclamar, o próprio erro
traz a versão disponível e a correção é uma linha no catálogo.

## Roteiro

- [x] **Fase 1** — motor, IA, Jogo da Velha, Damas
- [x] **Fase 2** — app Android jogável (Compose, contra a IA e passa-e-joga, desfazer, dica)
- [ ] **Fase 3** — Reversi e Xadrez
- [ ] **Fase 4** — Dominó e Ludo
- [ ] **Fase 5** — acabamento (animações, som, acessibilidade, tradução, CI)

O modo online ficou fora por decisão de escopo. O motor já está preparado para recebê-lo
sem reescrita — lances serializáveis, aplicação determinística e semente explícita são
exatamente o que uma sincronização entre aparelhos precisa —, mas nenhuma dependência de
rede entra no projeto por enquanto.

## Requisitos

- JDK 17 ou mais novo
- Gradle vem pelo wrapper (`./gradlew`)
- Fase 2 em diante: Android Studio com o SDK do Android
