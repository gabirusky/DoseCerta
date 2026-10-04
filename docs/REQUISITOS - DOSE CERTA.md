## **Requisitos de Software para o app Dose Certa**

Para o desenvolvimento do aplicativo "Dose Certa", uma solução nativa para Android em Kotlin, apresento uma análise detalhada dos requisitos de software. Este documento abrange os requisitos funcionais, não funcionais, de domínio e os inovadores requisitos subconscientes, visando guiar a equipe de desenvolvimento na criação de uma ferramenta robusta, segura e, acima de tudo, centrada no usuário.

### **Requisitos Funcionais**

Os requisitos funcionais descrevem as ações e funcionalidades que o sistema deve ser capaz de executar. Para a primeira versão do "Dose Certa", focada no gerenciamento de medicamentos, os seguintes requisitos são essenciais:

| ID | Requisito | Descrição | Prioridade |
| :---- | :---- | :---- | :---- |
| **RF-001** | **Cadastro de Medicamentos** | O usuário deve poder adicionar um novo medicamento à sua lista, inserindo informações como nome, dosagem (ex: mg, ml, gotas), forma farmacêutica (ex: comprimido, cápsula, xarope) e uma anotação opcional. | **Alta** |
| **RF-002** | **Configuração de Lembretes** | Para cada medicamento cadastrado, o usuário deve poder configurar múltiplos horários para os lembretes, com opções de frequência (diário, semanal, mensal, dias específicos). | **Alta** |
| **RF-003** | **Alarmes Personalizáveis** | O sistema deve gerar um alarme sonoro e uma notificação visual no horário configurado para o medicamento. O usuário deve poder escolher o som do alarme e a opção de adiá-lo ("soneca"). | **Alta** |
| **RF-004** | **Confirmação de Dose** | Na notificação de alarme, o usuário deve ter a opção de confirmar que tomou a medicação ("Tomei") ou que pulou a dose ("Pulei"). | **Alta** |
| **RF-005** | **Histórico de Medicação** | O aplicativo deve manter um registro de todas as doses confirmadas e puladas, que possa ser visualizado pelo usuário em formato de lista ou calendário. | **Média** |
| **RF-006** | **Edição e Exclusão de Medicamentos** | O usuário deve poder editar as informações de um medicamento já cadastrado, bem como suas configurações de lembrete, ou excluí-lo permanentemente da sua lista. | **Média** |
| **RF-007** | **Listagem de Medicamentos** | A tela inicial do aplicativo deve exibir a lista de medicamentos do usuário, com informações claras sobre os próximos horários de dose. | **Alta** |

### 

### **Requisitos Não Funcionais**

Estes requisitos definem como o sistema deve operar, focando em suas qualidades e na experiência do usuário. Para um aplicativo de saúde, são tão cruciais quanto os requisitos funcionais.

| Categoria | Requisito | Descrição |
| :---- | :---- | :---- |
| **Usabilidade** | **Interface Intuitiva e Acessível** | A interface deve ser limpa, com ícones claros e textos legíveis, seguindo as diretrizes de design do Android (Material Design). Deve ser fácil de usar por pessoas de diferentes faixas etárias, incluindo idosos com pouca afinidade tecnológica. |
| **Usabilidade** | **Feedback ao Usuário** | O aplicativo deve fornecer feedback visual imediato para as ações do usuário (ex: um "check" ao confirmar uma dose, uma mensagem de sucesso ao cadastrar um medicamento). |
| **Desempenho** | **Rapidez e Responsividade** | O aplicativo deve iniciar rapidamente e responder aos comandos do usuário em menos de 2 segundos. O consumo de bateria deve ser otimizado, especialmente em relação aos alarmes em segundo plano. |
| **Confiabilidade** | **Garantia dos Alarmes** | Os alarmes devem funcionar de forma confiável, mesmo que o aplicativo esteja fechado ou o dispositivo reinicie. Devem ser persistentes e não serem facilmente descartados pelo sistema operacional. |
| **Segurança** | **Privacidade dos Dados** | Todos os dados do usuário devem ser armazenados localmente no dispositivo de forma segura. Nenhuma informação de saúde deve ser transmitida para servidores externos nesta primeira versão. |
| **Compatibilidade** | **Versões do Android** | O aplicativo deve ser compatível com as versões do Android a partir da 8.0 (Oreo), garantindo que alcance uma vasta gama de dispositivos. |
| **Localização** | **Idioma** | O aplicativo deve ser totalmente em Português (Brasil). |

### **Requisitos de Domínio**

Estes requisitos são derivados do domínio da aplicação, neste caso, a área da saúde e bem-estar.

| ID | Requisito | Descrição |
| :---- | :---- | :---- |
| **RD-001** | **Terminologia Médica Acessível** | Utilizar termos simples e de fácil compreensão para o público leigo, evitando jargões médicos complexos na interface principal. |
| **RD-002** | **Aviso de Responsabilidade** | O aplicativo deve exibir, no primeiro uso e em uma seção de "Sobre", um aviso claro de que não substitui a orientação de um profissional de saúde e que o usuário deve sempre seguir a prescrição médica. |
| **RD-003** | **Conformidade com a LGPD** | Embora os dados sejam armazenados localmente, o aplicativo deve informar ao usuário, através de uma política de privacidade clara e acessível, como seus dados são tratados, garantindo a transparência exigida pela Lei Geral de Proteção de Dados (LGPD) do Brasil. |

### **Requisitos Subconscientes**

Estes são os requisitos não declarados, mas que impactam profundamente a percepção de valor e a confiança do usuário no aplicativo. São as expectativas implícitas que, quando atendidas, criam uma experiência positiva e fidelizam o usuário.

| Requisito | Descrição |
| :---- | :---- |
| **Sensação de Controle e Empoderamento** | O usuário deve sentir que está no controle total de seu tratamento. A interface deve ser projetada para empoderá-lo, dando-lhe fácil acesso e compreensão do seu histórico e da sua jornada de medicação. |
| **Confiança e Segurança Emocional** | O aplicativo deve transmitir uma sensação de segurança e confiabilidade. O design deve ser sóbrio e profissional, evitando cores e elementos que possam infantilizar a experiência ou gerar desconfiança. A ausência de falhas nos alarmes é fundamental para construir essa confiança. |
| **Simplicidade que Gera Paz de Espírito** | O fluxo de uso para as tarefas principais (adicionar medicamento, ver horários, confirmar dose) deve ser extremamente simples e rápido. A complexidade deve ser evitada a todo custo. O objetivo é reduzir a carga mental do usuário, não aumentá-la. O usuário deve pensar: "Ufa, agora não preciso mais me preocupar em esquecer". |
| **Inteligência Sutil** | O aplicativo deve parecer inteligente, mas sem ser intrusivo. Por exemplo, ao cadastrar um medicamento, o teclado numérico pode ser o padrão para o campo de dosagem. Pequenos detalhes de usabilidade que antecipam as necessidades do usuário contribuem para essa percepção. |
| **Respeito pelo Tempo e Atenção do Usuário** | As notificações devem ser precisas e relevantes. O aplicativo não deve enviar notificações desnecessárias ou de marketing. A interação deve ser focada e objetiva, respeitando que o usuário tem uma vida fora do aplicativo. |

## **Conclusão**

Ao seguir esta detalhada engenharia de requisitos, a equipe de desenvolvimento do "Dose Certa" estará bem equipada para criar um produto que não apenas funcione bem, mas que também se torne um companheiro de confiança na jornada de saúde de seus usuários, cumprindo seu objetivo de mitigar problemas de adesão ao tratamento e promover o bem-estar.