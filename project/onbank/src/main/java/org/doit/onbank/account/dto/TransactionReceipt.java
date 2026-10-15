package org.doit.onbank.account.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import org.doit.onbank.account.entity.TransactionHistory;

// 입출금 완료 후 화면에 보여 줄 영수증
// 리다이렉트(PRG) 후에도 보이도록 flash 속성(세션)에 담으므로 Serializable
public record TransactionReceipt(
		String transactionType,
		long amount,
		long balanceAfter,
		String content,
		LocalDateTime transactionAt,
		String transactionSqno) implements Serializable {

	public static TransactionReceipt from(TransactionHistory history) {
		return new TransactionReceipt(
				history.getTransactionType(),
				history.getTransactionAmount(),
				history.getBalanceAfterTransaction(),
				history.getTransactionContent(),
				history.getTransactionAt(),
				history.getTransactionSqno());
	}
}
