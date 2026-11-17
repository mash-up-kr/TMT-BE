#!/usr/bin/env bash
# DB 서버 — 데이터 볼륨 마운트와 PostgreSQL 컨테이너.
# 선행: /etc/tmt/db_password(600)를 먼저 올려 둔다. 비밀번호는 레포에 두지 않는다.
# 사용: ssh ubuntu@<host> 'sudo bash -s' < db.sh
set -euo pipefail

DEV=/dev/oracleoci/oraclevdb
for _ in $(seq 1 60); do
  [ -e "$DEV" ] && break
  sleep 5
done
[ -e "$DEV" ] || { echo "data volume did not appear" >&2; exit 1; }

# 파일시스템이 이미 있으면 절대 다시 만들지 않는다 — 재실행이 DB를 지우면 안 된다
if ! blkid "$DEV" >/dev/null 2>&1; then
  mkfs.ext4 -L tmtpgdata "$DEV"
fi
mkdir -p /mnt/pgdata
UUID=$(blkid -s UUID -o value "$DEV")
grep -q "$UUID" /etc/fstab || echo "UUID=$UUID /mnt/pgdata ext4 defaults,nofail,_netdev 0 2" >> /etc/fstab
mount -a
# ext4 루트의 lost+found 때문에 initdb가 거부한다 — 하위 디렉토리를 PGDATA로 쓴다
mkdir -p /mnt/pgdata/data

[ -s /etc/tmt/db_password ] || { echo "/etc/tmt/db_password missing" >&2; exit 1; }
chmod 600 /etc/tmt/db_password

if ! docker ps -a --format '{{.Names}}' | grep -qx postgres; then
  # 5432는 호스트에 바인딩하고, 노출면은 보안 규칙(VCN 내부만)으로 막는다
  docker run -d \
    --name postgres \
    --restart unless-stopped \
    -p 5432:5432 \
    -v /mnt/pgdata/data:/var/lib/postgresql/data \
    -v /etc/tmt/db_password:/run/secrets/db_password:ro \
    -e POSTGRES_DB="${POSTGRES_DB:-tmt}" \
    -e POSTGRES_USER="${POSTGRES_USER:-tmt}" \
    -e POSTGRES_PASSWORD_FILE=/run/secrets/db_password \
    postgis/postgis:16-3.4
fi
