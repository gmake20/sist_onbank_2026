package org.doit.onbank.common.security;

// 백오피스 작업자 구분 : admin_log.actor_type, admin_notification.recipient_type 에 저장되는 값
public enum ActorType {
	ADMIN,		// admin 테이블 (/admin/**)
	EMPLOYEE	// employee 테이블 (/employee/**)
}
