<!--
  Finan+ — Copyright (C) 2026 Juscelino Be
  SPDX-License-Identifier: GPL-3.0-or-later
-->
# Auditoria do Finan+ Android (outubro de 2026)

Auditoria da versão 1.1.0, feita em 06/10/2026 por revisão completa do código (38 arquivos, cerca de 6.500 linhas de Kotlin), dividida em três frentes: segurança e armazenamento, lógica financeira e interface. Os testes do núcleo foram compilados e rodados na JVM, e testes extras confirmaram os erros de cálculo. As correções estão na versão **1.1.1** (detalhes no [CHANGELOG](../CHANGELOG.md)).

## Resultado

| Gravidade | Encontrados | Corrigidos na 1.1.1 | Parciais | Pendentes |
|---|---|---|---|---|
| Alta | 7 | 7 | — | — |
| Média | 12 | 11 | M10 | — |
| Baixa | 22 | 16 | B22 | B5, B8, B18, B19, B20 |

Três erros de cálculo (A5, M1, M2) também existiam nas outras versões, porque as três usam as mesmas regras financeiras. Eles foram corrigidos igualmente no Finan+ web 1.1.2 e no Finan+ para Linux 1.1.7.

Verificação após as correções: núcleo Android com 71 testes passando (11 novos em `RegressionTest.kt`), web com 77 e Linux com 69. A interface Android passou por uma revisão independente focada em erros de compilação, que encontrou 3 bugs nas novas proteções de dados (releitura do Keystore, preservação do `.bak` e carência do seletor de arquivos), todos corrigidos antes da entrega.

## Gravidade Alta

| # | Problema | Situação |
|---|---|---|
| A1 | Diálogos ("Definir PIN", "Apagar tudo", "Restaurar") continuavam utilizáveis por cima da tela de bloqueio | Corrigido: diálogos fecham ao bloquear e não são desenhados com o app bloqueado |
| A2 | Limite de tentativas do PIN ficava só na memória e zerava ao fechar o app | Corrigido: gravado no aparelho, espera de 30 s dobrando até 1 h |
| A3 | Falha passageira do Keystore levava a gravar dados vazios por cima dos reais | Corrigido: "Tentar de novo", gravação bloqueada até decisão explícita, `.bak` da versão anterior |
| A4 | Falha ao gravar passava em silêncio | Corrigido: aviso fixo no topo e nova tentativa a cada 15 s |
| A5 | Reativar uma recorrência pausada criava os lançamentos de todos os meses parados | Corrigido (Android, web e Linux): retoma no mês atual |
| A6 | Saldos da Início cortados com "…" | Corrigido: a fonte diminui até caber |
| A7 | Backup JSON e CSV sem tratamento de erro nem confirmação | Corrigido: resultado sempre avisado, gravação que trunca o arquivo |

## Gravidade Média

| # | Problema | Situação |
|---|---|---|
| M1 | Desmarcar um pagamento de fatura descontava o valor duas vezes | Corrigido (Android, web e Linux) |
| M2 | Valores gigantes e booleanos aceitos no backup (saldo trocava de sinal) | Corrigido (Android, web e Linux) |
| M3 | Restauração lia o arquivo inteiro e analisava na thread principal | Corrigido |
| M4 | Com PIN, o app não bloqueava de novo por padrão; ajuste ia no backup | Corrigido: padrão "Imediatamente", ajuste só no aparelho |
| M5 | Notificações mostravam nomes e valores com o app bloqueado | Corrigido |
| M6 | Widget mostrava o saldo por padrão mesmo com PIN | Corrigido |
| M7 | Biometria aceitava sensores fracos (rosto 2D) | Corrigido: só Classe 3 |
| M8 | Navegação e formulários se perdiam ao bloquear ou girar a tela | Corrigido |
| M9 | TalkBack não anunciava o estado dos interruptores | Corrigido |
| M10 | Cálculos pesados durante o desenho da tela | Parcial: a busca foi otimizada; assistente e prévia do relatório ainda calculam na composição |
| M11 | Tela de bloqueio não rolava (celular deitado, fonte grande) | Corrigido |
| M12 | Deslizar a folha descartava o formulário sem perguntar | Corrigido |

## Gravidade Baixa

| # | Problema | Situação |
|---|---|---|
| B1 | Símbolos de texto usados como ícone (↑ ↓ ● ◎ ✓ ⚠ ＋ ⌫ ▾) | Corrigido: só Material Symbols |
| B2 | Botões +/− dos cartões de Ajustes eram texto | Corrigido |
| B3 | Porcentagens visíveis com "Ocultar valores" | Corrigido |
| B4 | Hash do PIN corrompido fazia o app fechar | Corrigido |
| B5 | Ações sensíveis não pedem o PIN de novo | Pendente |
| B6 | Gravação com plano B não atômico, sem `.bak` | Corrigido |
| B7 | Cópias "danificado" se acumulavam | Corrigido: ficam as 3 mais recentes |
| B8 | Abertura dos dados na thread principal | Pendente |
| B9 | "Apagar tudo" deixava a última notificação | Corrigido |
| B10 | Backup com BOM recusado | Corrigido |
| B11 | Descrição longa de parcela ou recorrência perdia o fim | Corrigido |
| B12 | Ids inválidos num backup mudavam a conta dos lançamentos | Corrigido |
| B13 | Assistente entendia "últimos dez dias" como dezembro | Corrigido |
| B14 | Datas do assistente dependiam do idioma do aparelho | Corrigido |
| B15 | "0.500" virava R$ 500,00; algarismos não latinos aceitos | Corrigido |
| B16 | Eixo do gráfico com "R$ 1000" nas fronteiras | Corrigido |
| B17 | Alvos de toque menores que 48dp | Corrigido |
| B18 | Margens laterais na horizontal | Pendente |
| B19 | Permissão de notificação pedida sem contexto | Pendente |
| B20 | PDF sem botão de compartilhar; coluna de valor estreita | Pendente |
| B21 | Filtro ficava no mês anterior na virada do mês | Corrigido |
| B22 | Higiene do build (hash do Gradle, `.gitignore`, versão do backup) | Parcial: `.gitignore` e aviso de backup de versão mais nova feitos; o GitHub Actions confere o Gradle Wrapper a cada build |

## O que já estava bem feito

- Cifragem AES-256-GCM com chave no Android Keystore, IV aleatório a cada gravação e gravação atômica.
- PIN guardado só como hash PBKDF2 com sal, comparação em tempo constante e teclado que não revela o tamanho.
- Sem permissão de internet, backup do Google desligado, bloqueio de captura de tela ligado por padrão e nenhum log com dados.
- Valores em centavos inteiros, formatação pt-BR independente do idioma do aparelho e relatórios sem divisão por zero.
- Leitor de JSON próprio com limites, validação de backup e CSV protegido contra fórmulas.
