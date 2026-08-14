# Publicar o newgame na Google Play

Guia do caminho inteiro: o que falta no projeto, como gerar o pacote assinado e o que a
Play Console pede em cada passo.

> As regras da Play mudam com frequência. Onde este guia dá um número (prazo, quantidade de
> testadores, versão de API exigida), confirme na própria Console antes de contar com ele —
> o texto que aparece lá é sempre a regra em vigor.

---

## 1. O que precisa mudar no projeto antes de publicar

Estes cinco pontos são bloqueios reais. Nenhum deles impede compilar; todos impedem
publicar bem.

### 1.1 O nome do app

Hoje o app se chama **newgame** (`app/src/main/res/values/strings.xml`, chave `app_name`,
e a mesma chave em `values-en/`). É nome de projeto, não de produto: na loja ele aparece
embaixo do ícone e no resultado de busca.

Escolha um nome e troque nos dois idiomas. Até 30 caracteres — é o limite da ficha da loja.

### 1.2 O identificador do pacote

```kotlin
applicationId = "io.github.andre88br.newgame"   // app/build.gradle.kts
```

**Este valor é permanente.** Depois do primeiro envio, mudá-lo significa um app novo, com
outra ficha, outra URL e zero instalações — não há como renomear. Se quiser outro (por
exemplo `com.seudominio.jogos`), mude **agora**, antes do primeiro envio.

O `namespace` logo acima dele é interno e pode ficar como está.

### 1.3 Assinatura de release

O APK que o CI gera hoje é assinado com a chave de depuração, que a Play recusa. É preciso
uma chave sua, guardada fora do repositório.

**Gerar a chave** (uma vez na vida, e guarde a senha):

```bash
keytool -genkeypair -v \
  -keystore ~/newgame-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

**Ligar a chave ao build** — crie `keystore.properties` na raiz (o `.gitignore` do
repositório já ignora esse arquivo, além de `*.jks` e `local.properties`):

```properties
storeFile=/caminho/absoluto/newgame-upload.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

E em `app/build.gradle.kts`:

```kotlin
import java.util.Properties

val keystoreProperties = Properties().apply {
    val arquivo = rootProject.file("keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

android {
    signingConfigs {
        create("release") {
            // Sem o arquivo de chaves, o build de release ainda roda (sai sem assinatura):
            // é o que mantém o CI e a máquina de outra pessoa funcionando.
            if (keystoreProperties.containsKey("storeFile")) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
```

**Perder essa chave é perder o app.** Guarde o `.jks` e as senhas em dois lugares (gerenciador
de senhas + backup offline). Com o *Play App Signing* ligado (obrigatório para app novo), a
Google guarda a chave final de distribuição e a sua serve só para enviar — dá para pedir
reset dela se sumir, mas é burocracia que ninguém quer.

### 1.4 Versão

```kotlin
versionCode = 1        // inteiro, precisa CRESCER a cada envio. Nunca repete.
versionName = "0.2.0"  // o que a pessoa vê. Formato livre.
```

Cada `.aab` enviado precisa de um `versionCode` maior que o anterior. Para publicar, comece
em `versionCode = 1` e `versionName = "1.0.0"`.

### 1.5 Testar o build de release de verdade

O release liga o R8 (`isMinifyEnabled`/`isShrinkResources`), que o debug não usa. O app
grava partidas com `kotlinx.serialization`, que depende de metadados que o R8 pode remover
se as regras não pegarem. **Não publique sem instalar o release no celular e jogar uma
partida, fechar o app e retomá-la** — é exatamente onde esse tipo de erro aparece.

```bash
./gradlew :app:bundleRelease     # gera o .aab para a Play
./gradlew :app:assembleRelease   # gera um .apk de release para instalar e testar à mão
```

Saídas:

```
app/build/outputs/bundle/release/app-release.aab   ← é este que sobe para a Play
app/build/outputs/apk/release/app-release.apk      ← este serve para testar no aparelho
```

A Play **não aceita APK** em app novo: o formato é o Android App Bundle (`.aab`).

---

## 2. O que já está pronto

| Exigência | Situação |
|---|---|
| `targetSdk` recente | 36 — acima do mínimo exigido para app novo |
| `minSdk` | 24 (Android 7) — cobre praticamente todo aparelho em uso |
| Ícone adaptativo | existe (`mipmap/ic_launcher`) |
| Permissões | nenhuma — simplifica muito a declaração de privacidade |
| Rede | nenhuma — o app é offline por inteiro |
| Anúncios / compras | não existem |
| Conteúdo gerado por usuário | não existe |
| Idiomas | português e inglês, com `localeConfig` |
| Acessibilidade | leitor de tela nas telas e nas peças |

Esse conjunto é o cenário mais simples possível de aprovação: sem coleta de dados, sem
login, sem anúncios, sem conteúdo de terceiros.

---

## 3. Conta de desenvolvedor

1. https://play.google.com/console → criar conta.
2. **US$ 25**, pagamento único, vitalício (não é anuidade).
3. Escolha **pessoal** ou **organização**:
   - *Pessoal*: mais simples, mas o **seu nome e endereço** ficam visíveis na ficha do app.
   - *Organização*: exige D-U-N-S Number da empresa; aparece o nome dela.
4. Verificação de identidade (documento) e, na conta pessoal, verificação do endereço. Pode
   levar alguns dias — comece por aqui, é o passo mais lento.

> **Conta pessoal criada recentemente:** a Play exige um **teste fechado com pelo menos 12
> testadores inscritos por 14 dias seguidos** antes de liberar o acesso à produção. Não é
> opcional e não dá para pular; planeje com essas duas semanas em mente e junte os 12
> testadores (amigos, família, colegas — cada um com um endereço de Gmail) antes de começar.
> Confira o número e o prazo na Console, que é onde a regra vigente aparece.

---

## 4. Criar o app na Console

**Todos os apps → Criar app**:

- Nome do app (até 30 caracteres)
- Idioma padrão (português-Brasil)
- **App** ou **Jogo** → *Jogo* (é o caso aqui; muda a categoria e o questionário de classificação)
- **Gratuito** ou **Pago** → gratuito. **Atenção:** gratuito não vira pago depois. O contrário
  (pago → gratuito) é permitido.
- Aceitar as declarações de diretrizes e de leis de exportação

---

## 5. Ficha da loja (Store listing)

| Campo | Limite | Sugestão para este app |
|---|---|---|
| Nome | 30 | o nome escolhido no passo 1.1 |
| Descrição breve | 80 | "Seis jogos de tabuleiro clássicos, offline, contra o celular ou com um amigo." |
| Descrição completa | 4.000 | veja o modelo abaixo |
| Ícone | 512 × 512 PNG, 32 bits, até 1 MB | exportar do ícone adaptativo já existente |
| Gráfico de destaque | 1024 × 500 PNG/JPG | obrigatório |
| Capturas de celular | mínimo 2, máximo 8 | leve 6: uma por jogo |
| Capturas de tablet | opcionais | mandam bem na avaliação de qualidade |
| Vídeo (YouTube) | opcional | dispensável |

Regras das capturas: cada lado entre 320 px e 3.840 px, proporção no máximo 2:1, PNG ou JPG.
Tire-as no emulador ou no celular mesmo, com o app em tela cheia — a Play recusa montagens
que não mostrem o app de verdade.

Modelo de descrição completa:

```
Seis jogos de tabuleiro clássicos num app só, para jogar sozinho contra o celular
ou com outra pessoa no mesmo aparelho.

• Jogo da Velha
• Damas brasileiras — captura obrigatória e máxima, dama voadora
• Reversi
• Xadrez completo — roque, en passant, promoção, xeque-mate e todos os empates
• Dominó de bater — de dois a quatro jogadores
• Ludo — de dois a quatro jogadores

Escolha o nome de quem joga; os adversários do celular vêm com nome próprio.

Três níveis de dificuldade. Desfazer lance e dica a qualquer momento.
Tema claro e escuro. Português e inglês.

100% offline: nenhuma permissão, nenhum anúncio, nenhuma conta, nenhum dado seu
sai do aparelho. A partida continua de onde parou mesmo depois de fechar o app.
```

---

## 6. Conteúdo do app (App content) — a parte que mais reprova

Menu **Política → Conteúdo do app**. Tudo aqui é obrigatório antes de publicar.

### 6.1 Política de privacidade

É um **campo obrigatório**: uma URL pública. Mesmo sem coletar nada, a página precisa
existir e dizer isso. O jeito mais barato é uma página no GitHub Pages deste repositório.

Texto que serve:

```
Política de Privacidade — <nome do app>

Este aplicativo não coleta, não armazena e não transmite nenhum dado pessoal.

Não pede permissões, não acessa a internet, não usa serviços de terceiros, não
exibe anúncios e não usa ferramentas de análise.

As partidas ficam gravadas apenas no diretório privado do aplicativo, no próprio
aparelho, e são apagadas ao desinstalá-lo.

Contato: <seu e-mail>
Atualizada em: <data>
```

### 6.2 Segurança dos dados (Data safety)

Formulário obrigatório. Para este app as respostas são:

- Coleta ou compartilha dados do usuário? **Não**
- Os dados são criptografados em trânsito? **Não se aplica** (não há trânsito)
- O usuário pode pedir exclusão dos dados? **Não se aplica**

Responder "não coleta nada" e depois o app fazer qualquer chamada de rede é motivo de
suspensão — aqui não há esse risco, o app não tem sequer permissão de internet.

### 6.3 Classificação de conteúdo

Questionário IARC. Jogo de tabuleiro sem violência, sem apostas, sem interação entre
usuários, sem conteúdo sexual → sai **Livre / 3+** em todos os territórios.

**Cuidado com uma pergunta:** "o jogo simula jogos de azar?" — dominó e ludo com dado **não**
são jogos de azar; não há aposta, dinheiro nem prêmio. Responda não.

### 6.4 Público-alvo e crianças

Se marcar faixas abaixo de 13 anos, entra a política **Famílias**, que traz exigências
extras. Para simplificar, marque **13+** como público-alvo, a menos que queira mesmo o
programa Famílias.

### 6.5 Demais declarações

- **Anúncios**: não contém
- **Acesso ao app**: todas as funções estão disponíveis sem login
- **App de notícias / financeiro / de saúde / governamental**: não
- **Conformidade com a política de dispositivos**: sim
- **Leis de exportação dos EUA**: aceitar

---

## 7. Caminho até a produção

A Play tem quatro trilhas. Use nesta ordem:

1. **Teste interno** — até 100 pessoas por e-mail, disponível em minutos. É onde se descobre
   que o build de release quebrou.
2. **Teste fechado** — a etapa dos 12 testadores por 14 dias, na conta pessoal nova.
3. **Teste aberto** — opcional; qualquer pessoa entra pelo link.
4. **Produção** — a loja de verdade.

Em cada trilha: **Criar nova versão → enviar o `.aab` → notas da versão → Revisar → Iniciar
lançamento**.

A revisão da primeira publicação costuma levar de alguns dias a duas semanas. Atualizações
seguintes saem bem mais rápido.

Na produção dá para usar **lançamento gradual** (5% → 20% → 50% → 100%): se aparecer travamento,
dá para interromper antes de atingir todo mundo.

---

## 8. Ordem sugerida de trabalho

- [ ] Escolher o nome e trocar `app_name` nos dois idiomas
- [ ] Decidir o `applicationId` definitivo
- [ ] Criar a conta na Play Console e mandar a verificação de identidade (leva dias)
- [ ] Gerar a chave de upload e configurar a assinatura de release
- [ ] `versionCode = 1`, `versionName = "1.0.0"`
- [ ] `./gradlew :app:assembleRelease`, instalar no celular, jogar os seis jogos, fechar e retomar
- [ ] Publicar a política de privacidade (GitHub Pages)
- [ ] Tirar as capturas de tela e montar ícone 512 e gráfico 1024 × 500
- [ ] Criar o app na Console e preencher ficha + conteúdo do app
- [ ] `./gradlew :app:bundleRelease` e subir no teste interno
- [ ] Teste fechado com 12 testadores por 14 dias (conta pessoal nova)
- [ ] Solicitar acesso à produção e lançar

---

## 9. Erros que mais fazem voltar atrás

- **Perder a chave de upload.** Não há como reenviar sem ela sem passar pelo suporte.
- **Publicar o debug.** APK assinado com chave de depuração é recusado na hora.
- **Não testar o release.** O R8 só entra no release; o que quebra por causa dele nunca
  aparece no debug.
- **`versionCode` repetido.** A Console recusa o envio sem explicar muito.
- **Data safety incompatível com o app.** Aqui é fácil acertar: não há coleta.
- **Nome de pacote provisório.** Não tem volta depois do primeiro envio.
- **Capturas de tela que não são do app.** Montagem com texto de propaganda é reprovada.
