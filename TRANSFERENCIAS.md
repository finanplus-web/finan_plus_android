# Transferência entre contas

Move dinheiro de uma conta para outra (ex.: da Conta principal para a Poupança) sem registrar uma receita nem uma despesa. O saldo de cada conta muda, mas o saldo total continua o mesmo.

## Onde fica

- **Botão +** › **Transferir entre contas**.
- **Meu dinheiro › Contas e cartões** › **Transferir entre contas** (só aparece com duas contas ou mais).
- Na lista de Lançamentos, tocar em qualquer um dos dois lados abre a transferência para editar ou excluir.

## Como funciona

Uma transferência são **dois lançamentos ligados pelo mesmo `transferId`**:

| Lado | Tipo | Conta | Na lista |
|---|---|---|---|
| Saída | `expense` | origem | "Enviada para Poupança", com `−` |
| Entrada | `income` | destino | "Recebida de Conta principal", com `+` |

Os dois lados têm sempre o mesmo valor e a mesma data, ficam na categoria interna `Transferência` (não aparece na lista de categorias) e são sempre realizados.

### O que conta e o que não conta

- **Conta:** o saldo de cada conta (Meu dinheiro, saldo atual, saldo acumulado, widget). Como um lado tira e o outro põe, o saldo total não muda.
- **Não conta** (`Tx.isFlow = false`, como o pagamento de fatura e a reserva para meta): receitas e despesas do mês, Resumo, Evolução, despesas por categoria, limites, relatórios em PDF, assistente, sugestão de categoria, dívidas e o total do dia na lista e no calendário.
- Os filtros **Receitas** e **Despesas** da lista não mostram transferências. Em **Todos**, os dois lados aparecem, um em cada conta.

### Cores

Seguem a regra do app: verde = entrou, vermelho = saiu, âmbar = meta. A transferência não é nenhum dos três, então usa **cor neutra**: ícone de setas (`swap`, Phosphor) no tom neutro e valor na cor do texto.

## Regras

- Precisa de **duas contas ou mais**. Origem e destino precisam ser diferentes. Escolher a mesma conta nos dois campos troca as duas de lugar, e o botão de setas inverte origem e destino.
- **Data de hoje ou passada.** A transferência entra no saldo na hora; uma transferência "agendada" mudaria o saldo antes do dia. Transferência futura fica para depois, se fizer falta.
- Não usa cartão de crédito e não tem parcelas, repetição nem situação pago/pendente.
- **Editar** muda os dois lados juntos (valor, data, contas e descrição). O formulário comum de receita ou despesa recusa editar um lado sozinho.
- **Excluir** qualquer lado exclui os dois.
- Uma conta que tem transferências não pode ser excluída (mesma regra dos outros lançamentos da conta).
- A descrição é opcional. Sem descrição, os dois lados ficam como "Transferência". Os nomes das contas na lista vêm dos nomes atuais, então renomear uma conta já aparece nas transferências antigas.

## Backup, CSV e acesso pela rede

- **Backup JSON:** campo `transferId` nos dois lados. Ao restaurar, um lado sozinho ou um par que não combina (mesmo tipo, valor ou data diferentes, mesma conta, cartão, meta) **deixa de ser transferência** e vira receita ou despesa comum, para o valor continuar visível em vez de mexer no saldo escondido (`Transfers.sanitize`). Versões antigas do app ignoram o campo e mostram os dois lados como receita e despesa da categoria "Transferência".
- **CSV:** tipo `transferência enviada` ou `transferência recebida`.
- **Acesso pela rede (Finan+ web embutido):** o Finan+ web ainda não conhece o `transferId`. Ao gravar, o celular restaura o campo e recusa alterar ou excluir uma transferência pelo navegador; isso continua sendo feito no celular (`RemoteSave.protectTransfers`). No navegador, os dois lados ainda aparecem como receita e despesa até o Finan+ web ganhar a mesma função.

## Código

- `core/Model.kt`: `Tx.transferId`, `Tx.isTransfer`, `AppState.TRANSFER_CAT`.
- `core/Transfers.kt`: lados da transferência, validação do par e descrição.
- `core/Ops.kt`: `saveTransfer` (criar/editar) e `deleteTx` (exclui os dois lados).
- `core/Backup.kt`, `core/RemoteSave.kt`, `lan/LanApi.kt`: formato do backup e gravação pela rede.
- `core/Totals.kt`, `core/Period.kt`, `core/MonthCalendar.kt`, `core/assist/Insights.kt`: fora dos totais.
- `ui/screens/TransferSheet.kt`: formulário. `ui/Root.kt` (`TxRow`): como aparece na lista.
- Testes: `app/src/test/java/com/finanplus/core/TransferTest.kt`.
