package org.doit.onbank.common.security;

// 로그인 결과를 계정 테이블에 기록하는 창구 (영역마다 하나 : 고객·직원·관리자)
// LoginSuccessHandler / LoginFailureHandler가 이 인터페이스만 사용하므로
// 테이블마다 잠금 표시 방법이 달라도(login_locked_at / status = LOCKED) 핸들러는 하나로 쓴다
// 다음 단계 : 로그인 이력(login_log, employee_login_log, admin_login_log) 기록도 여기에 추가
public interface LoginAttemptRecorder {

	// 비밀번호를 이 횟수만큼 연속으로 틀리면 잠근다
	int MAX_FAIL_COUNT = 5;

	// 비밀번호 오류 기록. 이번 실패로 잠긴 상태가 되었으면 true
	// 없는 아이디면 아무것도 하지 않고 false
	boolean recordFailure(String username);

	// 로그인 성공 기록 (실패 횟수 초기화 등)
	void recordSuccess(String username);
}
