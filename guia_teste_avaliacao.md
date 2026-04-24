# Guião de Avaliação - Projeto SegC (Fase 2)

> **Terminal 1** = rodrigo · **Terminal 2** = tiago · **Terminal 3** = simao
> **Máquina 1** = SpertaServer · **Máquina 2** = SpertaClients

---

## 1. Preparação (Parte 1) — Arranque e Comandos Funcionais *(4,5 val)*

### 1.1 Arrancar o Servidor (Máquina 1)
```bash
java -jar dist/SpertaServer.jar 22345 secret src/sperta/certs/server.keystore server-password
```

### 1.2 Arrancar dois Clientes (Máquina 2)
**Terminal 1 (rodrigo):**
```bash
java -jar dist/SpertaClient.jar localhost:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password rodrigo frutas
```
**Terminal 2 (tiago):**
```bash
java -jar dist/SpertaClient.jar localhost:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password tiago leite1
```

### 1.3 Testar comandos no Terminal 1 (rodrigo)
```text
> CREATE casa1
> ADD tiago casa1 P
> RD casa1 P
> EC casa1 P1 1
> RT casa1
> RH casa1 P1
```

---

## 2. Gestão da Concorrência *(0,5 val)*

> **Verificação:** Ver código no servidor.

👉 **Onde mostrar:**
- `server/DataRepository.java` — blocos `synchronized (fileLock)` nas **linhas 123, 448, 464, 486, 497, 508, 524, 540, 555, 600, 660, 682, 708, 742, 767, 805, 837, 844, 857, 866, 874, 897**
- `server/AuthService.java` — `synchronized (activeUsers)` nas **linhas 112 e 125**

---

## 3. Keystores + Truststores *(1 val)*

> **Verificação:** Usar `keytool` para ver conteúdo.

```bash
keytool -list -v -keystore src/sperta/certs/server.keystore -storepass server-password
keytool -list -v -keystore src/sperta/certs/client.keystore -storepass client-password
keytool -list -v -keystore src/sperta/certs/client.truststore -storepass truststore-password
```

---

## 4. Utilização de Sockets SSL *(1,5 val)*

> **Verificação:** Ver código no servidor e no cliente.

👉 **Onde mostrar:**
- `server/SpertaServer.java` — **linhas 73–78**: `SSLServerSocketFactory.getDefault()`, criação do `SSLServerSocket`, e `ss.accept()` que devolve um `SSLSocket`
- `client/ClientSession.java` — **linhas 32–37**: Configuração das propriedades TLS (`javax.net.ssl.trustStore`, etc.) e criação do `SSLSocket` com `SSLSocketFactory.getDefault().createSocket()`

---

## 5. Confidencialidade das Passwords *(1 val)*

> **Verificação:** Ver formato do ficheiro de utilizadores: `<user-id>:<hash(password + salt)>:<salt>`

👉 **Mostrar ficheiro:**
```bash
cat src/sperta/data/server/user.txt
```
*(Nota: o ficheiro está cifrado com PBE, pelo que vai apresentar conteúdo binário — o formato pode ser demonstrado via código.)*

👉 **Onde mostrar no código:**
- `server/DataRepository.java` **linhas 463–478** — método `addUser()`: gera salt, calcula hash, guarda no formato `user:hashB64:saltB64`
- `server/CryptoUtils.java` **linha 15** — constante `SHA-256`, e **linhas 26–27** — método `hashPassword()` que calcula `SHA-256(password + salt)`

---

## 6. Autenticação de Clientes *(0,5 val)*

> **Verificação:** Ver no código se o servidor verifica a síntese de (password + salt).

👉 **Onde mostrar:**
- `server/AuthService.java` **linhas 130–157** — método `authenticateOrRegister()`: lê o registo do utilizador, descodifica `storedHash` e `salt` (linhas 145–146), calcula `computedHash` (linha 147), e compara com `CryptoUtils.isHashEqual()` (linha 149)

---

## 7. Servidor cifra ficheiros com PBE/AES128 *(1 val)*

> **Verificação:** Ver conteúdo dos ficheiros (deve estar cifrado) e ver geração da chave AES baseada na password.

👉 **Mostrar ficheiros cifrados:** Abrir no VS Code os ficheiros em `src/sperta/data/server/houses/` — o editor mostrará a mensagem *"The file is not displayed... binary or unsupported encoding"*, provando que estão cifrados.

👉 **Onde mostrar no código:**
- `server/DataRepository.java` **linha 49** — constante `PBE_ALGORITHM = "PBEWithHmacSHA256AndAES_128"`
- `server/DataRepository.java` **linhas 61–70** — método `initPBEKey()`: cria a `PBEKeySpec` com password, salt e iterações, e gera a chave com `SecretKeyFactory`
- `server/DataRepository.java` **linhas 131–145** — método `encryptBytes()`: cifra com `Cipher.getInstance(PBE_ALGORITHM)`
- `server/DataRepository.java` **linhas 148–165** — método `decryptBytes()`: decifra com `Cipher.DECRYPT_MODE`
- `server/DataRepository.java` **linhas 87–119** — método `encryptAttestationIfNeeded()`: cifra o `attestation.txt` com PBE

---

## 8. Integridade dos Ficheiros *(1 val)*

> **Verificação:**
> 1. É guardado o hash de cada ficheiro (e atualizado em qualquer alteração).
> 2. O hash é verificado a cada acesso de leitura.
> 3. O servidor imprime aviso e termina quando há corrupção.

👉 **Onde mostrar no código:**
- **Escrita do hash:** `server/DataRepository.java` **linhas 167–176** — método `writeHash()`: calcula `SHA-256` e guarda no ficheiro `.hash`
- **Verificação do hash:** `server/DataRepository.java` **linhas 178–191** — método `verifyHash()`: lê `.hash`, calcula novo hash e compara com `MessageDigest.isEqual()`
- **Leitura segura (cifrada):** `server/DataRepository.java` **linhas 211–237** — método `secureReadFile()`: decifra, verifica hash, e faz `System.exit(-1)` se falhar (**linhas 224–225, 229–230, 233–234**)
- **Leitura segura (plain):** `server/DataRepository.java` **linhas 256–281** — método `plainReadFile()`: mesma lógica sem decifra
- **Verificação completa ao arranque:** `server/DataRepository.java` **linhas 347–392** — método `verifyAllIntegrity()`: percorre users, houses, states, logs, certs e attestation
- **Invocação no construtor:** `server/DataRepository.java` **linha 58** — `verifyAllIntegrity()`

👉 **Teste prático de corrupção:**
1. Desligar o servidor (Ctrl+C).
2. Editar manualmente um ficheiro cifrado (ex: `src/sperta/data/server/houses/casa1.txt` — alterar um caractere qualquer).
3. Tentar arrancar o servidor novamente:
   ```bash
   java -jar dist/SpertaServer.jar 22345 secret src/sperta/certs/server.keystore server-password
   ```
4. **Resultado esperado:** O servidor imprime `NOK-INTEGRITY` e **termina imediatamente**.
5. Para continuar os testes, limpar dados: `mvn clean package`

---

## 9. Atestação Remota *(1,5 val)*

> **Verificação 1 (0,5 val):** O servidor envia o nonce para o cliente.

👉 **Onde mostrar:**
- `server/AuthService.java` **linhas 31–34** — método `performAttestation()`: gera nonce com `SecureRandom().nextLong()` e envia com `outStream.writeLong(nonce)`

> **Verificação 2 (0,5 val):** O cliente concatena nonce + ficheiro executável, calcula SHA256 e envia.

👉 **Onde mostrar:**
- `client/ClientSession.java` **linhas 42–55** — recebe nonce (`inStream.readLong()`), lê o JAR do ficheiro executável, calcula `SHA-256` da concatenação (`nonce + jarBytes`) com `MessageDigest.getInstance("SHA-256")`

> **Verificação 3 (0,5 val):** Testar o mecanismo.

👉 **Teste Caso 1 — cliente válido:** Arrancar cliente normalmente → ligação bem-sucedida.

👉 **Teste Caso 2 — referência inválida:** Alterar o ficheiro de referência listado em `src/sperta/server/attestation.txt` (ou substituí-lo por outro ficheiro qualquer). Arrancar o cliente → o servidor rejeita com `NOK-ATTEST`.

---

## 10. Servidor de Certificados (Parte 2) *(1 val)*

### 10.1 Verificação dos certificados iniciais *(0,5 val)*

1. **Verificar** que na pasta `src/sperta/data/server/certs/` só existem **dois certificados** (`.cer`): `rodrigo.cer` e `tiago.cer`.
2. **Terminal 3 (simao):** Abrir um novo terminal na Máquina 2 e autenticar o `simao`:
   ```bash
   java -jar dist/SpertaClient.jar localhost:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password simao alexandre
   ```
3. **Verificar** que na pasta `src/sperta/data/server/certs/` agora existem **três certificados**, incluindo `simao.cer`.

👉 **Onde mostrar no código:**
- `server/AuthService.java` **linhas 80–86 e 95–106** — método `receiveCertificateFromClient()`: recebe o certificado do cliente e guarda-o
- `server/DataRepository.java` **linhas 485–494** — método `saveUserCertificate()`: guarda o ficheiro `.cer`

### 10.2 Partilha do certificado via ADD *(0,5 val)*

1. **Verificar** que a `truststore` do `rodrigo` ainda **não possui** o certificado do `simao`:
   ```bash
   keytool -list -keystore src/sperta/certs/client.truststore -storepass truststore-password | findstr simao
   ```
   *(Não deve aparecer nada.)*

2. No **Terminal 1 (rodrigo)**:
   ```text
   > CREATE c1
   > ADD simao c1 M
   ```

3. **Verificar** que agora a `truststore` **já possui** o certificado do `simao`:
   ```bash
   keytool -list -keystore src/sperta/certs/client.truststore -storepass truststore-password | findstr simao
   ```
   *(Deve aparecer uma entrada para "simao".)*

👉 **Onde mostrar no código:**
- `client/ClientCommandLoop.java` **linhas 179–180** — `saveCertToTruststore(user1, certBytes)` no comando ADD
- `client/ClientCommandLoop.java` **linhas 424–447** — método `saveCertToTruststore()`: carrega a truststore, insere o certificado com `setCertificateEntry()`, e guarda no ficheiro

---

## 11. Confidencialidade Fim-a-Fim (Parte 3) *(4 val)*

### 11.1 Geração das Chaves da Secção *(0,5 val)*

👉 **Onde mostrar no código (geração + cifra com chave pública do owner):**
- `client/ClientCommandLoop.java` **linhas 383–386** — método `generateAESKey()`: `KeyGenerator.getInstance("AES")`, tamanho 128 bits
- `client/ClientCommandLoop.java` **linhas 390–393** — método `wrapKey()`: `Cipher.getInstance("RSA")`, `Cipher.WRAP_MODE`, cifra com chave pública
- `client/ClientCommandLoop.java` **linhas 151–152** — no comando CREATE: `generateAESKey()` + `wrapKey(sectionKey, publicKey)` para cada secção

👉 **Teste:**
No **Terminal 1 (rodrigo)**:
```text
> CREATE c2
```
**Verificar no servidor:** na pasta `src/sperta/data/server/keys/` devem existir **6 chaves cifradas** com nomes `key.c2.<s>.rodrigo` (uma por cada secção).

### 11.2 Partilha das Chaves da Secção *(1 val)*

No **Terminal 1 (rodrigo)**:
```text
> ADD tiago c2 P
> ADD simao c2 P
```
**Verificar no servidor:** na pasta `src/sperta/data/server/keys/` devem existir: `key.c2.P.tiago` e `key.c2.P.simao`.

👉 **Onde mostrar no código:**
- `client/ClientCommandLoop.java` **linhas 191–193** — no comando ADD: `unwrapKey()` com a privKey do owner, depois `wrapKey()` com a pubKey do novo utilizador

### 11.3 Envio de Comandos Cifrados *(1 val)*

No **Terminal 1 (rodrigo)**:
```text
> RD c2 P
```
No **Terminal 2 (tiago)**:
```text
> EC c2 P1 5
```

👉 **Onde mostrar no código:**
- **Cliente cifra o valor:** `client/ClientCommandLoop.java` **linhas 231–234** — `unwrapKey(wrappedKey, privateKey)` para obter a chave de secção, depois `encryptAES(valBytes, sectionKey)`
- **Método de cifra AES:** `client/ClientCommandLoop.java` **linhas 403–407** — `Cipher.getInstance("AES")`, `Cipher.ENCRYPT_MODE`
- **Servidor guarda cifrado (sem ver):** `server/DataRepository.java` **linhas 802–833** — método `updateStateAndLogEncrypted()`: guarda o valor em Base64 tal como recebido, sem decifrar

### 11.4 Obtenção de Informações Cifradas *(1,5 val)*

👉 **Onde mostrar no código (receção e decifra da Chave da Secção e das informações):**
- **Unwrap da chave:** `client/ClientCommandLoop.java` **linhas 396–400** — método `unwrapKey()`: `Cipher.getInstance("RSA")`, `Cipher.UNWRAP_MODE`, devolve `SecretKey`
- **Decifra AES:** `client/ClientCommandLoop.java` **linhas 410–414** — método `decryptAES()`: `Cipher.getInstance("AES")`, `Cipher.DECRYPT_MODE`
- **No comando RH:** `client/ClientCommandLoop.java` **linhas 325–347** — unwrap da chave, e para cada linha do log decifra o valor com `decryptAES(encBytes, sectionKey)`
- **No comando RT:** `client/ClientCommandLoop.java` **linhas 260–290** — unwrap das chaves de cada secção, e decifra do valor de cada dispositivo

👉 **Teste:**
No **Terminal 1 (rodrigo)**:
```text
> RD c2 M
> EC c2 M1 1
```
No **Terminal 3 (simao)**:
```text
> RH c2 P1
```
✅ `simao` deve ver o log em **texto claro** (tem permissão na secção P).

```text
> RH c2 M1
```
✅ `simao` deve receber **NOPERM** (não tem acesso à secção M).

```text
> RT c2
```
✅ `simao` deve ver em texto claro **apenas as informações da secção P** (P1). Não deve ver dados de M1.

---

## 12. Impressão Geral *(1 val)*

> Código organizado, legível, e com o sistema a funcionar corretamente.

---

## 13. Preparação e Qualidade da Discussão *(2 val)*

- ✅ Os `.jar` submetidos são os mesmos executados nos terminais.
- ✅ Todos os elementos do grupo respondem sem hesitações.
- ✅ Demonstração sem incidentes e dentro do tempo.
