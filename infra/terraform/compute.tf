data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-kernel-6.1-x86_64"]
  }
}

resource "aws_instance" "was" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.was_instance_type
  subnet_id              = aws_subnet.was.id
  vpc_security_group_ids = [aws_security_group.was.id]
  key_name               = aws_key_pair.main.key_name
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  user_data = file("${path.module}/user_data/was.sh")

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_tokens = "required" # IMDSv2 강제 — SSRF로 인스턴스 자격증명이 새는 경로를 막는다
  }

  tags = { Name = "${local.name}-was" }
}

resource "aws_instance" "db" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = var.db_instance_type
  subnet_id              = aws_subnet.db.id
  vpc_security_group_ids = [aws_security_group.db.id]
  key_name               = aws_key_pair.main.key_name
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  user_data = templatefile("${path.module}/user_data/db.sh.tftpl", {
    region         = var.region
    volume_id      = aws_ebs_volume.db_data.id
    postgres_image = var.postgres_image
    postgres_db    = var.postgres_db
    postgres_user  = var.postgres_user
    password_param = aws_ssm_parameter.db_password.name
  })

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true
  }

  metadata_options {
    http_tokens = "required"
  }

  tags = { Name = "${local.name}-db" }
}

# 데이터는 루트 볼륨이 아니라 별도 EBS에 둔다. 인스턴스를 갈아엎어도(타입 변경,
# AMI 교체, terraform taint) 데이터가 따라 죽지 않게 하기 위한 것 —
# RDS 자동 백업이 없는 구성이라 이 분리가 사실상 1차 방어선이다.
resource "aws_ebs_volume" "db_data" {
  availability_zone = var.az
  size              = var.db_data_volume_size
  type              = "gp3"
  encrypted         = true

  tags = { Name = "${local.name}-db-data" }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_volume_attachment" "db_data" {
  device_name = "/dev/sdf"
  volume_id   = aws_ebs_volume.db_data.id
  instance_id = aws_instance.db.id

  # 기본값(false)이면 인스턴스가 살아있는 상태에서 detach가 막혀 apply가 멈출 수 있다.
  # 다만 마운트된 채로 강제 분리되면 파일시스템이 깨질 수 있으므로,
  # 인스턴스 교체 전에는 DB 컨테이너를 먼저 내리는 것이 안전하다.
  stop_instance_before_detaching = true
}

# WAS는 재부팅·교체와 무관하게 주소가 고정돼야 DNS·CI 배포 대상이 흔들리지 않는다.
# DB는 EIP를 주지 않는다 — WAS가 사설 IP로만 붙기 때문이다.
resource "aws_eip" "was" {
  instance = aws_instance.was.id
  domain   = "vpc"

  tags = { Name = "${local.name}-was" }
}
