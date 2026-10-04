# Home — correção visual em 30/09/2026

A simplificação anterior havia substituído o card de boas-vindas e a composição dos medicamentos por textos e controles empilhados. Após o pedido de correção imediata, a Home recuperou o card com gradiente, o indicador circular de adesão, a hierarquia dos títulos, os cards de medicamentos e a seção de dose avulsa.

Os dados continuam vindo dos flows atuais. Próximas doses, estado Sem dados, ações Tomei/Pular e confirmação de tomadas avulsas foram mantidos. O status da dose fica abaixo da linha de identificação para não comprimir o nome. O cabeçalho empilha texto/indicador em fontes grandes ou janelas estreitas, sem substituir tamanhos sp por px. As barras do sistema usam ícones legíveis no tema ativo.

`assembleDebug` e `lintDebug` passaram. O APK foi atualizado com `adb -s emulator-5554 install -r`, mantendo os dados existentes, e a Home foi aberta e conferida visualmente. A captura posterior mostra o card, a seção de próximas doses, o estado sem doses de hoje e o medicamento PRN existente. Nenhuma tomada foi registrada, nenhum medicamento foi criado/excluído e nenhum AVD foi limpo nesta verificação.

Comparação e hash do APK ficam no cache local `.cache/qa-front-end/`. As capturas contêm o estado existente do emulador e não são fixtures de evidência pública. Esta inspeção visual não fecha U19/V02, nem comprova a matriz de fontes/temas/idiomas e acessibilidade.
