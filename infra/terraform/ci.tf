# cicd-release.yml이 쓰는 배포 자격증명(리포지토리 시크릿의 IAM 사용자)에 붙일 정책.
# 사용자 자체는 자격증명 발급이 수반되는 리소스라 Terraform state에 두지 않는다 —
# 정책만 코드화하고 부착은 수동이다:
#
#   aws iam attach-user-policy --user-name <ci-user> \
#     --policy-arn "$(terraform output -raw ci_deploy_policy_arn)"
#
# SSH 시크릿(SERVER_HOST/SERVER_KEY)을 대체하는 구성이다. 배포는 SSM Run Command로
# 가고, 대상 인스턴스는 Name 태그로 찾는다 (TMT-107).
data "aws_iam_policy_document" "ci_deploy" {
  # ECR push
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.app.arn]
  }

  # 배포 대상 WAS를 Name 태그로 조회한다. Describe 계열은 리소스 단위 제한이 안 된다.
  statement {
    actions   = ["ec2:DescribeInstances"]
    resources = ["*"]
  }

  # Run Command는 문서와 인스턴스 양쪽 모두에 SendCommand 권한이 필요하다.
  # 인스턴스 쪽은 Name 태그 조건으로 WAS 한 대로 좁힌다 — 이 자격증명이 새어도
  # DB 인스턴스에는 명령을 못 보낸다.
  statement {
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ssm:${var.region}::document/AWS-RunShellScript"]
  }

  statement {
    actions   = ["ssm:SendCommand"]
    resources = ["arn:aws:ec2:${var.region}:${data.aws_caller_identity.current.account_id}:instance/*"]

    condition {
      test     = "StringEquals"
      variable = "ssm:resourceTag/Name"
      values   = ["${local.name}-was"]
    }
  }

  statement {
    # DescribeInstanceInformation — 배포 전 에이전트 생존 확인 (TMT-306). 리소스 단위 제한을 지원하지 않는다
    actions   = ["ssm:GetCommandInvocation", "ssm:DescribeInstanceInformation"]
    resources = ["*"]
  }
}

resource "aws_iam_policy" "ci_deploy" {
  name   = "${local.name}-ci-deploy"
  policy = data.aws_iam_policy_document.ci_deploy.json
}
