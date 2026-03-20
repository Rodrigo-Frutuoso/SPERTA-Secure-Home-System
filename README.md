# 🏠 SPERTA

> **Segurança e Confiabilidade 2025/26 — Projeto 1 (Fase 1)**  
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
- Atualizar `src/sperta/server/attestation.txt` com o tamanho do `SpertaClient.jar`

---

## ▶️ Execução

### Servidor

```bash
java -jar dist/SpertaServer.jar 12345
```

> Se não for indicado um porto, o servidor usa o porto por omissão **22345**.

### Cliente *(noutro terminal)*

```bash
java -jar dist/SpertaClient.jar localhost:12345 <user-id> <password>
```

**Exemplo:**

```bash
java -jar dist/SpertaClient.jar localhost:12345 rodrigo frutas
```

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
│   ├── client/
│   │   ├── ClientCommandLoop.java    # Parser, menu e comandos
│   │   ├── ClientSession.java        # Socket, atestação e autenticação
│   │   └── SpertaClient.java         # Ponto de entrada do cliente
│   │
│   ├── data/                         # Gerado em runtime
│   │   ├── server/                   # Persistência interna do servidor
│   │   │   ├── houses/
│   │   │   │   └── <casa>.txt        # Permissões e dispositivos por casa
│   │   │   ├── logs/
│   │   │   │   └── <casa>/<disp>.csv # Histórico por dispositivo
│   │   │   ├── states/
│   │   │   │   └── <casa>.txt        # Último estado de cada dispositivo
│   │   │   ├── all_houses.txt        # Casas: casa|owner|contadores
│   │   │   └── user.txt              # Utilizadores: user:password
│   │   └── client/
│   │       └── downloads/            # Ficheiros recebidos nos comandos RT/RH
│   │
│   └── server/
│       ├── AuthService.java          # Atestação e autenticação
│       ├── ClientSessionHandler.java # Thread por cliente
│       ├── CommandService.java       # Lógica dos comandos (CREATE/ADD/RD/EC/RT/RH)
│       ├── DataRepository.java       # Persistência e ficheiros
│       ├── SpertaServer.java         # Ponto de entrada do servidor
│       └── attestation.txt           # Nome e tamanho esperado do JAR do cliente
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
java -jar dist/SpertaServer.jar 12345

# Iniciar cliente
java -jar dist/SpertaClient.jar localhost:12345 <user> <password>

# Limpar
mvn clean
```
