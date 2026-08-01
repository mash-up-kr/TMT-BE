#!/bin/bash
set -euo pipefail

dnf install -y docker
systemctl enable --now docker

# 애플리케이션 자체는 여기서 띄우지 않는다. 릴리즈 태그 트리거 CI가
# 이미지를 배포하는 구조이므로(TMT-62), 부트스트랩은 런타임 준비까지만 한다.
mkdir -p /opt/tmt
