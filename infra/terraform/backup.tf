# RDS를 쓰지 않는 대가로 자동 백업이 없다. 이 파일이 그 자리를 메운다.
# 없으면 도그푸딩 데이터가 인스턴스와 함께 사라진다 (TMT-61).

resource "aws_s3_bucket" "backup" {
  bucket = var.backup_bucket_name

  # 백업 버킷을 destroy로 날리는 경로를 만들지 않는다.
  lifecycle {
    prevent_destroy = true
  }

  tags = { Name = "${local.name}-backup" }
}

resource "aws_s3_bucket_public_access_block" "backup" {
  bucket = aws_s3_bucket.backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# 덤프가 무한히 쌓이면 비용이 새므로 보관 기간을 강제한다.
resource "aws_s3_bucket_lifecycle_configuration" "backup" {
  bucket = aws_s3_bucket.backup.id

  rule {
    id     = "expire-old-dumps"
    status = "Enabled"

    filter {
      prefix = "pg/"
    }

    expiration {
      days = var.backup_retention_days
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 7
    }
  }
}

# DB 인스턴스가 덤프를 올릴 수 있게 한다. 쓰기만 주고 삭제 권한은 주지 않는다 —
# 인스턴스가 털렸을 때 백업까지 지워지는 시나리오를 막는다. 만료는 위 lifecycle이 처리한다.
data "aws_iam_policy_document" "backup_write" {
  statement {
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.backup.arn}/pg/*"]
  }

  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.backup.arn]
  }
}

resource "aws_iam_role_policy" "backup_write" {
  name   = "${local.name}-backup-write"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.backup_write.json
}
