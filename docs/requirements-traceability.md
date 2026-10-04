# Requisitos e critérios — v1.0

Fonte normativa local: REQUISITOS - DOSE CERTA.md; justificativa; pedido atual de concluir TASKS.md. Relatório v0.5 descreve escolhas de intervalo 4/6/8/12 h, mas RF-002 exige também semanal, mensal e dias específicos. Nenhuma descrição anterior prova funcionamento. Não há prescrição, recomendação de dose nem cálculo clínico no escopo.

| Requisito | Critério concreto | Tarefas / evidência exigida |
|---|---|---|
| RF-001 cadastro | Nome, dose, unidade, forma, notas, cor; commit atômico e erro recuperável | D06, U05–U07, E03 |
| RF-002 posologia | Múltiplos horários; diária/intervalo/semanal/mensal/dias selecionados/PRN; prévia e versões | F01–F12, D03, U05 |
| RF-003 alarme | Evento real do AlarmManager, áudio finito, snooze persistido, concessões efetivas | A01–A24, E04, V04 |
| RF-004 confirmar/pular | Ações distintas, idempotentes, persistidas; acesso sem gesto obrigatório | D02–D05, A14–A20, V05 |
| RF-005 histórico | Snapshots estáveis; status/horário editáveis explicitamente; arquivo preservado | D07,D09,D10,U11,R01–R12,V03 |
| RF-006 editar/excluir | Arquivar preserva; exclusão permanente informa opção de histórico e impede recriação | D06–D08,U10,V05 |
| RF-007 lista | Hoje e próxima data futura; PRN separado; semanal/mensal sem dose hoje visíveis | F10,U08,U10 |
| RNF usabilidade | 320/360/412/≥600 dp, fonte100/130/200%, rotação, leitor e Switch Access | U01–U19,V02,V08 |
| RNF feedback | Confirmação só após commit; erro e retry; sem confirmação de concessão negada | D05,U07,A03,R11,V05 |
| RNF resposta | Cold start e comandos <2 s no aparelho definido com10.000 registros; export assíncrono | B03,V07; duração e ambiente reais |
| RNF confiabilidade | Boot/processo morto/Doze/DND/revogação/fuso; nenhuma dose duplicada | A21–A24,V01,V04,E09 |
| RNF privacidade | Sem upload/analytics; sandbox/FBE auditados, backup excluído; export informado | P03–P06,S01–S06 condicionais |
| RNF compatibilidade | min26; target36 mínimo atual, matriz por API,16 KB binário real | B04–B07,V01,V06 |
| RNF localização | Português integral; inglês com mesmas chaves; datas/fuso concretos | U18,R07,V02 |
| RD-001 linguagem | Termos simples, status explícitos e sem enum cru | U02,U18,R07 |
| RD-002 responsabilidade | Aviso no primeiro uso e Sobre; seguir prescrição profissional | U03,U14,P05 |
| RD-003 transparência | Política clara de armazenamento/export/retenção/exclusão/permissões e contato real | P04–P06,U14 |
| Pedido atual | Todos os itens rastreados, sub-agentes e validação explícita; EXT sem execução fictícia | TASKS.md, docs/qa/workstream-*.md, docs/release/go-no-go.md |

Os testes de código, instrumentação e captura visual comprovam somente os cenários realmente executados. Conta/assinatura/URL pública ainda não definidas pelo responsável. Xiaomi disponível; ligação ADB e sessões reais de usabilidade ainda não confirmadas.
