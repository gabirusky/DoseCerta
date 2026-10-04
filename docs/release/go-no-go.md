# Go/no-go de lançamento

Estado atualizado em 04/10/2026: **NO-GO para submissão e publicação**. Build/lint/JVM, persistência/PDF e alarmes API26 têm evidência aprovada; ver [registro atual](../qa/task-completion-20261004.md). A matriz completa e os gates externos permanecem pendentes. As descrições de preparação abaixo são históricas quando superadas por esse registro. Este documento distingue o código revisável, o candidato técnico validado e a aprovação externa.

## Identidade do candidato

Preencher quando houver um AAB de release assinado e congelado. O APK de debug anterior não substitui o candidato.

| Campo | Registro atual |
|---|---|
| Aplicação | `com.dosecerta`; registro/reserva no Console ainda não confirmado |
| Nome/versão | Dose Certa / `1.0`; `versionCode` final deve superar o já usado no Console |
| Source revision e mudanças locais | Base `cc2ca144e10c720345ed6924c7b7e7ef27c2cfbe` com mudanças locais; registrar revisão final e patch congelado |
| AAB e SHA-256 | Pendente |
| Certificado de upload e Play App Signing | Pendente; registrar somente fingerprint público, nunca chave/senha |
| APKs gerados do AAB / hashes | Pendente |
| Relatórios vinculados ao candidato | Pendente; cada relatório deve identificar o hash/revisão que foi testado |

O `versionCode` local padrão é `1`, com override `-PreleaseVersionCode=N`. A configuração de assinatura lê `DOSECERTA_UPLOAD_KEYSTORE`, `DOSECERTA_UPLOAD_STORE_PASSWORD`, `DOSECERTA_UPLOAD_KEY_ALIAS` e `DOSECERTA_UPLOAD_KEY_PASSWORD`. Nenhuma credencial foi definida nesta sessão. A existência dessa configuração não comprova assinatura válida.

## Gate técnico

Todas as linhas obrigatórias precisam de evidência do candidato final. Um resultado anterior a mudanças de código fica registrado como histórico e deve ser repetido quando essas mudanças afetam o cenário.

| Gate | Evidência necessária | Estado atual |
|---|---|---|
| Build/lint/unitários | Build release reproduzível, lint sem erro bloqueante e resultados de domínio com revisão registrada | Há build debug anterior e resultados anteriores; fontes mudaram depois. Validação final adiada |
| Persistência e upgrades | Schemas suportados → schema 4, dados/histórico preservados, operações concorrentes e exclusão sem ressurgimento | Testes instrumentados preparados; validação final pendente |
| Recorrência e dose | Datas de calendário, dias 29–31, DST, fuso/hora, identidade, estado, adesão e correções | Contratos/código preparados; novo caso do dia 30 ainda não executado |
| Alarmes reais | Jornada pelo cadastro normal, horário real, bloqueio, permissões concedidas/negadas/revogadas, ações isoladas, som finito, reboot e force-stop conforme contrato | Harness/runbook preparados; evidências finais pendentes |
| Compatibilidade | APIs/formatos da matriz V01, imagem 16 KB com `PAGE_SIZE=16384`, instalação e smoke do candidato; OEMs quando retomados | Emuladores autorizados; validação final e OEMs pendentes |
| Artefato/16 KB | AAB real, inventário transitivo, ELF e APK ZIP alignment quando houver `.so`, APKs gerados do mesmo AAB | Ferramentas locais prontas; AAB/APKs finais e runtime pendentes |
| Privacidade | Manifesto release efetivo, CE/FDE conforme ADR, detalhes bloqueados, backup/transfer excluídos em restore real e exclusão local | Decisão/código preparados; validação final pendente |
| UI/acessibilidade | Todas as telas, fontes grandes, claro/escuro, PT/EN, leitor de tela, rotação/estado, sucesso/erro/cancelamento e pessoas reais de V08 | Revisão estática/harness disponíveis; critérios runtime/pessoas pendentes |
| PDF e escala | Período/filtro imutáveis, contagem/ordem, PDF reaberto, páginas longas, falha/cancelamento SAF, benchmark de escala | Gerador/testes preparados; critérios runtime e escala pendentes |
| Bateria/estabilidade | Jornada repetida, sem crash/ANR/vazamento, janela de 24 h e medição conforme V/E | Pendente; teste curto não satisfaz uma janela de 24 h |
| Licenças/ativos | LICENSE coerente, POMs exatos, avisos preservados, fonte correspondente e direitos dos ativos confirmados pelo responsável | Inventário/documentação locais preparados; conferir candidato e ativos finais |

S01–S06 estão condicionadas à revisão da ameaça em [data-protection.md](../adr/data-protection.md). A decisão atual usa proteção do SO, sem alegar que Room é criptografado. Se a condição mudar, ativar esses gates e repetir migração, artefato, performance, alarmes e PDF.

## Gate de submissão

| Gate externo | Evidência necessária | Estado atual |
|---|---|---|
| Conta/entidade e pacote P01 | Conta elegível verificada, dados públicos e categoria de saúde adequados | Não definido |
| Política/contato P05–P06 | URL pública ativa, texto final, contato operacional e links no app/loja | Texto local preparado; URL/contato não definidos |
| Assinatura P08 | Upload key e versionCode corretos; certificados conferidos | Não definido |
| Loja P09 | Textos, ícone/graphic e capturas reais do mesmo candidato com direitos confirmados | Rascunho local; capturas finais pendentes |
| Play P10–P12 | Instalação pela faixa de testes, requisitos aplicáveis da conta, formulários saúde/Data safety/conteúdo/FSI/FGS e pre-launch analisado | Sem acesso/envio ao Console |
| Aprovação P13 | Responsável confere gates e aprova promover o AAB exato validado | Pendente |

Não promover outro AAB depois da aprovação. Se código, dependência, versão, manifesto ou assinatura mudar, gerar novo candidato e atualizar hashes/evidências afetadas. Publicação é ação do responsável com acesso ao Console; este registro não representa autorização automática para publicar.

## Registro final de decisão

Após concluir os gates, registrar data, responsável, decisão GO/NO-GO, links de evidência, hash do AAB, versionCode, faixa/percentual pretendidos e riscos aceitos explicitamente. Para NO-GO, listar os IDs ainda abertos e próximos passos. Pendências externas permanecem em [external-gates.md](external-gates.md).
