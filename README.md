<div align="center">

![App Icon](https://github.com/user-attachments/assets/fa460868-fc87-4dfb-8efc-f493d6d77341)
<h3>Dose Certa</h3>

## 

<img width="250" height="513" alt="Screenshot (2)" src="https://github.com/user-attachments/assets/480cc15c-3665-4f4f-8e53-52805dc1ac85" />
<img width="250" height="513" alt="Screenshot (4)" src="https://github.com/user-attachments/assets/9d93750c-60e0-4158-abcd-f654c2b4f776" />
<img width="250" height="513" alt="Screenshot (3)" src="https://github.com/user-attachments/assets/16887194-0aeb-4dc8-88ae-8ce607fe7de0" />

</div>

## Sobre o Projeto

**Dose Certa** é um aplicativo Android nativo desenvolvido para ajudar usuários a gerenciar seus medicamentos de forma simples e eficaz. Com uma interface moderna e intuitiva, o app permite cadastrar medicamentos, configurar lembretes personalizados e acompanhar o histórico completo de administração.

O objetivo é promover maior adesão ao tratamento médico, garantindo que você tome seus medicamentos corretamente e no horário certo, contribuindo para seu bem-estar e saúde.

## ✨ Principais Funcionalidades

- ✅ **Cadastro Completo de Medicamentos** - Nome, dosagem, unidade, frequência e anotações
- ⏰ **Alarmes Estilo Despertador** - Lembretes com tela fullscreen, vibração e som personalizado
- 📊 **Painel de Estatísticas** - Acompanhe sua adesão semanal com gráficos visuais  
- 📅 **Histórico Detalhado** - Visualize doses tomadas, perdidas e puladas
- 🔔 **Notificações Personalizadas** - Alertas com ações rápidas (Tomei, Pular, Adiar)
- 🔄 **Relembretes Automáticos** - Notifica novamente doses não tomadas após intervalo configurável
- 🎨 **Interface Moderna** - Design Material 3 com tema claro e escuro
- 🌐 **Suporte Multilíngue** - Português (BR) e Inglês
- 🔒 **Privacidade Total** - Todos os dados são armazenados localmente (LGPD)

## 🏗️ Estrutura do Projeto

```
DoseCerta/
├── app/src/main/java/com/dosecerta/
│   ├── data/                          # Camada de dados
│   │   ├── local/
│   │   │   ├── entity/               # Entidades Room (Medication, Schedule, MedicationLog)
│   │   │   ├── dao/                  # Data Access Objects
│   │   │   └── DoseCertaDatabase.kt  # Configuração do banco de dados
│   │   ├── model/                    # Modelos de domínio
│   │   └── repository/               # Repositórios (abstração de dados)
│   │       └── MedicationRepository.kt
│   ├── ui/                           # Camada de apresentação
│   │   ├── home/                     # Tela inicial com medicações do dia
│   │   │   ├── HomeFragment.kt
│   │   │   ├── HomeViewModel.kt
│   │   │   └── ScheduleAdapter.kt
│   │   ├── medications/              # Lista e gerenciamento de medicamentos
│   │   │   ├── MedicationsFragment.kt
│   │   │   ├── MedicationsViewModel.kt
│   │   │   └── MedicationAdapter.kt
│   │   ├── addmedication/            # Adicionar/editar medicamento
│   │   │   ├── AddMedicationFragment.kt
│   │   │   ├── AddMedicationViewModel.kt
│   │   │   └── ScheduleTimeAdapter.kt
│   │   ├── history/                  # Histórico e estatísticas
│   │   │   ├── HistoryFragment.kt
│   │   │   ├── HistoryViewModel.kt
│   │   │   └── LogAdapter.kt
│   │   ├── settings/                 # Configurações
│   │   │   └── SettingsFragment.kt
│   │   ├── privacy/                  # Política de privacidade
│   │   │   └── PrivacyFragment.kt
│   │   └── setup/                    # Wizard de configuração inicial
│   │       ├── SetupActivity.kt
│   │       ├── SetupNotificationsFragment.kt
│   │       ├── SetupTermsFragment.kt
│   │       └── SetupTutorialFragment.kt
│   ├── alarm/                        # Sistema de alarmes (estilo despertador)
│   │   ├── AlarmScheduler.kt         # Agendamento de alarmes
│   │   ├── AlarmService.kt           # Serviço de foreground para alarmes
│   │   ├── AlarmActivity.kt          # Activity fullscreen de alarme
│   │   ├── AlarmSoundManager.kt      # Gerenciamento de áudio do alarme
│   │   ├── MedicationAlarmReceiver.kt
│   │   └── BootCompletedReceiver.kt
│   ├── notification/                 # Gerenciamento de notificações
│   │   ├── NotificationHelper.kt
│   │   ├── NotificationActionReceiver.kt
│   │   ├── MarkMissedReceiver.kt     # Marcação automática de doses perdidas
│   │   └── MissedReminderReceiver.kt # Lembrete de doses não tomadas
│   ├── util/                         # Utilitários
│   │   ├── DateTimeUtils.kt
│   │   └── SampleDataProvider.kt
│   └── DoseCertaApplication.kt       # Application class
├── app/src/main/res/
│   ├── layout/                       # Layouts XML
│   ├── drawable/                     # Recursos gráficos e gradientes
│   ├── navigation/                   # Navigation graph
│   ├── values/                       # Strings, cores, temas, dimensões
│   ├── values-en/                    # Suporte a inglês
│   └── mipmap/                       # Ícones do app
└── gradle/                           # Configuração Gradle
```

## 🛠️ Tecnologias e Bibliotecas

### Core
- **Linguagem**: Kotlin 1.9.20
- **Min SDK**: API 26 (Android 8.0)
- **Target SDK**: API 34 (Android 14)
- **Build System**: Gradle 8.2.1 com Kotlin DSL

### Arquitetura
- **Pattern**: MVVM (Model-View-ViewModel)
- **Princípios**: Clean Architecture, Single Responsibility, Separation of Concerns

### Android Jetpack
- **Room Database** 2.6.1 - Persistência de dados local com TypeConverters
- **Navigation Component** 2.7.6 - Navegação declarativa entre telas
- **ViewModel** 2.7.0 - Gerenciamento de estado consciente do ciclo de vida
- **Lifecycle** 2.7.0 - Observação do ciclo de vida
- **DataStore Preferences** 1.0.0 - Armazenamento de preferências
- **Work Manager** 2.9.0 - Tarefas em background confiáveis

### UI/UX
- **Material Components** 1.11.0 - Material Design 3
- **RecyclerView** 1.3.2 - Listas eficientes e performáticas
- **View Binding** - Type-safe view references
- **Circular Progress Indicator** - Visualização de progresso

### Programação Assíncrona
- **Kotlin Coroutines** 1.7.3 - Gerenciamento de operações assíncronas
- **Flow & StateFlow** - Streams reativos de dados

### Processamento de Anotações
- **KSP** 1.9.20-1.0.14 - Kotlin Symbol Processing para Room

### Sistema de Alarmes
- **AlarmManager** - Agendamento preciso com alarmes exatos
- **Foreground Service** - AlarmService com notificação persistente
- **Full-Screen Intent** - Activity de alarme sobre lock screen
- **NotificationCompat** - Notificações ricas com ações

## 🎨 Design System

- **Paleta de Cores**: Tons oceânicos (Baltic Blue, Teal, Verdigris, Mint Leaf, Cream)
- **Temas**: Suporte completo a Light e Dark mode
- **Tipografia**: Sans-serif com hierarquia clara
- **Componentes**: Cards elevados, botões arredondados, gradientes suaves

## 🚀 Como Compilar

### Pré-requisitos
- Android Studio Hedgehog | 2023.1.1 ou superior
- JDK 17 (incluído no Android Studio)
- Android SDK API 34

### Passos

1. **Clone o repositório**
   ```bash
   git clone https://github.com/gabirusky/DoseCerta.git
   cd DoseCerta
   ```

2. **Abra no Android Studio**
   - File → Open → Selecione a pasta do projeto
   - Aguarde o Gradle Sync completar

3. **Build do Projeto**
   - Build → Make Project (Ctrl+F9)

4. **Gerar APK**
   - **Debug APK**: Build → Build Bundle(s) / APK(s) → Build APK(s)
     - Localização: `app/build/outputs/apk/debug/app-debug.apk`
   
   - **Release APK**: Build → Generate Signed Bundle / APK
     - Siga o wizard para criar/usar signing key
     - Localização: `app/build/outputs/apk/release/app-release.apk`

5. **Executar no Emulador/Dispositivo**
   - Run → Run 'app' (Shift+F10)

## 📋 Funcionalidades Implementadas

- [x] Tela inicial com medicações do dia e status em tempo real
- [x] Cadastro e edição de medicamentos com unidades personalizadas
- [x] Configuração de múltiplos horários por medicamento
- [x] Sistema de alarmes estilo despertador (fullscreen, som, vibração)
- [x] Ações rápidas: Tomar, Pular, Adiar (snooze configurável)
- [x] Marcação automática de doses perdidas
- [x] Relembretes para doses não tomadas (intervalo configurável)
- [x] Histórico completo com filtros por status
- [x] Edição/exclusão de logs no histórico
- [x] Estatísticas de adesão semanal
- [x] Tema claro e escuro
- [x] Suporte a Português (BR) e Inglês
- [x] Wizard de configuração inicial (tutorial + permissões)
- [x] Política de privacidade LGPD
- [x] Persistência local com Room Database
- [x] Reagendamento de alarmes após reinício do dispositivo

## 🔒 Privacidade e Segurança

O **Dose Certa** foi desenvolvido com foco total na privacidade do usuário:

- ✅ Todos os dados são armazenados **localmente** no dispositivo
- ✅ **Nenhuma informação** é enviada para servidores externos
- ✅ **Sem rastreamento** de atividades ou analytics
- ✅ **Sem requisição de permissões** desnecessárias
- ✅ Conformidade com a **LGPD** (Lei Geral de Proteção de Dados)

## 🤝 Contribuindo

Contribuições são muito bem-vindas! Para contribuir:

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/NovaFuncionalidade`)
3. Commit suas mudanças (`git commit -m 'Adiciona nova funcionalidade'`)
4. Push para a branch (`git push origin feature/NovaFuncionalidade`)
5. Abra um Pull Request

## 📄 Licença

Este projeto está licenciado sob a **GNU General Public License v3.0 (GPL-3.0)**.

Consulte o arquivo [LICENSE](LICENSE) para mais detalhes.

## 👨‍💻 Autor

**Gabriel Pereira**

- GitHub: [@gabirusky](https://github.com/gabirusky)

---

⭐ Se este projeto foi útil para você, considere dar uma estrela!
