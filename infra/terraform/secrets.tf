# DB 비밀번호는 tfvars에 두지 않는다. Terraform이 생성해서 SSM에만 넣고,
# 인스턴스는 부팅 시 자기 IAM 역할로 읽어간다.
#
# 주의: 이 값은 Terraform state에 평문으로 남는다. state를 로컬에 두거나
# 커밋하면 안 되는 이유이며, versions.tf의 S3 백엔드(encrypt=true)가 전제다.
resource "random_password" "db" {
  length  = 32
  special = false # URL/셸 인용 사고를 피한다. 32자 영숫자면 엔트로피는 충분하다.
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
