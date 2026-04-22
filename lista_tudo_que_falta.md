# 📋 Lista Completa — TUDO o que falta no Sperta Fase 2

---

## A. Infraestrutura (pré-requisitos para o E2E)

### ✅ A1. `CommandService.java` — precisa de receber `ObjectInputStream`
- ~~**Atual:** `handleCommand(String command, String requester, ObjectOutputStream out)` — só envia, não recebe~~
- ~~**Falta:** Adicionar `ObjectInputStream in` ao `handleCommand()` e a `handleCreate()`, `handleAdd()`, `handleEC()`~~
- **FEITO:** `handleCommand`, `handleCreate`, `handleAdd`, `handleEC` agora recebem `ObjectInputStream in`

### ✅ A2. `ClientSessionHandler.java` — passar `inStream` ao `CommandService`
- ~~**Atual (L48):** `commandService.handleCommand(command, user, outStream)`~~
- **FEITO:** `commandService.handleCommand(command, user, inStream, outStream)`

### ✅ A3. `ClientCommandLoop.java` — carregar chaves RSA da keystore
- ~~**Atual (L22):** Construtor `ClientCommandLoop(out, in, scanner)` — sem acesso a chaves RSA~~
- **FEITO:**
  - Construtor recebe `keystorePath` e `keystorePassword`
  - Carrega `PrivateKey` e `PublicKey` da keystore (PKCS12)
  - Imports adicionados: `javax.crypto.*`, `java.security.*`, `CertificateFactory`
  - Métodos auxiliares: `wrapKey`, `unwrapKey`, `encryptAES`, `decryptAES`, `extractPublicKeyFromCert`, `generateAESKey`

### ✅ A4. `ClientSession.java` — passar keystore ao `ClientCommandLoop`
- ~~**Atual (L97):** `new ClientCommandLoop(outStream, inStream, scanner)`~~
- **FEITO:** `new ClientCommandLoop(outStream, inStream, scanner, keystore, keystorePassword)`

---

## B. Confidencialidade E2E — Comando CREATE

### ✅ B1. `ClientCommandLoop.handleCreate()` — gerar chaves AES por secção
- ~~**Atual:** Envia `"CREATE <hm>"`, recebe e imprime resposta~~
- **FEITO:** Após receber "OK", gera 6 chaves AES-128, wrap com PublicKey RSA do owner, envia ao servidor, recebe confirmação

### ✅ B2. `CommandService.handleCreate()` — receber e guardar chaves wrapped
- ~~**Atual:** Cria casa e envia `"OK"`~~
- **FEITO:** Após enviar "OK", recebe numKeys + (section, keyLen, wrappedKey) × 6, guarda via `saveWrappedKey`, envia "OK" final

---

## C. Confidencialidade E2E — Comando ADD

### ✅ C1. `CommandService.handleAdd()` — troca de chaves e certificado
- ~~**Atual:** Verifica permissões, adiciona permissão, envia `"OK"`~~
- **FEITO:** Verifica certificado user1, envia "OK-KEYS" + certBytes + wrapped keys do owner, recebe re-wrapped keys para user1, guarda-as, adiciona permissão, envia "OK" final

### ✅ C2. `ClientCommandLoop.handleAdd()` — unwrap + re-wrap
- ~~**Atual:** Envia comando, recebe e imprime resposta~~
- **FEITO:** Quando recebe "OK-KEYS": recebe cert do user1 → extrai PublicKey → recebe wrapped keys → unwrap com PrivateKey → re-wrap com PublicKey do user1 → envia ao servidor → recebe "OK" final

---

## D. Confidencialidade E2E — Comando EC

### ✅ D1. `CommandService.handleEC()` — protocolo com chave wrapped
- ~~**Atual:** Valida permissões, guarda valor em claro via `updateStateAndLog(hm, device, val)`~~
- **FEITO:** Envia "OK-KEY" + wrapped key ao cliente, recebe valor cifrado (bytes), converte a Base64, guarda via `updateStateAndLogEncrypted`. Também adicionado `updateStateAndLogEncrypted` ao DataRepository.

### ✅ D2. `ClientCommandLoop.handleEC()` — cifrar valor com chave de secção
- ~~**Atual:** Envia comando, recebe e imprime resposta~~
- **FEITO:** Quando recebe "OK-KEY": recebe wrapped key → unwrap com PrivateKey → cifra valor com AES → envia ciphertext → recebe "OK" final

---

## E. Confidencialidade E2E — Comandos RT e RH

### ✅ E1. `CommandService.handleRT()` — enviar chaves wrapped junto com dados
- ~~**Atual:** Envia `"OK"` + `data.length` (long) + `data` (bytes)~~
- **FEITO:** Envia "OK" + wrapped keys por secção (numKeys, section, keyLen, wrappedKey) + data

### ✅ E2. `ClientCommandLoop.handleRT()` — decifrar estados
- ~~**Atual:** Recebe dados e guarda em ficheiro em claro~~
- **FEITO:** Recebe wrapped keys → unwrap → recebe dados → decifra cada linha (Base64→AES decrypt) → guarda decifrado

### ✅ E3. `CommandService.handleRH()` — enviar chave wrapped junto com log
- ~~**Atual:** Envia `"OK"` + `data.length` (long) + `data` (bytes)~~
- **FEITO:** Envia "OK" + wrapped key da secção do device + data

### ✅ E4. `ClientCommandLoop.handleRH()` — decifrar histórico
- ~~**Atual:** Recebe dados e guarda em ficheiro em claro~~
- **FEITO:** Recebe wrapped key → unwrap → recebe dados CSV → decifra valor Base64 de cada linha → guarda decifrado

---

## F. Métodos auxiliares necessários no `ClientCommandLoop`

### ✅ F1-F4 — Todos implementados em A3
- `wrapKey`, `unwrapKey`, `encryptAES`, `decryptAES`, `extractPublicKeyFromCert`, `generateAESKey`
- PrivateKey/PublicKey carregados no construtor do ClientCommandLoop

---

## G. Correções dos Problemas do PBE

### G1. `attestation.txt` — cifrar com PBE
- **Onde:** `AuthService.readReferenceJarPath()` (L177-188)
- **Falta:** Usar `repository` para ler o ficheiro cifrado em vez de `BufferedReader`/`FileReader`
- **Nota:** O `pom.xml` (L69) gera o `attestation.txt` em claro no build — será necessário cifrar na primeira execução do servidor, ou ajustar a lógica

### G2. Logs `.csv` — NÃO cifrar com PBE
- **Onde:** `DataRepository.updateStateAndLog()` / `updateStateAndLogEncrypted()` (L627-698) e `readDeviceLog()` (L670-675)
- **Falta:** Criar métodos `plainWriteFile()` / `plainReadFile()` que:
  - Escrevem/leem ficheiros **sem** cifração PBE
  - **Mantêm** o hash SHA-256 de integridade (`.hash`)
- Usar estes métodos para os ficheiros de log em vez de `secureReadFile()`/`secureWriteFile()`
- Também atualizar `verifyAllIntegrity()` para verificar logs **sem** decifrar com PBE

---

## H. Problemas Menores

### H1. Tipo de keystore hardcoded
- **Onde:** `ClientSession.java` L110 — `KeyStore.getInstance("JKS")`
- **Falta:** Usar o mesmo tipo da keystore que foi gerada (ou `KeyStore.getDefaultType()`)

### H2. `README.md` desatualizado
- L30: diz "tamanho" → deveria dizer "path para a cópia de referência"
- L75-77: diz "valida o tamanho" → deveria dizer "valida o hash SHA-256"

### H3. Certificados não guardados na truststore do cliente (no ADD)
- O enunciado diz que ao receber o certificado de outro user, o cliente deve guardá-lo na sua truststore
- **Prioridade baixa** — o sistema funciona sem isto porque o servidor fornece o certificado quando necessário

---

## 📊 Resumo por Ficheiro

| Ficheiro | Nº de alterações |
|---|---|
| `ClientCommandLoop.java` | 8 (A3, B1, C2, D2, E2, E4, F1-F4) |
| `CommandService.java` | 5 (A1, B2, C1, D1, E1, E3) |
| `ClientSessionHandler.java` | 1 (A2) |
| `ClientSession.java` | 2 (A4, H1) |
| `DataRepository.java` | 1 (G2) |
| `AuthService.java` | 1 (G1) |
| `README.md` | 1 (H2) |
