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

  # 적재 반입 파일(data/)도 같은 원칙으로 만료한다 — DB에 들어간 뒤에는 원본 CSV에서
  # 언제든 재생성할 수 있는 전송용 사본이라, 백업(pg/)보다 짧게 둔다.
  rule {
    id     = "expire-ingest-files"
    status = "Enabled"

    filter {
      prefix = "data/"
    }

    expiration {
      days = 7
    }
  }
}

# DB 인스턴스가 덤프를 올릴 수 있게 한다. 쓰기만 주고 삭제 권한은 주지 않는다 —
# 실수나 스크립트 오작동으로 기존 백업이 지워지는 시나리오를 막는다 (같은 키 덮어쓰기까지
# 막지는 못한다). 만료는 위 lifecycle이 처리한다.
# AbortMultipartUpload: aws s3 cp는 8MiB를 넘으면 멀티파트로 올리고 실패 시 abort를
# 시도한다. 이 권한이 없으면 abort가 AccessDenied로 또 실패해 로그에 실제 실패 원인
# 대신 권한 에러가 남는다. 잔여 파트 정리는 lifecycle의 abort_incomplete_multipart_upload 몫.
data "aws_iam_policy_document" "backup_write" {
  statement {
    actions   = ["s3:PutObject", "s3:AbortMultipartUpload"]
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

# 데이터 적재 반입 경로 (TMT-163). SSM Run Command 페이로드 한도(8KB) 때문에
# 적재 파일(수십 MB TSV)을 명령에 실을 수 없어 S3를 경유한다 — 로컬에서
# data/ 프리픽스에 올리면 DB 인스턴스가 내려받는다 (scripts/place-pipeline/README.md).
# 읽기만 준다: pg/(백업)는 읽을 수 없어 덤프 유출 경로가 되지 않고,
# data/에 쓰기 권한이 없어 인스턴스가 반입 파일을 위조할 수도 없다.
data "aws_iam_policy_document" "data_read" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.backup.arn}/data/*"]
  }
}

resource "aws_iam_role_policy" "data_read" {
  name   = "${local.name}-data-read"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.data_read.json
}
