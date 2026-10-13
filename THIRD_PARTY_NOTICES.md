# Componentes de terceiros — Finan+ para Android

O Finan+ é software livre sob a **GNU GPL versão 3 ou posterior** (`GPL-3.0-or-later`, ver [`LICENSE`](LICENSE)). Este arquivo lista todos os componentes de terceiros do app, com versão, origem, licença e o que cada licença exige. Cada componente continua sob a própria licença.

- **Dentro do app:** *Ajustes › Sobre › Componentes de terceiros* mostra os avisos de copyright, os NOTICE exigidos e os textos das licenças, sem internet. O conteúdo está em [`app/src/main/assets/licenca/TERCEIROS.txt`](app/src/main/assets/licenca/TERCEIROS.txt) e o texto da Apache 2.0 em [`APACHE-2.0.txt`](app/src/main/assets/licenca/APACHE-2.0.txt).
- **Textos originais das licenças e NOTICE:** [`third_party/licenses/`](third_party/licenses/) (copiados das fontes primárias indicadas abaixo, sem alteração).
- **Conferência automática:** o workflow [*Auditoria de licenças*](.github/workflows/license-audit.yml) gera o SBOM CycloneDX (`sbom.json`) e falha se aparecer dependência nova sem revisão, licença desconhecida ou alterada, licença incompatível no APK ou aviso faltando no app. A política revisada fica em [`third_party/license-policy.json`](third_party/license-policy.json).
- **Relatório da auditoria:** [`docs/OPEN_SOURCE_COMPLIANCE_AUDIT.md`](docs/OPEN_SOURCE_COMPLIANCE_AUDIT.md).

Versões: as **efetivamente resolvidas** pelo Gradle no `releaseRuntimeClasspath` da versão 1.5.0 (não apenas as declaradas em `gradle/libs.versions.toml`). Ao mudar uma dependência, o SBOM do CI é a referência atualizada.

## 1. Distribuídos no APK (F-Droid e GitHub Releases)

| Componente | Versão | Origem | Licença (SPDX) | Titular / aviso | Obrigações | Alteração pelo Finan+ | Onde é usado |
|---|---|---|---|---|---|---|---|
| **AndroidX (Jetpack)**: Core, Activity, Lifecycle, Fragment, Biometric, WorkManager, Glance, Compose (UI, Foundation, Material 3, Animation, Runtime, Material Icons Core, Ripple), Room, SQLite, DataStore, Emoji2, AppCompat, Profile Installer, Startup, Tracing, SavedState e demais `androidx.*` (lista completa abaixo) | Compose BOM 2025.05.01 (Compose UI 1.8.2, Material 3 1.3.2); Core 1.16.0; Activity 1.10.1; Lifecycle 2.9.1; Fragment 1.8.6; Biometric 1.1.0; Work 2.10.1; Glance 1.1.1 | https://developer.android.com/jetpack/androidx · https://android.googlesource.com/platform/frameworks/support | Apache-2.0 | The Android Open Source Project | Cópia da licença; manter avisos | Nenhuma | Todo o app |
| Protocol Buffers / protobuf-javalite (copiado dentro de `androidx.glance:glance-appwidget-external-protobuf` e de `androidx.datastore:datastore-preferences-core`; o POM do DataStore só declara Apache-2.0) | Glance 1.1.1; DataStore 1.0.0 | https://github.com/protocolbuffers/protobuf | BSD-3-Clause | Copyright 2008 Google Inc. | Reproduzir aviso e licença na documentação do binário | Nenhuma | Widget de saldo (Glance/DataStore) |
| Kotlin Standard Library (`kotlin-stdlib`, `-common`, `-jdk7`, `-jdk8`) | 2.1.21 (jdk7/jdk8: 1.8.22) | https://github.com/JetBrains/kotlin | Apache-2.0 (com trechos BSD-3-Clause do ThreeTen e BSL-1.0 do Boost, ver `license/README.md` do Kotlin) | JetBrains s.r.o. e colaboradores | Cópia da licença; avisos BSD do ThreeTen | Nenhuma | Todo o app |
| kotlinx.coroutines (`-core`, `-android`) | 1.7.3 | https://github.com/Kotlin/kotlinx.coroutines | Apache-2.0 | NOTICE: *kotlinx.coroutines library. Copyright 2016-2021 JetBrains s.r.o and respective authors and developers* | Cópia da licença; **reproduzir o NOTICE** | Nenhuma | Todo o app |
| kotlinx.serialization (`-core`, dependência transitiva do AndroidX) | 1.7.3 | https://github.com/Kotlin/kotlinx.serialization | Apache-2.0 | NOTICE do projeto (JetBrains s.r.o) | Cópia da licença; **reproduzir o NOTICE** | Nenhuma | Transitiva |
| JetBrains Java Annotations | 23.0.0 | https://github.com/JetBrains/java-annotations | Apache-2.0 | JetBrains s.r.o. | Cópia da licença | Nenhuma | Transitiva |
| JSpecify | 1.0.0 | https://github.com/jspecify/jspecify | Apache-2.0 | Projeto JSpecify | Cópia da licença | Nenhuma | Transitiva |
| Guava ListenableFuture | 1.0 | https://github.com/google/guava | Apache-2.0 | The Guava Authors | Cópia da licença | Nenhuma | Transitiva (WorkManager) |
| **PdfBox-Android** (adaptação do Apache PDFBox por Tom Roush) | 2.0.27.0 | https://github.com/TomRoush/PdfBox-Android | Apache-2.0 | NOTICE: Apache PDFBox, Copyright 2014 The Apache Software Foundation; pdfbox.org (2002-2007); PaDaF (Atos Worldline SAS); Adobe Glyph List e Zapf Dingbats Glyph List (Adobe); Unicode Scripts | Cópia da licença; **reproduzir o NOTICE**; avisos dos componentes externos (abaixo) | Nenhuma | Ler fatura ou boleto em PDF |
| ↳ Métricas AFM das 14 fontes básicas do PDF (assets `com/tom_roush/pdfbox/resources/afm`) | — | Adobe, via PdfBox-Android | Licença própria da Adobe para os AFM (permissiva, texto no LICENSE do PdfBox) | Adobe Systems Incorporated | Manter avisos; não distribuir os AFM sem o texto da licença; marcar modificações | Nenhuma | PdfBox-Android |
| ↳ CMaps e Adobe Glyph List (assets `com/tom_roush/fontbox/resources/cmap`, `.../glyphlist`) | — | Adobe, via PdfBox-Android | BSD-3-Clause (texto da Adobe) | Copyright 1990-2009 / 1997-2010 Adobe Systems Incorporated | Reproduzir aviso na documentação do binário | Nenhuma | PdfBox-Android |
| ↳ Fonte Liberation Sans Regular (asset `.../ttf/LiberationSans-Regular.ttf`) | 2.1.5 | https://github.com/liberationfonts/liberation-fonts | OFL-1.1 | Google Corporation (2010); Red Hat, Inc. (2012); *Reserved Font Name* Liberation | Distribuir com aviso e licença (também nos metadados da fonte); não vender a fonte sozinha | Nenhuma | PdfBox-Android |
| ↳ Dados Unicode `Scripts.txt` (10.0.0) e `BidiMirroring.txt` (8.0.0) | — | https://www.unicode.org/ | Unicode-DFS-2016 | Unicode, Inc. | Aviso de copyright e de permissão junto com os arquivos ou na documentação | Nenhuma | PdfBox-Android |
| **Bouncy Castle** (`bcprov`, `bcpkix`, `bcutil`, `-jdk15to18`) | 1.72 | https://www.bouncycastle.org · https://github.com/bcgit/bc-java | MIT (Bouncy Castle Licence) | Copyright (c) 2000-2021 The Legion of the Bouncy Castle Inc. | Manter aviso de copyright e de permissão | Nenhuma | Transitiva do PdfBox-Android (PDF com senha) |
| **Material Symbols Rounded** (peso 300) — 50 ícones em 54 arquivos (4 também na versão preenchida) | `master` de google/material-design-icons (traçados conferidos um a um em 13/10/2026) | https://github.com/google/material-design-icons | Apache-2.0 | Google | Cópia da licença; marcar arquivos modificados | Conversão de SVG para VectorDrawable, cor branca (anotada no topo de cada `res/drawable/ms_*.xml`) | Ícones da interface |
| Ícones do PWA do *Acesso pela rede* (`assets/lan/pwa/js/app.bundle.js`) | — | Material Symbols, via [Finan+ web](https://github.com/finanplus-web/finan_plus) | Apache-2.0 | Google | Cópia da licença (o PWA mostra a sua) | Copiados como caminhos SVG | Página servida pelo celular |
| Larguras de caracteres da Helvetica e Helvetica Bold no PWA (`HELVETICA`, `HELVETICA_BOLD` em `app.bundle.js`) | — | Métricas AFM das fontes básicas do PDF (Adobe), via Finan+ web | Licença AFM da Adobe (a mesma reproduzida para o PdfBox-Android) | Adobe Systems Incorporated | Manter aviso; texto da licença junto | Só os números de largura, na ordem WinAnsi | Relatório em PDF do PWA |

### Lista completa dos componentes `androidx.*` no APK

activity, activity-compose, activity-ktx (1.10.1) · annotation, annotation-jvm (1.9.1), annotation-experimental (1.4.1) · appcompat, appcompat-resources (1.2.0) · arch.core core-common, core-runtime (2.2.0) · autofill (1.0.0) · biometric (1.1.0) · collection, collection-jvm, collection-ktx (1.5.0) · compose-bom (2025.05.01) · compose animation, animation-core, foundation, foundation-layout, material-ripple, runtime, runtime-saveable, ui, ui-geometry, ui-graphics, ui-text, ui-tooling-preview, ui-unit, ui-util (1.8.2, com as variantes `-android`) · compose material-icons-core (1.7.8) · compose material3 (1.3.2) · concurrent-futures, concurrent-futures-ktx (1.1.0) · core, core-ktx (1.16.0), core-remoteviews (1.1.0), core-viewtree (1.0.0) · cursoradapter (1.0.0) · customview, customview-poolingcontainer (1.0.0) · datastore, datastore-core, datastore-preferences, datastore-preferences-core (1.0.0) · drawerlayout (1.0.0) · emoji2 (1.4.0) · fragment, fragment-ktx (1.8.6) · glance, glance-appwidget, glance-appwidget-proto, glance-material3 (1.1.1) · graphics-path (1.0.1) · interpolator (1.0.0) · lifecycle common, common-java8, common-jvm, livedata, livedata-core, livedata-core-ktx, process, runtime, runtime-compose, runtime-ktx, service, viewmodel, viewmodel-ktx, viewmodel-savedstate (2.9.1, com as variantes `-android`) · loader (1.0.0) · profileinstaller (1.4.0) · room-common, room-ktx, room-runtime (2.6.1) · savedstate, savedstate-ktx (1.3.0) · sqlite, sqlite-framework (2.4.0) · startup-runtime (1.1.1) · tracing, tracing-ktx (1.2.0) · vectordrawable, vectordrawable-animated (1.1.0) · versionedparcelable (1.1.1) · viewpager (1.0.0) · work-runtime, work-runtime-ktx (2.10.1). Todos Apache-2.0.

## 2. Só no APK de teste (debug, não publicado)

`androidx.compose.ui:ui-tooling`, `ui-tooling-data`, `ui-test-manifest` e `androidx.compose.material:material` (1.8.2), Apache-2.0. Ferramentas de pré-visualização e de teste do Compose; o APK publicado não as inclui.

## 3. Só nos testes (não distribuídos)

Usados por `./gradlew test` e pelas capturas de tela da documentação; nunca entram no APK (o CI confere).

| Componente | Versão | Licença |
|---|---|---|
| JUnit 4 | 4.13.2 | EPL-1.0 (incompatível com a GPL para combinar e distribuir; aqui só roda os testes, ver o relatório) |
| Hamcrest | 1.3 | BSD-3-Clause |
| Robolectric | 4.14.1 (nativeruntime-dist-compat 1.0.16: Apache-2.0) | MIT |
| Roborazzi | 1.44.0 | Apache-2.0 |
| AndroidX Test (core 1.6.1, runner, monitor, espresso, ext-junit, storage) e Compose UI Test (1.8.2) | — | Apache-2.0 |
| Kotlin Test / kotlin-test-junit | 2.1.21 | Apache-2.0 |
| kotlinx-coroutines-test 1.7.3, kotlinx-serialization-json 1.7.3, kotlinx-io 0.3.3 | — | Apache-2.0 |
| Guava 33.3.1-jre, failureaccess, error_prone, j2objc, auto-value-annotations, jsr305, test-parameter-injector, javawriter, javax.inject, conscrypt, sqlite4java, snakeyaml, differ | — | Apache-2.0 |
| ASM | 9.7.1 | BSD-3-Clause |
| checker-qual | 3.43.0 | MIT |
| Bouncy Castle `bcprov-jdk18on` | 1.78.1 | MIT |
| ICU4J | 75.1 | Unicode-3.0 |
| javax.annotation-api | 1.3.2 | CDDL-1.1 ou GPL-2.0 com Classpath Exception |

## 4. Ferramentas de build (não distribuídas)

Gradle 8.14.3 (Apache-2.0, wrapper conferido pelo CI), Android Gradle Plugin 8.10.1 (Apache-2.0), compilador Kotlin e plugin do Compose 2.1.21 (Apache-2.0) e, só na auditoria do CI, CycloneDX Gradle Plugin 3.5.1 (Apache-2.0). Usadas para compilar; não entram no APK, então as licenças delas não impõem obrigações ao APK distribuído.

## 5. Ícone e identidade do Finan+

O ícone do app (F+) e as imagens em [`docs/icone/`](docs/icone/) são criação própria do Finan+, sob a GPL-3.0-or-later. As paletas dos temas Gruvbox, Nord e Tokyo Night usam valores de cor inspirados nesses esquemas conhecidos; nenhum arquivo desses projetos é copiado (ver o relatório da auditoria).

## Relatar um problema de licença

Se encontrar um componente sem atribuição, uma licença errada ou qualquer outro problema de licenciamento, abra uma *issue* em https://github.com/finanplus-web/finan_plus_android/issues com o título começando por **[licença]**, indicando o arquivo ou a dependência e a fonte da informação.
