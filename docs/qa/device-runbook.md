# Execução sintética e evidências

Selecione explicitamente serial. AVD dedicado: `DoseCerta_QA`, configuração em `/tmp/dosecerta-qa-avd`, imagem instalada API36.1/Google Play/x86_64, fuso `America/Sao_Paulo`, porta5580 (serial`emulator-5580`). O AVD pessoal `Medium_Phone_API_36.1`/serial5554 não deve ser apagado. Reinicialização/limpeza de dados só no AVD dedicado sintético identificado pelo nome real retornado por `adb -s SERIAL emu avd name`.

1. Verificar `adb devices -l`, boot completo, API, `getconf PAGE_SIZE`, idioma e fuso. Anotar SHA256 do APK e commit+diff. Selecionar build atual.
2. Compilar checks; instalar APK/test APK. Migração e PDF usam bancos/fixtures sintéticos. UI completa deve cadastrar pela tela; seed de banco não substitui o vídeo de cadastro.
3. No fluxo principal, concessões POST_NOTIFICATIONS/exact/FSI acontecem pela UI do sistema. Não usar `pm grant`/`appops` invisíveis para a demonstração. Negar/voltar e observar estado real também são cenários.
4. Cadastrar `Medicamento fictício QA` com horário futuro; conferir Home/prévia e `dumpsys alarm`. Bloquear e esperar horário real; não iniciar receiver/card por broadcast manual. Capturar timeout diagnóstico se não entregar.
5. Tomei/Pular/Adiar/Silenciar têm execuções separadas. Conferir ID/horário/status e contagem no histórico; tomada única. Privacidade bloqueada não pode mostrar identidade nem aceitar tomada/pulo sem desbloqueio.
6. PDF via CreateDocument; cancelar, salvar, abrir, compartilhar e falhar provider. Usar fixtures vazio, longo,50 medicamentos,30+ doses no dia,10.000 logs e filtros.
7. `bash scripts/qa/device-checks.sh SERIAL` executa instrumentação e salva estado. Não assumir matriz inteira validada por uma API.

Vídeo Android `screenrecord` normalmente não captura áudio interno; apontar limitação no manifesto. Preservar segmentos por execução e não juntar negativos com principal sem identificação. Vídeo precisa existir e ser revisado antes de marcar E07. Coleções `dumpsys notification --noredact` são permitidas somente nesse ambiente sem dados reais.

Xiaomi: responsável possui aparelho; solicitar ligação ADB com dados sintéticos/medicação futura e anotar modelo/API/HyperOS. Não mudar PIN/contas nem apagar conteúdo pessoal. Samsung/níveis API26/28/29/31/33/34/35/37/16KB faltantes exigem aparelhos/imagens e execuções próprias. Force-stop separado de processo morto, pois Android bloqueia entregas até reabrir o app.
