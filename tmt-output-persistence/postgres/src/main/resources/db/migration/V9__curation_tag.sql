-- 큐레이션 칩을 테이블로 (E12 · D4 개정)
--
-- 질문 36이 열어둔 두 갈래 중 "운영이 매장을 수동 매핑" 쪽을 택한 것이다. 질문 문서가
-- 그 갈래에 필요한 테이블 이름으로 curation_tag_place를 이미 지목해 뒀고, 결론도
-- "초기 하드코딩으로 관리 이후 논의 필요"였다 — 원칙적 결정이 아니라 유예였다.
--
-- 왜 서버 상수(CurationPresets)로는 안 되는가: 칩이 담던 것은 (categoryId, regionPrefix)
-- 조건 하나였고, 그래서 "을지로 야장"이 중구 전체와 같았다. 야장을 조건식으로 표현할
-- 방법이 없다. 매장을 지정하면 칩이 조건이 아니라 목록이 되고, 목록은 배포와 함께
-- 나갈 이유가 없다 (D4가 상수로 둔 근거는 "조건이라 해석 코드가 같이 바뀐다"였다).

CREATE TABLE curation_tag (
    id            VARCHAR(30) PRIMARY KEY,                  -- 'curation_euljiro_yajang' — API의 curationTagId 그대로
    label         VARCHAR(30) NOT NULL,                     -- 화면 노출 문구 (E12)
    display_order SMALLINT    NOT NULL,                     -- 화면 칩 배열 순서
    active        BOOLEAN     NOT NULL DEFAULT true,        -- 삭제 대신 비활성화 — review_tag_definition과 같은 방식
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

-- 칩에 속한 매장 집합. 조건 매칭이 아니라 운영이 고른 목록이다 (질문 36)
CREATE TABLE curation_tag_place (
    curation_tag_id VARCHAR(30) NOT NULL REFERENCES curation_tag(id),
    place_id        BIGINT      NOT NULL REFERENCES place(id),
    -- 운영이 의도한 노출 순서. **읽는 경로가 아직 없다** — 검색·핀은 기존 정렬
    -- (거리순·유사도순)을 그대로 쓴다. 칩이 필터인지 결과 형태를 바꾸는지가
    -- 도메인 v2 §7-1의 유일한 미결(E5, FE 확인 대기)이라, 정렬 축을 새로 만들지
    -- 않고 운영 입력만 받아 둔다. E5가 닫히면 이 컬럼이 정렬 키가 된다
    pin_order       SMALLINT    NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (curation_tag_id, place_id)
);
-- 칩 → 매장 조회는 PK 선두 컬럼으로 덮인다. 역방향(매장 → 칩) 쿼리는 없어 인덱스를 두지 않는다
