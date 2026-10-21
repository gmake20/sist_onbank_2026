package org.doit.onbank.admin.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// fds_detection.rule_code 에 저장하는 값 (name()) + 화면 이름 (FDS-탐지규칙.md 2장)
// 이름은 detail 에 넣지 않고 여기서 붙인다 (4장)
@Getter
@RequiredArgsConstructor
public enum FdsRule {
    OTP_REISSUED("OTP 재발급 직후"),
    ELDERLY("고령자 계좌"),
    HIGH_ONCE("1회 고액"),
    HIGH_DAILY("1일 고액"),
    ABOVE_USUAL("평소보다 큰 금액"),
    RAPID_COUNT("짧은 시간 연속 거래"),
    NEW_RECIPIENT("처음 보내는 계좌"),
    IN_THEN_OUT("큰 입금 직후 출금"),
    DORMANT_WAKE("장기 미사용 후 출금");

    private final String label;
}
