-- 상가정보 원본 소분류 보존 (TMT-162)
--
-- 매핑(상권업종소분류명 43종 → 카테고리 14종, E11)을 나중에 고쳐도 재적재 없이
-- UPDATE로 재계산하기 위한 파이프라인 참조 테이블이다. place에는 매핑 결과
-- (category_id)만 남고 원본 소분류가 사라지므로, 여기 보존하지 않으면 매핑 수정
-- 시 원본 CSV부터 다시 돌려야 한다.
--
-- 적재 파이프라인(scripts/place-pipeline)만 쓰고 읽는다 — 앱 엔티티로 매핑하지 않는다.
CREATE TABLE place_semas_category (
    external_id     VARCHAR(100) PRIMARY KEY,   -- place.external_id (external_source = 'SEMAS')
    category_source VARCHAR(50)  NOT NULL       -- 상권업종소분류명 원문
);
