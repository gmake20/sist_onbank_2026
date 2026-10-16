package org.doit.onbank.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 실제 SecurityConfig + MemberDetailsService + MySQL 로 로그인 흐름 확인
// @Transactional : 테스트 회원은 테스트가 끝나면 롤백 (공용 DB에 남지 않음)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private final String suffix = String.valueOf(System.currentTimeMillis() % 100_000_000);
	private final String loginId = "login" + suffix;
	private final String rawPassword = "Test1234!";

	private Member member;

	@BeforeEach
	void saveMember() {
		member = memberRepository.saveAndFlush(Member.builder()
				.loginId(loginId)
				.password(passwordEncoder.encode(rawPassword))
				.name("로그인테스트")
				.email(loginId + "@onbank.test")
				.phone("010" + suffix)
				.birthDate(LocalDate.of(2000, 1, 1))
				.termsAgreedAt(LocalDateTime.now())
				.build());
	}

	@Test
	void loginSuccess() throws Exception {
		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password(rawPassword))
				.andExpect(authenticated().withUsername(loginId).withRoles("USER"))
				.andExpect(redirectedUrl("/"));
	}

	@Test
	void loginFailWrongPassword() throws Exception {
		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password("wrong"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?error"));
	}

	// 비밀번호 5회 연속 오류면 잠기고, 잠긴 뒤에는 비밀번호가 맞아도 로그인 불가
	@Test
	void lockedAfterFiveFailures() throws Exception {
		for (int i = 1; i <= 4; i++) {
			mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password("wrong"))
					.andExpect(redirectedUrl("/login?error"));
		}
		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password("wrong"))
				.andExpect(redirectedUrl("/login?locked"));
		assertThat(member.getLoginLockedAt()).isNotNull();

		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password(rawPassword))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?locked"));
		mockMvc.perform(get("/login").param("locked", ""))
				.andExpect(content().string(containsString("5회 잘못 입력")));
	}

	// 성공하면 실패 횟수 초기화
	@Test
	void successResetsFailCount() throws Exception {
		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password("wrong"));
		assertThat(member.getLoginFailCount()).isEqualTo(1);

		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password(rawPassword))
				.andExpect(authenticated());
		assertThat(member.getLoginFailCount()).isZero();
	}

	@Test
	void loginFailUnknownId() throws Exception {
		mockMvc.perform(formLogin("/login").userParameter("loginId").user("no-such-" + suffix).password(rawPassword))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/login?error"));
	}

	@Test
	void mybankRequiresLogin() throws Exception {
		mockMvc.perform(get("/mybank"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void loginPageIsPublic() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk());
	}

	@Test
	void logoutRedirectsToMain() throws Exception {
		mockMvc.perform(logout("/logout"))
				.andExpect(unauthenticated())
				.andExpect(redirectedUrl("/"));
	}

	// 헤더 : 로그인하면 "이름님 + 로그아웃 버튼" 표시
	@Test
	void headerShowsNameWhenLoggedIn() throws Exception {
		mockMvc.perform(get("/").with(user(new LoginMember(member))))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("로그인테스트")))
				.andExpect(content().string(containsString("로그아웃")));
	}
}
