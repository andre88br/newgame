# newgame — jogos de tabuleiro para Android

App Android com vários jogos de tabuleiro, jogáveis contra o aparelho ou entre duas
pessoas no mesmo celular.

> **Status: Fase 1 concluída.** O motor de jogos e a IA estão prontos e testados. A
> interface Android ainda não existe — veja o [roteiro](#roteiro) abaixo.

## Como está organizado

```
core-game/   Kotlin/JVM puro — regras dos jogos, IA e serialização. Sem Android.
app/         (Fase 2) Aplicativo Android com Jetpack Compose.
```

A separação não é enfeite: as regras num módulo sem Android significam que a parte mais
delicada do projeto é testada por `./gradlew :core-game:test`, em segundos, sem emulador e
sem SDK.

## Rodando os testes

```bash
./gradlew :core-game:test
```

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

Acrescentar um jogo é implementar `BoardGame`, escrever um `Evaluator` e adicionar uma
linha em `GameCatalog`. Nenhuma tela precisa saber quais jogos existem.

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

## Como isso é testado

68 testes. Os que realmente seguram o projeto:

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

## Roteiro

- [x] **Fase 1** — motor, IA, Jogo da Velha, Damas
- [ ] **Fase 2** — app Android (Compose, Room, jogar contra a IA e passa-e-joga)
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
