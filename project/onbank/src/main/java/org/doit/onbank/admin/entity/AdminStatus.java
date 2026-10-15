package org.doit.onbank.admin.entity;

// admin.status
public enum AdminStatus {
	ACTIVE,		// 정상
	INACTIVE,	// 비활성 - 로그인 불가
	LOCKED		// 5회 실패 잠금 - 로그인 불가
}
