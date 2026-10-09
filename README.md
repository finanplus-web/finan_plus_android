# Finan+ para Android — v1.3.0

Finan+ é um aplicativo para gerenciamento financeiro pessoal, desenvolvido com foco em simplicidade, privacidade, leveza e funcionamento offline.

Esta é a versão nativa para Android, em **Kotlin + Jetpack Compose**: dados criptografados no aparelho, widget de saldo, notificações de vencimento, desbloqueio por PIN e digital, assistente que funciona sem internet, relatório em PDF, backup compatível com as outras versões e **acesso pela rede local** (usar o Finan+ no navegador do computador, com os dados no celular).

**Baixar:** pelo F-Droid (envio em andamento, ver [Publicar no F-Droid](#publicar-no-f-droid)) ou [APK assinado no GitHub](../../releases/latest) · **Outras versões:** [Finan+ web](https://finanplus-web.github.io/finan_plus/) ([código](https://github.com/finanplus-web/finan_plus)) · [Finan+ para Linux](https://github.com/finanplus-web/finan_plus_linux/releases/latest) ([código](https://github.com/finanplus-web/finan_plus_linux))

- O que mudou em cada versão: [CHANGELOG.md](CHANGELOG.md)
- Calendário de lançamentos (como ler e o que entra na conta): [CALENDARIO.md](CALENDARIO.md)
- Como o assistente decide cada coisa: [ASSISTENTE.md](ASSISTENTE.md)
- Acesso pela rede (uso, certificado, segurança): [ACESSO-PELA-REDE.md](ACESSO-PELA-REDE.md) · auditoria: [AUDITORIA-LAN.md](AUDITORIA-LAN.md)
- Auditoria de outubro de 2026 e o que foi corrigido: [docs/AUDITORIA-2026-10.md](docs/AUDITORIA-2026-10.md)

## Como abrir e gerar o app

1. Instale o **Android Studio** (versão estável recente).
2. Em *File › Open*, escolha a pasta `finan-android`. O Android Studio baixa o Gradle e as dependências sozinho.
3. Conecte um celular (com *Depuração USB* ativada) ou crie um emulador e clique em **Run ▶**.
4. Para gerar um APK de teste para o seu celular: *Build › Generate Signed App Bundle / APK*. A versão distribuída ao público é compilada e assinada pelo F-Droid (ver abaixo), então essa chave serve só para os seus testes e nunca entra no repositório.

Requisitos: Android 8.0 (API 26) ou superior. O SDK alvo é o 35.

> **Importante:** o projeto foi escrito num ambiente sem acesso aos repositórios do Android. A parte de **regras de negócio** (`core/`, incluindo o assistente em `core/assist/`) foi compilada e testada lá: 78 testes passando (fora os do acesso pela rede). A interface, o widget, as notificações e a criptografia foram revisados linha a linha, mas só compilam no Android Studio ou no GitHub Actions (ver abaixo). Se a compilação mostrar algum erro, copie a mensagem (aba *Build*) e envie para correção.

### Testes

```bash
./gradlew test        # testes das regras de negócio (JUnit)
```

## O que tem

| Área | Recursos |
|---|---|
| Início | Saldo atual (inclui saldo inicial das contas), saldo previsto no fim do mês, receitas/despesas do mês, contas e cartões com fatura atual e "Pagar fatura", limites do mês, metas com plano |
| Lançamentos | **Lista**: período com atalhos (este mês, 30 dias, tudo), busca, filtros de tipo e situação, comparação receitas × despesas, marcar como pago. **Calendário**: o mês em grade com o saldo de cada dia, receitas, despesas e faturas no vencimento, atrasos em destaque, totais do mês, lançamentos do dia escolhido, saldo previsto ao fim do dia e "Novo" já com a data. Detalhes em [CALENDARIO.md](CALENDARIO.md) |
| Relatórios | Despesas por categoria (com limites), últimos 6 meses (com descrição para leitores de tela), este mês × anterior |
| Editores | Lançamento (parcelas com divisão do total, repetir mensalmente, cartão), meta, conta, cartão, recorrência (com início, pausa e edição), limite, pagamento de fatura |
| Ajustes | 6 temas (Sistema, Claro, Material You com cores do papel de parede no Android 12+, OLED, Tokyo Night, Nord), PIN, biometria, ocultar valores, bloqueio automático, bloqueio de capturas de tela, notificações, widget, contas/cartões, recorrências, limites, categorias (com renomear), CSV, backup, restauração, apagar tudo |
| Relatório em PDF | Qualquer período (atalhos ou datas): resumo com comparação ao período anterior, gastos por categoria (gráfico e tabela com limites), evolução mensal, maiores despesas, contas, metas e lista de lançamentos, com páginas numeradas. Em *Relatórios* ou *Ajustes › Dados* |
| Assistente | No aparelho, sem internet: sugestão de categoria pela descrição, resumo do mês, dicas (duplicados, aumento de preço, ritmo do limite, acima da média, pequenos gastos, gastos fixos) e perguntas rápidas. Cada resultado tem “Por quê?”. Detalhes em [ASSISTENTE.md](ASSISTENTE.md) |
| Acesso pela rede | Opcional, em *Ajustes › Acesso pela rede*: o celular serve o Finan+ web completo para o navegador de outro aparelho **na mesma rede Wi-Fi**, por HTTPS com certificado próprio do celular. Código de 6 dígitos + "Permitir" no celular. Os dados continuam só no celular. Detalhes em [ACESSO-PELA-REDE.md](ACESSO-PELA-REDE.md) |
| Widget | Saldo atual, previsto e próximo vencimento. Respeita "Ocultar valores" e a opção "Mostrar valores no widget" |
| Notificações | Uma vez por dia (~9h): contas atrasadas ou vencendo, valores a receber e faturas a vencer. Com PIN, digital ou "Ocultar valores", mostra só "N lançamentos pedem atenção" |

## Segurança

- **Dados criptografados**: AES-256-GCM. A chave é gerada no **Android Keystore** (em hardware quando o aparelho tem), nunca sai dele e não pode ser copiada. O arquivo é gravado de forma atômica: se o app for fechado no meio, o arquivo anterior continua íntegro.
- **PIN**: guardado só como hash PBKDF2-HMAC-SHA256 (120 mil iterações, sal aleatório) e comparado em tempo constante. Após 5 erros, a espera começa em 30 s e dobra até 1 hora; a contagem fica gravada no aparelho (fechar o app ou reiniciar não zera). O PIN nunca entra no backup e não é importado de backups.
- **Bloqueio por PIN, digital ou os dois** (independentes). Com os dois, desbloqueia com qualquer um. Só com digital, a alternativa caso a digital falhe é o bloqueio de tela do próprio Android (senha/padrão), para ninguém ficar trancado fora. Ligar ou desligar a digital pede confirmação pela própria digital.
- **Bloqueio**: ao abrir o app e ao voltar do segundo plano (padrão: imediatamente; opções de 1 a 30 minutos ou só ao abrir). Ao bloquear, diálogos abertos são fechados. Enquanto bloqueado, a interface nem é montada, então nada fica acessível por trás.
- **Capturas de tela** bloqueadas por padrão. O conteúdo também fica oculto na lista de apps recentes.
- **Backup do Android desativado** (`allowBackup=false`): a chave do Keystore não pode ser restaurada em outro aparelho. A cópia de segurança é o **Backup JSON** feito pelo usuário.
- **Dados danificados**: se o arquivo não puder ser aberto, uma cópia (ainda cifrada) é guardada e nada é gravado por cima até o usuário decidir. Cada gravação guarda a versão anterior (`.bak`), usada se a atual não abrir. Um erro passageiro do Keystore oferece "Tentar de novo" em vez de tratar os dados como perdidos.
- **Notificações e widget discretos**: com PIN ou digital, a notificação não mostra títulos nem valores, e o widget oculta os valores por padrão.

- **Rede**: o app só abre uma porta quando você liga o *Acesso pela rede* em Ajustes, e só no IP privado do Wi-Fi. Não há nenhum servidor externo: o app não envia dados para a internet. HTTPS com uma autoridade (CA) do próprio celular, com chave no Keystore e válida só para IPs privados; cada navegador precisa do código e da sua permissão no celular. Desliga sozinho após 10 min sem uso. Ver [AUDITORIA-LAN.md](AUDITORIA-LAN.md).

**Decisão de projeto:** a chave de dados *não* exige biometria a cada uso. Se exigisse, o widget e as notificações não conseguiriam ler os dados em segundo plano. A proteção contra quem está com o aparelho desbloqueado na mão é o PIN/biometria do app. A proteção contra cópia do arquivo, backups e leitura fora do app é a criptografia com chave no Keystore.

## Publicar no F-Droid

O Finan+ para Android é distribuído **só pelo F-Droid**. O F-Droid baixa o código deste repositório, compila e assina o APK com a chave dele. Nenhuma chave de assinatura fica no projeto ou no GitHub, e não é preciso configurar Secrets.

O que já está pronto no repositório:

| Onde | O quê |
|---|---|
| `fastlane/metadata/android/pt-BR/` e `en-US/` | Nome, descrições, ícone, capturas de tela e notas de cada versão (`changelogs/<versionCode>.txt`, até 500 caracteres) mostrados no F-Droid |
| `fdroid/com.finanplus.yml` | Receita de compilação para o repositório `fdroiddata` |
| `.github/workflows/android.yml` | Compila e testa a cada envio, gera o APK de release sem assinatura (o mesmo build do F-Droid) e confere tag e textos |

### Primeiro envio (uma vez)

1. Envie as mudanças para a `main` do GitHub e espere o *Compilar e testar* ficar verde em *Actions*.
2. Crie a tag da versão e envie: `git tag v1.1.2` e `git push origin v1.1.2`.
3. Crie uma conta em [gitlab.com](https://gitlab.com) e faça um *fork* de [fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata). Deixe o fork **público**.
4. No seu fork, crie a branch `com.finanplus` a partir da `master`.
5. Adicione o arquivo `metadata/com.finanplus.yml` com o conteúdo de [`fdroid/com.finanplus.yml`](fdroid/com.finanplus.yml). Troque `commit: v1.1.2` pelo hash completo do commit da tag (`git rev-parse v1.1.2`): o F-Droid prefere o hash.
6. Faça o commit com a mensagem `New App: com.finanplus` e abra o *merge request* para o `fdroiddata` com o título **New app: Finan+**, preenchendo o checklist do modelo.
7. A compilação automática do GitLab mostra se deu certo. Responda às perguntas dos revisores no próprio merge request. A revisão costuma levar algumas semanas.

### Cada versão nova (depois de aceito)

1. Aumente `versionCode` e `versionName` em `app/build.gradle.kts`.
2. Escreva a entrada no `CHANGELOG.md` e o arquivo `fastlane/metadata/android/pt-BR/changelogs/<versionCode>.txt` (e o `en-US`, se quiser).
3. Envie para a `main`, crie a tag `vX.Y.Z` com o mesmo número do `versionName` e envie a tag.

O F-Droid encontra a tag nova sozinho, compila e publica, geralmente em alguns dias. A tag precisa ser exatamente `v` + `versionName`; o GitHub Actions avisa se não bater.

## Publicar no GitHub (opcional)

Além do F-Droid, o GitHub pode publicar sozinho o **APK assinado com a sua chave** na página de versões (Releases), a cada tag `vX.Y.Z`. Isso não muda nada no F-Droid, que continua compilando e assinando com a chave dele.

> **Duas assinaturas:** o APK do GitHub (sua chave) e o do F-Droid (chave do F-Droid) não se atualizam um pelo outro. Para trocar de canal, a pessoa precisa desinstalar o app, então deve fazer antes o *Backup JSON* em *Ajustes › Dados*. As notas de cada Release avisam isso.

### Configurar a chave (uma vez)

1. Use a sua chave `.jks` do Android Studio (ou crie uma em *Build › Generate Signed App Bundle / APK › Create new*). Guarde uma cópia e as senhas fora do computador: sem elas não dá para publicar atualizações no GitHub.
2. Gere o texto da chave: `base64 -w0 sua-chave.jks > chave.txt` (no Windows: `certutil -encode sua-chave.jks chave.txt` e apague a primeira e a última linha).
3. No repositório: *Settings › Secrets and variables › Actions › New repository secret*, e crie:

| Secret | Valor |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | o conteúdo de `chave.txt` |
| `ANDROID_KEYSTORE_PASSWORD` | a senha do arquivo `.jks` |
| `ANDROID_KEY_ALIAS` | o nome (alias) da chave |
| `ANDROID_KEY_PASSWORD` | a senha da chave |

4. Apague o `chave.txt`. A chave nunca entra no repositório (`*.jks`, `*.keystore` e afins estão no `.gitignore`).

### Publicar

É o mesmo passo do F-Droid: ao criar e enviar a tag `vX.Y.Z`, o job **publicar** do *Compilar e testar* gera o `finan-plus_X.Y.Z.apk`, mostra no log a impressão digital SHA-256 do certificado e anexa o APK à Release da tag (criando a Release, com as notas do `CHANGELOG.md`, se ela ainda não existir). Sem os Secrets, ele só mostra um aviso e não publica nada.

Se a tag já tiver sido enviada antes de configurar os Secrets: *Actions › Compilar e testar › Run workflow*, informe a tag (ex.: `v1.1.3`) e rode. Tags anteriores à 1.1.3 não têm o suporte a assinatura no código e não podem ser publicadas assim.

## Levar os dados do Finan+ web para o app

1. No Finan+ web: *Ajustes › Dados › Backup JSON*.
2. Envie o arquivo para o celular.
3. No app: *Ajustes › Dados › Restaurar* e escolha o arquivo.

O formato é o mesmo nos dois sentidos. Itens inválidos são ignorados e informados.

## Estrutura

```
app/src/main/java/com/finanplus/
  core/report/ relatório em PDF: cálculo dos números (o desenho fica em export/)
  core/assist/ assistente: categorias, resumo, dicas e perguntas (ver ASSISTENTE.md)
  core/        regras de negócio puras (sem Android): modelo em centavos, JSON, backup,
               saldos, faturas, recorrências, parcelas, metas, lembretes, CSV, operações,
               calendário (MonthCalendar.kt, ver CALENDARIO.md)
  data/        SecureStore (criptografia), Repo (fonte única dos dados), DevicePrefs
  security/    PIN (PBKDF2) e AppLock (bloqueio)
  notify/      canal, agendamento diário (WorkManager) e notificações
  widget/      widget de saldo (Glance)
  lan/         acesso pela rede: servidor HTTPS local, pareamento, certificados (ver ACESSO-PELA-REDE.md)
  ui/          Compose: tema, componentes, telas e editores
app/src/test/  testes das regras de negócio
.github/       compilação, testes e APK assinado no GitHub (GitHub Actions)
fastlane/      textos, ícone e capturas de tela do F-Droid
fdroid/        receita de compilação para o fdroiddata
docs/          auditoria e documentação extra
```

Todo valor em dinheiro é `Long` em centavos: nada de ponto flutuante. Toda alteração de dados passa por `core/Ops.kt`, com as mesmas validações e mensagens do Finan+ web.

## Próximos passos sugeridos

- Ícone monocromático para o "ícone temático" do Android 13+ (requer um desenho vetorial da marca).
- Testes instrumentados de interface (Compose UI Test) depois da primeira compilação.

## Licença

Finan+ — Copyright (C) 2026 Juscelino Be

Finan+ é **software livre**, distribuído sob a **GNU General Public License, versão 3 ou (a seu critério) qualquer versão posterior** (`GPL-3.0-or-later`). O texto completo está em [`LICENSE`](LICENSE) e também dentro do app, em *Ajustes › Sobre › Licença*.

Na prática:
- Qualquer pessoa pode usar, estudar, copiar, modificar e redistribuir o Finan+.
- Quem distribuir o app ou uma versão modificada precisa manter a mesma licença e **disponibilizar o código-fonte**, incluindo as modificações.
- O programa é fornecido **sem garantia**.

**Componente de terceiros:** os ícones do app são do conjunto Material Symbols, © Google, sob a Licença Apache 2.0, compatível com a GPL v3. Detalhes em `third_party/material-symbols/`.

Cada arquivo de código traz no topo o aviso de copyright e o identificador `SPDX-License-Identifier: GPL-3.0-or-later`.

> **Ao publicar** (Play Store, F-Droid ou APK em site), informe onde está o código-fonte, por exemplo o link de um repositório público. É uma exigência da GPL para quem distribui o app.
