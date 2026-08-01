resource "aws_key_pair" "main" {
  key_name   = local.name
  public_key = var.ssh_public_key
}

resource "aws_security_group" "was" {
  name        = "${local.name}-was"
  description = "WAS instance"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-was" }
}

resource "aws_security_group" "db" {
  name        = "${local.name}-db"
  description = "PostgreSQL instance"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-db" }
}

# --- WAS 인바운드 ---

resource "aws_vpc_security_group_ingress_rule" "was_app" {
  for_each = toset(var.app_ingress_cidrs)

  security_group_id = aws_security_group.was.id
  description       = "app port"
  cidr_ipv4         = each.value
  from_port         = var.app_port
  to_port           = var.app_port
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "was_ssh" {
  for_each = toset(var.ssh_allowed_cidrs)

  security_group_id = aws_security_group.was.id
  description       = "ssh (emergency only)"
  cidr_ipv4         = each.value
  from_port         = 22
  to_port           = 22
  ip_protocol       = "tcp"
}

# --- DB 인바운드 ---
#
# 여기가 2대 구성의 핵심 방어선이다. 1대 구성이라면 DB가 도커 네트워크 안에만 있어
# 애초에 노출면이 없지만, 분리한 이상 5432가 VPC 네트워크에 뜬다.
# CIDR이 아니라 WAS 보안그룹을 소스로 지정해, WAS를 거치지 않은 접근은 전부 막는다.
resource "aws_vpc_security_group_ingress_rule" "db_postgres" {
  security_group_id            = aws_security_group.db.id
  description                  = "postgres from WAS only"
  referenced_security_group_id = aws_security_group.was.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "db_ssh" {
  for_each = toset(var.ssh_allowed_cidrs)

  security_group_id = aws_security_group.db.id
  description       = "ssh (emergency only)"
  cidr_ipv4         = each.value
  from_port         = 22
  to_port           = 22
  ip_protocol       = "tcp"
}

# --- 아웃바운드 ---
# 이미지 pull·패치·SSM 연결에 필요하다. 인바운드와 달리 열어둔다.

resource "aws_vpc_security_group_egress_rule" "was_all" {
  security_group_id = aws_security_group.was.id
  description       = "all outbound"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_vpc_security_group_egress_rule" "db_all" {
  security_group_id = aws_security_group.db.id
  description       = "all outbound"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}
