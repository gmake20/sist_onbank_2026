package org.doit.onbank.account.entity;

// transaction_history.transaction_type 에 저장하는 값
// 이체 1건은 보낸 계좌에 TRANSFER_OUT, 받은 계좌에 TRANSFER_IN 으로 1행씩 기록한다
public final class TransactionType {

	public static final String DEPOSIT = "DEPOSIT";				// 입금
	public static final String WITHDRAWAL = "WITHDRAWAL";		// 출금 (현금, 이체 한도와 별개)
	public static final String TRANSFER_OUT = "TRANSFER_OUT";	// 이체 보냄
	public static final String TRANSFER_IN = "TRANSFER_IN";		// 이체 받음

	private TransactionType() {
	}
}
