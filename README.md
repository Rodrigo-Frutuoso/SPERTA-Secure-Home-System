# 🏠 SPERTA

> **Segurança e Confiabilidade 2025/26 — Projeto 1 (Fase 2)**  
> Sistema de gestão de dispositivos domésticos com autenticação, atestação e controlo de acesso por utilizador.

---

## 🔧 Dependências

| Ferramenta     | Versão Mínima |
|----------------|---------------|
| Java JDK       | 21+           |
| Apache Maven   | 3.9+          |

> Compatível com **Windows**, **Linux** e **macOS**.

---

## ⚙️ Compilação

```bash
mvn clean package
```

O Maven irá:
- Compilar cliente e servidor para `out-client/` e `out-server/`
- Gerar os JARs em `dist/`:
  - `dist/SpertaClient.jar`
  - `dist/SpertaServer.jar`
- Atualizar `src/sperta/server/attestation.txt` com o caminho da cópia de referência do `SpertaClient.jar`

---

## ▶️ Execução

### Servidor

```bash
java -jar dist/SpertaServer.jar <port> <cipher-password> <keystore> <keystore-password>
```

> Se não for indicado um porto, o servidor usa o porto por omissão **22345**.
> O `cipher-password` é a palavra-passe usada para cifrar os ficheiros do servidor.

**Exemplo:**
```bash
java -jar dist/SpertaServer.jar 22345 secret src/sperta/certs/server.keystore server-password
```

### Cliente *(noutro terminal)*

```bash
java -jar dist/SpertaClient.jar <serverAddress> <truststore> <trust-pass> <keystore> <key-pass> <user> <password>
```

> Regra de sessao: o servidor permite apenas uma sessao ativa por utilizador.
> Se o mesmo user tentar autenticar num segundo cliente em simultaneo,
> a segunda ligacao e rejeitada com `USER-ALREADY-CONNECTED`.

**Exemplo (mesmo PC):**

```bash
java -jar dist/SpertaClient.jar localhost:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password rodrigo frutas

```

---

## 🌐 Execução em PCs Diferentes (Rede Local)

O servidor e o cliente podem correr em máquinas distintas na mesma rede.
Os ficheiros `dist/SpertaServer.jar` e `dist/SpertaClient.jar` já vêm **pré-compilados** no projeto — **não é necessário compilar**.

> [!IMPORTANT]
> **Atestação:** o servidor valida o hash SHA-256 de `SpertaClient.jar`.
> Ambos os PCs devem usar o **mesmo** `SpertaClient.jar` (o que vem na pasta `dist/`).
> **Nunca recompilar separadamente** em cada PC, pois os JARs podem diferir e a atestação falha.

### Passo a passo

1. **Copiar a pasta do projeto** (ou apenas a pasta `dist/`) para ambos os PCs (pen USB, partilha de rede, etc.).

2. **No PC do servidor** — iniciar o servidor:
   ```bash
   java -jar dist/SpertaServer.jar 22345 secret src/sperta/certs/server.keystore server-password
   ```

3. **No PC do cliente** — usar o IP do servidor (ex: `192.168.1.100`):
   ```bash
   java -jar dist/SpertaClient.jar 192.168.1.100:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password <user> <password>
   ```

> [!NOTE]
> Para descobrir o IP do PC servidor, executar `ipconfig` (Windows) ou `ip a` (Linux/macOS).

### Requisitos de rede

- Ambos os PCs devem estar na **mesma rede local** (Wi-Fi ou cabo).
- A **firewall** do PC servidor deve permitir ligações na porta utilizada (ex: 12345).
  No Windows, ao iniciar o servidor pela primeira vez, clicar em **"Permitir acesso"** na janela da firewall.

---

## 📁 Estrutura do Projeto

```
.
├── dist/
│   ├── SpertaClient.jar
│   └── SpertaServer.jar
│
├── out-client/
│   ├── ClientCommandLoop.class
│   ├── ClientSession.class
│   └── SpertaClient.class
│
├── out-server/
│   ├── AuthService.class
│   ├── ClientSessionHandler.class
│   ├── CommandService.class
│   ├── DataRepository.class
│   └── SpertaServer.class
│
├── src/sperta/
│   ├── certs/                        # Infraestrutura criptográfica
│   │   ├── client.keystore
│   │   ├── client.truststore
│   │   ├── server.cer
│   │   └── server.keystore
│   │
│   ├── client/
│   │   ├── ClientCommandLoop.java    # Parser, menu e comandos
│   │   ├── ClientSession.java        # Socket, atestação e autenticação
│   │   └── SpertaClient.java         # Ponto de entrada do cliente
│   │
│   ├── data/                         # Gerado em runtime
│   │   ├── server/                   # Persistência interna do servidor
│   │   │   ├── certs/                # Certificados de utilizadores
│   │   │   │   └── <user>.cer
│   │   │   ├── houses/
│   │   │   │   └── <casa>.txt        # Permissões e dispositivos por casa
│   │   │   ├── logs/
│   │   │   │   └── <casa>/<disp>.csv # Histórico por dispositivo
│   │   │   ├── states/
│   │   │   │   └── <casa>.txt        # Último estado de cada dispositivo
│   │   │   ├── all_houses.txt        # Casas: casa|owner|contadores
│   │   │   └── user.txt              # Utilizadores: formato hash+salt
│   │   └── client/
│   │       └── downloads/            # Ficheiros recebidos nos comandos RT/RH
│   │
│   └── server/
│       ├── AuthService.java          # Atestação e autenticação
│       ├── ClientSessionHandler.java # Thread por cliente
│       ├── CommandService.java       # Lógica dos comandos (CREATE/ADD/RD/EC/RT/RH)
│       ├── CryptoUtils.java          # Funções de hash, base64 e crypto
│       ├── DataRepository.java       # Persistência e ficheiros
│       ├── SpertaServer.java         # Ponto de entrada do servidor
│       └── attestation.txt           # Path da cópia de referência do JAR do cliente
│
├── pom.xml
└── README.md
```

---

## 📟 Comandos Disponíveis

| Comando  | Descrição                                      |
|----------|------------------------------------------------|
| `CREATE` | Criar uma nova casa                            |
| `ADD`    | Adicionar dispositivo ou utilizador a uma casa |
| `RD`     | Remover dispositivo                            |
| `EC`     | Enviar comando a um dispositivo                |
| `RT`     | Obter estado atual de todos os dispositivos    |
| `RH`     | Obter histórico de comandos de um dispositivo  |

---

## 🧹 Limpeza

```bash
mvn clean
```

Remove os seguintes artefactos gerados:

- `out-client/`
- `out-server/`
- `dist/`
- `src/sperta/data/`
- `src/sperta/server/attestation.txt`

---

## ⚡ Referência Rápida

```bash
# Compilar
mvn clean package

# Iniciar servidor
java -jar dist/SpertaServer.jar 22345 secret src/sperta/certs/server.keystore server-password

# Iniciar cliente
java -jar dist/SpertaClient.jar localhost:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password <user> <password>

# Limpar
mvn clean
```

---

## 🔑 Passwords dos Utilizadores

- `rodrigo`: `frutas`
- `tiago`: `leite1`
- `simao`: `alexandre`
