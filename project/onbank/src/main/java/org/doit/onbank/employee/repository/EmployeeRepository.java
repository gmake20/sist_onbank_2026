package org.doit.onbank.employee.repository;

import java.util.Optional;

import org.doit.onbank.employee.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

	// 직원 로그인 : 사번으로 조회
	Optional<Employee> findByEmployeeNo(String employeeNo);
}
