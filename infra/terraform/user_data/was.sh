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

# --- 스왑 관측 (TMT-303) ---
# 스왑(TMT-298)은 장애를 "죽음"에서 "느린 채 살아 있음"으로 바꾼다 — 관측이 없으면 열화를 아무도 모른다.
# 5분마다 CloudWatch TMT/Memory에 4개 지표를 올린다: SwapUsedPercent · MemAvailableMB ·
# SwapInPages · SwapOutPages(직전 실행 이후 델타). 상향 판단 기준은 infra/terraform/README.md §스왑 관측.
cat > /usr/local/bin/tmt-swap-metrics.sh <<'EOS'
#!/bin/bash
set -euo pipefail
ROLE="was"
STATE=/var/lib/tmt/swap-metrics.state
mkdir -p "$(dirname "$STATE")"

TOKEN=$(curl -sS -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
REGION=$(curl -sS -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/placement/region)

# /proc/meminfo는 "SwapTotal:  2097148 kB" 꼴 — 숫자 필드만 뽑는다 (read로 받으면 "kB"가 붙어 정수 비교가 깨진다)
swap_total=$(awk '/^SwapTotal:/ {print $2}' /proc/meminfo)
swap_free=$(awk '/^SwapFree:/ {print $2}' /proc/meminfo)
mem_avail=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo)
pswpin=$(awk '/^pswpin/ {print $2}' /proc/vmstat)
pswpout=$(awk '/^pswpout/ {print $2}' /proc/vmstat)

swap_used_pct=0
if [ "$swap_total" -gt 0 ]; then
  swap_used_pct=$(( (swap_total - swap_free) * 100 / swap_total ))
fi

# 직전 실행과의 델타 — 첫 실행이나 재부팅(카운터 리셋)이면 0으로 본다
prev_in=0; prev_out=0
[ -f "$STATE" ] && read -r prev_in prev_out < "$STATE"
d_in=$(( pswpin - prev_in ));  [ "$d_in"  -lt 0 ] && d_in=0
d_out=$(( pswpout - prev_out )); [ "$d_out" -lt 0 ] && d_out=0
echo "$pswpin $pswpout" > "$STATE"

aws cloudwatch put-metric-data --region "$REGION" --namespace TMT/Memory --metric-data \
  "MetricName=SwapUsedPercent,Dimensions=[{Name=Role,Value=$ROLE}],Value=$swap_used_pct,Unit=Percent" \
  "MetricName=MemAvailableMB,Dimensions=[{Name=Role,Value=$ROLE}],Value=$(( mem_avail / 1024 )),Unit=Megabytes" \
  "MetricName=SwapInPages,Dimensions=[{Name=Role,Value=$ROLE}],Value=$d_in,Unit=Count" \
  "MetricName=SwapOutPages,Dimensions=[{Name=Role,Value=$ROLE}],Value=$d_out,Unit=Count"
EOS
chmod +x /usr/local/bin/tmt-swap-metrics.sh

cat > /etc/systemd/system/tmt-swap-metrics.service <<'EOS'
[Unit]
Description=TMT swap/memory metrics to CloudWatch (TMT-303)
After=network-online.target
Wants=network-online.target

[Service]
Type=oneshot
ExecStart=/usr/local/bin/tmt-swap-metrics.sh
EOS

cat > /etc/systemd/system/tmt-swap-metrics.timer <<'EOS'
[Unit]
Description=Run tmt-swap-metrics every 5 minutes

[Timer]
OnCalendar=*:0/5
AccuracySec=30s
Persistent=true

[Install]
WantedBy=timers.target
EOS
systemctl daemon-reload
systemctl enable --now tmt-swap-metrics.timer
