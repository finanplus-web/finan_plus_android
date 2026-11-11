# Material Symbols

Os ícones do app Android deixaram de ser Material Symbols: agora são **Phosphor Icons** (ver [`third_party/phosphor/`](../phosphor/) e [ICONES.md](../../ICONES.md)). Os antigos `res/drawable/ms_*.xml` foram removidos.

Esta pasta continua no repositório só por causa do **Finan+ web servido pelo Acesso pela rede** (`app/src/main/assets/lan/pwa`), que ainda usa ícones Material Symbols como caminhos SVG dentro de `js/app.bundle.js`.

- **Origem:** https://github.com/google/material-design-icons
- **Licença:** Apache License 2.0 (texto em `LICENSE`, nesta pasta; também dentro do app em *Ajustes › Sobre › Componentes de terceiros*). Compatível com a GPL-3.0 do Finan+.
