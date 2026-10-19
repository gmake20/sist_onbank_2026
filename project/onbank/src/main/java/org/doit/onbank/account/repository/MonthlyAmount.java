package org.doit.onbank.account.repository;

// 자산 리포트 : 월별 금액 1개 (대출 상환액, 카드 사용액)
public interface MonthlyAmount {

	String getYm();			// 기준년월 'yyyyMM'

	Long getAmount();
}
