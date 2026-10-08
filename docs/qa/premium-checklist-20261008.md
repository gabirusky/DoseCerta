# Checklist de acessos e acabamento visual — 08/10/2026

O usuário relatou que a sequência de permissões da versão anterior entra em loop no Xiaomi e impede concluir o setup após o último ajuste. Esta revisão substitui a sequência por ações individuais e revê margens, hierarquia e componentes em todas as telas.

## Comportamento esperado

- Cada item do checklist abre apenas o ajuste escolhido. O retorno atualiza o estado, sem abrir outro ajuste automaticamente.
- As marcações de notificações, canais, alarmes exatos, tela cheia e bateria vêm do Android. Os ajustes específicos Xiaomi/Redmi/POCO têm conferência manual identificada, pois não há uma API pública para comprovar esses acessos.
- O botão Continuar não depende de permissões, ajustes de fabricante ou de um callback de retorno para avançar ao tutorial. As pendências continuam visíveis, sem simular autorização.
- Recriar a tela ou retomar um setup anterior não recupera uma sequência automática nem um estado que bloqueie os controles.

## Direção visual

Material 3 nativo, verde mineral e superfícies claras/escuras existentes. Margens de página de 20 dp em celulares e 48 dp em tablets; seções de 24 dp, cards de 22 dp de raio e componentes compactos de 16 dp. Títulos, textos auxiliares e ações compartilham uma escala tipográfica consistente. Alvos de toque mantêm pelo menos 48 dp.

O Início preserva o gradiente e o indicador circular compacto; o Histórico preserva os três resumos coloridos pedidos pelo usuário. As listas continuam compactas, com adaptações para fonte ampliada. A revisão inclui setup, tutorial, Início, Medicamentos, Histórico, formulário, Configurações, privacidade, diálogos e cartão do alarme.

As correções anteriores de insets, apresentação sobre a tela bloqueada, ações diretas e cor escolhida do medicamento permanecem no código.

## Ambiente de verificação

AVD sintético `DoseCerta_QA`, Android 15/API 35, 360 × 640 dp, porta 5580, criado em `.cache/qa-avds/windows-premium-oct08`. O AVD pessoal na porta 5554 não é usado para testes nem recebe alterações. O emulador de QA usa a imagem já instalada no SDK e discos próprios, sem resetar dados pessoais.

Resultados de build, testes e revisão visual serão registrados após a validação desta revisão. A verificação no Android padrão não substitui a conferência dos ajustes do fabricante em um Xiaomi físico.
