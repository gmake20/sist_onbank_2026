package org.doit.onbank.admin.security;

import java.time.LocalDateTime;

import org.doit.onbank.admin.entity.Admin;
import org.doit.onbank.admin.entity.AdminLoginLog;
import org.doit.onbank.admin.repository.AdminLoginLogRepository;
import org.doit.onbank.admin.repository.AdminRepository;
import org.doit.onbank.common.security.LoginAttemptRecorder;
import org.doit.onbank.common.security.LoginFailReason;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 관리자 로그인 결과 기록 (admin.login_fail_count, status = LOCKED, last_login_at)
// 로그인 이력 : 성공·실패마다 admin_login_log 에 한 줄 (recordHistory)
// 잠금 해제는 관리자 파트의 계정 잠금 해제 화면에서 Admin.unlock() 호출
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminLoginAttemptRecorder implements LoginAttemptRecorder {

	private final AdminRepository adminRepository;
	private final AdminLoginLogRepository adminLoginLogRepository;

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

	// 비밀번호 오류인데 아이디가 없으면 UNKNOWN_ID 로 바꿔 남긴다 (화면 안내는 구분하지 않음)
	// 이력 저장에 실패해도 로그인 처리는 계속한다 → 예외는 로그만 남긴다
	// 핸들러는 트랜잭션 밖에서 부르므로 save()가 자체 트랜잭션으로 바로 저장된다
	@Override
	public void recordHistory(String loginId, LoginFailReason failReason, HttpServletRequest request) {
		try {
			Long adminId = (loginId == null || loginId.isBlank()) ? null
					: adminRepository.findByLoginId(loginId).map(Admin::getAdminId).orElse(null);
			if (adminId == null && failReason == LoginFailReason.BAD_CREDENTIALS) {
				failReason = LoginFailReason.UNKNOWN_ID;
			}
			adminLoginLogRepository.save(AdminLoginLog.builder()
					.adminId(adminId)
					.loginIdInput(loginId)
					.success(failReason == null)
					.failReason(failReason == null ? null : failReason.name())
					.ipAddress(request.getRemoteAddr())
					.userAgent(request.getHeader("User-Agent"))
					.build());
		} catch (RuntimeException e) {
			log.error("관리자 로그인 이력 저장 실패 loginId={}", loginId, e);
		}
	}
}
