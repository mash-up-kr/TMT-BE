#!/usr/bin/env bash
# WAS에서 실행한다 — CI가 /opt/tmt에 올린 뒤 부른다 (TMT-445).
# 시크릿 정본은 서버의 /opt/tmt/.env(600)다. 여기서는 이미지와 릴리즈만 바꾸고 재기동한다.
# 사용: sudo bash /opt/tmt/remote-deploy.sh <이미지> <릴리즈>
set -euo pipefail

APP_IMAGE="${1:?이미지를 넘겨야 한다}"
SENTRY_RELEASE="${2:?릴리즈를 넘겨야 한다}"

cd /opt/tmt
[ -f .env ] || { echo "/opt/tmt/.env가 없다 — 시크릿을 먼저 올려야 한다" >&2; exit 1; }

set_env() {
  if grep -q "^$1=" .env; then
    sed -i "s|^$1=.*|$1=$2|" .env
  else
    echo "$1=$2" >> .env
  fi
}
set_env APP_IMAGE "$APP_IMAGE"
set_env SENTRY_RELEASE "$SENTRY_RELEASE"

# 필수 값이 비면 기존 컨테이너를 내리기 전에 멈춘다 — 빈 값으로 뜨면 그 기능만 조용히 죽거나
# 기동이 실패해 서비스가 함께 내려간다 (TMT-252·TMT-191)
for k in DB_HOST DB_NAME DB_USER DB_PASSWORD MEDIA_BUCKET MEDIA_BASE_URL \
         JUSO_SEARCH_KEY JUSO_COORD_KEY ADDRESS_TOKEN_SECRET \
         KAKAO_REST_API_KEY KAKAO_CLIENT_SECRET JWT_SECRET; do
  grep -q "^$k=." .env || { echo "/opt/tmt/.env에 $k가 비어 있다" >&2; exit 1; }
done

docker compose -f docker-compose.prod.yml pull app
docker compose -f docker-compose.prod.yml up -d --force-recreate
docker image prune -f

echo ">>> 배포 완료"
docker ps
