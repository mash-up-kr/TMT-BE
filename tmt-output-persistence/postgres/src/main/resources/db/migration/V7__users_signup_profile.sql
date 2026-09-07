-- 회원가입 프로필 저장 (TMT-370)
--
-- 카카오 로그인이 만든 users 행은 닉네임이 카카오 값이고, 사용자가 입력한 값이 들어갈 자리가
-- 없었다. 가입 완결(PUT /v1/users/me/profile)이 채울 두 컬럼을 넣는다.

-- 가입 화면을 끝낸 시각. NULL이면 미완료 — 가입 완결·내 프로필 조회 외 요청이 막힌다 (U8)
ALTER TABLE users
    ADD COLUMN profile_completed_at timestamptz;

-- 프로필 사진의 정본. 그룹 대표 이미지와 같은 업로드 경로다 (M7).
-- 기존 profile_image_url(카카오 값)은 남기고 신규 쓰기만 멈춘다 — 조회는 이 컬럼을 우선한다
ALTER TABLE users
    ADD COLUMN profile_image_asset_id BIGINT REFERENCES media_asset(id);

-- 이 마이그레이션 이전에 가입한 사용자는 완료로 본다. 안 채우면 이미 쓰고 있던 계정이
-- 전부 미완료가 되어 가입 화면으로 되돌려진다
UPDATE users
SET profile_completed_at = created_at
WHERE profile_completed_at IS NULL;
