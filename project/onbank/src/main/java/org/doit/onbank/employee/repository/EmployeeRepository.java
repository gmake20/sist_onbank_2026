package org.doit.onbank.employee.repository;

import java.util.Optional;

import org.doit.onbank.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

	// 직원 로그인 : 사번으로 조회
	Optional<Employee> findByEmployeeNo(String employeeNo);

	// 직원 계정 발급 : 다음 사번을 만들기 위해 가장 큰 사번 조회 (퇴사자 포함, 사번은 재사용하지 않음)
	// 사번은 문자열이라 글자 순서로 비교한다. 형식을 "E" + 4자리 숫자(E0001)로 고정해야 숫자 순서와 같다
	// 예) findTopByEmployeeNoStartingWithOrderByEmployeeNoDesc("E") → E0003 → 다음 사번 E0004
	// 동시에 두 명을 발급하면 같은 사번이 나올 수 있으나 uk_employee_no 제약이 두 번째 저장을 막는다
	Optional<Employee> findTopByEmployeeNoStartingWithOrderByEmployeeNoDesc(String prefix);

	// 직원 계정 발급 : 이메일 중복 검사 (uk_employee_email)
	boolean existsByEmail(String email);
}
