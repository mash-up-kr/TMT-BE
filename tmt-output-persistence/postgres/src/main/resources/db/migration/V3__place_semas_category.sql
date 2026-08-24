-- 상가정보 원본 소분류 보존 (TMT-162)
--
-- 매핑(상권업종소분류명 43종 → 카테고리 14종, E11)을 나중에 고쳐도 재적재 없이
-- UPDATE로 재계산하기 위한 파이프라인 참조 테이블이다. place에는 매핑 결과
-- (category_id)만 남고 원본 소분류가 사라지므로, 여기 보존하지 않으면 매핑 수정
-- 시 원본 CSV부터 다시 돌려야 한다.
--
-- PK가 (external_source, external_id)인 이유: place의 유일성과 같은 축이다.
-- 지금은 SEMAS 단독이지만 인허가 병합(UT2 이후)이 들어오면 소스가 다른 같은
-- 번호가 충돌한다 — 적용 전에 축을 맞춰두는 쪽이 싸다 (PR #34 리뷰).
--
-- 적재 파이프라인(scripts/place-pipeline)만 쓰고 읽는다 — 앱 엔티티로 매핑하지 않는다.
CREATE TABLE place_semas_category (
    external_source VARCHAR(30)  NOT NULL,      -- place.external_source ('SEMAS' 등)
    external_id     VARCHAR(100) NOT NULL,      -- place.external_id
    category_source VARCHAR(50)  NOT NULL,      -- 원본 분류명 (상가정보: 상권업종소분류명)
    PRIMARY KEY (external_source, external_id)
);
