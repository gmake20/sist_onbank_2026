package org.doit.onbank.common.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// 임시 비밀번호로 로그인한 고객·직원·관리자는 비밀번호를 바꾸기 전까지 다른 화면을 쓸 수 없다
// - 화면 주소 → 비밀번호 변경 화면으로 redirect
// - API 주소(fetch) → 403 (화면 스크립트가 HTML redirect를 JSON으로 읽지 않도록)
// - 비밀번호 변경 화면, 로그아웃, otherAllowed(고객 영역의 css·js 등)는 통과
// SecurityConfig에서 체인마다 new로 붙인다. @Component로 등록하면 모든 요청에 걸리므로 등록하지 않는다
public class TempPasswordFilter extends OncePerRequestFilter {

	private final String passwordUrl;
	private final RequestMatcher allowed;
	private final RequestMatcher api;

	public TempPasswordFilter(String passwordUrl, String logoutUrl, String apiPattern, String... otherAllowed) {
		PathPatternRequestMatcher.Builder matcher = PathPatternRequestMatcher.withDefaults();
		List<RequestMatcher> allowedMatchers = new ArrayList<>();
		allowedMatchers.add(matcher.matcher(passwordUrl));
		allowedMatchers.add(matcher.matcher(logoutUrl));
		for (String pattern : otherAllowed) {
			allowedMatchers.add(matcher.matcher(pattern));
		}
		this.passwordUrl = passwordUrl;
		this.allowed = new OrRequestMatcher(allowedMatchers);
		this.api = matcher.matcher(apiPattern);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof TempPasswordUser user
				&& user.isTempPassword() && !allowed.matches(request)) {
			if (api.matches(request)) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
			} else {
				response.sendRedirect(request.getContextPath() + passwordUrl);
			}
			return;
		}
		chain.doFilter(request, response);
	}
}
