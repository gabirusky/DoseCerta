# Execução da meta TASKS — 04/10/2026

Pedido: concluir todos os critérios aplicáveis de TASKS.md. A execução inclui implementação, testes e evidências, sem substituir aceites externos por resultados de emulador.

Plano por fase:

1. Screen: conferir checklist, instruções, árvore, estado Git, toolchain e dispositivos. Baseline limpo; Android nativo Kotlin/XML/Room, target 36; nenhuma alteração prévia nesta retomada.
2. Discover: mapear dados/recorrência, scheduler/receiver/serviço/notificação/card, UI/SAF, suites e scripts de evidência. Usar perfis sintéticos separados e preservar AVD pessoal.
3. Trace: seguir os caminhos de cada aceite, executar os testes relevantes, corrigir causas demonstradas e vincular cada conclusão ao resultado real.

Resposta atual do responsável: Xiaomi Redmi 12 disponível; versão do Android e conexão USB autorizada ainda pendentes. `adb devices -l` em 04/10 mostrou somente os AVDs sintéticos. Conta Play, assinatura, contato/URL pública e outros gates externos continuam pendentes. A preferência anterior por emuladores continua vigente. Sem credenciais ou chaves solicitadas.

O emulador 37.1.11 com renderizador software caiu antes de iniciar o aplicativo (exit 139). O emulador instalado 36.4.9 com renderizador host e Vulkan desativado iniciou API 36.1; os resultados do aplicativo são registrados separadamente dos logs desse ambiente. Versão confirmada no source.properties do SDK instalado.

S01–S06 são dispensadas pelo ADR de proteção de dados, salvo mudança explícita do modelo de ameaça. Não há implementação adicional de criptografia alegada.

## Resultados já aprovados

- [Sistema API 36.1](runs/20261004T184119664656Z-system/manifest.json): dois testes sem skip. IME/voltar, recents, rotação/recriação, insets e rascunho; DocumentsUI cancela e salva um PDF legível com URI/grants corretos e abertura em viewer real. B05 e R08 concluídas.
- O primeiro ensaio de sistema falhou porque o IME autocorrigiu a fixture; o teste agora injeta texto exato antes de abrir o teclado real. [Falha preservada](runs/20261004T183624552348Z-system/manifest.json).

- [Interações de alarme API 36.1](runs/20261004T184908439646Z-interactions/manifest.json): quatro testes sem skip. Card atualiza na mesma instância, descarta confirmação antiga e restaura a segunda identidade; ACTION_CANCEL não confirma e ação de acessibilidade clicável funciona em view anexada; ações antigas/repetidas preservam dose encerrada e outro aviso; AlarmManager entrega timeout e follow-up reais com prazo sintético de 5 s. Não equivale a ensaio manual de TalkBack/Switch Access. A13/A15/A17 concluídas.
- Imagens oficiais Google APIs API 28/29/31/33/34/35 instaladas no cache QA do projeto, sem substituir imagens do SDK pessoal. [Instalação](checks/sdk-api-matrix-install-20261004.log).

- [Provider API 36.1](runs/20261004T185508344724Z-provider/manifest.json): dois testes sem skip. Falha ENOSPC ao abrir e depois de aceitar bytes reais; remoção do documento parcial e PDF legível no retry. Provider Java em processo separado do APK de instrumentação, ausente do APK de produção. R09 concluída. O primeiro provider Kotlin não dispunha da stdlib no processo separado do APK de teste; falha preservada, encerrada e corrigida sem alterar bibliotecas de produção.

## Jornada real e relatórios

- [Jornada principal](../evidence/20261004T191139857927Z-main/manifest.json): primeiro uso e permissões por UI, medicamento/posologia pela UI, alarme real com atraso de 20 ms, card privado sob PIN sintético, desbloqueio e tomada única. E02/E03/E04/E06 e privacidade A20 comprovadas no perfil Google API 36.1. E07 continua dependente do candidato assinado V06.
- [Adiar](../evidence/20261004T192705308678Z-snooze/manifest.json) e [Silenciar](../evidence/20261004T193105961139Z-silence/manifest.json) passaram em execuções distintas. Não equivalem a uma tomada.
- [Pular](../evidence/20261004T195725803191Z-skip/manifest.json): alarme real sob PIN, atraso de 18 ms, um único registro SKIPPED com horário original preservado e sem horário de tomada. As quatro ações têm execuções independentes aprovadas; E05 concluída.
- [Dez disparos nominais](../evidence/20261004T193842346704Z-nominal/manifest.json): 10/10 entregas e tomadas únicas, atraso observado entre 9 e 31 ms, bloqueio seguro, horários consecutivos de um minuto. Resultado restrito à imagem Google API 36.1; E09/V04 não comprovadas em Samsung/Xiaomi.
- [POST negado](../evidence/20261004T193504483221Z-denied/manifest.json): negativa pela tela do Android; ocorrência entregue, ausência de áudio iniciado e estado bloqueado informado em Configurações.
- [PDF por UI](../evidence/20261004T193805975166Z-pdf-ui/manifest.json): cancelar, escolher destino real, rotacionar, gravar e abrir o PDF. Esta demonstração usa setup sintético explícito e não substitui primeiro uso do vídeo principal.
- [Estado de exportação API 26](runs/20261004T192835820114Z-export-state/manifest.json): dois testes aprovados. SavedStateHandle restaura escolha/sucesso/interrupção; loading impede segunda abertura; provider lento não bloqueia main; falha parcial permite retry. R11 comprovada com estado real e UI do picker.
- [Texto e IDs API 26](runs/20261004T191943688212Z-pdf/content-audit.json): vazio, 150 doses/50 medicamentos, 10.000 doses e textos longos sem IDs ausentes/duplicados nem palavras fora das margens horizontais. [Integridade contra banco](runs/20261004T192045830178Z-integrity/content-audit.json): filtro/edição/arquivo/avulsas preservam resumo e snapshot.
- [Contraste declarado](checks/semantic-contrast-20261004.json): 54 pares de texto semântico claro/escuro atingem 4,5:1. Não cobre pixels compostos, bordas decorativas ou estados desabilitados.

## Revisão visual da gravação principal

Os segmentos MP4 foram decodificados e inspecionados por mosaicos de quadros a cada segundo em `docs/evidence/20261004T191139857927Z-main/visual-review/`, além das capturas da instrumentação. Observados: consentimento/aviso, escolhas reais de acesso, tutorial único, cadastro e prévia, Home, keyguard, card privado, identificação, confirmação e Histórico. A tela de PIN é protegida pelo Android e não aparece no vídeo; a captura XML/asserção de credencial e o resultado de keyguard seguro documentam esse limite. Sem revisão de áudio: screenrecord não o grava.

O primeiro segmento tem 50,725 s de vídeo codificado dentro de 177,7 s de coleta: o Android deixa de produzir quadros quando o display está apagado. O segundo tem 27,064 s e contém o disparo/ação. Timestamps de coleta e de entrega constam no manifesto; não se alega filmagem contínua da espera com display apagado. Ainda é necessária a revisão do candidato de release para E07.

## Correções de verificação observadas

- O Histórico acrescenta dose/unidade ao nome; a verificação espera a tela de Histórico e a linha com prefixo correto, evitando validar a Home anterior à navegação.
- O teste passou a esperar views visíveis e com tamanho após a emissão assíncrona do fluxo da Home.
- Insets são conferidos em coordenadas globais contra a área útil da janela; API 26 já desconta barras do conteúdo.
- Android 8 DocumentsUI pode restaurar o nome sugerido ao rotacionar. O teste registra a rotação e reinsere o destino pela UI antes de salvar; não afirma preservar texto externo ao app.
- Falhas e novas tentativas permanecem nos manifestos, sem converter falha em aprovação.
- API 31 não possui leitor PDF instalado; o Snackbar real de ausência de leitor expirava durante a coleta de screenshot/XML. O teste passa a verificar o feedback e a ação Compartilhar antes da coleta, sem ampliar ou simular o resultado do aplicativo.
- Imagens Google recém-criadas podem manter `com.google.android.googlesdksetup` como HOME, impedindo o overview real. O preparo de matriz registra `device_provisioned`/`user_setup_complete` e desativa somente esse assistente no AVD sintético verificado, sem conceder acessos do aplicativo.

## Correção de desempenho em validação

A matriz API 33 registrou 3.286,253 ms para 100 previews, acima do limite de 2 s. O caminho de produção repetia a consulta global de pendências por horário. A busca foi limitada pelo índice da posologia e as leituras relacionadas foram reunidas numa transação consistente do Room. A medição mantém volume e limite; a repetição aprovou os testes de dados nas APIs 26/28/29/31/33/34/35. As evidências anteriores identificam o APK anterior por SHA e não são resultados do APK alterado.

O ensaio com base grande observada durante escrita também registrou um crash real de `CursorWindow` no APK anterior: [log](runs/20261004T200607980630Z-safety/logcat.txt). Consultas de listas grandes passaram a usar `@Transaction`, como recomenda a [documentação do Room](https://developer.android.com/reference/androidx/room/Transaction) para refills de cursor durante mudanças concorrentes. Home agrupa logs por posologia e calcula listas fora da thread principal.

- [Regressão API 36](runs/20261004T201722334343Z-history-flow/manifest.json): dois Flows reais leem 10.000 linhas com 512 caracteres de notas por linha, enquanto dez transações excluem 500 linhas. Cada emissão tem IDs únicos, campos íntegros e tamanho coerente com uma transação; estado final 9.500 linhas, sem crash.
- [Ciclo de vida API 36](runs/20261004T201624886947Z-safety/manifest.json): dois testes aprovados. Um AlarmManager real entrega handle antigo depois do arquivamento e não toca; serviço real é destruído enquanto IO está pendente, sem áudio nem republicação quando os workers são liberados. A22 comprovada localmente.
- O segundo teste antigo de safety foi contaminado pela fixture de 10.000 registros e por premissas de tela/posição do diagnóstico. As falhas e o crash continuam preservados; os testes corrigidos identificam registros por timestamp e usam o card real apresentado pelo observador de foreground.

A primeira matriz [26/28/29/31/33/34/35/37](runs/20261004T192614559940Z-api-matrix/manifest.json) terminou com falhas, incluindo desempenho, preparo do launcher, IME, Snackbar transitório e perda de transporte ADB na API 35. Não é matriz aprovada. A repetição congela os APKs por rodada e confirma o IME visível antes de testar Voltar. A imagem API 37 apresentou pressão de memória no primeiro boot; seu resultado inicial não é convertido em sucesso.

- [Cold start API 36](runs/20261004T202223195664Z-performance/manifest.json): cinco processos novos, primeira tela em até 1.016 ms. A fixture principal conserva 10.000 logs/50 medicamentos/100 slots; snapshot 437,375 ms, tomada 4,016 ms e 100 previews 337,068 ms. A medição da primeira tela não inclui todo o carregamento assíncrono da Home, que é observado separadamente por XML e inclui custo de coleta. Caches de arquivos retidos. B03 concluída; V07/24 h permanece pendente.

## Estados de interface e auditoria dos artefatos

- [Superfícies API 36.1](runs/20261004T212309412974Z-surfaces/manifest.json): três testes aprovados, sem skip, em português e fonte 200%. Home vazia; diálogo avulso vazio/erro/IME real/50 medicamentos; confirmação cancelável e toque duplo com exatamente uma tomada; busca sem resultado/nome longo/arquivo/exclusão com histórico; filtro/edição do Histórico, cancelamento de data/hora; relembrete e privacidade persistidos; som inválido recuperado; diagnóstico/Sobre/Privacidade. U09/U10/U13 comprovadas neste escopo.
- Defeitos observados e corrigidos: o texto de lista avulsa vazia tinha altura zero, e a mensagem de busca não mudava quando um StateFlow suprimia uma segunda lista vazia igual. O diálogo agora rola e mantém lista limitada; a explicação da busca observa consulta/filtro independentemente da lista. Não há mudança de schema.
- Quatro capturas de lista avulsa longa, busca vazia, filtro do Histórico e som recuperado foram inspecionadas visualmente, além das asserções automáticas. O texto longo quebra, ações permanecem alcançáveis por rolagem e estados não dependem apenas de cor. Isso não encerra leitores de tela ou a matriz de 48 configurações.
- [PDF API 37/16 KB](runs/20261004T201351336662Z-pdf/content-audit.json) e [integridade API 37](runs/20261004T201501582332Z-integrity/content-audit.json): PDFs vazios/10k/longos/filtrados, IDs e snapshots auditados por extração textual e banco. Resultados identificam o APK histórico; repetição das correções em execução.
- A coleta de [integridade API 35](runs/20261004T201128573007Z-integrity/manifest.json) continha tar truncado apesar do teste JUnit aprovado. Manifesto original preservado em `manifest-before-artifact-audit.json`; resultado de coleta reprovado com a causa explícita. O runner agora valida o tar antes de declarar sucesso.
- A [segunda matriz](runs/20261004T201956369923Z-api-matrix/manifest.json) passou dados/concorrência de histórico/alarmes em sete APIs, mas não é uma matriz aprovada: recents em 26/33 tinham premissas incorretas, e a nova instância API 36 foi impedida por espaço insuficiente no disco. A API 36.1 já iniciada continua disponível; não se limpa AVD pessoal para liberar espaço.
- A revisão das capturas dos recents confirmou overview real em API 33, com árvore de acessibilidade do app ainda exposta dentro do card. O teste consulta também a Activity efetivamente retomada. Em API 26 havia tasks de viewer/instrumentação à frente; o retorno seleciona o título real do Dose Certa.
