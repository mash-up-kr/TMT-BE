#!/usr/bin/env bash
# WAS에서 실행한다 — CI가 /opt/tmt에 올린 뒤 부른다 (TMT-445).
# 시크릿 정본은 Vault 시크릿 tmt-prod-env(infra/oracle/vault.tf)다. 배포마다 WAS의 인스턴스 권한으로
# 읽어 /opt/tmt/.env를 새로 쓰고, 이미지와 릴리즈를 덧붙여 재기동한다.
# 사용: sudo bash /opt/tmt/remote-deploy.sh <이미지> <릴리즈> <시크릿 OCID>
set -euo pipefail

APP_IMAGE="${1:?이미지를 넘겨야 한다}"
SENTRY_RELEASE="${2:?릴리즈를 넘겨야 한다}"
ENV_SECRET_ID="${3:?.env 시크릿 OCID를 넘겨야 한다}"

cd /opt/tmt
umask 077

# 새 파일에 받아 검사한 뒤 바꾼다 — 읽기에 실패하면 기존 .env와 컨테이너를 그대로 둔다
oci secrets secret-bundle get --secret-id "$ENV_SECRET_ID" --auth instance_principal \
  --query 'data."secret-bundle-content".content' --raw-output | base64 -d > .env.new

# 필수 값이 비면 기존 컨테이너를 내리기 전에 멈춘다 — 빈 값으로 뜨면 그 기능만 조용히 죽거나
# 기동이 실패해 서비스가 함께 내려간다 (TMT-252·TMT-191)
for k in DB_HOST DB_NAME DB_USER DB_PASSWORD MEDIA_BUCKET MEDIA_BASE_URL \
         JUSO_SEARCH_KEY JUSO_COORD_KEY ADDRESS_TOKEN_SECRET \
         KAKAO_REST_API_KEY KAKAO_CLIENT_SECRET JWT_SECRET; do
  grep -q "^$k=." .env.new || { echo "Vault .env에 $k가 비어 있다" >&2; rm -f .env.new; exit 1; }
done

{
  echo "APP_IMAGE=$APP_IMAGE"
  echo "SENTRY_RELEASE=$SENTRY_RELEASE"
} >> .env.new
mv .env.new .env

docker compose -f docker-compose.prod.yml pull app
docker compose -f docker-compose.prod.yml up -d --force-recreate
docker image prune -f

echo ">>> 배포 완료"
docker ps
