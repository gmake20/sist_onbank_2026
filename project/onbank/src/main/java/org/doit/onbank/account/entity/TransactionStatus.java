package org.doit.onbank.account.entity;

// transaction_history.transaction_status 에 저장하는 값
// 실패한 거래는 롤백되어 행이 남지 않으므로 지금은 COMPLETED 하나만 쓴다
public final class TransactionStatus {

	public static final String COMPLETED = "COMPLETED";	// 완료

	private TransactionStatus() {
	}
}
