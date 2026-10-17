# Redesign UX/UI — Finan+

## Auditoria da base

- Aplicativo Android nativo em Kotlin, com Jetpack Compose e Material 3; não há alvo iOS no repositório.
- Navegação principal em cinco destinos por pager; o estado de navegação é salvo.
- Estado financeiro imutável, persistido localmente como JSON e protegido por AES-GCM com chave do Android Keystore.
- Valores monetários representados em centavos inteiros (Long); cálculos principais estão em core/Finance.kt e operações/validações em core/Ops.kt.
- Backup JSON, importação/exportação CSV, relatórios PDF, categorias, contas, cartões, parcelas, recorrências, metas, limites, simulador, lembretes, biometria/bloqueio, widget e servidor local opcional já existem.
- O modelo atual representa receita e despesa, mas não tem transferência pareada entre contas. A UI não anuncia transferência até haver modelagem e migração segura.
- Não há migração de esquema nesta etapa. O formato de backup e o armazenamento existentes foram preservados.

## Plano incremental e rollback

Esta branch altera somente componentes Compose, rótulos de navegação, o agrupamento do formulário e a seleção de próximos vencimentos. As mudanças são reversíveis como um único PR; não alteram o esquema persistido nem substituem a lógica de cálculo existente.

1. Design system: tokens semânticos de cor, espaçamento e raio; fundo pontilhado já adaptado à paleta dos temas existentes.
2. Navegação e adição: mantém cinco abas; adiciona seletor modal de despesa, receita e recorrência; mantém o botão contextual apenas em Início e Lançamentos.
3. Formulário: campos essenciais primeiro, opções avançadas recolhidas; descrição opcional usa a categoria como rótulo quando vazia.
4. Início e nomes: botão de privacidade, texto contextual factual, até três vencimentos relevantes, e rótulos “Meu dinheiro” e “Resumo”.
5. Verificação: regressões para vencimentos ordenados e descrição opcional; compilação, testes, APK debug/release e auditoria de licenças por Actions.

## Limitações e validação

- Transferências entre contas não estão disponíveis no modelo atual. Implementá-las exige definir semântica de saldo, histórico, edição/exclusão e uma migração compatível; não foi simulada como receita/despesa.
- A validação visual em dispositivo/emulador ainda depende de executar o APK depois da integração do PR.
- O workflow inicial apontou uma referência ausente ao composable da barra inferior; corrigida nesta atualização. A validação final deve usar a execução de Actions do commit atualizado.
