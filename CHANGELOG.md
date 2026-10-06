# Changelog

## 1.1.0 — Ajustes recolhíveis

Todos os cartões de *Ajustes* agora abrem e fecham com o botão **+ / −**, como "Limites mensais" já fazia. Fechado, cada cartão mostra um resumo do que está configurado:

| Cartão | Resumo quando fechado |
|---|---|
| Aparência | Tema atual (ex.: "Tema: Claro") |
| Privacidade e segurança | "Bloqueio ativo/desativado" e, se for o caso, "valores ocultos" |
| Notificações e widget | "Avisos de vencimento ligados/desligados" |
| Assistente | "N de 3 funções ligadas" |
| Contas e cartões | Nº de contas e de cartões |
| Dados | "Backup, restauração, CSV e relatório em PDF" |

`ui/screens/SettingsScreen.kt`: as seções usam o mesmo componente `Collapsible`; o antigo `Section`, sempre aberto, foi removido. O estado aberto/fechado se mantém ao girar a tela.

## 1.1.0 — Relatório em PDF

Exportação de um relatório financeiro em PDF, para qualquer período: atalhos (este mês, mês passado, este ano, 12 meses, tudo) ou datas livres.

**O que o PDF traz:** resumo (receitas, despesas e saldo, com variação contra o período anterior de mesmo tamanho); a receber, a pagar, média diária de gastos e nº de lançamentos; rosca e tabela de despesas por categoria (percentual, nº de lançamentos, média mensal e limite mensal, com destaque quando passa do limite); gráfico e tabela da evolução mensal; receitas por categoria; as 10 maiores despesas; saldos das contas e progresso das metas; lista completa de lançamentos do período (opcional); e uma nota "Como ler este relatório". Rodapé "Página n de N" em todas as páginas.

| Arquivo | Mudança |
|---|---|
| `core/report/Report.kt` (novo) | Cálculo de todos os números do relatório, sem Android, com as mesmas convenções dos Relatórios do app |
| `export/PdfReport.kt` (novo) | Desenho do PDF A4 com o `PdfDocument` do Android (sem bibliotecas): cartões, rosca, barras mensais, tabelas paginadas com cabeçalho repetido. O layout roda duas vezes para numerar as páginas |
| `ui/screens/ReportExport.kt` (novo) | Folha "Relatório em PDF": atalhos de período, datas, opção de incluir lançamentos, prévia dos totais e aviso de privacidade; salva onde o usuário escolher |
| `ui/Root.kt` / `ui/screens/Sheets.kt` | Nova folha `Sheet.ReportPdf` |
| `ui/screens/ReportsScreen.kt` | Botão "Exportar relatório em PDF", que já abre com o período da aba Lançamentos |
| `ui/screens/SettingsScreen.kt` | Botão "Relatório em PDF" em *Dados* |
| `test/.../core/report/ReportTest.kt` (novo) | 6 testes: totais, pendentes, período anterior, categorias e limites, meses, atalhos de período e formatos |

**Privacidade:** o PDF é gerado no aparelho e salvo onde o usuário escolher. Ele não é criptografado e mostra os valores mesmo com "Ocultar valores" ligado. A folha avisa isso antes de gerar.

## 1.1.0 — Ícones Material

Depois de testar os dois estilos no aparelho, o autor escolheu **só os ícones Material** (Material Symbols Rounded, traço leve, 18–22dp). Os símbolos de texto (⌂ ⇄ ◎ ⚙ ◉ ◆ ✚ ★) foram removidos, junto com o seletor de estilo e a preferência usados no teste.

| Arquivo | Mudança |
|---|---|
| `res/drawable/ms_*.xml` (36 novos) | Ícones Material Symbols Rounded peso 300, convertidos de SVG para vetor Android (poucos KB no total) |
| `ui/components/AppIcon.kt` (novo) | `AppIcon` desenha o ícone com a cor do tema; `categoryIcon` liga cada categoria (e variações comuns, sem acento) a um ícone; categorias desconhecidas mostram a inicial |
| `ui/Root.kt` | Barra inferior (ícone preenchido na aba ativa), botão ＋, ícone de categoria, cartão e ✓ de pago usam `AppIcon`; removido o mapa antigo de símbolos (`ICON`) |
| `ui/screens/HomeScreen.kt` | Engrenagem e botões Receita/Despesa/Meta usam `AppIcon` |
| `ui/screens/Assistant.kt` | ✦ do assistente e da sugestão usam `AppIcon` |
| `ui/screens/LockScreen.kt` | Botão de digital usa o ícone de impressão digital |
| `ui/screens/SettingsScreen.kt` | No Sobre, crédito dos ícones e texto da Licença Apache 2.0 |
| `data/DevicePrefs.kt` | Apaga a preferência `materialIcons`, que só existiu na fase de teste |
| `third_party/material-symbols/` (novo) | Origem, licença (Apache 2.0, texto oficial) e o que foi alterado |
| `assets/licenca/APACHE-2.0.txt` (novo) | Licença dos ícones dentro do app |

Os textos, os botões com "＋ Conta" etc. e os títulos continuam sem ícone, de propósito, para não poluir a tela.

## 1.1.0 — Ajustes após o primeiro teste no aparelho

| Arquivo | Mudança | Motivo |
|---|---|---|
| `ui/Root.kt` | A linha do lançamento agora tem duas linhas fixas: "categoria · conta" e "data · situação". "Em atraso" aparece em vermelho | Com nomes longos, a data e a situação eram cortadas ("15/10/2026 · A") |
| `ui/Root.kt` | Categorias sem ícone próprio mostram a inicial (E, A…) em vez de "•" | Ícone vazio em categorias criadas pelo usuário |
| `ui/Root.kt` | A faixa atrás da barra de status ficou opaca | O conteúdo rolado aparecia por trás do relógio |
| `core/assist/Insights.kt` | O resumo mostra também "A receber neste mês" | O resumo listava as contas a pagar, mas não os valores a receber |
| `ui/components/Today.kt` (novo) | "Hoje" dinâmico compartilhado pelas telas: muda na meia-noite, quando o app volta do segundo plano e quando a data, a hora ou o fuso do aparelho mudam | Data sempre acompanhando o sistema |
| `ui/screens/HomeScreen.kt` | O cartão principal mostra a data completa ("03 de Outubro de 2026") no lugar de só o mês | Pedido do autor |
| `ui/Root.kt`, `HomeScreen.kt`, `ReportsScreen.kt`, `SettingsScreen.kt`, `Assistant.kt` | Usam o "hoje" dinâmico. Saldo previsto, faturas, situação "Em atraso", resumo e dicas se atualizam sozinhos na virada do dia, e as recorrências do mês novo são geradas mesmo com o app aberto | Antes, a data só era atualizada quando a tela era montada de novo |

## 1.1.0 — Licença

- O projeto passa a ser **software livre sob a GNU GPL v3 ou posterior** (`GPL-3.0-or-later`).
- `LICENSE`: texto oficial e integral da GPL v3, idêntico ao publicado pela FSF (`gpl-3.0.txt`, MD5 `1ebbd3e34237af26da5dc08a4e440464`).
- `app/src/main/assets/licenca/LICENSE.txt`: a mesma licença, empacotada no app.
- Aviso de copyright e `SPDX-License-Identifier: GPL-3.0-or-later` no topo de todos os arquivos `.kt` (código e testes) e do `dicionario.txt`.
- *Ajustes › Sobre*: nova seção "Licença" com os avisos legais exigidos pela GPL (copyright, liberdade de redistribuir e modificar, ausência de garantia) e o botão "Ver licença completa".
- `README.md`: seção "Licença".

## 1.1.0 — Assistente no aparelho

Assistente leve e de código aberto: sugestão de categoria, resumo do mês, dicas de economia e perguntas rápidas. Funciona sem internet e sem bibliotecas novas, explica cada resultado e pode ser desligado função por função. Detalhes de cada regra em [ASSISTENTE.md](ASSISTENTE.md).

O formato do backup JSON **não mudou** (continua compatível com o Finan+ web e com a 1.0.x).

### Arquivos novos

| Arquivo | O que é |
|---|---|
| `app/src/main/java/com/finanplus/core/assist/Text.kt` | Normalização de texto (sem acento, sem ruído de extrato) e formatação de datas/percentuais em português |
| `app/src/main/java/com/finanplus/core/assist/Dictionary.kt` | Leitura e uso do dicionário aberto de palavras → categoria |
| `app/src/main/java/com/finanplus/core/assist/Categorizer.kt` | Sugestão de categoria: mesma descrição → aprendizado (Naive Bayes com os lançamentos do usuário) → dicionário |
| `app/src/main/java/com/finanplus/core/assist/Insights.kt` | Resumo do mês e as 7 dicas (duplicado, aumento de preço, ritmo do limite, ritmo do mês, acima da média, pequenos gastos, gastos fixos) |
| `app/src/main/java/com/finanplus/core/assist/Ask.kt` | Interpretador de perguntas em português e respostas calculadas |
| `app/src/main/assets/assistente/dicionario.txt` | Dicionário inicial em texto simples (editável por qualquer pessoa) |
| `app/src/main/java/com/finanplus/data/AssistData.kt` | Carrega o dicionário do APK uma única vez |
| `app/src/main/java/com/finanplus/ui/screens/Assistant.kt` | Telas: cartão no Início, folha do assistente, sugestão no editor, seção em Ajustes |
| `app/src/test/java/com/finanplus/core/assist/AssistTest.kt` | 26 testes novos, um ou mais por regra |
| `ASSISTENTE.md` | Documentação completa de cada função |
| `CHANGELOG.md` | Este arquivo |

### Arquivos alterados

| Arquivo | Mudança | Motivo |
|---|---|---|
| `ui/Root.kt` | Nova folha `Sheet.Assistant` | Abrir o assistente |
| `ui/screens/Sheets.kt` | Mostra a sugestão de categoria abaixo da descrição; abre a folha do assistente | Sugestão de categoria |
| `ui/screens/HomeScreen.kt` | Cartão "✦ Assistente" abaixo dos botões rápidos | Resumo e dicas |
| `ui/screens/SettingsScreen.kt` | Seção "Assistente" (liga/desliga por função, dicas dispensadas, o que foi aprendido); parágrafo no Sobre | Controle e transparência |
| `ui/screens/MovesScreen.kt` | A busca passou a ignorar acentos ("cafe" encontra "Café") | Necessário para "Ver lançamentos" e útil em geral |
| `data/DevicePrefs.kt` | 3 interruptores do assistente e a lista de dicas dispensadas | Configurações locais do aparelho, fora do backup |
| `app/build.gradle.kts` | versionCode 4, versionName 1.1.0 | Nova versão |
| `README.md` | Linha do assistente na tabela de recursos e contagem de testes | Documentação |

### O que não mudou

Nenhuma dependência nova, nenhuma permissão nova, nada enviado para fora do aparelho. Formato dos dados, criptografia, backup, widget e notificações estão iguais.

### Testes

`./gradlew test`: **54 testes** (28 anteriores + 26 do assistente). Todos passam.
