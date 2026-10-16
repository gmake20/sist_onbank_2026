package org.doit.onbank.config;

import java.util.LinkedHashMap;

import org.doit.onbank.admin.security.AdminDetailsService;
import org.doit.onbank.employee.security.EmployeeDetailsService;
import org.doit.onbank.member.security.MemberDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.DelegatingAccessDeniedHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

// 필터 체인 3개 : 관리자(/admin/**) → 직원(/employee/**) → 고객(나머지) 순서로 검사 (README 8-1장)
// - 체인마다 사용할 UserDetailsService를 직접 지정한다. 3개가 Bean으로 등록되어 있어 자동 선택되지 않음
// - 직원·관리자는 로그인 정보를 세션에 다른 이름으로 저장한다 → 고객 로그인으로 직원 화면에 들어갈 수 없고,
//   같은 브라우저에서 고객과 직원으로 함께 로그인해도 섞이지 않음
// 다음 단계 : 로그인 성공/실패 핸들러(5회 잠금, 로그인 이력), 임시 비밀번호 변경 강제, 세션 타임아웃, 중복 로그인 제한
@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// 권한 계층 : 위 권한이 아래 권한을 포함한다 (hasRole 검사, @PreAuthorize 모두 적용)
	// - 직원   : MANAGER(책임자) > STAFF(일반 직원)
	// - 관리자 : SUPER_ADMIN(총괄) > OPERATOR(운영), SERVICE(고객지원)
	//           OPERATOR와 SERVICE는 서로 포함하지 않음 (운영 관리자는 공지 관리 불가, 고객지원 관리자는 회원·계좌 조회 불가)
	// 직원과 관리자 권한 이름이 겹치지 않으므로 계층 하나에 함께 둔다
	@Bean
	static RoleHierarchy roleHierarchy() {
		return RoleHierarchyImpl.withDefaultRolePrefix()
				.role("MANAGER").implies("STAFF")
				.role("SUPER_ADMIN").implies("OPERATOR", "SERVICE")
				.build();
	}

	// 관리자 영역 (admin 테이블)
	@Bean
	@Order(1)
	SecurityFilterChain adminFilterChain(HttpSecurity http, AdminDetailsService adminDetailsService) throws Exception {
		http
			.securityMatcher("/admin/**")
			.userDetailsService(adminDetailsService)
			.securityContext(context -> context.securityContextRepository(contextRepository("SPRING_SECURITY_CONTEXT_ADMIN")))
			.requestCache(cache -> cache.requestCache(requestCache("SPRING_SECURITY_SAVED_REQUEST_ADMIN")))
			.exceptionHandling(ex -> ex
				.accessDeniedHandler(accessDeniedHandler("/admin/login"))
				.authenticationEntryPoint(entryPoint("/admin/login", "/admin/api/**")))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/admin/login").permitAll()
				// TODO 역할별 세부 권한은 팀원4가 정하면 추가 (예: 공지사항 관리)
				.anyRequest().hasAnyRole("SUPER_ADMIN", "OPERATOR", "SERVICE"))
			.formLogin(form -> form
				.loginPage("/admin/login")
				.loginProcessingUrl("/admin/login")
				.usernameParameter("loginId")
				.passwordParameter("password")
				.defaultSuccessUrl("/admin")
				.failureUrl("/admin/login?error")
				.permitAll())
			.logout(logout -> logout
				.logoutUrl("/admin/logout")
				.logoutSuccessUrl("/admin/login?logout")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"));

		return http.build();
	}

	// 직원 영역 (employee 테이블)
	@Bean
	@Order(2)
	SecurityFilterChain employeeFilterChain(HttpSecurity http, EmployeeDetailsService employeeDetailsService) throws Exception {
		http
			.securityMatcher("/employee/**")
			.userDetailsService(employeeDetailsService)
			.securityContext(context -> context.securityContextRepository(contextRepository("SPRING_SECURITY_CONTEXT_EMPLOYEE")))
			.requestCache(cache -> cache.requestCache(requestCache("SPRING_SECURITY_SAVED_REQUEST_EMPLOYEE")))
			.exceptionHandling(ex -> ex
				.accessDeniedHandler(accessDeniedHandler("/employee/login"))
				.authenticationEntryPoint(entryPoint("/employee/login", "/employee/api/**")))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/employee/login").permitAll()
				// TODO 승인 업무 URL이 정해지면 hasRole("MANAGER")로 추가 (계좌 동결, 이체한도 증액, 대출 심사, 상품 관리)
				// 대출 심사 페이지 및 일반 심사 업무
			    .requestMatchers("/employee/loans/review",
			                     "/employee/loans/review/**")
			        .hasAnyRole("STAFF", "MANAGER")
			    // 직원 대출 심사 REST API
			    .requestMatchers("/employee/api/loan-applications/**")
			        .hasAnyRole("STAFF", "MANAGER")
				.anyRequest().hasRole("STAFF"))
			.formLogin(form -> form
				.loginPage("/employee/login")
				.loginProcessingUrl("/employee/login")
				.usernameParameter("employeeNo")
				.passwordParameter("password")
				.defaultSuccessUrl("/employee")
				.failureUrl("/employee/login?error")
				.permitAll())
			.logout(logout -> logout
				.logoutUrl("/employee/logout")
				.logoutSuccessUrl("/employee/login?logout")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"));

		return http.build();
	}

	// 고객 영역 (members 테이블) : 위 두 영역이 아닌 나머지 전부
	@Bean
	@Order(3)
	SecurityFilterChain memberFilterChain(HttpSecurity http, MemberDetailsService memberDetailsService) throws Exception {
		http
			.userDetailsService(memberDetailsService)
			.exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler("/login")))
			.authorizeHttpRequests(auth -> auth
				// 비회원 공개 : 회원가입, 가입용 휴대폰 인증 API (아래 anyRequest를 잠가도 열려 있어야 함)
				.requestMatchers("/signup", "/api/phone/**").permitAll()
				// 로그인한 고객만
				.requestMatchers("/mybank/**").hasRole("USER")
				.requestMatchers("/loan/apply", "/loan/apply/**").authenticated()
				// TODO 다른 팀원 화면(/account/** 등)이 로그인 사용자 기준으로 바뀌면 여기에 추가
				//      최종적으로는 공개 URL만 permitAll, 나머지는 hasRole("USER")
				.anyRequest().permitAll())
			.formLogin(form -> form
				.loginPage("/login")				// 우리가 만든 로그인 화면 (GET)
				.loginProcessingUrl("/login")		// POST /login 은 Spring Security가 처리
				.usernameParameter("loginId")
				.passwordParameter("password")
				.defaultSuccessUrl("/")			// 로그인 전에 보려던 페이지가 있으면 그 페이지로 이동
				.failureUrl("/login?error")
				.permitAll())
			.logout(logout -> logout
				.logoutUrl("/logout")				// POST만 허용 (CSRF 보호)
				.logoutSuccessUrl("/")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"));
		// CSRF는 끄지 않는다. th:action 을 쓰면 폼에 토큰이 자동으로 들어감

		return http.build();
	}

	// 403 처리
	// - CSRF 토큰이 만료·불일치한 경우 (로그인 화면을 띄워 둔 채 서버가 재시작되거나 세션이 끝난 뒤 제출)
	//   → 403 오류 화면 대신 그 영역의 로그인 화면으로 보내고 "다시 시도" 안내 (?expired)
	// - 그 외 권한 부족 → 기본 403 응답 (403 화면은 공통 오류 화면에서 처리)
	private static AccessDeniedHandler accessDeniedHandler(String loginPage) {
		LinkedHashMap<Class<? extends AccessDeniedException>, AccessDeniedHandler> handlers = new LinkedHashMap<>();
		handlers.put(CsrfException.class,
				(request, response, exception) -> response.sendRedirect(request.getContextPath() + loginPage + "?expired"));
		return new DelegatingAccessDeniedHandler(handlers, new AccessDeniedHandlerImpl());
	}

	// 로그인하지 않은 요청 처리
	// - API 주소(fetch) : 로그인 화면 HTML 대신 401만 돌려준다. 화면 스크립트는 401을 받으면 로그인 화면으로 이동
	// - 그 외 화면 주소 : 그 영역의 로그인 화면으로 redirect (formLogin 기본 동작과 같음)
	// 두 규칙을 직접 지정한다. formLogin 기본 규칙에 API 규칙만 덧붙이면 Accept 헤더에 text/html이 없는 요청이
	// 로그인 화면이 아니라 401로 가 버린다
	private static AuthenticationEntryPoint entryPoint(String loginPage, String apiPattern) {
		return DelegatingAuthenticationEntryPoint.builder()
				.addEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
						PathPatternRequestMatcher.withDefaults().matcher(apiPattern))
				.defaultEntryPoint(new LoginUrlAuthenticationEntryPoint(loginPage))
				.build();
	}

	// 로그인 정보를 세션의 지정한 이름으로 저장
	private static HttpSessionSecurityContextRepository contextRepository(String sessionKey) {
		HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
		repository.setSpringSecurityContextKey(sessionKey);
		return repository;
	}

	// 로그인 전에 보려던 주소를 세션의 지정한 이름으로 저장 (다른 영역의 주소로 이동하지 않도록)
	private static HttpSessionRequestCache requestCache(String sessionKey) {
		HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
		requestCache.setSessionAttrName(sessionKey);
		return requestCache;
	}
}
