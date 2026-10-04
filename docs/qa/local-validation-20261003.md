# Validação local — 03/10/2026

Retomada do objetivo TASKS com autorização explícita do responsável para retomar testes e validações locais. Alterações preexistentes preservadas. A Home mantém a estrutura visual restaurada em 30/09. Não houve publicação, commit, alteração de AVD pessoal ou acesso ao Google Play Console.

## Build e artefatos

Build debug/release, AAB unsigned, APK instrumentado, lint debug/release e 20 testes JVM aprovados. [Log Gradle](checks/local-checks-20261003.log). Lint: zero erros, 210 warnings por variante. Recorrência: 11 testes; estados: 6; adesão: 3, sem falhas ou skips. Resultados detalhados em `app/build/test-results/testDebugUnitTest`.

O [script reproduzível](../../scripts/qa/checks.sh) passou localmente com os mesmos checks, incluindo release e auditorias; [log](checks/reproducible-checks-20261003.log). A configuração CI chama esse script, sem segredos de assinatura; nenhuma execução remota é alegada. Inventário `releaseRuntimeClasspath`: 95 componentes, 90 binários, nenhuma licença sem resolução. [Inventário](checks/dependency-inventory.json), [avisos](../release/THIRD_PARTY_NOTICES.md), [APK](checks/release-apk-audit.json), [AAB](checks/aab-audit.json). Nenhuma `.so` nesses artefatos. Release permanece unsigned e não satisfaz o gate de assinatura/submissão ou o teste runtime de 16 KB.

## Ambiente Android

AVD dedicado `DoseCerta_QA`, profile `api26-oct03`, serial `emulator-5580`, API 26, 720 × 1280, 320 dpi, fuso America/Sao_Paulo. Imagem `Android/sdk_gphone_x86_64/generic_x86_64:8.0.0/OSR1.180418.026/6741039:userdebug/dev-keys`. Criptografia efetiva declarada pelo SO: encrypted/block; KernelPageSize=4096. A ausência de `getconf` na imagem API 26 foi tratada lendo KernelPageSize em `/proc/self/smaps`.

Emulator 37.1.11/software/headless terminou com SIGSEGV antes do boot. O emulador instalado, 36.4.9, com `host` e janela concluiu o boot do novo AVD sintético. [Falha](checks/emulator-software-api26-oct03-headless-20261003T194739Z.log), [boot](checks/emulator-host-api26-oct03-window-20261003T195117Z.log). Não se atribui uma causa definitiva ao crash. As [notas oficiais](https://developer.android.com/studio/releases/emulator) e a [orientação de troubleshooting](https://developer.android.com/studio/run/emulator-troubleshooting) foram consultadas; o sucesso observado é deste perfil/host, sem equivalência OEM ou 16 KB.

As guardas identificam `ro.boot.qemu.avd_name` em imagens recentes e `ro.kernel.qemu.avd_name` na API 26. Seed de banco principal exige também o AVD dedicado. `scripts/qa/instrument.py` preserva resultados, hashes dos dois APKs, propriedades, dumps e artefatos; suites com skips ou contagem diferente da esperada não são aprovadas.

## Dados e escala

[13 testes Android aprovados](runs/20261003T195856179326Z-data/instrumentation.txt), sem skips. [Manifesto](runs/20261003T195856179326Z-data/manifest.json) e [medições](runs/20261003T195856179326Z-data/scale.json).

Cobertura: migrações v1/v2/v3 para schema 4 com IDs/dados preservados; deduplicação legada; unicidade concorrente; comandos repetidos; conflito tomada/pulo; rollback de cadastro; createdAt; snapshots após editar/arquivar/excluir cadastro; supressão de histórico excluído; snooze/meia-noite e horário original; correção explícita de horário; versões de recorrência; recibos de cadastro/avulsa; relógio retrocedido; mudança de fuso sem duplicação. As versões históricas são as do repositório; não há comprovação de versões anteriormente distribuídas pela loja.

Fixture privada ao teste: 10.000 logs, 50 medicamentos, 100 slots, 100 dias e 10% avulsas. Snapshot: 510,55 ms; ação persistida: 6,39 ms; prévia dos 100 slots: 272,21 ms. Estes valores são do AVD, sem medição de cold start ou bateria de 24 horas.

## PDF

[6 testes Android aprovados](runs/20261003T200336628294Z-pdf/instrumentation.txt), [manifesto](runs/20261003T200336628294Z-pdf/manifest.json), [conferência do conteúdo](runs/20261003T200336628294Z-pdf/content-audit.json). Documentos preservados em `cache/qa-reports` dentro dessa execução: vazio (1 página), 150 doses/50 medicamentos (15), 10.000 doses (911), 30 linhas com texto longo em inglês (63). Cada ID aparece exatamente uma vez; o PDF vazio contém Sem dados; nenhuma palavra verificada ultrapassa as margens horizontais. PdfRenderer abriu os documentos no Android 8; pdfinfo/pdftotext conferiram conteúdo/paginação no host. Primeira página do resumo e continuação do texto longo foram renderizadas e inspecionadas visualmente.

Falha reproduzida e corrigida: a implementação nativa de PdfDocument no Android 8 consumia a IOException do stream; o renderer agora registra a falha no limite Java e a propaga antes de informar sucesso. [Execução reprovada preservada](runs/20261003T195946987142Z-pdf/instrumentation.txt). Falha ao consultar o nome do arquivo não transforma escrita concluída em erro; o nome padrão é usado. Doses históricas com pais removidos mantêm a classificação programada, verificada na suíte de dados.

Não há aceite de picker/cancelamento SAF, leitura por app externo, provider real sem espaço, API atual ou relatório filtrado completo apenas por esses testes do renderer.

## Cadastro e interface

[Fluxo das nove frequências aprovado](runs/20261003T203035234429Z-ui/instrumentation.txt), [manifesto](runs/20261003T203035234429Z-ui/manifest.json). Cadastro pela UI, prévia, recriação, rascunho, salvar e conferir a frequência/slots no Room. Mês 31, dias selecionados, intervalos, diária e PRN cobertos. Capturas de cada formulário foram preservadas. A captura de telas após navegação precisa aguardar um marcador próprio do destino; essa correção do harness ainda requer execução antes de validar todas as imagens de navegação.

Bloqueio inicial de build corrigido: controles dos grupos de detalhes/prévia/dose e remoção por slot, mais strings PT/EN, faltavam no XML embora fossem usados pelo Kotlin. Os seletores agora resolvem o item pelo rótulo, sem presumir que a posição filtrada coincide com a enumeração. O teste usa o item do popup via Espresso e verifica o valor antes/depois da recriação. [Execuções anteriores reprovadas](runs/20261003T200425901622Z-ui/instrumentation.txt) permanecem preservadas. A troca da seleção no harness foi necessária para o resultado aprovado; não se atribui a falha anterior somente à implementação do formulário.

Alarme real, jornada gravada, APIs restantes, matriz visual/acessibilidade completa e gates externos continuam em execução ou pendentes. Cada aceite é atualizado somente após a evidência pertinente.
