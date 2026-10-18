# Backup automático

Em teste no repositório de desenvolvimento. Fica em **Ajustes › Dados › Backup automático**. O código está em `data/AutoBackup.kt` (cópia, agendamento e senha), `core/BackupCrypto.kt` (criptografia, testada em JVM em `BackupCryptoTest.kt`) e `ui/screens/AutoBackupSettings.kt` (tela).

## Como funciona

1. Defina uma **senha** (mínimo 8 caracteres, digitada duas vezes) e toque em **Escolher pasta e ligar**.
2. Escolha uma pasta pelo seletor do Android: do celular, do cartão SD, do Syncthing, do Nextcloud etc. O Finan+ só tem acesso a essa pasta.
3. A primeira cópia é feita na hora. Depois, **uma vez por dia, de madrugada** (por volta das 3h, quando a bateria não está fraca; o Android pode atrasar um pouco).

- Arquivo: `finan-plus-auto-AAAA-MM-DD.json`, um por dia. No mesmo dia, a cópia nova substitui a anterior.
- **Guardar:** as últimas 7, 14 (padrão), 30 ou 60 cópias. As mais antigas **com esse nome** são apagadas; os outros arquivos da pasta nunca são tocados.
- **Fazer agora**, **Trocar pasta**, **Trocar senha** e **Desligar**. Desligar para o agendamento e esquece pasta e senha; os arquivos já gravados ficam na pasta.
- A tela mostra a última cópia (data, hora e arquivo) e o último erro. Se uma cópia falhar (pasta removida, sem acesso, sem espaço), aparece também uma notificação discreta, sem valores.

## Segurança

- Cada arquivo é criptografado com **AES-256-GCM**, com chave derivada da senha por **PBKDF2-HMAC-SHA256** (310.000 iterações), sal e IV aleatórios em cada cópia. Sem a senha, o arquivo não revela nada (nem nomes nem valores).
- A senha fica **só neste aparelho**, cifrada por uma chave do **Android Keystore** (como as senhas de fatura lembradas). Não vai para o backup nem para o acesso pela rede. **Guarde a senha fora do celular**: se o celular for perdido, ela é a única forma de abrir as cópias.
- Trocar a senha vale para as próximas cópias; as antigas continuam abrindo com a senha antiga.
- "Apagar todos os dados" desliga o backup automático e esquece pasta e senha.

## Proteções

- **Nunca copia um app vazio por engano:** se o arquivo de dados do Finan+ não pôde ser lido (o app abriu vazio por erro), nenhuma cópia é feita, para não substituir as boas.
- **Sem lançamentos, não apaga cópias antigas:** se o app estiver vazio de verdade (ex.: depois de apagar tudo), a cópia do dia é gravada, mas as anteriores ficam.
- Grava o estado mais recente (salva pendências antes de copiar).

## Restaurar

**Ajustes › Dados › Restaurar**, escolha o arquivo `finan-plus-auto-…json` e digite a senha. Depois aparece a mesma revisão do backup comum ("Backup com X lançamentos…"), antes de substituir.

## Formato do arquivo

Um JSON com o backup comum criptografado dentro:

```json
{"format":"finanplus-backup-cifrado","v":1,"kdf":"PBKDF2-HMAC-SHA256","iter":310000,
 "salt":"<16 bytes em base64>","cipher":"AES-256-GCM","iv":"<12 bytes em base64>","data":"<texto cifrado + tag de 16 bytes, base64>"}
```

- Chave: PBKDF2-HMAC-SHA256(senha em UTF-8, salt, iter) → 32 bytes.
- `data` decifrado = o mesmo JSON do "Backup JSON" (com o bloco `_backup` e `"auto": true`).
- Ao ler: só aceita `v` = 1 e `iter` entre 100.000 e 5.000.000.

São algoritmos padrão (WebCrypto no navegador, OpenSSL no Linux), para o Finan+ web e o Linux poderem restaurar essas cópias.

## Paridade

Ainda só no Android. O Finan+ web e o Linux ainda não abrem o backup criptografado; o "Backup JSON" comum continua funcionando entre todos.
