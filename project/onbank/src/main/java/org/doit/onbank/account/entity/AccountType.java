package org.doit.onbank.account.entity;

// account.account_type 에 저장하는 값
// 엔티티 필드는 String 이므로 문자열을 직접 쓰지 말고 이 상수를 사용한다
public final class AccountType {

	public static final String CHECKING = "CHECKING";	// 입출금
	public static final String DEPOSIT = "DEPOSIT";		// 예금
	public static final String SAVINGS = "SAVINGS";		// 적금
	public static final String LOAN = "LOAN";			// 대출

	private AccountType() {
	}
}
