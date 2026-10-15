package org.doit.onbank.employee.entity;

// employee.status : ACTIVE가 아니면 로그인 불가
public enum EmployeeStatus {
	ACTIVE,		// 재직
	ON_LEAVE,	// 휴직
	RETIRED		// 퇴사 (retired_at 필수)
}
