# Relatório de Implementação – Dose Certa v0.5.0

## 1. Resumo
A versão 0.5.0 marca uma evolução substancial de toda a base de código do Dose Certa. Esta atualização traz uma reformulação completa da estrutura interna, tornando o aplicativo mais enxuto e performático, além de introduzir funcionalidades críticas focadas na adesão ao tratamento. O destaque principal é a nova lógica de **Alarme Forçado**, que transforma a experiência de lembretes, garantindo que o usuário não perca suas doses.

## 2. Nova Feature: Alarme Forçado (Estilo Despertador)
Para resolver o problema de notificações perdidas ou ignoradas, implementei um sistema robusto de alarme que se sobrepõe ao bloqueio de tela e ignora o modo silencioso.

### 2.1 Funcionalidades Principais
*   **Tela Cheia Forçada**: A interface do alarme aparece instantaneamente, mesmo com o dispositivo bloqueado.
*   **Som Contínuo e Prioritário**: O som toca em loop até haver interação e utiliza canais de áudio de alarme para ignorar configurações de "Não Perturbe" ou modo silencioso.
*   **Ações Rápidas**: Interface intuitiva com três opções claras:
    *   **Tomei**: Registra a dose como tomada e encerra o alarme.
    *   **Pular**: Registra como pulada e encerra.
    *   **Soneca**: Adia o lembrete por 10 minutos.
*   **Personalização**: O usuário pode escolher o som do alarme nas configurações do aplicativo.

### 2.2 Detalhes Técnicos da Implementação
*   **AlarmService & AlarmActivity**: Utilização de um *Foreground Service* para manter o toque contínuo e uma *Activity* configurada com `ShowWhenLocked` e `TurnScreenOn` para garantir visibilidade imediata.
*   **Gestão de Energia**: Implementação de `WakeLock` para manter a CPU ativa durante o disparo do alarme.
*   **Persistência**: O alarme é resiliente a tentativas de fechamento simples, exigindo interação explicita.

## 3. Melhorias de UI/UX e Novas Funcionalidades
Além do alarme, diversas áreas do aplicativo receberam refinamentos visuais e funcionais baseados nos testes e feedback de uso.

### 3.1 Refinamentos na Interface (Material Design 3)
*   **Novos Ícones Dinâmicos**: O ícone de medicação foi simplificado para um design circular minimalista, permitindo personalização de cores para fácil identificação visual na lista.
*   **Feedback Visual Aprimorado**: Melhorias nos componentes de `Toasts` e diálogos de confirmação ao realizar ações como tomar ou pular doses.
*   **Modo Escuro/Claro**: Correções de contraste e paleta de cores para garantir legibilidade perfeita em ambos os temas.

### 3.2 Gestão de Histórico e Logs
*   **Auto-Miss Logic**: Implementação inteligente que marca automaticamente uma dose como "Perdida" (Missed) se o alarme for ignorado por muito tempo, garantindo que o histórico reflita a realidade mesmo sem ação do usuário.
*   **Refresh Automático**: A tela de histórico agora atualiza automaticamente ao ser aberta, exibindo sempre os dados mais recentes sem necessidade de recarregar manualmente.
*   **Correção de Histórico de Custom Meds**: Ajustes nas consultas SQL (`LEFT JOIN`) para garantir que medicamentos personalizados e doses extras apareçam corretamente nos relatórios.

### 3.3 Configurações Avançadas
*   **Delay de Lembrete Configurável**: Usuários agora podem personalizar o tempo das notificações de acompanhamento de dose perdida (padrão de 2 horas, configurável).
*   **Seletor de Som**: Nova opção nas configurações para escolher sons específicos para o alarme, utilizando o `RingtoneManager` nativo.

## 4. Evolução Técnica e Refatoração
A base de código passou por uma limpeza profunda para garantir escalabilidade e manutenibilidade.

*   **Logic Optimization**: Refatoração completa dos *ViewModels* (especialmente `AddMedicationViewModel` e `HistoryViewModel`) para uso correto de *Coroutines* e *Scopes*, eliminando travamentos e melhorando a resposta da UI.
*   **Correção de Bugs Críticos**:
    *   Resolução de problemas de compilação com o Gradle e compatibilidade com Android 14.
    *   Correção na lógica de edição de medicamentos que antes resetava o status de doses passadas.
    *   Fix na geração de lembretes padrão que falhava em certos cenários de edição.

## 5. Conclusão
A versão 0.5.0 é um marco para o Dose Certa. Com a introdução do Alarme Forçado, o aplicativo deixa de ser apenas um passivo registrador de remédios para se tornar um assistente ativo e insistente na saúde do usuário. As refatorações técnicas asseguram que essa nova complexidade não comprometa a estabilidade, entregando um produto sólido, confiável e agradável de usar. Futuras implementações visam adicionar mais funcionalidades e melhorias na experiência do usuário de acordo com requisitos descritos no início do projeto e novas ideias que surgiram ao longo do tempo. O objetivo para versão 1.0.0 é lançar o aplicativo gratuitamente para Android pela Google Play Store.
