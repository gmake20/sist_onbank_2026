package org.doit.onbank.loan.entity;

public enum LoanAccountStatus {
    ACTIVE,   // 정상 대출
    OVERDUE,  // 연체
    CLOSED,   // 완납
    DEFAULTED // 부실 처리
}
