package org.doit.onbank.account.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// account.account_type 에 저장하는 값 (Account 엔티티에서 @Enumerated(EnumType.STRING) 으로 이름 그대로 저장)
// 테스트 계좌는 account_type 이 NULL 일 수 있다 → 화면·집계에서는 입출금으로 본다
// 비교는 == 로 한다 : account.getAccountType() == AccountType.CHECKING
@Getter
@RequiredArgsConstructor
public enum AccountType {

	CHECKING("입출금"),
	DEPOSIT("예금"),
	SAVINGS("적금"),
	LOAN("대출");

	private final String label;		// 화면에 보여 줄 한글 이름
}

