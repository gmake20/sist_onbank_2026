package org.doit.onbank.member.entity;

// members.status
// LOCKED는 비밀번호 5회 오류로 시스템이 자동으로 거는 상태 (ACTIVE일 때만). 해제하면 ACTIVE로 돌아간다
public enum MemberStatus {
	ACTIVE,		// 정상
	LOCKED,		// 로그인 잠금 (비밀번호 5회 오류)
	SUSPENDED,	// 정지
	WITHDRAWN	// 탈퇴
}
