# ADR — recorrência por calendário e horário civil

Data: 2026-09-29. Implementação: `RecurrenceCalculator`, `Schedule` e o versionamento transacional do repositório.

Posologia é a regra de lembretes informada pelo usuário, sem sugestão de prescrição, troca de dose ou instrução para compensar uma dose perdida. Cada horário explícito tem um slot independente e pode aparecer junto com outros horários da mesma medicação.

| Regra | Campos | Política e exemplo |
| --- | --- | --- |
| Diária | `DAILY`, minuto do dia | 08:00 após 2026-09-29 08:00 → 2026-09-30 08:00. |
| Intervalos do cadastro | `INTERVAL`, horários explícitos | Cada 8 h gera slots civis 00:00/08:00/16:00, editáveis e preservados; não é um cronômetro iniciado na última tomada. |
| Semanal | `WEEKLY`, dia(s), horários | Segunda 08:00 após sábado 2026-10-03 → segunda 2026-10-05 08:00. |
| Dias específicos | `SELECTED_DAYS`, dias 1–7 | Domingo/quarta 08:00 após sábado → domingo 2026-10-04 08:00. |
| Mensal | `MONTHLY`, dia 1–31 | Dia31 após janeiro31 → março31; fevereiro não é substituído pelo último dia. |
| Conforme necessário | `AS_NEEDED` | Nenhuma ocorrência automática; registros avulsos ficam fora da adesão programada. |

Numeração de dias segue o contrato legado de `Calendar`: 1 domingo, 2 segunda, …, 7 sábado. Seleção semanal/específica vazia é erro de formulário e de persistência. Mês é uma unidade de calendário, nunca 30 dias. Dia29 existe em fevereiro bissexto; dias29/30/31 ausentes fazem o mês ser pulado. Virada de ano usa `LocalDate.plusDays`, sem aritmética fixa de milissegundos.

O cálculo injeta `Clock` e fuso de fallback. `zoneId` vazio acompanha o aparelho para slots ainda não capturados; fuso explícito mantém a zona escolhida. Horários de intervalos legados continuam nos minutos apresentados; a migração não converte frequências antigas para regras semanais/mensais nem altera horários passados. Em dias de DST, slots civis podem distar 7 ou 9 horas em termos absolutos para uma lista apresentada como cada8h. O formulário não afirma que os alarmes medem tempo desde a última tomada.

Um horário inexistente no início de DST avança pela duração do salto: 2026-03-08 02:30 em America/New_York torna-se 03:30 (07:30Z). A prévia informa o ajuste; a identidade conserva o slot solicitado 02:30. Uma sobreposição no fim de DST usa o primeiro offset uma única vez: 2026-11-01 01:30 nessa zona produz 05:30Z, sem segunda ocorrência às 06:30Z. `OccurrenceDate` retorna timestamp resolvido, data local resolvida, data local solicitada, fuso, `adjustedForDst` e `isPinned`.

`occurrencesBetween` usa intervalo [início,fim), cortado por `validFrom`/`validUntil`; meia-noite não aparece duas vezes em dois dias adjacentes. `nextOccurrence` retorna apenas uma data estritamente posterior ao instante pedido e encerra a busca no primeiro slot válido. O horizonte interno de371dias cobre todas as regras existentes; não limita o backfill histórico, que percorre o intervalo concreto solicitado. Inativa e PRN não têm próximo alarme.

Ocorrências já capturadas conservam instante original após mudança de hora/fuso. `MedicationRepository.previewOccurrences` inclui essas ocorrências, sinaliza `isPinned`, remove concluídas/canceladas/suprimidas e calcula slots ainda não capturados no fuso atual. O mesmo slot local de uma mesma versão tem a mesma chave, evitando criar segunda dose ao viajar. Snooze tem instante independente e não substitui a próxima recorrência. Editar qualquer versão aposenta slots antigos, preserva conclusões, cancela pendências e cria novas identidades a partir da vigência do commit.

As provas de calendário, DST, migração, concorrência e integração estão registradas em `docs/qa/workstream-data.md`; verificação de fluxo UI pertence às tarefas F07–F12 e não é comprovada apenas pelos testes do domínio.
