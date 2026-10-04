# Alarmes, permissões e Configurações — implementação e evidência

Estado em 2026-09-30: fontes preparadas para integração; validação final em runtime ainda pendente. Não usar esta página como comprovação de OEM, aparelho bloqueado, revogação ou entrega real antes de anexar os resultados correspondentes. O usuário escolheu usar emuladores por enquanto; os ensaios Xiaomi/Samsung físicos continuam como gates externos.

## Contrato e mudanças

- O log persistido identifica uma ocorrência por `occurrenceId` opaco. AlarmScheduler não concatena IDs; `AlarmIdentity` usa a identidade inteira em URI distinta para recorrência, snooze, timeout, follow-up, card e cada ação. Request code zero é seguro porque data/action/component distinguem os PendingIntents. Cancelamento e criação compartilham mutex; após AlarmManager, o scheduler reconsulta estado/versão e remove handles se houve encerramento/edição concorrente. Métodos de cancelamento de legado procuram os request codes antigos antes de migrar. A fila cancela uma ocorrência sem cancelar a recorrência seguinte.
- `AlarmScheduler.scheduleAlarm` e `scheduleAlarmsForMedication` são suspensos e retornam `ScheduleResult`. O timestamp vem de `repository.previewOccurrences`, que combina slots capturados e calculador comum. O handle persistido declara versão, tipo, horário, prazo e entrega exata/inexata; cancelamento é reconstruível. Revogação entre checagem e AlarmManager é tratada por SecurityException e fallback inexato explicitamente identificado.
- Receiver valida/transiciona a ocorrência com `DoseActionCoordinator.deliver` antes de iniciar serviço/som/card. TAKEN, SKIPPED, MISSED, CANCELLED, posologia obsoleta e entregas repetidas não tocam. A próxima recorrência é agendada como outra ocorrência. Um alarme inexato publica somente notificação quando permitida; não supõe que possa iniciar foreground service em background. Falha de início/foreground gera diagnóstico e fallback possível.
- AlarmService é o único dono de MediaPlayer, e a notificação do alarme é silenciosa. `ReminderChannels.ensure` cria canais somente se ausentes e preserva decisões existentes do usuário. Nenhum canal é apagado ou recriado para forçar volume/importância/DND. O app não promete ignorar DND/volume/limitações OEM.
- A fila mantém IDs e notificações independentes por ocorrência. A notificação de foreground é uma âncora genérica; as notificações de dose são marcadas pela URI completa, com ID inteiro reservado 1 por tag. Encerrar uma dose mantém as demais. Som para em até 60 s por ocorrência; silêncio/fechar/voltar executam DISMISS, sem TAKEN/SKIPPED, e não reabrem o card.
- FSI só é anexado à notificação quando autorizado e o canal/notificações permitem. Não há `startActivity` tardio no serviço. Android decide a apresentação em tela bloqueada/desbloqueada.
- AlarmActivity restaura/recarrega a identidade persistida; `onNewIntent` cancela observer, ação, diálogo e gesto do evento anterior. Tomei/Pular pedem confirmação identificando a medicação. Snooze usa seleção explícita 5/10/15/30/60 min. ACTION_CANCEL do swipe reinicia o gesto; somente ACTION_UP acima do limiar alcança confirmação. performClick e semântica Button oferecem caminho acessível equivalente, além de botões visíveis.
- Dados de medicação na tela bloqueada ficam ocultos por padrão. O card privado oferece silêncio/adiar/desbloquear. Ações de tomada/pulo exigem identidade desbloqueada ou escolha explícita de mostrar os detalhes na tela bloqueada. Receiver verifica keyguard e a preferência para rejeitar ações antigas que tentem contornar a escolha. Notificações privadas têm publicVersion genérica e apenas silêncio/adiar.
- Snooze persiste `snoozedUntil`, preserva `originalDueAt`/identidade/versão e estende o prazo conforme a política comum. Não substitui a próxima recorrência. Timeout só marca MISSED após a tolerância de 30 min e agenda aviso conforme preferência 1–10 h depois do timeout. SKIPPED não recebe convite para reconsiderar; aviso MISSED oferece conferir Histórico, sem registrar uma tomada atrasada automaticamente.
- Reconciliation chama `repository.reconcileBacklog` e restaura pendências futuras/snoozes/follow-ups, cancelando handles inválidos; dose vencida offline não toca em lote. BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET, TIMEZONE_CHANGED e concessão de exact alarm entram pelo mesmo reconciliador. Boot não inicia mediaPlayback.
- Todo goAsync termina em finally, com limite de 9 s. Service scope, callbacks, timer, loads, wake lock e player são cancelados/liberados em stop/destroy. O token do MediaPlayer impede callback de preparação após release ressuscitar o áudio. Diagnóstico registra etapas, hash opaco e classe do erro, sem nomes/doses/exceções com conteúdo sensível, limitado a 100 eventos locais e sem upload.

## Superfícies de permissões e Settings

`ReminderCapabilityChecker` verifica POST_NOTIFICATIONS, estado geral, canais, exact alarms por API e full-screen intent por API. Overlay não é requisito. Onboarding apresenta acessos independentes e explícitos; recusar ou voltar de Settings apenas atualiza estado, sem concessão falsa, navegação automática ou prompts concorrentes. Continuar preserva a escolha de acesso atual e avança ao único tutorial.

Settings reconsulta ao retomar e exibe bloqueio de notificações/canal, entrega inexata e acesso FSI. Recuperação abre o canal correto ou app settings; indisponibilidade OEM tenta app details e informa falha. Alterações de idioma, snooze follow-up e privacidade são enfileiradas em escritas finitas de DataStore que sobrevivem à saída/recriação da view. URI de som indisponível restaura o padrão. Sobre usa BuildConfig para versão e aviso médico RD-002. Layouts são roláveis, ações ≥56dp, texto escalável e superfícies semânticas claro/escuro; swipe usa dimensão sp, sem sobrescrever por altura em px. Textos próprios estão em `values/feature_alarm.xml` e `values-en/feature_alarm.xml`.

## Mapeamento de tarefas

| Tarefa | Mudança / evidência necessária |
|---|---|
| A01 | Diagnóstico local identifica schedule, receiver, service_start/validate, notification_build/publish, audio prepare/playing/stopped. Capturar dumpsys/logcat da entrega real e do Xiaomi antes de concluir reprodução OEM. |
| A02–A04 | Checker, SetupNotificationsFragment, SettingsFragment e ReminderSettingsNavigator; teste de concessão/recusa/revogação/retorno ainda deve ser anexado. |
| A05 | Manifesto pertence ao workstream principal; conferir manifesto mesclado e documentação Play. |
| A06–A09 | Canais estáveis, URI identidade, ScheduleResult, persistência de handle e fallback apenas notificação. |
| A10–A15 | Coordenador comum, validação persistida, notificações por tag, FSI mediado, fila e card restaurável. Home/Histórico chamam cancelOccurrence após commit. |
| A16–A17 | Snooze/timeout/follow-up persistidos, cancelamento por ocorrência e regras de estado. |
| A18–A22 | DISMISS explícito, limite 60 s, acessibilidade/privacidade, reconciliação e lifecycle. |
| A23 | AlarmDiagnostics e entrada Settings, sem nomes/doses, nenhum upload. |
| A24 | `AlarmPipelineInstrumentedTest`, testes de integridade do domínio/Room e ensaios externos/OEM pendentes conforme cobertura abaixo. |
| U13 | Settings redesenhado; gravação serial em DataStore, recovery sound URI, diagnóstico e estado dos lembretes. |
| U15–U16 | Card rolável/adaptável, texto sp, swipe cancellable e botões acessíveis equivalentes. |

## Verificação reproduzível

Compilação local foi usada durante integração; falhas encontradas e corrigidas incluem campos duplicados no card e ID duplicado de diagnóstico Settings. `compileDebugKotlin compileDebugAndroidTestKotlin` passou (BUILD SUCCESSFUL, 47 s). Resultado final de assemble/lint/test pertence ao log de integração principal, não ao estado intermediário.

Testes de instrumentação:

- `pendingIntentUrisKeepAllLongIdsAndKindsDistinct` verifica IDs longos, datas e tipos, sem modificar permissões/dados.
- `actualAlarmDeliveryKeepsConcurrentOccurrencesIndependent` só roda com `-e alarmPipeline 1` e `ro.boot.qemu.avd_name=DoseCerta_QA`. Usa medicamentos sintéticos com UUID e remove somente seus próprios dados; não limpa a base inteira. O teste agenda dois slots reais em AlarmManager, aguarda entrega normal (não envia broadcast), confirma dois logs e notificações privadas, toma um mantendo o outro, verifica parada finita em 60 s, adia preservando identidade/recorrência e restaura por reconciliation. Revogação exact não ocorre dentro da instrumentação: o Android pode matar o processo-alvo. Esse ensaio deve ser dirigido externamente entre execuções e precisa de evidência própria.

Comandos no dispositivo dedicado, após instalar APK/app-test gerados pelo build:

```sh
adb -s emulator-5580 shell am instrument -w -e class com.dosecerta.alarm.AlarmPipelineInstrumentedTest -e alarmPipeline 1 com.dosecerta.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5580 shell dumpsys alarm
adb -s emulator-5580 shell dumpsys notification
adb -s emulator-5580 logcat -d -s DoseCertaAlarm
```

Ainda são necessários: logs do ensaio real, canal bloqueado/POST negado, full-screen concedido/negado, keyguard seguro com confirmação de desbloqueio, cancelamento de gesto, rotação/recriação/onNewIntent com dois cards, timezone/hora/boot/package update em runtime e Xiaomi físico do usuário. Não declarar sucesso por ausência de exceção ou broadcast manual. Volume/DND/OEM exigem observar áudio/dispositivo; emulador não comprova comportamento Xiaomi/Samsung.

## Fontes primárias usadas

- [Android: Schedule alarms](https://developer.android.com/develop/background-work/services/alarms): acesso exact e distinção da exceção de início de foreground service.
- [Android 12 behavior changes](https://developer.android.com/about/versions/12/behavior-changes-12): início de foreground service em background e restrição de notification trampoline.
- [Android 14 behavior changes](https://developer.android.com/about/versions/14/behavior-changes-14): canUseFullScreenIntent e Settings do acesso.
- [AOSP: full-screen intent limits](https://source.android.com/docs/core/permissions/fsi-limits): sistema decide concessão/apresentação, sem garantia universal de full-screen.

## Continuação em 2026-09-30

Guardas de API 26 para showWhenLocked/turnScreenOn corrigidas. FLAG_SECURE incondicional retirado para permitir vídeo sintético de card genérico bloqueado e card identificado após desbloqueio; ocultação de identidade/keyguard e preferência explícita de privacidade permanecem. Criação/cancelamento e snooze agora compartilham mutex; após AlarmManager, o scheduler revalida estado/vigência para remover handles criados durante uma edição ou encerramento concorrente.

Harness principal/negativa/dez disparos implementado em FullJourneyInstrumentedTest e documentado em [evidence-runbook.md](evidence-runbook.md). Os métodos JUnit usam retorno Unit explícito e artefatos separados por run ID. `evidence_record.py` inicia vídeo antes da instrumentação, rastreia somente seu próprio recorder, preserva manifesto em falha e impede reutilização de resultado antigo. Sintaxe Python e planejamento passaram; estas últimas mudanças Android ainda aguardam build/lint/instrumentação. Não usar os APKs anteriores como prova.
