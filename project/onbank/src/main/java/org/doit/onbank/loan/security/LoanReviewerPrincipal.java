package org.doit.onbank.loan.security;

/** 직원 로그인 구현 시 직원 Principal이 이 인터페이스를 구현하도록 연결합니다. */
public interface LoanReviewerPrincipal {
    Long getEmployeeId();
}
