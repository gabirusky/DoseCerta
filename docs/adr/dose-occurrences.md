# ADR — identidade, estado e integridade das doses

Data: 2026-09-29. Decisão implementada no schema Room v4.

A ocorrência é uma extensão de `MedicationLog`, em vez de uma entidade concorrente ao histórico. Uma dose programada tem `occurrenceId = dose:<scheduleId>:<version>:<YYYY-MM-DDTHH:mm>`; o último componente é o horário civil solicitado, antes de ajuste de DST. Consumidores tratam esse valor como opaco. `originalDueAt` e `scheduledTime` são timestamps imutáveis, `originalLocalDateTime` guarda o slot solicitado e `originalZoneId` registra o fuso da captura. Snooze altera `snoozedUntil` e `deadlineAt`, preservando identidade, dia e horário originais. Avulsas usam UUID ou `extra-request:<token>` para pedidos restaurados.

Uma ocorrência já capturada mantém seu timestamp mesmo se o fuso do aparelho mudar. Slots ainda não capturados usam o fuso da regra, ou o fuso atual do aparelho quando `zoneId` está vazio. Essa escolha evita que uma troca de fuso recrie a mesma dose do mesmo dia. `previewOccurrences` combina slots futuros capturados com datas calculadas e marca os capturados como `isPinned`; não reconstrói ocorrências concluídas, canceladas ou suprimidas. Alterar a posologia cria uma nova versão, cancela ocorrências pendentes da versão anterior e não muda resultados já concluídos.

A ocorrência captura nome, dose, unidade, cor, forma, frequência e notas da medicação. Consultas de Histórico/PDF usam esses campos, sem `JOIN` que substitua detalhes por valores atuais. A nota do registro de dose (`notes`) é distinta da nota de cadastro (`snapshotNotes`). Campos reconstruídos de v3 recebem `snapshotOrigin=LEGACY_V3_RECONSTRUCTED`: o cadastro existente no momento da migração não prova a prescrição antiga. O app preserva a informação disponível e explicita essa origem; não inventa versões anteriores de posologia.

## Transições e parâmetros

`DoseStateMachine` é puro. A entrega muda PENDING/SNOOZED para ALERTING, mantendo o resultado público PENDING. Há 30 minutos de tolerância desde o horário efetivo; não se registra MISSED ao tocar. Tomar e pular produzem TAKEN/SKIPPED, respectivamente. Fechar, voltar e silenciar produzem DISMISSED, sem resultado clínico. TIMEOUT produz MISSED somente ao atingir `deadlineAt`. Comandos incompatíveis, doses inativas e ações após o prazo são rejeitados. Repetir a mesma conclusão devolve sucesso com `changed=false`; timeout não sobrescreve TAKEN/SKIPPED. Correção de histórico é um comando separado e requer horário explícito para TAKEN; SKIPPED/MISSED têm `actualTime=null`.

O som tem limite de 60 segundos, o snooze padrão é 10 minutos e amplia o prazo até pelo menos snooze + 30 minutos. O follow-up de MISSED usa um atraso fornecido pelo pipeline; a preferência do app (1–10 horas, padrão 2 horas) substitui o fallback de domínio de 15 minutos. SKIPPED não recebe follow-up. Reconciliação offline registra doses expiradas sem som e sem disparar uma sequência de follow-ups antigos.

## Contrato de persistência

Todos os comandos que dependem de leitura seguida de escrita usam `RoomDatabase.withTransaction`. Há índice único de ocorrência; `INSERT IGNORE` permite recuperar a linha vencedora sem `REPLACE` destrutivo. Criação concorrente converge para uma linha. `DoseActionCoordinator` expõe deliver/take/skip/dismiss/timeout/cancel/snooze e retorna `DoseActionResult.Success(occurrence, changed)`, `Rejected(reason)` ou `Failure(error)`. Cancelamento de coroutine é propagado. UI só confirma depois de receber sucesso persistido.

`saveMedicationWithSchedules` grava medicação e slots numa transação, preserva `createdAt`, rejeita horários duplicados, arquiva versões anteriores e devolve `SavedMedication(id, activeSchedules, retiredScheduleIds)`. O consumidor cancela IDs aposentados e agenda horários novos depois do commit. `requestId` opcional gera recibo persistido e torna retry após recriação idempotente. Uma falha ao inserir horário reverte também o cadastro.

Arquivar medicação/horário cancela pendências e preserva logs. Chaves estrangeiras de log usam `SET_NULL` na exclusão física, sem apagar snapshots; `isScheduledDose` conserva elegibilidade sem depender da existência dos pais. Exclusão permanente exige opção explícita `deleteHistory`; manter histórico remove cadastro e slots, preservando as linhas congeladas. Excluir log grava tombstone opaco em `occurrence_suppressions`, para que boot/reconciliação não recriem dados que o usuário excluiu. Nenhuma informação de medicação é guardada no tombstone. Recibos de formulário impedem recriação por retry de um cadastro já excluído.

A adesão compartilhada conta somente conclusões de doses programadas: TAKEN/(TAKEN+MISSED+SKIPPED). Pendentes, canceladas, avulsas e AS_NEEDED não entram no denominador. Exclusão física de um cadastro com histórico mantido não muda a elegibilidade das doses anteriores. Período sem conclusões elegíveis tem percentual `null`, exibido como Sem dados. Um filtro de linhas tomadas não substitui o conjunto completo do período usado no cálculo.

## Migrações e versões conhecidas

O repositório contém schema v1 (`b1cfb38`), v2 (`c0896bd`, adição de cor) e v3 (`66bf96e`, doses avulsas e pais opcionais). Não há prova de publicação dessas versões no Play Console. Foram implementados caminhos preservadores 1→2→3→4, sem fallback destrutivo. A migração 3→4 mantém cada ID de medicação, horário e log; reconstrói snapshots a partir do cadastro disponível, conserva `originalDueAt`, remove `actualTime` de estados não tomados e cria contratos de recorrência sem reinterpretar horários explícitos.

Duplicados legados de um slot são resolvidos por TAKEN > SKIPPED > MISSED > PENDING; entre iguais vence o maior ID. A linha canônica fica elegível. Todas as outras linhas permanecem no banco com seus IDs e snapshots, estado CANCELLED, identidade `legacy-duplicate:<id>` e sem elegibilidade, evitando perda silenciosa e adesão duplicada. O fuso local da migração é registrado ao reconstruir slots antigos, pois o v3 não armazenava o fuso original.

A vigência reconstruída dos horários e o checkpoint de reconciliação começam no momento da migração: o modelo antigo não prova que a posologia atual existiu em cada dia anterior. Logs anteriores conhecidos permanecem no histórico. Depois desse ponto, a reconciliação percorre todo o intervalo conhecido desde o checkpoint/vigência, sem corte arbitrário de sete dias. Retrocesso do relógio não retrocede o checkpoint; enquanto ele estiver no futuro, a reconciliação revisita a vigência conhecida de cada regra, para não presumir que um cadastro recém-criado já foi reconciliado. Chaves locais e tombstones tornam essa revisão idempotente; ocorrências capturadas mantêm suas identidades.

Schema v4 exportado em `app/schemas/com.dosecerta.data.local.DoseCertaDatabase/4.json`. Evidências de execução e limitações constam em `docs/qa/workstream-data.md`.
