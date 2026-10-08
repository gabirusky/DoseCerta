# Política de privacidade — DoseCerta

Versão em preparação para v1.0, 29/09/2026. Texto destinado a publicação pública após definir contato operacional e URL. Nenhuma URL pública é alegada ativa nesta versão.

DoseCerta organiza lembretes, medicamentos e registros pessoais. Não substitui orientação de um profissional de saúde, não prescreve tratamentos, não é um serviço de emergência e não é apresentado como dispositivo médico certificado. Siga a prescrição recebida e consulte um profissional antes de alterar uma dose.

Os dados inseridos incluem nome de medicamento, dose, unidade, forma, posologia, horários, observações e confirmações/pulos. Preferências incluem idioma, som e visibilidade dos detalhes nas notificações da tela bloqueada. O cartão do alarme mostra nome, dose e horário e permite registrar tomada, pular ou adiar 10 minutos sem desbloquear e sem confirmação adicional. A escolha de ocultar detalhes afeta as notificações, enquanto o cartão do alarme continua completo. O aplicativo processa esses dados no dispositivo. Não usa contas de usuário, servidor de pacientes, anúncios, SDK de analytics ou upload de diagnóstico. A versão entregue deve manter ausência de permissão INTERNET e de SDKs de rede no manifesto auditado.

O banco Room/SQLite e as preferências ficam na área privada do aplicativo. Room padrão não fornece criptografia independente do banco. A proteção contra perda de aparelho bloqueado depende da criptografia e do bloqueio de tela efetivos do Android. Aparelhos antigos, desbloqueados, comprometidos ou com acesso root não oferecem a mesma proteção. A decisão e os limites estão no ADR de proteção de dados; não há promessa de segurança absoluta ou certificação legal.

O backup automático e a transferência de banco/preferências entre aparelhos estão desabilitados/excluídos nas regras da versão candidata. Desinstalar ou limpar os dados remove os dados locais do aplicativo. O histórico permanece ao arquivar medicamentos. A exclusão permanente permite escolher preservar o histórico com seus detalhes originais ou também apagá-lo. Registros apagados pelo usuário não devem ser recriados pela reconciliação de lembretes.

O usuário pode gerar um PDF e escolher um destino no seletor de documentos do Android. O PDF contém dados sensíveis do período escolhido. O provedor escolhido pode guardar/sincronizar essa cópia segundo suas próprias regras. Abrir/compartilhar concede acesso à cópia ao aplicativo escolhido pelo usuário. Essas cópias ficam fora do controle do DoseCerta e podem permanecer após exclusão dos registros ou desinstalação. Remova-as também do destino se desejar apagar todas as cópias.

Permissões usadas: notificações e canal para avisos; acesso a alarmes exatos para horários precisos quando autorizado; intenção de tela cheia para o card de alarme quando o sistema permitir; serviço de reprodução para som de duração finita; vibração; restauração de agendamentos após reinício; wake lock limitado ao ciclo do alarme. Não exige sobreposição sobre outros aplicativos nem acesso geral ao armazenamento. Negativas podem limitar som/precisão/tela cheia e são mostradas em Configurações. Não há promessa de contornar Não Perturbe ou restrições de fabricantes.

O diagnóstico local registra etapas técnicas e identificadores opacos em uma lista limitada. Não registra nome de medicamento/dose nem envia informações. O usuário pode acessar/corrigir/excluir os registros nas telas do app, e controlar os PDFs que exportar.

Contato do responsável: **pendente de definição e verificação antes da publicação**. Autor anunciado no repositório: Gabriel Pereira. Esta política descreve comportamento de software e não declara, por si só, conformidade integral com qualquer lei.
