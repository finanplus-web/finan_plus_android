# Finan+ Beta · Acesso pela rede (servidor local)

> Versão de teste, **não publicada**. Não faça push deste código para o GitHub enquanto o beta estiver em teste.

Liga um pequeno servidor no celular para registrar lançamentos e exportar dados pelo navegador de
outro aparelho (computador, tablet) **na mesma rede Wi-Fi**. Os dados continuam só no celular: o
navegador lê e grava por ele, enquanto o servidor estiver ligado.

Desde a versão 2 do beta, a conexão é **HTTPS** com uma **autoridade certificadora (CA) própria do
celular**, e cada aparelho novo precisa do **código** e da sua **permissão no celular**.
Desde a versão 3, o celular mostra **um endereço só** e o navegador usa **o mesmo tema do app**.
Desde a versão 4, o navegador abre o **Finan+ web (PWA) completo** — início, lançamentos, relatórios,
assistente, metas, contas, cartões, recorrências, limites, PDF, CSV, backup — lendo e gravando **no celular**.

---

## Índice

1. [Gerar e instalar o beta](#1-gerar-e-instalar-o-beta)
2. [Primeiro uso, passo a passo](#2-primeiro-uso-passo-a-passo)
3. [Instalar o certificado no computador](#3-instalar-o-certificado-no-computador)
4. [Como a segurança funciona](#4-como-a-segurança-funciona)
5. [O que fica no celular](#5-o-que-fica-no-celular)
6. [Permissões](#6-permissões-só-no-build-beta)
7. [Arquivos do projeto](#7-arquivos-do-projeto)
8. [API](#8-api)
9. [Testes](#9-testes)
10. [Limitações conhecidas](#10-limitações-conhecidas)
11. [Histórico do beta](#11-histórico-do-beta)

---

## 1. Gerar e instalar o beta

No Debian (ou qualquer Linux) com JDK 17 e o Android SDK:

```bash
unzip finan_plus_android-beta-lan.zip
cd finan_plus_android
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # ou exporte ANDROID_HOME
./gradlew assembleBeta
adb install -r app/build/outputs/apk/beta/app-beta.apk
```

No Android Studio: **Build › Select Build Variant › beta** e **Run ▶**.

- O APK sai assinado com a **chave de debug** desta máquina (`~/.android/debug.keystore`, criada sozinha).
  Compilando em outro computador, a chave muda: desinstale o beta antes de instalar de novo.
- Instala **ao lado** do app normal: nome **Finan+ Beta**, id `com.finanplus.beta`, dados separados.
  Ele começa vazio; para testar com seus dados, faça **Backup JSON** no app normal e **Restaurar** no beta.
- Não interfere no Finan+ do F-Droid (outro id, outra assinatura).

## 2. Primeiro uso, passo a passo

1. **Celular:** Ajustes › **Acesso pela rede (beta)** › **Iniciar servidor**.
   Na primeira vez o celular cria a CA (leva alguns segundos).
2. **Computador:** abra no navegador o **único endereço** mostrado no celular (ex.: `http://192.168.0.12:8090`).
   - **Primeira vez neste navegador:** a página mostra *“Primeiro acesso neste computador”* com o passo a
     passo. **Confira a impressão digital** com a do celular e instale o certificado (seção 3). No fim,
     o botão *Abrir* leva ao endereço seguro.
   - **Certificado já instalado:** a página percebe sozinha (em menos de 1 s) e abre o Finan+ direto em
     `https://192.168.0.12:8443`, com cadeado. O endereço `https` nunca precisa ser digitado.
3. Digite o **código de 6 dígitos** do celular.
4. **Celular:** aparece *“Permitir Chrome no Linux?”* (em qualquer tela do app, ou como notificação).
   Toque em **Permitir**.
5. Pronto: abre o **Finan+ web completo**, com os dados do celular. Tudo o que você fizer é gravado no
   celular; o que for feito no celular aparece sozinho no navegador (em até ~4 s).

**Tema:** o navegador usa as mesmas cores do app (Sistema, Claro, Material You, OLED, Tokyo Night,
Nord). Trocar o tema no celular muda a página aberta em poucos segundos, sem recarregar. No tema
*Sistema*, claro/escuro segue o computador; no *Material You*, vêm as cores do papel de parede do celular.
O mesmo vale para lançamentos feitos no celular: aparecem sozinhos na página.

Para desligar: **Parar** na notificação ou em Ajustes. Desliga sozinho após **10 min** sem uso.
Também funciona com o **roteador (hotspot) do celular** ligado: o outro aparelho se conecta a ele.

## 3. Instalar o certificado no computador

Feito **uma vez por computador/navegador**. A página `http://<ip>:8090` traz este passo a passo,
com a impressão digital e os botões de download. Também dá para pegar o arquivo pelo próprio
celular: **Ajustes › Acesso pela rede (beta) › Salvar certificado** (e levar por cabo/pendrive, sem rede).

**Sempre confira a impressão digital SHA-256** mostrada no celular antes de confiar. A página de
instalação chega sem criptografia; o celular é a fonte confiável.

| Navegador | Como |
|---|---|
| **Firefox** (qualquer sistema) | Clique em *Baixar finanplus-ca.crt* → janela “Confiar nesta autoridade?” → **Ver** (confira SHA-256) → marque *Confiar nesta AC para identificar sites*. Alternativa: Configurações › Privacidade e segurança › Certificados › Ver certificados › Autoridades › Importar. |
| **Chrome / Edge / Brave no Linux** | Terminal: `sudo apt install libnss3-tools` e `certutil -d sql:$HOME/.pki/nssdb -A -t "C,," -n "Finan+ CA" -i ~/Downloads/finanplus-ca.crt`. Ou `chrome://certificate-manager` › Importar. Reabra o navegador. |
| **Windows** (Chrome/Edge) | Abrir o arquivo › Detalhes (confira a impressão digital **SHA-1**) › Geral › Instalar certificado › Usuário atual › *Autoridades de Certificação Raiz Confiáveis*. |
| **macOS** | Abrir o arquivo (Acesso às Chaves) › duplo clique em “Finan+ Rede Local” › Confiar › *Confiar Sempre*. |
| **Outro Android** | Configurações › Segurança › Criptografia e credenciais › Instalar certificado › *Certificado de CA*. |
| **iPhone/iPad** | Baixar pelo Safari › Ajustes › Perfil Transferido › Instalar; depois Geral › Sobre › Ajustes de Confiança de Certificados › ativar. |

**Remover:** apague “Finan+ Rede Local” da lista de autoridades do navegador/sistema. No Debian/Chrome:
`certutil -d sql:$HOME/.pki/nssdb -D -n "Finan+ CA"`.

## 3b. O Finan+ web dentro do celular (modo remoto)

O navegador não usa mais uma página simplificada: o celular serve o **próprio Finan+ web (PWA)**,
embutido no APK (sempre a mesma versão do app, funciona sem internet).

```
Navegador (PC)                                   Celular
┌──────────────────────────┐   HTTPS   ┌────────────────────────────────┐
│ Finan+ web (PWA)          │──────────▶│ LanServer                       │
│  RemoteStore (memória)    │ GET state │  /api/remote/state  ◀── Repo    │
│  nada gravado no PC       │ PUT state │  valida (Backup.normalize)      │
│                           │  + rev    │  rev diferente → 409 (conflito) │
│  a cada 4 s: /api/rev ────│──────────▶│  mudou? → recarrega             │
└──────────────────────────┘           └────────────────────────────────┘
```

- **Dados só no celular.** O PWA guarda o estado em memória; cada alteração vai ao celular (estado
  inteiro, formato do backup) com a **versão** que foi lida.
- **Sem sobrescrever ninguém.** Se o celular (ou outro navegador) mudou algo nesse meio-tempo, a
  gravação é recusada (409); o navegador mostra a versão do celular e avisa *“Alteração não salva”*.
- **Validação do celular.** Tudo passa pela mesma rotina do backup; se algum item fosse descartado,
  a gravação inteira é recusada (nada se perde por incompatibilidade).
- **Mudanças do celular aparecem sozinhas** (tema, lançamentos, tudo), em até ~4 s.
- **O Finan+ web normal não mudou:** aberto pelo GitHub Pages, instalado ou pela pasta, ele continua
  lendo e gravando os dados **do navegador**, criptografados, como sempre. O endereço do celular é outra
  origem para o navegador, então os dois nunca se misturam.
- **No modo remoto:** sem service worker/cache; avisos de vencimento ficam com o celular; *Apagar tudo*
  só pelo celular; *Restaurar backup* pelo navegador **substitui os dados do celular** (com a revisão e
  a confirmação de sempre); em Ajustes aparece *Conectado ao celular · Desconectar*.
- **Material You:** as cores do papel de parede do celular são aplicadas no navegador; *Sistema* no
  escuro usa o OLED, como no app.

**Atualizar o PWA embutido** (depois de mudar o Finan+ web):

```bash
cd ../finan_plus && npm install && npm run build
../finan_plus_android/tools/sync-pwa.sh .
```

Detalhes do lado do PWA: `MODO-REMOTO.md` no repositório do Finan+ web.

## 4. Como a segurança funciona

### 4.1 HTTPS com CA própria

```
Celular                                              Computador
┌──────────────────────────────┐                     ┌─────────────────────────┐
│ Android Keystore             │  (instalado 1 vez)  │ Navegador               │
│  └ chave da CA (não sai)     │ ── finanplus-ca.crt ─▶ confia na CA do Finan+ │
│        │ assina              │                     │                         │
│        ▼                     │      TLS 1.3/1.2    │                         │
│ certificado do servidor      │ ◀──────────────────▶│ https://192.168.0.12:8443│
│  (IP atual, chave nova,      │                     │                         │
│   30 dias, só em memória)    │                     │                         │
└──────────────────────────────┘                     └─────────────────────────┘
```

- **Chave da CA no Android Keystore**: criada dentro do Keystore (EC P-256), marcada só para assinar
  e **não exportável**. Nem o app consegue ler a chave; ele só pede assinaturas.
- **Certificado do servidor novo a cada vez que o servidor liga**: chave EC P-256 nova, só em
  memória, válido por 30 dias e **só para o IP daquele momento** (se o IP mudar, ele é reemitido no
  próximo início).
- **TLS 1.3 e 1.2** apenas (o que o Android oferecer dentro disso).
- **A CA não serve para sites da internet** (*Name Constraints* crítico): ela só vale para
  10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 e para nomes `.invalid` (que não existem). Mesmo que
  alguém copiasse a chave, não conseguiria se passar pelo seu banco ou e-mail. Testado: o Chromium
  recusa (`ERR_CERT_INVALID`) um certificado desta CA com qualquer nome fora disso, e o OpenSSL
  acusa *permitted subtree violation*.
- **pathLen 0**: a CA não pode criar outras autoridades.
- O certificado da CA vale 10 anos. **Gerar novo** (em Ajustes) apaga a chave e cria outra: todos os
  computadores precisam reinstalar.

### 4.2 Pareamento em duas etapas

1. **Código de 6 dígitos** mostrado no celular. Muda a cada uso (um código visto por cima do ombro
   não serve de novo) e após 5 erros, com espera de 30 s.
2. **Permitir no celular**: com o código certo, o celular mostra *“Permitir Firefox no Linux
   (192.168.0.20)?”*.
   - Dentro do app: janela em qualquer tela, **só com o app desbloqueado** (PIN/digital).
   - Fora do app: notificação com **Abrir e permitir** (abre o app, que pede o desbloqueio) e
     **Recusar** (funciona direto). Na tela de bloqueio a notificação não mostra detalhes.
   - Fechar a janela conta como **Recusar**. O pedido expira em 2 minutos.
3. Só depois disso o navegador recebe um **token aleatório de 256 bits**, entregue **uma única vez**.
   O servidor guarda apenas o hash (SHA-256), em memória. Parar o servidor invalida todos.

Em Ajustes aparecem os **aparelhos conectados**, cada um com **Desconectar**. Limite de 4 aparelhos.

### 4.3 Outras proteções

| Proteção | Como funciona |
|---|---|
| Só na rede local | Escuta apenas no IP privado do Wi-Fi/hotspot, nunca em todas as interfaces |
| Dados só por HTTPS | A porta HTTP (8090) só entrega a página de instalação e o `.crt`; qualquer `/api` nela responde *Use HTTPS* |
| Outros sites | `Host` precisa ser o IP:porta do celular (bloqueia *DNS rebinding*); `Origin` de outro site é recusado; sem CORS |
| Limites | 4 conexões simultâneas, fila de 8 (sem vaga: fecha na hora), 16 KB de cabeçalho, 64 KB de conteúdo, 10 s por leitura e **30 s por conexão inteira** (derruba conexões lentas de propósito) |
| Requisições | Validação estrita: só GET/POST/PUT/DELETE e HTTP/1.0–1.1, `Host`/`Content-Length`/`Authorization`/`Origin` sem repetição, sem `Transfer-Encoding`, caminho sem `..` nem `\`; cada campo da API com formato exato |
| Tentativas | 5 códigos errados **do mesmo endereço** → aquele endereço espera 30 s e o código muda; os outros aparelhos continuam podendo parear |
| Gravação honesta | Se o celular não estiver conseguindo salvar (arquivo com problema, disco cheio), a gravação pela rede é recusada (`503`) em vez de ficar só na memória |
| Registro | Log `FinanLan` só com nomes de eventos e tipos de erro: nunca código, token, conteúdo ou dados financeiros |
| Página | O PWA vem com a mesma CSP dele (sem scripts inline, `connect-src 'self'`) + `frame-ancestors 'none'`, `X-Frame-Options: DENY`, `no-store`, COOP/CORP. Arquivos servidos só por nomes simples (sem `..`); `sw.js` nunca é servido |
| Estado grande | Até 8 MB só para `PUT /api/remote/state` e só com token válido; o resto continua em 64 KB |
| Navegador | Token só em `sessionStorage` (some ao fechar a aba); sem cookies |
| Verificação do certificado | A página HTTP só pode consultar `GET /api/ping` no endereço seguro (CSP `connect-src` + `Cross-Origin-Resource-Policy` liberado só nessa rota, que não tem dados) |
| Inatividade | Desliga após 10 min sem uso por um aparelho pareado. A consulta de mudanças da página (a cada 4 s, só com a aba visível) **não conta como uso** |

### 4.4 Do que isto protege (e do que não)

| Ameaça | Situação |
|---|---|
| Alguém na mesma rede “escutando” o Wi-Fi | **Protegido**: tudo cifrado (TLS) depois que a CA foi instalada |
| Alguém na rede tentando entrar | **Protegido**: precisa do código **e** do seu toque em Permitir |
| Site malicioso aberto no computador | **Protegido**: Host/Origin conferidos, sem CORS |
| Alguém trocar o `.crt` na página de instalação (HTTP) | **Mitigado**: confira a impressão digital no celular, ou use *Salvar certificado* e leve por cabo |
| CA do celular usada para falsificar sites | **Bloqueado** pelas *Name Constraints* (só IPs privados e `.invalid`) |
| Celular roubado | Fora do escopo do beta: o servidor só liga pelo app (que tem PIN/digital, se ativado) |
| Computador já comprometido (vírus) | **Não protegido**: quem controla o navegador vê o que você vê |

## 5. O que fica no celular

| Item | Onde | Some com |
|---|---|---|
| Chave privada da CA | Android Keystore, alias `finanplus_lan_ca` (não exportável) | *Gerar novo*, *Apagar tudo*, desinstalar |
| Certificado da CA (público) | `files/lan/ca.crt` | idem |
| Certificado e chave do servidor | só memória | parar o servidor |
| Tokens dos aparelhos (hash) | só memória | parar o servidor, *Desconectar* |

## 6. Permissões (só no build beta)

`INTERNET`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_SPECIAL_USE`,
declaradas em `app/src/beta/AndroidManifest.xml`. Os builds **debug** e **release** (F-Droid) não
mudam: continuam sem permissão de internet, e o código do servidor fica inativo
(`BuildConfig.LAN_BETA = false`, removido pelo R8 no release).

## 7. Arquivos do projeto

| Arquivo | O quê |
|---|---|
| `app/src/main/java/com/finanplus/lan/LanServer.kt` | Liga as peças e controla o ciclo de vida (partida, parada, inatividade, eventos) |
| `app/src/main/java/com/finanplus/lan/HttpServer.kt` | Transporte HTTP/1.1 genérico: portas, threads, prazos, leitura e validação da requisição, escrita com cabeçalhos de segurança |
| `app/src/main/java/com/finanplus/lan/LanTls.kt` | Socket TLS 1.3/1.2 com o certificado do servidor |
| `app/src/main/java/com/finanplus/lan/Pairing.kt` | Código, bloqueio por endereço, pedidos, tokens e aparelhos (lógica pura, sem rede) |
| `app/src/main/java/com/finanplus/lan/LanApi.kt` | `LanBackend`, rotas e validação de cada campo, `StateVersion`, `PwaAssets`, `UserAgent` |
| `app/src/main/java/com/finanplus/lan/LanConfig.kt` | Portas, tempos e limites (validados), configuráveis no build; `LanLog` |
| `app/src/main/java/com/finanplus/lan/X509.kt` | Gerador de certificados X.509 (codificador DER próprio): CA com *Name Constraints* e certificado do servidor por IP |
| `app/src/main/java/com/finanplus/lan/LanCa.kt` | CA do celular no Android Keystore (criar, conferir, apagar) |
| `app/src/main/java/com/finanplus/lan/LanService.kt` | Serviço em primeiro plano e notificações (status e pedidos) |
| `app/src/main/java/com/finanplus/lan/Lan.kt` | Estado para a tela, comandos, configuração, log e descoberta do IP privado (`LocalNetwork`) |
| `app/src/main/java/com/finanplus/lan/RepoBackend.kt` | Liga os dados do app (`Repo`) ao servidor; recusa gravar quando o app não está salvando |
| `app/src/main/java/com/finanplus/lan/LanCompose.kt` | Seção em Ajustes e a janela “Permitir …?” |
| `app/src/beta/assets/lan/pwa/` | Finan+ web (PWA) embutido: `index.html`, `style.css`, `js/app.bundle.js`, ícones (~750 KB, **só no APK beta**) |
| `app/src/beta/assets/lan/setup.html` | Página de instalação do certificado, com passo a passo por navegador |
| `tools/sync-pwa.sh` | Copia o PWA (já compilado) para `app/src/beta/assets/lan/pwa` |
| `app/src/main/java/com/finanplus/ui/theme/Theme.kt` | `paletteFor()`: a escolha de cores de cada tema fora do Compose (o app e o navegador usam a mesma) |
| `app/src/main/java/com/finanplus/ui/Root.kt` | Mostra “Permitir …?” em qualquer tela (só no beta, só desbloqueado) |
| `app/src/main/java/com/finanplus/ui/screens/SettingsScreen.kt` | Chama a seção do beta; *Apagar tudo* também apaga a CA |
| `app/src/beta/…` | Manifesto com as permissões e o serviço; nome “Finan+ Beta” |
| `app/build.gradle.kts` | Build type `beta` e os campos `LAN_BETA` e `LAN_CONFIG` |
| `app/src/test/java/com/finanplus/lan/*Test.kt` | 30 testes (pareamento, segurança, modo remoto, certificados) e `LanFixture` (servidor real em 127.0.0.1) |
| `AUDITORIA-LAN.md` | Auditoria de segurança, resiliência e arquitetura deste recurso |

Sem dependências novas (nada de Ktor, Netty ou Bouncy Castle): o APK quase não cresce.

## 8. API

Porta segura (`https://<ip>:8443`). Todas as rotas `/api` (menos as de pareamento) exigem
`Authorization: Bearer <token>`.

| Método e caminho | Faz |
|---|---|
| `POST /api/pair` `{"code":"123456"}` | Código certo → `202 {"pending":"<id>","label":"Chrome no Linux","timeout":120}` |
| `GET /api/pair/<id>` | `{"status":"waiting"}` · `{"status":"approved","token":"…"}` (uma vez) · `{"status":"denied"}` · `410` expirado |
| `GET /api/remote/state` | PWA: `{rev, palette, data}` (estado completo no formato do backup) |
| `PUT /api/remote/state` `{"rev":7,"data":{…}}` | PWA grava o estado inteiro. `200 {rev}` · `409` versão velha · `422` item inválido · `503` o celular não está salvando (nada gravado) |
| `GET /api/rev` | `{"rev":"<dados>-<cores>","data":<versão dos dados>}` — não conta como uso |
| `GET /api/ping` | Sem token. `{"ok":true}`: a página de instalação testa se o navegador confia no certificado |
| `POST /api/logout` | Encerra o token |

Porta de instalação (`http://<ip>:8090`): `GET /` (página: abre o app direto se o certificado já for
confiável, senão o passo a passo), `GET /finanplus-ca.crt` (DER), `GET /finanplus-ca.pem`.

Formatos aceitos: código `^\d{6}$`; id do pedido 32 hex; token 64 hex; `rev` inteiro ≥ 0; `data` objeto.
Fora disso: `400`. A API da página simplificada (betas 1–3) foi removida na auditoria.

## 8b. Configuração (sem segredos)

Não há segredos a configurar: a chave da CA nasce no Android Keystore e os tokens só existem em memória.
Os parâmetros operacionais têm padrões seguros e podem ser trocados **no build**, sem mexer no código:

```bash
./gradlew assembleBeta -PfinanLan="httpsPort=9443;httpPort=9090;idleMinutes=5"
# ou: ORG_GRADLE_PROJECT_finanLan="httpsPort=9443" ./gradlew assembleBeta
```

Chaves: `httpPort`, `httpsPort`, `idleMinutes`, `maxDevices`, `workers`. Valores fora dos limites
(portas < 1024, inatividade < 1 min…) são recusados na partida, com mensagem na tela.

## 9. Testes

```bash
./gradlew testDebugUnitTest --tests "com.finanplus.lan.*"
```

30 testes em JVM pura (sem Android), descritos em `AUDITORIA-LAN.md`: pareamento (bloqueio por
endereço, token uma vez, expiração, limites), segurança (só HTTPS com a CA, Host/Origin, 11 tipos de
requisição mal formada, *path traversal*, corpo grande só com token, conexões lentas derrubadas),
modo remoto (versão, conflito, item inválido, celular sem salvar → 503, inatividade) e certificados
(*Name Constraints*). O CI do PR também compila o build beta.

No PWA (`npm test` no repositório do Finan+ web): 85 testes, 8 deles do modo remoto.

Também verificado durante o desenvolvimento (fora do Gradle):

- `openssl verify -x509_strict`: certificado do servidor válido; IP público e nome DNS real → *permitted subtree violation*.
- `curl` e `openssl s_client`: TLS 1.3, verificação OK só com a CA.
- **Chromium real** com a CA instalada no NSS (`certutil`): abre sem aviso; sem a CA →
  `ERR_CERT_AUTHORITY_INVALID`; certificado com nome fora das restrições → `ERR_CERT_INVALID`.
- Fluxo completo no navegador: código → “Confirme no celular” → permitido → lançamento salvo; e recusado.
- Primeiro acesso: navegador **sem** o certificado fica no passo a passo; **com** o certificado, o mesmo
  endereço HTTP abre o app em HTTPS sozinho.
- Tema: Tokyo Night → Nord → Claro trocados “no celular” com a página aberta; a página muda em 1–4 s.
- **PWA completo pelo celular (Chromium):** conectar → confirmar → o Finan+ web abre com os dados do
  celular; lançamento feito no navegador chega ao celular; lançamento feito no celular aparece no
  navegador; tema trocado no celular muda o navegador; conflito (celular mudou com o editor aberto) →
  *“Alteração não salva”* e nada sobrescrito; nada financeiro no `localStorage`, sem service worker.
- **PWA normal (sem o celular):** continua gravando no navegador e relendo após recarregar.

Não testado aqui (precisa do aparelho): Android Keystore, notificações, janela “Permitir”, a seção
de Ajustes e as cores reais do Material You. Erros de compilação ou de uso: anotar e reportar.

## 10. Limitações conhecidas

- A página de instalação (porta 8090) é HTTP: **confira a impressão digital** com o celular ou use
  *Salvar certificado* e leve o arquivo por cabo.
- Se o IP do celular mudar, o endereço muda (o certificado é reemitido ao iniciar).
- Faixa CGNAT (100.64.0.0/10) e IPv6 não são cobertos pela CA.
- Fechar a janela “Permitir” sem querer conta como Recusar: é só digitar o novo código.
- Cada alteração envia o estado inteiro: com muitos milhares de lançamentos, gravar pode levar um
  instante a mais (limite de 8 MB).
- Se a conexão cair, a alteração aparece no navegador mas não é gravada (há aviso); ao reconectar, a
  tela volta aos dados do celular.

## 11. Histórico do beta

### beta 5 — auditoria (segurança, resiliência, arquitetura)
Relatório completo em `AUDITORIA-LAN.md`. Principais mudanças:
- **Segurança:** bloqueio de tentativas por endereço (antes, um aparelho travava o pareamento de todos);
  TLS e confirmação obrigatórios (sem modo inseguro); API antiga removida; validação estrita de HTTP e de
  cada campo; escape correto dentro de `<script>`; mensagens de erro sem detalhes internos.
- **Integridade:** gravação pela rede recusada (`503`) quando o app não está conseguindo salvar; falha
  momentânea do Keystore não recria mais a CA (antes desfazia a confiança de todos os computadores).
- **Resiliência:** prazo total por conexão (contra conexões lentas de propósito); conexão recusada sem
  travar a thread que aceita; partida do serviço sem servidor órfão; eventos fora de locks; mesma porta ao
  religar; tolerância a falhas momentâneas ao aceitar; cores do tema em cache; PWA sem consultas sobrepostas
  e validando as respostas do celular.
- **Arquitetura:** `LanServer` (~800 linhas) dividido em `HttpServer`, `LanTls`, `Pairing`, `LanApi`,
  `LanServer`; `LanService.kt` dividido em `Lan.kt`, `RepoBackend.kt`, `LanService.kt`; `LanConfig` única;
  log `LanLog` sem dados sensíveis; CI compila o build beta. 30 testes novos/reescritos no app, 85 no PWA.

### beta 4 — o Finan+ web completo no navegador
- O celular serve o **próprio Finan+ web (PWA)**, embutido no APK beta, no lugar da página simplificada.
- PWA ganhou o **modo remoto** (`js/remote.js`): dados no celular, nada gravado no navegador, gravação
  com versão (conflito → 409, sem sobrescrever), validação do celular, mudanças do celular aparecem
  sozinhas, Material You com as cores do celular, *Desconectar*, avisos e *Apagar tudo* com o celular.
- O **modo normal do PWA não mudou** (dados locais do navegador, como sempre).
- Servidor: `GET/PUT /api/remote/state`, versão por contador, CSP do PWA, arquivos só por nomes
  seguros, `sw.js` nunca servido, 8 MB só com token. Páginas web agora ficam só no APK beta.
- `tools/sync-pwa.sh` para atualizar o PWA embutido. 4 testes novos no app (24) e 7 no PWA (84).

### beta 3 — correções
- **Tema no navegador igual ao do app** (bug): a página usava só claro/escuro do computador. Agora recebe
  as cores exatas do tema escolhido no celular (inclusive Material You) e muda sozinha quando você troca
  o tema, sem recarregar. `Theme.kt` ganhou `paletteFor()`, usada pelo app e pelo servidor.
- **Primeiro acesso sem confusão** (bug): o celular mostrava primeiro o endereço `https`, que não abre sem
  o certificado. Agora mostra **um endereço só** (o de instalação). Sem certificado: passo a passo
  *“Primeiro acesso neste computador”*. Com certificado: abre o Finan+ direto em `https`.
- A notificação também mostra só esse endereço.
- Lançamentos feitos no celular aparecem sozinhos na página aberta (mesma consulta do tema).
- 4 testes novos (20 no total).

### beta 2 — HTTPS com CA própria e confirmação no celular
- **HTTPS** (porta 8443) com certificado emitido por uma **CA própria do celular**, chave no Android
  Keystore; certificado do servidor novo a cada início, só para o IP atual.
- CA restrita a **IPs privados** e `.invalid` (*Name Constraints*), sem sub-autoridades.
- Porta HTTP (8090) virou só **instalação do certificado**, com passo a passo por navegador e
  impressão digital SHA-256/SHA-1. Dados nunca passam por ela.
- **Confirmação no celular** (“Permitir Chrome no Linux?”): janela no app (só desbloqueado) e
  notificação com *Abrir e permitir* / *Recusar*. Token entregue uma vez, depois da permissão.
- **Aparelhos conectados** em Ajustes, com *Desconectar*.
- **Salvar certificado** (arquivo pelo celular) e **Gerar novo** (invalida o antigo).
- *Apagar tudo* também apaga a CA.
- Página web: tela “Confirme no celular”, indicação de conexão criptografada.
- Gerador X.509 próprio (sem bibliotecas) e 9 testes novos.

### beta 1 — servidor local
- Servidor HTTP próprio (sem bibliotecas), serviço em primeiro plano, build type `beta` separado.
- Pareamento por código de 6 dígitos, token de 256 bits, Host/Origin, limites, desligamento por inatividade.
- App web: lançar, editar, pago/pendente, excluir com parcelas, navegar por mês, exportar JSON/CSV,
  ocultar valores, tema escuro automático.
- Correção: etiqueta “pendente” cortada em telas estreitas (descrição agora quebra linha).
