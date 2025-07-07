# Dose Certa

![image](https://github.com/user-attachments/assets/fa460868-fc87-4dfb-8efc-f493d6d77341)



## Sobre o Projeto

Dose Certa é um aplicativo móvel desenvolvido para ajudar usuários a gerenciar seus medicamentos. Ele permite cadastrar diferentes medicamentos, definir horários e dosagens para lembretes, e acompanhar o histórico de administração.

O objetivo é simplificar o acompanhamento de tratamentos médicos e garantir que os usuários tomem seus medicamentos corretamente e na hora certa, promovendo maior adesão ao tratamento e bem-estar.

## Principais Funções

- **Cadastro de Medicamentos:** Adicione informações detalhadas sobre seus medicamentos, incluindo nome, dosagem, frequência e duração do tratamento.
- **Agendamento de Lembretes:** Configure lembretes personalizados para cada medicamento, garantindo que você seja notificado na hora de tomar sua dose.
- **Controle de Estoque (Opcional):** Acompanhe a quantidade de medicamento disponível para saber quando é necessário reabastecer.
- **Histórico de Doses:** Mantenha um registro das doses administradas para monitorar seu progresso e compartilhar informações com profissionais de saúde, se necessário.
- **Alertas Personalizáveis:** Configure tipos de alerta e sons para os lembretes.

## Build do APK (Instruções Básicas)

Para gerar o arquivo APK do projeto, siga os passos básicos abaixo.

1.  **Clonar repositório:**
    ```bash
    git clone https://github.com/gabirusky/DoseCerta
    cd DoseCerta
    ```
2.  **Abrir no Android Studio:**
    Import the project into Android Studio. Let Android Studio sync the project and download necessary dependencies.
3.  **Build do Projeto:**
    Go to `Build` -> `Make Project` in the Android Studio menu.
4.  **Generate Signed or Debug APK:**
    - For a debug APK: Go to `Build` -> `Build Bundles / APKs` -> `Build APKs`. The APK will be located in `app/build/outputs/apk/debug/`.
    - For a release (signed) APK: Go to `Build` -> `Build Bundles / APKs` -> `Generate Signed Bundle / APK...`. Follow the wizard to create a signing key (if you don't have one) and sign your application. The release APK will typically be in `app/build/outputs/apk/release/`.

Os arquivos APKs gerados estarão no diretório `app/build/outputs/apk/`.

## Estrutura do Projeto


  O projeto segue a arquitetura recomendada pelo Google para aplicativos Android, utilizando
  componentes do Android Jetpack.


   - `app/src/main/java/com/example/medtracker/`: Código-fonte principal do aplicativo.
     - `data/`: Contém as classes de dados (entidades do Room), DAOs (Data Access Objects) e o
       banco de dados.
       - AppDatabase.kt: Configuração do banco de dados Room.
       - Medication.kt, MedicationLog.kt: Entidades do banco de dados.
       - MedicationDao.kt, MedicationLogDao.kt: Interfaces para acesso ao banco de dados.
     - `ui/`: Contém as classes relacionadas à interface do usuário (Fragments, Activities,
       ViewModels).
       - MainActivity.kt: A atividade principal que hospeda os diferentes fragments da
         aplicação.
       - home/, history/, medications/: Pacotes para cada uma das principais seções do app,
         contendo seus respectivos Fragments, ViewModels e Adapters.
     - `viewmodel/`: Contém os ViewModels que fornecem dados para a UI e sobrevivem a mudanças
       de configuração.
     - `repository/`: Contém os repositórios que gerenciam as fontes de dados (banco de dados,
       rede, etc.).
     - `util/`: Classes utilitárias, como helpers para datas, notificações, etc.
   - `app/src/main/res/`: Recursos do aplicativo (layouts, strings, imagens, etc.).
     - layout/: Arquivos de layout XML para as telas.
     - drawable/: Ícones e outros recursos gráficos.
     - values/: Arquivos de recursos como strings, cores e estilos.

## Tecnologias e Bibliotecas


   - Linguagem: Kotlin
   - Arquitetura: MVVM (Model-View-ViewModel)
   - Componentes do Jetpack:
     - Room: Para persistência de dados locais.
     - ViewModel: Para gerenciar dados da UI de forma consciente do ciclo de vida.
     - LiveData: Para construir objetos de dados observáveis.
     - Navigation Component: Para gerenciar a navegação entre as telas do app.
     - RecyclerView: Para exibir listas de dados de forma eficiente.
   - Outras bibliotecas:
     - Coroutines: Para gerenciamento de tarefas assíncronas.
     - Material Components for Android: Para um design moderno e consistente.

## Contribuições


  Contribuições são bem-vindas! Se você tiver sugestões de melhorias, correções de bugs ou
  novas funcionalidades, sinta-se à vontade para abrir uma issue ou enviar um pull request.

## Licença

  Este projeto está licenciado sob a Licença MIT (LICENSE).

