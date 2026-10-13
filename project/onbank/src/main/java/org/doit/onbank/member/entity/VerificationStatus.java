package org.doit.onbank.member.entity;

// members.verification_status : 본인확인 상태
public enum VerificationStatus {
	NONE,		// 미확인
	PENDING,	// 심사중
	VERIFIED,	// 완료
	REJECTED	// 반려
}
