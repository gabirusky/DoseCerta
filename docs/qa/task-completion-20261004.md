# Retomada de TASKS — 04/10/2026

O pedido atual autoriza concluir implementação e validação local. A suspensão de testes dos checkpoints de setembro é histórica. Alterações preexistentes e o polimento visual da Home foram preservados. Não houve commit, publicação, alteração de AVD pessoal, acesso ao Console nem geração de identidade/assinatura fictícia.

## Aceites comprovados

O checklist foi reconciliado com os resultados efetivamente existentes, incluindo execuções de 03/10 que ainda não tinham sido refletidas nas caixas. Uma caixa concluída tem evidência no próprio TASKS; uma caixa aberta pode ter implementação pronta e ainda precisar de algum critério de aceite.

| Área | Evidência real | Limite |
|---|---|---|
| Build e CI | [Checks locais](checks/task-completion-20261004.log): assemble debug/release, AAB, lint debug/release, 20 testes JVM e APK de instrumentação; [inventário](checks/dependency-inventory.json) com 95 componentes/90 binários e zero problemas de licença | CI configurada, sem execução remota alegada; release unsigned |
| Persistência | [13 testes Room](runs/20261003T203834865223Z-data/instrumentation.txt): migrações v1/v2/v3, concorrência, rollback, snapshots, arquivo/exclusão, correção, versões, idempotência e fuso/relógio | Fixtures sintéticas; distribuição antiga pela loja não comprovada |
| Escala | [10.000 registros](runs/20261003T203834865223Z-data/scale.json), 50 medicamentos/100 slots; snapshot, ação e prévias abaixo de 2 s no ensaio | Sem equivalência a aparelho físico, cold start ou bateria de 24 h |
| Recorrência | 11 testes JVM de diária/intervalos/semana/mês/DST; [nove regras pela UI](runs/20261003T210503689904Z-ui/instrumentation.txt), com recriação e persistência | TalkBack/Switch Access continuam separados |
| PDF | [Seis testes Android](runs/20261003T200336628294Z-pdf/instrumentation.txt), [auditoria textual](runs/20261003T200336628294Z-pdf/content-audit.json): vazio, 150 doses/50 medicamentos, 10k, texto longo EN e stream com falha | Provider real sem espaço e matriz completa ainda pendentes |
| Alarmes | [Dois testes reais em 04/10](runs/20261004T130852364287Z-alarm/instrumentation.txt), [ambiente/hashes](runs/20261004T130852364287Z-alarm/manifest.json): dois AlarmManager simultâneos, independência, repetição, som finito, snooze e restauração | API 26/AOSP, concessões de fixture explícitas; não substitui jornada de onboarding, OEM ou bloqueio seguro |
| Interface | [Polimento e capturas](ui-polish-20261003.md): PT/EN, claro/escuro e fonte 200%, mantendo Home com gráfico circular e cards compactos | Amostra em 360 dp; não equivale à matriz completa |

## Correções nesta retomada

- Android 16 expôs `NoSuchMethodException: InputManager.getInstance` no Espresso 3.5.1. Dependências de teste atualizadas para Espresso 3.7.0/JUnit AndroidX 1.3.0. A [release oficial](https://developer.android.com/jetpack/androidx/releases/test#espresso_370) registra a substituição dessa chamada reflexiva. Dependências de produção permanecem as do inventário.
- `SystemUiInstrumentedTest` exercita teclado/voltar, recents, rotação/recriação, insets e rascunho, além de cancelar/salvar/reabrir um PDF pelo DocumentsUI e verificar URI/grants. Corrigidas premissas do harness sobre IME, retorno dos recents e revogação automática de grants ao apagar documento.
- Recorder acorda a tela sintética antes do vídeo: Android 16 retornou `UNASSIGNED_LAYER_STACK` com a tela apagada. Fluxo de onboarding agora rola até controles e reconhece as duas variantes de pacote do PermissionController. Concessões principais continuam visíveis pela UI.
- Runner de instrumentação preserva manifesto mesmo quando coleta de logs expira; timeout também encerra a instrumentação no app sintético. Comentários obsoletos de armazenamento/OEM foram retirados do manifesto.

## Compatibilidade e execução pendente

API 26 está funcional no profile `api26-oct03`. API 36.1 bootou no profile `api36-oct03` e permitiu salvar/reabrir PDF, mas a suíte completa ainda não foi aprovada: uma execução encontrou erros do harness e outra perdeu o emulador com exit 139, após erros de memória gráfica/Vulkan. [Log do emulador](checks/emulator-host-api36-oct03-window-20261004T131408Z.log). Não se atribui esse crash ao aplicativo sem evidência.

Imagem oficial Android 17/API 37.0 com páginas de 16 KB foi instalada somente no cache QA, em profile separado `api37-16kb-oct04`. Um nome de pacote inicial sem `.0` foi rejeitado; o nome efetivamente disponível consta do [inventário SDK](checks/sdk-available-20261004.txt) e [log de instalação](checks/sdk-api37-16kb-install.log). `getconf PAGE_SIZE` retornou `16384`; boot e testes precisam concluir antes de aceitar B06.

Android 17 é estável, conforme [anúncio oficial](https://developer.android.com/blog/posts/android-17-is-here). O [requisito Play consultado em 04/10](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en) mantém API 36 para novas versões; o projeto já usa target 36. A matriz V01 precisa incluir também API 37. API 28/29/31/33/34/35, OEMs e candidato assinado não foram declarados aprovados.

## O que impede concluir todas as caixas

| IDs | Falta para o aceite completo |
|---|---|
| B03/B05/B06 | Cold start com 10k; suíte de retorno/insets API 36 aprovada; testes de runtime 16 KB |
| F08, U01/U06/U09/U12–U17/U19, V02 | Todas as superfícies/erros, 320/412/600 dp, rotação/multiwindow, contraste medido e ensaio de TalkBack/Switch Access |
| A01–A04/A06/A08–A11/A13–A15/A17/A19–A22/A24, V04/V05 | Matriz de negativas/revogação/canais, comandos antigos, card/privacidade sob bloqueio seguro, boot/hora/fuso/processo morto e aparelhos físicos |
| R08–R12, V03 | Suíte SAF integral aprovada, recriação durante exportação, provider sem espaço, comparação de relatório filtrado com banco e APIs requeridas |
| V01/V06, P08 | Matriz completa, mesmo AAB assinado, APKs derivados, certificado/versionCode reais e upgrade do candidato |
| V07 | Ensaio real de bateria de 24 horas; teste curto não pode satisfazê-lo |
| V08 | Ausência de pacientes/cuidadores explicitada; hipóteses de tempo/privacidade aguardam revisão apoiada em feedback real quando disponível |
| E02–E09 | Jornada principal aprovada e vídeo revisado, ações separadas, negativas/PDF e dez disparos nominais por plataforma aprovada |
| P01/P02/P05/P06/P09–P13 | Conta/entidade/pacote, contato e política pública, assinatura, ativos finais, faixas/participantes aplicáveis, formulários e pre-launch no Console |
| P04/P07 | Restore/transfer real e confirmação dos direitos dos ativos finais; inventário/licenças locais já preparados |
| S01–S06 | Não ativadas pelo [ADR](../adr/data-protection.md); não há implementação SQLCipher/Keystore alegada |

O responsável já informou que conta, URL/contato e assinatura ainda não estão definidos, e pediu emuladores. Esses dados não foram solicitados novamente. Continuam em [external-gates](../release/external-gates.md). Estado de lançamento: **NO-GO** até completar os aceites aplicáveis, não uma autorização de publicar.
