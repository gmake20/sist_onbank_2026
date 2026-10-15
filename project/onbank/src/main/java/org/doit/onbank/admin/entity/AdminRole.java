package org.doit.onbank.admin.entity;

// admin.role : 기본값 없음 (계정 생성 시 반드시 지정). 역할별 세부 권한은 팀원4가 정함
public enum AdminRole {
	SUPER_ADMIN,
	OPERATOR,
	SERVICE
}
