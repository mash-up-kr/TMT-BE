#!/usr/bin/env bash
# 상가정보 TSV → place 적재 (TMT-161). 재실행 가능 — upsert라 두 번 돌려도 결과가 같다.
#
# 사용법:
#   PSQL="podman exec -i tmt-postgres psql -U tmt -d tmt" ./load.sh clean.tsv
#
# PSQL 은 대상 DB에 맞게 넘긴다:
#   로컬(compose):  podman exec -i tmt-postgres psql -U tmt -d tmt
#   검증 컨테이너:  podman exec -i ddl psql -U postgres -d tmt
#   운영(DB EC2):   docker exec -i postgres psql -U tmt -d tmt  (SSM 경유, README 참고)
set -euo pipefail

TSV="${1:?사용법: load.sh <clean.tsv>}"
PSQL="${PSQL:?PSQL 환경변수로 psql 실행 방법을 지정해야 한다 (스크립트 상단 주석 참고)}"
SQL_DIR="$(cd "$(dirname "$0")" && pwd)/sql"

echo ">>> staging 재생성"
$PSQL -v ON_ERROR_STOP=1 -c "
  DROP TABLE IF EXISTS place_staging;
  CREATE UNLOGGED TABLE place_staging (
    external_source varchar(30)  NOT NULL,
    external_id     varchar(100) NOT NULL,
    name            varchar(100) NOT NULL,
    road_address    varchar(200) NOT NULL,
    jibun_address   varchar(200),
    region_name     varchar(50)  NOT NULL,
    lon             double precision NOT NULL,
    lat             double precision NOT NULL
  );"

echo ">>> COPY (${TSV})"
$PSQL -v ON_ERROR_STOP=1 -c "COPY place_staging FROM STDIN WITH (FORMAT text)" < "$TSV"

echo ">>> upsert"
$PSQL -v ON_ERROR_STOP=1 < "$SQL_DIR/upsert.sql"

echo ">>> staging 정리"
$PSQL -v ON_ERROR_STOP=1 -c "DROP TABLE place_staging;"
