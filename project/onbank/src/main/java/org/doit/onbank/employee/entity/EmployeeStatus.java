package org.doit.onbank.employee.entity;

// employee.status : ACTIVE가 아니면 로그인 불가
// LOCKED는 비밀번호 5회 오류로 시스템이 자동으로 거는 상태 (ACTIVE일 때만). 해제하면 ACTIVE로 돌아간다
public enum EmployeeStatus {
	ACTIVE,		// 재직
	LOCKED,		// 로그인 잠금 (비밀번호 5회 오류)
	ON_LEAVE,	// 휴직
	RETIRED		// 퇴사 (retired_at 필수)
}
