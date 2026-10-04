# Inventário de dependências e 16 KB — B06

O inventário parte do classpath resolvido e do APK/AAB real. Linguagem Kotlin, ausência de pasta NDK ou emulador com páginas de 4 KB não comprovam compatibilidade com 16 KB. A orientação de ELF, ZIP alignment e execução na imagem adequada está na [documentação Android](https://developer.android.com/guide/practices/page-sizes), consultada em 30/09/2026.

Evidência local em 30/09/2026, limitada ao artefato anterior às últimas mudanças: [debug-apk-audit-before-final.json](checks/debug-apk-audit-before-final.json) identifica o APK de 7.624.164 bytes, SHA-256 `a242437db8d5f2dab3c8fefab05340a308e2bf643c38ef70e00e056e851451ba`, AGP 8.13.2 e nenhuma `.so`. O snapshot de dependências de debug está em [debug-dependency-inventory.json](checks/debug-dependency-inventory.json): 90 componentes binários, nenhum `.so`, nenhuma licença não resolvida. Esses inventários são inspeções de arquivos, sem execução de teste. O APK não inclui as fontes/assets adicionados depois de sua compilação; repetir no candidato final.

## Classpath e licenças

O coordenador de build executa os comandos Gradle de forma serial, evitando concorrência sobre intermediários/KSP:

```bash
env JAVA_HOME=/home/gabirusky/Programs/android-studio/jbr ./gradlew --console=plain :app:dependencies --configuration releaseRuntimeClasspath > docs/qa/checks/release-dependencies.txt
python3 scripts/qa/audit_dependencies.py --input docs/qa/checks/release-dependencies.txt --configuration releaseRuntimeClasspath
```

`audit_dependencies.py` resolve as versões efetivas do relatório, ignora constraints como artefatos, usa POMs exatos/parent, guarda cópias e SHA-256 dos metadados e lista AAR/JAR reais. Inspeciona `.so` e LICENSE/NOTICE/COPYING/COPYRIGHT no arquivo e nos JARs internos. Avisos encontrados são preservados byte a byte em `docs/release/licenses/bundled`; ausência de aviso em um binário não remove obrigações de atribuição do source.

Se faltarem POMs exatos, o script registra erro em vez de assumir a licença da família de bibliotecas. Metadados já conferidos podem ficar em `docs/release/dependency-metadata`. `--format lint-package` aceita o modelo de dependências do lint para um snapshot de debug; esse escopo deve ser rotulado e não substitui `releaseRuntimeClasspath` do candidato.

O JSON registra nós somente de metadados/plataforma separadamente dos binários. A presença no classpath não comprova que todas as classes chegaram ao APK após otimização; a análise do artefato real complementa o inventário.

## Artefato e alinhamento

```bash
python3 scripts/qa/audit_artifact.py app/build/outputs/apk/debug/app-debug.apk
python3 scripts/qa/audit_artifact.py app/build/outputs/bundle/release/app-release.aab --require-native-alignment
```

O relatório inclui hash/tamanho do arquivo, bibliotecas `.so` reais e seus hashes, segmentos ELF `PT_LOAD`, `p_align`, congruência de endereço/offset e offset no ZIP. Para `.so` descomprimida em APK, exige alinhamento de 16384 bytes. Para AAB, ZIP alignment é conferido nos APKs gerados a partir do bundle; o layout interno do AAB não comprova o layout entregue pelo instalador. Biblioteca comprimida não é diretamente mapeada do ZIP, mas ainda precisa de ELF compatível depois da extração.

`--require-native-alignment` retorna erro se um ELF não tiver alinhamento adequado ou uma `.so` descomprimida de APK estiver desalinhada. Ele não declara runtime aprovado. Conferir também os APKs finais com a ferramenta Android `zipalign -c -P 16 -v 4` e, quando houver `.so`, confrontar o relatório com `llvm-objdump -p`/ferramenta oficial. Registrar versões das ferramentas. Verificar configuração de page alignment do bundle com `bundletool dump config` quando aplicável.

## Gate runtime

Em AVD dedicado com imagem 16 KB, registrar serial, AVD, fingerprint, API, ABI, fuso e:

```bash
adb -s SERIAL shell getconf PAGE_SIZE
```

O resultado obrigatório é `16384`. Instalar os APKs gerados do mesmo AAB, registrar hashes/certificado/versionCode e executar abertura, cadastro, lembrete real, ação, histórico e PDF. Anexar a captura/log/resultado com timestamps e observar crash/ANR. Repetir o cenário após upgrade quando houver banco anterior. O teste está adiado pela instrução atual do usuário.

Observação já registrada: o emulador pessoal `emulator-5554` apresentou `PAGE_SIZE=4096`. Esse dado descreve o ambiente de 4 KB e não fecha o gate. Mesmo um candidato sem `.so` precisa do teste na imagem 16 KB exigido por TASKS.md. Não limpar ou substituir o AVD pessoal para criar essa evidência.
