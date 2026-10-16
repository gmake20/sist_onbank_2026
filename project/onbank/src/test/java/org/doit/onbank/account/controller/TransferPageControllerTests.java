package org.doit.onbank.account.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.doit.onbank.account.dto.RecipientInfo;
import org.doit.onbank.account.dto.TransferLimitInfo;
import org.doit.onbank.account.dto.TransferReceipt;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountFavorite;
import org.doit.onbank.account.repository.RecentDepositAccount;
import org.doit.onbank.account.service.AccountFavoriteService;
import org.doit.onbank.account.service.AccountService;
import org.doit.onbank.account.service.AccountTransferService;
import org.doit.onbank.admin.security.AdminDetailsService;
import org.doit.onbank.config.SecurityConfig;
import org.doit.onbank.employee.security.EmployeeDetailsService;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.member.security.MemberDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// 이체 화면 테스트 : Controller + Thymeleaf 화면 + SecurityConfig 만 띄운다 (DB, JPA 없음)
@WebMvcTest(TransferPageController.class)
@Import(SecurityConfig.class)
class TransferPageControllerTests {

	private static final Long MEMBER_ID = 100L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountService accountService;

	@MockitoBean
	private AccountTransferService accountTransferService;

	@MockitoBean
	private AccountFavoriteService accountFavoriteService;

	// OnbankApplication 의 @EnableJpaAuditing 이 JPA 설정을 찾으므로 가짜로 채운다
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	// SecurityConfig 의 로그인 설정 3개(회원·관리자·직원 FilterChain)가 요구하는 Bean (로그인은 user(me)로 대신하므로 가짜)
	@MockitoBean
	private MemberDetailsService memberDetailsService;

	@MockitoBean
	private AdminDetailsService adminDetailsService;

	@MockitoBean
	private EmployeeDetailsService employeeDetailsService;

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

		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(
				account(1L, "TEST-0001", AccountStatus.ACTIVE, 100_000L),
				account(2L, "TEST-0002", AccountStatus.FROZEN, 50_000L)));	// 동결 계좌는 출금계좌 목록에서 빠짐
	}

	private Account account(Long id, String number, String status, long balance) {
		Account account = Account.builder()
				.memberId(MEMBER_ID)
				.accountNumber(number)
				.accountStatus(status)
				.balance(balance)
				.todayTransferAmount(0L)
				.build();
		ReflectionTestUtils.setField(account, "accountId", id);
		return account;
	}

	private TransferReceipt receipt() {
		return new TransferReceipt(1L, "TEST-0001", "TEST-9999", "김*수", 30_000L, 70_000L, "김철수", "테스트",
				LocalDateTime.of(2026, 10, 15, 14, 30, 12), "20261015143012-482915");
	}

	@Test
	void loginRequired() throws Exception {
		mockMvc.perform(get("/mybank/transfer"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	// ---------- 정보입력 ----------

	@Test
	void formShowsOnlyWithdrawableAccounts() throws Exception {
		mockMvc.perform(get("/mybank/transfer").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("ON 통장 (TEST-0001)"),
						not(containsString("ON 통장 (TEST-0002)")),
						containsString("data-balance=\"100000\""),
						containsString("이체실행"))));
	}

	// 입금계좌 선택 창 : 즐겨찾기(기본 출금계좌 포함), 최근입금계좌, 본인계좌(입금 가능한 계좌)
	@Test
	void formShowsPickerLists() throws Exception {
		AccountFavorite favorite = AccountFavorite.builder()
				.accountId(1L)
				.recipientAccountNumber("TEST-7777")
				.recipientAccountName("박*희")
				.favoriteAlias("월세")
				.build();
		RecentDepositAccount recent = new RecentDepositAccount() {
			@Override
			public String getAccountNumber() {
				return "TEST-8888";
			}

			@Override
			public LocalDateTime getLastTransferAt() {
				return LocalDateTime.of(2026, 10, 16, 9, 0);
			}
		};
		given(accountFavoriteService.getMyFavorites(MEMBER_ID)).willReturn(List.of(favorite));
		given(accountFavoriteService.getRecentDepositAccounts(MEMBER_ID)).willReturn(List.of(recent));

		mockMvc.perform(get("/mybank/transfer").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("월세"),
						containsString("data-account-number=\"TEST-7777\" data-from-id=\"1\""),
						containsString("data-account-number=\"TEST-8888\""),
						containsString("2026.10.16"),
						containsString("data-account-number=\"TEST-0002\""))));	// 동결 계좌도 입금은 가능
	}

	// 즐겨찾기 관리 화면의 [이체] : 출금계좌와 입금계좌를 미리 채운다
	@Test
	void formPrefillsToAccount() throws Exception {
		mockMvc.perform(get("/mybank/transfer").param("from", "1").param("to", "TEST-7777").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("value=\"TEST-7777\"")));
	}

	@Test
	void formShowsMessageWhenNoAccount() throws Exception {
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of());

		mockMvc.perform(get("/mybank/transfer").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("이체할 수 있는 계좌가 없습니다")));
	}

	// ---------- 이체 실행 ----------

	@Test
	void transferSuccessRedirectsToResult() throws Exception {
		given(accountTransferService.transfer(MEMBER_ID, 1L, "1234", "TEST-9999", 30_000L, "", ""))
				.willReturn(receipt());

		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("amount", "30000")
						.param("toAccountNumber", "TEST-9999")
						.param("senderMemo", "")
						.param("receiverMemo", ""))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/transfer/result"))
				.andExpect(flash().attributeExists("receipt"));
	}

	@Test
	void transferRejectedShowsMessageAndKeepsInput() throws Exception {
		given(accountTransferService.transfer(MEMBER_ID, 1L, "1234", "TEST-9999", 30_000L, null, null))
				.willThrow(new IllegalStateException("1일 이체한도를 초과했습니다."));

		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("amount", "30000")
						.param("toAccountNumber", "TEST-9999"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("1일 이체한도를 초과했습니다."),
						containsString("value=\"TEST-9999\""),
						not(containsString("value=\"1234\"")))));	// 비밀번호는 다시 입력받음
	}

	@Test
	void transferInputCheckedBeforeService() throws Exception {
		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "12")
						.param("amount", "0")
						.param("toAccountNumber", ""))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("계좌 비밀번호는 숫자 4자리입니다."),
						containsString("이체금액은 1원 이상이어야 합니다."),
						containsString("입금계좌번호를 입력하세요."))));
		verify(accountTransferService, never())
				.transfer(anyLong(), anyLong(), anyString(), anyString(), anyLong(), any(), any());
	}

	@Test
	void postWithoutCsrfIsRejected() throws Exception {
		mockMvc.perform(post("/mybank/transfer").with(user(me))
						.param("fromAccountId", "1"))
				// SecurityConfig : CSRF 토큰이 없거나 틀리면 403 대신 로그인 화면(?expired)으로 보낸다
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?expired"));
	}

	// ---------- 결과 ----------

	@Test
	void resultShowsReceipt() throws Exception {
		mockMvc.perform(get("/mybank/transfer/result").with(user(me)).flashAttr("receipt", receipt()))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("이체가 완료되었습니다."),
						containsString("김*수"),
						containsString("즐겨찾기 추가"),
						containsString("name=\"recipientAccountNumber\" value=\"TEST-9999\""),
						containsString("30,000원"),
						containsString("20261015143012-482915"))));
	}

	// 받는 분 계좌가 이미 즐겨찾기에 있으면 [즐겨찾기 추가] 대신 "등록됨"
	@Test
	void resultShowsAlreadyFavorite() throws Exception {
		given(accountFavoriteService.getMyFavoriteIdsByNumber(MEMBER_ID)).willReturn(Map.of("TEST-9999", 5L));

		mockMvc.perform(get("/mybank/transfer/result").with(user(me)).flashAttr("receipt", receipt()))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("★ 즐겨찾기 등록됨"),
						not(containsString("⭐ 즐겨찾기 추가")))));
	}

	// 새로고침 등으로 영수증이 없으면 입력 화면으로
	@Test
	void resultWithoutReceiptRedirectsToForm() throws Exception {
		mockMvc.perform(get("/mybank/transfer/result").with(user(me)))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/transfer"));
	}

	// ---------- fetch 조회 ----------

	@Test
	void recipientReturnsMaskedName() throws Exception {
		given(accountTransferService.findRecipient("TEST-9999")).willReturn(new RecipientInfo("TEST-9999", "김*수"));

		mockMvc.perform(get("/mybank/transfer/recipient").with(user(me)).param("accountNumber", "TEST-9999"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("김*수"));
	}

	@Test
	void recipientNotFoundReturnsMessage() throws Exception {
		given(accountTransferService.findRecipient("NONE"))
				.willThrow(new IllegalArgumentException("입금계좌를 찾을 수 없습니다."));

		mockMvc.perform(get("/mybank/transfer/recipient").with(user(me)).param("accountNumber", "NONE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("입금계좌를 찾을 수 없습니다."));
	}

	@Test
	void limitReturnsAmounts() throws Exception {
		given(accountTransferService.getLimit(MEMBER_ID, 1L))
				.willReturn(new TransferLimitInfo(5_000_000L, 1_000_000L, 300_000L));

		mockMvc.perform(get("/mybank/transfer/limit").with(user(me)).param("accountId", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dailyLimit").value(5_000_000))
				.andExpect(jsonPath("$.remaining").value(4_700_000));
	}
}
