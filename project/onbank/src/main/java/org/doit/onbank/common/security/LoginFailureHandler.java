package org.doit.onbank.common.security;

import java.io.IOException;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 로그인 실패 : 비밀번호 오류 횟수를 기록하고, 실패 사유에 맞는 안내와 함께 로그인 화면으로 보낸다
// - 비밀번호 오류 / 없는 아이디 → ?error   (둘을 구분하지 않음. 없는 아이디는 Spring이 BadCredentials로 바꿔 줌)
//   단, 이번 실패로 5회가 되어 잠겼으면 → ?locked
// - 이미 잠긴 계정 → ?locked   (Spring이 비밀번호 확인 전에 막으므로 실패 횟수는 더 늘지 않음)
// - 정지·휴직·퇴사·비활성 계정 → ?disabled
// 로그인 이력(recordHistory)에는 사유를 BAD_CREDENTIALS / LOGIN_LOCKED / INACTIVE 로 남긴다
// SecurityConfig에서 영역마다 하나씩 만든다 (Bean 아님)
public class LoginFailureHandler implements AuthenticationFailureHandler {

	private final LoginAttemptRecorder recorder;
	private final String usernameParameter;
	private final String loginPage;
	private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

	public LoginFailureHandler(LoginAttemptRecorder recorder, String usernameParameter, String loginPage) {
		this.recorder = recorder;
		this.usernameParameter = usernameParameter;
		this.loginPage = loginPage;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		String username = request.getParameter(usernameParameter);
		String reason = "error";
		LoginFailReason failReason = null;	// 그 외 예외(DB 오류 등)는 이력에 남기지 않는다
		if (exception instanceof BadCredentialsException) {
			failReason = LoginFailReason.BAD_CREDENTIALS;
			if (username != null && !username.isBlank() && recorder.recordFailure(username)) {
				reason = "locked";
			}
		} else if (exception instanceof LockedException) {
			failReason = LoginFailReason.LOGIN_LOCKED;
			reason = "locked";
		} else if (exception instanceof DisabledException) {
			failReason = LoginFailReason.INACTIVE;
			reason = "disabled";
		}
		if (failReason != null) {
			recorder.recordHistory(username, failReason, request);
		}
		redirectStrategy.sendRedirect(request, response, loginPage + "?" + reason);
	}
}
