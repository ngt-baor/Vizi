#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script with sudo." >&2
  exit 1
fi

apt-get update
apt-get install -y docker.io docker-compose-v2 git ufw

systemctl enable --now docker

ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo "Docker and the host firewall are ready."
echo "Also allow TCP 80/443 and UDP 443 in the Oracle VCN ingress rules."
