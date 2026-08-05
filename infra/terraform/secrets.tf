# DB 비밀번호는 tfvars에 두지 않는다. Terraform이 생성해서 SSM에만 넣고,
# 인스턴스는 부팅 시 자기 IAM 역할로 읽어간다.
#
# 주의: 이 값은 Terraform state에 평문으로 남는다. state를 로컬에 두거나
# 커밋하면 안 되는 이유이며, versions.tf의 S3 백엔드(encrypt=true)가 전제다.
# 비밀번호 수명을 데이터 볼륨에 묶는다.
#
# postgres 엔트리포인트는 POSTGRES_PASSWORD_FILE 을 PGDATA가 비어 있을 때(initdb)만
# 읽는다. 그런데 데이터 볼륨은 prevent_destroy + db.sh.tftpl 의 blkid 가드로 영속되므로
# initdb는 최초 1회만 돈다. 이 리소스가 재생성되면 SSM에는 새 값이 들어가지만
# PostgreSQL은 옛 비밀번호를 계속 요구하고, 맞는 값이 어디에도 남지 않아 DB에 락아웃된다.
#
# keepers를 볼륨 ID에 걸어두면 볼륨이 살아 있는 한 재생성되지 않는다.
# 볼륨을 새로 만드는 경우에만 새 비밀번호가 나오고, 그때는 initdb도 다시 돌아 짝이 맞는다.
resource "random_password" "db" {
  length  = 32
  special = false # URL/셸 인용 사고를 피한다. 32자 영숫자면 엔트로피는 충분하다.

  keepers = {
    volume_id = aws_ebs_volume.db_data.id
  }
}

resource "aws_ssm_parameter" "db_password" {
  name        = "/${local.name}/db/password"
  description = "PostgreSQL password for ${var.postgres_user}"
  type        = "SecureString"
  value       = random_password.db.result
}

# 애플리케이션이 읽어갈 접속 정보. 비밀번호는 위 파라미터에서 따로 읽는다.
resource "aws_ssm_parameter" "db_host" {
  name  = "/${local.name}/db/host"
  type  = "String"
  value = aws_instance.db.private_ip
}

resource "aws_ssm_parameter" "db_name" {
  name  = "/${local.name}/db/name"
  type  = "String"
  value = var.postgres_db
}

resource "aws_ssm_parameter" "db_user" {
  name  = "/${local.name}/db/user"
  type  = "String"
  value = var.postgres_user
}
