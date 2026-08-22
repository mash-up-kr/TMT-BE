-- 초기 스키마 (TMT-96). 설계 문서: docs/DB-SCHEMA.md
-- 적용된 마이그레이션 파일은 수정하지 않는다 — 변경은 V2__*.sql을 새로 추가한다.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- 매장·그룹 검색 (E9·G18)

-- ── 사용자 ──────────────────────────────────────────────
CREATE TABLE users (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kakao_id          BIGINT NOT NULL UNIQUE,                  -- U1
    nickname          VARCHAR(10) NOT NULL,                    -- U3: 2~10자, 중복 허용
    profile_image_url TEXT,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_nickname_len CHECK (char_length(nickname) BETWEEN 2 AND 10)
);

-- ── 매장 (공공데이터 적재, P1~P5) ────────────────────────
CREATE TABLE place (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_source VARCHAR(30)  NOT NULL,                     -- 'LOCALDATA' | 'SEMAS'
    external_id     VARCHAR(100) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    road_address    VARCHAR(200) NOT NULL,
    jibun_address   VARCHAR(200),
    region_name     VARCHAR(50)  NOT NULL,                     -- "마포구 도화동" (ReviewCard·PlaceCard 표기)
    category_id     VARCHAR(30),                                -- 14종 상수 코드, 매핑 실패 시 NULL (E11)
    phone_number    VARCHAR(20),                                -- 결측 흔함 (P10)
    location        geography(Point, 4326) NOT NULL,            -- P4
    review_count    INT    NOT NULL DEFAULT 0,                  -- D3
    rating_sum      BIGINT NOT NULL DEFAULT 0,                  -- 평균 = rating_sum / review_count (P9)
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT place_external_uq UNIQUE (external_source, external_id)   -- P5
);
CREATE INDEX place_location_gix ON place USING GIST (location);          -- 반경·viewport (E1·E8)
-- E9 대상(가게명·주소·카테고리) 중 가게명만 커버한다. 주소·카테고리 검색은
-- 인덱스 없이도 동작하므로 느려지면 후속으로 붙인다.
CREATE INDEX place_name_trgm    ON place USING GIN (name gin_trgm_ops);
CREATE INDEX place_pins_ix      ON place (review_count) WHERE review_count > 0;  -- 지도 핀 (E6)

-- ── 찜 (F1·F2) ──────────────────────────────────────────
CREATE TABLE place_favorite (
    user_id    BIGINT NOT NULL REFERENCES users(id),
    place_id   BIGINT NOT NULL REFERENCES place(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, place_id)                             -- F2
);

-- ── 미디어 (M1~M5) ──────────────────────────────────────
CREATE TABLE media_asset (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id       BIGINT NOT NULL REFERENCES users(id),        -- M2
    s3_key         VARCHAR(300) NOT NULL UNIQUE,
    content_type   VARCHAR(50)  NOT NULL,
    content_length BIGINT       NOT NULL,                       -- ≤ 5MB는 앱 검증 (M3)
    status         VARCHAR(10)  NOT NULL DEFAULT 'STAGED',      -- STAGED → ATTACHED 단방향
    created_at     timestamptz  NOT NULL DEFAULT now(),
    attached_at    timestamptz,
    CONSTRAINT media_asset_status_ck CHECK (status IN ('STAGED', 'ATTACHED'))
);
CREATE INDEX media_asset_cleanup_ix ON media_asset (created_at) WHERE status = 'STAGED';  -- TTL 정리 (M4)

-- ── 저장 = 방문 기록의 정본 (§3) ─────────────────────────
CREATE TABLE save (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id),
    place_id   BIGINT NOT NULL REFERENCES place(id),            -- R3. (user,place) unique 없음 (S6)
    rating     SMALLINT,                                        -- R4
    content    VARCHAR(500),                                    -- C4 상한
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,                                     -- D6
    CONSTRAINT save_rating_ck CHECK (rating BETWEEN 1 AND 5)
);
-- 이어쓰기 목록 (G §5-1): 미완성만, updatedAt DESC — review가 없는 save만 담는 partial index
-- (review 존재 여부는 review.save_id로 판별하므로 인덱스는 §review 아래 참고)
CREATE INDEX save_owner_ix ON save (user_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX save_place_ix ON save (place_id) WHERE deleted_at IS NULL;

CREATE TABLE save_photo (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    save_id        BIGINT   NOT NULL REFERENCES save(id),
    media_asset_id BIGINT   NOT NULL UNIQUE REFERENCES media_asset(id),  -- attach 1회 (MEDIA_ALREADY_ATTACHED)
    photo_order    SMALLINT NOT NULL,
    CONSTRAINT save_photo_order_uq UNIQUE (save_id, photo_order),
    CONSTRAINT save_photo_order_ck CHECK (photo_order BETWEEN 0 AND 2)   -- 최대 3장 (M3)
);

CREATE TABLE review_tag_definition (
    id            VARCHAR(30) PRIMARY KEY,                      -- 'tag_couple' — API의 tagId 그대로
    tag_type      VARCHAR(20) NOT NULL,                         -- COMPANION | POSITIVE_POINT
    label         VARCHAR(30) NOT NULL,
    display_order SMALLINT    NOT NULL,
    active        BOOLEAN     NOT NULL DEFAULT true,            -- 삭제 대신 비활성화 (§3)
    CONSTRAINT review_tag_type_ck CHECK (tag_type IN ('COMPANION', 'POSITIVE_POINT'))
);

CREATE TABLE save_tag (
    save_id BIGINT      NOT NULL REFERENCES save(id),
    tag_id  VARCHAR(30) NOT NULL REFERENCES review_tag_definition(id),
    PRIMARY KEY (save_id, tag_id)                               -- 중복 금지 (§3)
);

-- ── 리뷰 = 완성 사실의 표지 (§3, D1) ─────────────────────
CREATE TABLE review (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    save_id    BIGINT NOT NULL UNIQUE REFERENCES save(id),      -- Save 1건당 최대 1개
    user_id    BIGINT NOT NULL REFERENCES users(id),            -- 조회 비정규화 (게이트·프로필 목록)
    place_id   BIGINT NOT NULL REFERENCES place(id),            -- 조회 비정규화 (가게 리뷰 목록·핀)
    created_at timestamptz NOT NULL DEFAULT now(),              -- 공개 시각
    deleted_at timestamptz                                      -- D6
);
CREATE INDEX review_place_ix ON review (place_id, created_at DESC, id DESC) WHERE deleted_at IS NULL;  -- B §3-2
CREATE INDEX review_user_ix  ON review (user_id,  created_at DESC, id DESC) WHERE deleted_at IS NULL;  -- 공유 목록 (H §3-1)

CREATE TABLE review_ai_summary (
    review_id  BIGINT PRIMARY KEY REFERENCES review(id),        -- 행 없음 = 응답 null (A2)
    pros       TEXT,
    cons       TEXT,
    model      VARCHAR(50),
    created_at timestamptz NOT NULL DEFAULT now()
);

-- ── 티켓 (T1~T9, D2) ────────────────────────────────────
CREATE TABLE reward_grant (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    reward_type VARCHAR(20) NOT NULL,                           -- 'GROUP_JOIN_TICKET'
    source_type VARCHAR(20) NOT NULL,                           -- 'SIGNUP' | 'REVIEW'
    source_id   BIGINT      NOT NULL,                           -- SIGNUP이면 user_id, REVIEW면 review_id
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT reward_grant_uq UNIQUE (source_type, source_id, reward_type)   -- T8
);

CREATE TABLE group_join_ticket (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    reward_grant_id BIGINT NOT NULL UNIQUE REFERENCES reward_grant(id),  -- 근거 1건당 1장 (§3)
    status          VARCHAR(10) NOT NULL DEFAULT 'AVAILABLE',   -- T4: EXPIRED 없음
    -- 소비처 로그. TX-3의 티켓 확보 UPDATE가 consumed_at과 함께 채운다 (도메인 v2 반영 예정).
    -- 로그 성격이라 FK를 걸지 않는다 — 그룹 삭제가 생겨도 로그가 지워지거나 삭제를 막지 않게.
    consumed_group_id BIGINT,
    consumed_at     timestamptz,
    revoked_at      timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ticket_status_ck CHECK (status IN ('AVAILABLE', 'CONSUMED', 'REVOKED'))
);
CREATE INDEX ticket_available_ix ON group_join_ticket (user_id, id) WHERE status = 'AVAILABLE';  -- T5·T7

-- ── 그룹 (G1~G19) ───────────────────────────────────────
CREATE TABLE groups (                                           -- "group"은 예약어
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             VARCHAR(50) NOT NULL UNIQUE,               -- G6
    one_line_description VARCHAR(100) NOT NULL,                 -- G15 필수
    description      VARCHAR(200),                              -- G15 선택
    image_asset_id   BIGINT REFERENCES media_asset(id),         -- 원형 대표 이미지 (M7)
    food_category_id VARCHAR(30) NOT NULL,                      -- G7: 1개, 상수 코드 (D4)
    owner_id         BIGINT NOT NULL REFERENCES users(id),      -- G13 불변
    member_count     INT NOT NULL DEFAULT 1,                    -- D3 (생성자 포함)
    review_count     INT NOT NULL DEFAULT 0,
    place_count      INT NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);
-- G18 대상(그룹명·한줄 소개·그룹 태그) 중 그룹명만 커버한다. one_line_description·
-- group_region_tag 경로는 인덱스 없이도 동작하므로 느려지면 후속으로 붙인다.
CREATE INDEX groups_name_trgm ON groups USING GIN (name gin_trgm_ops);
CREATE INDEX groups_recommend_ix ON groups (member_count DESC, id DESC); -- G17 2·3차 키

CREATE TABLE group_region_tag (
    group_id      BIGINT      NOT NULL REFERENCES groups(id),
    region_tag_id VARCHAR(30) NOT NULL,                         -- 26종 상수 코드 (D4). 최소 1개는 앱 강제 (G7)
    PRIMARY KEY (group_id, region_tag_id)
);

CREATE TABLE group_membership (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id  BIGINT NOT NULL REFERENCES groups(id),
    user_id   BIGINT NOT NULL REFERENCES users(id),
    status    VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',            -- ACTIVE | LEFT (G3·G11: PENDING·REMOVED 없음)
    joined_at timestamptz NOT NULL DEFAULT now(),
    left_at   timestamptz,
    CONSTRAINT membership_status_ck CHECK (status IN ('ACTIVE', 'LEFT'))
);
CREATE UNIQUE INDEX membership_active_uq ON group_membership (group_id, user_id) WHERE status = 'ACTIVE';  -- D5
CREATE INDEX membership_user_ix ON group_membership (user_id, joined_at) WHERE status = 'ACTIVE';  -- 홈 내 그룹 (A §2)

CREATE TABLE group_review_share (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id   BIGINT NOT NULL REFERENCES groups(id),
    review_id  BIGINT NOT NULL REFERENCES review(id),
    user_id    BIGINT NOT NULL REFERENCES users(id),            -- 공유자 = 리뷰 소유자. 집합 갱신 단위 (G14)
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT share_uq UNIQUE (group_id, review_id)            -- §3
);
CREATE INDEX share_gate_ix ON group_review_share (group_id, created_at DESC, review_id DESC);  -- 게이트 목록 (G1)
CREATE INDEX share_user_ix ON group_review_share (group_id, user_id);                          -- 집합 교체 (TX-4)
CREATE INDEX share_review_ix ON group_review_share (review_id);                                -- 리뷰 삭제 시 내림 (TX-5)

CREATE TABLE group_place (                                      -- 파생 집계 (§3 GroupPlace, D3)
    group_id            BIGINT NOT NULL REFERENCES groups(id),
    place_id            BIGINT NOT NULL REFERENCES place(id),
    shared_review_count INT    NOT NULL DEFAULT 0,              -- 0이 되면 행 삭제 → place_count = 행 수
    PRIMARY KEY (group_id, place_id)
);

-- ── 멱등성 (공통 규약 §9) ────────────────────────────────
CREATE TABLE idempotency_key (
    user_id             BIGINT       NOT NULL REFERENCES users(id),
    endpoint            VARCHAR(80)  NOT NULL,                  -- 'POST /v1/saves' 등
    idem_key            VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64)  NOT NULL,                  -- 바디 해시. 다르면 IDEMPOTENCY_CONFLICT
    response_status     SMALLINT     NOT NULL,
    response_body       JSONB        NOT NULL,                  -- 최초 응답 재현
    created_at          timestamptz  NOT NULL DEFAULT now(),    -- TTL 정리 대상
    PRIMARY KEY (user_id, endpoint, idem_key)
);
