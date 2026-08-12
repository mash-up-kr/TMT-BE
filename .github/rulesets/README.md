# Ruleset

`docs/BRANCHING.md` §4의 머지 규칙을 GitHub ruleset으로 강제한다 (TMT-103). 규칙 자체는 TMT-55에서 확정됐다.

콘솔에서 클릭으로 만들지 않고 **JSON을 리뷰받은 뒤 그걸로 적용한다** — 인프라를 Terraform으로 정의하는 것과 같은 이유다. 누가 언제 무엇을 왜 바꿨는지가 git 히스토리에 남는다.

## `main.json`이 강제하는 것

| 룰 | 근거 (BRANCHING.md) |
|---|---|
| PR 필수 + **approve 최소 1명** | §4 머지 조건 |
| **squash merge만** 허용 | §4 "main 히스토리 = PR 단위" |
| main 삭제 차단 (`deletion`) | §1 "main은 항상 배포 가능" |
| force push 차단 (`non_fast_forward`) | 같음 |
| CI 통과 필수 (`required_status_checks`) | §4 머지 조건 |

`pull_request` 룰이 있으면 main 직접 push는 자동으로 막힌다 — 별도 룰이 필요 없다.

## 적용

```bash
GH_HOST=github.com gh api repos/mash-up-kr/TMT-BE/rulesets --input .github/rulesets/main.json
```

required check인 `자동 검증 (ktlint + test)`는 `ci-pull-request.yml`(PR #4, 머지됨)로 main에서 이미 PR마다 돌고 있으므로 바로 적용해도 안전하다. 단, **존재하지 않는 체크를 required로 걸면 모든 PR이 영구히 블록되므로**, 컨텍스트를 바꾸거나 추가할 때는 워크플로의 job `name`과 문자 단위로 일치하는지 반드시 확인한다.

### `ready for launch (CI)`는 왜 없나

같은 워크플로의 `build` job(`ready for launch (CI)`)은 required check으로 넣지 않았다. `issue_comment`(`빌드검증` 코멘트) 전용 job이라 `pull_request` 이벤트에서는 skip되고, 코멘트로 실행돼도 run이 PR head SHA에 붙지 않아 required check을 만족시킬 수단이 없다. 온디맨드 빌드 게이트를 머지 조건으로 강제하려면 워크플로를 `pull_request` 트리거로 고치는 게 선행돼야 하고, 그건 별도 이슈로 다룬다.

## ⚠️ `bypass_actors`의 `actor_id`를 확인할 것

`{"actor_id": 5, "actor_type": "RepositoryRole"}`을 **Repository admin**으로 넣어뒀다. BRANCHING.md §4의 *"리뷰 요청 후 48시간 내 리뷰가 없으면 머지 가능"* 을 살리기 위한 것이다 — approve 1명을 강제하면서 이 예외가 없으면 문서와 실제가 어긋난다.

**다만 이 ID는 이 조직에서 실제로 확인하지 못했다.** `admin:org` 스코프가 없어 조직 ruleset을 읽을 수 없었다. 적용 후 아래로 반드시 확인한다:

```bash
RULESET_ID=$(GH_HOST=github.com gh api repos/mash-up-kr/TMT-BE/rulesets -q '.[] | select(.name=="main") | .id')
GH_HOST=github.com gh api repos/mash-up-kr/TMT-BE/rulesets/$RULESET_ID \
  -q '.bypass_actors[] | "\(.actor_type) id=\(.actor_id) mode=\(.bypass_mode)"'
```

Settings → Rules → main → Bypass list에 **"Repository admin"** 으로 보이면 맞다. 다른 역할로 보이면 ID를 고쳐서 이 파일도 같이 갱신한다.

## ruleset 밖의 설정

`delete_branch_on_merge`는 ruleset이 아니라 레포 설정이다. BRANCHING.md §4의 "머지 후 작업 브랜치 삭제(자동 삭제 설정)"가 현재 **꺼져 있다**:

```bash
GH_HOST=github.com gh api -X PATCH repos/mash-up-kr/TMT-BE -f delete_branch_on_merge=true
```

`allow_merge_commit` / `allow_rebase_merge`는 ruleset의 `allowed_merge_methods`가 main에 대해 막아주므로 레포 설정까지 끌 필요는 없다. 다만 UI에서 회색 버튼을 보고 헷갈릴 수 있으니 꺼두는 편이 낫다.

## 검증

적용했다고 끝이 아니다. 실제로 확인한다:

- approve 없는 PR에서 머지 버튼이 막히는지
- merge commit / rebase 버튼이 사라지는지
- `git push origin main`이 거부되는지
