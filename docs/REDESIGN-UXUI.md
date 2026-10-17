# Redesign de UX/UI do Finan+ Android (1.6.0)

Registro das rodadas de ajustes feitas no repositório de desenvolvimento e mescladas na 1.6.0. O resumo para quem usa o app está no [CHANGELOG](../CHANGELOG.md).

## Correções UX/UI da rodada seguinte

- A ação contextual foi compactada em um FAB circular de 56 dp, disponível apenas em Início e Lançamentos. O bottom sheet continua oferecendo despesa, receita e recorrência; o conteúdo mantém espaço inferior reservado e rótulo acessível.
- O status do lançamento agora aparece em texto junto à data (pago/recebido, pendente, vencido, cartão ou recorrência prevista). A ação de confirmar mantém o diálogo existente e oferece **Desfazer** por snackbar, usando a mesma operação financeira persistida.
- Para lançamentos futuros marcados como realizados, a lógica existente move a data efetiva para hoje e preserva o vencimento original; desfazer restaura a data agendada. Foi acrescentada regressão que verifica também o total realizado do mês.
- O saldo previsto da tela Início informa a data final de referência e os componentes considerados, sem reimplementar a fórmula de Finance.futureBalance. A decomposição simples só aparece quando bate com o cálculo existente.
- O calendário mantém seleção diária e detalhes dos lançamentos; o rótulo do resultado diário foi esclarecido para não confundir fluxo do dia com saldo acumulado.
- Estados vazios de contas/cartões, limites e metas agora explicam a ação útil e fornecem CTA contextual. Ajustes foram agrupados visualmente sem remover opções.
- A auditoria de contraste identificou o vermelho de texto do tema Gruvbox abaixo de 4,5:1 em superfície escura (3,82:1). O token foi ajustado para #FF7F7F nos dois temas Gruvbox; o contraste calculado é 5,37:1 no cartão Gruvbox e 7,96:1 no Gruvbox OLED. É uma verificação de tokens, não substitui auditoria visual completa em dispositivo.

### Verificação desta rodada

- Teste de regressão para marcar um lançamento futuro como realizado, conferir a contabilização no mês de hoje e desfazer preservando o vencimento.
- Compilação, testes unitários e auditoria de licenças serão executados pelo GitHub Actions no commit desta rodada.
- Não houve teste visual por emulador nesta sessão.


## Ajuste final

- A seção de gerenciamento de categorias começa minimizada; sua lista e formulário só aparecem após tocar em **Maximizar**. O estado segue reversível pela ação **Minimizar**.
