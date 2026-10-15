package org.doit.onbank.account.entity;

// account.account_status 에 저장하는 값
// 엔티티 필드는 String 이므로 문자열을 직접 쓰지 말고 이 상수를 사용한다
public final class AccountStatus {

	public static final String ACTIVE = "ACTIVE";	// 정상: 입금 O, 출금 O (비밀번호 오류 5회면 출금 X)
	public static final String PENDING = "PENDING";	// 개설대기 (본인확인 심사 중): 입금 X, 출금 X
	public static final String FROZEN = "FROZEN";	// 동결 (관리자 정지): 입금 O, 출금 X
	public static final String CLOSED = "CLOSED";	// 해지 (개설 반려 포함): 입금 X, 출금 X

	private AccountStatus() {
	}
}
