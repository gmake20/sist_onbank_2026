package org.doit.onbank.common.security;

// 로그인한 직원·관리자의 공통 정보
// 활동 로그(admin_log), 알림함(admin_notification)은 직원과 관리자를 "구분 + ID"로 기록하므로
// 두 로그인 정보(EmployeePrincipal, AdminPrincipal)를 이 인터페이스 하나로 다룰 수 있게 한다
// 예) adminLogService.record(me, ...)
public interface BackOfficeUser {

	ActorType getActorType();

	Long getActorId();	// employee_id 또는 admin_id

	String getName();

	// 임시 비밀번호(temp_pw_yn = 'Y')면 true → 비밀번호 변경 전에는 다른 화면을 쓸 수 없다 (TempPasswordFilter)
	boolean isTempPassword();
}
