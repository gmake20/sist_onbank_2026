package org.doit.onbank.admin.entity;

// fds_handling_history.contact_result
public enum ContactResult {
    CONFIRMED_BY_CUSTOMER,  // 고객 본인 거래 확인
    NOT_CUSTOMER,           // 고객이 한 거래 아님
    NO_ANSWER               // 연락 안 됨
}
