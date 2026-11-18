variable "region" {
  type    = string
  default = "ap-osaka-1"
}

variable "tenancy_ocid" {
  description = "무료 계정은 컴파트먼트를 따로 두지 않고 루트(테넌시)에 둔다."
  type        = string
}

variable "ssh_allowed_cidr" {
  description = "SSH(22)를 열어줄 주소. GitHub Actions가 SSH로 배포하므로 0.0.0.0/0이고, 서버는 키 인증만 받는다."
  type        = string
}

variable "vcn_cidr" {
  type    = string
  default = "10.10.0.0/16"
}

variable "public_subnet_cidr" {
  type    = string
  default = "10.10.1.0/24"
}

variable "availability_domain" {
  type    = string
  default = "MmHh:AP-OSAKA-1-AD-1"
}

variable "x86_image_id" {
  description = "Canonical-Ubuntu-22.04-2026.11.05-0 (x86). 두 마이크로를 만든 이미지다."
  type        = string
  default     = "ocid1.image.oc1.ap-osaka-1.aaaaaaaameswvptf7avimyxvs7upemg7fvfcbaps5zg4yxd5o6bk5fgspkqa"
}
