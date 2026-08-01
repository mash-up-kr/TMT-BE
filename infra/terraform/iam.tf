# SSM Session Manager용 역할. SSH 키 없이 콘솔/CLI로 셸을 열 수 있어
# ssh_allowed_cidrs를 빈 목록으로 두는 것이 기본값일 수 있는 근거다.
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "${local.name}-instance"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# 인스턴스가 부팅 시 DB 접속 정보를 SSM Parameter Store에서 직접 읽는다.
# user_data에 평문으로 박지 않기 위한 것 — user_data는 인스턴스 메타데이터로
# 조회 가능하므로 시크릿을 넣으면 안 된다.
#
# DB 인스턴스는 password만, WAS는 host/name/user까지 읽어야 하므로 경로 단위로 준다.
# 두 인스턴스가 프로파일을 공유하는 결과로 WAS도 password를 읽을 수 있는데,
# 어차피 접속하려면 필요한 값이라 분리 실익이 없다.
data "aws_iam_policy_document" "db_secret_read" {
  statement {
    actions = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = [
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${local.name}/db/*"
    ]
  }

  statement {
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "db_secret_read" {
  name   = "${local.name}-db-secret-read"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.db_secret_read.json
}

resource "aws_iam_instance_profile" "instance" {
  name = "${local.name}-instance"
  role = aws_iam_role.instance.name
}
