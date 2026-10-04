# Licenças e autoria

README anterior anuncia GPL-3.0 e Gabriel Pereira; histórico Git também identifica Gabriel Pereira. LICENSE contém texto GPLv3 canônico distribuído pelo sistema (`/usr/share/licenses/binutils/COPYING3`), sem inventar nova atribuição de direitos ou ano de copyright.

## Inventário local efetivamente conferido

Em 30/09/2026, o snapshot `debug-package-snapshot` do modelo resolvido de lint identificou **90 componentes e 90 arquivos binários AAR/JAR**. Os POMs exatos, ou parent exato no caso de `listenablefuture:1.0`, declaram Apache 2.0 para todos. Não foram encontrados arquivos `.so` ou avisos LICENSE/NOTICE/COPYING/COPYRIGHT nos binários e JARs internos inspecionados. Isto descreve esses arquivos, sem afirmar que o source upstream não contém atribuições.

Evidência: [inventário JSON](../qa/checks/debug-dependency-inventory.json), [avisos por componente](THIRD_PARTY_NOTICES-debug.md) e cópias dos POMs em `dependency-metadata/`. O JSON inclui configuração, hash do relatório, coordenadas selecionadas, arquivos/hashes, licenças declaradas e problemas de resolução; nesta execução não há problemas pendentes. Bibliotecas do toolchain, KSP e testes não são rotuladas como componentes do app só porque estão no cache.

Quatro POMs ausentes do cache foram obtidos do Google Maven em suas versões exatas; o parent `guava-parent:26.0-android` veio do Maven Central. O texto [Apache-2.0.txt](licenses/Apache-2.0.txt) veio do [site oficial Apache](https://www.apache.org/licenses/LICENSE-2.0.txt). As cópias integrais da licença Apache 2.0 e da GPL anunciada pelo projeto estão também em `app/src/main/assets/licenses/`, para inclusão no próximo artefato compilado. Não se afirma que o APK anterior já contém esses novos assets.

O inventário de debug é evidência local histórica. Regenerar a lista para o **releaseRuntimeClasspath final**, conferir o APK/AAB real e conservar quaisquer avisos novos antes da distribuição. O procedimento e as limitações estão em [artifact-audit.md](../qa/artifact-audit.md). A presença de dependência no classpath não comprova que toda classe foi empacotada depois de uma eventual otimização.

## Fonte correspondente e direitos

A licença do projeto preserva a intenção já anunciada no repositório. Não foi criada autorização de terceiro nem escolhido silenciosamente um regime `-only`/`-or-later` além do anúncio GPL-3.0 existente. O responsável confirma autoria, escopo da concessão e direito de distribuir os ativos finais antes da Play; terceiros continuam donos de suas bibliotecas e marcas.

Para a versão distribuída, conservar a fonte correspondente exata, scripts/configuração necessários para produzir o binário e avisos upstream. Vincular a revisão/patch ao hash do AAB no go/no-go. A base pública antiga, sozinha, não equivale à fonte das mudanças locais atuais. Preservar notices/copyrights existentes ao distribuir source. Não há importação de arte gráfico externo nova nesta frente de trabalho; a revisão dos ícones/imagens originais e dos recursos de loja pelo responsável continua pendente.

P07 local: LICENSE, textos integrais e inventário/avisos revisáveis presentes. A confirmação de direitos e a conferência do candidato distribuído continuam gates explícitos; não há conclusão jurídica irrestrita de compatibilidade ou aprovação de publicação.
