package org.doit.onbank.account.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

// 이체 완료 후 결과 화면에 보여 줄 영수증
// 리다이렉트(PRG) 후에도 보이도록 flash 속성(세션)에 담으므로 Serializable
public record TransferReceipt(
		String fromAccountNumber,
		String toAccountNumber,
		String recipientName,		// 가린 이름 (예: 홍*동)
		long amount,
		long balanceAfter,			// 출금 계좌의 이체 후 잔액
		String senderMemo,			// 내 통장 표시
		String receiverMemo,		// 받는 분 통장 표시
		LocalDateTime transferAt,
		String transactionSqno) implements Serializable {
}
