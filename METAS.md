# Metas e reserva para meta

Desde a 1.7.0, guardar dinheiro numa meta pode **tirar o valor da conta**, como acontece na vida real, sem contar como despesa.

## Como funciona

| Ação | O que acontece |
|---|---|
| **Guardar** (meta › "Guardar ou retirar agora", valor positivo) | Com **Tirar da conta** ligado (padrão), cria o lançamento **"Meta › nome da meta"** na categoria **Metas**: sai do saldo da conta escolhida e aparece em Lançamentos e no calendário. |
| **Retirar** (valor negativo) | Com **Devolver para a conta** ligado, cria **"Retirada da meta › nome"**, que devolve o valor ao saldo da conta. Retirar mais do que está guardado para no zero, e só volta o que havia. |
| **Desligar a opção** | Só o valor guardado na meta muda; o saldo da conta continua igual (o comportamento de antes, para quem já guarda o dinheiro da meta na mesma conta). |
| **Excluir o lançamento** (em Lançamentos) | Desfaz o efeito na meta: um "guardado" excluído diminui o valor guardado, uma retirada excluída aumenta. |
| **Editar o lançamento** | Não é possível: o valor muda pela própria meta (botão **Abrir a meta**). |
| **Excluir a meta** | O que saiu das contas para ela pelo Finan+ (e ainda não voltou) volta para a conta do último depósito, num lançamento **"Meta excluída › nome"**. Os lançamentos antigos ficam no histórico. |

## O que conta e o que não conta

A reserva para meta segue a mesma regra do pagamento de fatura: **mexe no saldo da conta, mas não é receita nem despesa**.

- **Conta:** saldo das contas, saldo previsto, calendário (ponto da cor de destaque, "Meta" na legenda), lista de lançamentos, CSV ("reserva para meta" / "retirada de meta") e relatório em PDF (situação "Meta" / "Da meta").
- **Não conta:** receitas e despesas do mês e do Resumo, % economizado, despesas por categoria, limites mensais, assistente (resumo, dicas e perguntas) e aprendizado de categorias.
- **No Resumo:** abaixo do resultado do período aparece **Guardado em metas** (o que foi guardado menos o que foi retirado). No **fluxo do dinheiro**, o que foi para metas aparece em "Para onde foi › Metas" e o que voltou, em "De onde veio › Retirado de metas", separados dos gastos.

## No backup

O lançamento leva o campo `goalId` (o `id` da meta). Ele nunca fica no cartão nem pendente. Um backup sem esse campo abre como antes.

**Paridade:** o Finan+ web (PWA) e o Linux ainda não entendem o `goalId`: num backup do Android, as reservas para meta aparecem neles como despesas comuns da categoria "Metas".

**Acesso pela rede:** o navegador usa o Finan+ web embutido, que devolve as reservas sem o `goalId`. O celular protege os dados (`core/RemoteSave.kt`): restaura o `goalId` de cada reserva a partir do que já tem e recusa, sem gravar nada, alterar ou excluir uma reserva ou excluir uma meta com dinheiro guardado pelo Finan+. Essas ações continuam no celular. Quando o Finan+ web passar a conhecer o `goalId`, a proteção continua valendo e não precisa mudar.

## Testes

`app/src/test/java/com/finanplus/core/GoalReserveTest.kt`: guardar, retirar (parando no zero), sem tirar da conta, excluir lançamento, edição recusada, excluir meta com devolução, backup e assistente.
