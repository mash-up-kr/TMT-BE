package com.tmt.input.http.auth

/**
 * 컨트롤러 파라미터에 붙여 요청 사용자의 ID를 주입받는다.
 *
 * - `@UserId userId: Long` — 인증 필수. 헤더가 없으면 401 UNAUTHORIZED
 * - `@UserId userId: Long?` — 인증 선택(비로그인 열람 허용 화면). 헤더가 없으면 null
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class UserId
