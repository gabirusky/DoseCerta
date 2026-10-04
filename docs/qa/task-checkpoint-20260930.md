# Checkpoint histórico de TASKS

Estado antigo preservado; não representa o estado em 04/10/2026.



**8 de 121 aceites concluídos; 113 caixas abertas.** Seis dessas caixas são S01–S06, condicionais não ativadas pelo [ADR de proteção](docs/adr/data-protection.md); não representam implementação fictícia pendente. As outras 107 possuem aceite, integração, evidência ou dependência ainda aberta. Arquivos completos de implementação não substituem seus critérios de validação.

| Workstream | Estado salvo | Próximo aceite pendente |
| --- | --- | --- |
| B | Toolchain/manifesto/CI/scripts atualizados; APK antigo, mudanças iniciais e resultados preservados | Cold start/10k; voltar/insets runtime; AAB/16 KB; lint/checks finais; README/BUILD final após checks |
| D | Schema 4/migrações, transações/coordenação, snapshots, archive/delete/tombstones e adesão comum implementados | 12 testes Android de persistência e consumidores UI/pipeline; fixes de migração/relógio posteriores ao APK |
| F | Todas as regras/preview/versionamento persistidos, seletores/Home/lista integrados; 10 testes anteriores de calculador passaram | Novo caso mensal dia 30 não executado; testes fuso/edição/múltiplos horários/UI de cada regra e TalkBack |
| A | Checker/concessões/canais/URI/fallback/FSI/fila/ações/snooze/timeout/privacy/reconcile/lifecycle/diagnóstico implementados | Rebuild do último Mutex/snooze; AlarmManager real/negativas/recuperação/acessibilidade; ausência de Xiaomi/Samsung preservada |
| R | Request/snapshot/renderer/SAF/content URI/estados/fixtures implementados | PDFs vazios/10k/longos/filtrados renderizados e conferidos; provider/cancelamento/leitor/API antiga; conteúdo versus banco |
| U | Inventário/heurística/tokens/PT-EN e redesign das superfícies escritos | Capturas de todas as telas/estados, comparação antes/depois, fontes/janelas/contraste/alvos, TalkBack/Switch Access; contato público |
| V | Harnesses e matriz planejados; nenhum resultado Android aprovado | APIs, 16 KB, alarmes/OEM, relatório, acessibilidade, release assinada/upgrade, bateria 24 h; sessão real ou ausência registrada |
| E | AVD/runbook, UI Automator de jornada, recorder segmentado/manifesto/run IDs implementados | Ambiente bootado, compilação do harness e execução/vídeo assistido; negativas/PDF/10 disparos; candidato release |
| P | ADR/backups, política/declarações/ficha, assinatura condicional, licença/inventário e go-no-go/notas/hotfix preparados | Conta/identidade/contato/URL/certificado/versionCode reais; restore/transfer; candidato/Console/Play/pre-launch e aprovação |
| S | Não ativadas para o modelo de ameaça registrado | Reavaliar se proteção independente de CE/FDE for exigida; nenhum SQLCipher/Keystore alegado |

Evidência executada anterior: **19 testes JVM passaram**, zero falhas/erros/skips; assembleDebug/assembleDebugAndroidTest passaram. **Lint falhou 43 erros/228 warnings** e ainda não foi repetido depois dos fixes. Há agora 20 métodos JVM escritos, mas aprovação registrada apenas para os 19 anteriores. Nenhum teste novo executado após a instrução de pular testes.

Emulador: nenhum ADB conectado nesta retomada; tentativas dedicadas falharam exit 139/1, com [diagnóstico e arquivos preservados](docs/qa/emulator-validation.md). Ferramentas oficiais preparadas em .cache/qa-sdk; retry interrompido por pedido do usuário. Pessoal não alterado. Emulador 4 KB não é evidência 16 KB.

Gates externos: usuário informou que conta Play, política/contato público e assinatura existentes ainda não estão definidos; possui Xiaomi, mas pediu emuladores. Pessoas reais, Samsung, Console e candidato assinado indisponíveis. Ver [external-gates](docs/release/external-gates.md), [go/no-go](docs/release/go-no-go.md), [notas](docs/release/release-notes-v1.0.md), [hotfix](docs/release/hotfix.md) e [CONTEXT](CONTEXT.md). Não publicar nem declarar todos os itens concluídos por terminar as fontes.

Continuar código/documentação/build sem testes; retomar validações somente quando autorizado. Salvar resultados por artefato/ambiente e atualizar cada caixa quando o aceite correspondente for comprovado.
