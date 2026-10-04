# DoseCerta — contexto completo para retomada

## Estado vigente — TASKS em 04/10/2026

Pedido atual: terminar TASKS rapidamente, incluindo validações locais. [Registro desta retomada](docs/qa/task-completion-20261004.md) e TASKS atualizado prevalecem sobre os checkpoints abaixo. Checks debug/release/lint/JVM passaram; alarmes reais API26 passaram. A ferramenta de teste foi atualizada para Android16. API36 bootou, mas um crash do emulador/GPU interrompeu a suíte; API37/16KB foi preparada separadamente. Gates externos permanecem não definidos. Preservar Home com gráfico, dados preexistentes e AVD pessoal. Nenhum commit ou publicação.

## Atualização vigente — polimento da UI em 03/10/2026

A solicitação atual é terminar o polimento visual, priorizando proporção e informação visível de primeira. Depois de compactar as listas, o responsável pediu **restaurar o gráfico circular de percentual na Home**. A direção vigente combina Material 3 nativo, card de boas-vindas com gradiente e anel compacto, cards de doses com cerca de 80 dp e cards de medicamentos com cerca de 79 dp. Não voltar aos cards altos nem remover novamente o gráfico circular sem pedido do responsável.

O usuário autorizou retomar testes e validações locais na sessão de 03/10, às 19:47 UTC; as instruções de suspensão abaixo são históricas. O AVD dedicado `DoseCerta_QA` está disponível em `emulator-5580`, API 26, 360 × 640 dp. Testes inserem e removem somente suas próprias fixtures; registros preexistentes e AVD pessoal devem ser preservados.

Resumo, capturas, APK e limites da validação atual em [ui-polish-20261003](docs/qa/ui-polish-20261003.md). Resultados de outras frentes da sessão anterior estão em [local-validation-20261003](docs/qa/local-validation-20261003.md). O objetivo de polimento é distinto da conclusão dos 121 itens de TASKS e da publicação. Nenhum commit, reset ou publicação nesta etapa.

O restante deste documento preserva checkpoints anteriores; não usar o estado de build, emulador ou preferências desses checkpoints como estado atual.

## Histórico de 30/09 — correção urgente da Home

Em 30/09/2026, o responsável pediu corrigir imediatamente a regressão visual da tela principal. A Home recuperou o card com gradiente, indicador circular, hierarquia dos títulos e cards compactos de medicamentos, preservando os flows e as ações atuais. Build/lint passaram e o APK foi atualizado e inspecionado no `emulator-5554`/API36, com `install -r`, sem limpar dados ou registrar doses. Evidência e limites em [home-view-restoration](docs/qa/home-view-restoration.md). Preservar essa estrutura visual; a versão anterior com todos os textos e ações empilhados foi rejeitada pelo responsável.

Antes dessa correção, a retomada de TASKS executou 20 testes JVM sem falhas, build/lint debug e release sem erros, e inventário do releaseRuntimeClasspath: 95 componentes, 90 binários, licenças resolvidas, nenhuma `.so` no APK/AAB inspecionados. Os artefatos release antecedem a correção da Home. A conta Play, contato/URL e assinatura continuam não definidos, conforme resposta do responsável nesta retomada. Não há instrumentação Android aprovada: tentativas sintéticas API36.1/API26 terminaram com falhas do emulador. Scripts `prepare_avd.py`/`start-emulator.sh` e logs no cache/QA preservam as tentativas sem alterar o AVD pessoal.

O objetivo TASKS retornou `paused` na ferramenta de goal após o redirecionamento para a Home; não foi marcado completo. O checkpoint anterior abaixo permanece como histórico, inclusive a instrução temporária de pular testes daquela sessão.

Checkpoint: **30/09/2026, 17:04 UTC**, atualizado nesta sessão. Objetivo: concluir os **121 itens de TASKS.md**, usando múltiplos agentes. Pedido atual: salvar CONTEXT/README/TASKS, continuar o trabalho e **pular testes por enquanto**. Novos testes não serão executados nesta etapa. Aceites que dependem de execução permanecem abertos. Não houve pedido de publicar, commitar, descartar mudanças ou apagar dados pessoais.

## Preferências e limites confirmados

- Conta Play, identidade/organização, contato e URL pública da política, certificado de upload e versionCode do Console ainda **não definidos**.
- O usuário possui Xiaomi, mas escolheu **usar emuladores por enquanto**. Samsung, telefone conectado e participantes reais não estão disponíveis nesta etapa.
- Screenshot anterior mostrou AVD pessoal `Medium_Phone_API_36.1`, serial `emulator-5554`. **Não resetar, apagar ou substituir esse AVD.** Nesta retomada nenhum emulador apareceu no ADB; a captura anterior não comprova disponibilidade atual.
- Somente o AVD sintético `DoseCerta_QA`, porta 5580, serial `emulator-5580`, pode ser preparado com dados fictícios. O diretório temporário anterior desapareceu entre sessões; foi recriado separadamente, sem dados pessoais.
- Nenhuma chave, segredo, PIN ou autorização de publicação recebida. Credenciais fora de Git/logs/documentos. Aprovação de comando não equivale a aprovação de release.
- Workspace `/home/gabirusky/Code/DoseCerta`; escrita no workspace e `/tmp`. Gradle/ADB/downloads/emulador usam execução aprovada quando necessária. Nenhum reset/commit/merge realizado.
- A ferramenta de objetivo retornou `usageLimited` na retomada; objetivo original não concluído. O pedido atual autoriza continuar o trabalho, sem registrar conclusão fictícia.

## Base preservada e toolchain

Base Git `cc2ca144e10c720345ed6924c7b7e7ef27c2cfbe`, branch `main`. O usuário já tinha mudanças em `.gitignore`, BUILD, CONTEXT, README, Gradle e arquivos não rastreados PLAN/TASKS/docs/wrapper. Preservadas em `docs/qa/initial-local-changes.patch` e `docs/qa/initial-status.txt`. Contexto anterior à reescrita: `docs/qa/context-before-implementation.md`.

| Configuração | Valor |
| --- | --- |
| Namespace / applicationId | `com.dosecerta` |
| min / compile / target SDK | 26 / 36 / 36 |
| Versão | 1.0; versionCode padrão 1, sobrescrito por `-PreleaseVersionCode=N` |
| Gradle / AGP | wrapper 8.14-all / 8.13.2 |
| Kotlin / KSP | 1.9.20 / 1.9.20-1.0.14 |
| Room / Material | 2.6.1 / 1.11.0 |
| Java local / CI / bytecode | JDK 21.0.9 do Studio / JDK 17 / JVM 17 |
| JDK local | `/home/gabirusky/Programs/android-studio/jbr` |
| SDK instalado | `/home/gabirusky/Android/Sdk`; plataformas 34, 35, 36 e 36.1 |
| Imagem inicialmente disponível | API 36.1 Google Play x86_64 |

Java padrão 25 não é o JDK escolhido para build. Schema Room exportado em `app/schemas`. UI Automator 2.3.0 integra instrumentação. Assinatura opcional por `DOSECERTA_UPLOAD_KEYSTORE`, `DOSECERTA_UPLOAD_STORE_PASSWORD`, `DOSECERTA_UPLOAD_KEY_ALIAS`, `DOSECERTA_UPLOAD_KEY_PASSWORD`; bundle sem essa configuração não é candidato assinado para Play.

APK preexistente preservado em `docs/qa/baseline-artifacts/app-debug.apk`, SHA-256 `acc07dae118b1a91c263f1cd8c9fb1c643a6132f43fc4ae40864527b4f65c061`. AAPT confirmou compile/target 34, min 26, versionCode 1. Não foi demonstrado que esse APK é reproduzível a partir do commit base. Manifesto em `docs/qa/baseline-merged-manifest.xml`; não usar como candidato atual.

## Dados, identidade e estados

`MedicationLog` representa a ocorrência, sem entidade paralela competindo com o histórico. Identidade opaca `dose:<scheduleId>:<version>:<YYYY-MM-DDTHH:mm>` usa slot civil solicitado antes de DST; consumidores usam a API, sem recompor IDs. `originalDueAt`, `scheduledTime`, data local/fuso originais e snapshots de nome/dose/unidade/cor/forma/frequência/notas são congelados. Snooze altera instante efetivo/prazo sem substituir próxima recorrência. Formulário e avulsa usam tokens idempotentes persistidos.

Schema Room 4: medications, schedules, medication_logs, occurrence_suppressions, reconciliation_checkpoints, medication_save_receipts. Exportado em `app/schemas/com.dosecerta.data.local.DoseCertaDatabase/4.json`, com 29 campos de log. Migrações preservadoras 1→2→3→4 baseadas nos commits históricos; distribuição antiga não comprovada. Sem fallback destrutivo. FKs de logs SET_NULL. Duplicados conservam IDs; canônico TAKEN > SKIPPED > MISSED > PENDING, depois maior ID. Demais ficam CANCELLED e fora da adesão. Origem de snapshot reconstruído `LEGACY_V3_RECONSTRUCTED` não prova prescrição anterior.

`DoseStateMachine` puro: PENDING, ALERTING, SNOOZED, DISMISSED, TAKEN, SKIPPED, MISSED, CANCELLED. Entrega não produz MISSED imediato; tolerância 30 minutos. Som 60 segundos, snooze padrão 10 minutos, follow-up do pipeline 1–10 horas/padrão 2 horas. SKIPPED não recebe convite para reconsiderar. TAKEN histórico exige horário explícito; SKIPPED/MISSED têm actualTime nulo.

`DoseActionCoordinator` expõe deliver/take/skip/dismiss/timeout/cancel/snooze/correct e retorna Success(occurrence, changed)/Rejected(reason)/Failure(error). Ação ao vivo atrasada rejeitada. Transações, índice único e INSERT IGNORE; falha de persistência não confirma UI. Cancelamento de coroutine propagado.

`saveMedicationWithSchedules` grava medicação/slots juntos, preserva createdAt, rejeita duplicados e aposenta versões; retorna SavedMedication(id, activeSchedules, retiredScheduleIds) para agendar depois do commit. Archive preserva histórico; exclusão permanente oferece manter/remover logs. Tombstones opacos impedem recriação. `correctLog(logId, status, takenAt)` usa horário informado. Relatório consulta snapshots consistentes, sem substituir detalhes pelo cadastro atual.

Adesão comum: TAKEN/(TAKEN+MISSED+SKIPPED) apenas para conclusões de doses programadas. PRN/avulsas/pendentes/canceladas fora do denominador; pais excluídos com histórico mantido conservam elegibilidade. Vazio percentual nulo/Sem dados; filtros de linhas não alteram resumo.

Fixes posteriores ao APK integrado: migração recupera minuto histórico pelo timestamp, usando minuto atual somente quando resolve mesmo instante em DST; checkpoint futuro após retrocesso do relógio revisita vigência conhecida para incluir versões novas. Não mudam shape do schema; precisam reconstrução/verificação. Contratos: `docs/adr/dose-occurrences.md`, `docs/qa/workstream-data.md`.

## Recorrência

Schedule persiste DAILY/INTERVAL/WEEKLY/SELECTED_DAYS/MONTHLY/AS_NEEDED, dia mensal, dias Calendar 1=domingo a 7=sábado, fuso, versão/vigência. Intervalos legados conservam horários civis explícitos, sem cronômetro desde última tomada. Cada horário tem slot independente.

Calculador injeta Clock/fuso, usa [começo, fim) e próxima data estritamente futura. Mês sem dia escolhido é pulado; mensal não é 30 dias. Gap DST avança pela duração do salto; overlap usa primeiro offset uma vez. Capturadas mantêm instante/identidade após viagem; novos slots acompanham regra/aparelho. Preview mescla capturados/calculados, exclui terminais/suprimidos. Exemplos em `docs/adr/recurrence.md`.

Dez testes de recorrência passaram anteriormente. Novo caso mensal dia 30 cobre fevereiro comum/bissexto, março→abril e virada do ano, mas **não executado**. Há agora 20 métodos JVM escritos; apenas os 19 anteriores têm resultado de aprovação.

## Alarmes, capacidades e privacidade

Canais estáveis, sem recriação para resetar preferências. Serviço é dono do áudio; notificação da dose silenciosa. Checker lê POST/estado geral/canal/exact/FSI por API. Onboarding centraliza concessões, reconsulta Settings e não informa sucesso quando recusado. Main não abre prompts concorrentes.

PendingIntent URI inclui ocorrência/tipo/ação; recorrência/snooze/timeout/reminder independentes com handles persistidos. Scheduler retorna exato/inexato/falha, trata revogação entre check/chamada; fallback inexato só notifica quando permitido, sem presumir FGS em background. Mutex comum serializa schedule/cancel/snooze; revalida aposentadoria após AlarmManager. Última correção de snooze ainda não compilada.

Receiver/serviço validam medicação/versão/estado antes de áudio/card. FSI mediado por notificação, sem Activity tardia indiscriminada. Fila/tags por ocorrência; cancelar uma mantém outra e próxima recorrência. Reconciliation cobre boot/package/exactgrant/hora/fuso, sem áudio boot/passados em lote. Scopes/callbacks/timers/MediaPlayer/wake lock liberados; goAsync finally. Diagnóstico local 100 eventos/IDs hash, sem nomes/doses/upload.

Card revalida em onNewIntent/restauração, cancela gesto/IO anterior. ACTION_CANCEL não adia. Botões explícitos equivalentes ao swipe; texto sp/scroll. API 26 flags legadas; APIs 27+ showWhenLocked/turnScreenOn sob guarda. FLAG_SECURE incondicional removido para evidência sintética; detalhes bloqueados continuam ocultos por padrão. Silêncio/adiar/desbloquear disponíveis; tomar/pular exigem identificação por desbloqueio ou preferência explícita.

Settings tem escritas DataStore finitas/serializadas, recovery de capacidades/som inválido e aviso médico/versão. Manifesto remove USE_EXACT_ALARM/storage/overlay/batteryrequest/portrait. ACCESS_NETWORK_STATE transitiva de WorkManager; manifesto observado sem INTERNET. Workstream: `docs/qa/workstream-alarm.md`.

## UI e PDF

Add/Edit empilha campos, seção Posologia PT/EN, seletores dias/mês e cinco próximas datas. Rascunho SavedStateHandle; commit idempotente, erros/retry. Título/Salvar/Cancelar rolam com teclado/fonte ampliada. Seleção de horário conserva identidade ao inserir slot anterior. Insets barras/cutout/IME comuns; bottomnav oculta no form; tutorial único/retomável.

Home hoje/futuros/PRN separados, relógio sem writes, ação após commit. Avulsa com token/confirm/lista limitada. Medicamentos restaura busca/filtro, distingue vazio/sem resultado, archive/delete informados. PT/EN, tokens claro/escuro e largura adaptável. Inventário/heurística em `docs/design/`; nenhuma entrevista real.

Histórico ConcatAdapter header+linhas virtualizadas, opções por click além de long press. Correção TAKEN pede data/hora explicitamente, default anterior; horário tomado só em TAKEN. Resumo período completo, filtros somente linhas.

ReportRequest captura datas [começo, fim), fuso, idioma, status, instante pedido. ReportSnapshot consulta repositório diretamente, separa resumo/linhas, independente de logs.value. Renderer PdfReportGenerator puro sem Context/DB/Intent/storage: medição/quebra/paginação resumo/detalhes/continuação e páginas numeradas. PdfDocument fecha finally. VM só applicationContext, request/estados restauráveis.

SAF CreateDocument(application/pdf) API 26+, ContentResolver stream.use, cancelamento/erro/cleanup parcial best effort. Abrir/share content URI, MIME/grant/ClipData; sem FileProvider necessário nesse caminho. Leitor ausente informa alternativa. Fixtures/provider runtime não executados.

## Evidência real e limites

| Check anterior | Resultado comprovado |
| --- | --- |
| assembleDebug / assembleDebugAndroidTest | Passaram; APKs anteriores aos fixes recentes |
| JVM 2026-09-30T02:52:50Z | 19 passaram: recorrência 10, estados 6, adesão 3; zero falhas/erros/skips |
| lintDebug | **Falhou: 43 erros, 228 warnings**; fixes aplicados, rerun pendente |
| Schema/manifesto | Room 4 exportado; min 26 / target 36 confirmados |
| Strings/layouts | XML válido; paridade estática legado 200/200, feature_ui 75/75 |
| APK real auditado | SHA-256 `a242437db8d5f2dab3c8fefab05340a308e2bf643c38ef70e00e056e851451ba`; nenhuma .so no ZIP |
| Emulador pessoal anterior | API 36; encrypted/file; PAGE_SIZE=4096 |

XMLs JVM em `app/build/test-results/testDebugUnitTest/`; duração 0,336 s no host não é desempenho Android. Log `docs/qa/checks-integration.log`; auditoria `docs/qa/checks/apk-audit.json`. Fixes lint: guards API 27, windowLightNavigationBar condicionado, 36 strings EN, aliases privacidade com % literal. Sem baseline para ocultar falhas. APK anterior não valida fontes recentes.

**Nenhum teste Android aprovado declarado**: migrações, escala, UI, PDF, alarme, vídeo, AAB assinado/Play, backup/transfer real, bateria 24 h ou entrevista. Testes novos suspensos por pedido do usuário. Revisão/compilação não altera esse estado.

## Estado do emulador

Tentativas anteriores: mount /data e framebuffer 0×0; após wipe sintético, host GPU chegou aos serviços e terminou exit 139/SIGSEGV. Na retomada /tmp antigo desapareceu e ADB não tinha dispositivos. AVD recriado com swiftshader/Vulkan desativado também terminou exit 139, confirmado por processo/coredump; pessoal intacto.

Ferramentas oficiais verificadas/preparadas em `.cache/qa-sdk`: Emulator 37.1.11 e SDK Tools 23.0, sem substituir SDK instalado. Tentativa com SDK preparado terminou exit 1 por platform-tools ausente; link para instalação existente criado, mas **sem retry após pedido de pular testes**. Nenhum dedicado saudável/rodando. Comandos/checksums/logs: `docs/qa/emulator-validation.md`, `sdk-tool-integrity.json`, `synthetic-avd-config.ini`. Reconsultar processos/arquivos na retomada; handles temporários não são garantia.

16 KB exige PAGE_SIZE=16384; 4096 não substitui. V01 exige APIs 26/28/29/31/33/34/35/36 e consulta da estável mais recente. Revalidar políticas/imagens na submissão; pesquisa anterior não comprova Console atual.

## Harnesses preparados, Android sem execução

- OccurrencePersistenceTest: 12 métodos/fixtures SQL v1/v2/v3; Room, concorrência, rollback, snapshots, delete/supressão, revisão, relógio/fuso, tokens.
- SyntheticScaleTest: 10.000 logs/50 meds/100 slots/100 dias/10% avulsas, banco privado teste; métricas query/action/preview. Cold start separado. seedMainDatabase=true só explicitamente em banco principal vazio do AVD sintético.
- UiFlowInstrumentedTest: cada Frequency via UI/preview/recreate/save/Room/capturas; setup marcado completo para isolar formulário, não demonstra primeiro uso; guard DoseCerta_QA.
- PdfReportInstrumentedTest: vazio, 150 linhas/50 meds, 10k, 30 longas, wrap/streamerro, PDFs/PdfRenderer. Host pdftotext/pdfinfo disponível quando retomados.
- AlarmPipelineInstrumentedTest: URIs distintas/entrega AlarmManager real simultânea, opt-in alarmPipeline=1/guard dedicado. Revogação exact externa pois pode matar processo.
- FullJourneyInstrumentedTest: primeiro uso/permissões UI/tutorial/cadastro fictício futuro/lock/wait/card/histórico; take/skip/snooze/silence e POST negada. fullJourney=1, journeyAction, journeyCount=1..10, deniedJourney=1, evidenceRunId. @Test runBlocking<Unit>; artefatos qa-journey por run ID. Ajustes não compilados/executados.
- evidence_record.py: grava antes da instrumentação, serial/AVD explícitos, segmentos screenrecord, SHA/API/fuso/logs/resultados/manifesto até em falha. Só interrompe recorder próprio; resultado deve ter run ID atual. Runbook `docs/qa/evidence-runbook.md`. Python syntax/print-plan passaram antes de pular testes; nenhum vídeo executado.
- Scripts locais e CI em `scripts/qa/`, `.github/workflows/android-checks.yml`; CI remoto não executado.

## Proteção de dados e release

ADR `docs/adr/data-protection.md`: armazenamento CE privado, sandbox UID, criptografia efetiva/bloqueio do aparelho, backup excluído. Room não criptografado independentemente. Sem dados clínicos em Device Protected Storage; boot após primeiro desbloqueio. Root/SO comprometido/debugging autorizado/aparelho desbloqueado/PDF fora do app não cobertos. FBE emulador não prova Keystore físico/OEM/API antiga.

S01–S06 **não ativadas**, motivo no ADR; não marcar SQLCipher/Keystore fictício. Se ameaça mudar, ativar antes da release e repetir validações. P03/P04/V01/V04 têm verificação pendente.

Backup XML full-backup-content e extraction cloud/device-transfer excluem database/file/sharedpref/root/external; allowBackup=false. Restore real pendente. Política/permissions/data-safety/licensing/store-listing/external-gates em `docs/release/`. LICENSE GPLv3 conforme README prévio; inventário transitivo em conclusão.

Conta/contato/URL/certificado/versionCode, health/DataSafety/FSI/FGS, testes Play/pre-launch/publicação pendentes. Go/no-go/notas/hotfix preparados localmente não comprovam submissão. Não inventar avaliações, vídeos, usuários ou aceites.

## Continuação e propriedade

1. Salvar três docs e finalizar código/tooling/inventário/licenças/gates locais.
2. Build sem execução de testes pode continuar; registrar resultado separado. Testes JVM/Android/matriz/vídeos aguardam autorização para retomar.
3. Quando retomados: reparar AVD dedicado, reconstruir APKs/testes, lint/JVM/Room/escala/PDF/UI e corrigir falhas reais.
4. Alarme real/concorrência/negativas/privacy/boot/processo/Doze/fuso, provider/PDF, APIs/16 KB/fontes/tamanhos/locales/acessibilidade e dez disparos. OEM físico/bateria 24 h gates próprios.
5. Definir dados externos, mesmo candidato assinado/Play e go/no-go; marcar tarefa somente com aceite pertinente comprovado.

Agentes desta retomada: root (CONTEXT/README/TASKS, build/shared/History/PDF/integração); alarm_evidence_finish (scheduler/harness/recorder, concluído/fonte congelada); release_qa_finish (licenças/go-no-go/hotfix/teste mensal); emulator_validation (ambiente/diagnóstico, concluído por pedido de pular testes). Agentes v2 antigos são históricos; consultar agentes vivos antes de duplicar. Só root edita docs de estado, só um Gradle gera no mesmo diretório por vez. Arquivos/logs existentes prevalecem sobre memória de sessões efêmeras.
