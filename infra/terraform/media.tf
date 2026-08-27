# 미디어(리뷰 사진) 업로드·조회 (TMT-201)
#
# 업로드 방식은 TMT-99에서 확정 — 앱이 presigned PUT URL을 발급하고(M1) 클라이언트가
# 브라우저에서 S3로 직접 올린다. 백업 버킷과 분리한다: 수명주기(백업은 30일 만료,
# 미디어는 영구)·공개 정책(백업은 전면 차단, 미디어는 공개 읽기)이 다르다.
#
# 조회는 공개 읽기(GetObject만 허용)다 — CloudFront는 비용 방침(TMT-61)과 UT2 일정상
# 도입하지 않고, 실도메인·캐싱이 필요해지는 시점에 별도 티켓으로 다룬다.
# 키가 UUID 기반이라(M2, media_asset.s3_key) 열거로 훑는 공격면은 제한적이다.

resource "aws_s3_bucket" "media" {
  bucket = var.media_bucket_name

  # 리뷰 사진은 원본이 이 버킷에만 있다 — 백업 버킷보다 destroy 보호가 더 필요하다.
  lifecycle {
    prevent_destroy = true
  }

  tags = { Name = "${local.name}-media" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# ACL은 전면 차단하고(레거시), 공개는 아래 버킷 정책 한 장으로만 연다.
resource "aws_s3_bucket_public_access_block" "media" {
  bucket = aws_s3_bucket.media.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

# 공개는 GetObject 하나뿐이다 — 목록(ListBucket)은 열지 않아 키를 모르면 훑을 수 없다.
resource "aws_s3_bucket_policy" "media_public_read" {
  bucket = aws_s3_bucket.media.id
  policy = data.aws_iam_policy_document.media_public_read.json

  depends_on = [aws_s3_bucket_public_access_block.media]
}

data "aws_iam_policy_document" "media_public_read" {
  statement {
    sid       = "PublicReadGetObject"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.media.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}

# 클라이언트가 브라우저에서 presigned URL로 직접 PUT 한다 — 오리진 목록은 앱 CORS
# (WebConfig.ALLOWED_ORIGIN_PATTERNS)와 같은 문자열로 맞춘다. Vercel 프리뷰 URL이
# <project>-<hash>-<scope>.vercel.app 형태라 와일드카드 1개로 스코프까지 고정된다.
resource "aws_s3_bucket_cors_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  cors_rule {
    allowed_origins = [
      "http://localhost:3000",
      "https://ttomatto-web.vercel.app",
      "https://ttomatto-*-ttalkkakfe.vercel.app",
    ]
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3600
  }
}

# 업로드가 끊긴 멀티파트 잔여물만 정리한다. STAGED 미첨부 객체의 TTL 정리(M4)는
# media_asset 테이블 기준으로 앱이 판단할 일이라 여기서 만료 규칙을 걸지 않는다.
resource "aws_s3_bucket_lifecycle_configuration" "media" {
  bucket = aws_s3_bucket.media.id

  rule {
    id     = "abort-incomplete-uploads"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# presigned PUT 발급 권한. presigned URL은 발급자 자격증명의 권한을 그대로 쓰므로
# 역할에 PutObject가 있어야 클라이언트의 PUT이 통과한다. 조회는 공개 읽기라 GetObject를
# 주지 않는다. DeleteObject는 M4 TTL 정리와 리뷰 삭제가 S3 객체를 실제로 걷어내야 해서
# 준다 — presigned로 나가는 것은 서명된 PUT뿐이라 이 권한이 클라이언트에 노출되지 않는다.
# instance 역할은 WAS·DB가 공유한다(기존 구조) — 분리는 인스턴스 프로필 교체가 따라와
# 별도 티켓 감이고, DB 인스턴스에 PutObject가 더 열리는 것을 알고 감수한다.
data "aws_iam_policy_document" "media_write" {
  statement {
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.media.arn}/*"]
  }
}

resource "aws_iam_role_policy" "media_write" {
  name   = "${local.name}-media-write"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.media_write.json
}

# 앱이 버킷 이름·조회 base URL을 받는 경로 — DB 접속 정보(/tmt-prod/db/*)와 같은 방식.
# 배포 스크립트가 읽어 .env로 내려준다 (ci-push.yml · cicd-release.yml).
resource "aws_ssm_parameter" "media_bucket" {
  name  = "/${local.name}/media/bucket"
  type  = "String"
  value = aws_s3_bucket.media.bucket
}

resource "aws_ssm_parameter" "media_base_url" {
  name  = "/${local.name}/media/base-url"
  type  = "String"
  value = "https://${aws_s3_bucket.media.bucket}.s3.${var.region}.amazonaws.com"
}
