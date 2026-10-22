package org.doit.onbank.admin.dto;

import java.time.LocalDateTime;

// FDS AI 보조 판정 결과 1건 (D-72). 점수·탐지 여부에는 안 들어가고 모니터링 화면에 참고로만 보인다
// ok = false 면 "AI 판정 없음" + failReason (키 없음 / 시간 초과 / 응답 오류)
// pattern = 닮은 수법 화면 이름, 표시 기준 미만이거나 "해당 없음"이면 null
// memo = 보이스피싱 표현으로 본 메모 원문 (이체만), 표시 기준 미만이면 null
// 확률은 % 정수 (화면 표시용)
public record FdsAiResult(boolean ok, String failReason,
                          String pattern, Integer patternPercent,
                          String memo, Integer memoPercent,
                          String model, LocalDateTime judgedAt) {

    public static FdsAiResult failed(String reason, LocalDateTime now) {
        return new FdsAiResult(false, reason, null, null, null, null, null, now);
    }
}
