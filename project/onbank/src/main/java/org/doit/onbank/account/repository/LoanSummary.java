package org.doit.onbank.account.repository;

import java.math.BigDecimal;

// 자산 리포트 : 실행된 대출 1건 (팀3 loan_account + loan_repayment_schedule 조회만)
// 날짜는 DB 종류에 따라 반환 타입이 달라지지 않게 'yyyy-MM-dd' 문자열로 받는다
public interface LoanSummary {

	Long getLoanAccountId();

	String getProductName();

	Long getPrincipalAmount();			// 실행된 대출 원금

	Long getRemainingBalance();			// 남은 원금 (팀3가 상환할 때마다 줄인다)

	BigDecimal getInterestRate();		// 적용 금리 (연 %)

	String getMaturityDate();			// 만기일

	String getNextDueDate();			// 다음 납부일 (납부하지 않은 회차 중 가장 빠른 날, 없으면 null)
}
