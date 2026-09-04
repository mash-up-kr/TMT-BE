# 스왑 관측 알람 (TMT-303·336). 알림 액션은 없다 — 상향 판단(TMT-298 재론)의 근거를 남기는 용도다.
# 기준은 README §스왑 관측: SwapUsedPercent >= 25%가 5분 지속이면 "관측", swap out이
# 상시 트래픽에서 주 2회 이상 반복되면 "교체 판단". 지표는 user_data의 tmt-swap-metrics.timer가 5분마다 올린다.
#
# 둘 다 `treat_missing_data = "missing"`이다. `notBreaching`으로 두면 **지표 파이프라인이 죽어도
# 알람은 OK로 조용하다** — 타이머·IAM·IMDS 중 하나만 어긋나도 관측이 사라진 걸 모른다.
# `missing`이면 INSUFFICIENT_DATA로 드러나고, 그게 곧 "관측이 끊겼다"는 신호다 (PR #89 리뷰).
resource "aws_cloudwatch_metric_alarm" "swap_used" {
  for_each = toset(["was", "db"])

  alarm_name          = "${local.name}-${each.key}-swap-used-25pct"
  namespace           = "TMT/Memory"
  metric_name         = "SwapUsedPercent"
  dimensions          = { Role = each.key }
  statistic           = "Maximum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 25
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "missing"
  alarm_description   = "스왑 사용률 25%(≈512MB) 이상 — 열화 관측 시작점. 반복되면 TMT-298(인스턴스 상향) 재론"
}

# 사용률만으로는 첫 열화를 놓친다. 실측 첫 스왑아웃이 226페이지(≈0.9MB)였는데 사용률로는
# 0%로 반올림돼 25% 알람이 **침묵했다**. 밀려난 페이지가 있었다는 사실 자체가 신호이므로
# Sum > 0으로 잡는다 — 이 알람의 히스토리가 곧 "주 2회" 카운트다.
#
# SwapInPages는 알람을 걸지 않는다. 되읽기는 이미 밀려난 뒤의 결과라 out이 먼저 뜨고,
# 무료 한도(10개) 안에서 신호 대비 소음이 가장 낮은 하나만 고른다 — 지금 총 4개다.
resource "aws_cloudwatch_metric_alarm" "swap_out" {
  for_each = toset(["was", "db"])

  alarm_name          = "${local.name}-${each.key}-swap-out"
  namespace           = "TMT/Memory"
  metric_name         = "SwapOutPages"
  dimensions          = { Role = each.key }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_description   = "직전 5분에 스왑아웃 발생 — 양과 무관하게 밀려났다는 사실이 신호다. 주 2회 이상 반복되면 TMT-298 재론"
}
