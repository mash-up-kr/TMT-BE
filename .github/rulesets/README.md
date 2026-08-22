# Ruleset

`docs/BRANCHING.md` §4의 머지 규칙을 GitHub ruleset으로 강제한다 (TMT-103). 규칙 자체는 TMT-55에서 확정됐다.

콘솔에서 클릭으로 만들지 않고 **JSON을 리뷰받은 뒤 그걸로 적용한다** — 인프라를 Terraform으로 정의하는 것과 같은 이유다. 누가 언제 무엇을 왜 바꿨는지가 git 히스토리에 남는다.

## 왜 ruleset이 두 개인가 (TMT-173)

BRANCHING.md §4의 *"리뷰 요청 후 48시간 내 리뷰가 없으면 머지 가능"* 은 **CI 통과 전제, 사후 리뷰 환영**으로 정의돼 있다. 그런데 `bypass_actors`는 ruleset 최상위 속성이라 그 안의 **모든 룰에 걸린다** — 하나의 ruleset에 approve 룰과 CI 룰을 같이 두고 바이패스를 주면, approve만 건너뛰어야 할 사람이 CI 실패까지 함께 뚫는다.

그래서 둘로 나눈다:

| ruleset | 룰 | 바이패스 |
|---|---|---|
| `main.json` | main 삭제 차단 · force push 차단 · **CI 통과 필수** | **없음** — 누구도 못 뚫는다 |
| `main-review.json` | PR 필수 + **approve 최소 1명** + squash만 허용 | Maintain(4) · Admin(5), `pull_request` 모드 |

`bypass_mode: "pull_request"`는 PR을 통해서만 우회할 수 있다는 뜻이다. `git push origin main` 직접 푸시는 `main-review`의 `pull_request` 룰이 (바이패스 대상이 아닌 경로로) 계속 막고, 삭제·force push·CI는 `main.json`이 바이패스 없이 막는다.

### 바이패스 대상이 Maintain + Admin인 이유

Repository admin(5)만으로는 BE 3명 중 1명만 커버된다 — 나머지 admin 11명은 Mash-Up 운영진이라 이 레포 작업과 무관하고, mingdodev·toychip은 maintain이다. Maintain(4)을 함께 넣어야 팀 전원이 48시간 룰을 쓸 수 있다.

### squash 강제는 레포 설정으로도 닫는다

`allowed_merge_methods`가 `pull_request` 룰 파라미터 안에 있어서, 바이패스 대상자는 approve와 함께 squash 강제도 벗어난다. 레포 설정에서 merge commit·rebase를 꺼서 이중으로 닫는다:

```bash
GH_HOST=github.com gh api -X PATCH repos/mash-up-kr/TMT-BE \
  -F allow_merge_commit=false -F allow_rebase_merge=false
```

## 적용

```bash
# 신규 생성
GH_HOST=github.com gh api repos/mash-up-kr/TMT-BE/rulesets --input .github/rulesets/main-review.json
# 기존 main ruleset 갱신 (ID는 목록에서 확인)
RULESET_ID=$(GH_HOST=github.com gh api repos/mash-up-kr/TMT-BE/rulesets -q '.[] | select(.name=="main") | .id')
GH_HOST=github.com gh api -X PUT repos/mash-up-kr/TMT-BE/rulesets/$RULESET_ID --input .github/rulesets/main.json
```

순서 주의 — **`main-review`를 먼저 만들고 `main`에서 `pull_request` 룰을 뺀다.** 반대로 하면 그 사이에 approve 없는 머지·직접 push가 열린다. ruleset 관리는 레포 Admin 권한이 필요하다.

required check인 `자동 검증 (ktlint + test)`는 `ci-pull-request.yml`(PR #4, 머지됨)로 main에서 이미 PR마다 돌고 있으므로 바로 적용해도 안전하다. 단, **존재하지 않는 체크를 required로 걸면 모든 PR이 영구히 블록되므로**, 컨텍스트를 바꾸거나 추가할 때는 워크플로의 job `name`과 문자 단위로 일치하는지 반드시 확인한다.

### `ready for launch (CI)`는 왜 없나

같은 워크플로의 `build` job(`ready for launch (CI)`)은 required check으로 넣지 않았다. `issue_comment`(`빌드검증` 코멘트) 전용 job이라 `pull_request` 이벤트에서는 skip되고, 코멘트로 실행돼도 run이 PR head SHA에 붙지 않아 required check을 만족시킬 수단이 없다. 온디맨드 빌드 게이트를 머지 조건으로 강제하려면 워크플로를 `pull_request` 트리거로 고치는 게 선행돼야 하고, 그건 별도 이슈로 다룬다.

## `actor_id` 검증

RepositoryRole의 `actor_id`는 **4 = Maintain, 5 = Repository admin**이다 (TMT-173 적용 시 Settings → Rules → main-review → Bypass list에서 두 역할이 그대로 보이는 것을 확인함). 값을 바꿀 때는 적용 후 아래로 다시 확인한다:

```bash
RULESET_ID=$(GH_HOST=github.com gh api repos/mash-up-kr/TMT-BE/rulesets -q '.[] | select(.name=="main-review") | .id')
GH_HOST=github.com gh api repos/mash-up-kr/TMT-BE/rulesets/$RULESET_ID \
  -q '.bypass_actors[] | "\(.actor_type) id=\(.actor_id) mode=\(.bypass_mode)"'
```

## ruleset 밖의 설정

`delete_branch_on_merge`는 ruleset이 아니라 레포 설정이다. BRANCHING.md §4의 "머지 후 작업 브랜치 삭제(자동 삭제 설정)":

```bash
GH_HOST=github.com gh api -X PATCH repos/mash-up-kr/TMT-BE -F delete_branch_on_merge=true
```

## 검증

적용했다고 끝이 아니다. 실제로 확인한다:

- approve 없는 PR에서 maintain 계정의 머지 버튼이 (바이패스 경고와 함께) 열리는지
- CI 실패 상태에서는 여전히 막히는지 — 바이패스가 CI까지 뚫으면 분리가 잘못된 것이다
- merge commit / rebase 버튼이 사라지는지
- `git push origin main`이 거부되는지
