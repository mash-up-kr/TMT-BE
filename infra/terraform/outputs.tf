output "was_public_ip" {
  description = "WAS 고정 공인 IP (EIP). DNS·CI 배포 대상"
  value       = aws_eip.was.public_ip
}

output "was_private_ip" {
  value = aws_instance.was.private_ip
}

output "db_private_ip" {
  description = "DB 사설 IP. WAS는 이 주소로만 접속한다 (공인 접근 경로 없음)"
  value       = aws_instance.db.private_ip
}

output "instance_ids" {
  description = "SSM Session Manager 접속용 — aws ssm start-session --target <id>"
  value = {
    was = aws_instance.was.id
    db  = aws_instance.db.id
  }
}

output "db_password_ssm_parameter" {
  description = "DB 비밀번호가 저장된 SSM 파라미터 이름 (값은 --with-decryption으로 조회)"
  value       = aws_ssm_parameter.db_password.name
}

output "ecr_repository_url" {
  description = "앱 이미지 리포지토리. cicd-release.yml이 push, WAS가 pull"
  value       = aws_ecr_repository.app.repository_url
}

output "ci_deploy_policy_arn" {
  description = "CI 배포 IAM 사용자에 부착할 정책 — aws iam attach-user-policy 용"
  value       = aws_iam_policy.ci_deploy.arn
}

output "db_data_volume_id" {
  description = "DB 데이터 EBS 볼륨. prevent_destroy가 걸려 있어 terraform destroy로 지워지지 않는다"
  value       = aws_ebs_volume.db_data.id
}
