package org.doit.onbank.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// 고객 로그인 최소 버전 (docs/auth.md 6장 1~3단계)
// 다음 단계 : 로그인 성공/실패 핸들러(5회 잠금, login_log), 세션 타임아웃, 중복 로그인 제한, 직원(/admin) 로그인
@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				// 로그인한 고객만
				.requestMatchers("/mybank/**").hasRole("USER")
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
}
