# TREINE AI — aplicativo Android nativo

App de treino com análise de execução por visão computacional.
**Sem WebView e sem HTML**: toda a interface é Jetpack Compose nativo, e todo o
processamento de imagem acontece no aparelho.

---

## Como gerar o APK

### Caminho recomendado: build automático no GitHub (não precisa instalar nada)

1. Crie um repositório no GitHub e envie esta pasta para ele.
2. Abra a aba **Actions** do repositório. O build começa sozinho no primeiro envio.
3. Quando terminar (5 a 8 minutos), clique na execução e baixe o artefato
   **treine-ai-apk**. Dentro dele está o `.apk`.
4. Transfira para o celular e instale (é preciso permitir "instalar de fontes
   desconhecidas" para o app usado na transferência).

O fluxo está em `.github/workflows/android.yml`. Ele baixa o modelo de pose,
compila em modo release, assina e publica o APK.

### Assinatura própria (necessária só para publicar na Play Store)

Sem configurar nada, o APK sai assinado com a chave de depuração: instala e roda
normalmente, mas não serve para a loja. Para assinar com a sua chave, gere um
keystore e cadastre quatro segredos no repositório
(Settings → Secrets and variables → Actions):

| Segredo | Conteúdo |
|---|---|
| `KEYSTORE_BASE64` | o arquivo `.jks` convertido em base64 |
| `KEYSTORE_PASSWORD` | senha do keystore |
| `KEY_ALIAS` | nome da chave |
| `KEY_PASSWORD` | senha da chave |

```bash
keytool -genkey -v -keystore treineai.jks -keyalg RSA -keysize 2048 \
  -validity 10000 -alias treineai
base64 -w0 treineai.jks > treineai.jks.base64   # macOS: base64 -i treineai.jks
```

### Build na sua máquina (precisa do Android Studio)

```bash
./tools/fetch-model.sh          # baixa o modelo de pose (~3 MB)
./gradlew assembleRelease       # ou abra a pasta no Android Studio
```

O APK sai em `app/build/outputs/apk/release/`.

---

## Como o projeto está organizado

```
app/src/main/java/com/treineai/app/
  MainActivity.kt          única Activity; nenhuma WebView
  data/
    Models.kt              catálogo, perfil, treinos, assinatura
    Store.kt               persistência local, autenticação, XP, conquistas
  motion/
    Kinematics.kt          modelo de corpo: 28 padrões de movimento
    Analysis.kt            métricas articulares, posicionamento, 18 regras
    Analyzer.kt            máquina de estados da repetição e do score
    PoseProvider.kt        MediaPipe real + provider simulado
    CameraSource.kt        CameraX entregando quadros
    MotionAnalysisService.kt   camada de abstração
  voice/
    Feedback.kt            fila de voz com prioridade, vibração e som
    Commands.kt            tabela de comandos falados
    VoiceCommands.kt       escuta contínua
  ui/
    Theme.kt Components.kt Icons.kt PoseView.kt Nav.kt
    AppState.kt App.kt CameraPane.kt
    screens/               as 22 telas
  assets/catalog.json      os 50 exercícios, treinos e conquistas
```

### A camada de abstração

```
Interface  →  MotionAnalysisService  →  PoseProvider
```

As telas nunca falam com a câmera nem com o MediaPipe. Trocar a engine de visão
computacional não muda uma linha de interface. Há dois providers:

- **MediaPipeProvider** — pose real, 33 pontos, na GPU (com queda para CPU).
- **SimulationProvider** — atleta sintético, para demonstrar tudo sem câmera.

Se o modelo não abrir, o app **não quebra**: cai para a simulação e avisa na tela
com o selo "MODO DEMO".

---

## Paridade com a versão web

A camada de análise é a mesma, número a número. `parity/run.sh` roda as duas
implementações lado a lado e compara os resultados:

| Etapa | Cobertura |
|---|---|
| 1 · Cinemática e métricas | 50 exercícios × 21 fases = 1.050 amostras |
| 2 · Posicionamento e motor de repetição | 750 verificações + 50 sessões completas |
| 3 · Regras de execução | as 18 regras, quadro a quadro |

```bash
KOTLINC=/caminho/para/kotlinc ./parity/run.sh
```

`tools/check.py` faz uma conferência estática do projeto (ícones inexistentes,
parâmetros errados, linguagem proibida) sem precisar do SDK do Android.

---

## Marca

Cor principal **#F73D14**, secundária **#E3E1DC**. A logo oficial está em
`app/src/main/res/drawable-nodpi/ta_logo.png` e nos ícones do lançador, na
proporção original. **Não redesenhe nem substitua.**

As fontes (Archivo e IBM Plex) vão empacotadas no APK: nada é baixado em
tempo de execução, e o app funciona sem internet.

---

## Privacidade

Todo o processamento de imagem acontece no aparelho. Nenhum quadro de vídeo sai
do celular. Os dados do usuário ficam num único arquivo JSON na área privada do
app, e a tela de privacidade permite exportar ou apagar tudo.

O app não é autoridade médica e não usa esse tipo de linguagem: ele diz "revise
sua execução", nunca "seguro" ou "perigoso".
