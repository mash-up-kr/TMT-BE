#!/bin/bash
set -euo pipefail

# --- 스왑 2GiB (TMT-298) ---
# t3.micro(916MB)는 스왑이 없으면 메모리 스파이크가 곧 유저랜드 동결이다 — SSM 에이전트까지
# 죽어 복구 수단이 사라진다 (2026-09-01 v0.1.0 배포 장애). 스왑은 동결을 일시 지연으로 바꾼다.
# swappiness=10 — 평상시에는 안 쓰고 압박 시에만 밀어낸다.
if ! swapon --show --noheadings | grep -q /swapfile; then
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
fi
grep -q "^/swapfile" /etc/fstab || echo "/swapfile none swap sw 0 0" >> /etc/fstab
echo "vm.swappiness=10" > /etc/sysctl.d/99-tmt-swap.conf
sysctl -p /etc/sysctl.d/99-tmt-swap.conf

dnf install -y docker
systemctl enable --now docker

# 애플리케이션 자체는 여기서 띄우지 않는다. 릴리즈 태그 트리거 CI가
# 이미지를 배포하는 구조이므로(TMT-62), 부트스트랩은 런타임 준비까지만 한다.
mkdir -p /opt/tmt
