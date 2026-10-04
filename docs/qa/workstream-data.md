# Dados e recorrência — mudanças e verificação

Data: 2026-09-29. Esta evidência descreve implementação e testes específicos; tarefas não são consideradas completas por existência de arquivo ou compilação. Estado de execução será atualizado após checks e ensaio instrumental.

| Tarefa | Implementação revisável | Evidência / pendência |
| --- | --- | --- |
| D01 | ADR dose-occurrences; log v4 com chave opaca, instante/local/fuso originais e snapshots | Contrato e APIs implementados; inspecionar teste de snooze/fuso e pipeline. |
| D02 | Máquina pura de estado; tolerância30min, silêncio separado, limite sonoro60s, reminder parametrizado | 6 testes JVM; runtime áudio é responsabilidade A18/A24. |
| D03 | Schema exportado; caminhos1→2→3→4; FK de log SET_NULL e sem fallback destrutivo | Testes Android com fixtures SQL reais v1/v2/v3 e validação final Room; publicação antiga desconhecida. |
| D04 | Índice único, `INSERT IGNORE`, Room transactions, deduplicação legada auditável | Teste Android20criações concorrentes e take/skip; timeout não sobrescreve conclusão. |
| D05 | Coordenador único Success/Rejected/Failure, rejeição atrasada, cancelamento propagado | Estado e persistência cobertos; UI e receiver devem consumir resultado. |
| D06 | Save multientidade, preservação createdAt, recibo idempotente, resultado para agendar depois do commit | Teste triggerfalha reverte cadastro; retry token preserva IDs/versão. |
| D07 | Arquivar slot/medicação, aposentar versão e cancelar só pendências | Testes de edição/PRN/arquivo mantêm snapshots; alarm cancel depende consumidor. |
| D08 | Exclusão permanente com opção de histórico; tombstones sem dados clínicos | Teste delete seguido de backfill mantém exclusão; histórico retido perde FK sem perder elegibilidade. |
| D09 | AdherenceCalculator compartilhado, percentualnull quando sem dados | 3 testes JVM; consumidores Home/Histórico/PDF usam mesmo cálculo. |
| D10 | correctLog requer tomadoEm explícito; remove actualTime de SKIPPED/MISSED; snapshots sem JOIN atual | Testes correção e alteração do medicamento; UI edição precisa ensaio. |
| F01 | ADR recurrence contém diário/intervalo/semanal/mensal/dias, DST e limites de prescrição | Contrato escrito com exemplos; intervalo mantém slots civis explícitos legados. |
| F02 | Schedule regra/mês/fuso/versão/vigência, migração preserva os minutos e dias legados | Fixtures v1/v2/v3; regra8h migra INTERVAL, sem reinterpretar doses. |
| F03 | nextOccurrence com Clock/fuso injetáveis, retorno estritamente futuro, busca encerra cedo | Diário, intervalos explícitos, PRN, inativa e janela de vigência testados. |
| F04 | Seleção semanal/dias com múltiplos slots independentes | Testes passagem sábado/domingo; fluxo seleçãoUI pertence F08/F12. |
| F05 | Mensal por calendário, ausente pula mês | Testes fevereiro29/bissexto/dia31/ano; sem soma30dias. |
| F06 | Gap adianta, overlap primeirooffset único; chave local evita duplicação de fuso | JVMgap/overlap e Androidmudança de fuso, preview fixada. |
| F11 | Save aposenta versão, cancela pendências, preserva terminal e original | Teste revisão seguida de reconciliação não cria dose no horário antigo; pipeline deve cancelar handles. |

## Testes implementados

`app/src/test/java/com/dosecerta/domain/`: 19 testes (10 recorrência,6transições,3adesão).

`app/src/androidTest/java/com/dosecerta/data/OccurrencePersistenceTest.kt`: 12 ensaios: unicidade concorrente; rollbackde falha e createdAt; snapshot após edit/archive/delete; supressão e preview; graça/snooze/correção; revisão e backfill; tokenavulsa; tokensave; correção do relógio para trás e nova vigência; fuso/preview; migraçãov3/dedup/FK; caminhosv1/v2. A implementação de FixtureSQL vem dos tipos e FKs dos commits legados, sem depender de hash fictício de schema exportado.

Comandos previstos:

```bash
env JAVA_HOME=/home/gabirusky/Programs/android-studio/jbr ./gradlew testDebugUnitTest assembleDebugAndroidTest --console=plain
env JAVA_HOME=/home/gabirusky/Programs/android-studio/jbr ./gradlew connectedDebugAndroidTest --console=plain
```

JVM executado na integração em 2026-09-30T02:52:50Z: os 19 testes do domínio passaram, zero falhas/erros/skips. XMLs em `app/build/test-results/testDebugUnitTest/TEST-com.dosecerta.domain.{RecurrenceCalculatorTest,DoseStateMachineTest,AdherenceCalculatorTest}.xml`. Duração conjunta de 0,336s no host Fedora; isso não mede cold start Android nem tempo de ação em aparelho. Schema v4 exportado e inspecionado: seis tabelas e índices de ocorrência/estado/período presentes.

Instrumentação Android ainda pendente enquanto o emulador prepara e os agentes integram o pipeline. Atualizar este documento com resultado real, API/aparelho, XML de teste e falhas corrigidas. Volume10000/performance, UI de cada regra, OEM/Xiaomi e alarmes reais são gates adicionais e não são provados pelos ensaios acima.


## Fixture de escala B03

`SyntheticScaleTest` cria 10.000 logs sintéticos em 100 dias, com 50 medicamentos/100 horários, 1.000 avulsas e 9.000 conclusões programadas. O banco padrão é privado ao teste e removido no final; nenhuma informação de paciente ou banco de uso é copiada. Mede consulta de snapshot, resumo compartilhado, commit de tomada e próximas datas dos 100 slots; gera `files/qa-scale-measurement.json` no app e JSON na saída instrumental com API, modelo, volume e durações. O teste exige resposta menor que2s para consulta/ação/próximas datas; ainda não executado.

O argumento instrumental explícito `seedMainDatabase=true` permite preencher somente um banco principal vazio de emulador de QA, para o root medir cold start por `adb shell am start -W` após encerrar a instrumentação. O teste não mede cold start por si mesmo e registra `coldStartMeasured=false`. Exemplo para um emulador limpo, depois de instalar APKs: `adb -s emulator-5580 shell am instrument -w -e class com.dosecerta.data.SyntheticScaleTest -e seedMainDatabase true com.dosecerta.test/androidx.test.runner.AndroidJUnitRunner`. Os métodos nunca removem dados existentes para fabricar banco vazio.

Revisão independente corrigiu dois casos adicionais: a migração conserva o minuto antigo quando um horário legado foi editado (só recupera o minuto solicitado para DST se resolver ao mesmo instante), e um checkpoint futuro após relógio ajustado para trás não bloqueia a reconciliação de uma nova versão criada no novo período. Testes instrumentais foram ampliados; requerem recompilação antes da execução.
