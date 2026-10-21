package org.doit.onbank.common.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 로그인 이력(admin_login_log.fail_reason 등)에 남기는 실패 사유
// LoginFailureHandler가 Spring Security 예외를 보고 정한다 (UNKNOWN_ID는 recorder가 계정을 찾지 못했을 때 바꾼다)
@Getter
@RequiredArgsConstructor
public enum LoginFailReason {
	BAD_CREDENTIALS("비밀번호 오류"),
	LOGIN_LOCKED("잠긴 계정"),		// 이미 잠긴 계정 (5회 오류)
	INACTIVE("사용 불가 계정"),		// 정지·휴직·퇴사·비활성 계정
	UNKNOWN_ID("없는 아이디");

	private final String label;	// 화면 표시용 (로그인 이력 조회)

	// DB에 문자열로 저장된 값 → 화면 표시. 알 수 없는 값이면 그대로
	public static String labelOf(String name) {
		if (name == null) {
			return null;
		}
		for (LoginFailReason r : values()) {
			if (r.name().equals(name)) {
				return r.label;
			}
		}
		return name;
	}
}
