package org.doit.onbank.employee.security;

import org.doit.onbank.employee.repository.EmployeeRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

// POST /employee/login 시 호출 : 사번으로 직원을 찾아 EmployeePrincipal로 돌려준다
// SecurityConfig의 직원 필터 체인에만 연결됨 (고객·관리자 로그인에서는 사용하지 않음)
@Service
@RequiredArgsConstructor
public class EmployeeDetailsService implements UserDetailsService {

	private final EmployeeRepository employeeRepository;

	@Override
	public UserDetails loadUserByUsername(String employeeNo) throws UsernameNotFoundException {
		return employeeRepository.findByEmployeeNo(employeeNo)
				.map(EmployeePrincipal::new)
				.orElseThrow(() -> new UsernameNotFoundException("직원 없음: " + employeeNo));
	}
}
