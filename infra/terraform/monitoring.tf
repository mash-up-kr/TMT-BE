# 스왑 관측 알람 (TMT-303). 알림 액션은 없다 — 상향 판단(TMT-298 재론)의 근거를 남기는 용도다.
# 기준은 README §스왑 관측: SwapUsedPercent >= 25%가 5분 지속이면 "관측", swap in/out이
# 상시 트래픽에서 주 2회 이상 반복되면 "교체 판단". 지표는 user_data의 tmt-swap-metrics.timer가 5분마다 올린다.
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
  treat_missing_data  = "notBreaching"
  alarm_description   = "스왑 사용률 25%(≈512MB) 이상 — 열화 관측 시작점. 반복되면 TMT-298(인스턴스 상향) 재론"
}
