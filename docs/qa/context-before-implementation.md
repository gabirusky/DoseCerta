# DoseCerta — contexto para execução da v1.0

Atualizado em **2026-09-29**. Ler junto de [PLAN.md](PLAN.md), [TASKS.md](TASKS.md) e requisitos em `docs/` antes de implementar.

Este contexto substitui orientações antigas que tratavam detalhes de implementação como garantias. O trabalho desta etapa foi auditoria estática e planejamento. Não houve correção do código do app, execução de testes do candidato ou gravação do fluxo. Distinga fatos do repositório, hipóteses sobre o aparelho e decisões propostas.

## 1. Produto e documentos de referência

DoseCerta é um app Android nativo para cadastrar medicamentos e posologia, apresentar alarmes, registrar tomadas/pulos/doses avulsas e consultar histórico. Público principal brasileiro, inclusive idosos, pacientes com múltiplos medicamentos e cuidadores. Produto gratuito, offline, sem backend/analytics/anúncios no escopo da v1.0. pt-BR é obrigatório; inglês já existe e deve continuar funcional.

| Documento existente | Como usar |
|---|---|
| `docs/REQUISITOS - DOSE CERTA.md` | Fonte de RF-001–007, RNF e RD-001–003; contém semanal/mensal/dias específicos, Android 8, resposta <2 s e aviso no primeiro uso/Sobre |
| `docs/JUSTIFICATIVA - DOSE CERTA.md` | Público, gratuidade, controle/privacidade e proteção de dados; sintomas/informações gerais identificados como futuro |
| `docs/RELATORIO Dose Certa v0.5.0.md` | Histórico de intenção técnica, datado 12/12/2025; algumas alegações não correspondem ao código atual ou às regras atuais |
| Relatório de curricularização `.docx` em `docs/` | Texto extraído localmente para análise; cita capturas/vídeo antigos. Evidências embutidas não foram validadas nesta etapa |
| `README.md` / `BUILD.md` | Funcionalidades anunciadas e execução; conferir versões no Gradle antes de repetir instruções |

O relatório antigo registra mudança para intervalos; não existe revogação formal de RF-002 semanal/mensal/dias específicos. Plano contempla ambas as famílias. Não omitir requisitos por já haver outra enumeração no código. Histórico em lista satisfaz RF-005 (lista ou calendário); calendário é opcional.

Aviso de responsabilidade deve existir no primeiro uso **e** em Sobre (RD-002). Não usar estatísticas médicas da justificativa em texto de loja sem verificar fonte e adequação. Não transformar lembrete em recomendação de prescrição, compensação de dose ou prova de adesão clínica.

## 2. Repositório, convenções e ambiente

Há orientação ancestral em `/home/gabirusky/Code/AGENTS.md`, voltada a análise arquitetural. Nesta etapa, a instrução explícita do usuário autoriza os três documentos. Respeitar convenções aplicáveis durante implementação e escopo efetivamente autorizado.

O workspace já tinha modificações em README/BUILD/Gradle e arquivos do wrapper não rastreados. `docs/` foi adicionado pelo usuário durante o planejamento. Não executar reset/clean, substituir mudanças alheias ou atribuir seu build ao agente. `PLAN.md`/`TASKS.md` estavam ignorados no `.gitignore`; as entradas foram removidas para que os documentos possam ser versionados. Nenhum commit foi criado nesta etapa.

| Item | Estado auditado |
|---|---|
| Namespace/applicationId | `com.dosecerta` |
| Linguagem/UI | Kotlin, XML, ViewBinding, Material Components 1.11.0 |
| Arquitetura | MVVM, Repository/DAO, Flow/StateFlow, DI manual |
| SDK | min 26, compile 34/target 34; objetivo de submissão atual API 36 |
| Versão declarada | versionCode 1 / versionName 1.0; não significa release validada |
| Toolchain | AGP 8.13.2 no root Gradle, wrapper 8.14, Kotlin 1.9.20, KSP 1.9.20-1.0.14, bytecode JVM 17 |
| Persistência | Room 2.6.1, schema v3, DataStore Preferences 1.0.0 |
| Outras dependências | Navigation 2.7.6, Lifecycle 2.7.0, Coroutines 1.7.3, WorkManager 2.9.0 |
| Testes declarados | JUnit4, AndroidX/Espresso, Room testing; nenhuma suíte de source encontrada no inventário inicial |
| SDK local | `/home/gabirusky/Android/Sdk`, plataformas34/35/36/36.1 instaladas no inventário |
| JDK local | `/home/gabirusky/Programs/android-studio/jbr` (JDK 21 observado); conferir antes de usar |
| AVD existente | `Medium_Phone_API_36.1`; criar AVD QA próprio antes de limpar dados |

Android SDK/adb/emulator podem não estar no PATH. Preferir variável como `DOSECERTA_SDK` ou caminhos absolutos; não sobrescrever HOME/CODEX_HOME. `local.properties` e assinaturas são locais; não versionar segredos. Um APK debug já existia no diretório de build; sua presença não comprova que este agente compilou ou testou o app.

O sandbox falhou no início com `mountinfo path is not absolute`, exigindo execução aprovada fora dele. O ambiente depois voltou a aceitar execução normal. Isso é falha da ferramenta, não do projeto. Não contornar rejeição de aprovação; registrar bloqueio real e continuar trabalho permitido.

## 3. Arquitetura e caminhos críticos atuais

```text
app/src/main/java/com/dosecerta/
  DoseCertaApplication.kt
  data/local/DoseCertaDatabase.kt
  data/local/entity/{Medication,Schedule,MedicationLog}.kt
  data/local/dao/{MedicationDao,ScheduleDao,MedicationLogDao}.kt
  data/model/{Enums,Models,MedicationLogWithDetails}.kt
  data/repository/MedicationRepository.kt
  alarm/{AlarmScheduler,MedicationAlarmReceiver,BootCompletedReceiver,
         AlarmService,AlarmSoundManager,AlarmActivity,SwipeToConfirmView}.kt
  notification/{NotificationHelper,NotificationActionReceiver,
                MarkMissedReceiver,MissedReminderReceiver}.kt
  ui/MainActivity.kt
  ui/{setup,home,medications,addmedication,history,settings,privacy}/
  ui/history/PdfReportGenerator.kt
  util/{SettingsPreferences,Constants,DateTimeUtils,SampleDataProvider}.kt
```

Fluxo de UI: Fragment → ViewModelFactory → ViewModel → MedicationRepository → DAO → Room → Flow → StateFlow → UI. Factories são manuais; não introduzir Hilt/Dagger por hábito. A liberação do binding segue `onDestroyView`; coletar dados com lifecycle adequado, preferencialmente `repeatOnLifecycle`, para não atualizar view destruída.

Fluxo de alarme: `AlarmScheduler` → AlarmManager/PendingIntent → `MedicationAlarmReceiver` → `AlarmService` → notificação/FSI → `AlarmActivity`. Ações também vêm da Home e `NotificationActionReceiver`. Boot e `DoseCertaApplication.onCreate` reagendam horários ativos; esse caminho atual não reconcilia todos os eventos duráveis.

Primeiro uso: MainActivity consulta `hasAcceptedTerms`; redireciona para SetupActivity quando falso. Após termos/permissões, SetupNotifications segue direto para MainActivity. Tutorial ativo é overlay na MainActivity; SetupTutorialFragment continua registrado no grafo legado. Não redesenhar apenas o fragment legado e assumir que usuário verá a alteração.

Navegação: `res/navigation/nav_graph.xml`, `nav_graph_setup.xml` e `res/menu/bottom_nav_menu.xml`. Principais destinos: Home, Medicamentos, Histórico, Configurações; Add/Edit e Privacidade são destinos adicionais. Incluir diálogos e superfícies do sistema no inventário de jornada.

## 4. Modelos, dados e semântica

| Entidade atual | Campos e cuidados |
|---|---|
| Medication | ID Long auto, nome/dose/unidade/forma/frequência/notas/cor/isActive/createdAt; `@Parcelize` usado em extras |
| Schedule | ID Long, medicationId, timeInMinutes 0–1439, daysOfWeek 1=domingo…7=sábado, isActive; dias vazios significam todo dia |
| MedicationLog | ID Long, medicationId/scheduleId nullable, scheduledTime, actualTime nullable, status, notes, isExtraDose, customMedicationName |

Room atual é **v3**, não v1. `exportSchema=false` e `fallbackToDestructiveMigration()` devem ser tratados antes de ampliar o modelo. Schema compilado não comprova migração nem retenção. Logs têm CASCADE do medicamento **e** horário, índices sem unicidade de ocorrência. `@Insert(REPLACE)` não torna query-then-insert atômico nem deduplica uma chave lógica não indexada.

Existem **duas classes MedicationLogWithDetails**: a usada nas queries está em `data/local/dao/MedicationLogDao.kt`; há outra em `data/model/MedicationLogWithDetails.kt`. Conferir imports; não criar terceira classe equivalente sem contrato explícito. Query atual faz LEFT JOIN com medicamento atual, incluindo avulsos sem medicamento, mas reescreve metadados históricos se nome/dose atual mudar.

Regras alvo no PLAN:

- Identidade persistente por ocorrência e horário original imutável; snooze é próximo alerta da mesma dose.
- Snapshot de nome/dose/unidade/posologia; histórico de versões para reconciliação. Dados antigos reconstruídos precisam origem distinguível.
- Resultado único/transacional; comandos duplicados idempotentes; comandos atrasados não substituem conclusão.
- TAKEN tem horário efetivo de tomada; SKIPPED/MISSED não têm horário de tomada. Dismiss/silenciar não confirmam tratamento.
- Proposta de tolerância 30 min e som 60 s é decisão UX revisável, não regra médica; persistir prazo, estender após snooze aceito.
- Dose pulada não deve receber mensagem automática incentivando reconsideração/tomada. Relembrete de não confirmação não recomenda compensação.
- Arquivar/remove da rotina preserva histórico. Exclusão permanente é explícita, com escolha sobre histórico. Reconciliação respeita exclusão manual.
- Adesão usa tomadas programadas/ocorrências programadas encerradas; PRN/avulsas/pendentes fora do denominador; vazio é Sem dados.

Frequências atuais: DAILY e intervalos 4/6/8/12 h geram 1/6/4/3/2 horários; AS_NEEDED tem intervalo 0 e nenhum horário. O save de AS_NEEDED já aceita lista vazia; não repetir a correção antiga como se faltasse. Conversão atual remove horários e pode apagar logs por CASCADE. Semanal/mensal/dias escolhidos exigem implementação adicional.

Calendário mensal não é intervalo 30 dias. Dia 29/30/31 ausente exige regra explícita mostrada ao usuário. Tempo local versus duração decorrida, DST, viagens, meia-noite, clock jump e dias de semana precisam testes com relógio/fuso injetáveis. Não usar timestamp “hoje” para recuperar ocorrência de ontem.

## 5. Achados de alarmes e permissões com evidência

Referências relativas a `app/src/main/java/com/dosecerta/`; linhas são as do snapshot auditado e podem mudar.

| Achado confirmado | Referência |
|---|---|
| Serviço cria MISSED assim que dispara | `alarm/AlarmService.kt:249`; Home também deriva MISSED ao passar horário, `ui/home/HomeViewModel.kt:56` |
| Verificador 30 min definido, sem chamadas no inventário | `alarm/AlarmScheduler.kt:248` (`scheduleMissedCheckAlarm`) |
| Snooze e recorrência têm mesma identidade de PendingIntent | `alarm/AlarmScheduler.kt:32,160,238`; próxima recorrência agendada em `AlarmService.kt:274` |
| Foreground ID 1001 e parada global misturam doses | `alarm/AlarmService.kt:39,110,239,546`; receiver para serviço antes da ação |
| Activity singleTop sem onNewIntent | Manifest Activity do alarme; extraído apenas em `AlarmActivity.onCreate` |
| Dismiss do relembrete cancela ID diferente e para outro alarme | `notification/NotificationHelper.kt:135,206,259`; `NotificationActionReceiver.kt:44–51,96` |
| Criação/ação via múltiplas leituras+inserts sem transação comum | `AlarmService.kt:249`, `AlarmActivity.kt:315`, `NotificationActionReceiver.kt:128`, `HomeViewModel.kt:118` |
| Falha de persistência ainda fecha card | `alarm/AlarmActivity.kt:325–327,358–360,377–379` |
| Gesture ACTION_CANCEL confirma snooze | `alarm/AlarmActivity.kt:237–242` |
| Callbacks/scope não cancelados podem republicar após stop | `alarm/AlarmService.kt:213,233,240,303,337` |
| Edição altera scheduledTime de log do dia | `ui/addmedication/AddMedicationViewModel.kt:228–234` |

Validar estado ativo/terminal antes do som/card. Confirmação na Home/Histórico deve encerrar o alerta correspondente. Serviço deve possuir scopes/timers canceláveis e parar callbacks em destroy/stop; IO tardio não pode reabrir card. Conservar outras ocorrências ativas na fila.

### Inventário de acessos

Manifesto declara POST_NOTIFICATIONS, VIBRATE, SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, WRITE_EXTERNAL_STORAGE(max28), FOREGROUND_SERVICE, FOREGROUND_SERVICE_MEDIA_PLAYBACK, USE_FULL_SCREEN_INTENT, WAKE_LOCK, SYSTEM_ALERT_WINDOW e REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.

POST solicitado em setup e MainActivity. Exact verificado no scheduler/MainActivity, com fallback inexato. FSI verificado só no setup; retorno de Settings não valida concessão. Serviço monta FSI indiscriminadamente, tenta startActivity com delay e apaga/recria canal. Overlay e pedido direto de isenção de bateria não têm uso correspondente encontrado. DND policy access não está implementado: não adicionar permissão por reflexo.

Canais atuais: `medication_reminders` e `medication_alarm_channel`. Importância gravada e escolha do usuário importam; alterar constante não modifica automaticamente canal existente. Não apagar/recriar para forçar configuração. FSI, exact scheduling, notificações e canal habilitado são gates separados.

Identidades legacy a migrar/cancelar deliberadamente:

| Uso | Fórmula atual |
|---|---|
| Main/snooze | `(medicationId * 1000 + scheduleId).toInt()` |
| Missed check | `(medicationId * 1000000 + scheduleId * 1000 + 999).toInt()` |
| Missed reminder alarm | `(medicationId * 1000000 + scheduleId * 1000 + 998).toInt()` |
| Missed reminder notification | `(medicationId * 1000000 + scheduleId * 1000 + 997).toInt()` |
| Actions | notificationId multiplicado por 10 com sufixo da ação |
| Foreground | ID fixo 1001 |

Essas fórmulas não são invariantes desejáveis: podem colidir/truncar Long→Int e não incluem ocorrência. Mudar sem cancelar/migrar mantém alarmes antigos vivos. Usar identidade explícita de Intent/action/data e ID persistido coerente com cancelamento.

### Hipóteses que exigem aparelho

Canal bloqueado/baixo, POST negado, FSI negado, background activity launch, timing da primeira notificação sem FSI, FGS recusado, Doze/OEM e estado da tela são hipóteses separadas. Duas permissões exact no manifesto não comprovam conflito. Capturar causa real, incluindo `SecurityException` e `ForegroundServiceStartNotAllowedException`.

Exact alarm pode permitir início de FGS em background em situações documentadas; **fallback inexato não herda automaticamente essa exceção**. Se FGS for recusado, degradar para notificação permitida e mostrar saúde do lembrete; não reportar toque/tela cheia como sucesso.

## 6. Tela bloqueada e interação do paciente

A presença de FSI e showWhenLocked não autoriza contornar PIN/biometria. Em dispositivo desbloqueado, heads-up pode ser comportamento normal; em bloqueado/apagado, testar FSI concedido. Se POST/canal também bloqueados, nem heads-up é garantido: mostrar estado degradado dentro do app.

Proposta de privacidade: ocultar nome/dose por padrão em notificação **e Activity**. Card genérico pode oferecer Silenciar/Adiar; “Tomei/Pular” exige identificar a ocorrência por desbloqueio ou escolha explícita de mostrar detalhes bloqueados. Ações que abrem histórico/configurações exigem desbloqueio. Não registrar tomada de um medicamento cuja identidade o usuário não pôde confirmar, principalmente com alarmes simultâneos.

Não afirmar que ongoing é irremovível em qualquer Android/OEM. Oferecer ação explícita de silêncio porque swipe pode estar limitado no bloqueio. Não inferir TAKEN/SKIPPED de deleteIntent, back, fechamento ou tela apagada. Gesto cancelado não comita; leitor de tela precisa alternativa a arraste/hold/timeout. Não aprisionar o usuário numa Activity.

## 7. PDF e histórico: pontos de atenção

`ui/history/PdfReportGenerator.kt` usa PdfDocument nativo e mistura layout/storage/Intent. `HistoryViewModel.exportPdf` lê `logs.value` (status filtrado) em coroutine IO; o cabeçalho recebe somente período. Histórico alterna 7/30/todo período; null significa todo período. O filtro todo período e PDF já têm código; sua existência não comprova correção.

| Defeito estático | Referência |
|---|---|
| Paginação de dia inteiro não divide dia denso | `PdfReportGenerator.kt:107–117` |
| Resumo encerra sem continuar medicamentos | `PdfReportGenerator.kt:191–199` |
| Texto sem wrapping/altura medida | `PdfReportGenerator.kt:194–197,262–282,306` |
| Forma/frequência retornam vazio | `PdfReportGenerator.kt:397,402` |
| Adesão baseada em lista filtrada/avulsas/vazio 100% | `PdfReportGenerator.kt:143–147`; `HistoryViewModel.kt:140` |
| API 26–28 salva file URI; erro de viewer engolido | `PdfReportGenerator.kt:426–438`; `HistoryFragment.kt:232` |
| document fecha só após sucesso; MediaStore!!/pending sem cleanup | `PdfReportGenerator.kt:126–127,412–423` |

Usar snapshot consultado a partir de request capturado, parâmetros de período explícitos, fuso/idioma, resumo completo e recorte indicado. Separar gerador, renderizador e destino. SAF `CreateDocument` em API 26+ elimina permissão storage ampla; cancelamento normal, cleanup e URI content obrigatórios. FileProvider de cache deve ter paths restritos e grants temporários; não abrir file URI fora do app.

Paginar resumo e linhas de dias, quebrar texto, numerar todas as páginas, preservar todos os registros e status. Locale real aplica-se ao PDF. “Medicamentos ativos” não pode ser inferido da presença de logs. Relatório contém registros informados pelo usuário e não possui validação médica automática.

Matriz mínima: vazio/um/muitos, 30+ doses no dia, 50 medicamentos, 10.000 registros, texto extenso/acentos, avulsa/PRN, alteração/arquivo/exclusão, filtro recente, limites de datas, cancelamento, sem leitor/sem espaço/provider falhando. Verificar conteúdo extraído, renderização e abertura real.

## 8. UI, localização e preferências

Todas as páginas e diálogos estão no inventário do PLAN. Não tratar card do alarme ou primeira página de onboarding como único redesenho. Capturar matriz de tamanhos/fontes/temas/idiomas e estados antes/depois.

Achados estáticos úteis:

- Add/Edit: seis cores 48 dp + gaps + padding exigem 396 dp (`fragment_add_medication.xml:181–258`); implementar wrap/scroll/adaptação. Três colunas dose/unidade/forma frágeis; título central pode sobrepor ações.
- Seção solicitada chama-se atualmente **Lembretes** (`add_med_reminders`, PTstrings:53; layout:273). Trocar a seção para **Posologia**, explicar Como e quando tomar. Não substituir todas as ocorrências de “alarme” no app.
- `SwipeToConfirmView` é touch-only; em `:91` substitui texto escalado por `height*0.30` em px. Corrigir escala e semântica/acessibilidade.
- Histórico: texto branco 12 sp em verde/vermelho/laranja tem contrastes calculados 2,26/4,17/2,63:1; revisar cores/estilos. Editar log só por long press é pouco descobrível.
- Tutorial ativo e legado duplicados; alguns botões 40/44 dp. Permissões e overlay podem competir no primeiro uso.
- Settings: save do slider aguarda 4 s e cancela no destroy; usuário pode perder preferência ao navegar. Persistir na conclusão de interação.
- Código/UI ainda têm PT hardcoded e 36 chaves PT ausentes no EN no inventário. Pharmaceutical forms às vezes exibem enum name. Corrigir formatadores e recursos.
- `home_button_snooze` é ID legado de botão que exibe Pular/Skip e executa skip. Não mudar comportamento pelo nome do ID.

Recursos default `res/values/strings.xml` são pt-BR; `values-en/strings.xml` é inglês. Adicionar strings novas a ambos. Fallback é mecanismo de recursos Android, não Room. Datas e saudações também precisam locale; não basta traduzir XML.

SettingsPreferences usa DataStore `settings`, chaves language, missed_reminder_hours, alarm_sound_uri, setup_completed e terms_accepted. Relembrete configurável 1–10 h, padrão 2 h, é diferente da tolerância inicial 30 min. Som inválido/removido exige fallback. Não reusar tutorial-completed como permissão concedida.

## 9. Correções de orientações antigas e erros comuns

| Erro recorrente | Orientação correta |
|---|---|
| “runBlocking é correto porque receiver deve ser síncrono” | Trabalho bloqueante longo em onReceive pode causar ANR. Usar lifetime-aware trabalho limitado, goAsync/finish em finally e mecanismo durável quando necessário |
| “Manter todos os flags legacy de janela” | Preservar comportamento validado, modernizar APIs/insets/keyguard; flags obsoletos não são requisito |
| “USAGE_ALARM ignora DND/volume sempre” | Atributo e política de canal não anulam escolhas do usuário em todos os aparelhos |
| “FGS + delay permite startActivity” | Serviço foreground não é autorização universal de background activity launch |
| “Permissão no manifesto significa concedida” | Runtime e special access/canais têm estados independentes e podem ser revogados |
| “setOngoing torna impossível cancelar” | Dismissibilidade varia; implementar silêncio explícito com semântica correta |
| “Inserir MISSED no disparo simplifica histórico” | Corrompe janela de confirmação; seguir máquina de estados |
| “cancelar serviço globalmente depois de qualquer botão” | Pode silenciar outro medicamento; ações precisam ocorrência |
| “Room ou sandbox comprova criptografia/LGPD” | Room padrão não criptografa; auditar ameaças/proteção/backup e documentação |
| “Compilar comprova migrations/alarme” | Compilar valida estrutura; execução específica comprova comportamento |
| “logs.value serve para exportar filtro recém-selecionado” | StateFlow pode estar vazio/antigo; consultar request/snapshot consistente |
| “Mudar horários deve atualizar log passado” | Horário original é fato histórico; editar só vigência futura |
| “Mudar fórmula de PendingIntent é refatoração inocente” | Cancelar/migrar identities antigas para evitar órfãos |
| “Passar Context do Fragment ao ViewModel sem cuidado” | Injetar abstração/application context quando necessário; não reter tela |
| “Se um teste não pôde rodar, resultado implícito aprovado” | Marcar pendente e manter gate afetado aberto |

## 10. Backup, proteção e Play

Manifesto atual `allowBackup=true`; `backup_rules.xml` contém `<paths>` de FileProvider, não regra adequada de backup. `data_extraction_rules.xml` exclui sharedpref/database de cloud, mas DataStore está em arquivos e transferência precisa decisão. Não afirmar que desinstalar remove PDFs fora do sandbox ou que nenhum backup existe sem ensaio.

A justificativa exige medidas de criptografia/controle. P03 especifica ameaça e proteção verificável; se necessário, executar tarefas condicionais de criptografia com biblioteca mantida/chave Keystore/migração/16KB. Não exigir autenticação biométrica a cada consulta usada por receiver; acesso a dados antes do primeiro desbloqueio após reboot exige tratamento explícito. Não migrar segredos do usuário para logs/JSON de teste.

Políticas oficiais verificadas em 2026-09-29; revalidar no envio:

- Telefone novo/update: targetAPI 36 desde 31/08/2026. [Target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-gb).
- Health apps abrangidos precisam Organization; confirmar enquadramento Medication and Treatment Management e requisitos reais da conta. [Console](https://support.google.com/googleplay/android-developer/answer/10788890?hl=en), [categorias de saúde](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en).
- Brasil: marco de identidade/registro 30/09/2026, conferir pacote no Console. [Verificação](https://developer.android.com/developer-verification).
- USE_EXACT_ALARM tem elegibilidade restrita distinta de SCHEDULE_EXACT_ALARM. FSI e FGS têm declaração/avaliação próprias. [Permissões restritas](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en), [FSI/FGS](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en).
- Suporte 16 KB verificar no binário/transitivas; página vigente informa bloqueio de updates incompatíveis 01/02/2027. [Page sizes](https://developer.android.com/guide/practices/page-sizes).
- Offline ainda exige Data safety e política pública coerentes. Conta de usuário não existe, exclusão de conta é condicional. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en).
- Regra 12 testers/14 dias é para contas pessoais novas abrangidas, não universal nem substituto de Organization quando exigida. [Testes](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en).

Preparar AAB/Play App Signing, proteger chave de upload, versionCode monotônico, release instalada pela faixa de teste, ficha/locales/contato/privacidade/saúde/conteúdo/FSI/FGS e instruções reais ao revisor. Não criar conta ou adicionar Health Connect apenas por ser app de saúde. README anuncia GPL-3.0; LICENSE não foi encontrado no inventário: confirmar antes da distribuição.

## 11. Gravação e verificação para o próximo agente

Criar AVD de QA separado. Somente dados sintéticos; serial explícito em adb. Roteiro obrigatório usa UI real desde instalação limpa, concessões visíveis, cadastro com horário ≥2 min futuro, bloqueio e disparo AlarmManager. Não usar am broadcast/startActivity como evidência do fluxo que está quebrado. Vídeo deve mostrar ação e histórico durável, acompanhado de logs/dumps/asserções.

screenrecord não grava áudio e tem limite de duração; preservar segmentos originais e timestamps quando necessário. Captura bloqueada pode ter restrições; usar evidência externa se exigida sem desligar proteção só para filmar. Não marcar vídeo citado pelo DOCX como vídeo atual da v1.0.

Check de implementação: assemble/lint/testes locais; Room instrumentado e migrations; Espresso/UI Automator e aparelho; bundleRelease e instalação Play. Tests apropriados devem focar riscos de dados/concorrência/tempo, não espelhar cada troca de string.

Atualizar docs com decisões executadas, caminhos de evidência e limitações reais. Ao retomar: ler TASKS, escolher tarefa desbloqueada, inspecionar git status e concluir mudança+validação; não repetir auditoria inteira nem alegar lançamento antes de concluir gates externos.
