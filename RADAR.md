# Radar e "Posso comprar?"

Em teste no repositório de desenvolvimento (próxima versão). Inspirado na proposta "Finanças+" (out/2026), adaptado ao que o Finan+ já sabe: **nada novo para lançar**, tudo sai dos dados que já existem. As contas estão em `core/Radar.kt` (sem Android, testado em JVM em `RadarTest.kt`); as telas em `ui/screens/RadarSheet.kt`.

## Onde fica

- **Início › Radar:** cartão com o minigráfico dos próximos 30 dias, a situação (Tranquilo, Atenção, Risco) e os atalhos **Ver Radar** e **Posso comprar?**.
- **Radar** (folha): gráfico do saldo previsto em 30, 60 ou 90 dias, menor saldo, dias negativos, "O que explica a queda" e a **reserva mínima**.
- **E se…? › Posso comprar?:** primeira pergunta do simulador.

## O que entra na conta

O Radar é o **saldo previsto** do Início (`Finance.futureBalance`) somado dia a dia; o fim de cada dia é sempre igual ao saldo previsto naquela data (há teste para isso).

| No Radar | Vem de | Rótulo |
|---|---|---|
| Ponto de partida | saldo atual de todas as contas (saldo inicial + realizados) | |
| Contas a pagar e a receber | lançamentos pendentes, na data de cada um | Previsto |
| Pendentes vencidos | entram **hoje** | Atrasado |
| Faturas | valor em aberto de cada fatura, no **vencimento** (vencida: hoje) | Fatura |
| Salário, aluguel, assinaturas | recorrências previstas nos meses que ainda não chegaram (fora do cartão) | Recorrente |
| Metas | a reserva para meta já mexe no saldo da conta | |

**Saídas antes das entradas no mesmo dia:** o gráfico e o "menor saldo" usam o pior momento do dia. Uma conta que vence no dia do salário não é coberta por ele (o salário pode cair à tarde).

**O que fica de fora:** gastos do dia a dia que ainda não foram lançados, recorrências no cartão (como no saldo previsto do Início) e rendimentos. A folha diz quantos lançamentos, recorrências e faturas entraram, e avisa quando não há nenhuma receita prevista no período.

## Situação

| Situação | Quando |
|---|---|
| **Risco de saldo negativo** | algum dia fica abaixo de zero |
| **Abaixo da reserva** | não fica negativo, mas fica abaixo da reserva mínima |
| **Sem aperto previsto** | nunca abaixo da reserva (sem reserva: nunca negativo) |

**Reserva mínima:** definida na folha do Radar (vazio ou zero = só avisa saldo negativo). Fica no backup como `"reserve"` (em reais), só quando definida; backups sem o campo continuam iguais. Pelo acesso na rede local, a interface web ainda não conhece o campo, então o celular mantém a reserva dele.

## Posso comprar?

Informe valor, data, conta ou cartão (e parcelas). O Finan+ cria uma compra **só na memória** e compara o Radar **sem** e **com** ela, nas mesmas datas:

- **À vista:** sai da conta no dia escolhido.
- **No cartão:** usa a regra das faturas (compra depois do fechamento vai para a fatura seguinte); cada parcela sai no vencimento da sua fatura.
- O período vai até 30 dias depois do último efeito da compra (no mínimo 30, no máximo 120 dias).
- Mostra o menor saldo sem e com a compra, e o porquê (as maiores saídas antes do aperto e a próxima entrada).
- Nunca diz "compra aprovada": diz se o saldo previsto fica positivo, abaixo da reserva ou negativo.
- **Lançar como previsto** abre o formulário de despesa já preenchido, **a pagar**. Só grava se você salvar.

## Alternativas

Quando a compra deixa o saldo negativo ou abaixo da reserva, até três saídas, só com o que o Finan+ conhece:

1. **Adiar:** o primeiro dia (até 60 dias depois) em que a compra não deixa o saldo negativo, de preferência respeitando a reserva.
2. **Parcelar no cartão:** o menor número de parcelas (2 a 12) que resolve, em qualquer cartão. Avisa que juros aumentam o custo.
3. **Usar dinheiro de uma meta:** quanto tirar da menor meta que cobre a falta. Abre a meta com o valor a retirar já preenchido.

Uma alternativa só aparece se melhora a situação. Tocar nela abre o formulário preenchido; nada é executado sozinho.

## Exemplo (o de `RadarTest`)

Hoje 18/10, R$ 4.200 na conta, reserva R$ 700. A pagar: internet R$ 120 (20/10), aluguel R$ 1.600 (25/10), luz R$ 300 (30/10); fatura do cartão Azul R$ 1.440 (3/11); salário R$ 3.500 recorrente (5/11).

- Radar: menor saldo **R$ 740 em 3/11**, R$ 40 acima da reserva.
- Comprar R$ 900 hoje à vista: **R$ −160 em 3/11** (risco).
- Alternativas: comprar em **6/11** (depois do salário; em 5/11 a manhã ainda ficaria negativa), parcelar em 2× no Azul (fica abaixo da reserva, sem negativo) ou tirar da meta.

## Paridade

Ainda só no Android. Antes de publicar: levar o Radar e o campo `reserve` ao Finan+ web (inclusive a interface da rede local) e ao Linux.
