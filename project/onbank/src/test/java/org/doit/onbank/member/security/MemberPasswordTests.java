package org.doit.onbank.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.doit.onbank.common.mail.EmailSender;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.MemberStatus;
import org.doit.onbank.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 비밀번호 찾기(임시 비밀번호 메일) → 임시 비밀번호 로그인 → 변경 강제 → 비밀번호 변경 흐름 확인
// EmailSender는 가짜로 바꿔 실제 메일을 보내지 않고, 보낸 본문에서 임시 비밀번호를 꺼내 쓴다
// @Transactional : 테스트 회원은 테스트가 끝나면 롤백 (공용 DB에 남지 않음)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberPasswordTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@MockitoBean
	private EmailSender emailSender;

	private final String suffix = String.valueOf(System.currentTimeMillis() % 100_000_000);
	private final String loginId = "pwfind" + suffix;
	private final String email = loginId + "@onbank.test";
	private final LocalDate birthDate = LocalDate.of(2000, 1, 1);

	private Member member;

	@BeforeEach
	void saveMember() {
		member = memberRepository.saveAndFlush(Member.builder()
				.loginId(loginId)
				.password(passwordEncoder.encode("Test1234!"))
				.name("비번찾기")
				.email(email)
				.phone("010" + suffix)
				.birthDate(birthDate)
				.termsAgreedAt(LocalDateTime.now())
				.build());
	}

	// 정보가 모두 맞으면 메일 발송 + 임시 비밀번호 표시 + 잠금 해제
	@Test
	void findSendsTempPasswordAndUnlocks() throws Exception {
		for (int i = 0; i < 5; i++) {
			member.recordLoginFailure(5, LocalDateTime.now());
		}
		assertThat(member.getStatus()).isEqualTo(MemberStatus.LOCKED);

		String tempPassword = requestTempPassword();

		assertThat(member.isTempPassword()).isTrue();
		assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
		assertThat(passwordEncoder.matches(tempPassword, member.getPassword())).isTrue();
	}

	// 하나라도 다르면 메일을 보내지 않지만, 화면 안내는 같다 (가입 여부를 알려주지 않음)
	@Test
	void findWithWrongInfoSendsNothing() throws Exception {
		mockMvc.perform(post("/password/find").with(csrf())
						.param("loginId", loginId)
						.param("name", "다른이름")
						.param("birthDate", birthDate.toString())
						.param("email", email))
				.andExpect(redirectedUrl("/login"));

		verify(emailSender, never()).send(anyString(), anyString(), anyString());
		assertThat(member.isTempPassword()).isFalse();
	}

	// 임시 비밀번호로 로그인하면 변경 화면으로 가고, 다른 화면은 막힌다
	@Test
	void tempPasswordLoginForcesChange() throws Exception {
		String tempPassword = requestTempPassword();

		mockMvc.perform(formLogin("/login").userParameter("loginId").user(loginId).password(tempPassword))
				.andExpect(authenticated())
				.andExpect(redirectedUrl("/mybank/profile/password"));

		LoginMember me = new LoginMember(member);
		mockMvc.perform(get("/mybank/profile").with(user(me)))
				.andExpect(redirectedUrl("/mybank/profile/password"));
		mockMvc.perform(get("/mybank/profile/password").with(user(me)))
				.andExpect(status().isOk());
	}

	// 비밀번호를 바꾸면 임시 비밀번호 표시가 풀리고 로그아웃된다
	@Test
	void changePasswordClearsTempPassword() throws Exception {
		String tempPassword = requestTempPassword();

		mockMvc.perform(post("/mybank/profile/password").with(csrf()).with(user(new LoginMember(member)))
						.param("currentPassword", tempPassword)
						.param("newPassword", "NewPass12!")
						.param("newPasswordConfirm", "NewPass12!"))
				.andExpect(redirectedUrl("/login?changed"));

		assertThat(member.isTempPassword()).isFalse();
		assertThat(passwordEncoder.matches("NewPass12!", member.getPassword())).isTrue();
	}

	// 비밀번호 찾기를 요청하고, 보낸 메일 본문에서 임시 비밀번호를 꺼낸다
	private String requestTempPassword() throws Exception {
		mockMvc.perform(post("/password/find").with(csrf())
						.param("loginId", loginId)
						.param("name", "비번찾기")
						.param("birthDate", birthDate.toString())
						.param("email", email.toUpperCase()))	// 이메일 대소문자는 구분하지 않는다
				.andExpect(redirectedUrl("/login"));

		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(emailSender).send(eq(email), anyString(), text.capture());
		Matcher matcher = Pattern.compile("임시 비밀번호 : (\\S+)").matcher(text.getValue());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}
}
