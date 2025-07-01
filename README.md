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
    git clone <repository_url>
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
