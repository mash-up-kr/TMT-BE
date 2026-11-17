#!/usr/bin/env bash
# DB 서버 — 매주 pg_dump를 떠서 tmt-db-backup 버킷 pg/YYYY/MM/에 올린다. 보관 30일은 버킷 수명주기가 지운다.
# 선행: /etc/tmt/backup.env(600)에 S3 호환 키를 둔다.
#   OCI_S3_ACCESS_KEY_ID=… / OCI_S3_SECRET_ACCESS_KEY=…
# 사용: ssh ubuntu@<db> 'sudo bash -s' < backup.sh
set -euo pipefail

[ -s /etc/tmt/backup.env ] || { echo "/etc/tmt/backup.env missing" >&2; exit 1; }
chmod 600 /etc/tmt/backup.env

command -v rclone >/dev/null || { apt-get update -q && DEBIAN_FRONTEND=noninteractive apt-get install -y -q rclone; }

cat > /usr/local/bin/tmt-pg-backup.sh <<'EOS'
#!/usr/bin/env bash
set -euo pipefail
source /etc/tmt/backup.env

export RCLONE_CONFIG_OCI_TYPE=s3
export RCLONE_CONFIG_OCI_PROVIDER=Other
export RCLONE_CONFIG_OCI_ENDPOINT=https://axrpyc6zylkj.compat.objectstorage.ap-osaka-1.oraclecloud.com
export RCLONE_CONFIG_OCI_REGION=ap-osaka-1
export RCLONE_CONFIG_OCI_FORCE_PATH_STYLE=true
export RCLONE_CONFIG_OCI_ACCESS_KEY_ID="$OCI_S3_ACCESS_KEY_ID"
export RCLONE_CONFIG_OCI_SECRET_ACCESS_KEY="$OCI_S3_SECRET_ACCESS_KEY"

docker ps --format '{{.Names}}' | grep -qx postgres || { echo "postgres is not running — skipping backup" >&2; exit 1; }

TS=$(date -u +%Y%m%dT%H%M%SZ)
OUT="/var/tmp/tmt-$TS.dump"
trap 'rm -f "$OUT"' EXIT

docker exec postgres bash -c 'PGPASSWORD=$(cat /run/secrets/db_password) pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$OUT"
# 빈 덤프를 성공으로 올리지 않는다
[ -s "$OUT" ] || { echo "empty dump" >&2; exit 1; }

rclone copyto "$OUT" "oci:tmt-db-backup/pg/$(date -u +%Y/%m)/tmt-$TS.dump"
echo "uploaded tmt-$TS.dump ($(stat -c %s "$OUT") bytes)"
EOS
chmod 750 /usr/local/bin/tmt-pg-backup.sh

cat > /etc/systemd/system/tmt-pg-backup.service <<'EOS'
[Unit]
Description=TMT PostgreSQL backup to Object Storage
After=docker.service

[Service]
Type=oneshot
ExecStart=/usr/local/bin/tmt-pg-backup.sh
EOS

# 인스턴스 타임존이 UTC다 — 일요일 18:00 UTC = 월요일 03:00 KST
cat > /etc/systemd/system/tmt-pg-backup.timer <<'EOS'
[Unit]
Description=TMT PostgreSQL backup schedule

[Timer]
OnCalendar=Sun *-*-* 18:00:00
Persistent=true

[Install]
WantedBy=timers.target
EOS

systemctl daemon-reload
systemctl enable --now tmt-pg-backup.timer
