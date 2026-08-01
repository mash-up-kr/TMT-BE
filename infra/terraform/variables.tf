variable "region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "az" {
  description = <<-EOT
    사용할 단일 AZ. Multi-AZ는 쓰지 않는다(TMT-61 비용 방침).
    EBS 데이터 볼륨은 인스턴스와 같은 AZ에만 붙일 수 있으므로 두 인스턴스 모두 이 AZ에 둔다.
  EOT
  type        = string
  default     = "ap-northeast-2a"
}

variable "env" {
  description = "환경 이름. state key와 리소스 이름 접두사에 쓰인다"
  type        = string
  default     = "prod"
}

variable "vpc_cidr" {
  description = "VPC CIDR"
  type        = string
  default     = "10.0.0.0/16"
}

variable "was_subnet_cidr" {
  description = "WAS 서브넷 CIDR"
  type        = string
  default     = "10.0.1.0/24"
}

variable "db_subnet_cidr" {
  description = "DB 서브넷 CIDR"
  type        = string
  default     = "10.0.2.0/24"
}

variable "was_instance_type" {
  description = "WAS 인스턴스 타입. x86(t3) 고정 — TMT-61 아키텍처 결정"
  type        = string
  default     = "t3.small"
}

variable "db_instance_type" {
  description = "DB 인스턴스 타입"
  type        = string
  default     = "t3.small"
}

variable "db_data_volume_size" {
  description = "PostgreSQL 데이터용 EBS 볼륨 크기(GiB). 루트와 분리해 인스턴스 교체 시에도 보존된다"
  type        = number
  default     = 20
}

variable "root_volume_size" {
  description = "각 인스턴스 루트 볼륨 크기(GiB)"
  type        = number
  default     = 20
}

variable "ssh_public_key" {
  description = <<-EOT
    EC2에 등록할 SSH 공개키 (ssh-ed25519 AAAA... 형식).
    평상시 접속은 SSM Session Manager를 쓰고, SSH는 비상용으로만 남긴다.
  EOT
  type        = string
}

# SSH 허용 대역은 WAS와 DB를 따로 둔다.
# 하나로 공유하면 비상시 WAS에 본인 IP를 열어주는 순간 DB의 22번도 함께 열린다.
# DB 서브넷은 퍼블릭이라(NAT 미사용) 그 노출이 곧 인터넷 노출이다.
variable "was_ssh_cidrs" {
  description = <<-EOT
    WAS의 SSH(22)를 허용할 CIDR 목록. 기본값은 빈 목록 = 전면 차단.
    평상시 접속은 SSM Session Manager를 쓰고, 그래도 SSH가 필요할 때만 본인 IP/32를 넣는다.
    0.0.0.0/0 은 넣지 않는다.
  EOT
  type        = list(string)
  default     = []

  validation {
    condition     = !contains(var.was_ssh_cidrs, "0.0.0.0/0")
    error_message = "was_ssh_cidrs에 0.0.0.0/0을 넣을 수 없다. 전 세계에 SSH를 여는 설정이다."
  }
}

variable "db_ssh_cidrs" {
  description = <<-EOT
    DB의 SSH(22)를 허용할 CIDR 목록. 기본값 빈 목록을 유지하는 것을 강하게 권한다.
    DB 인스턴스는 SSM Session Manager로만 접속하고, SSH는 열지 않는다.
  EOT
  type        = list(string)
  default     = []

  validation {
    condition     = !contains(var.db_ssh_cidrs, "0.0.0.0/0")
    error_message = "db_ssh_cidrs에 0.0.0.0/0을 넣을 수 없다. DB를 전 세계에 여는 설정이다."
  }
}

variable "app_port" {
  description = "WAS가 리슨하는 포트"
  type        = number
  default     = 8080
}

variable "app_ingress_cidrs" {
  description = <<-EOT
    WAS 애플리케이션 포트를 열어줄 CIDR.
    ALB를 쓰지 않으므로(TMT-61 비용 방침) 현재는 인스턴스에 직접 붙는다.
    도그푸딩 단계에서 공개가 필요하면 0.0.0.0/0, 아니면 팀 IP만 남긴다.
  EOT
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "postgres_image" {
  description = <<-EOT
    PostgreSQL 컨테이너 이미지. PostGIS·pgvector 확장이 포함된 이미지를 쓴다(TMT-61).
    x86 전용 이미지여도 무방하다 — t3(x86) 고정이라 arm64 빌드 가용성 문제를 피한 것이 결정 이유다.
  EOT
  type        = string
  default     = "postgis/postgis:16-3.4"
}

variable "postgres_db" {
  description = "생성할 데이터베이스 이름"
  type        = string
  default     = "tmt"
}

variable "postgres_user" {
  description = "애플리케이션이 쓸 DB 계정명"
  type        = string
  default     = "tmt"
}

variable "backup_bucket_name" {
  description = "pg_dump 보관 S3 버킷. 이름은 전역 유니크여야 한다"
  type        = string
  default     = "tmt-db-backup"
}

variable "backup_retention_days" {
  description = "덤프 보관 일수. 지나면 S3 lifecycle이 지운다"
  type        = number
  default     = 30
}

variable "backup_schedule" {
  description = <<-EOT
    systemd OnCalendar 형식의 백업 주기. 기본은 매일 UTC 18:00 = KST 03:00.
    인스턴스 타임존이 UTC라 KST 기준으로 적으면 안 된다.
  EOT
  type        = string
  default     = "*-*-* 18:00:00"
}
