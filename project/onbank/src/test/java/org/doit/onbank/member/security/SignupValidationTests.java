package org.doit.onbank.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.doit.onbank.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 회원가입 아이디·비밀번호 길이 검사 (SignupForm의 @Pattern) : 짧거나 길면 가입되지 않는다
// 화면(HTML)만 막으면 우회할 수 있으므로 서버로 바로 요청해서 확인한다
// 휴대폰·이메일 인증은 꺼서 길이 검사만 본다
// @Transactional : 가입된 테스트 회원은 테스트가 끝나면 롤백 (공용 DB에 남지 않음)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
		"onbank.phone-verification.enabled=false",
		"onbank.email-verification.enabled=false" })
class SignupValidationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MemberRepository memberRepository;

	private final String suffix = String.format("%08d", System.currentTimeMillis() % 100_000_000);

	// 아이디 : 영문 소문자·숫자 5~20자
	@ParameterizedTest
	@ValueSource(strings = { "a", "ab12", "abcdefghij0123456789x" })	// 1자, 4자, 21자
	void loginIdOutOfLengthIsRejected(String loginId) throws Exception {
		mockMvc.perform(signup(loginId, "Test1234!"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("form", "loginId", "Pattern"));

		assertThat(memberRepository.existsByLoginId(loginId)).isFalse();
	}

	// 비밀번호 : 영문·숫자·특수문자 포함 8~20자
	@ParameterizedTest
	@ValueSource(strings = { "A1!", "Abc12!x", "Abcdefgh12345678901!" + "x" })	// 3자, 7자, 21자
	void passwordOutOfLengthIsRejected(String password) throws Exception {
		String loginId = "pwlen" + suffix;
		mockMvc.perform(signup(loginId, password))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrorCode("form", "password", "Pattern"));

		assertThat(memberRepository.existsByLoginId(loginId)).isFalse();
	}

	// 경계값 : 아이디 5자, 비밀번호 8자는 가입된다
	@Test
	void minimumLengthIsAccepted() throws Exception {
		String loginId = "m" + suffix.substring(4);	// 5자
		mockMvc.perform(signup(loginId, "Abcd12!x"))	// 8자
				.andExpect(redirectedUrl("/login"));

		assertThat(memberRepository.existsByLoginId(loginId)).isTrue();
	}

	private MockHttpServletRequestBuilder signup(String loginId, String password) {
		return post("/signup").with(csrf())
				.param("loginId", loginId)
				.param("password", password)
				.param("passwordConfirm", password)
				.param("name", "길이검사")
				.param("birthDate", "2000-01-01")
				.param("phone", "010" + suffix)
				.param("email", "len" + suffix + "@onbank.test")
				.param("termsAgreed", "true");
	}
}
