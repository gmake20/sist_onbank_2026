package org.doit.onbank.account.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.doit.onbank.account.dto.OpenAccountReceipt;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.service.AccountOpenService;
import org.doit.onbank.admin.security.AdminDetailsService;
import org.doit.onbank.admin.security.AdminLoginAttemptRecorder;
import org.doit.onbank.config.SecurityConfig;
import org.doit.onbank.employee.security.EmployeeDetailsService;
import org.doit.onbank.employee.security.EmployeeLoginAttemptRecorder;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.member.security.MemberDetailsService;
import org.doit.onbank.member.security.MemberLoginAttemptRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// 계좌 개설 화면 테스트 : Controller + Thymeleaf 화면 + SecurityConfig 만 띄운다 (DB, JPA 없음)
@WebMvcTest(AccountOpenPageController.class)
@Import(SecurityConfig.class)
class AccountOpenPageControllerTests {

	private static final Long MEMBER_ID = 100L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountOpenService accountOpenService;

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

	// (10-17) SecurityConfig 로그인 실패 5회 잠금 핸들러가 요구하는 Bean
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

	@Test
	void openFormRequiresLogin() throws Exception {
		mockMvc.perform(get("/mybank/accounts/open"))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void openFormRenders() throws Exception {
		mockMvc.perform(get("/mybank/accounts/open").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("계좌비밀번호 설정")))
				.andExpect(content().string(containsString("ON 통장")));
	}

	@Test
	void openRedirectsToResult() throws Exception {
		Account account = Account.open(MEMBER_ID, "1002-123-456789", AccountType.CHECKING, "hash",
				LocalDate.of(2026, 10, 16));
		ReflectionTestUtils.setField(account, "accountId", 7L);
		given(accountOpenService.openChecking(MEMBER_ID, "1234")).willReturn(account);

		mockMvc.perform(post("/mybank/accounts/open").with(user(me)).with(csrf())
				.param("password", "1234")
				.param("passwordConfirm", "1234")
				.param("agreed", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/accounts/open/result"))
				.andExpect(flash().attributeExists("receipt"));
	}

	@Test
	void openRejectsMismatchedPassword() throws Exception {
		mockMvc.perform(post("/mybank/accounts/open").with(user(me)).with(csrf())
				.param("password", "1234")
				.param("passwordConfirm", "4321")
				.param("agreed", "true"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "passwordMatched"))
				.andExpect(content().string(containsString("계좌 비밀번호가 서로 다릅니다")));
		verify(accountOpenService, never()).openChecking(anyLong(), anyString());
	}

	@Test
	void openRejectsInvalidPasswordAndNoAgreement() throws Exception {
		mockMvc.perform(post("/mybank/accounts/open").with(user(me)).with(csrf())
				.param("password", "12a4")
				.param("passwordConfirm", "12a4"))
				.andExpect(status().isOk())
				.andExpect(model().attributeHasFieldErrors("form", "password", "agreed"));
		verify(accountOpenService, never()).openChecking(anyLong(), anyString());
	}

	@Test
	void resultRendersReceipt() throws Exception {
		OpenAccountReceipt receipt = new OpenAccountReceipt(7L, "1002-123-456789", "ON 통장", "ACTIVE",
				LocalDate.of(2026, 10, 16));

		mockMvc.perform(get("/mybank/accounts/open/result").with(user(me)).flashAttr("receipt", receipt))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("계좌가 개설되었습니다")))
				.andExpect(content().string(containsString("1002-123-456789")))
				.andExpect(content().string(containsString("2026.10.16")));
	}

	// 새로고침이나 주소 직접 입력으로 결과가 없으면 개설 화면으로
	@Test
	void resultWithoutReceiptRedirectsToForm() throws Exception {
		mockMvc.perform(get("/mybank/accounts/open/result").with(user(me)))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/accounts/open"));
	}
}
