<!--
  Finan+ — Copyright (C) 2026 Juscelino Be
  SPDX-License-Identifier: GPL-3.0-or-later
-->
# Auditoria de licenças e conformidade open source — Finan+ Android

Auditoria da versão **1.5.0** (`versionCode` 12), feita em 13/10/2026 sobre a `main` no commit `51fbd9b` e atualizada para a **1.5.1** (`ad593c1`, que não mudou dependências), na branch `audit/licencas-open-source` (PR #19). Escopo: todas as dependências diretas e transitivas, recursos incorporados (assets, ícones, fontes, página web do *Acesso pela rede*), avisos dentro do app, documentação e automação no GitHub Actions.

> Este relatório é uma análise técnica de licenças, não um parecer jurídico.

## A. Resumo executivo

**Licenças: nenhuma incompatibilidade com a GPL-3.0-or-later no que é distribuído.** Os 128 componentes do APK de release são Apache-2.0, MIT, BSD-3-Clause, SIL OFL 1.1, Unicode e a licença AFM da Adobe, todas compatíveis com a GPLv3. O único componente com licença incompatível (JUnit 4, EPL-1.0) e o `javax.annotation-api` (CDDL/GPL-2.0) só rodam nos testes e não entram no APK; o CI agora impede que isso mude sem aviso.

**Obrigações de distribuição: não estavam cumpridas e foram corrigidas.** O app só citava os ícones e o PdfBox-Android. Ficavam sem atribuição componentes que vão dentro do APK: **Bouncy Castle** (MIT, exige o aviso em todas as cópias), os **NOTICE** do PdfBox-Android, kotlinx.coroutines e kotlinx.serialization (Apache 2.0 §4(d)), e os recursos embutidos pelo PdfBox-Android: **métricas AFM e CMaps da Adobe, a fonte Liberation Sans (OFL-1.1) e dados Unicode**. O Android Gradle Plugin descarta os arquivos `META-INF/NOTICE` das bibliotecas, e o APK conferido no CI não tinha nenhum desses avisos. Também havia **duas cópias do Protocol Buffers (BSD-3-Clause)** escondidas no Glance e no DataStore, sendo que o POM do DataStore só declara Apache-2.0.

**Estado após este PR:** inventário completo, avisos e textos de licença dentro do app (offline), `THIRD_PARTY_NOTICES.md`, textos originais em `third_party/licenses/`, SBOM CycloneDX e verificação automática no CI. Também resolvidos: Bouncy Castle atualizado para a 1.83, Actions de todos os workflows fixadas por SHA e teste automático da tela de licenças e da leitura de faturas com senha. Compilação, testes e auditoria passaram no GitHub Actions.

**Não é declarada conformidade integral**, por dois motivos registrados em *E. Problemas pendentes*: a tela de licenças e a leitura de faturas foram testadas no Robolectric, mas não num aparelho, e a revisão de licenças das dependências de teste se apoia no POM declarado (não no conteúdo dos JARs).

## B. Inventário

Inventário completo, com versões resolvidas, origem, licença, titular, obrigações e uso: [`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md). O SBOM CycloneDX 1.6 (`sbom.json`) é gerado a cada execução do workflow *Auditoria de licenças*.

Como as versões foram obtidas: as versões **efetivamente resolvidas** vieram do Gradle no CI (`releaseRuntimeClasspath`, `debugRuntimeClasspath`, `debugUnitTestRuntimeClasspath`), não do `libs.versions.toml`. Exemplos de diferença entre o declarado e o resolvido: Compose BOM 2025.05.01 resolve Compose UI 1.8.2 e Material 3 1.3.2; Kotlin 2.1.21 traz `kotlin-stdlib-jdk7/jdk8` 1.8.22; o PdfBox-Android traz Bouncy Castle 1.72.

| Escopo | Componentes | Licenças |
|---|---|---|
| APK de release | 128 | Apache-2.0 (124), MIT (3, Bouncy Castle), BSD-3-Clause (1, protobuf do Glance) + recursos embutidos: OFL-1.1, Unicode-DFS-2016, licenças Adobe (AFM e BSD) |
| Só APK de teste (debug) | 7 | Apache-2.0 |
| Só testes | 70 | Apache-2.0, MIT, BSD-3-Clause, EPL-1.0 (JUnit), Unicode-3.0 (ICU4J), CDDL/GPL-2.0+CPE (javax.annotation-api) |
| Ferramentas de build | Gradle 8.14.3, AGP 8.10.1, Kotlin 2.1.21, CycloneDX 3.5.1 | Apache-2.0 (não distribuídas) |

### Classificação dos componentes distribuídos

"Compatível" fala da licença; "obrigações" fala do que a distribuição precisa fazer. As duas coisas são avaliadas separadamente.

| Componente | Licença | Compatível com GPL-3.0+? | Obrigações cumpridas antes? | Situação após o PR |
|---|---|---|---|---|
| AndroidX / Jetpack (110 artefatos `androidx.*`) | Apache-2.0 | Sim | Parcial (licença no app, sem atribuição) | COMPATÍVEL |
| Protocol Buffers dentro do Glance e do DataStore | BSD-3-Clause | Sim | Não | COMPATÍVEL |
| Kotlin stdlib (com trechos ThreeTen BSD-3 e Boost BSL-1.0) | Apache-2.0 | Sim | Não | COMPATÍVEL |
| kotlinx.coroutines, kotlinx.serialization | Apache-2.0 + NOTICE | Sim | Não (NOTICE ausente) | COMPATÍVEL |
| JetBrains Annotations, JSpecify, Guava ListenableFuture | Apache-2.0 | Sim | Parcial | COMPATÍVEL |
| PdfBox-Android 2.0.27.0 | Apache-2.0 + NOTICE | Sim | Parcial (citado, sem NOTICE) | COMPATÍVEL |
| ↳ AFM (Adobe), CMaps e Glyph List (Adobe), Liberation Sans 2.1.5, dados Unicode | AFM Adobe, BSD-3 (Adobe), OFL-1.1, Unicode-DFS-2016 | Sim (a fonte segue sob a OFL, que permite empacotar com software) | Não | COMPATÍVEL |
| Bouncy Castle (bcprov, bcpkix, bcutil): 1.72 na auditoria, 1.83 após este PR | MIT | Sim | Não | COMPATÍVEL |
| Material Symbols Rounded (54 arquivos) | Apache-2.0 | Sim | Parcial (modificação não marcada em cada arquivo) | COMPATÍVEL |
| Página web do *Acesso pela rede* (código próprio + ícones + larguras Helvetica) | GPL-3.0-or-later + Apache-2.0 + AFM Adobe | Sim | Parcial (métricas sem atribuição) | COMPATÍVEL |
| JUnit 4.13.2 (só testes) | EPL-1.0 | **Não** para combinar e distribuir | — (não distribuído) | COMPATÍVEL COM OBRIGAÇÕES: manter fora do APK (o CI confere) |
| javax.annotation-api 1.3.2 (só testes) | CDDL-1.1 ou GPL-2.0+CPE | Não para combinar | — (não distribuído) | COMPATÍVEL COM OBRIGAÇÕES: manter fora do APK |

Nenhum componente ficou **INCOMPATÍVEL** ou **NÃO VERIFICADO** entre os distribuídos.

### Análises específicas

**Apache 2.0.** Compatível com a GPLv3 (não com a GPLv2). Exige entregar cópia da licença (já havia `APACHE-2.0.txt` no app, idêntico ao texto do repositório do Google), manter avisos e reproduzir o conteúdo dos arquivos NOTICE (§4(d)) "dentro de um NOTICE distribuído, na documentação ou numa tela do programa". Um arquivo Apache genérico não basta quando há NOTICE: o PdfBox-Android, o kotlinx.coroutines e o kotlinx.serialization têm NOTICE, agora reproduzidos em *Avisos de terceiros*. Os artefatos AndroidX conferidos não trazem NOTICE. Arquivos modificados precisam de aviso de mudança (§4(b)), que faltava nos ícones.

**EPL 1.0 e JUnit.** A EPL-1.0 é incompatível com a GPL (a FSF a lista entre as licenças livres incompatíveis com a GNU GPL). O JUnit 4.13.2 é `testImplementation`: só aparece em `debugUnitTestRuntimeClasspath`, não em `releaseRuntimeClasspath` nem `debugRuntimeClasspath`, por isso não entra em nenhum APK. Rodar testes não é "distribuir" o JUnit com o Finan+, então não há combinação distribuída nem obrigação adicional para o APK. A política (`third_party/license-policy.json`) só permite o JUnit no escopo de testes e marca a EPL-1.0 como incompatível para distribuição: se um dia ele vazar para o APK, o CI falha.

**PdfBox-Android.** Licença Apache-2.0 (LICENSE.txt e NOTICE.txt da tag `v2.0.27.0`). Dependências transitivas: `bcprov`, `bcpkix` e `bcutil-jdk15to18` 1.72 (Bouncy Castle, MIT) como `api`, forçados para a 1.83 neste PR; o decodificador JPEG 2000 (`com.gemalto.jp2`) é `compileOnly` e não vem junto (por isso o `-dontwarn` no `proguard-rules.pro`). O AAR traz 112 recursos em `assets/com/tom_roush/` (14 AFM, 92 CMaps, 3 listas de glifos, Liberation Sans, Scripts.txt, BidiMirroring.txt), todos presentes no APK de release (conferido no CI). O LICENSE.txt do projeto tem um apêndice "EXTERNAL COMPONENTS" com as licenças desses recursos, agora reproduzido no app sem alteração. A licença AFM da Adobe exige que os arquivos AFM não sejam distribuídos sem o texto dela: atendido. A fonte Liberation Sans 2.1.5 é OFL-1.1 (conferido nos metadados da própria fonte e no LICENSE da tag 2.1.5).

**Material Symbols.** Licença Apache-2.0, igual ao `LICENSE` do repositório `google/material-design-icons`. Os 54 arquivos `res/drawable/ms_*.xml` foram comparados traçado a traçado com os SVG originais (`materialsymbolsrounded/<nome>_wght300_24px.svg`, ou `wght300fill1` para os 4 preenchidos): **54 de 54 idênticos**. A única mudança é a conversão para VectorDrawable com cor branca, agora anotada no topo de cada arquivo. O README da pasta dizia 48 ícones; são 50 símbolos em 54 arquivos.

**Recursos web (`assets/lan/`).** `app.bundle.js` é gerado pelo Finan+ web (mesmo autor, GPL-3.0-or-later) e contém só os módulos próprios `js/*.js`: nenhum módulo de `node_modules`, nenhuma biblioteca JavaScript ou CSS de terceiros, nenhuma fonte embutida (o CSS cita a fonte Inter, mas cai para as fontes do sistema), sem arquivos minificados de terceiros. A criptografia usa a Web Crypto API do navegador. Encontrados: ícones Material Symbols (Apache-2.0, já atribuídos no próprio PWA) e as tabelas `HELVETICA`/`HELVETICA_BOLD` do gerador de PDF, que batem com as larguras das métricas AFM da Adobe (conferido contra `Helvetica.afm` e `Helvetica-Bold.afm`). São só números, mas a origem passou a ser atribuída. `setup.html`, `index.html`, `style.css` e `manifest.webmanifest` são próprios, com cabeçalho SPDX.

**Temas.** As paletas Gruvbox, Nord e Tokyo Night usam valores de cor desses esquemas. Nenhum arquivo foi copiado; listas de cores não costumam ser protegidas por direito autoral. Registrado como informativo.

## C. Problemas identificados

| Id | Gravidade | Problema | Situação |
|---|---|---|---|
| A1 | Alto | Bouncy Castle 1.72 (MIT) no APK sem o aviso de copyright e permissão | Corrigido |
| A2 | Alto | NOTICE do PdfBox-Android, kotlinx.coroutines e kotlinx.serialization não reproduzidos; o AGP remove os `META-INF/NOTICE` do APK | Corrigido |
| A3 | Alto | Recursos do PdfBox-Android no APK (AFM e CMaps da Adobe, Liberation Sans OFL-1.1, dados Unicode) sem as licenças e avisos exigidos | Corrigido |
| M1 | Médio | Protocol Buffers (BSD-3-Clause) copiado dentro do Glance e do DataStore, sem aviso; o POM do DataStore nem declara | Corrigido |
| M2 | Médio | Tela *Sobre* não citava AndroidX, Kotlin e demais componentes; atribuição incompleta | Corrigido |
| M3 | Médio | Sem inventário, SBOM nem verificação de licenças: uma dependência nova poderia entrar sem revisão | Corrigido |
| M4 | Médio | PdfBox-Android sem lançamento desde a 2.0.27.0 e preso ao Bouncy Castle 1.72; versões mais novas do Bouncy Castle corrigiram vulnerabilidades publicadas (por exemplo, a 1.78 corrigiu a CVE-2024-30171) | Corrigido: Bouncy Castle 1.83 por restrição de versão no Gradle (o PdfBox continua o mesmo) |
| M5 | Médio | Workflows existentes (`android.yml`, `screenshots.yml`) usam Actions por tag móvel (`@v4`), não por SHA, e versões que rodam em Node 20 (descontinuado pelo GitHub) | Corrigido (lógica de assinatura e publicação intacta) |
| B1 | Baixo | Ícones modificados sem aviso de mudança em cada arquivo (Apache §4(b)); contagem errada no README da pasta | Corrigido |
| B2 | Baixo | Texto do app atribuía copyright do PdfBox a "The Apache Software Foundation e Tom Roush", o que o NOTICE não diz | Corrigido |
| B3 | Baixo | README desatualizado: versão 1.4.0 (Gradle: 1.5.0), lista de temas com Material You, frase "distribuído só pelo F-Droid" contradizendo a seção do GitHub | Corrigido |
| B4 | Baixo | Larguras da Helvetica (métricas AFM da Adobe) no PWA sem atribuição | Corrigido |
| I1 | Informativo | JUnit 4 (EPL-1.0) e javax.annotation-api (CDDL/GPL-2.0) só nos testes: sem conflito | Monitorado pelo CI |
| I2 | Informativo | `fdroid/com.finanplus.yml` ainda na 1.1.2: é só o modelo do primeiro envio; o `fdroiddata` atualiza sozinho (`AutoUpdateMode: Version`) | Sem ação |
| I3 | Informativo | `build.gradle.kts`, `settings.gradle.kts` e `app/build.gradle.kts` sem cabeçalho SPDX (o `LICENSE` da raiz cobre o repositório) | Sem ação |
| I4 | Informativo | O texto *Sobre* do app ainda citava os temas Material You, OLED, Tokyo Night e Nord (não é questão de licença) | Corrigido |
| I5 | Informativo | `setup-gradle` v6 usa por padrão um cache proprietário ("Enhanced Caching"); os workflows usam `cache-provider: basic` (MIT) ou o cache do `setup-java` (MIT) | Corrigido |
| B5 | Baixo | `screenshots.yml` rodado à mão em outro branch fazia `git pull --rebase origin main` e `git push origin HEAD:main`: se as imagens mudassem, enviaria os commits do branch direto para a `main` | Corrigido: a gravação só roda na `main` |
| I6 | Informativo | O PdfBox-Android só usa o Bouncy Castle para PDF cifrado por certificado; faturas com senha usam a criptografia do próprio Java (conferido no código do PdfBox) | Registrado |

## D. Correções implementadas

| Arquivo | Mudança | Por quê |
|---|---|---|
| `app/src/main/assets/licenca/TERCEIROS.txt` (novo) | Avisos de terceiros: atribuições, NOTICE e textos de licença (BSD, MIT, OFL, Unicode, Boost, AFM), em português com os textos originais sem alteração | A1–A3, M1, M2, B4 |
| `app/src/main/java/com/finanplus/ui/screens/SettingsScreen.kt` | Em *Sobre*: seção "Componentes de terceiros", botão "Ver avisos de terceiros" (mesmo componente `LicenseText` já usado para a GPL, lido dos assets, sem internet) e texto corrigido | M2, B2 |
| `app/src/main/res/drawable/ms_*.xml` (54) | Só o comentário do topo: anota a conversão para VectorDrawable | B1 |
| `THIRD_PARTY_NOTICES.md` (novo) | Inventário completo com versões resolvidas, titulares e obrigações | M3 |
| `third_party/licenses/` (novo) | Textos originais: Apache-2.0, LICENSE e NOTICE do PdfBox-Android, Bouncy Castle 1.83, OFL do Liberation 2.1.5, Unicode, protobuf, ThreeTen, Boost, NOTICE do kotlinx | A1–A3, M1 |
| `third_party/material-symbols/README.md` | Contagem correta, origem exata e conferência dos traçados | B1 |
| `third_party/license-policy.json` (novo) | Política revisada por componente: licença, escopos permitidos e seção de aviso exigida | M3 |
| `tools/license-audit/audit.init.gradle.kts` (novo) | Init script do CI: SBOM CycloneDX e versões resolvidas por classpath, **sem alterar o build do app** | M3 |
| `tools/license-audit/check_licenses.py` (novo) | Verificador (só biblioteca padrão do Python) | M3 |
| `.github/workflows/license-audit.yml` (novo) | Workflow em PR, push com mudança de dependências e manual; leitura apenas; Actions fixadas por SHA | M3 |
| `README.md` | Versão 1.5.1, temas, canais de publicação, seção de licenças, contribuições e como relatar problemas | B3 |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Restrição de versão: Bouncy Castle 1.83 (bcprov, bcpkix, bcutil `-jdk15to18`) no lugar da 1.72 trazida pelo PdfBox-Android; nas capturas, falhas de teste com a mensagem completa | M4 |
| `.github/workflows/android.yml`, `screenshots.yml` | Actions fixadas por SHA (checkout v6.1.0, setup-java v5.7.0, setup-gradle e wrapper-validation v6.4.0 com `cache-provider: basic`, upload-artifact v6.0.0); `persist-credentials: false` no job de build; gravação das capturas só na `main`; erros de asserção dos testes viram avisos da execução | M5, B5, I5 |
| `app/src/main/java/com/finanplus/ui/screens/SettingsScreen.kt` | Texto *Sobre* com a lista de temas atual | I4 |
| `app/src/test/java/com/finanplus/screenshots/LicensesScreenTest.kt` (novo) | Abre *Ajustes › Sobre* no app real e confere GPL, avisos de terceiros (começo, meio e fim) e Apache 2.0 | E.1 |
| `app/src/test/java/com/finanplus/screenshots/BillPdfTest.kt` (novo) | Gera faturas em PDF sem senha e com senha (AES-256 e AES-128) e lê com o código do app | M4 |

**O que não mudou:** `settings.gradle.kts`, `AndroidManifest.xml` (nenhuma permissão nova), `proguard-rules.pro`, `fdroid/`, a lógica de assinatura e publicação, keystore e Secrets. A única dependência do app alterada é a versão do Bouncy Castle. O plugin CycloneDX só é carregado pelo init script no CI: o F-Droid e o build normal não o baixam.

## E. Problemas pendentes

1. **Tela de licenças e leitura de faturas não testadas num aparelho.** No Robolectric (Android simulado na JVM), o `LicensesScreenTest` abre *Ajustes › Sobre* no app real e confirma que a GPL, os avisos de terceiros e a Apache 2.0 aparecem inteiros, lidos dos assets e sem rede; o `BillPdfTest` lê faturas com e sem senha. Falta o teste num celular: instalar o APK de teste do PR, ativar o modo avião, abrir *Ajustes › Sobre*, tocar em "Ver avisos de terceiros" e em "Ver licença Apache 2.0" e rolar até o fim; depois, em *Ler fatura ou boleto*, abrir uma fatura real **com senha**.
2. **Licenças das dependências de teste** foram conferidas pelo que os POMs declaram (via SBOM), não pelo conteúdo de cada JAR. Como não são distribuídas, o risco é baixo.
3. **PdfBox-Android sem manutenção.** O Bouncy Castle foi atualizado, mas o PdfBox-Android continua parado desde 2023. Acompanhar o projeto ou uma alternativa mantida; não há troca necessária agora.
4. **Publicação por tag com as Actions novas.** O job "publicar" do `android.yml` só roda numa tag, então as versões novas das Actions nesse job só ficam comprovadas na próxima versão lançada. Conferir na Release seguinte que o APK assinado foi anexado.

## F. Testes realizados

O ambiente desta auditoria não alcança o Maven Central, o Google Maven nem o servidor do Gradle (bloqueio de rede), então **nenhum comando Gradle rodou localmente**. Toda a compilação, os testes e o SBOM rodaram no GitHub Actions, nos workflows do PR #19, que executam exatamente estes comandos:

| Verificação | Comando | Onde | Resultado |
|---|---|---|---|
| Testes unitários | `./gradlew testDebugUnitTest` | CI, *Compilar e testar* | Passou |
| APK de teste | `./gradlew assembleDebug` | CI, *Compilar e testar* | Passou |
| APK de release sem assinatura (o mesmo build do F-Droid) | `./gradlew assembleRelease` | CI, *Compilar e testar* | Passou |
| Gradle Wrapper | `gradle/actions/wrapper-validation` | CI, os dois workflows | Passou |
| SBOM e dependências resolvidas | `./gradlew --init-script tools/license-audit/audit.init.gradle.kts :app:cyclonedxDirectBom :app:finanResolvedDependencies` e `:app:dependencies --configuration <classpath>` | CI, *Auditoria de licenças* | Passou: 205 componentes (128 release, 7 debug, 70 testes) |
| Conferência de licenças | `python3 tools/license-audit/check_licenses.py` | CI | Passou: 0 erros, 0 avisos |
| Conteúdo do APK de release | `unzip -l` do APK: assets do PdfBox presentes, nenhum `NOTICE`, `TERCEIROS.txt` presente | CI (passo temporário, removido) | Confirmado |
| Protobuf copiado no DataStore e no Glance | listagem dos JARs no cache do Gradle | CI (passo temporário, removido) | Confirmado |
| Verificador detecta falhas | 6 cenários simulados: JUnit no APK, dependência nova, licença alterada, licença desconhecida, licença ausente, aviso ausente no app | Local | Todos falharam como esperado |
| Ícones iguais ao original | traçados dos 54 `ms_*.xml` contra os SVG do `google/material-design-icons` | Local | 54/54 idênticos |
| Permissões Android | `git diff` do `AndroidManifest.xml` | Local | Sem mudança |
| Tela de licenças | `LicensesScreenTest` (Robolectric): app real, *Ajustes › Sobre*, GPL, avisos de terceiros e Apache 2.0, sem rede | CI, *Capturas de tela* rodado no branch | Passou; **teste no aparelho não executado** (ver E.1) |
| Leitura de faturas com Bouncy Castle 1.83 | `BillPdfTest` (Robolectric): PDF sem senha, AES-256 (senha certa, sem senha, senha errada) e AES-128 | CI, *Capturas de tela* rodado no branch | Passou |
| Testes de abas e capturas existentes | `TabSwipeTest`, `ScreenshotTest` com `-PfinanScreenshots=true` | CI, *Capturas de tela* rodado no branch | Passaram; gravação pulada fora da `main` |
| Workflows com as Actions novas | *Compilar e testar*, *Capturas de tela* e *Auditoria de licenças* no PR | CI | Passaram (job "publicar" só roda em tag, ver E.4) |
| `./gradlew clean` | — | — | Não executado à parte: cada execução do CI parte de um ambiente limpo |
| Testes instrumentados | — | — | Não existem no projeto (`app/src/androidTest` ausente) |

## G. Conformidade com a GPL-3.0-or-later

- **Licença preservada:** `LICENSE` (GPLv3 integral) sem mudança; cabeçalhos SPDX nos arquivos de código; `fdroid/com.finanplus.yml` declara `GPL-3.0-or-later`.
- **Avisos legais apropriados (§5(d)):** o app mostra o aviso de copyright, a ausência de garantia e a licença completa em *Ajustes › Sobre*, sem internet.
- **Código-fonte correspondente (§6):** o repositório público tem tudo o que é preciso para gerar o APK, incluindo os scripts de build e o wrapper do Gradle. O F-Droid compila a partir dele. O APK do GitHub Releases é publicado no mesmo repositório que contém o código da tag (§6(d), acesso equivalente no mesmo lugar). A página web servida pelo *Acesso pela rede* é gerada a partir do repositório `finanplus-web/finan_plus`, informado em *Avisos de terceiros* e no cabeçalho do próprio bundle. Recomendação: registrar no `tools/sync-pwa.sh` o commit do PWA copiado, para ligar cada versão do APK ao código exato do bundle.
- **Componentes de terceiros:** todos com licenças compatíveis; os avisos exigidos por elas agora acompanham o binário.
- **Sem restrições adicionais:** nenhum componente impõe termos que a GPL proíbe; nada de DRM, telemetria ou serviços externos.

## H. Compatibilidade com o F-Droid

- **Só software livre:** todas as dependências do APK são livres e vêm do Maven Central e do Google Maven (repositórios aceitos). Não há Google Play Services, Firebase, analytics ou SDKs proprietários.
- **Bloco de dependências do Google:** desligado (`dependenciesInfo.includeInApk = false`), como o F-Droid exige.
- **Build inalterado:** a auditoria usa um init script carregado só no CI; o build que o F-Droid roda (`gradle: yes`, `subdir: app`) é o mesmo de antes.
- **Recursos não livres:** nenhum. Fonte (OFL), métricas e CMaps (Adobe, permissivas) e ícones (Apache) são livres.
- **Antifeatures:** nenhuma identificada. A permissão `INTERNET` é usada só pelo servidor local opcional.
- **Reprodutibilidade:** o F-Droid assina com a chave dele, então builds reproduzíveis não são exigidos. Para oferecer no futuro a verificação do APK do autor, seria preciso fixar JDK e ferramentas e comparar com o build do F-Droid.

## I. Recomendações futuras

1. Manter `THIRD_PARTY_NOTICES.md`, `TERCEIROS.txt` e a política juntos a cada dependência nova: o CI aponta o que falta.
2. Acompanhar o PdfBox-Android (seção E.3) e conferir a próxima Release publicada por tag (seção E.4).
3. Ativar o Dependabot (ou equivalente) para as Actions e o Gradle, com revisão humana: cada atualização passa pela auditoria de licenças.
4. Anexar o `sbom.json` às Releases quando o pipeline de publicação for revisto.
5. Considerar a verificação de dependências do Gradle (`gradle/verification-metadata.xml`) para conferir os hashes dos artefatos baixados.

### Política de atualização das Actions fixadas por SHA

O workflow *Auditoria de licenças* fixa cada Action pelo SHA completo do commit, com a versão no comentário (`# v6.1.0`). Para atualizar:

1. Escolher a versão nova na página de Releases da Action e conferir as notas (mudanças de entradas, versão do Node).
2. Obter o SHA do commit da tag: `git ls-remote --tags https://github.com/<dono>/<action> 'refs/tags/vX.Y.Z*'` (em tag anotada, usar a linha terminada em `^{}`).
3. Trocar o SHA e o comentário, abrir PR e conferir que o workflow passa.
4. Para o `setup-gradle` v6 ou mais novo, usar `cache-provider: basic` (código MIT); o cache padrão é proprietário.
