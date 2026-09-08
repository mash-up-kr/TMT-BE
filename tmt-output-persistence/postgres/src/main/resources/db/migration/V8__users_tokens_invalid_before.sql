-- 로그아웃 — 발급된 refresh 토큰 폐기 (TMT-353, U8)
--
-- 토큰은 stateless(서명 검증만)라 서버가 개별 토큰을 폐기할 자리가 없었다. 토큰마다 저장하는
-- 대신 사용자 행에 "이 시각 전에 발급된 토큰은 무효"를 한 번 찍는다 — 재발급이 refresh의 iat를
-- 이 값과 비교해 거절한다. 늘어나는 행이 없고, 의미는 "이 사용자의 전 기기 로그아웃"이다.
--
-- NULL이면 로그아웃한 적이 없다. access는 짧은 만료(1h)로 흘려보낸다 (X 명세 §4-2).
ALTER TABLE users
    ADD COLUMN tokens_invalid_before timestamptz;
