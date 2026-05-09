# 🔒 Projeto 2 — Segurança e Confiabilidade 2025/26

> **Objetivo**: Configurar a máquina **SperServ** de forma segura usando **iptables** (firewall) e **snort** (IDS) no ambiente Mininet-GUI da VM fornecida.
>
> **Entrega**: 29 de maio (23:59) — ficheiro `SegC-grupo02-proj2.zip` com `iptables.pdf` e `snort.pdf` (máx. 3 páginas cada).

---

## 1. Preparação do Ambiente

### 1.1 Setup da VM

| Passo | Ação |
|-------|------|
| 1 | Importar `mininetx-GUI-VM Seg.ova` no VirtualBox/VMware |
| 2 | Login: `mininet` / `mininet` |
| 3 | Abrir terminal → executar `mininet_gui` |
| 4 | No Firefox, abrir o endereço fornecido |
| 5 | `File → Open Topology` → carregar `SegC.json` |
| 6 | `Run → Start Network` |
| 7 | Novo terminal da VM → executar `./config_running_network.sh` (em `/home/mininet/mininet-gui/mininet-gui-backend`) |

### 1.2 Deploy do Sperta na VM

1. Copiar o projeto (ou apenas `dist/` + `src/sperta/certs/` + `src/sperta/server/attestation.txt`) para `/home/mininet/mininet-gui/mininet-gui-backend` na VM
2. Confirmar que Java 21+ está disponível na VM
3. Testar lançamento do servidor na máquina **SperServ** (webshell):
   ```bash
   java -jar dist/SpertaServer.jar 22345 secret src/sperta/certs/server.keystore server-password
   ```
4. Testar ligação de um cliente a partir de **SperCli1** ou **SperCli2**:
   ```bash
   java -jar dist/SpertaClient.jar <IP_SperServ>:22345 src/sperta/certs/client.truststore truststore-password src/sperta/certs/client.keystore client-password rodrigo frutas
   ```

> [!IMPORTANT]
> **Antes de aplicar qualquer regra iptables**, confirmar que servidor e clientes comunicam corretamente **sem** firewall. Isto valida o setup base.

### 1.3 Levantamento da Topologia de Rede

Antes de escrever regras, é fundamental descobrir os IPs de cada máquina na topologia `SegC.json`. Abrir a webshell de cada máquina e executar `ip a` ou `ifconfig`.

| Máquina | IP (a preencher) | Sub-rede |
|---------|-------------------|----------|
| **SperServ** | `10.0.0.2` | `10.0.0.0/24` |
| **SperCli1** | `10.0.1.3` | `10.0.1.0/24` (sub-rede dos clientes) |
| **SperCli2** | `10.0.1.4` | `10.0.1.0/24` (mesma sub-rede de SperCli1) |
| **SperAdm** | `10.0.0.3` | `10.0.0.0/24` (mesma sub-rede de SperServ) |
| **CondAdm** | `10.0.3.2` | `10.0.3.0/24` |
| **CondServ** | `10.0.3.3` | `10.0.3.0/24` (mesma sub-rede de CondAdm) |
| **Broker** | `10.0.2.4` | `10.0.2.0/25` |
| **Outsider** | `10.10.0.2` | `10.10.0.0/24` (fora da rede interna — atacante) |

> [!TIP]
> Nas regras abaixo, substituir as variáveis (ex: `$CLIENTES_SUBNET`) pelos IPs reais descobertos.

---

## 2. Parte I — iptables (Firewall)

### 2.1 Estratégia: Default Policy DROP

Adotar **DROP** como política por omissão em INPUT, OUTPUT e FORWARD.

**Justificação**: A abordagem *default deny* é a mais segura — bloqueia todo o tráfego por omissão e permite apenas o estritamente necessário. Qualquer serviço não explicitamente autorizado é automaticamente rejeitado.

### 2.2 Regras iptables (Script)

Criar um ficheiro `firewall.sh` a executar na webshell de **SperServ**:

```bash
#!/bin/bash
# ============================================================
# Firewall iptables — Máquina SperServ
# Segurança e Confiabilidade 2025/26 — Projeto 2 — Grupo 02
# ============================================================

# ──────────────── VARIÁVEIS (PREENCHER) ─────────────────────
SPERSERV_IP="10.0.0.2"             # IP de SperServ
SPERSERV_SUBNET="10.0.0.0/24"     # Sub-rede de SperServ
CLIENTES_SUBNET="10.0.1.0/24"     # Sub-rede de SperCli1/SperCli2
SPERADM_IP="10.0.0.3"             # IP de SperAdm
CONDADM_IP="10.0.3.2"             # IP de CondAdm
CONDSERV_IP="10.0.3.3"            # IP de CondServ
BROKER_IP="10.0.2.4"              # IP de Broker
SPERTA_PORT=22345                # Porto do SpertaServer (SSL)
# ──────────────────────────────────────────────────────────────

# 0. Limpar regras existentes
sudo iptables -F
sudo iptables -X

# 1. Default Policy: DROP
sudo iptables -P INPUT DROP
sudo iptables -P OUTPUT DROP
sudo iptables -P FORWARD DROP

# 2. Permitir tráfego de loopback (localhost)
sudo iptables -A INPUT  -i lo -j ACCEPT
sudo iptables -A OUTPUT -o lo -j ACCEPT

# 3. Permitir tráfego de conexões já estabelecidas
sudo iptables -A INPUT  -m state --state ESTABLISHED,RELATED -j ACCEPT
sudo iptables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# ═══════════════════ SERVIÇOS SUPORTADOS (INPUT) ═══════════════════

# 4. Ping (ICMP echo-request) — aceitar apenas de:
#    - SperAdm
#    - CondAdm
#    - Sub-rede dos clientes (SperCli1, SperCli2)
sudo iptables -A INPUT -p icmp --icmp-type echo-request -s $SPERADM_IP -j ACCEPT
sudo iptables -A INPUT -p icmp --icmp-type echo-request -s $CONDADM_IP -j ACCEPT
sudo iptables -A INPUT -p icmp --icmp-type echo-request -s $CLIENTES_SUBNET -j ACCEPT

# 5. SSH (porta 22) — aceitar apenas de:
#    - Broker
#    - Sub-rede local de SperServ
sudo iptables -A INPUT -p tcp --dport 22 -s $BROKER_IP -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -s $SPERSERV_SUBNET -j ACCEPT

# 6. SpertaServer (porta 22345 TCP/SSL) — aceitar apenas da sub-rede dos clientes
sudo iptables -A INPUT -p tcp --dport $SPERTA_PORT -s $CLIENTES_SUBNET -j ACCEPT

# ═══════════════════ SERVIÇOS UTILIZADOS (OUTPUT) ═══════════════════

# 7. Ping (ICMP echo-request) — permitir para:
#    - Sub-rede local de SperServ
#    - Broker
#    Limitado a 4 pings/segundo
sudo iptables -A OUTPUT -p icmp --icmp-type echo-request -d $SPERSERV_SUBNET \
    -m limit --limit 4/second --limit-burst 4 -j ACCEPT
sudo iptables -A OUTPUT -p icmp --icmp-type echo-request -d $BROKER_IP \
    -m limit --limit 4/second --limit-burst 4 -j ACCEPT

# 8. SSH (porta 22 destino) — permitir apenas para CondServ
sudo iptables -A OUTPUT -p tcp --dport 22 -d $CONDSERV_IP -j ACCEPT

# ═══════════════════ LOG (Opcional — para debug) ═══════════════════
# sudo iptables -A INPUT  -j LOG --log-prefix "[FW-INPUT-DROP] "
# sudo iptables -A OUTPUT -j LOG --log-prefix "[FW-OUTPUT-DROP] "
```

### 2.3 Resumo das Regras

| # | Chain | Protocolo | Direção | Origem/Destino | Ação |
|---|-------|-----------|---------|----------------|------|
| 2 | INPUT/OUTPUT | all | loopback | localhost | ACCEPT |
| 3 | INPUT/OUTPUT | all | — | ESTABLISHED,RELATED | ACCEPT |
| 4 | INPUT | ICMP echo-req | ← | SperAdm, CondAdm, sub-rede clientes | ACCEPT |
| 5 | INPUT | TCP/22 | ← | Broker, sub-rede SperServ | ACCEPT |
| 6 | INPUT | TCP/22345 | ← | sub-rede clientes | ACCEPT |
| 7 | OUTPUT | ICMP echo-req | → | sub-rede SperServ, Broker (4/s) | ACCEPT |
| 8 | OUTPUT | TCP/22 | → | CondServ | ACCEPT |
| * | INPUT/OUTPUT | all | — | qualquer outro | **DROP** (default) |

### 2.4 Plano de Testes — iptables

| Teste | Máquina origem | Comando | Resultado esperado |
|-------|---------------|---------|-------------------|
| T1 | SperCli1 | `ping <SperServ_IP>` | ✅ Sucesso |
| T2 | SperAdm | `ping <SperServ_IP>` | ✅ Sucesso |
| T3 | CondAdm | `ping <SperServ_IP>` | ✅ Sucesso |
| T4 | Outsider | `ping <SperServ_IP>` | ❌ Sem resposta |
| T5 | Broker | `ping <SperServ_IP>` | ❌ Sem resposta |
| T6 | SperCli1 | Conectar SpertaClient ao SpertaServer | ✅ Sucesso |
| T7 | Outsider | Conectar ao porto 22345 | ❌ Bloqueado |
| T8 | Broker | `ssh <SperServ_IP>` | ✅ Sucesso (iniciar sshd primeiro) |
| T9 | SperServ subnet | `ssh <SperServ_IP>` | ✅ Sucesso |
| T10 | Outsider | `ssh <SperServ_IP>` | ❌ Bloqueado |
| T11 | SperServ | `ping <máquina na sub-rede local>` | ✅ Sucesso (≤4/s) |
| T12 | SperServ | `ping <Broker_IP>` | ✅ Sucesso (≤4/s) |
| T13 | SperServ | `ping <Outsider_IP>` | ❌ Bloqueado |
| T14 | SperServ | `ssh <CondServ_IP>` | ✅ Sucesso |
| T15 | SperServ | `ssh <SperCli1_IP>` | ❌ Bloqueado |

> [!TIP]
> Para cada teste, tirar screenshot com o comando e resultado para incluir no relatório. Usar `sudo iptables -L -v -n` para mostrar contadores de pacotes.

---

## 3. Parte II — snort (IDS)

### 3.1 Regras Snort

Criar ficheiro `sperta.rules` com as seguintes regras:

```
# ================================================================
# Regras Snort — SperServ
# Segurança e Confiabilidade 2025/26 — Projeto 2 — Grupo 02
# ================================================================

# ────── Regra 1: Port Scanning ──────
# Alerta quando a mesma máquina faz 20+ ligações TCP a portos < 1024 
# em 60 segundos. Apenas 1 alerta por minuto.
alert tcp any any -> $HOME_NET 1:1023 (msg:"[ALERTA] Possível varrimento de portos - 20+ ligações TCP a portos < 1024 em 60s"; flags:S; threshold:type threshold, track by_src, count 20, seconds 60; sid:1000001; rev:1;)

# ────── Regra 2: Brute-Force no SpertaServer ──────
# Alerta a cada 3 ligações da mesma máquina ao porto 22345 em 20 segundos.
alert tcp any any -> $HOME_NET 22345 (msg:"[ALERTA] Possível tentativa de brute-force no SpertaServer - 3 ligações em 20s"; flags:S; threshold:type threshold, track by_src, count 3, seconds 20; sid:1000002; rev:1;)

# ────── Regra 3: Acesso externo ao SpertaServer ──────
# Alerta quando uma máquina FORA da sub-rede dos clientes tenta ligar ao porto 22345.
# Nota: substituir ?.?.?.0/24 pela sub-rede real dos clientes.
alert tcp !10.0.1.0/24 any -> $HOME_NET 22345 (msg:"[ALERTA] Tentativa de ligação ao SpertaServer de fora da sub-rede autorizada"; flags:S; sid:1000003; rev:1;)

# ────── Regra 4: ICMP Flood (DDoS) ──────
# Alerta quando SperServ recebe mais de 4 pings por segundo (qualquer origem).
# Apenas 1 alerta por segundo.
alert icmp any any -> $HOME_NET any (msg:"[ALERTA] Possível ICMP flood - mais de 4 pings/segundo"; itype:8; threshold:type threshold, track by_dst, count 5, seconds 1; sid:1000004; rev:1;)
```

### 3.2 Detalhes de Cada Regra

#### Regra 1 — Port Scanning
- **Deteção**: `threshold:type threshold, track by_src, count 20, seconds 60`
- **Significado**: Gera **1 alerta** quando a mesma origem faz 20+ SYNs para portos <1024 em 60s
- **Flag `S`**: Apenas pacotes SYN (início de ligação TCP)

#### Regra 2 — Brute-Force
- **Deteção**: `threshold:type threshold, track by_src, count 3, seconds 20`
- **Significado**: Gera alerta **a cada 3 ligações** da mesma origem ao porto 22345 em 20s
- **Nota**: Usa `type threshold` (não `type limit`) para gerar alertas repetidos

#### Regra 3 — Acesso Não Autorizado
- **Deteção**: `!CLIENTES_SUBNET` como origem
- **Significado**: Qualquer SYN ao porto 22345 vindo de fora da sub-rede autorizada gera alerta
- **Nota**: Complementa as regras iptables (defesa em profundidade)

#### Regra 4 — ICMP Flood
- **Deteção**: `threshold:type threshold, track by_dst, count 5, seconds 1`
- **Significado**: Gera **1 alerta/segundo** quando >4 pings chegam (de qualquer origem combinada)
- **`track by_dst`**: Conta pings de TODAS as origens (DDoS pode vir de múltiplas máquinas)

### 3.3 Invocação do Snort

```bash
# Na webshell de SperServ:
sudo snort -A console -q -c /path/to/sperta.rules -i <INTERFACE>
```

- `-A console`: Alertas na consola
- `-q`: Modo silencioso (menos output)
- `-c`: Ficheiro de regras
- `-i`: Interface de rede → usar `SperServ-eth0`

> [!IMPORTANT]
> Pode ser necessário criar um `snort.conf` mínimo ou usar `-c` apontando diretamente para o ficheiro de regras. Se o snort exigir `snort.conf`, incluir a linha `include sperta.rules` no ficheiro de configuração.

### 3.4 Plano de Testes — snort

| Teste | Ação | Máquina origem | Comando | Alerta esperado |
|-------|------|---------------|---------|-----------------|
| S1 | Port Scan | SperCli1 ou Outsider | `nmap <SperServ_IP>` | ✅ Regra 1 — "varrimento de portos" |
| S2 | Brute-Force | SperCli1 | Executar SpertaClient 3x rapidamente (em <20s) | ✅ Regra 2 — "brute-force" |
| S3 | Acesso externo | Outsider | Tentar ligar ao porto 22345 | ✅ Regra 3 — "fora da sub-rede" |
| S4 | ICMP Flood | Qualquer | `ping -f <SperServ_IP>` ou `ping -i 0.1 <SperServ_IP>` | ✅ Regra 4 — "ICMP flood" |

> [!TIP]
> Para o teste S2, podem usar um script bash para automatizar:
> ```bash
> for i in 1 2 3; do
>   java -jar dist/SpertaClient.jar <IP>:22345 ... &
> done
> ```

---

## 4. Relatórios (Entregáveis)

### 4.1 `iptables.pdf` (máx. 3 páginas)

| Secção | Conteúdo |
|--------|----------|
| **1. Política por omissão** | Política DROP com justificação (*default deny*) |
| **2. Regras iptables** | Script completo com explicação de cada regra |
| **3. Testes e validação** | Screenshots dos testes T1-T15 com observações |

### 4.2 `snort.pdf` (máx. 3 páginas)

| Secção | Conteúdo |
|--------|----------|
| **1. Regras definidas** | Cada regra com explicação dos parâmetros |
| **2. Invocação do snort** | Comando completo utilizado |
| **3. Testes e validação** | Screenshots dos alertas gerados nos testes S1-S4 |

---

## 5. Distribuição de Tarefas

> [!NOTE]
> Sugestão de distribuição para 3 membros do grupo. Ajustar conforme necessário.

### Fase 1 — Setup (todos juntos ou 1 pessoa)
- [ ] Importar e configurar a VM
- [ ] Copiar o projeto Sperta para a VM
- [ ] Levantar a topologia `SegC.json`
- [ ] Executar `./config_running_network.sh`
- [ ] Descobrir e registar todos os IPs da topologia (preencher tabela da secção 1.3)
- [ ] Testar SpertaServer + SpertaClient sem firewall

### Fase 2 — iptables (Membro A + Membro B)
- [ ] Escrever o script `firewall.sh` com IPs reais
- [ ] Aplicar as regras na máquina SperServ
- [ ] Executar todos os testes T1-T15
- [ ] Tirar screenshots de cada teste
- [ ] Redigir `iptables.pdf`

### Fase 3 — snort (Membro B + Membro C)
- [ ] Escrever o ficheiro `sperta.rules` com IPs reais
- [ ] Configurar e lançar o snort na máquina SperServ
- [ ] Executar todos os testes S1-S4
- [ ] Tirar screenshots dos alertas
- [ ] Redigir `snort.pdf`

### Fase 4 — Finalização (todos)
- [ ] Revisão cruzada dos relatórios
- [ ] Criar `SegC-grupo02-proj2.zip` com `iptables.pdf` e `snort.pdf`
- [ ] Submeter no Moodle
- [ ] Preencher formulário de contribuições (até 30 de maio)

---

## 6. Timeline Sugerida

| Período | Tarefa |
|---------|--------|
| **8-12 maio** | Setup da VM, descobrir IPs, testar Sperta |
| **12-18 maio** | Implementar e testar regras iptables |
| **18-24 maio** | Implementar e testar regras snort |
| **24-28 maio** | Redigir relatórios, revisão |
| **29 maio** | Submissão final |
| **30 maio** | Preencher formulário contribuições |

---

## Open Questions

> [!IMPORTANT]
> **IPs da topologia**: Os IPs exatos de cada máquina na topologia `SegC.json` precisam ser descobertos dentro da VM. É o primeiro passo crítico — sem eles, não é possível escrever as regras finais.

> [!WARNING]
> **Java na VM**: Verificar se a VM tem Java 21+ instalado. Se não tiver, será necessário instalar (`sudo apt install openjdk-21-jdk` ou similar) antes de poder correr o SpertaServer/Client.

> [!NOTE]
> **SSH na VM**: O serviço SSH não está a correr por omissão nas máquinas simuladas. Para testar regras SSH, é necessário iniciar o sshd primeiro: `sudo /usr/sbin/sshd -D` na máquina destino.
