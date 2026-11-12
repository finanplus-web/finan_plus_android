# Ícones: Phosphor Icons

Todos os ícones do Finan+ Android passaram de **Material Symbols** para **Phosphor Icons** (https://phosphoricons.com), licença MIT. A troca foi feita primeiro no repositório de desenvolvimento, para testes. Só o desenho mudou: telas, textos, tamanhos e cálculos são os mesmos.

## Como os pesos são usados

| Peso | Onde | Como pedir no código |
|---|---|---|
| **Regular** | Padrão: listas, botões, setas, filtros, barra inferior (abas inativas) | `AppIcon(Ico.X, cor)` |
| **Fill** | Aba ativa da barra inferior e o ícone do Assistente | `AppIcon(Ico.X, cor, filled = true)` |
| **Duotone** | Ícones dentro de quadrados (`IconTile` e `CategoryTile`): categorias, Meu dinheiro, Ajustes, cabeçalhos de cartão | `AppIcon(Ico.X, cor, duotone = true)` (o `IconTile` já faz isso) |

No Duotone, a parte clara do desenho é a mesma cor do ícone com opacidade 0,2, então ela acompanha qualquer tema (Claro, Gruvbox, OLED, Tokyo Night, Nord) sem cor fixa.

## Onde ficam

- Desenhos: `app/src/main/res/drawable/ph_<nome>.xml` (Regular), `ph_<nome>_fill.xml` e `ph_<nome>_duotone.xml`, convertidos do pacote `@phosphor-icons/core` 2.1.1 (viewport 256×256, cor branca para receber a cor do tema).
- Mapa conceito → ícone: enum `Ico` em `ui/components/AppIcon.kt`. Uma tela nunca aponta para um arquivo de ícone direto: sempre usa `Ico`, então trocar um desenho é mudar uma linha.
- Licença e créditos: `third_party/phosphor/`, `THIRD_PARTY_NOTICES.md` e *Ajustes › Sobre › Componentes de terceiros* (`assets/licenca/TERCEIROS.txt`).
- Os ícones do Finan+ web servido pelo *Acesso pela rede* (`assets/lan/pwa`) continuam Material Symbols até o PWA ser atualizado.

`Ico.INCOME` e `Ico.EXPENSE` (cartões "Falta receber" e "Falta pagar" do Início e a tela "Adicionar movimentação") usam as setas no círculo, que preenchem melhor o quadrado do que uma seta solta. `Ico.UP` e `Ico.DOWN` (Resumo, botões Receita/Despesa) continuam com as setas simples.

## Mapa completo

| `Ico` | Antes (Material Symbols) | Agora (Phosphor) | Fill |
|---|---|---|---|
| `HOME` | `home` | `house` | sim |
| `MOVES` | `swap_horiz` | `arrows-left-right` | sim |
| `REPORTS` | `pie_chart` | `chart-pie-slice` | sim |
| `SETTINGS` | `settings` | `gear-six` | sim |
| `ADD` | `add` | `plus` |  |
| `REMOVE` | `remove` | `minus` |  |
| `GOAL` | `flag` | `flag` |  |
| `CHECK` | `check` | `check` |  |
| `CARD` | `credit_card` | `credit-card` |  |
| `INFO` | `info` | `info` |  |
| `EDIT` | `edit` | `pencil-simple` |  |
| `GAMES` | `sports_esports` | `game-controller` |  |
| `TREND` | `trending_up` | `trend-up` |  |
| `EXPAND` | `expand_more` | `caret-down` |  |
| `PALETTE` | `palette` | `palette` |  |
| `BELL` | `notifications` | `bell` |  |
| `CALC` | `calculate` | `calculator` |  |
| `WIFI` | `wifi` | `wifi-high` |  |
| `DATABASE` | `database` | `database` |  |
| `SCIENCE` | `science` | `flask` |  |
| `DEBT` | `request_quote` | `invoice` |  |
| `TAG` | `sell` | `tag` |  |
| `SPEED` | `speed` | `gauge` |  |
| `DRAG` | `drag_indicator` | `dots-six-vertical` |  |
| `ARCHIVE` | `archive` | `archive` |  |
| `LOCK` | `lock` | `lock-simple` |  |
| `ASSIST` | `auto_awesome` | `sparkle` | sim |
| `FINGERPRINT` | `fingerprint` | `fingerprint-simple` |  |
| `UP` | `arrow_upward` | `arrow-up` |  |
| `DOWN` | `arrow_downward` | `arrow-down` |  |
| `BACKSPACE` | `backspace` | `backspace` |  |
| `DROPDOWN` | `arrow_drop_down` | `caret-down` |  |
| `WARNING` | `warning` | `warning` |  |
| `SHIELD` | `shield` | `shield` |  |
| `CALENDAR` | `calendar_month` | `calendar-dots` |  |
| `LIST` | `view_list` | `list-bullets` |  |
| `PREV` | `chevron_left` | `caret-left` |  |
| `NEXT` | `chevron_right` | `caret-right` |  |
| `SEARCH` | `search` | `magnifying-glass` |  |
| `CLOSE` | `close` | `x` |  |
| `RECEIPT` | `receipt_long` | `receipt` |  |
| `TUNE` | `tune` | `sliders-horizontal` |  |
| `WALLET` | `account_balance_wallet` | `wallet` | sim |
| `FORECAST` | `bar_chart` | `chart-bar` |  |
| `INCOME` | `arrow_upward` | `arrow-circle-up` |  |
| `EXPENSE` | `arrow_downward` | `arrow-circle-down` |  |
| `LIMIT` | `track_changes` | `target` |  |
| `GROUP` | `group` | `users-three` |  |
| `SHARE` | `share` | `share-network` |  |
| `ATTACH` | `attach_file` | `paperclip` |  |
| `FOOD` | `shopping_cart` | `shopping-cart` |  |
| `RESTAURANT` | `restaurant` | `fork-knife` |  |
| `TRANSPORT` | `directions_car` | `car` |  |
| `FUEL` | `local_gas_station` | `gas-pump` |  |
| `HEALTH` | `favorite` | `heart` |  |
| `FITNESS` | `fitness_center` | `barbell` |  |
| `LEISURE` | `theaters` | `film-slate` |  |
| `SUBSCRIPTIONS` | `subscriptions` | `monitor-play` |  |
| `EDUCATION` | `school` | `graduation-cap` |  |
| `OTHER` | `more_horiz` | `dots-three` |  |
| `SALARY` | `payments` | `money` |  |
| `EXTRA` | `attach_money` | `coins` |  |
| `INVEST` | `savings` | `piggy-bank` |  |
| `PETS` | `pets` | `paw-print` |  |
| `CLOTHES` | `checkroom` | `t-shirt` |  |
| `SHOPPING` | `shopping_bag` | `shopping-bag` |  |
| `BEAUTY` | `spa` | `flower-lotus` |  |
| `TAXES` | `receipt_long` | `receipt` |  |
| `GIFTS` | `redeem` | `gift` |  |
| `BANK` | `account_balance` | `bank` |  |
| `WORK` | `work` | `briefcase` |  |
| `REPEAT` | `repeat` | `repeat` |  |
| `TRANSFER` | `swap_horiz` | `swap` |  |
