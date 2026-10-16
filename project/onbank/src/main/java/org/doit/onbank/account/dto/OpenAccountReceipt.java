package org.doit.onbank.account.dto;

import java.time.LocalDate;

import org.doit.onbank.account.entity.Account;

// 계좌 개설 결과 화면에 보여 줄 값 (리다이렉트 후 flash 로 전달)
public record OpenAccountReceipt(
		Long accountId,
		String accountNumber,
		String productName,
		String accountStatus,
		LocalDate openedDate) {

	public static OpenAccountReceipt from(Account account, String productName) {
		return new OpenAccountReceipt(account.getAccountId(), account.getAccountNumber(), productName,
				account.getAccountStatus(), account.getSignupDate());
	}
}
