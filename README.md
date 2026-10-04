# Dose Certa

> **04/10/2026 — TASKS retomado:** checks locais e evidências anteriores reconciliados. Resultados, limites e gates restantes em [task-completion](docs/qa/task-completion-20261004.md). Não há release assinada ou aprovação Play.

Aplicativo Android nativo para organizar medicamentos, posologias, lembretes e registros de doses. Não substitui orientação profissional; siga a prescrição recebida.

## Estado da v1.0

A interface usa Material 3 nativo, com um destaque compacto de gradiente e percentual circular na Home. As listas agrupam dose, horário, estado e ações para mostrar mais informações de primeira; o Histórico mantém a exportação no cabeçalho, sem sobrepor os registros. [Capturas e validação da UI](docs/qa/ui-polish-20261003.md).

[CONTEXT.md](CONTEXT.md) guarda o estado de retomada, [TASKS.md](TASKS.md) mantém os 121 critérios e [PLAN.md](PLAN.md) registra o plano. Testes e validações locais foram retomados por autorização em 03/10/2026. [Resultados locais dessa sessão](docs/qa/local-validation-20261003.md) são evidência de escopo específico; não equivalem a lançamento aprovado.

Conta Play, assinatura/versionCode e contato/URL pública ainda precisam ser definidos; [gates externos](docs/release/external-gates.md) abertos. O usuário escolheu emuladores; ensaios Xiaomi/Samsung não realizados.

## Funcionalidades presentes no código

- Cadastro/edição com horários, dias da semana, dia mensal, prévia de próximas datas e rascunho restaurável.
- Regras diárias, intervalos por horários explícitos, semanais, mensais, dias específicos e conforme necessário.
- Ocorrências com identidade persistente, snapshots históricos, tomada/pulo/silêncio/snooze distintos e transações idempotentes.
- Home hoje/próximas doses/PRN, busca, arquivamento e exclusão informada de medicamentos/histórico.
- AlarmManager, áudio finito e FSI condicionado a acessos/canais do Android. Sistema/aparelho determinam apresentação; sem promessa de contornar DND ou restrições OEM.
- Histórico/adesão com cálculo comum; PDF por destino SAF escolhido pelo usuário e abertura/compartilhamento com content URI.
- Português/inglês, temas claro/escuro, layouts roláveis e ações explícitas equivalentes aos gestos.

Essa lista descreve implementação. Aceite em runtime fica registrado por tarefa.

## Build

| Configuração | Versão |
| --- | --- |
| Android min / compile / target | 26 / 36 / 36 |
| Gradle / AGP | 8.14 / 8.13.2 |
| Kotlin / KSP | 1.9.20 / 1.9.20-1.0.14 |
| Room / Material | 2.6.1 / 1.11.0 |
| Java | JVM 17; local JDK 21 do Studio; CI JDK 17 |

Abra a raiz no Android Studio, instale SDK Platform 36 e configure o JDK. Nesta máquina:

```bash
env JAVA_HOME=/home/gabirusky/Programs/android-studio/jbr ./gradlew assembleDebug
adb devices
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL shell am start -n com.dosecerta/.ui.MainActivity
```

Substitua SERIAL pelo dispositivo escolhido. Não execute limpeza de dados em AVD pessoal. [BUILD.md](BUILD.md) cobre SDK, Windows, QA e assinatura.

## QA e evidências

Comandos de validação local:

```bash
env JAVA_HOME=/home/gabirusky/Programs/android-studio/jbr ./gradlew assembleDebug lintDebug testDebugUnitTest assembleDebugAndroidTest
bash scripts/qa/device-checks.sh SERIAL
```

Testes JVM em `app/src/test`; instrumentação em `app/src/androidTest`; Room schema 4 em `app/schemas`. Resultados em `app/build/test-results`, `app/build/reports` e `docs/qa/`. Checks locais/CI em `scripts/qa/checks.sh` e `.github/workflows/android-checks.yml`; CI remoto não executado.

[Runbook de dispositivo](docs/qa/device-runbook.md), [gravação](docs/qa/evidence-runbook.md) e [estado do emulador](docs/qa/emulator-validation.md) distinguem ambiente sintético/permissões reais/evidência. O AVD dedicado `DoseCerta_QA`/API 26 executou a validação visual; a matriz completa de APIs e aparelhos continua separada. Imagem 4 KB não comprova runtime 16 KB.

## Dados e privacidade

Banco e preferências ficam no armazenamento privado do Android. Manifesto observado sem INTERNET; não há implementação de upload/analytics. Room/SQLite não é criptografado independentemente: proteção depende do sandbox, criptografia efetiva e bloqueio do aparelho, conforme [ADR](docs/adr/data-protection.md). Backup/transfer excluídos na configuração; validação real pendente. PDFs são cópias no destino do usuário.

A [política em preparação](docs/release/privacy-policy.md) explica retenção/exclusão/permissões/exportação. Contato/URL pública não definidos. Armazenamento local não comprova conformidade legal automática.

## Estrutura

```text
app/src/main/java/com/dosecerta/
  data/          Room, migrações, snapshots e repositório
  domain/        recorrência, estados, ações e adesão
  alarm/         capacidades, agendamento, serviço e card
  notification/  canais, ações, timeout e follow-up
  ui/            setup, Home, cadastro, lista, Histórico/PDF e Settings
app/src/test/          testes JVM
app/src/androidTest/   migrações, escala, UI, PDF, alarmes e jornada
app/schemas/           schema Room exportado
docs/                  requisitos, ADRs, design, QA e release
scripts/qa/            checks, auditoria e gravação
```

## Release e licença

`./gradlew bundleRelease -PreleaseVersionCode=N` prepara bundle sem assinatura quando não há configuração externa de upload. N e certificado devem ser conferidos no Console. Variáveis DOSECERTA_UPLOAD_* fora de Git/logs; nenhum segredo em CLI/docs. [Go/no-go](docs/release/go-no-go.md), [notas v1.0](docs/release/release-notes-v1.0.md) e [hotfix](docs/release/hotfix.md) registram critérios; publicação depende do responsável e das validações.

Licença anunciada: [GNU GPL v3](LICENSE). Autor: **Gabriel Pereira**, [@gabirusky](https://github.com/gabirusky). Inventário transitivo/avisos em `docs/release/`.
