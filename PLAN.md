# DoseCerta — plano de implementação da versão 1.0

Data da análise: **2026-09-29**. Estado: **planejado; implementação, validação em dispositivos e publicação pendentes**.

Este documento transforma o pedido de lançamento em escopo executável. [TASKS.md](TASKS.md) contém tarefas e dependências; [CONTEXT.md](CONTEXT.md) orienta o agente que executará o trabalho. A entrega desta etapa é documental: não comprova correção do aplicativo nem gravação do fluxo.

## 1. Resultado esperado e limites

Entregar um aplicativo Android offline, em pt-BR e inglês, com cadastro de medicamentos, posologia, lembretes confiáveis dentro das condições permitidas pelo Android, registro consistente de doses, histórico e PDF utilizável. Todas as telas devem passar por estudo e redesenho. A versão candidata deve ter evidências reproduzíveis, gravação autônoma do primeiro uso até o alarme real e pacote pronto para análise da Google Play.

Fontes de requisitos: pedido atual e [REQUISITOS - DOSE CERTA](docs/REQUISITOS%20-%20DOSE%20CERTA.md), apoiados pela [justificativa](docs/JUSTIFICATIVA%20-%20DOSE%20CERTA.md). O [relatório v0.5.0](docs/RELATORIO%20Dose%20Certa%20v0.5.0.md) e o relatório de extensão `.docx` em `docs/` documentam intenção e execução histórica; não comprovam funcionamento atual. README, código e contexto anterior ajudam a identificar lacunas. O DOCX foi lido por extração do texto; imagens e vídeo citado ainda não foram validados. Preservar os originais.

RF-002 inclui semanal, mensal e dias específicos; o relatório antigo registra mudança para intervalos. Como não há alteração formal revogando RF-002, este plano inclui ambos na v1.0. A justificativa menciona sintomas/informações gerais como futuro: permanecem fora da primeira versão. Gratuidade e ausência de marketing são premissas do produto.

Manter Kotlin, XML/ViewBinding, Material Components, MVVM, Room e DataStore. Não incluir backend, conta de usuário, analytics, anúncios, Health Connect, cálculo de prescrição, sincronização ou migração para Compose na v1.0. O app organiza informações fornecidas pelo usuário; não determina tratamentos nem recomenda compensar doses.

Distinguir três marcos: **candidato técnico validado**, **submissão pronta no Console** e **publicação aprovada pela Google**. A aprovação e os prazos externos não podem ser garantidos por testes locais.

## 2. Matriz de requisitos e aceite

| ID | Requisito | Critério de aceite | Tarefas |
|---|---|---|---|
| R01 | Concluir funcionalidades anunciadas | CRUD, doses programadas e avulsas, AS_NEEDED, histórico, filtros, idioma, som e reagendamento funcionam sem perda de dados | B, D, A, U, V |
| R02 | Estudar e redesenhar todas as páginas | Inventário completo, capturas antes/depois, tokens e estados documentados; nenhuma ação inacessível na matriz visual | U01–U19, V02 |
| R03 | Corrigir relatório e PDF | Conteúdo coerente com período/filtros, paginação sem cortes; salvar, abrir e compartilhar URI válida em API 26–36 | D, R01–R12, V03 |
| R04 | Corrigir disparo e tela cheia | Diagnóstico identifica cada etapa; alarme real aparece com acesso concedido; negativa de acesso produz estado e alternativa explícitos | A01–A24, V04 |
| R05 | Adequar alarme ao paciente e à tela bloqueada | Tomei/Pular/Adiar/Silenciar têm efeitos distintos; ações não duplicam doses nem desbloqueiam dados pessoais | D02, A13–A20, U02, V05 |
| R06 | Add Med: “Posologia” | Seção visível de adicionar e editar usa “Posologia” em pt-BR; inglês equivalente; não renomear canais do sistema em massa | U05 |
| R07 | Gravar autonomamente o fluxo completo | Vídeo e execução verificável: instalação limpa → onboarding → cadastro via UI → bloqueio → disparo agendado → card → ação → histórico | E01–E09 |
| R08 | Adequar à Google Play atual | API exigida, permissões justificadas, AAB assinado, declarações, privacidade, conta e testes aplicáveis concluídos | B04–B06, P01–P13, V06 |

### 2.1 Rastreabilidade dos documentos adicionados

| Origem | Cobertura v1.0 / evidência necessária | Tarefas |
|---|---|---|
| RF-001 | Nome, dose, unidade, forma e notas; validação e recuperação de rascunho | D06, U05–U07 |
| RF-002 | Múltiplos horários; diário, semanal, mensal e dias escolhidos, além dos intervalos existentes; próxima data visível | F01–F12 |
| RF-003 | Som selecionável, disparo e soneca preservando identidade | A07–A24, U13 |
| RF-004 | Tomei/Pulei na notificação, inclusive comportamento bloqueado verificado | D05, A13–A20 |
| RF-005 | Histórico completo em lista é suficiente; calendário não é obrigatório (lista ou calendário) | D03–D10, R01–R12, U11 |
| RF-006 | Editar; retirar da lista; exclusão permanente explícita e cancelamento de alarmes, com escolha informada sobre histórico | D06–D08, U10 |
| RF-007 | Home mostra medicamentos e próximos horários, incluindo próxima data semanal/mensal | F10, U08 |
| RNF usabilidade/feedback | Todas as telas acessíveis a idosos; feedback imediato e mensagens compreensíveis | U01–U19, V02 |
| RNF desempenho | Ações usuais respondem em menos de 2 s no aparelho de referência; tarefas longas mostram progresso nesse prazo | B03, V07 |
| RNF confiabilidade | Testes de encerramento do processo/reboot e estados de permissão; documentar limite de force-stop | A21–A24, V04 |
| RNF privacidade/compatibilidade/idioma | Offline, minSdk26, pt-BR integral; inglês existente preservado | P03–P06, U18, V01 |
| RD-001 | Linguagem simples; explicar Posologia por Como e quando tomar, sem recomendação clínica | U02, U05, U18 |
| RD-002 | Aviso no primeiro uso e seção Sobre acessível em Configurações | U03, U14, P05 |
| RD-003 + justificativa | Política transparente, proteção local, criptografia/controle de acesso avaliados tecnicamente e direitos sobre dados | P03–P06, D08 |
| Requisitos subconscientes | Controle, sobriedade, simplicidade, teclado adequado e respeito à atenção; avaliação por tarefas reais | U02, U07, A18, V08 |

Garantia dos alarmes é traduzida em critérios verificáveis sob concessões e condições documentadas do Android, sem promessa de contornar o sistema. Relatório v0.5.0/DOCX afirmam bypass universal de DND, conformidade por delay de serviço e atomicidade; essas afirmações exigem revisão técnica e correção no material de lançamento.

## 3. Diagnóstico inicial e riscos concretos

Análise estática, não reprodução dos defeitos relatados. Referências detalhadas em CONTEXT.md.

| Evidência no código | Consequência / prioridade |
|---|---|
| `app/build.gradle.kts`: compile/target 34; versionCode 1; versionName 1.0 | P0: atualizar plataforma e validar alterações de comportamento; o nome 1.0 não indica prontidão |
| Room v3, `exportSchema=false`, `fallbackToDestructiveMigration()` | P0: atualização de schema pode apagar dados; criar migrações preservando dados |
| `MedicationLog` usa CASCADE para medicamento e horário; edição remove horários | P0: editar/excluir posologia pode apagar histórico; preservar ocorrências e snapshots |
| Serviço cria MISSED no disparo; agendamento do verificador de atraso sem chamada | P0: status de dose incorreto antes de qualquer oportunidade de resposta |
| Snooze e recorrência compartilham identidade de PendingIntent | P0: adiamento substitui próximo disparo; separar identidades |
| Serviço usa notificação 1001; Activity singleTop sem `onNewIntent` | P0: medicamentos simultâneos podem sobrescrever apresentação e ações |
| Cancelar relembrete usa ID diferente daquele usado para publicá-lo e para qualquer serviço ativo | P0: uma notificação pode ficar visível ou interromper outro alarme |
| Full-screen verificado só no onboarding; serviço inicia Activity diretamente e recria canal | P0: caminho sujeito a restrições e configuração real do aparelho; causa específica exige evidência |
| PDF pagina por dia inteiro, resumo limita quantidade; API 26–28 usa `file://` | P0: cortes e abertura inválida; geração e destino precisam correção |
| Exportação usa `logs.value` filtrado por status e título só com período | P0: relatório pode representar recorte como se fosse total; denominador incorreto |
| `allowBackup=true`, XML de backup com estrutura de FileProvider e exclusões incompletas | P0: política de armazenamento local não está demonstrada pela configuração |
| Recursos possuem medidas fixas, cores locais e strings incompletas em inglês | P1: redesenho, acessibilidade e consistência necessários |

A presença simultânea de `SCHEDULE_EXACT_ALARM` e `USE_EXACT_ALARM` **não comprova conflito de execução**. Tratar como decisão de elegibilidade e simplificação do manifesto; localizar a falha usando permissões efetivas, canais, scheduler, receiver, serviço, PendingIntent e estado de bloqueio.

## 4. Sequência de execução e dependências

1. **M0 — baseline e requisitos:** inventariar telas, capturar defeitos, registrar SDK/aparelho/manifesto final e confirmar acesso à conta de publicação. Preservar alterações locais existentes.
2. **M1 — fundações:** contrato das doses, schema/migrações, permissões e compatibilidade API 36. Estudo visual e preparação do Console podem ocorrer em paralelo.
3. **M2 — correções funcionais:** scheduler, serviço, notificações e ações; consulta consistente do histórico e renderização/exportação PDF. Integrar pela identidade única da ocorrência.
4. **M3 — redesenho aplicado:** implementar tokens e todas as telas, com acessibilidade, estados e tradução. Design pode começar em M0, mas a interface do alarme depende do contrato M1.
5. **M4 — validação e gravação:** testes da matriz, correções de regressões, vídeos autônomos e evidências do mesmo candidato.
6. **M5 — prontidão de distribuição:** AAB assinado, instalação pela faixa de teste, políticas e fichas revisadas, critérios de saída concluídos. Submissão/publicação exige conta habilitada e ação do responsável quando o acesso não estiver disponível.

Caminho crítico: B01 → B04/B05 → D01–D10/F01–F12 → A07–A24 → V04/V05 → E07 → P09–P13. PDF e UI podem ser implementados em paralelo após fixar contratos de dados. Tarefas de Console iniciam cedo porque verificação organizacional e testes externos podem demorar.

## 5. Dados e regras funcionais da v1.0

### 5.1 Contrato proposto, a registrar antes de alterar o schema

Separar **medicamento**, **versão da posologia**, **ocorrência programada** e **evento de ação**. Uma ocorrência tem identidade persistente, horário original imutável, horário de próximo alerta independente, snapshot do nome/dose/unidade e resultado. Implementar por extensão do modelo atual e/ou entidade `DoseOccurrence`, escolhida em D01; a API para o restante do app será baseada em `occurrenceId`, sem recalcular “hoje” para encontrar doses antigas.

Persistir ocorrências ao agendar; guardar vigência das versões de posologia para reconciliar períodos sem execução do app. Fazer reconciliação em lotes e sem tocar em eventos já encerrados. Não criar suposto histórico anterior ao início conhecido do acompanhamento. Logs migrados devem conservar seus dados; metadados reconstruídos da medicação atual devem ter procedência distinguível, pois informação histórica já perdida não pode ser recuperada com certeza.

Unicidade no banco e transação impedem dois resultados para a mesma ocorrência. Ações da Home, Activity, notificação, relembrete e histórico usam serviço de domínio comum. Resultado repetido é idempotente; comando atrasado não substitui confirmação recente. `actualTime` representa apenas tomada; registrar separadamente horário da ação de pular/fechar se necessário.

Edição altera somente ocorrências futuras elegíveis, cancela alarmes antigos e agenda após commit. Retirar medicamento da rotina arquiva seus dados e preserva histórico. Exclusão definitiva (RF-006) é ação separada, com confirmação e escolha explícita sobre apagar também o histórico; preservar snapshots ao excluir somente o cadastro. Excluir registro no histórico continua possível com confirmação. Rotinas automáticas não devem recriar um registro explicitamente removido sem regra documentada.

### 5.2 Estados e interação do paciente — proposta de produto

Estudar perfis com baixa visão, dificuldade motora, múltiplos medicamentos e uso sonolento. Registrar hipóteses e realizar avaliação heurística; validação com pacientes/cuidadores é uma atividade separada, sem inventar entrevistas ou endosso clínico.

| Evento | Resultado persistido | Efeito no alerta |
|---|---|---|
| Horário chegou | PENDING / aguardando confirmação | Toca e apresenta notificação/card conforme capacidade |
| “Tomei” confirmado | TAKEN e horário efetivo | Encerra apenas esta ocorrência e seus relembretes |
| “Pular” confirmado | SKIPPED, sem horário de tomada | Encerra apenas esta ocorrência |
| “Adiar” | Mesma ocorrência; resultado ainda pendente | Novo alerta em 5/10/15/30/60 min; padrão 10; recorrência preservada |
| “Silenciar/Fechar” ou remoção da notificação | Não equivale a tomada nem a pular | Encerra som/apresentação desta ocorrência; segue política de não confirmação |
| Sem resposta após tolerância | MISSED; exibir texto como “Não confirmada” | Pode gerar relembrete conforme preferência |
| Voltar/desligar tela | Nenhum resultado implícito | Comportamento de apresentação previsível; não prender usuário no card |

Proposta inicial de tolerância: 30 minutos, herdada da intenção do código; é regra de interface, não orientação de horário clínico. Adiamento aceito estende a tolerância até pelo menos 30 minutos após o novo alerta. Persistir prazo para sobreviver a reinício. Limitar duração de som (proposta: 60 segundos por acionamento) e manter sinalização silenciosa até ação/prazo. Registrar estas decisões em D02 e validar na avaliação de usabilidade.

Ações deliberadas devem funcionar com tela bloqueada quando o sistema permitir. Fornecer botões acessíveis como alternativa a swipe, gesto de segurar e timeout curto. “Pular” e “Tomei” devem exigir ação inequívoca sem depender exclusivamente de arraste. Silenciar e adiar podem funcionar sem desbloqueio; confirmar tomada ou pulo requer identificar a medicação, por desbloqueio ou escolha explícita de mostrar detalhes bloqueados. Consultar configurações do sistema e nunca contornar PIN/biometria.

Por padrão, ocultar nome/dose na superfície bloqueada; permitir escolha explícita de mostrar detalhes. Card privado genérico oferece Silenciar/Adiar; não permite tomada/pulo de uma medicação cuja identidade não pôde ser conferida, especialmente com ocorrências simultâneas. A Activity também deve respeitar isso: `VISIBILITY_PRIVATE` sozinho só protege a notificação. Abrir histórico/configurações a partir do card requer desbloqueio. Dismissibilidade de notificações depende da versão/OEM: não prometer notificação irremovível nem reabrir em loop após o usuário fechar.

### 5.3 Posologia e estatísticas

Preservar DAILY, intervalos de 4/6/8/12 h e AS_NEEDED e implementar semanal, mensal e dias específicos (RF-002). Representar regra de recorrência separadamente da quantidade de tomadas ao dia. Semanal escolhe dia(s) da semana; mensal escolhe dia(s) do mês; dias específicos escolhe subconjunto semanal. Exibir prévia das próximas datas antes de salvar.

Para dia mensal 29/30/31 inexistente, proposta: não deslocar automaticamente para outro dia; mostrar que aquele mês não contém ocorrência. Testar fevereiro, bissextos e virada de ano. Não interpretar mês como 30 dias. Para horário inexistente/ambíguo em mudança de fuso, documentar normalização e mostrar próxima ocorrência sem duplicação. Fixar comportamento em F01, sem inventar orientação de tratamento.

AS_NEEDED não cria horários, alarmes ou denominador de adesão; doses avulsas são registros explícitos, não recomendações. Documentar se intervalos representam horários locais gerados ou tempo decorrido; padrão v1 proposto: horários locais visíveis ao usuário, sem ajuste automático de tratamento por fuso.

Mostrar percentual apenas com base definida: tomadas programadas / ocorrências programadas encerradas no período (TAKEN + SKIPPED + MISSED); excluir avulsas e pendentes. Período sem denominador mostra “Sem dados”, não 100%. Filtro por status filtra linhas, mas não fabrica adesão de 100% ao selecionar “Tomadas”; resumo usa a base completa do período e informa o recorte. Compartilhar a mesma regra em Home, Histórico e PDF.

## 6. Estudo e correção de alarmes e permissões

### 6.1 Matriz inicial do manifesto

| Permissão | Situação atual | Decisão planejada |
|---|---|---|
| POST_NOTIFICATIONS | Declarada; solicitada no setup e MainActivity | Centralizar solicitação contextual API 33+, tratar negativa/revogação e canal bloqueado |
| SCHEDULE_EXACT_ALARM | Declarada; `canScheduleExactAlarms()` e acesso especial | Caminho padrão proposto para agendamento exato com concessão do usuário |
| USE_EXACT_ALARM | Declarada junto da anterior | Remover no caminho padrão; manter somente com elegibilidade específica demonstrada e estratégia alternativa documentada |
| USE_FULL_SCREEN_INTENT | Declarada; checagem só no setup | Verificar API 34+, acesso especial e elegibilidade Play; oferecer heads-up quando indisponível e notificações/canal permitirem |
| FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK | Serviço `mediaPlayback` reproduz som | Confirmar tipo correspondente à implementação real; duração limitada; declaração no Console |
| RECEIVE_BOOT_COMPLETED | Receiver presente | Reconciliar e reagendar; não tocar áudio diretamente no boot |
| VIBRATE / WAKE_LOCK | Som/vibração e wake lock usados | Manter somente usos necessários, liberação garantida e duração limitada |
| SYSTEM_ALERT_WINDOW | Declarada; uso de overlay não identificado | Remover; não é requisito de full-screen intent |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Declarada; fluxo correspondente não identificado | Remover da solução padrão; orientação contextual por fabricante sem concessão forçada |
| WRITE_EXTERNAL_STORAGE maxSdk 28 | Usada para exportação antiga | Remover ao migrar exportação para SAF |

As regras de exact alarms distinguem concessão do usuário e elegibilidade restrita de `USE_EXACT_ALARM`; revalidar capacidade ao retornar das configurações e ao reagendar. [Android: exact alarms](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms) e [Google Play: permissões restritas](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en).

Full-screen intent e alarmes exatos são capacidades independentes. Acesso a FSI no Android 14+ e concessão automática pela Play têm critérios próprios; justificar a função central de alarme do DoseCerta, sem afirmar aprovação antecipada. [FSI e foreground services na Play](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en) e [Android: limites de FSI](https://source.android.com/docs/core/permissions/fsi-limits).

### 6.2 Diagnóstico reproduzível

Para cada reprodução, registrar versão/build, API/OEM, bloqueio seguro, horário/fuso, permissões reais, importância do canal, modo DND/bateria e identidade da ocorrência. Capturar `dumpsys package`, `dumpsys alarm`, `dumpsys notification`, estado da Activity e logcat filtrado. Usar dados sintéticos; não gravar nomes de medicamentos reais nos logs de produção.

Verificar em ordem: persistência da posologia → ocorrência e timestamp futuro → PendingIntent correto no AlarmManager → entrada no receiver → promoção do serviço → publicação no canal → entrega de FSI → Activity com ocorrência correta → ação e encerramento. Reproduzir com app aberto, segundo plano, processo removido, tela apagada e bloqueio seguro. “Forçar parada” não é equivalente a remover dos recentes: registrar limitação do Android e recuperação ao reabrir.

### 6.3 Implementação alvo

Criar `AlarmCapabilityChecker`, estado de saúde dos lembretes em Configurações e UI de permissão compartilhada com onboarding. Nunca informar “alarme exato ativo” ao usar fallback inexato. Rechecar em `onResume`, antes de agendar e antes de apresentar FSI; tratar corrida de revogação com erro explícito e alternativa suportada.

Usar notificação de alta importância, categoria alarme e PendingIntent explícito/imutável. Criar canal uma vez e respeitar alterações do usuário; mudança de canal exige migração intencional, não apagar/recriar em cada toque. Som deve ter um único responsável para não tocar simultaneamente pelo canal e serviço. Não prometer bypass de DND por `setBypassDnd(true)`.

Remover a tentativa tardia e indiscriminada de `startActivity` do serviço; priorizar caminho oficial de FSI/notificação e validar regras de background activity launch e PendingIntent do target novo. Se dispositivo estiver em uso, heads-up pode ser o resultado esperado. [Restrições de início de Activity](https://developer.android.com/guide/components/activities/secure-bal).

Gerenciar fila de ocorrências simultâneas, com IDs de notificação/ações distintos e Activity atualizada por `onNewIntent`. Silenciar uma ocorrência não encerra outra. Rejeitar eventos inativos, antigos ou concluídos antes de iniciar áudio/card; confirmação na Home/Histórico encerra imediatamente o alerta correspondente. Serviço deve possuir scopes e timers canceláveis, cancelados em stop/destroy; persistência tardia não pode ressuscitar o card, e falha de gravação não pode exibir sucesso.

Fallback inexato não herda automaticamente a exceção de início de FGS do alarme exato. Tratar recusa de FGS por caminho permitido de notificação. Heads-up requer POST e canal habilitados; se também indisponíveis, exibir estado degradado dentro do app. Reboot, atualização do pacote, mudança de hora/fuso e concessão de acesso devem reconciliar estado. Receivers usam trabalho limitado com `goAsync()`/`finally { finish() }` quando necessário; evitar `runBlocking` prolongado na thread principal. Boot não pode iniciar diretamente `mediaPlayback` FGS em apps target 35+. [Tipos de foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types).

## 7. Relatório e exportação PDF

1. Definir `ReportRequest` com intervalo concreto, fuso, idioma e filtro; consultar snapshot consistente do banco em vez de ler `StateFlow` possivelmente desatualizado. Períodos 7/30 dias usam datas locais inclusivas apresentadas; consultas usam limites bem definidos, preferencialmente início inclusivo/fim exclusivo.
2. Separar consulta/métricas, renderização e gravação. ViewModel recebe abstração de exportação com application context onde necessário; não retém Activity/Fragment.
3. Usar `ActivityResultContracts.CreateDocument("application/pdf")` e `ContentResolver` em todas as APIs suportadas. Usuário escolhe o destino, inclusive Downloads; cancelamento é estado normal. SAF evita permissão ampla de armazenamento. [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files).
4. Se compartilhar antes de salvar, criar PDF em cache e FileProvider com caminho restrito a relatórios; configurar XML próprio. Para abrir/compartilhar usar `content://`, MIME correto, grants temporários e tratar ausência de leitor. Não reutilizar arquivo de regras de backup como paths de FileProvider.
5. Paginar por linhas medidas, incluindo continuação de um mesmo dia e do resumo. Repetir cabeçalhos, numerar páginas e quebrar nomes/notas longos sem perder texto relevante. Listar “Medicamentos no período”, não inferir “ativos” a partir de logs.
6. Mostrar intervalo, data de geração, filtros, contagens, horário previsto/efetivo, status, doses avulsas separadas e origem dos dados. Não apresentar percentual filtrado como adesão global nem conferir validade clínica ao relatório.
7. Fechar `PdfDocument` e streams em todos os caminhos; remover temporários e tratar destino sem espaço/permissão. Falha não pode virar sucesso com arquivo vazio; destino parcial deve ser removido quando o provider permitir, informando impossibilidade quando necessário.
8. Validar conjuntos com 0, 1, 30 doses no mesmo dia, muitos medicamentos, 10.000 registros, acentos, nomes extensos, dose avulsa, medicação arquivada e limites de datas. Conferir texto extraído, renderização e abertura real, inclusive API 26/28 e 36.

## 8. Estudo visual e redesenho de todas as telas

Produzir `docs/design/screen-audit.md` com imagem atual, objetivo do paciente, problemas, nova hierarquia e estados por superfície. Depois, capturas propostas no próprio app e especificação em `docs/design/ui-spec.md`. Não exigir Figma ou imagens geradas para executar o redesenho XML.

Direção inicial: preservar identidade verde/azul, simplificar decoração, dar prioridade ao próximo medicamento e à ação principal, reduzir altura sem reduzir área de toque. Centralizar espaçamentos (4/8/12/16/24/32 dp), tipografia em sp, cores semânticas de claro/escuro e estilos de cards/campos/botões. Valores são ponto de partida para avaliação, não proporção fixa de tela.

| Superfície | Arquivos-base | Trabalho obrigatório |
|---|---|---|
| Estrutura principal e navegação | `activity_main.xml`, `MainActivity` | Insets, teclado, bottom navigation, estado restaurado e retorno |
| Termos/primeiro uso | `fragment_setup_terms.xml`, `activity_setup.xml` | Leitura, consentimento, acesso à política, layout pequeno/grande |
| Permissões | `fragment_setup_notifications.xml` | Explicar cada acesso, recusar e corrigir depois, estado atualizado |
| Tutorial | `fragment_setup_tutorial.xml`, `layout_tutorial_overlay.xml`, itens | Resolver caminhos duplicados; um fluxo acessível e retomável |
| Home | `fragment_home.xml`, `item_schedule.xml`, `item_as_needed.xml` | Próxima dose, estados, progresso coerente, avulsos e lista vazia |
| Medicamentos | `fragment_medications.xml`, `item_medication.xml` | Busca, lista vazia/sem resultado, nomes longos, editar/arquivar |
| Adicionar/editar | `fragment_add_medication.xml`, `list_item_schedule_time.xml` | Seção “Posologia”, agrupamento de frequência/horários, erros e teclado |
| Histórico/relatório | `fragment_history.xml`, `item_medication_log.xml` | Período/filtro claros, estados, métricas e exportação |
| Configurações | `fragment_settings.xml` | Idioma/tema/som/relembrete e diagnóstico de permissões |
| Privacidade | `fragment_privacy_policy.xml` | Leitura, versão, contato, permissões e backup coerentes |
| Card de alarme | `activity_alarm.xml`, `SwipeToConfirmView` | Legibilidade bloqueado, privacidade, botões acessíveis, concorrência |
| Diálogos e estados transitórios | Extra dose, exclusão, edição de log, seletores de hora/cor/unidade/idioma/som | Mesmo sistema visual, foco, cancelamento e mensagens claras |

Validar 320/360/412 dp, janela ≥600 dp, teclado aberto, rotação/multiwindow, claro/escuro, pt-BR/en e escala de fonte 1,0/1,3/2,0. Metas do produto: alvos ≥48 dp, texto comum com contraste ≥4,5:1, texto grande ≥3:1, estado nunca indicado apenas por cor, TalkBack e Switch Access nas ações centrais. [Acessibilidade Android Views](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views).

API 36 exige revisão de edge-to-edge, navegação de retorno e layouts grandes; bloqueio em portrait no manifesto não substitui suporte a redimensionamento. [Mudanças ao usar target Android 16](https://developer.android.com/about/versions/16/behavior-changes-16).

O texto atual da seção Add Med é **“Lembretes”** (`add_med_reminders`), apesar de o pedido mencionar “alarmes”. Alterar a seção correta para **“Posologia”**, mantendo “Horários” como rótulo específico quando necessário; inglês sugerido: “Dosage schedule”.

## 9. Validação e gravação autônoma

### 9.1 Matriz mínima

| Área | Cobertura obrigatória |
|---|---|
| Android | API 26, 28/29 (PDF), 31/32 (exact), 33 (POST), 34 (FSI), 35 e 36; smoke na estável mais recente disponível |
| Aparelhos | Emulador AOSP/Google e pelo menos Samsung e Xiaomi/HyperOS reais quando disponíveis; ausência fica registrada, nunca simulada como aprovação |
| Alarmes | Foreground/background, tela apagada, PIN, permissão negada/revogada, canal silenciado, FSI negado, DND, economia, Doze, reboot e alteração de hora/fuso |
| Concorrência | Dois medicamentos no mesmo minuto, toque duplo, ação atrasada, ação no histórico durante alarme, snooze atravessando meia-noite e próximo evento |
| Persistência | Upgrade da v3, edições/arquivo sem apagar histórico, processo morto, interrupção durante gravação e reconciliação |
| Produto | Cadastro/edição de todas as frequências, AS_NEEDED, avulsas, unidades personalizadas, exclusão explícita, idiomas e configurações |
| PDF | Matriz da seção 7, cancelar picker, nenhum leitor instalado, falta de espaço, grande volume e arquivo aberto fora do app |
| UI | Matriz da seção 8, leitores de tela, teclado, estados vazios/carregando/erro e navegação |

Testes locais: regras de tempo/estado/identidade/métricas; Room instrumentado: transações, unicidade, migrações e retenção; UI instrumentada: Espresso/UI Automator para app e telas do sistema. Não substituir teste do sistema Android por mocks de permissões. Resultado sem execução em hardware deve ser marcado pendente.

### 9.2 Roteiro autônomo e artefatos

Criar `scripts/record-first-run.sh`, cenário UI Automator e `docs/qa/recording-runbook.md`. Usar AVD dedicado **DoseCerta_QA_API_36**, serial explícito e dados sintéticos. O AVD existente `Medium_Phone_API_36.1` não deve ser limpo indiscriminadamente. Reset, uninstall ou `pm clear` somente no ambiente de teste criado para isso.

Roteiro: iniciar captura antes do primeiro lançamento → abrir app limpo → aceitar termos → conceder acessos pela UI real → concluir tutorial → cadastrar “Medicamento de demonstração”, dose fictícia, horário arredondado ≥2 minutos no futuro → confirmar item na Home → bloquear tela sem forçar parada → aguardar disparo do AlarmManager → capturar card → acionar “Tomei” → abrir histórico e verificar uma única tomada. Capturar PDF desse registro em cenário complementar. Separar execuções de Pular, Adiar, Silenciar e permissões negadas.

Automação usa resource IDs/descrições e waits com prazo, sem coordenadas fixas ou broadcasts que simulem o disparo principal. Falha gera captura de tela, XML da UI e logs, encerrando com código não zero. A reexecução parte de estado controlado. Medir diferença entre horário previsto, receiver e card. Meta QA sob permissões e condições nominais: 10 disparos consecutivos sem perda e atraso observado de até 10 s; resultado é critério de ensaio, não garantia absoluta em qualquer aparelho.

Guardar por execução: MP4, screenshots, logcat redigido, dumpsys, relatório JUnit/HTML, amostra PDF, build SHA/versionCode, serial anonimizado, API/OEM, fuso, concessões e timestamps em `artifacts/qa/<run-id>/`. Incluir manifesto/checksums e resultado das asserções. Artefatos volumosos ficam fora do Git; versionar scripts e índice de evidências.

`adb screenrecord` tem limites de duração e não captura áudio. Usar segmentos contínuos com timestamps quando o fluxo ultrapassar o limite, preservando originais; não apresentar edição como take único. Se som/vibração forem critérios, anexar evidência complementar por captura do host ou aparelho físico. Não enfraquecer bloqueio seguro para obter vídeo; se a superfície não for capturável, registrar limite e usar captura externa. [ADB: gravação de tela](https://developer.android.com/tools/adb#screenrecord).

## 10. Google Play — verificação em 2026-09-29

Políticas e Console devem ser conferidos novamente no dia da submissão. Links são fontes oficiais consultadas; elegibilidade específica da conta e do app continua pendente.

| Tema | Regra/verificação e ação |
|---|---|
| Target API | Novos apps e atualizações de telefone exigem **API 36 desde 31/08/2026**. Atualizar compile/target para ≥36; não planejar extensão como concedida. [Política de target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-gb) |
| Conta de desenvolvedor | Apps de saúde abrangidos pela política precisam de conta Organization. DoseCerta se enquadra provisoriamente como gestão de medicação/tratamento; confirmar categoria, entidade, D-U-N-S e verificação no início. [Requisitos do Console](https://support.google.com/googleplay/android-developer/answer/10788890?hl=en) |
| Registro no Brasil | Marco de verificação do desenvolvedor/registro de pacote em **30/09/2026** para lojas participantes em dispositivos certificados. Conferir situação real de `com.dosecerta`, não presumir registro automático. [Verificação Android](https://developer.android.com/developer-verification) |
| Health apps | Preencher declaração de app de saúde, revisar alegações e informar que lembretes/registros não substituem orientação profissional. Conferir categoria Medication and Treatment Management, sem alegar dispositivo médico certificado. [Política de saúde](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en), [categorias](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en) |
| Privacidade/Data safety | Inventariar dados, SDKs, backup, exportação, permissões e contato. Política pública acessível e dentro do app; declaração consistente com comportamento real. Processamento somente local não equivale automaticamente a coleta pelo desenvolvedor. [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) |
| Contas de usuários | App atual não cria conta. Documentar inaplicabilidade do fluxo de exclusão de conta; preservar controle de dados locais. Se contas forem introduzidas, reavaliar requisitos. [Exclusão de contas](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en) |
| Alarmes/FSI/FGS | Minimizar manifesto e completar declarações aplicáveis com vídeo do uso real; decisão de elegibilidade não se resume a ter permissão no XML. [Declarações de FSI/FGS](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en) |
| Binário | Produzir Android App Bundle, assinatura de upload protegida, Play App Signing, versionCode monotônico e instalação pela faixa de teste. [Preparar lançamento](https://developer.android.com/studio/publish/preparing) |
| 16 KB | Validar suporte no candidato e bibliotecas transitivas. A página atual exige suporte API35+ em 64 bits e informa bloqueio de atualizações incompatíveis em **01/02/2027**. Conferir AAB por `.so`; testar imagem 16 KB. [Page sizes](https://developer.android.com/guide/practices/page-sizes) |
| Testes de conta pessoal | Apenas quando aplicável à conta habilitada: contas pessoais novas abrangidas exigem 12 participantes por 14 dias contínuos e solicitação de acesso à produção. Não substitui o requisito de conta Organization quando aplicável. [Testes para novas contas pessoais](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en) |
| Ficha e revisão | Preparar nome/descrições pt-BR/en, ícone/feature graphic/screenshots atuais, contato, público-alvo, classificação, declarações de anúncios/acesso e instruções ao revisor. Conferir dimensões/limites no Console antes de exportar. [Criar/configurar app](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en) |

Corrigir regras de backup antes de afirmar “dados exclusivamente locais”: excluir banco e arquivos DataStore nos formatos aplicáveis, definir transferência entre dispositivos e testar restauração. Explicar que PDFs salvos/compartilhados pelo usuário podem continuar fora do app após desinstalação. Não declarar conformidade LGPD como fato comprovado por usar Room; revisar texto e responsabilidades com o responsável pelo produto.

Auditar bibliotecas, permissões do **manifesto mesclado de release**, componentes exportados, logs, caminhos do FileProvider e dependências nativas. README cita GPL-3.0, mas arquivo LICENSE não foi encontrado: confirmar licença, autoria e avisos de dependências antes da distribuição.

## 11. Critérios de saída e pendências externas

**Pronto tecnicamente:** todas as tarefas P0/P1 pertinentes concluídas, nenhum defeito aberto de perda/duplicação de dose ou dados, build/lint/testes pertinentes aprovados, migração validada, matriz visual completa, PDF validado, alarmes com evidências e gravação reproduzível. Incluir desempenho observado e falhas conhecidas; não marcar teste ausente como passado.

**Pronto para submissão:** candidato assinado e identificado, pacote e conta verificados, declarações coerentes, política pública real, materiais de loja, evidência da instalação pela Play e requisitos de teste da conta atendidos. Revisão pré-lançamento sem bloqueio conhecido. Publicar somente o mesmo artefato validado; qualquer alteração relevante exige repetir testes afetados.

**Privacidade e criptografia (justificativa):** P03 registra ameaças, proteção efetiva do sandbox/criptografia do dispositivo e acesso a banco/DataStore/PDF. Room padrão não criptografa o banco por conta própria. Se a proteção exigida não for atendida pela criptografia do dispositivo validada, implementar banco criptografado com biblioteca mantida e chave protegida por Android Keystore, incluindo migração, recuperação e teste 16 KB nas tarefas condicionais S01–S06; não marcar a exigência atendida por mera declaração. Permitir alarmes após primeiro desbloqueio sem exigir biometria a cada leitura. PDFs exportados são cópias escolhidas pelo usuário, fora da proteção do banco. Avaliar impacto nos alarmes e nos dados existentes antes de escolher biblioteca.

**Desempenho (RNF):** medir cold start e interações usuais em aparelho de referência documentado, com base de 10.000 registros. Resposta/feedback em menos de 2 s; operações demoradas, como relatório grande, assíncronas com progresso nesse prazo. Registrar duração total de PDF e consumo de bateria em ensaio controlado de 24 h com/sem lembretes; investigar wake locks vazados, CPU contínua e regressões. Esse limite de resposta da UI é distinto do atraso de disparo.

**Dependências do responsável:** conta/organização e acessos, contato público e URL da política, identidade do pacote/assinatura já usada (se houver), disponibilidade de Samsung/Xiaomi e participantes reais de usabilidade/testes. Continuar trabalho local sem aguardar esses itens; apenas os gates correspondentes ficam pendentes. Credenciais, PINs pessoais e chaves privadas não devem ir para os documentos ou Git. Confirmar distribuição gratuita prevista na justificativa.

**Decisões iniciais revisáveis:** solução padrão SCHEDULE_EXACT_ALARM; PDF via seletor SAF; histórico preservado ao arquivar; conteúdo bloqueado privado por padrão; tolerância 30 min e som 60 s como hipóteses de UX. Registrar mudança com motivo, evidência e tarefas afetadas. Não expandir o app para prescrição clínica para resolver essas decisões.

Preparar procedimento de hotfix com novo versionCode, preservação de schema e interrupção de distribuição quando necessário. Não prometer downgrade automático de banco nem rollout percentual para primeiro lançamento sem verificar suporte da faixa no Console.
