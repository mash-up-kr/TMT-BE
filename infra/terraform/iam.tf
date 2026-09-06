# 아래 db_secret_read 정책이 SSM 파라미터 ARN을 조립할 때 계정 ID가 필요하다.
data "aws_caller_identity" "current" {}

# SSM Session Manager용 역할. SSH 키 없이 콘솔/CLI로 셸을 열 수 있어
# was_ssh_cidrs·db_ssh_cidrs를 빈 목록으로 두는 것이 기본값일 수 있는 근거다.
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

# 인스턴스가 배포 시 앱 설정을 SSM Parameter Store에서 직접 읽는다.
# user_data나 이미지에 평문으로 박지 않기 위한 것 — user_data는 인스턴스 메타데이터로
# 조회 가능하므로 시크릿을 넣으면 안 된다.
#
# DB 인스턴스는 password만, WAS는 host/name/user까지 읽어야 하므로 경로 단위로 준다.
# 두 인스턴스가 프로파일을 공유하는 결과로 WAS도 password를 읽을 수 있는데,
# 어차피 접속하려면 필요한 값이라 분리 실익이 없다.
#
# **배포 스크립트가 읽는 경로를 전부 열어야 한다.** 빠지면 `aws ssm get-parameter`가
# AccessDenied로 끝나고, 명령 치환이라 배포는 성공한 채 .env에 빈 값이 들어간다 —
# 앱이 기동은 되고 그 기능만 죽는다 (TMT-252). 워크플로의 SSM_*_PREFIX와 같이 움직인다.
data "aws_iam_policy_document" "db_secret_read" {
  statement {
    actions = ["ssm:GetParameter", "ssm:GetParameters"]
    resources = [
      # /db/*      DB 접속 정보 (ci-push.yml · cicd-release.yml)
      # /media/*   미디어 버킷·조회 base URL (TMT-201)
      # /address/* juso 승인키·addressId 서명키 (TMT-187)
      # /ai/*      Groq·Gemini 요약 키 (TMT-232) — 키 등록 완료로 이번에 연다
      # /sentry/*  Sentry DSN (TMT-325) — 온콜 봇이 폴링할 에러 수집처
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${local.name}/db/*",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${local.name}/media/*",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${local.name}/address/*",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${local.name}/ai/*",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter/${local.name}/sentry/*",
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

# 스왑·메모리 지표 발행 (TMT-303). 네임스페이스를 TMT/Memory로 못박아 다른 지표를 못 만든다.
data "aws_iam_policy_document" "metrics_put" {
  statement {
    actions   = ["cloudwatch:PutMetricData"]
    resources = ["*"]

    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = ["TMT/Memory"]
    }
  }
}

resource "aws_iam_role_policy" "metrics_put" {
  name   = "${local.name}-metrics-put"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.metrics_put.json
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
