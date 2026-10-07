# Auditoria · Acesso pela rede — 07/10/2026

Escopo: o recurso **Acesso pela rede** do Finan+ Android (pacote `com.finanplus.lan`; publicado na 1.2.0)
e o **modo remoto** do Finan+ web (`js/remote.js` e os pontos de integração em `js/app.js`).
Código escrito com auxílio de IA nas betas 1–4; esta auditoria trata esse código como legado.

Banca: DevSecOps (OWASP ASVS / Top 10, STRIDE) · SRE (resiliência, concorrência, falhas) ·
Arquitetura (Clean Code, SOLID, dívida técnica).

Todos os achados abaixo **foram corrigidos** nesta revisão, exceto os marcados como *risco aceito*.

---

## Etapa 1 — Vulnerabilidades (DevSecOps)

### Crítico / Alto

| # | Achado | STRIDE / OWASP | Correção |
|---|---|---|---|
| A1 | **Bloqueio global do pareamento.** 5 códigos errados de *qualquer* aparelho travavam o pareamento de *todos* por 30 s; um aparelho na rede repetindo isso impedia você de conectar para sempre. | DoS · A07 | Bloqueio **por endereço** (`Pairing`, mapa LRU limitado a 256). Quem erra fica esperando; os outros continuam. |
| A2 | **Modo inseguro alcançável em produção.** `LanServer(tls = null)` servia app e API em HTTP, e `requireConfirm = false` entregava token sem "Permitir" no celular. Eram parâmetros "para testes", mas uma regressão numa chamada desligaria criptografia e confirmação sem nenhum erro. | Spoofing / Info. disclosure · A02, A04 | Parâmetros removidos: TLS e confirmação são **obrigatórios**. Os testes usam o caminho real. |
| A3 | **API antiga exposta sem uso.** `/api/state`, `/api/tx` (criar/editar/excluir), `/api/tx/{id}/paid`, `/api/export.*` da página simplificada continuavam ativas depois da troca pelo PWA. | Superfície de ataque · A04 | Removidas (≈250 linhas). Restam só as rotas que o PWA usa. |
| A4 | **Gravação aceita sem ser salva.** Com o arquivo de dados com problema ou disco cheio, o `Repo` não gravava, mas a rede respondia `200`: o navegador achava que salvou e a alteração sumia ao fechar o app. | Tampering (integridade) · A04 | `LanBackend.writeBlocked()`: o celular recusa com `503` e o PWA avisa. Também impede ligar o servidor nessa situação. |
| A5 | **CA recriada em silêncio.** `LanCa.peek` engolia qualquer exceção (`runCatching`) e devolvia `null`; uma falha **momentânea** do Keystore fazia `get()` apagar a CA e criar outra — todos os computadores perdiam a confiança sem aviso. | Denial of trust · A08 | "Não existe" ≠ "não respondeu": `KeystoreUnavailable` interrompe a partida com mensagem; nada é apagado. Só recria se faltar, não corresponder ou expirar. |

### Médio

| # | Achado | Correção |
|---|---|---|
| M1 | **Conexões lentas de propósito ("slowloris").** Só havia prazo por leitura (10 s): mandar 1 byte a cada 9 s prendia as 4 threads indefinidamente; respostas grandes não tinham prazo. | Prazo **total por conexão** (`connectionDeadlineMillis`, 30 s), com vigia que derruba as atrasadas. Testado com 6 conexões paradas para 2 threads + 1 de fila. |
| M2 | **Resposta 503 na thread que aceita conexões.** Num socket TLS, escrever faz o *handshake* ali, sem prazo: um cliente parado travava o servidor inteiro. | Sem vaga, a conexão é **fechada sem responder**. |
| M3 | **HTTP frouxo.** Aceitava qualquer método e `HTTP/1.x`, `Host`/`Content-Length` repetidos (ambiguidade entre camadas), nomes de cabeçalho inválidos, `Content-Length` com sinal. | Validação estrita no início do fluxo (`HttpServer.readHead`): métodos GET/POST/PUT/DELETE, HTTP/1.0/1.1, cabeçalhos únicos, nomes RFC 7230, tamanho só dígitos, alvo ≤ 2 KB, caminho sem `..`, `\` ou controle. |
| M4 | **Entradas da API sem formato.** O código aceitava qualquer texto (filtrava dígitos), `rev` aceitava negativo/fracionário, `data` qualquer tipo, ids sem formato. | Formatos exatos: código `^\d{6}$`, pedido 32 hex, aparelho 12 hex, token 64 hex, `rev` inteiro seguro ≥ 0, `data` objeto. |
| M5 | **Escape no contexto errado.** A URL segura entrava num `<script>` com escape de HTML (seguro por acaso: vem do IP). | `jsonForScript` para dentro de `<script>` (escapa `< > &`), `Html.escape` só em HTML. |
| M6 | **Erro verboso.** Falha de validação do backup devolvia `e.message` (detalhes internos) ao navegador. | Mensagem fixa ao cliente; no log, só o tipo da exceção. |
| M7 | **Sem registro de falhas.** Erros engolidos sem log em vários pontos. | `LanLog`: eventos sem dados sensíveis (nunca código, token, corpo, dados financeiros). Android: `Log` com a tag `FinanLan`. |

### Verificado sem achado

Sem segredos no código ou no build (chaves e tokens só em tempo de execução) · chave da CA não
exportável (Android Keystore) · *Name Constraints* (CA só para IPs privados e `.invalid`) · tokens de
256 bits guardados só como SHA-256, em memória · código comparado em tempo constante · `Host`/`Origin`
(DNS rebinding, CSRF) · CSP sem scripts inline no PWA · `X-Frame-Options`, `nosniff`, `Referrer-Policy`,
COOP/CORP · sem SQL (SQLi não se aplica) · o servidor não faz requisições (SSRF não se aplica) · sem
recursos de outros usuários por id (IDOR não se aplica) · *path traversal* testado.

### Riscos aceitos (documentados)

- **Página de instalação em HTTP:** alguém na rede poderia trocar o `.crt`. Mitigação: conferir a
  impressão digital com o celular, ou levar o arquivo pelo próprio celular (*Salvar certificado*).
- **Servidor continua ligado com o app bloqueado por PIN:** é o uso pretendido (você no computador).
  Desligamento por inatividade e *Parar* na notificação continuam valendo.
- **Sem limite de taxa nas rotas autenticadas:** só aparelhos aprovados chegam a elas.

---

## Etapa 2 — Resiliência e concorrência (SRE)

| # | Achado | Correção |
|---|---|---|
| S1 | **`catch` genérico.** `catch (e: Exception)` virava 500 sem log; `catch (_: IOException) {}`; `runCatching` escondendo erros do Keystore, dos arquivos e das notificações. | Exceções específicas: `SocketTimeoutException`, `SSLException` (navegador sem a CA — esperado), `IOException`, e `RuntimeException` só na fronteira, **registrada**. A thread de partida do serviço também tem fronteira (antes um erro inesperado derrubava o app). |
| S2 | **Prazos.** Sem prazo total por conexão nem no handshake do caminho de rejeição (M1, M2). | Prazo total + vigia a cada 250 ms; leitura 10 s. |
| S3 | **Corrida na partida do serviço.** A partida roda numa thread; se o serviço fosse destruído (ou *Parar* tocado) nesse meio-tempo, o servidor subia **órfão**: sem serviço, sem notificação, sem como parar. | Estado de ciclo de vida sob lock (`starting`, `stopRequested`, `destroyed`); se algo pediu parada durante a partida, o servidor é parado logo depois. |
| S4 | **Eventos sob lock.** `start()`/`approve()` chamavam o código do app com locks internos seguros → risco de *deadlock* se o app chamasse o servidor de volta. | Eventos coletados e disparados **fora** de locks; falha num ouvinte é registrada e não derruba o servidor. |
| S5 | **Vazamento de socket.** Se a 2ª porta falhasse ao abrir, a 1ª ficava aberta. | Desfaz tudo na falha de partida. |
| S6 | **`SO_REUSEADDR` depois do `bind`** (sem efeito): religar logo após parar caía noutra porta (conexões em TIME_WAIT) e o endereço mostrado mudava. | Socket criado sem endereço; `reuseAddress` antes do `bind`. |
| S7 | **Uma falha ao aceitar derrubava tudo** (ex.: muitos arquivos abertos). | Tolera até 5 falhas seguidas com espera crescente; depois, para com erro visível. |
| S8 | **Crescimento sem limite.** Mapa de tentativas por endereço. | LRU de 256; pedidos (2) e aparelhos (4) já limitados. |
| S9 | **Consultas sobrepostas no PWA.** Com o celular lento (>4 s), novas consultas se empilhavam. | Uma por vez. |
| S10 | **Trabalho repetido.** As cores do tema (inclusive Material You) eram recalculadas a cada consulta de cada navegador (4 s). | Cache por tema, modo claro/escuro e minuto. |
| S11 | **PWA confiava no formato das respostas** (`rev`, `data`). | Validação; fora do formato → erro, nada alterado. |

*Circuit breaker:* não se aplica a um único par na rede local. O equivalente aqui é: limite de
conexões, prazo por conexão, falha rápida quando ocupado, e o PWA tratando "sem conexão" sem
gravar nada (aviso e reconexão).

---

## Etapa 3 — Arquitetura e "slop"

| # | Achado | Correção |
|---|---|---|
| C1 | **Classe "faz-tudo".** `LanServer` (~800 linhas): sockets, TLS, parser HTTP, rotas, pareamento, sessões, API, PWA, página de instalação e versões. Violação de SRP. | Separado: `HttpServer` (transporte, genérico), `LanTls`, `Pairing` (lógica pura, testável sem rede), `LanRoutes` + `PwaAssets` + `UserAgent` + `StateVersion` (`LanApi.kt`), `LanServer` (composição e ciclo de vida, 140 linhas). |
| C2 | **"Cabeçalhos mágicos".** Configuração contrabandeada dentro do mapa de cabeçalhos (`x-csp-connect-src`, `x-csp-full`) e filtrada na escrita — acoplamento implícito. | `HttpResponse` com campos tipados `csp` e `corp`; cabeçalhos validados contra injeção de CR/LF. |
| C3 | **Código morto** da beta 1 (API e página simples). | Removido (A3). |
| C4 | **Flags de teste em produção** (`requireConfirm`, `tls = null`). | Removidas (A2). |
| C5 | **Arquivo misturando camadas.** `LanService.kt` tinha estado de UI, adaptador de dados, descoberta de rede e o serviço. | `Lan.kt` (estado e comandos, `LocalNetwork`), `RepoBackend.kt`, `LanService.kt`. |
| C6 | **Constantes espalhadas** e textos fixos duplicados ("10 min" na tela e no servidor). | `LanConfig` única e validada; a tela lê dela. |
| C7 | **Duplicação no PWA:** `esc` reescrito em `remote.js`. | Usa o de `ui.js`. |
| C8 | Dependências: **nenhuma adicionada** (sem Ktor, Netty ou Bouncy Castle). Sem APIs inventadas: o servidor compila e roda em JVM; as APIs Android usadas existem no `compileSdk 35`. | — |

**Dívida técnica restante (consciente):** parâmetro `extraDns` de `X509.issueServer` existe só para
os testes de *Name Constraints*; os desvios `REMOTE` em `js/app.js` (8 pontos) poderiam virar um
objeto de estratégia; HTTP sem *keep-alive* (decisão de produto: manter simples).

---

## Etapa 4 — Código refatorado

O código está no repositório (este PR). Requisitos:

1. **Validação e higienização no início do fluxo:** `HttpServer.readHead/readBody` (protocolo) e
   `LanRoutes` (formatos de cada campo) antes de qualquer lógica; `Backup.normalize` com recusa
   total se algo seria descartado; no PWA, `RemoteStore` valida as respostas do celular.
2. **Erros granulares e logs limpos:** respostas com status específico (400/401/403/404/405/409/
   411/413/422/429/431/503) e mensagens sem detalhes internos; `LanLog` só com nomes de eventos e
   tipos de exceção.
3. **Variáveis sensíveis isoladas:** **não há segredos** a isolar — chaves nascem no Keystore e
   tokens em memória, nunca em código, build ou variável de ambiente (isso seria *pior*). Os
   **parâmetros operacionais** (portas, inatividade, limites) saíram do código para `LanConfig`,
   configurável sem recompilar código-fonte: propriedade Gradle `finanLan` ou variável de ambiente
   `ORG_GRADLE_PROJECT_finanLan` no build; `FINAN_LAN_*` em JVM. Valores inválidos são recusados.
4. **Idiomático, tipado, SOLID:** resultados como `sealed interface` (`PairAttempt`, `PairStatus`,
   `LanEvent`), interfaces pequenas (`LanBackend`, `HttpHandler`, `LanLog`), dependências injetadas
   (relógio, configuração, log), classes com uma responsabilidade.

## Testes

| Onde | Quantos | O quê |
|---|---|---|
| `PairingTest` | 9 | código, bloqueio **por endereço**, permitir/recusar, token entregue uma vez, expiração, limites, desconectar, rótulos |
| `LanSecurityTest` | 10 | só HTTPS com a CA, API exige pareamento, porta HTTP sem dados, Host/Origin, 11 tipos de requisição mal formada, nomes de arquivo, corpo grande só com token, **conexões lentas derrubadas e servidor de pé**, cabeçalho incompleto |
| `LanRemoteTest` | 8 | ler/gravar com versão, conflito, item inválido, entrada inválida, **celular sem salvar → 503**, revisão (dados × cores), desconectar, inatividade |
| `X509Test` | 3 | *Name Constraints*, certificado da CA, configuração |
| Finan+ web | 85 | 77 existentes + 8 do modo remoto (inclui resposta fora do formato) |
| Navegador real (Chromium) | — | instalação → HTTPS automático, conectar → permitir, gravar nos dois sentidos, tema, conflito, nada gravado no navegador; PWA normal intacto |

Não testado aqui (precisa do aparelho): Keystore, notificações, janela "Permitir", Ajustes. O CI do
CI compila o app e roda os testes em JVM a cada envio.
