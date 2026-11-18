#!/usr/bin/env bash
# WAS·DB 공통 런타임 — 스왑과 Docker. 여러 번 돌려도 같은 상태가 된다.
# 사용: ssh ubuntu@<host> 'sudo bash -s' < common.sh
set -euo pipefail

# 1GB 마이크로라 JVM·PostgreSQL이 순간적으로 넘칠 때 OOM 대신 스왑으로 버틴다.
# swappiness=10 — 평상시에는 쓰지 않고 압박 시에만 밀어낸다.
if ! swapon --show --noheadings | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
fi
grep -q "^/swapfile" /etc/fstab || echo "/swapfile none swap sw 0 0" >> /etc/fstab
echo "vm.swappiness=10" > /etc/sysctl.d/99-tmt-swap.conf
sysctl -p /etc/sysctl.d/99-tmt-swap.conf

if ! command -v docker >/dev/null; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -q
  apt-get install -y -q docker.io docker-compose-v2
fi
systemctl enable --now docker
usermod -aG docker ubuntu
