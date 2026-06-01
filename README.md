# 🏠 SPERTA

> **Security and Dependability 2025/26 — Project 1**
> Home device management system with authentication, attestation and per-user access control.

---

## 🔧 Dependencies

| Tool          | Minimum Version |
|---------------|------------------|
| Java JDK      | 21+              |
| Apache Maven  | 3.9+             |

Compatible with **Windows**, **Linux** and **macOS**.

---

## ⚙️ Build

Run:

```bash
mvn clean package
```

Maven will:
- Compile client and server into `out-client/` and `out-server/`
- Produce JARs in `dist/`:
  - `dist/SpertaClient.jar`
  - `dist/SpertaServer.jar`
- Update `src/sperta/server/attestation.txt` with the path to the reference copy of `SpertaClient.jar`

---

## ▶️ Run

### Server

```bash
java -jar dist/SpertaServer.jar <port> <cipher-password> <keystore> <keystore-password>
```

If no port is provided, the server uses the default port **22345**.
The `cipher-password` is used to encrypt the server data files.

Example:

```bash
java -jar dist/SpertaServer.jar 22345 secret src/sperta/certs/server.keystore server-password
```

### Client (in another terminal)

```bash
java -jar dist/SpertaClient.jar <serverAddress> <truststore> <trust-pass> <keystore> <key-pass> <user> <password>
```

Session rule: the server allows only one active session per user.
If the same user attempts to authenticate a second simultaneous client,
the second connection is rejected with `USER-ALREADY-CONNECTED`.

Example (same machine):

```bash
java -jar dist/SpertaClient.jar localhost:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password rodrigo frutas
```

---

## 🌐 Running on different machines (Local Network)
The server and the client can run on different machines within the same local network.
The pre-built artifacts `dist/SpertaServer.jar` and `dist/SpertaClient.jar` are included in the project — rebuilding is not required.

> [!IMPORTANT]
> **Attestation:** the server validates the SHA-256 hash of `SpertaClient.jar`.
> Both machines must use the **same** `SpertaClient.jar` (the one from the `dist/` folder).
> **Do not recompile separately** on each machine — otherwise the JARs may differ and the attestation will fail.

### Step-by-step

1. **Copy the project folder** (or at least the `dist/` folder) to both machines (USB drive, network share, etc.).

2. **On the server machine** — start the server:
  ```bash
  java -jar dist/SpertaServer.jar 22345 secret src/sperta/certs/server.keystore server-password
  ```

3. **On the client machine** — use the server IP address (e.g. `192.168.1.100`):
  ```bash
  java -jar dist/SpertaClient.jar 192.168.1.100:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password <user> <password>
  ```

> [!NOTE]
> To find the server IP address run `ipconfig` (Windows) or `ip a` (Linux/macOS).

### Network requirements

- Both machines must be on the **same local network** (Wi‑Fi or wired).
- The server machine's **firewall** must allow incoming connections on the chosen port (e.g. 22345).
  On Windows, when starting the server for the first time, click **"Allow access"** in the firewall prompt.

---

## 📁 Project Structure

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
│   ├── certs/                        # Cryptographic infrastructure
│   │   ├── client.keystore
│   │   ├── client.truststore
│   │   ├── server.cer
│   │   └── server.keystore
│   │
│   ├── client/
│   │   ├── ClientCommandLoop.java    # Parser, menu and commands
│   │   ├── ClientSession.java        # Socket, attestation and authentication
│   │   └── SpertaClient.java         # Client entry point
│   │
│   ├── data/                         # Generated at runtime
│   │   ├── server/                   # Server internal persistence
│   │   │   ├── certs/                # User certificates
│   │   │   │   └── <user>.cer
│   │   │   ├── houses/
│   │   │   │   └── <house>.txt       # Permissions and devices per house
│   │   │   ├── logs/
│   │   │   │   └── <house>/<dev>.csv # Device command history
│   │   │   ├── states/
│   │   │   │   └── <house>.txt       # Last known state of each device
│   │   │   ├── all_houses.txt        # Houses: house|owner|counters
│   │   │   └── user.txt              # Users: stored as hash+salt
│   │   └── client/
│   │       └── downloads/            # Files received via RT/RH commands
│   │
│   └── server/
│       ├── AuthService.java          # Attestation and authentication
│       ├── ClientSessionHandler.java # Thread per client
│       ├── CommandService.java       # Command logic (CREATE/ADD/RD/EC/RT/RH)
│       ├── CryptoUtils.java          # Hashing, base64 and crypto helpers
│       ├── DataRepository.java       # Persistence and file handling
│       ├── SpertaServer.java         # Server entry point
│       └── attestation.txt           # Path to the reference copy of the client JAR
│
├── pom.xml
└── README.md
```

---

## 📟 Available Commands

| Command  | Description                                    |
|----------|------------------------------------------------|
| `CREATE` | Create a new house                             |
| `ADD`    | Add a device or user to a house                |
| `RD`     | Register a device                              |
| `EC`     | Send a command to a device                     |
| `RT`     | Retrieve current state of all devices          |
| `RH`     | Retrieve command history for a device          |

---

## 🧹 Cleanup

```bash
mvn clean
```

Removes the following generated artifacts:

- `out-client/`
- `out-server/`
- `dist/`
- `src/sperta/data/`
- `src/sperta/server/attestation.txt`

---

## ⚡ Quick Reference

```bash
# Build
mvn clean package

# Start server
java -jar dist/SpertaServer.jar 22345 secret src/sperta/certs/server.keystore server-password

# Start client
java -jar dist/SpertaClient.jar localhost:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password <user> <password>

# Clean
mvn clean
```

---

## 🔑 User Passwords (example)

- `rodrigo`: `frutas`
- `tiago`: `leite1`
- `simao`: `alexandre`
