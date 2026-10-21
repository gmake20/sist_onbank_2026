package org.doit.onbank.account.repository;

// 자산 리포트 : 예적금 가입 1건 (팀3 savings_account + savings_product 조회만, 10-21 바꿈)
// 날짜는 'yyyy-MM-dd' 문자열로 받는다 (LoanSummary 와 같은 이유)
public interface SubscriptionSummary {

	String getProductName();

	String getProductType();			// FIXED_DEPOSIT 예금 / INSTALLMENT_SAVINGS 적금

	Long getPrincipalAmount();			// 예금 원금, 적금은 지금까지 넣은 금액

	Long getMonthlyPayment();			// 적금 월 납입액

	String getSubscribedAt();			// 가입일

	String getMaturityDate();			// 만기일

	Long getExpectedInterest();			// 예상 이자
}
