# Emulador sintético — validação de execução

Registro de QA em 30/09/2026. Nenhum resultado abaixo substitui testes físicos de Xiaomi/Samsung, testes de bateria de 24 horas ou instalação pela Play Store.

## Ambiente preservado

O AVD pessoal `Medium_Phone_API_36.1` está configurado em `/home/gabirusky/.android/avd/Medium_Phone.avd`. Seu arquivo de configuração foi lido; seus discos, snapshots e configurações não foram modificados. Em 30/09/2026, às 14:04 (America/Sao_Paulo), `adb devices -l` não listou dispositivos conectados. O estado de uma sessão anterior com `emulator-5554` online não foi presumido.

O diretório temporário `/tmp/dosecerta-qa-avd` do checkpoint anterior não existia nessa retomada. Foi recriado um AVD separado chamado `DoseCerta_QA`, com somente a configuração base copiada, sem userdata/snapshot pessoal. O disco sintético tem 2 GB; câmeras, SD card e entrada de áudio foram desativados. Serial esperado: `emulator-5580`, fuso `America/Sao_Paulo`, tela 1080 × 2400, densidade 420 dpi.

## Tentativas e evidência

1. O checkpoint anterior registrou `-gpu host -wipe-data` no AVD sintético, com falha terminal por sinal 11 / exit 139. Logs preservados: `emulator-startup.log` e `emulator-startup-clean.log`. Nenhum teste de aplicativo foi executado naquela tentativa.
2. Nesta retomada, o emulador instalado 36.4.9.0 foi iniciado com `-gpu swiftshader -feature -Vulkan -no-window -no-audio -no-snapshot -wipe-data`. O ADB chegou a listar `emulator-5580 offline`; o processo terminou por sinal 11 / exit 139 às 14:07. Log: `emulator-swiftshader-20260930.log`. `coredumpctl info 5265` confirmou o sinal 11 no executável `qemu-system-x86_64-headless`; a causa específica ainda não foi comprovada.
3. O log da versão instalada informa que a imagem atual exige o recurso `VulkanVirtualQueue`, que esse emulador não suporta. A versão estável 37.1.11 e as ferramentas oficiais de SDK 23.0 foram baixadas, verificadas e extraídas no cache ignorado do projeto, sem substituir o SDK instalado do usuário.
4. O emulador 37.1.11 foi iniciado contra o mesmo AVD sintético, sem novo wipe. Terminou imediatamente com exit 1: o SDK no cache não tinha `platform-tools`, e o emulador não conseguiu resolver uma raiz válida. Log: `emulator-updated-20260930.log`. Um symlink somente de referência para os `platform-tools` instalados foi criado no cache; não houve nova tentativa. A ferramenta SDK 23.0 informa que usa o novo Android CLI; ajuda/listagem retornaram `Failed to create bin dir / Read-only file system`, sem investigação adicional do caminho solicitado. Nenhum pacote de imagem foi instalado.

Em seguida, o usuário pediu **“skip tests for now”**. A execução de testes, a instalação da matriz e as próximas tentativas de boot foram interrompidas. Os downloads já estavam concluídos. Nenhum emulador dedicado está executando: as duas tentativas deste registro terminaram com exit 139 e exit 1, respectivamente, e `adb devices -l` ao encerrar não listou dispositivos. O daemon ADB pode permanecer ativo. A configuração sintética usada foi preservada em [synthetic-avd-config.ini](synthetic-avd-config.ini). A próxima retomada deve verificar processos/dispositivos antes de iniciar outra instância.

O modo SwiftShader e a opção `-feature -Vulkan` seguem a [documentação oficial de solução de problemas do Android Emulator](https://developer.android.com/studio/run/emulator-troubleshooting). A atualização é uma tentativa de resolver a incompatibilidade observada, não uma conclusão sobre a causa da falha.

## Pacotes preservados no cache

Metadados oficiais obtidos de [repository2-3.xml](https://dl.google.com/android/repository/repository2-3.xml), canal estável `channel-0`:

| Pacote | Arquivo oficial | Tamanho esperado | SHA-1 declarado pelo repositório |
| --- | --- | ---: | --- |
| Emulator 37.1.11 | `emulator-linux_x64-15917651.zip` | 334378080 bytes | `1b1f78891abf8ec268264356e1365c25519e8379` |
| SDK Command-line Tools 23.0 | `commandlinetools-linux-16111833_latest.zip` | 181052239 bytes | `e025545c62a8e64c7559119566a569fb1dec5f60` |

Tamanho e SHA-1 foram verificados antes da extração. [sdk-tool-integrity.json](sdk-tool-integrity.json) registra os valores oficiais e os SHA-256 dos arquivos locais. O cache `.cache/qa-sdk` já é ignorado pelo Git; não é uma alteração do SDK pessoal. Os arquivos extraídos e downloads ficam nesse cache e não devem ser considerados dependências versionadas do app.

| Pacote | SHA-256 local |
| --- | --- |
| Emulator 37.1.11 | `95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266` |
| SDK Command-line Tools 23.0 | `0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8` |

## Estado dos testes

Instrumentação de persistência/escala/PDF/UI/alarme, identificação da página de memória e propriedades finais de boot permanecem não executadas nesta retomada, por solicitação do usuário. Não há aprovação de runtime neste registro.
