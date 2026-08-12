# 앱 이미지 저장소. cicd-release.yml이 push하고 WAS 인스턴스가 pull한다 (TMT-107).
# 이름은 워크플로의 ECR_REPOSITORY 값과 일치해야 한다.
resource "aws_ecr_repository" "app" {
  name = "tmt"

  # latest 단일 태그 운용(docs/RELEASE.md §5)이라 태그 덮어쓰기가 전제다.
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

# latest가 덮어써질 때마다 이전 이미지는 untagged로 남는다. 방치하면 스토리지
# 비용이 계속 늘므로 만료시킨다. 롤백은 roll-forward 방식(RELEASE.md §5)이라
# 옛 이미지를 오래 붙들 이유가 없지만, 배포 직후 원인 파악 여유로 며칠은 남긴다.
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "expire untagged images after 7 days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = { type = "expire" }
      }
    ]
  })
}

# WAS가 배포 시 이미지를 pull할 권한. GetAuthorizationToken은 리소스 단위
# 제한이 불가능해 *로 둔다 — 토큰 발급일 뿐 접근 범위는 아래 statement가 정한다.
data "aws_iam_policy_document" "ecr_pull" {
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [aws_ecr_repository.app.arn]
  }
}

resource "aws_iam_role_policy" "ecr_pull" {
  name   = "${local.name}-ecr-pull"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.ecr_pull.json
}
