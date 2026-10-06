# Finan+ para Android (nativo) — v1.1.0

Versão nativa do Finan+ em **Kotlin + Jetpack Compose**, com todas as funcionalidades do Finan+ web v0.6.0, mais criptografia real, widget de saldo, notificações de vencimento e desbloqueio por biometria.

## Como abrir e gerar o app

1. Instale o **Android Studio** (versão estável recente).
2. Em *File › Open*, escolha a pasta `finan-android`. O Android Studio baixa o Gradle e as dependências sozinho.
3. Conecte um celular (com *Depuração USB* ativada) ou crie um emulador e clique em **Run ▶**.
4. Para gerar o arquivo de instalação: *Build › Generate Signed App Bundle / APK*. Use **AAB** para a Play Store e **APK** para instalar direto. Guarde a chave de assinatura (`.jks`) em lugar seguro: sem ela não é possível publicar atualizações.

Requisitos: Android 8.0 (API 26) ou superior. O SDK alvo é o 35.

> **Importante:** o projeto foi escrito num ambiente sem acesso aos repositórios do Android. A parte de **regras de negócio** (`core/`, incluindo o assistente em `core/assist/`) foi compilada e testada lá: 60 testes passando. **A interface, o widget, as notificações e a criptografia não puderam ser compilados lá.** Se a primeira compilação no Android Studio mostrar algum erro, copie a mensagem (aba *Build*) e envie para correção.

### Testes

```bash
./gradlew test        # testes das regras de negócio (JUnit)
```

## O que tem

| Área | Recursos |
|---|---|
| Início | Saldo atual (inclui saldo inicial das contas), saldo previsto no fim do mês, receitas/despesas do mês, contas e cartões com fatura atual e "Pagar fatura", limites do mês, metas com plano |
| Lançamentos | Período com atalhos (este mês, 30 dias, tudo), busca, filtros de tipo e situação, comparação receitas × despesas, lista com marcar como pago |
| Relatórios | Despesas por categoria (com limites), últimos 6 meses (com descrição para leitores de tela), este mês × anterior |
| Editores | Lançamento (parcelas com divisão do total, repetir mensalmente, cartão), meta, conta, cartão, recorrência (com início, pausa e edição), limite, pagamento de fatura |
| Ajustes | 6 temas (Sistema, Claro, Material You com cores do papel de parede no Android 12+, OLED, Tokyo Night, Nord), PIN, biometria, ocultar valores, bloqueio automático, bloqueio de capturas de tela, notificações, widget, contas/cartões, recorrências, limites, categorias (com renomear), CSV, backup, restauração, apagar tudo |
| Relatório em PDF | Qualquer período (atalhos ou datas): resumo com comparação ao período anterior, gastos por categoria (gráfico e tabela com limites), evolução mensal, maiores despesas, contas, metas e lista de lançamentos, com páginas numeradas. Em *Relatórios* ou *Ajustes › Dados* |
| Assistente | No aparelho, sem internet: sugestão de categoria pela descrição, resumo do mês, dicas (duplicados, aumento de preço, ritmo do limite, acima da média, pequenos gastos, gastos fixos) e perguntas rápidas. Cada resultado tem “Por quê?”. Detalhes em [ASSISTENTE.md](ASSISTENTE.md) |
| Widget | Saldo atual, previsto e próximo vencimento. Respeita "Ocultar valores" e a opção "Mostrar valores no widget" |
| Notificações | Uma vez por dia (~9h): contas atrasadas ou vencendo, valores a receber e faturas a vencer. Na tela de bloqueio, aparece só "Você tem vencimentos próximos" |

## Segurança

- **Dados criptografados**: AES-256-GCM. A chave é gerada no **Android Keystore** (em hardware quando o aparelho tem), nunca sai dele e não pode ser copiada. O arquivo é gravado de forma atômica: se o app for fechado no meio, o arquivo anterior continua íntegro.
- **PIN**: guardado só como hash PBKDF2-HMAC-SHA256 (120 mil iterações, sal aleatório) e comparado em tempo constante. Após 5 erros, há espera crescente. O PIN nunca entra no backup e não é importado de backups.
- **Bloqueio por PIN, digital ou os dois** (independentes). Com os dois, desbloqueia com qualquer um. Só com digital, a alternativa caso a digital falhe é o bloqueio de tela do próprio Android (senha/padrão), para ninguém ficar trancado fora. Ligar ou desligar a digital pede confirmação pela própria digital.
- **Bloqueio**: ao abrir o app e após o tempo configurado em segundo plano. Enquanto bloqueado, a interface nem é montada, então nada fica acessível por trás.
- **Capturas de tela** bloqueadas por padrão. O conteúdo também fica oculto na lista de apps recentes.
- **Backup do Android desativado** (`allowBackup=false`): a chave do Keystore não pode ser restaurada em outro aparelho. A cópia de segurança é o **Backup JSON** feito pelo usuário.
- **Dados danificados**: se o arquivo não puder ser aberto, uma cópia (ainda cifrada) é guardada antes de qualquer gravação, e o usuário é avisado.

**Decisão de projeto:** a chave de dados *não* exige biometria a cada uso. Se exigisse, o widget e as notificações não conseguiriam ler os dados em segundo plano. A proteção contra quem está com o aparelho desbloqueado na mão é o PIN/biometria do app. A proteção contra cópia do arquivo, backups e leitura fora do app é a criptografia com chave no Keystore.

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
               saldos, faturas, recorrências, parcelas, metas, lembretes, CSV, operações
  data/        SecureStore (criptografia), Repo (fonte única dos dados), DevicePrefs
  security/    PIN (PBKDF2) e AppLock (bloqueio)
  notify/      canal, agendamento diário (WorkManager) e notificações
  widget/      widget de saldo (Glance)
  ui/          Compose: tema, componentes, telas e editores
app/src/test/  testes das regras de negócio
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
