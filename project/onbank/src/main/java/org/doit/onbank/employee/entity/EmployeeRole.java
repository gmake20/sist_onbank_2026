package org.doit.onbank.employee.entity;

// employee.role : MANAGER는 STAFF 권한을 포함 (SecurityConfig의 RoleHierarchy)
public enum EmployeeRole {
	STAFF,		// 일반 직원 : 조회, 본인확인 심사, 1:1 문의 답변
	MANAGER		// 책임자 : 승인 업무 (계좌 동결, 이체한도 증액, 대출 심사, 상품 관리)
}
