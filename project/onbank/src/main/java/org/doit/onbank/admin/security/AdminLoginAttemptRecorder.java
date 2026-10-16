package org.doit.onbank.admin.security;

import java.time.LocalDateTime;

import org.doit.onbank.admin.repository.AdminRepository;
import org.doit.onbank.common.security.LoginAttemptRecorder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 관리자 로그인 결과 기록 (admin.login_fail_count, status = LOCKED, last_login_at)
// 잠금 해제는 관리자 파트의 계정 잠금 해제 화면에서 Admin.unlock() 호출
@Component
@RequiredArgsConstructor
public class AdminLoginAttemptRecorder implements LoginAttemptRecorder {

	private final AdminRepository adminRepository;

	@Override
	@Transactional
	public boolean recordFailure(String loginId) {
		return adminRepository.findByLoginId(loginId)
				.map(admin -> admin.recordLoginFailure(MAX_FAIL_COUNT))
				.orElse(false);
	}

	@Override
	@Transactional
	public void recordSuccess(String loginId) {
		adminRepository.findByLoginId(loginId).ifPresent(admin -> admin.recordLoginSuccess(LocalDateTime.now()));
	}
}
