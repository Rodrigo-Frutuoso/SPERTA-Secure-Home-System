# 📋 Lista Completa — TUDO o que falta no Sperta Fase 2

---

## A. Infraestrutura (pré-requisitos para o E2E)

### A1. `CommandService.java` — precisa de receber `ObjectInputStream`
- **Atual:** `handleCommand(String command, String requester, ObjectOutputStream out)` — só envia, não recebe
- **Falta:** Adicionar `ObjectInputStream in` ao `handleCommand()` e a `handleCreate()`, `handleAdd()`, `handleEC()`
- **Motivo:** CREATE, ADD e EC precisam de receber dados cifrados do cliente (chaves wrapped, valores cifrados)

### A2. `ClientSessionHandler.java` — passar `inStream` ao `CommandService`
- **Atual (L48):** `commandService.handleCommand(command, user, outStream)`
- **Falta:** Mudar para `commandService.handleCommand(command, user, inStream, outStream)`

### A3. `ClientCommandLoop.java` — carregar chaves RSA da keystore
- **Atual (L22):** Construtor `ClientCommandLoop(out, in, scanner)` — sem acesso a chaves RSA
- **Falta:**
  - Receber `keystorePath` e `keystorePassword` no construtor
  - Carregar `PrivateKey` e `PublicKey` da keystore (via `KeyStore.getInstance(...)`)
  - Adicionar imports: `javax.crypto.*`, `java.security.*`, `java.io.ByteArrayInputStream`, `java.util.HashMap`, etc.

### A4. `ClientSession.java` — passar keystore ao `ClientCommandLoop`
- **Atual (L97):** `new ClientCommandLoop(outStream, inStream, scanner)`
- **Falta:** Mudar para `new ClientCommandLoop(outStream, inStream, scanner, keystore, keystorePassword)`

---

## B. Confidencialidade E2E — Comando CREATE

### B1. `ClientCommandLoop.handleCreate()` — gerar chaves AES por secção
- **Atual:** Envia `"CREATE <hm>"`, recebe e imprime resposta
- **Falta (após receber `"OK"`):**
  1. Gerar 6 chaves AES-128 (uma por secção: E, G, L, M, P, S) com `KeyGenerator.getInstance("AES")`
  2. Fazer `wrap` de cada chave com a chave pública RSA do owner (a nossa)
  3. Enviar ao servidor: `numKeys`, e para cada: `section` (String) + `wrappedKey.length` (int) + `wrappedKey` (bytes)
  4. Receber confirmação do servidor

### B2. `CommandService.handleCreate()` — receber e guardar chaves wrapped
- **Atual:** Cria casa e envia `"OK"`
- **Falta (após enviar `"OK"`):**
  1. Receber `numKeys` (int) do cliente
  2. Para cada: ler `section` (String) + `keyLen` (int) + `wrappedKey` (bytes)
  3. Guardar via `repository.saveWrappedKey(hm, section, owner, wrappedKey)`
  4. Enviar confirmação `"OK"`

---

## C. Confidencialidade E2E — Comando ADD

### C1. `CommandService.handleAdd()` — troca de chaves e certificado
- **Atual:** Verifica permissões, adiciona permissão, envia `"OK"`
- **Falta (em vez de enviar `"OK"` diretamente):**
  1. Verificar se user1 tem certificado (`userHasCertificate`)
  2. Enviar `"OK-KEYS"` em vez de `"OK"`
  3. Enviar certificado do user1: `certBytes.length` (int) + `certBytes` (bytes)
  4. Determinar secções (se `"ALL"` → 6 secções, senão → 1 secção)
  5. Enviar `numSections` (int), e para cada: `section` (String) + `ownerWrappedKey.length` (int) + `ownerWrappedKey` (bytes)
  6. Receber chaves re-wrapped para user1: `numKeys` (int), para cada: `section` + `keyLen` + `user1WrappedKey`
  7. Guardar via `repository.saveWrappedKey(hm, section, user1, user1WrappedKey)`
  8. Adicionar permissão e enviar `"OK"` final

### C2. `ClientCommandLoop.handleAdd()` — unwrap + re-wrap
- **Atual:** Envia comando, recebe e imprime resposta
- **Falta (quando resposta é `"OK-KEYS"`):**
  1. Receber certificado do user1 → extrair `PublicKey` com `CertificateFactory.getInstance("X.509")`
  2. Receber chaves wrapped do owner (para cada secção)
  3. Para cada secção: `unwrap` com a nossa `PrivateKey` → chave AES em claro → `wrap` com `PublicKey` do user1
  4. Enviar chaves re-wrapped ao servidor
  5. Receber e imprimir confirmação final

---

## D. Confidencialidade E2E — Comando EC

### D1. `CommandService.handleEC()` — protocolo com chave wrapped
- **Atual:** Valida permissões, guarda valor em claro via `updateStateAndLog(hm, device, val)`
- **Falta:**
  1. Após verificar permissões, enviar `"OK-KEY"` (em vez de processar valor imediatamente)
  2. Carregar chave wrapped: `repository.loadWrappedKey(hm, section, requester)`
  3. Enviar ao cliente: `wrappedKey.length` (int) + `wrappedKey` (bytes)
  4. Receber valor cifrado do cliente: `encLen` (int) + `encryptedVal` (bytes)
  5. Guardar como Base64: `repository.updateStateAndLogEncrypted(hm, device, encB64)` (em vez de `updateStateAndLog`)

### D2. `ClientCommandLoop.handleEC()` — cifrar valor com chave de secção
- **Atual:** Envia comando, recebe e imprime resposta
- **Falta (quando resposta é `"OK-KEY"`):**
  1. Receber chave wrapped: `keyLen` (int) + `wrappedKey` (bytes)
  2. `unwrap` com a nossa `PrivateKey` → `SecretKey` AES
  3. Cifrar o valor `int` com AES: `Cipher.getInstance("AES")`, `ENCRYPT_MODE`, `doFinal`
  4. Enviar valor cifrado: `encryptedVal.length` (int) + `encryptedVal` (bytes)
  5. Receber e imprimir confirmação final

---

## E. Confidencialidade E2E — Comandos RT e RH

### E1. `CommandService.handleRT()` — enviar chaves wrapped junto com dados
- **Atual:** Envia `"OK"` + `data.length` (long) + `data` (bytes)
- **Falta (entre `"OK"` e os dados):**
  1. Obter secções do utilizador: `repository.getUserSections(hm, requester)`
  2. Enviar `numKeys` (int), e para cada secção: `section` (String) + `wrappedKey.length` (int) + `wrappedKey` (bytes)
  3. Depois enviar os dados (como já faz)

### E2. `ClientCommandLoop.handleRT()` — decifrar estados
- **Atual:** Recebe dados e guarda em ficheiro em claro
- **Falta (após receber `"OK"`):**
  1. Receber chaves wrapped por secção → `unwrap` cada uma
  2. Receber dados
  3. Para cada linha: extrair nome do device, inferir secção (ex: `"E1"` → secção `"E"`), decifrar valor Base64 com a chave AES da secção
  4. Guardar resultado decifrado no ficheiro

### E3. `CommandService.handleRH()` — enviar chave wrapped junto com log
- **Atual:** Envia `"OK"` + `data.length` (long) + `data` (bytes)
- **Falta (entre `"OK"` e os dados):**
  1. Carregar chave da secção: `repository.loadWrappedKey(hm, section, requester)`
  2. Enviar `wrappedKey.length` (int) + `wrappedKey` (bytes)
  3. Depois enviar os dados (como já faz)

### E4. `ClientCommandLoop.handleRH()` — decifrar histórico
- **Atual:** Recebe dados e guarda em ficheiro em claro
- **Falta (após receber `"OK"`):**
  1. Receber chave wrapped → `unwrap`
  2. Receber dados CSV
  3. Para cada linha do CSV: decifrar valor Base64 com a chave AES
  4. Guardar resultado decifrado no ficheiro

---

## F. Métodos auxiliares necessários no `ClientCommandLoop`

### F1. `wrapKey(SecretKey, PublicKey)` → `byte[]`
```
Cipher c = Cipher.getInstance("RSA");
c.init(Cipher.WRAP_MODE, publicKey);
return c.wrap(secretKey);
```

### F2. `unwrapKey(byte[], PrivateKey)` → `SecretKey`
```
Cipher c = Cipher.getInstance("RSA");
c.init(Cipher.UNWRAP_MODE, privateKey);
return (SecretKey) c.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
```

### F3. `loadPrivateKey(keystorePath, keystorePassword)` → `PrivateKey`
### F4. `loadPublicKey(keystorePath, keystorePassword)` → `PublicKey`

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
