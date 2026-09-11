#!/usr/bin/env bash
# 사용자 1명과 그 데이터를 전부 지운다 (TMT-353 시점 기준 탈퇴 기능이 없어 수동으로 지울 때).
# 되돌릴 수 없다. media_asset 행은 지우지만 S3 객체는 남는다.
# 지운 뒤 파생 집계(place 별점·리뷰수, group_place, groups의 멤버·리뷰·매장 수)를 같은 트랜잭션에서 다시 계산한다.
#
# 사용법:
#   PSQL="psql -h localhost -p 15432 -U tmt -d tmt" ./delete-user.sh 12
#   PSQL="..." ./delete-user.sh 12 --yes        # 확인 프롬프트 생략
#
# PSQL 은 대상 DB에 맞게 넘긴다:
#   로컬(compose):  docker exec -i tmt-postgres psql -U tmt -d tmt
#   운영:           psql -h localhost -p 15432 ...  (DB가 프라이빗이라 SSM 포트포워딩 경유)
#     aws ssm start-session --profile tmt --target <app-instance-id> \
#       --document-name AWS-StartPortForwardingSessionToRemoteHost \
#       --parameters '{"host":["<db-private-ip>"],"portNumber":["5432"],"localPortNumber":["15432"]}'
#
# 재로그인 테스트가 목적이라면 지우지 말고 kakao_id만 뒤집는 편이 낫다 — FK를 안 건드리고 되돌릴 수 있다:
#   UPDATE users SET kakao_id = -kakao_id WHERE id = <id>;
set -euo pipefail

USER_ID="${1:?사용법: delete-user.sh <user_id> [--yes]}"
CONFIRM="${2:-}"
PSQL="${PSQL:?PSQL 환경변수로 psql 실행 방법을 지정해야 한다 (스크립트 상단 주석 참고)}"

[[ "$USER_ID" =~ ^[0-9]+$ ]] || { echo "user_id는 숫자여야 한다: $USER_ID" >&2; exit 1; }

q() { $PSQL -v ON_ERROR_STOP=1 -tA -c "$1"; }

NICKNAME=$(q "SELECT nickname FROM users WHERE id = $USER_ID")
[ -n "$NICKNAME" ] || { echo "user_id=$USER_ID 사용자가 없다" >&2; exit 1; }

echo ">>> 대상"
$PSQL -v ON_ERROR_STOP=1 -c "SELECT id, kakao_id, nickname, created_at FROM users WHERE id = $USER_ID"

echo ">>> 함께 지워지는 것"
$PSQL -v ON_ERROR_STOP=1 -c "
  SELECT 'save' AS t, count(*) FROM save WHERE user_id = $USER_ID
  UNION ALL SELECT 'review',             count(*) FROM review             WHERE user_id  = $USER_ID
  UNION ALL SELECT 'place_favorite',     count(*) FROM place_favorite     WHERE user_id  = $USER_ID
  UNION ALL SELECT 'reward_grant',       count(*) FROM reward_grant       WHERE user_id  = $USER_ID
  UNION ALL SELECT 'group_join_ticket',  count(*) FROM group_join_ticket  WHERE user_id  = $USER_ID
  UNION ALL SELECT 'group_membership',   count(*) FROM group_membership   WHERE user_id  = $USER_ID
  UNION ALL SELECT 'group_review_share', count(*) FROM group_review_share WHERE user_id  = $USER_ID
  UNION ALL SELECT 'idempotency_key',    count(*) FROM idempotency_key    WHERE user_id  = $USER_ID
  UNION ALL SELECT 'media_asset',        count(*) FROM media_asset        WHERE owner_id = $USER_ID
  UNION ALL SELECT 'groups(owner)',      count(*) FROM groups             WHERE owner_id = $USER_ID
  ORDER BY 1"

# 소유 그룹에 다른 멤버가 있으면 멈춘다 — G13(소유자 불변)이라 넘길 자리가 없고,
# 그룹을 지우면 남의 공유 리뷰까지 사라진다. 사람이 정할 문제다.
SHARED=$(q "
  SELECT count(*) FROM groups g
  WHERE g.owner_id = $USER_ID
    AND EXISTS (SELECT 1 FROM group_membership m WHERE m.group_id = g.id AND m.user_id <> $USER_ID)")
if [ "$SHARED" != "0" ]; then
  echo >&2
  echo "중단: 이 사용자가 소유한 그룹에 다른 멤버가 있다 (${SHARED}개)." >&2
  $PSQL -v ON_ERROR_STOP=1 -c "
    SELECT g.id, g.name,
           (SELECT count(*) FROM group_membership m WHERE m.group_id = g.id) AS members
    FROM groups g
    WHERE g.owner_id = $USER_ID
      AND EXISTS (SELECT 1 FROM group_membership m WHERE m.group_id = g.id AND m.user_id <> $USER_ID)" >&2
  echo "그룹을 어떻게 할지 먼저 정하고 손으로 처리한 뒤 다시 실행한다." >&2
  exit 1
fi

if [ "$CONFIRM" != "--yes" ]; then
  echo
  read -r -p "user_id=$USER_ID ($NICKNAME) 를 지운다. 되돌릴 수 없다. user_id를 다시 입력: " TYPED
  [ "$TYPED" = "$USER_ID" ] || { echo "입력이 다르다 — 중단" >&2; exit 1; }
fi

# ON DELETE 규칙이 없어(NO ACTION) 순서가 틀리면 FK 위반으로 그 자리에서 멈춘다.
# 한 트랜잭션이라 중간에 실패하면 아무것도 지워지지 않는다.
$PSQL -v ON_ERROR_STOP=1 <<SQL
BEGIN;

-- 파생 집계를 다시 계산할 대상을 지우기 전에 잡아 둔다 (아래 재계산 구간에서 쓴다).
CREATE TEMP TABLE affected_place ON COMMIT DROP AS
SELECT DISTINCT place_id AS id FROM review WHERE user_id = $USER_ID;

CREATE TEMP TABLE affected_group ON COMMIT DROP AS
SELECT DISTINCT id FROM (
    SELECT group_id AS id FROM group_membership   WHERE user_id = $USER_ID
    UNION ALL
    SELECT group_id      FROM group_review_share  WHERE user_id = $USER_ID
    UNION ALL
    SELECT s.group_id
      FROM group_review_share s
      JOIN review r ON r.id = s.review_id
     WHERE r.user_id = $USER_ID
) t;

-- 남이 이 사용자의 리뷰를 공유한 행까지 끊어야 review 삭제가 FK에 걸리지 않는다.
DELETE FROM group_review_share
 WHERE user_id = $USER_ID
    OR review_id IN (SELECT id FROM review WHERE user_id = $USER_ID);
DELETE FROM review_ai_summary  WHERE review_id IN (SELECT id FROM review WHERE user_id = $USER_ID);
DELETE FROM review             WHERE user_id = $USER_ID;

DELETE FROM save_tag   WHERE save_id IN (SELECT id FROM save WHERE user_id = $USER_ID);
DELETE FROM save_photo WHERE save_id IN (SELECT id FROM save WHERE user_id = $USER_ID);
DELETE FROM save       WHERE user_id = $USER_ID;

-- 티켓이 근거(reward_grant)를 참조하므로 티켓부터
DELETE FROM group_join_ticket WHERE user_id = $USER_ID;
DELETE FROM reward_grant      WHERE user_id = $USER_ID;

DELETE FROM group_membership WHERE user_id = $USER_ID;
DELETE FROM group_place      WHERE group_id IN (SELECT id FROM groups WHERE owner_id = $USER_ID);
DELETE FROM group_region_tag WHERE group_id IN (SELECT id FROM groups WHERE owner_id = $USER_ID);
DELETE FROM group_membership WHERE group_id IN (SELECT id FROM groups WHERE owner_id = $USER_ID);
DELETE FROM groups           WHERE owner_id = $USER_ID;

DELETE FROM place_favorite  WHERE user_id  = $USER_ID;
DELETE FROM idempotency_key WHERE user_id  = $USER_ID;
-- users.profile_image_asset_id가 media_asset을 참조하므로 먼저 끊는다.
-- 남이 이 사용자의 에셋을 프로필로 쓰고 있어도 같이 풀린다.
UPDATE users SET profile_image_asset_id = NULL
 WHERE profile_image_asset_id IN (SELECT id FROM media_asset WHERE owner_id = $USER_ID);
DELETE FROM media_asset     WHERE owner_id = $USER_ID;

DELETE FROM users WHERE id = $USER_ID;

-- ── 파생 집계 재계산 ────────────────────────────────────
-- 증감으로 맞추지 않고, 지운 뒤 남은 사실에서 통째로 다시 만든다.
-- 앱의 PlaceStatsRepository·GroupStatsRepository가 하는 계산과 같은 정의다.

-- place.review_count / rating_sum — 별점은 save에 있다 (P9)
UPDATE place p
SET review_count = x.review_count,
    rating_sum   = x.rating_sum
FROM (
    SELECT ap.id,
           count(r.id)                  AS review_count,
           coalesce(sum(sv.rating), 0)  AS rating_sum
    FROM affected_place ap
    LEFT JOIN review r  ON r.place_id = ap.id AND r.deleted_at IS NULL
    LEFT JOIN save   sv ON sv.id = r.save_id
    GROUP BY ap.id
) x
WHERE p.id = x.id;

-- group_place — 공유 집합에서 재구성 (rebuildGroupPlaces와 동일)
DELETE FROM group_place WHERE group_id IN (SELECT id FROM affected_group);
INSERT INTO group_place (group_id, place_id, shared_review_count)
SELECT s.group_id, r.place_id, count(*)
FROM group_review_share s
JOIN review r ON r.id = s.review_id AND r.deleted_at IS NULL
WHERE s.group_id IN (SELECT id FROM affected_group)
GROUP BY s.group_id, r.place_id;

-- groups.member_count / review_count / place_count (group_place 재구성 후)
UPDATE groups g
SET member_count = (SELECT count(*) FROM group_membership m
                     WHERE m.group_id = g.id AND m.status = 'ACTIVE'),
    review_count = (SELECT count(*) FROM group_review_share s
                      JOIN review r ON r.id = s.review_id AND r.deleted_at IS NULL
                     WHERE s.group_id = g.id),
    place_count  = (SELECT count(*) FROM group_place p WHERE p.group_id = g.id),
    updated_at   = now()
WHERE g.id IN (SELECT id FROM affected_group);

COMMIT;
SQL

echo ">>> 완료 — user_id=$USER_ID ($NICKNAME)"
echo "    media_asset의 S3 객체는 남아 있다."
