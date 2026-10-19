package org.doit.onbank.loan.entity;

public enum LoanApplicationStatus {

	   PENDING,            // 신청 접수
	   REVIEWING,          // 심사 진행
	   ADDITIONAL_INFO,    // 추가 서류 요청
	   APPROVED,           // 승인
	   REJECTED,           // 거절
	   CANCELLED,          // 고객 취소
	   EXPIRED,            // 승인 유효기간 경과
	   EXECUTED			   // 대출금 입금
}
