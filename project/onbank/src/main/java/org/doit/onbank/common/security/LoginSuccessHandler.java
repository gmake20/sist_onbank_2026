package org.doit.onbank.common.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 로그인 성공 : 실패 횟수를 초기화한 뒤, 기존 defaultSuccessUrl과 똑같이 이동한다
// (로그인 전에 보려던 페이지가 있으면 그 페이지, 없으면 defaultTargetUrl)
// requestCache는 그 영역 체인과 같은 것을 넘겨야 한다. 직원·관리자는 세션에 다른 이름으로 저장하기 때문
// 임시 비밀번호로 로그인했으면 비밀번호 변경 화면(passwordUrl)으로 보낸다
// (직원·관리자 : 계정 발급 시 임시 비밀번호, 고객 : 비밀번호 찾기로 받은 임시 비밀번호)
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private final LoginAttemptRecorder recorder;
	private final String passwordUrl;

	public LoginSuccessHandler(LoginAttemptRecorder recorder, String defaultTargetUrl, RequestCache requestCache,
			String passwordUrl) {
		this.recorder = recorder;
		this.passwordUrl = passwordUrl;
		setDefaultTargetUrl(defaultTargetUrl);
		setRequestCache(requestCache);
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws ServletException, IOException {
		recorder.recordSuccess(authentication.getName());
		if (authentication.getPrincipal() instanceof TempPasswordUser user && user.isTempPassword()) {
			clearAuthenticationAttributes(request);
			getRedirectStrategy().sendRedirect(request, response, passwordUrl);
			return;
		}
		super.onAuthenticationSuccess(request, response, authentication);
	}
}
