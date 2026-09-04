-- 닉네임 상한 10자 → 20자 (TMT-350)
--
-- U3가 2026-08-23에 2~20자로 확정됐는데(X 명세 §3-1) DDL은 V1의 2~10자 그대로였다.
-- 지금 들어 있는 값은 시드 7건뿐이라 안 터지지만, 카카오 로그인(TMT-271·272)이
-- 붙으면 카카오 닉네임이 20자까지 오므로 INSERT가 거절된다. 그때 고치면 회원가입이
-- 막힌 채로 원인을 찾게 되므로 미리 넓혀 둔다.
--
-- 넓히는 방향이라 기존 행은 그대로 통과한다 — 재작성도 검증 스캔도 없다.
-- VARCHAR 확장은 Postgres에서 테이블 재작성 없이 카탈로그만 바꾼다.
--
-- 적용된 V1은 고치지 않는다. 체크섬이 달라져 기동이 막힌다 (TMT-96).

ALTER TABLE users
    ALTER COLUMN nickname TYPE VARCHAR(20);

-- CHECK는 폭과 따로 걸려 있어 함께 바꾸지 않으면 11자에서 여전히 막힌다.
-- 이름을 유지해야 다음 사람이 V1의 제약과 같은 것임을 안다.
ALTER TABLE users
    DROP CONSTRAINT users_nickname_len;

ALTER TABLE users
    ADD CONSTRAINT users_nickname_len CHECK (char_length(nickname) BETWEEN 2 AND 20);
