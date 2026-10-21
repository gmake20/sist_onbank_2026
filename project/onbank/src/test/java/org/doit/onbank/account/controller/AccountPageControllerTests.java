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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.doit.onbank.account.dto.TransactionReceipt;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.account.entity.TransactionStatus;
import org.doit.onbank.account.entity.TransactionType;
import org.doit.onbank.account.service.AccountFavoriteService;
import org.doit.onbank.account.service.AccountService;
import org.doit.onbank.admin.security.AdminDetailsService;
import org.doit.onbank.admin.security.AdminLoginAttemptRecorder;
import org.doit.onbank.config.SecurityConfig;
import org.doit.onbank.employee.security.EmployeeDetailsService;
import org.doit.onbank.employee.security.EmployeeLoginAttemptRecorder;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.security.LoginMember;
import org.doit.onbank.member.security.MemberDetailsService;
import org.doit.onbank.member.security.MemberLoginAttemptRecorder;
import org.doit.onbank.account.repository.AssetReportRepository;
import org.doit.onbank.account.repository.LoanSummary;
import org.doit.onbank.account.repository.SavingsLastPaid;
import org.doit.onbank.savings.entity.SavingsAccount;
import org.doit.onbank.savings.entity.SavingsProduct;
import org.doit.onbank.savings.entity.SavingsStatus;
import org.doit.onbank.savings.entity.SavingsType;
import org.doit.onbank.savings.repository.SavingsAccountRepository;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

// 계좌 화면 테스트 : Controller + Thymeleaf 화면 + SecurityConfig 만 띄운다 (DB, JPA 없음)
// AccountService 는 가짜(Mock) → 화면이 오류 없이 그려지는지, 입력·이동 흐름이 맞는지 확인
@WebMvcTest(AccountPageController.class)
@Import(SecurityConfig.class)
class AccountPageControllerTests {

	private static final Long MEMBER_ID = 100L;
	private static final Long ACCOUNT_ID = 1L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccountService accountService;

	@MockitoBean
	private AccountFavoriteService accountFavoriteService;

	// 전계좌조회 예금·적금·대출 영역 : 팀3 savings_account, loan_account 조회 (가짜, 기본은 빈 목록)
	@MockitoBean
	private SavingsAccountRepository savingsAccountRepository;

	@MockitoBean
	private AssetReportRepository assetReportRepository;

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
	private Account account;

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

		account = account(AccountStatus.ACTIVE, 1_234_567L);
		given(accountService.getMyAccount(MEMBER_ID, ACCOUNT_ID)).willReturn(account);
	}

	private Account account(AccountStatus status, long balance) {
		Account account = Account.builder()
				.memberId(MEMBER_ID)
				.accountNumber("TEST-0001")
				.accountStatus(status)
				.balance(balance)
				.todayTransferAmount(0L)
				.lastTransactionAt(LocalDateTime.of(2026, 10, 15, 14, 30))
				.build();
		ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
		return account;
	}

	private TransactionHistory history(String type, long amount, long balanceAfter) {
		return TransactionHistory.builder()
				.accountId(ACCOUNT_ID)
				.transactionType(type)
				.transactionAmount(amount)
				.balanceAfterTransaction(balanceAfter)
				.transactionContent("테스트")
				.transactionAt(LocalDateTime.of(2026, 10, 15, 14, 30, 12))
				.transactionStatus(TransactionStatus.COMPLETED)
				.transactionSqno("20261015143012-482915")
				.build();
	}

	// ---------- 로그인 ----------

	@Test
	void loginRequired() throws Exception {
		mockMvc.perform(get("/mybank/accounts"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	// ---------- 계좌 목록 ----------

	@Test
	void listShowsAccountsAndTotal() throws Exception {
		Account frozen = account(AccountStatus.FROZEN, 1_000L);
		Account closed = account(AccountStatus.CLOSED, 5_000L);	// 해지 계좌는 합계 제외
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(account, frozen, closed));

		mockMvc.perform(get("/mybank/accounts").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("전계좌조회"),
						containsString("TEST-0001"),
						containsString("1,235,567원"),	// 1,234,567 + 1,000
						containsString("거래정지"),		// 정상이 아닌 상태만 최종거래일 아래에 표시
						containsString("해지"),
						containsString("/mybank/accounts/1/withdraw"),
						containsString("/mybank/transfer?from=1"))));
	}

	// 예금명 옆 즐겨찾기 : 등록된 계좌는 ★(해제), 아니면 ☆(등록). 둘 다 처리 후 전계좌조회로 돌아온다
	@Test
	void listShowsFavoriteToggle() throws Exception {
		Account other = Account.builder()
				.memberId(MEMBER_ID)
				.accountNumber("TEST-0003")
				.accountStatus(AccountStatus.ACTIVE)
				.balance(0L)
				.todayTransferAmount(0L)
				.build();
		ReflectionTestUtils.setField(other, "accountId", 3L);
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(account, other));
		given(accountFavoriteService.getMyFavoriteIdsByNumber(MEMBER_ID)).willReturn(Map.of("TEST-0001", 5L));

		mockMvc.perform(get("/mybank/accounts").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("/mybank/favorites/5/delete?returnTo=accounts"),
						containsString("즐겨찾기 해제"),
						containsString("name=\"recipientAccountNumber\" value=\"TEST-0003\""),
						containsString("name=\"returnTo\" value=\"accounts\""))));
	}

	// 예금·적금 계좌가 없으면 영역은 그대로 두고 "보유중인 상품이 없습니다." 표시
	@Test
	void listShowsEmptyMessage() throws Exception {
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of());

		mockMvc.perform(get("/mybank/accounts").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("보유중인 계좌가 없습니다."),
						containsString("예금/신탁"),
						containsString("적금"),
						containsString("보유중인 상품이 없습니다."))));
	}

	// (10-21) 예금·적금은 팀3 savings_account (진행 중만), 대출은 팀3 loan_account 에서 읽어 각 영역에 보여 준다
	@Test
	void listShowsSavingsAndLoans() throws Exception {
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(account));
		SavingsAccount deposit = savings(11L, "ONBANK 프리미엄 예금", SavingsType.FIXED_DEPOSIT, 10_000_000L);
		SavingsAccount installment = savings(12L, "ONBANK 미래설계 장기적금", SavingsType.INSTALLMENT_SAVINGS, 150_000L);
		SavingsAccount closed = savings(13L, "해지한 예금", SavingsType.FIXED_DEPOSIT, 999L);
		ReflectionTestUtils.setField(closed, "status", SavingsStatus.CLOSED);
		given(savingsAccountRepository.findByMemberIdOrderByIdDesc(MEMBER_ID))
				.willReturn(List.of(deposit, installment, closed));
		given(assetReportRepository.findActiveLoans(MEMBER_ID)).willReturn(List.of(loan()));
		given(assetReportRepository.findSavingsLastPaidOn(MEMBER_ID)).willReturn(List.of(new SavingsLastPaid() {
			public Long getSavingsAccountId() { return 12L; }
			public String getLastPaidOn() { return "2026.11.20"; }	// 적금 2회차 납입
		}));

		mockMvc.perform(get("/mybank/accounts").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("연결계좌"),		// 연결계좌 = 입출금 TEST-0001 (savings 의 sourceAccountId)
						containsString("2026.11.20"),	// 적금 최종거래일 = 마지막 납입일
						containsString("2026.10.20"),	// 예금은 납입 기록이 없어 가입일
						containsString("1,234,567원"),	// 입출금 총잔액에 예적금은 포함하지 않음
						containsString("ONBANK 프리미엄 예금"),
						containsString("10,000,000원"),	// 예금 총잔액
						containsString("ONBANK 미래설계 장기적금"),
						containsString("150,000원"),		// 적금 총잔액
						containsString("/savings/11"),
						containsString("ON 직장인 신용 대출"),
						containsString("4002-261-787347"),
						containsString("35,000,000원"),	// 대출잔액 합계
						containsString("/loan/repayment?accountId=2"),
						not(containsString("해지한 예금")))));
	}

	// 대출 조회가 실패해도 전계좌조회는 열리고 대출 영역에만 안내
	@Test
	void listOpensWhenLoanQueryFails() throws Exception {
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(account));
		given(assetReportRepository.findActiveLoans(MEMBER_ID))
				.willThrow(new InvalidDataAccessResourceUsageException("Unknown column"));

		mockMvc.perform(get("/mybank/accounts").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("TEST-0001"),
						containsString("대출 정보를 불러오지 못했습니다."))));
	}

	private SavingsAccount savings(Long id, String name, SavingsType type, long principal) {
		SavingsProduct product = new SavingsProduct(name, type, new BigDecimal("4.0000"), 36, 1L, 100_000_000L);
		SavingsAccount savings = new SavingsAccount(product, MEMBER_ID, ACCOUNT_ID, principal, LocalDate.of(2026, 10, 20));
		ReflectionTestUtils.setField(savings, "id", id);
		return savings;
	}

	private LoanSummary loan() {
		return new LoanSummary() {
			public Long getLoanAccountId() { return 2L; }
			public String getLoanAccountNumber() { return "4002-261-787347"; }
			public String getProductName() { return "ON 직장인 신용 대출"; }
			public Long getPrincipalAmount() { return 37_000_000L; }
			public Long getRemainingBalance() { return 35_000_000L; }
			public BigDecimal getInterestRate() { return new BigDecimal("4.5000"); }
			public String getMaturityDate() { return "2028-10-20"; }
			public String getNextDueDate() { return null; }
		};
	}

	// ---------- 거래내역 ----------

	@Test
	void detailShowsHistories() throws Exception {
		given(accountService.getMyHistories(MEMBER_ID, ACCOUNT_ID)).willReturn(List.of(
				history(TransactionType.WITHDRAWAL, 3_000L, 1_234_567L),
				history(TransactionType.DEPOSIT, 10_000L, 1_237_567L)));

		mockMvc.perform(get("/mybank/accounts/1").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("1,234,567원"),
						containsString("출금"),
						containsString("3,000"),
						containsString("2026.10.15 14:30:12"),
						containsString("20261015143012-482915"))));
	}

	@Test
	void detailShowsReceiptAfterRedirect() throws Exception {
		given(accountService.getMyHistories(MEMBER_ID, ACCOUNT_ID)).willReturn(List.of());
		TransactionReceipt receipt = TransactionReceipt.from(history(TransactionType.DEPOSIT, 10_000L, 20_000L));

		mockMvc.perform(get("/mybank/accounts/1").with(user(me)).flashAttr("receipt", receipt))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("입금이 완료되었습니다."),
						containsString("20,000원"))));
	}

	// 남의 계좌·없는 계좌 → 목록으로
	@Test
	void otherMembersAccountRedirectsToList() throws Exception {
		given(accountService.getMyAccount(MEMBER_ID, 99L)).willThrow(new NoSuchElementException("계좌가 없습니다."));

		mockMvc.perform(get("/mybank/accounts/99").with(user(me)))
				.andExpect(redirectedUrl("/mybank/accounts"))
				.andExpect(flash().attribute("errorMessage", "계좌가 없습니다."));
	}

	// ---------- 입금 ----------

	@Test
	void depositFormRenders() throws Exception {
		mockMvc.perform(get("/mybank/accounts/1/deposit").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("입금계좌번호"),
						containsString("1,234,567원"),
						containsString("_csrf"))));
	}

	@Test
	void depositSuccessRedirectsWithReceipt() throws Exception {
		given(accountService.deposit(ACCOUNT_ID, 10_000L, "용돈"))
				.willReturn(history(TransactionType.DEPOSIT, 10_000L, 1_244_567L));

		mockMvc.perform(post("/mybank/accounts/1/deposit").with(user(me)).with(csrf())
				.param("amount", "10000").param("content", "용돈"))
				.andExpect(redirectedUrl("/mybank/accounts/1"))
				.andExpect(flash().attributeExists("receipt"));
	}

	@Test
	void depositInvalidAmountShowsError() throws Exception {
		mockMvc.perform(post("/mybank/accounts/1/deposit").with(user(me)).with(csrf())
				.param("amount", "0"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("금액은 1원 이상이어야 합니다.")));
		verify(accountService, never()).deposit(anyLong(), anyLong(), any());
	}

	@Test
	void depositRejectedShowsMessage() throws Exception {
		given(accountService.deposit(anyLong(), anyLong(), any()))
				.willThrow(new IllegalStateException("입금할 수 없는 계좌 상태입니다: CLOSED"));

		mockMvc.perform(post("/mybank/accounts/1/deposit").with(user(me)).with(csrf())
				.param("amount", "10000"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("입금할 수 없는 계좌 상태입니다")));
	}

	// ---------- 출금 ----------

	@Test
	void withdrawFormRenders() throws Exception {
		mockMvc.perform(get("/mybank/accounts/1/withdraw").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("계좌비밀번호"),
						containsString("data-all=\"1234567\""))));
	}

	@Test
	void withdrawSuccessRedirectsWithReceipt() throws Exception {
		given(accountService.withdraw(MEMBER_ID, ACCOUNT_ID, 3_000L, "1234", null))
				.willReturn(history(TransactionType.WITHDRAWAL, 3_000L, 1_231_567L));

		mockMvc.perform(post("/mybank/accounts/1/withdraw").with(user(me)).with(csrf())
				.param("amount", "3000").param("password", "1234"))
				.andExpect(redirectedUrl("/mybank/accounts/1"))
				.andExpect(flash().attributeExists("receipt"));
	}

	// 비밀번호가 틀리면 메시지와 오류 횟수를 보여 주고, 입력한 비밀번호는 화면에 남기지 않는다
	@Test
	void withdrawWrongPasswordShowsFailCount() throws Exception {
		account.increasePasswordFailCount();
		given(accountService.withdraw(anyLong(), anyLong(), anyLong(), anyString(), any()))
				.willThrow(new IllegalArgumentException("계좌 비밀번호가 일치하지 않습니다."));

		mockMvc.perform(post("/mybank/accounts/1/withdraw").with(user(me)).with(csrf())
				.param("amount", "3000").param("password", "9876"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("계좌 비밀번호가 일치하지 않습니다."),
						containsString("비밀번호 오류 1회"),
						not(containsString("9876")))));
	}

	@Test
	void withdrawPasswordFormatChecked() throws Exception {
		mockMvc.perform(post("/mybank/accounts/1/withdraw").with(user(me)).with(csrf())
				.param("amount", "3000").param("password", "12"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("계좌 비밀번호는 숫자 4자리입니다.")));
		verify(accountService, never()).withdraw(anyLong(), anyLong(), anyLong(), any(), any());
	}

	@Test
	void postWithoutCsrfIsRejected() throws Exception {
		// SecurityConfig : CSRF 토큰이 없거나 틀리면 403 대신 로그인 화면(?expired)으로 보낸다
		mockMvc.perform(post("/mybank/accounts/1/deposit").with(user(me)).param("amount", "10000"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login?expired"));
	}
}
