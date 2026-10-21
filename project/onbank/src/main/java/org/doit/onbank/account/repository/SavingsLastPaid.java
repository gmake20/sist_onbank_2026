package org.doit.onbank.account.repository;

// 전계좌조회 : 예적금 1건의 마지막 거래일 (팀3 savings_payment 조회만)
// 가입할 때 1회차가 기록되고 적금은 매달 납입할 때마다 1행씩 쌓이므로, 가장 늦은 paid_on 을 최종거래일로 쓴다
public interface SavingsLastPaid {

	Long getSavingsAccountId();

	String getLastPaidOn();		// 'yyyy.MM.dd' (화면 표시 형식으로 SQL 에서 바꿔 받는다)
}
