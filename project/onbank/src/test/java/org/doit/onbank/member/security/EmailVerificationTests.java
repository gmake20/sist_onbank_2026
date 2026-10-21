package org.doit.onbank.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.doit.onbank.common.mail.EmailSender;
import org.doit.onbank.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 회원가입 이메일 인증 : 인증번호 메일 → 확인 → 가입 흐름 확인
// EmailSender는 가짜로 바꿔 실제 메일을 보내지 않고, 보낸 본문에서 인증번호를 꺼내 쓴다
// 휴대폰 인증은 끄고(이메일 인증만 확인) 이메일 인증은 켠 상태로 실행한다
// @Transactional : 가입한 테스트 회원은 테스트가 끝나면 롤백 (공용 DB에 남지 않음)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
		"onbank.phone-verification.enabled=false",
		"onbank.email-verification.enabled=true" })
class EmailVerificationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberRepository memberRepository;

	@MockitoBean
	private EmailSender emailSender;

	private final String suffix = String.valueOf(System.currentTimeMillis() % 100_000_000);
	private final String loginId = "emailvf" + suffix;
	private final String email = loginId + "@onbank.test";
	private final MockHttpSession session = new MockHttpSession();

	// 인증번호를 받아 확인하면 가입된다 (이메일 대소문자는 구분하지 않음)
	@Test
	void verifiedEmailCanSignup() throws Exception {
		String code = requestCode();

		mockMvc.perform(post("/api/email/verify").with(csrf()).session(session)
						.param("email", email.toUpperCase())
						.param("code", code))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true));

		mockMvc.perform(signup(email))
				.andExpect(redirectedUrl("/login"));

		assertThat(memberRepository.findByLoginId(loginId)).isPresent();
	}

	// 인증하지 않으면 가입 화면으로 돌아가고 이메일 칸에 오류가 표시된다
	@Test
	void signupWithoutEmailVerificationFails() throws Exception {
		mockMvc.perform(signup(email))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("form", "email", "notVerified"));

		assertThat(memberRepository.findByLoginId(loginId)).isEmpty();
	}

	// 다른 주소로 인증했으면 가입할 수 없다
	@Test
	void verifiedOtherEmailCannotSignup() throws Exception {
		String code = requestCode();
		mockMvc.perform(post("/api/email/verify").with(csrf()).session(session)
						.param("email", email)
						.param("code", code))
				.andExpect(status().isOk());

		mockMvc.perform(signup("other" + email))
				.andExpect(model().attributeHasFieldErrorCode("form", "email", "notVerified"));
	}

	// 인증번호가 틀리면 400 + 남은 횟수 안내, 인증 완료로 표시되지 않는다
	@Test
	void wrongCodeIsRejected() throws Exception {
		String code = requestCode();
		String wrong = code.equals("000000") ? "111111" : "000000";

		mockMvc.perform(post("/api/email/verify").with(csrf()).session(session)
						.param("email", email)
						.param("code", wrong))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.ok").value(false));

		mockMvc.perform(signup(email))
				.andExpect(model().attributeHasFieldErrorCode("form", "email", "notVerified"));
	}

	// 60초 안에 다시 요청하면 메일을 보내지 않는다
	@Test
	void resendTooSoonIsRejected() throws Exception {
		requestCode();

		mockMvc.perform(post("/api/email/send").with(csrf()).session(session).param("email", email))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.ok").value(false));
	}

	// 이미 가입된 이메일이면 메일을 보내지 않는다
	@Test
	void alreadyRegisteredEmailIsRejected() throws Exception {
		verifiedEmailCanSignup();

		MockHttpSession other = new MockHttpSession();
		mockMvc.perform(post("/api/email/send").with(csrf()).session(other).param("email", email))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
	}

	// 형식이 틀리면 메일을 보내지 않는다
	@Test
	void invalidEmailIsRejected() throws Exception {
		mockMvc.perform(post("/api/email/send").with(csrf()).session(session).param("email", "not-an-email"))
				.andExpect(status().isBadRequest());

		verify(emailSender, never()).send(anyString(), anyString(), anyString());
	}

	// 인증번호 메일을 요청하고, 보낸 메일 본문에서 인증번호를 꺼낸다
	private String requestCode() throws Exception {
		mockMvc.perform(post("/api/email/send").with(csrf()).session(session).param("email", email))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true));

		ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
		verify(emailSender).send(eq(email), anyString(), text.capture());
		Matcher matcher = Pattern.compile("인증번호 : (\\d{6})").matcher(text.getValue());
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private MockHttpServletRequestBuilder signup(String email) {
		return post("/signup").with(csrf()).session(session)
				.param("loginId", loginId)
				.param("password", "Test1234!")
				.param("passwordConfirm", "Test1234!")
				.param("name", "이메일인증")
				.param("birthDate", "2000-01-01")
				.param("phone", "010" + String.format("%08d", Long.parseLong(suffix)))
				.param("email", email)
				.param("termsAgreed", "true");
	}
}
