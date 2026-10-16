package org.doit.onbank.member.security;

import java.time.LocalDateTime;

import org.doit.onbank.common.security.LoginAttemptRecorder;
import org.doit.onbank.member.repository.MemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

// 고객 로그인 결과 기록 (members.login_fail_count, login_locked_at)
// 잠금 해제는 다음 단계 : 비밀번호 재설정(휴대폰 인증) 또는 직원 회원 관리 화면
@Component
@RequiredArgsConstructor
public class MemberLoginAttemptRecorder implements LoginAttemptRecorder {

	private final MemberRepository memberRepository;

	@Override
	@Transactional
	public boolean recordFailure(String loginId) {
		return memberRepository.findByLoginId(loginId)
				.map(member -> member.recordLoginFailure(MAX_FAIL_COUNT, LocalDateTime.now()))
				.orElse(false);
	}

	@Override
	@Transactional
	public void recordSuccess(String loginId) {
		memberRepository.findByLoginId(loginId).ifPresent(member -> member.recordLoginSuccess());
	}
}
