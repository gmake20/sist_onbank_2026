package org.doit.onbank.common.security;

import java.util.function.Consumer;

import org.doit.onbank.admin.repository.AdminRepository;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.employee.repository.EmployeeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 직원·관리자 본인 비밀번호 변경 (임시 비밀번호 → 새 비밀번호, 또는 일반 변경)
// 형식 검사(@Valid)와 새 비밀번호 확인 일치는 컨트롤러에서 먼저 확인한다
@Service
@RequiredArgsConstructor
@Transactional
public class BackOfficePasswordService {

	private final EmployeeRepository employeeRepository;
	private final AdminRepository adminRepository;
	private final PasswordEncoder passwordEncoder;

	public void changeEmployeePassword(Long employeeId, PasswordChangeForm form) {
		var employee = employeeRepository.findById(employeeId)
				.orElseThrow(() -> new BusinessException("계정을 찾을 수 없습니다."));
		change(employee.getPassword(), form, employee::changePassword);
	}

	public void changeAdminPassword(Long adminId, PasswordChangeForm form) {
		var admin = adminRepository.findById(adminId)
				.orElseThrow(() -> new BusinessException("계정을 찾을 수 없습니다."));
		change(admin.getPassword(), form, admin::changePassword);
	}

	// 현재 비밀번호 확인 → 같은 비밀번호 재사용 금지 → 암호화해서 저장
	private void change(String savedHash, PasswordChangeForm form, Consumer<String> changePassword) {
		if (!passwordEncoder.matches(form.getCurrentPassword(), savedHash)) {
			throw new BusinessException("현재 비밀번호가 올바르지 않습니다.");
		}
		if (passwordEncoder.matches(form.getNewPassword(), savedHash)) {
			throw new BusinessException("현재 비밀번호와 다른 비밀번호를 입력하세요.");
		}
		changePassword.accept(passwordEncoder.encode(form.getNewPassword()));
	}
}
