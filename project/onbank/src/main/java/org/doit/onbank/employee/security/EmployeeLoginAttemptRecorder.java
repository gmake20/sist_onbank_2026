package org.doit.onbank.employee.security;

import java.time.LocalDateTime;

import org.doit.onbank.common.security.LoginAttemptRecorder;
import org.doit.onbank.employee.repository.EmployeeRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 직원 로그인 결과 기록 (employee.login_fail_count, login_locked_at)
// 잠금 해제는 관리자 파트의 계정 잠금 해제 화면에서 Employee.unlockLogin() 호출
@Component
@RequiredArgsConstructor
public class EmployeeLoginAttemptRecorder implements LoginAttemptRecorder {

	private final EmployeeRepository employeeRepository;

	@Override
	@Transactional
	public boolean recordFailure(String employeeNo) {
		return employeeRepository.findByEmployeeNo(employeeNo)
				.map(employee -> employee.recordLoginFailure(MAX_FAIL_COUNT, LocalDateTime.now()))
				.orElse(false);
	}

	@Override
	@Transactional
	public void recordSuccess(String employeeNo) {
		employeeRepository.findByEmployeeNo(employeeNo).ifPresent(employee -> employee.recordLoginSuccess());
	}
}
