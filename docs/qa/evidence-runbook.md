# Gravação sintética da jornada e índice de evidências

Estado: harness e script implementados; **ainda não compilados nem executados após estas mudanças**. Nenhum vídeo novo foi produzido por esta página. O ambiente dedicado ainda precisa estar online e concluir o boot. Não marcar E02–E09 concluídos por existir automação.

## Seleção e limpeza

`scripts/qa/evidence_record.py` exige `--serial`, identifica `ro.boot.qemu.avd_name=DoseCerta_QA`, boot completo e fuso exatamente igual ao argumento `--timezone` (padrão `America/Sao_Paulo`). Qualquer outro AVD é rejeitado antes de instalar, desinstalar, mudar ajustes ou capturar dados. O AVD pessoal `emulator-5554` não é ambiente de reset. O script também rejeita um `screenrecord` já ativo; somente um driver pode usar o serial de cada vez.

`--clean` desinstala somente `com.dosecerta` e `com.dosecerta.test` no AVD sintético e reinstala os APKs escolhidos. Isso restaura primeiro uso e estados de acesso iniciais sem manter grants de uma execução anterior. O script configura formato de 24 horas e timeout de tela da fixture; **não concede permissões com pm grant/appops** no vídeo principal. Instalação por ADB é registrada no manifesto. O primeiro `screenrecord` deve estar em execução antes de iniciar a instrumentação; o vídeo mostra primeiro uso limpo, e a instalação por ADB tem registro separado.

Antes de executar, root confirma que nenhuma outra instrumentação usa o serial e que build/lint/checks do candidato passaram. Não misturar APK anterior com source atualizado; manifest registra SHA256 de ambos APKs, commit e working tree.

```sh
python3 scripts/qa/evidence_record.py --serial emulator-5580 --scenario main --clean --print-plan
python3 scripts/qa/evidence_record.py --serial emulator-5580 --scenario main --clean
```

O primeiro comando é inspeção local, não altera dispositivo. O segundo executa a jornada somente depois das guardas. Flags `--apk`, `--test-apk`, `--output`, `--timezone` e `--timeout-seconds` permitem selecionar concretamente o candidato e o ambiente. Output precisa ser novo, preservando execuções antigas.

## Jornada principal — E02–E05

`FullJourneyInstrumentedTest.firstUseFutureDoseRealAlarmAndPersistedOutcome` exige `-e fullJourney 1`, ambiente dedicado e primeiro uso limpo. Faz consentimento, POST_NOTIFICATIONS pela caixa real do sistema, exact alarm e FSI pelos switches reais das respectivas páginas, e tutorial. Não altera consentimento ou grants em DataStore/ADB para fingir a UI.

Cadastro acontece pelos campos do formulário e TimePicker em modo teclado de 24 horas. A medicação é fictícia e tem horário futuro; prévia e Home são capturadas. Consultas read-only comprovam a posologia gravada pela UI; não há inserts de banco nesta jornada.

A automação bloqueia o dispositivo e aguarda o card do alarme real agendado. Não inicia AlarmActivity/receiver diretamente. Card bloqueado deve ser genérico e esconder Tomei/Pular. A execução principal desbloqueia pelo botão, confere identidade, confirma Tomei e abre Histórico. Asserções verificam ocorrência/originalDueAt, actualTime, tomada única e atraso observado de entrega em 0–10 segundos. Falha captura hierarquia/tela e resultado; script tenta coletar AlarmManager/notificações/activity/audio/logcat mesmo ao atingir timeout. Falhas de coleta são registradas no manifesto; não são tratadas como sucesso.

A imagem sem PIN prova comportamento de keyguard não seguro. Autenticação de keyguard seguro e TalkBack/Switch Access precisam de ensaios separados; não transformar o sucesso do emulador em aprovação desses requisitos.

## Cenários separados e dez entregas — E08–E09

```sh
python3 scripts/qa/evidence_record.py --serial emulator-5580 --scenario skip --clean
python3 scripts/qa/evidence_record.py --serial emulator-5580 --scenario snooze --clean
python3 scripts/qa/evidence_record.py --serial emulator-5580 --scenario silence --clean
python3 scripts/qa/evidence_record.py --serial emulator-5580 --scenario denied --clean
python3 scripts/qa/evidence_record.py --serial emulator-5580 --scenario nominal --clean --timeout-seconds 1800
```

Pular confirma SKIPPED sem horário de tomada. Adiar confirma SNOOZED/originalDueAt preservado. Silenciar confirma DISMISSED com desfecho PENDING. Cada ação possui execução/manifesto/vídeo próprio. O cenário denied nega POST na UI real, permite exact pela UI para poder observar entrega real do receiver e comprova que Configurações informa o bloqueio e nenhum áudio foi declarado playing.

Nominal cadastra dez horários reais, um por minuto, na mesma medicação fictícia, por UI. Bloqueia/aguarda/identifica/toma cada ocorrência e verifica Histórico acumulado, sem inserção de logs. `result.json` contém cada occurrenceId, originalDueAt, deliveredAt e delayMillis. Atraso maior que 10 segundos ou perda falha o teste. Dez resultados em uma imagem não aprovam plataformas/OEMs não executados.

`--scenario pdf-fixtures` executa a suíte técnica `PdfReportInstrumentedTest`, com scope explícito no manifesto. **Não substitui o vídeo de exportação pela UI, CreateDocument e PDF aberto exigido por E08.** Esse cenário de UI permanece pendente; fixtures/render/provider têm suas próprias evidências no workstream de relatório.

## Artefatos e revisão — E06–E07

Cada execução grava segmentos `screen-001.mp4` etc. de até 175 segundos, preservados mesmo em timeout/erro, seus logs, `instrumentation.txt`, dumpsys antes/depois, logcat, e pacote de capturas/hierarquias/resultados exportado por run-as de `files/qa-journey/<runId>`. O run ID também é passado à instrumentação e validado no `result.json`; um resultado antigo não pode aprovar uma execução nova. O cenário PDF exporta `cache/qa-reports` separadamente. Nenhum arquivo anterior é substituído. `manifest.json` registra timestamps UTC, serial, AVD, API, fingerprint, page size, fuso, hashes dos APKs/vídeos, git e escopo. `docs/evidence/index.json` acrescenta cada manifesto.

Git preserva manifestos, resultados de auditoria, capturas, hierarquias, vídeos e resultados de instrumentação. Arquivos `.tar` de transporte e diretórios `cache/` exportados em `docs/qa/runs/` e `docs/evidence/` ficam somente no disco local, ignorados para evitar duplicação e PDFs sintéticos volumosos. Dumps rotineiros `docs/qa/runs/**/logcat.txt` também ficam locais; o log do crash citado no relatório de 04/10 é uma exceção explícita versionada. Um checkout novo não contém esses PDFs, logs rotineiros ou pacotes originais: para repetir auditorias que leem PDFs, execute novamente a suíte sintética; para preservar exatamente os bytes de uma execução histórica, arquive o bundle completo separadamente antes de limpar arquivos locais. Uma execução nova tem seu próprio run ID e não substitui a evidência histórica.

O script interrompe somente o PID de `screenrecord` associado ao segmento atual. Em timeout ou cancelamento, encerra a instrumentação e o pacote-alvo sintético antes da coleta; essa ação fica explícita no manifesto. Uma instalação falha, vídeo ausente, resultado com run ID diferente ou erro de coleta mantém a execução reprovada. A instalação por ADB não é evidência de instalação pelo Google Play.

Android screenrecord não grava o áudio interno. O manifesto declara essa limitação; vídeo sozinho não comprova som. Diagnóstico playing confirma MediaPlayer iniciado, mas verificação audível/volume/DND exige aparelho/saída de áudio observados.

O script nunca marca `videoReviewed=true`. Para E07, assistir cada segmento (incluindo suas transições), conferir legibilidade e relação com capturas/assertions e registrar o responsável e artefatos vistos no manifesto/índice. Marcar execução como aprovada só quando instrumentação, vídeo disponível e revisão forem verdadeiros. Se houve erro, preservar o manifesto e registrar o defeito; não renomear erro para sucesso.

## Verificação local do harness

Em 2026-09-30, `python3 -m py_compile` e `--print-plan` passaram. O plano inclui comando de jornada principal, serial explícito, run ID, guardas, política de concessão e limitação de áudio. Isto verifica sintaxe e planejamento do script; compilação Android, bootstrap da imagem, concessões reais, screenrecord/pull e execução/revisão ainda precisam de resultados próprios.
