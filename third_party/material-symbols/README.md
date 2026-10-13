# Material Symbols (ícones do app)

- **O que é:** 50 ícones do conjunto *Material Symbols Rounded* do Google, peso 300, em 54 arquivos (home, pie_chart, settings e swap_horiz também na versão preenchida, `*_fill.xml`), convertidos para vetor Android em `app/src/main/res/drawable/ms_*.xml`. Cada arquivo indica no comentário do topo o nome original, a origem, a licença e a mudança feita.
- **Origem:** https://github.com/google/material-design-icons (pasta `symbols/web/<nome>/materialsymbolsrounded/<nome>_wght300_24px.svg`, ou `_wght300fill1_24px.svg` para os preenchidos). Em 13/10/2026 os traçados dos 54 arquivos foram conferidos contra esses SVG originais: todos idênticos.
- **Licença:** Apache License 2.0 (texto em `LICENSE`, nesta pasta, igual ao do repositório original; também dentro do app em *Ajustes › Sobre › Componentes de terceiros*). A Apache 2.0 é compatível com a GPL-3.0 do Finan+.
- **Uso:** são os ícones do app: barra inferior, categorias, botões rápidos, bloqueio, assistente, calendário e filtros da lista.
- **Mudança feita:** só a conversão de SVG para VectorDrawable (mesmos traços, cor branca para receber a cor do tema). A mudança está anotada no topo de cada arquivo, como pede a seção 4(b) da Apache 2.0.
- **Para acrescentar um ícone:** baixe o SVG da mesma pasta e peso, converta mantendo o traçado e copie o comentário de origem de um `ms_*.xml` existente, trocando o nome. Atualize a lista em `app/src/main/assets/licenca/TERCEIROS.txt` (seção *Material Symbols*).
