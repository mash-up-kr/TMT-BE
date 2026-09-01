-- UT2 시드 검증 (TMT-231) — 모든 쿼리가 0행이면 정합. 한 행이라도 나오면 그 행이 어긋난 지점이다.
-- 실행: psql -U tmt -d tmt -f scripts/seed/verify_ut2_seed.sql
--
-- 검사 1~5는 파생 집계의 전역 불변식이라 전체를 본다. 검사 6~8은 시드에만 성립하는 계약이라
-- (리뷰≠티켓인 보유 상한 T6, 탈퇴자 공유 잔존, TMT-268의 사진 선택화 예정) 시드 범위로 좁힌다 —
-- 시드 리뷰 = seed/ut2/* 사진이 붙은 리뷰 (PR #84 리뷰).

-- 시드 리뷰 범위 (검사 6~8 공용)
CREATE TEMP VIEW _seed_reviews AS
SELECT DISTINCT r.id, r.user_id
FROM review r
JOIN save_photo sp ON sp.save_id = r.save_id
JOIN media_asset ma ON ma.id = sp.media_asset_id
WHERE ma.s3_key LIKE 'seed/ut2/%';

-- 1) place 파생 집계 (P9): review_count·rating_sum vs 실제 행
SELECT 'place_stats' AS check, p.id, p.name, p.review_count, c.cnt, p.rating_sum, c.rsum
FROM place p
JOIN (
    SELECT r.place_id, count(*) AS cnt, COALESCE(sum(s.rating), 0) AS rsum
    FROM review r JOIN save s ON s.id = r.save_id
    WHERE r.deleted_at IS NULL
    GROUP BY r.place_id
) c ON c.place_id = p.id
WHERE p.review_count <> c.cnt OR p.rating_sum <> c.rsum;

-- 2) 리뷰가 있는데 집계가 0인 매장 (1의 여집합)
SELECT 'place_zero' AS check, p.id, p.name
FROM place p
WHERE p.review_count = 0
  AND EXISTS (SELECT 1 FROM review r WHERE r.place_id = p.id AND r.deleted_at IS NULL);

-- 3) 그룹 지표 3종 (D3)
SELECT 'group_stats' AS check, g.id, g.name,
       g.member_count, m.cnt AS actual_members,
       g.review_count, s.cnt AS actual_shares,
       g.place_count,  gp.cnt AS actual_places
FROM groups g
LEFT JOIN (SELECT group_id, count(*) cnt FROM group_membership WHERE status = 'ACTIVE' GROUP BY group_id) m ON m.group_id = g.id
LEFT JOIN (SELECT grs.group_id, count(*) cnt FROM group_review_share grs JOIN review r ON r.id = grs.review_id AND r.deleted_at IS NULL GROUP BY grs.group_id) s ON s.group_id = g.id
LEFT JOIN (SELECT group_id, count(*) cnt FROM group_place GROUP BY group_id) gp ON gp.group_id = g.id
WHERE g.member_count <> COALESCE(m.cnt, 0)
   OR g.review_count <> COALESCE(s.cnt, 0)
   OR g.place_count  <> COALESCE(gp.cnt, 0);

-- 4) group_place.shared_review_count vs 실제 공유 행 (D3)
SELECT 'group_place' AS check, gp.group_id, gp.place_id, gp.shared_review_count, c.cnt
FROM group_place gp
JOIN (
    SELECT grs.group_id, r.place_id, count(*) AS cnt
    FROM group_review_share grs JOIN review r ON r.id = grs.review_id AND r.deleted_at IS NULL
    GROUP BY grs.group_id, r.place_id
) c ON c.group_id = gp.group_id AND c.place_id = gp.place_id
WHERE gp.shared_review_count <> c.cnt;

-- 5) 공유는 있는데 group_place 행이 없는 조합 (4의 여집합)
SELECT 'group_place_missing' AS check, grs.group_id, r.place_id
FROM group_review_share grs JOIN review r ON r.id = grs.review_id AND r.deleted_at IS NULL
GROUP BY grs.group_id, r.place_id
HAVING NOT EXISTS (SELECT 1 FROM group_place gp WHERE gp.group_id = grs.group_id AND gp.place_id = r.place_id);

-- 6) [시드 한정] 완성 리뷰의 필수 요소 (C4): 별점·본문·사진 1장 이상
SELECT 'review_c4' AS check, r.id, s.rating, char_length(s.content) AS content_len,
       (SELECT count(*) FROM save_photo sp WHERE sp.save_id = s.id) AS photos
FROM review r JOIN save s ON s.id = r.save_id
WHERE r.id IN (SELECT id FROM _seed_reviews) AND r.deleted_at IS NULL
  AND (s.rating IS NULL OR s.content IS NULL OR char_length(s.content) = 0
       OR NOT EXISTS (SELECT 1 FROM save_photo sp WHERE sp.save_id = s.id));

-- 7) [시드 한정] 리뷰 1건 = 발급 티켓 1장 — 실데이터는 보유 상한(T6)으로 리뷰≠티켓일 수 있다
SELECT 'ticket_grant' AS check, r.id AS review_id
FROM review r
WHERE r.id IN (SELECT id FROM _seed_reviews) AND r.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM reward_grant rg JOIN group_join_ticket t ON t.reward_grant_id = rg.id
    WHERE rg.source_type = 'REVIEW' AND rg.source_id = r.id
  );

-- 8) [시드 한정] 공유자는 그 그룹의 ACTIVE 멤버여야 한다 (G14 전제) — 실데이터는 정책 판단이 낄 수 있다
SELECT 'share_membership' AS check, grs.group_id, grs.user_id, grs.review_id
FROM group_review_share grs
WHERE grs.review_id IN (SELECT id FROM _seed_reviews)
  AND NOT EXISTS (
    SELECT 1 FROM group_membership m
    WHERE m.group_id = grs.group_id AND m.user_id = grs.user_id AND m.status = 'ACTIVE'
);

-- 9) 시드 사진의 media_asset은 전부 ATTACHED고 save_photo에 물려 있다 (M4 정리 대상 아님)
SELECT 'asset_attached' AS check, ma.id, ma.s3_key, ma.status
FROM media_asset ma
WHERE ma.s3_key LIKE 'seed/ut2/%'
  AND (ma.status <> 'ATTACHED' OR NOT EXISTS (SELECT 1 FROM save_photo sp WHERE sp.media_asset_id = ma.id));

DROP VIEW _seed_reviews;
