package org.doit.onbank.otp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.doit.onbank.admin.security.AdminDetailsService;
import org.doit.onbank.admin.security.AdminLoginAttemptRecorder;
import org.doit.onbank.common.exception.BusinessException;
import org.doit.onbank.config.SecurityConfig;
import org.doit.onbank.employee.security.EmployeeDetailsService;
import org.doit.onbank.employee.security.EmployeeLoginAttemptRecorder;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.member.security.MemberDetailsService;
import org.doit.onbank.member.security.MemberLoginAttemptRecorder;
import org.doit.onbank.otp.dto.OtpDeviceInfo;
import org.doit.onbank.otp.dto.OtpIssueResult;
import org.doit.onbank.otp.entity.OtpStatus;
import org.doit.onbank.otp.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// OTP 화면 테스트 : Controller + Thymeleaf 화면 + SecurityConfig 만 띄운다 (DB, JPA 없음)
@WebMvcTest(OtpPageController.class)
@Import(SecurityConfig.class)
class OtpPageControllerTests {

	private static final Long MEMBER_ID = 100L;
	private static final String CODE = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OtpService otpService;

	// OnbankApplication 의 @EnableJpaAuditing 이 JPA 설정을 찾으므로 가짜로 채운다 (이 테스트는 JPA 를 띄우지 않음)
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	// SecurityConfig 의 로그인 설정 3개(회원·관리자·직원 FilterChain)가 요구하는 Bean (로그인은 user(me)로 대신하므로 가짜)
	@MockitoBean
	private MemberDetailsService memberDetailsService;

	@MockitoBean
	private AdminDetailsService adminDetailsService;

	@MockitoBean
	private EmployeeDetailsService employeeDetailsService;

	@MockitoBean
	private MemberLoginAttemptRecorder memberLoginAttemptRecorder;

	@MockitoBean
	private AdminLoginAttemptRecorder adminLoginAttemptRecorder;

	@MockitoBean
	private EmployeeLoginAttemptRecorder employeeLoginAttemptRecorder;

	private LoginMember me;

	@BeforeEach
	void setUp() {
		Member member = Member.builder()
				.loginId("tester")
				.password("{noop}dummy")
				.name("테스트")
				.birthDate(LocalDate.of(2000, 1, 1))
				.build();
		ReflectionTestUtils.setField(member, "memberId", MEMBER_ID);
		me = new LoginMember(member);
	}

	private void currentDeviceIs(OtpStatus status) {
		LocalDateTime now = LocalDateTime.of(2026, 10, 17, 10, 0);
		given(otpService.findCurrent(MEMBER_ID)).willReturn(Optional.of(new OtpDeviceInfo("0123456789", status, 0,
				now, status == OtpStatus.ISSUED ? null : now)));
	}

	// ---------- 상태 화면 ----------

	@Test
	void homeRequiresLogin() throws Exception {
		mockMvc.perform(get("/mybank/security/otp"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void homeWithoutDeviceShowsIssueButton() throws Exception {
		given(otpService.findCurrent(MEMBER_ID)).willReturn(Optional.empty());

		mockMvc.perform(get("/mybank/security/otp").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("등록된 OTP가 없습니다")))
				.andExpect(content().string(containsString("OTP 발급")));
	}

	@Test
	void homeWithIssuedDeviceShowsActivateForm() throws Exception {
		currentDeviceIs(OtpStatus.ISSUED);

		mockMvc.perform(get("/mybank/security/otp").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("OTP 사용 등록")))
				.andExpect(content().string(containsString("다시 발급")));
	}

	@Test
	void homeWithActiveDeviceShowsVerifyForm() throws Exception {
		currentDeviceIs(OtpStatus.ACTIVE);

		mockMvc.perform(get("/mybank/security/otp").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("사용중")))
				.andExpect(content().string(containsString("OTP 번호 확인")))
				.andExpect(content().string(not(containsString("OTP 사용 등록"))));
	}

	// ---------- 발급 ----------

	@Test
	void issueRedirectsToIssuedPage() throws Exception {
		given(otpService.issue(MEMBER_ID)).willReturn(new OtpIssueResult("0123456789", CODE, "otpauth://totp/x"));

		mockMvc.perform(post("/mybank/security/otp/issue").with(user(me)).with(csrf()))
				.andExpect(redirectedUrl("/mybank/security/otp/issued"))
				.andExpect(flash().attributeExists("issued"));
	}

	@Test
	void issueFailureShowsMessage() throws Exception {
		given(otpService.issue(MEMBER_ID)).willThrow(new BusinessException("이미 등록된 OTP가 있습니다."));

		mockMvc.perform(post("/mybank/security/otp/issue").with(user(me)).with(csrf()))
				.andExpect(redirectedUrl("/mybank/security/otp"))
				.andExpect(flash().attribute("errorMessage", "이미 등록된 OTP가 있습니다."));
	}

	// 등록 코드는 4글자씩 띄어 표시하고, QR용 값을 화면에 넣는다
	@Test
	void issuedPageShowsCodeOnce() throws Exception {
		OtpIssueResult issued = new OtpIssueResult("0123456789", CODE, "otpauth://totp/OnBank:0123456789?secret=" + CODE);

		mockMvc.perform(get("/mybank/security/otp/issued").with(user(me)).flashAttr("issued", issued))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("JBSW Y3DP EHPK 3PXP JBSW Y3DP EHPK 3PXP")))
				.andExpect(content().string(containsString("data-uri=\"otpauth://totp/OnBank:0123456789")));
	}

	// 새로고침이나 주소 직접 입력으로는 등록 코드를 다시 볼 수 없다
	@Test
	void issuedPageWithoutResultRedirects() throws Exception {
		mockMvc.perform(get("/mybank/security/otp/issued").with(user(me)))
				.andExpect(redirectedUrl("/mybank/security/otp"));
	}

	// ---------- 사용 등록 · 번호 확인 ----------

	@Test
	void activateSuccess() throws Exception {
		mockMvc.perform(post("/mybank/security/otp/activate").with(user(me)).with(csrf())
				.param("firstCode", " 123456 ")
				.param("secondCode", "654321"))
				.andExpect(redirectedUrl("/mybank/security/otp"))
				.andExpect(flash().attributeExists("successMessage"));
		verify(otpService).activate(MEMBER_ID, "123456", "654321");
	}

	@Test
	void activateFailureShowsMessage() throws Exception {
		willThrow(new BusinessException("OTP 번호가 맞지 않습니다.")).given(otpService).activate(MEMBER_ID, "111111", "222222");

		mockMvc.perform(post("/mybank/security/otp/activate").with(user(me)).with(csrf())
				.param("firstCode", "111111")
				.param("secondCode", "222222"))
				.andExpect(redirectedUrl("/mybank/security/otp"))
				.andExpect(flash().attribute("errorMessage", "OTP 번호가 맞지 않습니다."));
	}

	@Test
	void verifySuccess() throws Exception {
		mockMvc.perform(post("/mybank/security/otp/verify").with(user(me)).with(csrf()).param("code", "123456"))
				.andExpect(redirectedUrl("/mybank/security/otp"))
				.andExpect(flash().attribute("successMessage", "OTP 번호가 맞습니다."));
	}

	@Test
	void verifyFailureShowsMessage() throws Exception {
		willThrow(new BusinessException("OTP 번호가 올바르지 않습니다. (오류 1/10회)")).given(otpService).verify(MEMBER_ID, "000000");

		mockMvc.perform(post("/mybank/security/otp/verify").with(user(me)).with(csrf()).param("code", "000000"))
				.andExpect(redirectedUrl("/mybank/security/otp"))
				.andExpect(flash().attribute("errorMessage", "OTP 번호가 올바르지 않습니다. (오류 1/10회)"));
	}

	// CSRF 토큰 없이 발급 요청 불가
	@Test
	void issueRequiresCsrf() throws Exception {
		mockMvc.perform(post("/mybank/security/otp/issue").with(user(me)))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?expired"));
	}
}
