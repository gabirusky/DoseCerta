# Proveniência dos metadados de dependências

Os POMs desta pasta são cópias byte a byte das versões efetivamente inventariadas. A fonte/cache e SHA-256 de cada POM, binário AAR/JAR e relatório de entrada estão no JSON da configuração em `docs/qa/checks/`. Eles documentam as declarações upstream; não concedem direitos novos sobre o projeto.

O gerador usa primeiro a cópia local exata e depois o cache Gradle da mesma coordenada. Quando há herança de licença, conserva também o POM do parent exato. Nunca substitui por parent de outra versão ou atribui a licença de uma biblioteca à família inteira.

Metadados inicialmente ausentes do cache, obtidos em 30/09/2026:

- `androidx.customview:customview-poolingcontainer:1.0.0`: [Google Maven](https://dl.google.com/dl/android/maven2/androidx/customview/customview-poolingcontainer/1.0.0/customview-poolingcontainer-1.0.0.pom).
- `androidx.emoji2:emoji2:1.2.0`: [Google Maven](https://dl.google.com/dl/android/maven2/androidx/emoji2/emoji2/1.2.0/emoji2-1.2.0.pom).
- `androidx.lifecycle:lifecycle-process:2.7.0`: [Google Maven](https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-process/2.7.0/lifecycle-process-2.7.0.pom).
- `androidx.profileinstaller:profileinstaller:1.3.0`: [Google Maven](https://dl.google.com/dl/android/maven2/androidx/profileinstaller/profileinstaller/1.3.0/profileinstaller-1.3.0.pom).
- `com.google.guava:guava-parent:26.0-android`: [Maven Central](https://repo.maven.apache.org/maven2/com/google/guava/guava-parent/26.0-android/guava-parent-26.0-android.pom).

Regenerar com `scripts/qa/audit_dependencies.py` quando a configuração ou as dependências mudarem. O snapshot de debug é rotulado como tal; usar o inventário da configuração release final no pacote de revisão de lançamento.
