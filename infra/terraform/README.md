# TMT 인프라 (Terraform)

또맛또 백엔드 AWS 인프라. **콘솔로 먼저 만들지 않고 여기서 정의한 뒤 apply한다** (TMT-62).

## 구성

```
        인터넷
          │
      ┌───┴────┐  IGW
      │  VPC 10.0.0.0/16  (ap-northeast-2a 단일 AZ)
      │
      ├── 10.0.1.0/24  was  ── EC2 t3.small  [EIP]  :8080
      │                            │
      │                            │ 5432 (SG 참조로만 허용)
      │                            ▼
      └── 10.0.2.0/24  db   ── EC2 t3.small
                                   └── EBS gp3 20GiB  /mnt/pgdata  (prevent_destroy)
                                        └── docker: postgis/postgis:16-3.4
```

- **RDS를 쓰지 않는다** — PostgreSQL은 DB 인스턴스의 도커 컨테이너로 돈다 (TMT-61)
- **NAT Gateway·ALB·Multi-AZ 없음** — 비용 방침 (TMT-61)
- **WAS/DB 분리 2대 구성** — 재배포가 DB에 영향을 주지 않고 메모리도 분리된다

## 사전 준비

1. AWS 자격증명 (`aws configure` 또는 `AWS_PROFILE`)
2. state 버킷 최초 1회 생성:
   ```bash
   cd bootstrap && terraform init && terraform apply
   ```
3. 변수 파일:
   ```bash
   cp terraform.tfvars.example terraform.tfvars   # ssh_public_key 필수
   ```

## 실행

```bash
terraform init
terraform plan     # 리뷰 후
terraform apply
```

## 접속

SSH는 기본적으로 **전면 차단**(`was_ssh_cidrs = []` · `db_ssh_cidrs = []`)이다. 두 변수는 분리돼 있어서, 비상시 WAS에 본인 IP를 열어도 DB의 22번은 닫힌 채로 남는다. DB 서브넷은 퍼블릭이라 그 노출이 곧 인터넷 노출이므로 `db_ssh_cidrs`는 빈 목록을 유지한다.

**SSM은 편의 수단이 아니라 부팅 하드 의존이다.** `db.sh.tftpl`이 SSM에서 DB 비밀번호를 읽어오지 못하면 PostgreSQL이 아예 뜨지 않는다(재시도 30회 후 실패 처리). 아웃바운드를 조이거나 인스턴스 역할 권한을 줄일 때 이 경로를 먼저 확인해야 한다.

접속:

```bash
aws ssm start-session --target $(terraform output -json instance_ids | jq -r .was)
```

DB 비밀번호:

```bash
aws ssm get-parameter --name "$(terraform output -raw db_password_ssm_parameter)" \
  --with-decryption --query Parameter.Value --output text
```

## 알아둘 것

**state에 DB 비밀번호가 평문으로 들어간다.** S3 백엔드(`encrypt = true`)가 전제이고, 로컬 state 파일은 `.gitignore` 대상이다. `*.tfplan`도 마찬가지다.

**DB 비밀번호는 최초 `initdb`에만 적용된다.** postgres 엔트리포인트는 `POSTGRES_PASSWORD_FILE`을 PGDATA가 비어 있을 때만 읽는다. 데이터 볼륨은 영속되므로 `initdb`는 최초 1회만 돈다.

따라서 **SSM 값만 바꿔도 실제 DB 비밀번호는 바뀌지 않는다.** 회전하려면 두 곳을 함께 고쳐야 한다:

```bash
# 1) 컨테이너 안에서 실제 비밀번호를 바꾼다
docker exec postgres bash -c \
  'PGPASSWORD=$(cat /run/secrets/db_password) psql -U tmt -d tmt \
     -c "ALTER USER tmt PASSWORD '"'"'<새 비밀번호>'"'"'"'
# 2) SSM 파라미터도 같은 값으로 갱신한다
aws ssm put-parameter --name /tmt-prod/db/password --type SecureString \
  --value '<새 비밀번호>' --overwrite
```

`random_password.db`에는 데이터 볼륨 ID를 `keepers`로 걸어뒀다. 볼륨이 살아 있는 한 Terraform이 비밀번호를 재생성하지 않으므로, state 유실·`taint`·리소스 교체로 SSM과 실제 DB가 어긋나 락아웃되는 상황을 막는다. 볼륨을 새로 만드는 경우에만 새 비밀번호가 나오고, 그때는 `initdb`도 다시 돌아 짝이 맞는다.

**DB 데이터 볼륨은 `terraform destroy`로 지워지지 않는다.** `prevent_destroy = true`가 걸려 있다. 정말 지우려면 그 설정을 먼저 제거해야 한다. 의도적인 마찰이다.

**DB 서브넷은 퍼블릭이다.** NAT Gateway를 안 쓰기로 한 이상 아웃바운드 경로는 IGW뿐이다. VPC 인터페이스 엔드포인트라는 선택지도 있지만, 필요한 것만 세도 ECR 2개 + SSM 3개 + KMS 1개로 6개이고 엔드포인트당 시간 과금이 붙어 월 $45 정도가 된다. NAT Gateway와 비슷한 금액이라 실익이 없어서 쓰지 않는다. 인바운드는 보안그룹에서 WAS 보안그룹 소스의 5432만 허용하므로 외부에서 DB에 직접 닿을 수는 없다. 나중에 NAT를 도입하면 `db` 서브넷의 라우팅 테이블만 갈아끼우면 된다 — 그러라고 서브넷을 갈라뒀다.

## 백업 · 복구

RDS 자동 스냅샷이 없으므로 `pg_dump`를 직접 돌린다. EBS 분리(`prevent_destroy`)는 인스턴스 교체를 견디게 해줄 뿐이고, 볼륨이 깨지거나 데이터를 잘못 지운 경우의 복구 수단은 이 덤프뿐이다.

- DB 인스턴스의 systemd 타이머가 하루 1회(기본 KST 03:00) `pg_dump -Fc` → S3 업로드
- 경로: `s3://<backup_bucket_name>/pg/YYYY/MM/tmt-<타임스탬프>.dump`
- 보관 30일 (`backup_retention_days`), 이후 S3 lifecycle이 삭제
- 인스턴스 역할에 **`s3:PutObject`만** 준다. 삭제 권한이 없어 인스턴스가 털려도 기존 백업은 지워지지 않는다

동작 확인·수동 실행:

```bash
aws ssm start-session --target $(terraform output -json instance_ids | jq -r .db)
systemctl list-timers tmt-pg-backup.timer
sudo /usr/local/bin/tmt-pg-backup.sh      # 즉시 1회
journalctl -u tmt-pg-backup.service -n 50
```

복구:

```bash
aws s3 cp s3://<bucket>/pg/2026/08/tmt-<ts>.dump /var/tmp/restore.dump
docker cp /var/tmp/restore.dump postgres:/tmp/restore.dump
docker exec -it postgres bash -c \
  'PGPASSWORD=$(cat /run/secrets/db_password) pg_restore -U tmt -d tmt --clean --if-exists /tmp/restore.dump'
```

> `--clean`은 기존 객체를 지우고 덮어쓴다. 운영 DB에 그대로 쏘기 전에 빈 DB를 만들어 먼저 확인할 것.

## 아직 없는 것

- **배포 CI** — 릴리즈 태그 트리거 GitHub Actions (TMT-62). PR #4에 이미 구현돼 있으나 단일 인스턴스 전제라 2대 구성에 맞춰 손봐야 한다
- **릴리즈 가이드** — `docs/RELEASE.md` (PR #4)
- **복구 리허설** — 위 절차를 실제로 한 번 돌려봐야 백업이 있다고 말할 수 있다
