package org.doit.onbank.account.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// account.account_status 에 저장하는 값 (Account 엔티티에서 @Enumerated(EnumType.STRING) 으로 이름 그대로 저장)
// 상태마다 입금·출금 가능 여부를 함께 둔다 → 상태가 늘어나도 이 파일만 고치면 된다
// 비교는 == 로 한다 : account.getAccountStatus() == AccountStatus.ACTIVE
@Getter
@RequiredArgsConstructor
public enum AccountStatus {

	ACTIVE("정상", true, true),			// 입금 O, 출금 O (비밀번호 오류 5회면 출금 X)
	PENDING("개설대기", false, false),	// 본인확인 심사 중 : 입금 X, 출금 X
	FROZEN("거래정지", true, false),		// 동결 (관리자 정지) : 입금 O, 출금 X
	CLOSED("해지", false, false);		// 해지 (개설 반려 포함) : 입금 X, 출금 X

	private final String label;			// 화면에 보여 줄 한글 이름
	private final boolean depositable;
	private final boolean withdrawable;
}
