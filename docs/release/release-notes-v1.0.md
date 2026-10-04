# Dose Certa 1.0 — notas em preparação

Rascunho em 30/09/2026. A versão ainda não foi publicada e estas notas dependem da validação do candidato descrita em [go-no-go.md](go-no-go.md).

## Texto de loja sugerido — pt-BR

Organize seus medicamentos e horários, acompanhe as próximas doses e registre tomadas ou pulos. Configure lembretes diários, por intervalos, semanais, mensais ou em dias escolhidos; mantenha o uso conforme necessário separado. Consulte o histórico e exporte um relatório PDF do período selecionado. Ajuste as notificações e os detalhes exibidos na tela bloqueada.

O app ajuda a registrar seu tratamento e não substitui orientação profissional. Os lembretes dependem das permissões e das condições do Android.

## Suggested store text — en

Organize medications and schedules, view upcoming doses, and record doses taken or skipped. Set daily, interval, weekly, monthly or selected-day reminders; keep as-needed use separate. Review your history and export a PDF for a selected period. Adjust notifications and the details shown on the lock screen.

The app helps you record your treatment and does not replace professional advice. Reminders depend on permissions and Android conditions.

## Registro técnico do candidato

- Ocorrências com identidade estável, horário original e snapshot de medicação; comandos idempotentes e correção explícita de horário no histórico.
- Recorrência por calendário, com regras documentadas para DST, dias mensais ausentes, fuso e mudanças de relógio.
- Schema 4 com migrações preservadoras, arquivamento, exclusão explícita e supressões para evitar recriar histórico removido.
- Pipeline de alarme por ocorrência, acesso efetivo às permissões, fallback declarado, som finito e reconciliação depois de eventos do sistema.
- Cadastro e edição com prévia, estados salvos, ações explícitas e texto PT/EN; histórico com lista virtualizada e cálculo de adesão compartilhado.
- Exportação PDF por SAF a partir de snapshot do período/filtro, paginação e opção de abrir/compartilhar a URI escolhida.
- Dados privados no aparelho, backup/transfer excluídos e detalhes ocultos na tela bloqueada por padrão, conforme ADR de proteção.

Esses itens descrevem o escopo implementado; não substituem resultados de teste. AAB/hash, versionCode, certificado público, matriz realmente executada, mudanças posteriores e limitações observadas devem ser anexados antes de usar estas notas como registro da release.

## Uso e limites documentados

No primeiro uso, configure as permissões e confira um lembrete de teste. Tela cheia, som e pontualidade dependem das capacidades concedidas, do canal de notificações, do Android e do fabricante. Force-stop interrompe a entrega até reabrir o app, conforme o contrato. Não há promessa de contornar Não Perturbe ou economia de bateria.

PDFs exportados podem conter dados de saúde e permanecer no destino/provedor escolhido pelo usuário. Não há conta de paciente ou upload automático ao desenvolvedor. Dados de versões anteriores só devem ser atualizados depois de validar as migrações correspondentes; não instruir reinstalação como solução de upgrade.
