# Compilar e executar o Dose Certa

Este guia mostra como abrir o projeto no Android Studio e como compilar,
instalar e iniciar o aplicativo pelo terminal. O projeto usa Kotlin, Gradle
8.14, Android Gradle Plugin 8.13.2 e Android SDK API 36. O APK de debug roda em
Android 8.0 (API 26) ou superior.

## Pré-requisitos

- Android Studio com Android SDK Platform 36 e Android SDK Build-Tools instalados.
- Um emulador Android configurado no Device Manager ou um celular Android com
  depuração USB ativada.
- Um JDK compatível com o Gradle 8.14. Os comandos Linux abaixo usam o JDK 21
  incluído na instalação local do Android Studio.
- Acesso à internet na primeira compilação para baixar o Gradle e as
  dependências. Depois, elas ficam no cache local.

O repositório inclui `gradlew` (Linux/macOS), `gradlew.bat` (Windows) e o JAR
do Gradle Wrapper. Não é necessário instalar o Gradle separadamente.

Se ainda não tiver o projeto, obtenha-o e entre na pasta:

```bash
git clone https://github.com/gabirusky/DoseCerta.git
cd DoseCerta
```

## Abrir no Android Studio

1. Abra a pasta raiz do projeto pelo menu **File → Open**. Nesta máquina Linux,
   o comando `studio .`, executado dentro da pasta do projeto, faz o mesmo.
2. Aguarde o Gradle Sync. Em **Settings → Languages & Frameworks → Android SDK**,
   instale a plataforma Android 36, o SDK Build-Tools e o Emulator se estiverem
   faltando. Se o Studio pedir a localização do SDK, selecione a pasta onde ele
   foi instalado; nesta máquina é `$HOME/Android/Sdk`.
3. Confira em **Settings → Build, Execution, Deployment → Build Tools → Gradle**
   se o **Gradle JDK** aponta para o JDK incluído no Studio ou outro JDK
   compatível.
4. Abra o **Device Manager**. Crie um dispositivo virtual com uma imagem Android
   se ainda não houver um, então inicie-o. Como alternativa, conecte um celular
   com depuração USB e autorize a conexão na tela do aparelho.
5. Selecione o módulo **app** e o dispositivo na barra superior. Clique em
   **Run** para compilar, instalar e iniciar o aplicativo.

## Linux: compilar pelo Bash

Na raiz do projeto, configure o Java usado pelo Gradle. O caminho abaixo é o
da instalação do Android Studio nesta máquina; ajuste-o se necessário.

```bash
cd /home/gabirusky/Code/DoseCerta
export JAVA_HOME="$HOME/Programs/android-studio/jbr"
export PATH="$HOME/Android/Sdk/platform-tools:$PATH"
./gradlew assembleDebug
```

O APK gerado fica em `app/build/outputs/apk/debug/app-debug.apk`. Para apenas
conferir a versão do Java selecionada, execute `"$JAVA_HOME/bin/java" -version`.
O Java padrão deste terminal é 25; por isso, mantenha `JAVA_HOME` apontando
para o JDK do Studio ao usar o wrapper.

### Configurar o Android SDK

O Android Studio costuma criar `local.properties` automaticamente. Se o
Gradle informar que não encontrou o SDK, crie esse arquivo na raiz do projeto
com o caminho da sua instalação. Exemplo para esta máquina:

```properties
sdk.dir=/home/gabirusky/Android/Sdk
```

Esse caminho é local e deve ser ajustado em outros computadores.

## Linux: iniciar um dispositivo e executar

Para listar os emuladores disponíveis:

```bash
$HOME/Android/Sdk/emulator/emulator -list-avds
```

Nesta máquina, o emulador configurado se chama `Medium_Phone_API_36.1`.
Inicie-o em outro terminal e aguarde a tela inicial do Android:

```bash
$HOME/Android/Sdk/emulator/emulator -avd Medium_Phone_API_36.1
```

Como alternativa, conecte um celular com depuração USB ativada. No terminal
do projeto, verifique se o dispositivo aparece, instale o APK e abra a
atividade principal:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.dosecerta/.ui.MainActivity
```

O `-r` reinstala o aplicativo preservando seus dados locais. Se houver mais
de um dispositivo na saída de `adb devices`, adicione `-s SERIAL` a cada
comando `adb`, usando o número exibido na primeira coluna.

Para compilar e instalar em um único passo, com um dispositivo conectado,
também é possível usar `./gradlew installDebug`. Depois, execute o comando
`adb shell am start` acima.

## Windows

Abra a pasta no Android Studio, configure o SDK e um emulador ou celular como
descrito acima. No PowerShell, na raiz do projeto, selecione o JDK do Studio
(ajuste o caminho para sua instalação) e execute:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:Path"
.\gradlew.bat assembleDebug
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.dosecerta/.ui.MainActivity
```

Se o SDK não for localizado automaticamente, configure `local.properties` com
o caminho do SDK do seu usuário, por exemplo:

```properties
sdk.dir=C\:\\Users\\SEU_USUARIO\\AppData\\Local\\Android\\Sdk
```

## Problemas comuns

| Mensagem ou sintoma | O que verificar |
| --- | --- |
| `SDK location not found` | Confira o caminho `sdk.dir` em `local.properties` e a instalação da plataforma Android 36. |
| `JAVA_HOME is set to an invalid directory` | Ajuste `JAVA_HOME` para a pasta do JDK, sem `/bin` no final. |
| Erro de versão do Java ou do Gradle | Use o JDK configurado no Android Studio; nesta máquina, `~/Programs/android-studio/jbr`. |
| `adb devices` mostra `unauthorized` | Desbloqueie o celular e aceite a autorização de depuração USB. |
| `adb devices` não mostra nenhum dispositivo | Inicie o emulador ou confira o cabo USB e a depuração USB do celular. |
| `more than one device/emulator` | Escolha um dispositivo com `adb -s SERIAL ...`. |
| O primeiro build falha ao baixar dependências | Verifique a conexão com a internet e repita `./gradlew assembleDebug` (ou `gradlew.bat` no Windows). |

Para ver detalhes de uma falha de compilação, execute
`./gradlew assembleDebug --stacktrace` no Linux ou
`.\gradlew.bat assembleDebug --stacktrace` no Windows.

## QA e release da v1.0

`bash scripts/qa/checks.sh` falha se assemble/lint/unit tests falharem. Configuração CI em `.github/workflows/android-checks.yml`; nenhum segredo de assinatura usado por checks. Resultados em `docs/qa/checks`, `app/build/reports`, `app/build/test-results`. Instrumentação requer serial explícito: `bash scripts/qa/device-checks.sh SERIAL`. O [runbook](docs/qa/device-runbook.md) distingue AVD sintético de aparelho pessoal.

`./gradlew bundleRelease -PreleaseVersionCode=N` gera bundle unsigned sem variáveis de upload. Para assinatura, fornecer externamente DOSECERTA_UPLOAD_KEYSTORE, DOSECERTA_UPLOAD_STORE_PASSWORD, DOSECERTA_UPLOAD_KEY_ALIAS, DOSECERTA_UPLOAD_KEY_PASSWORD. Não colocar valores em CLI, docs ou Git. N deve exceder o versionCode do Console; ainda não há evidência desse valor ou certificado. Bundle unsigned não é candidato para publicar.

compile/target36, min26, Room schema4 em `app/schemas`. Migrações preservadoras precisam passar instrumentação; compilar não comprova migração. Inventário `.so` real: `python3 scripts/qa/audit_artifact.py ARTEFATO`. O teste16KB exige PAGE_SIZE16384; imagem4096 não substitui esse teste.


### Evidência atual e suites selecionadas

Resultados de 04/10 em [task-completion](docs/qa/task-completion-20261004.md). O harness usa AndroidX JUnit 1.3.0 e Espresso 3.7.0, necessário para evitar reflexão removida de InputManager no Android16. Isso não altera dependências de produção.

Após `assembleDebug assembleDebugAndroidTest`, execute no AVD sintético `DoseCerta_QA` já bootado:

```bash
python3 scripts/qa/instrument.py --serial emulator-5580 --suite data
python3 scripts/qa/instrument.py --serial emulator-5580 --suite pdf
python3 scripts/qa/instrument.py --serial emulator-5580 --suite ui
python3 scripts/qa/instrument.py --serial emulator-5580 --suite system
python3 scripts/qa/instrument.py --serial emulator-5580 --suite alarm
```

Cada execução guarda hash, API, fuso, resultado e diagnósticos em `docs/qa/runs`. Timeout, skip ou contagem inesperada reprova a suíte. A jornada gravada usa o [runbook](docs/qa/evidence-runbook.md). Nunca limpar um AVD existente para satisfazer o primeiro uso; preparar profile sintético separado quando necessário.
