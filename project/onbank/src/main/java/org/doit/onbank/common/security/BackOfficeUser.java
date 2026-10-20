package org.doit.onbank.common.security;

// 로그인한 직원·관리자의 공통 정보
// 활동 로그(admin_log), 알림함(admin_notification)은 직원과 관리자를 "구분 + ID"로 기록하므로
// 두 로그인 정보(EmployeePrincipal, AdminPrincipal)를 이 인터페이스 하나로 다룰 수 있게 한다
// 예) adminLogService.record(me, ...)
// 임시 비밀번호 여부(isTempPassword)는 고객과 함께 쓰는 TempPasswordUser 에 있다
public interface BackOfficeUser extends TempPasswordUser {

	ActorType getActorType();

	Long getActorId();	// employee_id 또는 admin_id

	String getName();
}
