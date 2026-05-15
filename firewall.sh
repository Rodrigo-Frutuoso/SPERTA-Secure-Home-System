#!/bin/bash
# Firewall seguro (aplica regras essenciais; NÃO altera policies por omissão)
# Preenche as variáveis abaixo com os IPs reais antes de usar em produção.

SPERSERV_IP="10.0.0.2"
SPERSERV_SUBNET="10.0.0.0/24"
CLIENTES_SUBNET="10.0.1.0/24"
SPERADM_IP="10.0.0.3"
CONDADM_IP="10.0.3.2"
CONDSERV_IP="10.0.3.3"
BROKER_IP="10.0.2.4"
SPERTA_PORT=22345

set -euo pipefail

# Limpar regras antigas (apenas chains customizadas e counters)
sudo iptables -F
sudo iptables -X

# Regras essenciais (loopback e conexões já estabelecidas)
sudo iptables -A INPUT  -i lo -j ACCEPT
sudo iptables -A OUTPUT -o lo -j ACCEPT
sudo iptables -A INPUT  -m state --state ESTABLISHED,RELATED -j ACCEPT
sudo iptables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# INPUT: ICMP (ping) de SperAdm, CondAdm e sub-rede clientes
sudo iptables -A INPUT -p icmp --icmp-type echo-request -s "$SPERADM_IP" -j ACCEPT
sudo iptables -A INPUT -p icmp --icmp-type echo-request -s "$CONDADM_IP" -j ACCEPT
sudo iptables -A INPUT -p icmp --icmp-type echo-request -s "$CLIENTES_SUBNET" -j ACCEPT

# INPUT: SSH (porta 22) apenas de Broker e da sub-rede local de SperServ
sudo iptables -A INPUT -p tcp --dport 22 -s "$BROKER_IP" -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 22 -s "$SPERSERV_SUBNET" -j ACCEPT

# INPUT: SpertaServer (porta 22345) apenas da sub-rede dos clientes
sudo iptables -A INPUT -p tcp --dport "$SPERTA_PORT" -s "$CLIENTES_SUBNET" -j ACCEPT

# OUTPUT: permitir ping para sub-rede local e Broker (limitado)
sudo iptables -A OUTPUT -p icmp --icmp-type echo-request -d "$SPERSERV_SUBNET" \
  -m limit --limit 4/second --limit-burst 4 -j ACCEPT
sudo iptables -A OUTPUT -p icmp --icmp-type echo-request -d "$BROKER_IP" \
  -m limit --limit 4/second --limit-burst 4 -j ACCEPT

# OUTPUT: SSH para CondServ
sudo iptables -A OUTPUT -p tcp --dport 22 -d "$CONDSERV_IP" -j ACCEPT

echo "Regras aplicadas (sem mudar default policy). Verifica com: sudo iptables -L -v -n"