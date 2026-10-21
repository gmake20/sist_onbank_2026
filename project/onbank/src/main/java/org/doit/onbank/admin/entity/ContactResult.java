package org.doit.onbank.admin.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// fds_handling_history.contact_result + 화면 이름 (와이어프레임 S-16 판단 결과)
@Getter
@RequiredArgsConstructor
public enum ContactResult {
    CONFIRMED_BY_CUSTOMER("본인 거래 확인"),   // 고객 본인 거래 확인
    NOT_CUSTOMER("본인 아님"),                 // 고객이 한 거래 아님
    NO_ANSWER("연락 안 됨");                   // 연락 안 됨

    private final String label;
}
