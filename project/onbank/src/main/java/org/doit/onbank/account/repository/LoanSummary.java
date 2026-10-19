package org.doit.onbank.account.repository;

import java.math.BigDecimal;

// 자산 리포트 : 승인된 대출 1건 (팀3 loan_application + loan_repayment_schedule 조회만)
// 날짜는 DB 종류에 따라 반환 타입이 달라지지 않게 'yyyy-MM-dd' 문자열로 받는다
public interface LoanSummary {

	Long getApplicationId();

	String getProductName();

	Long getApprovedAmount();			// 승인 금액 (원금)

	BigDecimal getInterestRate();		// 승인 금리 (연 %)

	String getMaturityDate();			// 만기일

	Long getRepaidPrincipal();			// 갚은 원금 (상환 스케줄에서 납부한 회차의 원금 합계)

	String getNextDueDate();			// 다음 납부일 (없으면 null)
}
