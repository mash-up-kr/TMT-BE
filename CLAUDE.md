# CLAUDE.md

TMT-BE 작업 규칙. 원본은 [`docs/BRANCHING.md`](docs/BRANCHING.md)이고, 이 문서는 커밋·PR 절차만 추린 것이다.
두 문서가 어긋나면 `docs/BRANCHING.md`를 따르고 이 문서를 고친다.

## 브랜치

```
<type>/<Jira키>      예) chore/TMT-60
```

| type | 용도 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 개선 |
| `docs` | 문서 |
| `chore` | 빌드 · 설정 · 의존성 |
| `test` | 테스트만 |

Jira 이슈 없이 브랜치를 만들지 않는다. `main`에 직접 push 하지 않는다.

## 커밋

Conventional Commits — `type: 제목`. 제목은 50자 내 명령형, 본문 한국어.

```
chore: 프로젝트 최초 세팅 (TMT-60)
fix: 리뷰 3개 미만 조회 시 NPE 수정
```

squash merge라 브랜치 내 중간 커밋은 자유롭게 나눠도 된다.

## PR

제목 형식은 PR 템플릿을 따른다.

```
[TMT-이슈번호] type: 설명      예) [TMT-60] chore: 프로젝트 최초 세팅
```

본문은 `.github/PULL_REQUEST_TEMPLATE.md`의 섹션을 채운다.

| 섹션 | 내용 |
| --- | --- |
| Related Issue | Jira 링크 |
| Why | 변경의 배경 · 목적 |
| What | 변경 내용 |
| How | 구현 방법 |
| Prompt Log | 작업에 쓴 프롬프트 기록. 에이전트가 초안을 쓰고 개발자가 의도를 덧붙인다 |

올리기 전에 diff를 처음부터 끝까지 직접 읽고, 로컬에서 `./gradlew ktlintCheck build`가 통과해야 한다.
변경 ±300줄을 넘으면 PR 분할을 먼저 고민한다.

## 머지

Squash merge. 최소 1명 approve + CI 통과. 리뷰 요청 후 48시간 내 리뷰가 없으면 CI 통과를 전제로 머지 가능.

리뷰 코멘트에는 등급을 붙인다 — `[must]` 머지 전 반드시 반영 / `[want]` 권장 / `[q]` 질문.

머지된 작업 브랜치는 삭제한다. 원격은 자동 삭제되므로 로컬만 정리한다.

```bash
git checkout main && git pull
git branch -d <머지된-브랜치>
git fetch --prune
```

## Jira 상태

본인이 직접 옮긴다.

```
브랜치 생성 → 진행 중   PR 오픈 → 검토 중   머지 → 완료
```

## 시크릿

`.env` · 키 파일 · 토큰은 커밋하지 않는다. 실수로 커밋했다면 히스토리에서 지우는 것으로 끝내지 말고 해당 시크릿을 즉시 rotate 한다.
