# Auditoria das telas

Inventário baseado no PLAN e no código atual. “Estático” significa inspeção de fonte; não é um defeito observado em aparelho. A captura enviada pelo usuário mostra o launcher do emulador API36.1 no Android Studio, sem tela DoseCerta; ela comprova disponibilidade daquele emulador, sem comprovar fluxo do app. O AVD pessoal existente não deve ser limpo para obter um primeiro uso.

| Superfície | Estados a capturar | Riscos estáticos encontrados no baseline | Mudança implementada / responsável |
|---|---|---|---|
| Primeiro uso / termos | leitura, consentimento desmarcado/marcado, fonte200%, voltar | 11sp, política em caixa de160dp; alegações absolutas de LGPD/privacidade; aviso RD-002 ausente | rolagem única, corpo legível, política coerente com exportação, aviso médico / UI |
| Acessos de lembrete | concedido, negado, retorno sem conceder, revogado, canal bloqueado | prompts concorrentes, retorno declarado sucesso, overlay/bateria como requisito | capacidades efetivas e botões independentes / alarmes |
| Tutorial | três etapas, rotação, retomada, concluir | sequência ativa e overlay legado concorrentes; botão40dp e texto branco fixo | uma sequência, progresso textual, scroll, etapa persistida / UI + Main |
| Home | vazio, povoado, próxima semana/mês, PRN, TAKEN/SKIPPED/MISSED/PENDING |100% em vazio; passagem de horário vira MISSED; layout rígido com gradiente | próxima data, adesão comum, estado textual, datas atualizadas sem escrita / UI |
| Dose avulsa | lista longa/vazia, nome vazio, teclado, confirmar/erro/repetir | lista wrap sem limite efetivo, ação repetível sem confirmação, coroutine de diálogo retida | lista limitada, confirmação e retry, comando idempotente / UI |
| Medicamentos | vazio, sem resultado, busca, filtros, nome longo | vazio/sem resultado iguais; labels hardcoded PT; exclusão CASCADE sem escolha | estados distintos, labels PT/EN, próxima data e ações explícitas / UI + dados |
| Add/Edit | cada regra, seleção vazia, mês31, preview, erros, duplicação, rotação/teclado | três campos em linha estreita; cores excedem largura; título/Salvar sobrepõem; frequência apaga horários; log antigo reescrito | campos empilhados, chips com quebra, Posologia, draft e save transacional / UI + dados |
| Histórico / período | vazio, carga,10.000 logs, filtro, edição, falha | métricas derivadas do filtro; ações apenas long press; contrastes fixos | resumo independente do filtro, ação acessível, lista virtualizada / Main-relatório |
| Exportar PDF | picker/cancelar, progresso, erro, destino, abrir/compartilhar sem leitor | cached logs, arquivo externo legado, falsa mensagem após cancelamento | snapshot, SAF, progresso, URI concedida / Main-relatório |
| Configurações | idioma, tema, som, relembrete, diagnóstico | permissões incongruentes, status estático, alegação de bypass | capacidades efetivas, ações recuperáveis, texto coerente / alarmes |
| Privacidade / Sobre | leitura PT/EN, fonte200%, disclaimer, versão | promessa absoluta de segurança/LGPD; PDF ignorado; contato inexistente | texto atual de dados locais/PDF, aviso médico; contato público pendente / UI + alarmes |
| Card de alarme | bloqueado privado/detalhado, cada comando, rotação, simultâneos | dependência de swipe/hold, ACTION_CANCEL inseguro, extras antigos | botões equivalentes, conteúdo privado, uma ocorrência por comando / alarmes |
| Seletores / confirmações | hora, unidade, forma, cor, idioma, status, arquivo/excluir | ações sem rótulo, foco/cancelamento inconsistentes | controles nativos/Material nomeados, grupos semânticos, cancelamento explícito / respectivos fluxos |

As imagens runtime posteriores devem ser indexadas em `artifacts/qa/<run-id>/` com build, configuração, tela/estado e resultado. `UiFlowInstrumentedTest` gera capturas de recorrências e navegação em `files/qa-ui` do app no AVD dedicado; pull via `adb -s SERIAL exec-out run-as com.dosecerta ...`. Instrumentação não deve ser rodada no aparelho pessoal sem ambiente autorizado. Compile, inspeção de XML e screenshot não comprovam TalkBack/Switch Access ou contraste sem avaliação própria.

A matriz requerida inclui320/360/412dp, ≥600dp, teclado, portrait/landscape/multiwindow, claro/escuro, PT/EN e fontes1/1,3/2. Evidências ausentes permanecem pendentes; não declarar U01/U19 completos apenas por ter criado este inventário. A implementação e os checks executados são registrados em `docs/qa/workstream-ui.md`.

Jornadas heurísticas: uma pessoa com baixa visão precisa ampliar campos sem perder ações; dificuldade motora pede comandos explícitos e alvos48dp; polifarmácia requer identificação do medicamento e próxima data antes da ação; cuidador precisa diferenciar confirmação, pulo, silêncio e pendência e corrigir um registro sem mudar a dose original. Nenhuma sessão com pessoas foi realizada. As hipóteses de30min de tolerância e60s de som precisam revisão com feedback real em V08, sem mudar o significado de uma tomada.
