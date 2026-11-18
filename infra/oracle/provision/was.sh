#!/usr/bin/env bash
# WAS 서버 — 배포 스크립트가 Vault의 .env 시크릿을 읽는 데 쓰는 OCI CLI.
# 인증은 인스턴스 권한(동적 그룹 tmt-was, vault.tf)이라 키 파일이 필요 없다.
# 사용: ssh ubuntu@<was> 'sudo bash -s' < was.sh
set -euo pipefail

mkdir -p /opt/tmt

if [ ! -x /opt/oci-cli/bin/oci ]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -q
  apt-get install -y -q python3-venv
  python3 -m venv /opt/oci-cli
  /opt/oci-cli/bin/pip install -q --upgrade pip
  /opt/oci-cli/bin/pip install -q oci-cli
fi
ln -sf /opt/oci-cli/bin/oci /usr/local/bin/oci
oci --version
