-- 상가정보 → place upsert (TMT-161)
--
-- 전제: place_staging에 transform.py의 TSV가 COPY로 들어와 있다 (load.sh가 수행).
-- 재실행 가능해야 한다 — (external_source, external_id) 충돌 시 갱신하므로
-- 같은 입력으로 두 번 돌려도 행이 늘지 않는다 (승인 기준).
--
-- category_id는 여기서 채우지 않는다 — 매핑은 TMT-162다. staging의 소분류명은
-- TMT-162가 쓸 수 있도록 적재 후에도 남겨두지 않는다(파이프라인 재실행 시 재생성).

INSERT INTO place (
    external_source, external_id, name, road_address, jibun_address,
    region_name, category_id, phone_number, location
)
SELECT
    external_source,
    external_id,
    name,
    road_address,
    jibun_address,
    region_name,
    NULL,          -- category_id: TMT-162에서 UPDATE로 채운다
    NULL,          -- phone_number: 상가정보에 컬럼 자체가 없다 (실측 §1, P10 괴리)
    ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography
FROM place_staging
ON CONFLICT (external_source, external_id) DO UPDATE SET
    name          = EXCLUDED.name,
    road_address  = EXCLUDED.road_address,
    jibun_address = EXCLUDED.jibun_address,
    region_name   = EXCLUDED.region_name,
    location      = EXCLUDED.location,
    updated_at    = now();
-- category_id·phone_number·review_count·rating_sum은 갱신하지 않는다 —
-- 카테고리는 TMT-162 소관이고, 집계는 서비스 데이터라 원본 재적재가 덮으면 안 된다.

SELECT
    (SELECT count(*) FROM place_staging)                          AS staging_rows,
    (SELECT count(*) FROM place WHERE external_source = 'SEMAS')  AS place_semas_rows;
