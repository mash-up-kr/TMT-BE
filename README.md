# TMT-BE

>  또맛또 백엔드

---

## 기술 스택

| 레이어 | 선택 |
| --- | --- |
| 언어 | Kotlin 2.3 / JDK 21 (LTS) |
| 프레임워크 | Spring Boot 4.1.0 |
| 빌드 | Gradle (Kotlin DSL) |
| API 문서 | springdoc-openapi (API 계약 SSoT) |
| 테스트 | JUnit 5 + MockK |
| 린트 | ktlint |

결정 배경은 Confluence [[기술] 또맛또 백엔드 기술 스택](https://ttalkkak.atlassian.net/wiki/spaces/ttalkkak/pages/41877513) 참고.

## 프로파일

| 프로파일              | 용도 |
|-------------------| --- |
| `local` (default) | 로컬 개발 — 애플리케이션 로그 DEBUG |
| `prod`            | 배포 — Swagger UI 비활성화, 로그 INFO |

설정값·시크릿은 커밋하지 않고 환경 변수로 주입한다.

## 코드 스타일

ktlint가 스타일을 강제한다. 들여쓰기·개행 규칙은 `.editorconfig` 참고.

```bash
./gradlew ktlintFormat
```

## 문서

- [브랜치 룰](docs/BRANCHING.md): 브랜치 전략 · 커밋 컨벤션 · PR 머지 규칙
