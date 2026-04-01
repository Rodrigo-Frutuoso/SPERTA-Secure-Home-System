# Plano de Implementação – Sperta Fase 2 (Segurança)

## Resumo

A Fase 2 adiciona **4 grandes áreas de segurança** ao sistema Sperta existente, mantendo todos os comandos e sintaxe da Fase 1 inalterados:

1. **TLS** – Substituir sockets TCP simples por sockets SSL/TLS (autenticação unilateral do servidor)
2. **Passwords seguras + Certificados** – Armazenar passwords com hash SHA-256 + salt; trocar certificados de utilizadores no primeiro registo
3. **Atestação remota segura** – Usar nonce + SHA-256 do JAR em vez do tamanho do ficheiro
4. **Confidencialidade fim-a-fim** – Chave de Secção AES-128 por secção, partilhada via RSA; cifrar/decifrar dados de EC/RT/RH

## User Review Required

> [!IMPORTANT]
> **Argumentos de linha de comandos mudam:**
> - Servidor: `SpertaServer <port> <password-cifra> <keystore> <password-keystore>`
> - Cliente: `SpertaClient <serverAddress> <truststore> <password-truststore> <keystore> <password-keystore> <user-id> <password>`
>
> Isto é um **breaking change** na forma de lançar a aplicação.

> [!WARNING]
> **Ficheiros do servidor serão cifrados com PBE/AES-128** e terão ficheiros `.hash` associados. Dados existentes da Fase 1 não serão migráveis automaticamente – o servidor terá de ser reiniciado com dados limpos ou ser necessário um script de migração.

---

## Proposed Changes

### 1. TLS – Canais Seguros

#### [MODIFY] [SpertaServer.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/SpertaServer.java)
- Aceitar novos args: `<port> <password-cifra> <keystore> <password-keystore>`
- Substituir `ServerSocket` por `SSLServerSocket` usando a keystore fornecida
- Passar `password-cifra` ao [DataRepository](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3%C2%BAANO/2%C2%BASemestre/Seguran%C3%A7a%20e%20Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/DataRepository.java#24-537) para cifra PBE dos ficheiros

#### [MODIFY] [SpertaClient.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/client/SpertaClient.java)
- Aceitar novos args: `<serverAddress> <truststore> <password-truststore> <keystore> <password-keystore> <user-id> <password>`
- Criar `SSLSocketFactory` com a truststore (para verificar certificado do servidor)
- Carregar a keystore do utilizador (para uso futuro em cifras RSA)

#### [MODIFY] [ClientSession.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/client/ClientSession.java)
- Receber `SSLSocketFactory` e `KeyStore` do utilizador
- Substituir `new Socket(host, port)` por `sslSocketFactory.createSocket(host, port)`

---

### 2. Passwords Seguras + Certificados de Utilizadores

#### [MODIFY] [DataRepository.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/DataRepository.java)
- Formato do ficheiro `user.txt`: `<user-id>:<hash(password||salt)>:<salt>` (base64)
- [addUser()](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3%C2%BAANO/2%C2%BASemestre/Seguran%C3%A7a%20e%20Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/DataRepository.java#99-111): gerar salt aleatório de 16 bytes, calcular SHA-256 de [(password || salt)](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3%C2%BAANO/2%C2%BASemestre/Seguran%C3%A7a%20e%20Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/client/ClientCommandLoop.java#28-74), guardar em formato base64
- [getUserPassword()](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3%C2%BAANO/2%C2%BASemestre/Seguran%C3%A7a%20e%20Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/DataRepository.java#79-98) → renomear para `getUserRecord()` que devolve hash+salt
- Novo método `verifyPassword(user, password)`: recalcular o hash e comparar
- **Cifra PBE dos ficheiros do servidor** (AES-128):
  - Novo construtor que recebe `password-cifra`
  - Gerar chave PBE com `PBEWithHmacSHA256AndAES_128`
  - Salt fixo (guardado em ficheiro `pbe_salt.dat`)
  - Todos os ficheiros (casas, states, logs, users) são guardados cifrados
  - Na leitura: decifrar → verificar hash
  - Na escrita: calcular hash → cifrar → guardar ficheiro + `.hash`
- **Integridade com SHA-256**: cada ficheiro `<file>` terá `<file>.hash`
  - Na inicialização e em cada leitura: verificar hash, se falhar → `NOK-INTEGRITY` + `System.exit`

#### [MODIFY] [AuthService.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/AuthService.java)
- [authenticateOrRegister()](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3%C2%BAANO/2%C2%BASemestre/Seguran%C3%A7a%20e%20Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/AuthService.java#93-112): usar `verifyPassword()` em vez de comparar strings
- No registo de novo utilizador: pedir certificado ao cliente, guardar como `<user-id>.cert`
- Guardar certificados no diretório do servidor para distribuição futura

#### [MODIFY] [ClientSession.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/client/ClientSession.java)
- Se o servidor pedir certificado (mensagem `SEND-CERT`): extrair certificado da keystore e enviar

---

### 3. Atestação Remota Segura (Nonce + Hash)

#### [MODIFY] [AuthService.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/AuthService.java) (atestação)
- [performAttestation()](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3%C2%BAANO/2%C2%BASemestre/Seguran%C3%A7a%20e%20Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/AuthService.java#26-40):
  1. Gerar nonce aleatório de 8 bytes (`long`)
  2. Enviar nonce ao cliente
  3. Receber hash do cliente (SHA-256 de `nonce || conteúdo do JAR`)
  4. Calcular localmente o mesmo hash usando a cópia de referência do JAR
  5. Comparar → `OK-ATTEST` ou `NOK-ATTEST`
- [attestation.txt](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3%C2%BAANO/2%C2%BASemestre/Seguran%C3%A7a%20e%20Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/attestation.txt): agora guarda `SpertaClient.jar:<caminho_cópia_referência>` (sem tamanho)

#### [MODIFY] [ClientSession.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/client/ClientSession.java) (atestação)
- Receber nonce do servidor
- Calcular SHA-256 de `nonce || conteúdo do JAR` do próprio executável
- Enviar hash ao servidor

#### [MODIFY] [SpertaClient.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/client/SpertaClient.java)
- [getAttestationSize()](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3%C2%BAANO/2%C2%BASemestre/Seguran%C3%A7a%20e%20Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/client/SpertaClient.java#44-61) → substituir por `getJarBytes()` que devolve os bytes do JAR

---

### 4. Confidencialidade Fim-a-Fim (Chave de Secção)

#### [MODIFY] [ClientCommandLoop.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/client/ClientCommandLoop.java)
- Receber `KeyStore` (chave privada do utilizador) e `KeyStore` (truststore) como parâmetros
- **CREATE**: gerar chave AES-128 para cada secção, cifrar cada uma com a chave pública do owner (de si próprio), enviar ao servidor → servidor guarda como `key.<hm>.<s>.<owner>`
- **ADD**: pedir chave da secção ao servidor (cifrada com chave pública do owner) → decifrar com chave privada → pedir certificado do utilizador ao servidor (se não estiver na truststore) → cifrar chave da secção com chave pública do utilizador → enviar ao servidor → guarda como `key.<hm>.<s>.<user>`
- **EC**: enviar pedido de EC → receber chave da secção cifrada com chave pública do user → decifrar → cifrar o valor `<int>` com a chave da secção AES → enviar dados cifrados
- **RT**: receber dados cifrados + chave(s) da secção cifrada(s) → decifrar as chaves → decifrar os dados
- **RH**: igual ao RT mas para o histórico

#### [MODIFY] [CommandService.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/CommandService.java)
- **CREATE**: receber e guardar chaves de secção cifradas do owner
- **ADD**: enviar chave de secção ao owner, receber chave de secção cifrada para o novo utilizador e guardar
- **EC**: enviar chave de secção cifrada ao utilizador → receber valor cifrado → guardar (cifrado) no ficheiro
- **RT/RH**: enviar dados cifrados + chave(s) de secção cifrada(s) com a chave pública do utilizador

#### [MODIFY] [DataRepository.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/DataRepository.java)
- Novos métodos para guardar/ler chaves de secção: `storeEncryptedSectionKey()`, `getEncryptedSectionKey()`
- Novos métodos para guardar/ler certificados de utilizadores: `storeCertificate()`, `getCertificate()`
- Directório para chaves: `data/server/keys/`
- Directório para certificados: `data/server/certs/`

#### [NEW] [CryptoUtils.java](file:///c:/Users/rodri/Desktop/Rodrigo/Faculdade/3ºANO/2ºSemestre/Segurança e Confiabilidade/SegC-grupo02--proj1-fase1/src/sperta/server/CryptoUtils.java)
- Classe utilitária partilhada (ou duas, uma para server e outra para client)
- Métodos: `hashSHA256()`, `generateSalt()`, `encryptAES()`, `decryptAES()`, `encryptRSA()`, `decryptRSA()`, `generatePBEKey()`, `computeFileHash()`, `verifyFileHash()`

---

### 5. Preparação de Chaves e Certificados

#### [NEW] Scripts ou instruções para gerar keystores/truststores

Para testar, será necessário gerar:
1. **Keystore do servidor**: `keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -keystore server.keystore -storepass <password>`
2. **Exportar certificado do servidor**: `keytool -exportcert -alias server -keystore server.keystore -file server.cert`
3. **Truststore do cliente**: `keytool -importcert -alias server -file server.cert -keystore client.truststore`
4. **Keystore por cada utilizador**: `keytool -genkeypair -alias <user> -keyalg RSA -keysize 2048 -keystore <user>.keystore`

---

## Verificação

### Testes Manuais (este projeto não tem testes unitários automatizados)

1. **TLS**: Iniciar servidor com keystore → conectar cliente com truststore → verificar que a ligação é estabelecida via TLS (sem erros de handshake)
2. **Registo de utilizador**: Registar um novo utilizador → verificar que `user.txt` contém `user:hash:salt` em vez de password em claro → verificar que o servidor pede e guarda o certificado
3. **Atestação**: Iniciar cliente → verificar que recebe nonce, calcula hash, e atestação passa (`ATTESTATION OK`). Modificar o JAR do cliente → verificar que atestação falha
4. **Integridade**: Modificar manualmente um ficheiro de dados do servidor → reiniciar → verificar mensagem `NOK-INTEGRITY` e exit
5. **Confidencialidade fim-a-fim**:
   - CREATE casa → verificar que chaves de secção são geradas e guardadas
   - ADD utilizador → verificar que a chave é re-cifrada para o novo utilizador
   - EC com valor → verificar que dados são guardados cifrados no servidor
   - RT/RH → verificar que dados são decifrados corretamente no cliente
   - Utilizador sem permissão → verificar que recebe `NOPERM`
6. **PBE**: Verificar que ficheiros do servidor são cifrados em disco. Iniciar com password errada → verificar `NOK-INTEGRITY` e exit
