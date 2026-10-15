package org.doit.onbank.loan.entity;

public enum LoanApplicationStatus {

    PENDING,    // 신청 접수 / 심사 대기
    REVIEWING,  // 심사 중
    APPROVED,   // 승인
    REJECTED,   // 거절
    CANCELLED   // 신청 취소
}
