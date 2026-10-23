package org.doit.onbank.config;

import java.util.LinkedHashMap;

import org.doit.onbank.admin.security.AdminDetailsService;
import org.doit.onbank.admin.security.AdminLoginAttemptRecorder;
import org.doit.onbank.common.security.LoginFailureHandler;
import org.doit.onbank.common.security.LoginSuccessHandler;
import org.doit.onbank.common.security.TempPasswordFilter;
import org.doit.onbank.employee.security.EmployeeDetailsService;
import org.doit.onbank.employee.security.EmployeeLoginAttemptRecorder;
import org.doit.onbank.member.security.MemberDetailsService;
import org.doit.onbank.member.security.MemberLoginAttemptRecorder;
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
import org.springframework.security.web.access.intercept.AuthorizationFilter;
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
// - 로그인 성공/실패 핸들러 : 비밀번호 5회 오류 잠금 (LoginSuccessHandler, LoginFailureHandler)
// - 임시 비밀번호 변경 강제 (직원·관리자·고객) : 로그인 직후 변경 화면으로 이동(LoginSuccessHandler),
//   바꾸기 전에는 다른 화면 차단(TempPasswordFilter)
// 다음 단계 : 로그인 이력 기록, 세션 타임아웃, 중복 로그인 제한
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
	SecurityFilterChain adminFilterChain(HttpSecurity http, AdminDetailsService adminDetailsService,
			AdminLoginAttemptRecorder adminLoginAttemptRecorder) throws Exception {
		HttpSessionRequestCache requestCache = requestCache("SPRING_SECURITY_SAVED_REQUEST_ADMIN");
		http
			// 이 체인이 맡는 주소 범위 : /admin 으로 시작하는 요청만 이 체인을 탄다
			// (@Order(1)이라 가장 먼저 검사. 맞지 않으면 다음 체인(직원)으로 넘어감)
			.securityMatcher("/admin/**")
			// 로그인할 때 아이디·비밀번호를 admin 테이블에서 찾는다
			// (UserDetailsService Bean이 3개라 자동으로 고를 수 없어서 직접 지정)
			.userDetailsService(adminDetailsService)
			// 로그인 정보를 세션의 SPRING_SECURITY_CONTEXT_ADMIN 이름으로 저장·조회한다
			// → 고객·직원 로그인 정보(다른 이름)와 섞이지 않음. 고객으로 로그인해도 관리자 화면에서는 비로그인으로 처리
			.securityContext(context -> context.securityContextRepository(contextRepository("SPRING_SECURITY_CONTEXT_ADMIN")))
			// 로그인 전에 보려던 주소를 SPRING_SECURITY_SAVED_REQUEST_ADMIN 이름으로 저장한다
			// → 로그인 성공 후 그 주소로 돌아감 (LoginSuccessHandler에도 같은 requestCache를 넘김)
			.requestCache(cache -> cache.requestCache(requestCache))
			// 권한 검사를 통과한 뒤에 확인 (로그인하지 않은 요청은 먼저 로그인 화면으로)
			// 임시 비밀번호 상태면 변경 화면(/admin/password)과 로그아웃(/admin/logout)만 허용
			// 그 외 화면은 변경 화면으로 redirect, /admin/api/** 는 403 응답
			.addFilterAfter(new TempPasswordFilter("/admin/password", "/admin/logout", "/admin/api/**"),
					AuthorizationFilter.class)
			// 인증·권한 오류가 났을 때 처리 방법
			.exceptionHandling(ex -> ex
				// 403 : CSRF 토큰 오류면 /admin/login?expired 로, 그 외 권한 부족은 기본 403 응답
				.accessDeniedHandler(accessDeniedHandler("/admin/login"))
				// 로그인하지 않은 요청 : /admin/api/** 는 401만, 그 외 화면은 /admin/login 으로 redirect
				.authenticationEntryPoint(entryPoint("/admin/login", "/admin/api/**")))
			// 주소별 접근 권한 : 위에서부터 차례로 검사하고 처음 맞는 규칙 하나만 적용
			.authorizeHttpRequests(auth -> auth
				// 로그인 화면은 누구나 접근 가능 (막으면 로그인 화면으로 가는 redirect가 무한 반복됨)
				.requestMatchers("/admin/login").permitAll()
				// 활동 로그·로그인 이력(S-14)은 SUPER_ADMIN 전용. AdminLogController의 @PreAuthorize와 함께
				// URL에서도 막는다 (@EnableMethodSecurity가 빠져도 열리지 않도록)
				// /admin/logs 와 그 하위 주소 모두 해당. OPERATOR·SERVICE는 403
				.requestMatchers("/admin/logs", "/admin/logs/**").hasRole("SUPER_ADMIN")
				// TODO 역할별 세부 권한은 팀원4가 정하면 추가 (예: 공지사항 관리)
				// 나머지 관리자 주소는 관리자 역할 3개 중 하나만 있으면 접근 가능
				// (hasRole은 앞에 ROLE_을 붙여 검사 : ROLE_SUPER_ADMIN, ROLE_OPERATOR, ROLE_SERVICE)
				.anyRequest().hasAnyRole("SUPER_ADMIN", "OPERATOR", "SERVICE"))
			.formLogin(form -> form
				.loginPage("/admin/login")
				.loginProcessingUrl("/admin/login")
				.usernameParameter("loginId")
				.passwordParameter("password")
				.successHandler(new LoginSuccessHandler(adminLoginAttemptRecorder, "/admin", requestCache, "/admin/password"))
				.failureHandler(new LoginFailureHandler(adminLoginAttemptRecorder, "loginId", "/admin/login"))
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
	SecurityFilterChain employeeFilterChain(HttpSecurity http, EmployeeDetailsService employeeDetailsService,
			EmployeeLoginAttemptRecorder employeeLoginAttemptRecorder) throws Exception {
		HttpSessionRequestCache requestCache = requestCache("SPRING_SECURITY_SAVED_REQUEST_EMPLOYEE");
		http
			.securityMatcher("/employee/**")
			.userDetailsService(employeeDetailsService)
			.securityContext(context -> context.securityContextRepository(contextRepository("SPRING_SECURITY_CONTEXT_EMPLOYEE")))
			.requestCache(cache -> cache.requestCache(requestCache))
			// 권한 검사를 통과한 뒤에 확인 (로그인하지 않은 요청은 먼저 로그인 화면으로)
			.addFilterAfter(new TempPasswordFilter("/employee/password", "/employee/logout", "/employee/api/**"),
					AuthorizationFilter.class)
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
				.successHandler(new LoginSuccessHandler(employeeLoginAttemptRecorder, "/employee", requestCache, "/employee/password"))
				.failureHandler(new LoginFailureHandler(employeeLoginAttemptRecorder, "employeeNo", "/employee/login"))
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
	SecurityFilterChain memberFilterChain(HttpSecurity http, MemberDetailsService memberDetailsService,
			MemberLoginAttemptRecorder memberLoginAttemptRecorder) throws Exception {
		HttpSessionRequestCache requestCache = new HttpSessionRequestCache();	// 고객은 세션 기본 이름 사용
		http
			.userDetailsService(memberDetailsService)
			.requestCache(cache -> cache.requestCache(requestCache))
			// 비밀번호 찾기로 받은 임시 비밀번호로 로그인하면 변경 전까지 다른 화면 차단
			// 고객 체인은 css·js·오류 화면도 지나가므로 함께 통과시킨다
			.addFilterAfter(new TempPasswordFilter("/mybank/profile/password", "/logout", "/api/**",
					"/css/**", "/js/**", "/images/**", "/favicon.ico", "/error"),
					AuthorizationFilter.class)
			.exceptionHandling(ex -> ex.accessDeniedHandler(accessDeniedHandler("/login")))
			.authorizeHttpRequests(auth -> auth
				// 비회원 공개 : 회원가입, 가입용 휴대폰·이메일 인증 API, 비밀번호 찾기 (아래 anyRequest를 잠가도 열려 있어야 함)
				.requestMatchers("/signup", "/api/phone/**", "/api/email/**", "/password/find").permitAll()
				// 비회원 공개 : 지점/ATM 찾기, 고객센터 안내
				.requestMatchers("/branches", "/api/branches", "/customer").permitAll()
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
				// 성공 : 실패 횟수 초기화 → 로그인 전에 보려던 페이지, 없으면 /
				//        임시 비밀번호면 비밀번호 변경 화면
				.successHandler(new LoginSuccessHandler(memberLoginAttemptRecorder, "/", requestCache, "/mybank/profile/password"))
				// 실패 : 비밀번호 5회 오류면 잠금 → /login?error, ?locked, ?disabled
				.failureHandler(new LoginFailureHandler(memberLoginAttemptRecorder, "loginId", "/login"))
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
