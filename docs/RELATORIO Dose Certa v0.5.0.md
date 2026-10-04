# **Relatório de Implementação – Dose Certa v0.5.0**

## **1\. Resumo**

A versão 0.5.0 marca uma evolução substancial de toda a base de código do Dose Certa. Esta atualização traz uma reformulação completa da estrutura interna, tornando o aplicativo mais enxuto e performático, além de introduzir funcionalidades críticas focadas na adesão ao tratamento. O destaque principal é a nova lógica de **Alarme Forçado**, que transforma a experiência de lembretes, garantindo que o usuário não perca suas doses.

O desenvolvimento seguiu princípios de **Engenharia de Software Iterativa**, empregando ciclos curtos de implementação, testes manuais exploratórios e correções incrementais. Cada *commit* representa um passo verificado em direção ao produto final estável.

## **2\. Nova Feature: Alarme Forçado (Estilo Despertador)**

Para resolver o problema de notificações perdidas ou ignoradas, implementei um sistema robusto de alarme que se sobrepõe ao bloqueio de tela e ignora o modo silencioso.

### **2.1 Funcionalidades Principais**

* **Tela Cheia Forçada (*Full-Screen Intent*)**: A interface do alarme aparece instantaneamente, mesmo com o dispositivo bloqueado, utilizando atributos como showWhenLocked e turnScreenOn definidos no AndroidManifest.xml.  
* **Som Contínuo e Prioritário**: O som toca em loop até haver interação. O AlarmSoundManager utiliza AudioAttributes com USAGE\_ALARM para garantir que o áudio ignore configurações de "Não Perturbe" (*Do Not Disturb*) ou modo silencioso.  
* **Ações Rápidas**: Interface intuitiva com três opções claras:  
  * **Tomei**: Registra a dose como tomada no banco de dados (Room) e encerra o alarme.  
  * **Pular**: Registra como pulada e encerra.  
  * **Soneca**: Utiliza o AlarmScheduler para adiar o lembrete por 10 minutos.  
* **Personalização**: O usuário pode escolher o som do alarme nas configurações do aplicativo, utilizando a API RingtoneManager nativa do Android.

### **2.2 Detalhes Técnicos da Implementação**

A arquitetura do sistema de alarme foi projetada seguindo princípios de **Separação de Responsabilidades (SoC)** e **Injeção de Dependência**, típicos do padrão MVVM (*Model-View-ViewModel*) adotado no projeto.

#### **2.2.1 Componentes da Arquitetura**

| Componente | Responsabilidade | Tecnologia |
| :---- | :---- | :---- |
| AlarmService | *Foreground Service* que gerencia o ciclo de vida do alarme | Service \+ WakeLock |
| AlarmActivity | *Activity* full-screen exibida sobre lockscreen | showWhenLocked API |
| AlarmSoundManager | Gerenciamento do MediaPlayer com AudioAttributes | MediaPlayer API |
| MedicationAlarmReceiver | *BroadcastReceiver* para alarmes exatos | AlarmManager |
| NotificationHelper | Construção de notificações com ações rápidas | NotificationCompat |

#### **2.2.2 Fluxo de Execução**

O disparo do alarme segue uma cadeia de eventos orquestrada:

1. O AlarmManager dispara o MedicationAlarmReceiver no horário agendado.  
2. O *receiver* inicia o AlarmService como *Foreground Service*.  
3. O AlarmService adquire um WakeLock para manter a CPU ativa e inicia o AlarmSoundManager.  
4. O serviço então lança a AlarmActivity diretamente (após um pequeno delay para garantir conformidade com as restrições de *Background Activity Launch* do Android 10+).  
5. A AlarmActivity é exibida em tela cheia sobre a lockscreen, aguardando interação do usuário.

#### **2.2.3 Persistência e Resiliência**

O alarme é resiliente a tentativas de fechamento simples. O botão "Voltar" é interceptado para evitar dispensas acidentais, exigindo interação explícita com um dos três botões de ação. O estado de medicação é persistido de forma atômica no banco de dados Room, garantindo integridade transacional.

### **2.3 Correções e Refinamentos Recentes (Commits)**

Os seguintes *commits* documentam a evolução iterativa do sistema de alarme:

| Commit | Descrição | Impacto |
| :---- | :---- | :---- |
| fix: activity\_alarm redesign | Redesenho completo do layout do card de alarme com hierarquia visual aprimorada | UX |
| feat: implemented alarm sound selector | Implementação do seletor de som customizado | Feature |
| fix: history and medications bugs | Correção de bugs na integração com histórico e medicações | Estabilidade |
| feat: change frequency to interval-based | Mudança do modelo de frequência para baseado em intervalos | Arquitetura |
| feat: change reminder delay to interval-based | Implementação de delay customizável para lembretes perdidos | Feature |
| fix: add FOREGROUND\_SERVICE\_MEDIA\_PLAYBACK permission | Correção de SecurityException para SDK 34 | Compatibilidade |

## **3\. Melhorias de UI/UX e Novas Funcionalidades**

Além do sistema de alarme, diversas áreas do aplicativo receberam refinamentos visuais e funcionais com base em **testes de usabilidade exploratórios** e análise heurística.

### **3.1 Redesenho do Card de Alarme**

O layout activity\_alarm.xml foi completamente redesenhado utilizando os princípios do **Material Design 3**:

* **Hierarquia Visual Clara**: O horário agendado aparece em destaque (DisplaySmall), seguido do nome do medicamento (HeadlineMedium em negrito) e informações de dosagem (BodyLarge).  
* **Card Centralizado**: Utilização de MaterialCardView com cardCornerRadius de 24dp e cardElevation de 12dp para criar um efeito de destaque visual.  
* **Botões de Ação Distintos**:  
  * **Tomei**: Botão verde (mint\_leaf) de 56dp com ícone de seta, indicando progresso.  
  * **Pular**: Botão amarelo/laranja (status\_skipped) de 52dp.  
  * **Soneca**: Botão de texto (TextButton) com ícone de relógio, menor prioridade visual.

### **3.2 Refinamentos na Interface (Material Design 3\)**

* **Novos Ícones Dinâmicos**: O ícone de medicação foi simplificado para um design circular minimalista, permitindo personalização de cores para fácil identificação visual na lista.  
* **Feedback Visual Aprimorado**: Melhorias nos componentes de Toasts e diálogos de confirmação ao realizar ações como tomar ou pular doses.  
* **Modo Escuro/Claro**: Correções de contraste e paleta de cores para garantir legibilidade perfeita em ambos os temas, seguindo as diretrizes de acessibilidade WCAG 2.1.

### **3.3 Gestão de Histórico e Logs**

* **Auto-Miss Logic**: Implementação inteligente que marca automaticamente uma dose como "Perdida" (*Missed*) se o alarme for ignorado por tempo prolongado. Isso utiliza o MarkMissedReceiver em conjunto com o AlarmManager para agendar um *broadcast* de marcação automática.  
* **Refresh Automático**: A tela de histórico (HistoryFragment) agora observa o *LiveData* do HistoryViewModel e atualiza automaticamente ao ser aberta, eliminando a necessidade de reiniciar o app manualmente.  
* **Correção de Histórico de Custom Meds**: Ajustes nas consultas SQL do MedicationLogDao utilizando LEFT JOIN em vez de INNER JOIN para garantir que medicamentos personalizados e doses extras apareçam corretamente nos relatórios (feature futura de geração de relatórios), mesmo quando não possuem todos os relacionamentos.

### **3.4 Configurações Avançadas**

* **Delay de Lembrete Configurável**: O MissedReminderReceiver agora consulta o SettingsManager para obter o delay customizado (padrão de 2 horas, configurável de 1-10 horas) armazenado via DataStore Preferences.  
* **Seletor de Som**: Nova tela de configurações integrada com RingtoneManager.ACTION\_RINGTONE\_PICKER para permitir seleção de sons personalizados para o alarme.

## **4\. Evolução Técnica e Refatoração**

A base de código passou por uma limpeza profunda (*code refactoring*) para garantir **escalabilidade**, **manutenibilidade** e conformidade com padrões modernos de desenvolvimento Android.

### **4.1 Otimização de Lógica Assíncrona**

A refatoração dos *ViewModels* (AddMedicationViewModel, HistoryViewModel, HomeViewModel) implementou:

* **Coroutines**: Uso correto de viewModelScope.launch para operações assíncronas, garantindo cancelamento automático quando o *ViewModel* é destruído.  
* **Flow/LiveData**: Emissão reativa de estados para atualização da UI de forma desacoplada.  
* **Dispatchers**: Uso apropriado de Dispatchers.IO para operações de banco de dados e Dispatchers.Main para atualizações de UI.

### **4.2 Compatibilidade com Android 14 (SDK 34\)**

O targetSdk 34 exigiu adaptações específicas:

* **Permissão FOREGROUND\_SERVICE\_MEDIA\_PLAYBACK**: Adicionada ao AndroidManifest.xml para permitir que o AlarmService execute reprodução de áudio em *foreground*.  
* **Permissão USE\_FULL\_SCREEN\_INTENT**: Necessária para exibir telas de alarme sobre a lockscreen.  
* **Declaração de foregroundServiceType**: O serviço agora declara explicitamente mediaPlayback como tipo de serviço foreground.

### **4.3 Correção de Bugs Críticos**

| Bug | Causa Raiz | Solução |
| :---- | :---- | :---- |
| Reset de status ao editar medicamento | UPDATE substituía registros de log existentes | Lógica de *upsert* inteligente no MedicationLogDao |
| Falha na geração de lembretes padrão | generateDefaultReminders() só executava com lista vazia | Refatoração para regenerar sempre que frequência muda |
| SecurityException no AlarmService | Falta de permissão para SDK 34 | Adição de FOREGROUND\_SERVICE\_MEDIA\_PLAYBACK |
| Histórico vazio para doses extras | INNER JOIN excluía registros sem medicação associada | Migração para LEFT JOIN |

## **5\. Processo de Garantia de Qualidade (QA)**

O desenvolvimento da versão 0.5.0 empregou uma estratégia de **garantia de qualidade híbrida**, combinando **testes manuais exploratórios** com **verificações automatizadas** integradas ao pipeline de *build*.

### **5.1 Testes Manuais Exploratórios**

#### **5.1.1 Metodologia**

Para cada alteração significativa (*fix* ou *feat*), foi adotado o seguinte protocolo de **teste de regressão manual**:

1. **Instalação Limpa**: O aplicativo era desinstalado do emulador Android Studio e reinstalado a partir do APK recém-compilado. Isso garantia que estados residuais de versões anteriores não mascarassem bugs.  
2. **Configuração do Ambiente**: Emulador configurado com API 34 (Android 14), simulando cenários de uso real.  
3. **Fluxo Completo de Usuário**: Execução de *end-to-end tests* manuais, incluindo:  
   * Cadastro de novo medicamento  
   * Configuração de horários e frequência  
   * Disparo de alarme em tela bloqueada  
   * Ações de "Tomei", "Pular" e "Soneca"  
   * Verificação do histórico de medicações  
   * Edição e exclusão de medicamentos  
4. **Identificação de Bugs e Features**: Comportamentos inesperados eram corrigidos no mesmo ciclo de desenvolvimento, seguindo a prática de **Continuous Integration/Continuous Delivery (CI/CD)** em ambiente local.

#### **5.1.2 Cenários de Teste Críticos**

| Cenário | Objetivo | Resultado Esperado |
| :---- | :---- | :---- |
| Alarme com tela bloqueada | Verificar exibição full-screen | Card aparece sobre lockscreen |
| Alarme em modo silencioso | Verificar bypass de DND | Som toca normalmente |
| Soneca múltipla | Verificar reagendamento | Alarme dispara após 10 min |
| Edição de medicamento | Verificar persistência de status | Status de doses passadas mantido |
| Histórico de dose extra | Verificar registro de custom meds | Dose aparece no histórico |

### **5.2 Testes Automatizados de Build**

#### **5.2.1 Pipeline Gradle**

O sistema de *build* Android utiliza o **Gradle** como ferramenta de automação. As seguintes verificações são executadas automaticamente durante o processo de compilação:

// app/build.gradle.kts  
testImplementation("junit:junit:4.13.2")  
androidTestImplementation("androidx.test.ext:junit:1.1.5")  
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")  
androidTestImplementation("androidx.room:room-testing:2.6.1")

#### **5.2.2 Verificações Automáticas no Build**

| Tipo | Ferramenta | Momento de Execução |
| :---- | :---- | :---- |
| **Lint Analysis** | Android Lint | ./gradlew lint |
| **Compile-time Checks** | KSP \+ Kotlin Compiler | Durante assembleDebug |
| **Resource Validation** | AAPT2 | Durante geração de APK |
| **Dependency Resolution** | Gradle Dependency Manager | No início do build |
| **Room Schema Validation** | Room Compiler (KSP) | Durante anotação processing |

#### **5.2.3 Verificação de Integridade do Schema Room**

O **Room Compiler** (via KSP) valida automaticamente:

* Consistência entre entidades (@Entity) e DAOs (@Dao)  
* Sintaxe SQL de queries @Query  
* Relacionamentos e chaves estrangeiras  
* Migrações de banco de dados

#### 5.2.4 Análise Estática de Código

O **Android Lint** detecta automaticamente:

* Problemas de acessibilidade  
* Uso de APIs obsoletas  
* Falhas de internacionalização  
* Problemas de performance  
* Possíveis crashes em tempo de execução

### **5.3 Justificativa da Abordagem**

A escolha de uma abordagem **híbrida** (manual \+ automatizada) foi fundamentada nos seguintes fatores:

1. **Complexidade de UI/UX**: Interações de alarme full-screen sobre lockscreen são difíceis de automatizar com Espresso devido a limitações de acesso a componentes de sistema.  
2. **Variedade de Dispositivos**: Comportamentos de WakeLock, AlarmManager e exibição sobre lockscreen variam entre fabricantes (Samsung, Xiaomi, etc.), exigindo validação visual.  
3. **Ciclo de Desenvolvimento Ágil**: Para um projeto de escopo acadêmico/pessoal, testes manuais exploratórios fornecem *feedback* mais rápido que a criação de suítes automatizadas completas.  
4. **Cobertura Complementar**: Testes automatizados de build garantem integridade estrutural, enquanto testes manuais validam comportamento funcional e experiência do usuário.

## **6\. Conclusão**

A versão 0.5.0 é um marco para o Dose Certa. Com a introdução do **Alarme Forçado**, o aplicativo se torna uma assistência ativa na vida do usuário. As refatorações técnicas asseguram que essa nova complexidade não comprometa a estabilidade, entregando um produto sólido, confiável e agradável de usar.

A garantia de qualidade foi alcançada através da execução sistemática de verificações automatizadas durante o *build* e testes manuais exploratórios após cada alteração. Essa abordagem validou a integridade estrutural do código e o comportamento funcional da aplicação, assegurando que cada *commit* entregue uma versão estável e testada.

Futuras implementações visam adicionar mais funcionalidades e melhorias na experiência do usuário de acordo com requisitos descritos no início do projeto e novas ideias que surgiram ao longo do tempo. Como por exemplo, a função de Geração de Relatório. O objetivo para versão 1.0.0 é lançar o aplicativo gratuitamente para Android pela Google Play Store.

*Gabriel Pereira G. Santos*  
*Atualizado em 12 de Dezembro de 2025*