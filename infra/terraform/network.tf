locals {
  name = "tmt-${var.env}"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.name }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = local.name }
}

# WAS·DB를 서브넷으로 분리한다. 지금은 둘 다 퍼블릭이지만, 나중에 NAT를 도입해
# DB만 프라이빗으로 내릴 때 서브넷 재설계 없이 라우팅 테이블만 바꾸면 되도록 갈라둔다.
resource "aws_subnet" "was" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.was_subnet_cidr
  availability_zone       = var.az
  map_public_ip_on_launch = true

  tags = { Name = "${local.name}-was" }
}

# DB 서브넷도 퍼블릭이다. NAT Gateway를 쓰지 않기로 한 이상(TMT-61 비용 방침)
# 컨테이너 이미지 pull·OS 패치·SSM 연결에 필요한 아웃바운드를 확보할 방법이 IGW뿐이다.
# 대신 인바운드는 보안그룹에서 WAS로부터의 5432만 허용한다 — security.tf 참고.
resource "aws_subnet" "db" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.db_subnet_cidr
  availability_zone       = var.az
  map_public_ip_on_launch = true

  tags = { Name = "${local.name}-db" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${local.name}-public" }
}

resource "aws_route_table_association" "was" {
  subnet_id      = aws_subnet.was.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table_association" "db" {
  subnet_id      = aws_subnet.db.id
  route_table_id = aws_route_table.public.id
}
