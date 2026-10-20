package org.doit.onbank.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.doit.onbank.account.dto.FavoriteGroups;
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
import org.doit.onbank.account.service.OtpRequiredException;
import org.doit.onbank.account.service.ScheduledTransferService;
import org.doit.onbank.account.entity.ScheduledTransfer;
import org.doit.onbank.common.exception.BusinessException;
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

	@MockitoBean
	private ScheduledTransferService scheduledTransferService;

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

		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(
				account(1L, "TEST-0001", AccountStatus.ACTIVE, 100_000L),
				account(2L, "TEST-0002", AccountStatus.FROZEN, 50_000L)));	// 동결 계좌는 출금계좌 목록에서 빠짐
		given(accountFavoriteService.getMyFavoriteGroups(MEMBER_ID))
				.willReturn(new FavoriteGroups(List.of(), List.of()));
	}

	private AccountFavorite favorite(Long baseAccountId, String number, String name, String alias) {
		return AccountFavorite.builder()
				.accountId(baseAccountId)
				.recipientAccountNumber(number)
				.recipientAccountName(name)
				.favoriteAlias(alias)
				.build();
	}

	private Account account(Long id, String number, AccountStatus status, long balance) {
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

	// 입금계좌 선택 창 : 상대계좌 즐겨찾기(기본 출금계좌 포함), 최근입금계좌, 본인계좌(입금 가능한 계좌)
	@Test
	void formShowsPickerLists() throws Exception {
		AccountFavorite favorite = favorite(1L, "TEST-7777", "박*희", "월세");
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
		given(accountFavoriteService.getMyFavoriteGroups(MEMBER_ID))
				.willReturn(new FavoriteGroups(List.of(), List.of(favorite)));
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

	// 내 계좌 즐겨찾기 : 출금 가능한 계좌만 선택 창에 나오고, 출금계좌 목록 맨 위(기본 선택)에 온다
	@Test
	void formShowsMyAccountFavoritesFirst() throws Exception {
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(
				account(1L, "TEST-0001", AccountStatus.ACTIVE, 100_000L),
				account(2L, "TEST-0002", AccountStatus.FROZEN, 50_000L),
				account(3L, "TEST-0003", AccountStatus.ACTIVE, 30_000L)));
		given(accountFavoriteService.getMyFavoriteGroups(MEMBER_ID)).willReturn(new FavoriteGroups(
				List.of(favorite(3L, "TEST-0003", "테*트", "생활비"), favorite(2L, "TEST-0002", "테*트", "동결통장")),
				List.of()));

		String html = mockMvc.perform(get("/mybank/transfer").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("생활비"),
						containsString("class=\"btn-sm js-pick-from\" data-account-number=\"TEST-0003\""),
						not(containsString("동결통장")))))	// 동결 계좌는 출금계좌로 고를 수 없음
				.andReturn().getResponse().getContentAsString();

		assertThat(html.indexOf("ON 통장 (TEST-0003)")).isLessThan(html.indexOf("ON 통장 (TEST-0001)"));
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
		given(accountTransferService.transfer(MEMBER_ID, 1L, "1234", null, "TEST-9999", 30_000L, "", ""))
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
		given(accountTransferService.transfer(MEMBER_ID, 1L, "1234", null, "TEST-9999", 30_000L, null, null))
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
				.transfer(anyLong(), anyLong(), anyString(), any(), anyString(), anyLong(), any(), any());
	}

	// ---------- 이체 인증 (OTP) ----------

	@Test
	void formHasOtpGuideValues() throws Exception {
		given(accountTransferService.isOtpReady(MEMBER_ID)).willReturn(true);

		mockMvc.perform(get("/mybank/transfer").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("data-otp-required-amount=\"" + AccountTransferService.OTP_REQUIRED_AMOUNT + "\""),
						containsString("data-otp-ready=\"true\""),
						containsString("name=\"otpCode\""))));
	}

	@Test
	void transferPassesOtpCodeToService() throws Exception {
		given(accountTransferService.transfer(MEMBER_ID, 1L, "1234", "123456", "TEST-9999", 300_000L, null, null))
				.willReturn(receipt());

		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("otpCode", "123456")
						.param("amount", "300000")
						.param("toAccountNumber", "TEST-9999"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/transfer/result"));
	}

	// OTP 미등록 회원의 기준 금액 초과 이체 : 안내 문구 + [OTP 등록하기]
	@Test
	void transferWithoutOtpShowsRegisterLink() throws Exception {
		given(accountTransferService.transfer(MEMBER_ID, 1L, "1234", null, "TEST-9999", 300_000L, null, null))
				.willThrow(new OtpRequiredException("100,000원을 넘는 이체는 OTP 등록이 필요해요."));

		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("amount", "300000")
						.param("toAccountNumber", "TEST-9999"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("100,000원을 넘는 이체는 OTP 등록이 필요해요."),
						containsString("<a class=\"btn-sm js-otp-register\" href=\"/mybank/security/otp\">OTP 등록하기</a>"))));
	}

	// OTP 번호 오류 (팀1 OtpService 의 BusinessException) : 문구를 보여 주고 OTP 번호는 남기지 않는다
	@Test
	void transferWithWrongOtpShowsMessage() throws Exception {
		given(accountTransferService.transfer(MEMBER_ID, 1L, "1234", "999999", "TEST-9999", 300_000L, null, null))
				.willThrow(new BusinessException("OTP 번호가 올바르지 않습니다. (오류 1/10회)"));

		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("otpCode", "999999")
						.param("amount", "300000")
						.param("toAccountNumber", "TEST-9999"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("OTP 번호가 올바르지 않습니다. (오류 1/10회)"),
						not(containsString("value=\"999999\"")))));
	}

	@Test
	void transferRejectsMalformedOtpCode() throws Exception {
		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("otpCode", "12ab")
						.param("amount", "300000")
						.param("toAccountNumber", "TEST-9999"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("OTP 번호는 숫자 6자리입니다.")));
		verify(accountTransferService, never())
				.transfer(anyLong(), anyLong(), anyString(), any(), anyString(), anyLong(), any(), any());
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

	// ---------- 예약이체 등록 ----------

	// 일시를 보내면 이체하지 않고 예약만 등록한 뒤 예약 목록으로
	@Test
	void scheduledTransferReservesAndRedirectsToList() throws Exception {
		LocalDateTime at = LocalDateTime.of(2026, 12, 1, 9, 30);
		given(scheduledTransferService.reserve(MEMBER_ID, 1L, "1234", null, "TEST-9999", 50_000L, null, null, at))
				.willReturn(ScheduledTransfer.reserve(MEMBER_ID, 1L, "TEST-9999", 50_000L, null, null, at, LocalDateTime.now()));

		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("amount", "50000")
						.param("toAccountNumber", "TEST-9999")
						.param("executeDate", "2026-12-01")
						.param("executeTime", "09:30"))
				.andExpect(redirectedUrl("/mybank/transfer/scheduled"))
				.andExpect(flash().attribute("successMessage", "예약이체를 등록했습니다. 2026.12.01 09:30에 50,000원이 이체됩니다."));
		verify(accountTransferService, never()).transfer(anyLong(), anyLong(), any(), any(), any(), anyLong(), any(), any());
	}

	// 등록 실패 : 문구를 보여 주고, 입력했던 예약 일시는 남겨 다시 예약 모드로 열리게 한다
	@Test
	void scheduledTransferFailureKeepsExecuteAt() throws Exception {
		given(scheduledTransferService.reserve(anyLong(), anyLong(), any(), any(), any(), anyLong(), any(), any(), any()))
				.willThrow(new IllegalArgumentException("예약 일시는 지금 이후로 선택하세요."));

		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("amount", "50000")
						.param("toAccountNumber", "TEST-9999")
						.param("executeDate", "2026-01-01")
						.param("executeTime", "09:30"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("예약 일시는 지금 이후로 선택하세요."),
						containsString("value=\"2026-01-01\""),
						containsString("value=\"09:30\""))));
	}

	// ---------- 정기이체 등록 ----------

	// 매월 날짜·시각을 보내면 이체하지 않고 정기이체만 등록한 뒤 목록으로
	@Test
	void recurringTransferSubscribesAndRedirectsToList() throws Exception {
		LocalDateTime first = LocalDateTime.of(2026, 10, 25, 9, 0);
		LocalDate endDate = LocalDate.of(2027, 10, 25);
		given(scheduledTransferService.subscribe(MEMBER_ID, 1L, "1234", null, "TEST-9999", 50_000L, null, null,
				25, LocalTime.of(9, 0), endDate))
				.willReturn(ScheduledTransfer.subscribe(MEMBER_ID, 1L, "TEST-9999", 50_000L, null, null, 25, first, endDate,
						LocalDateTime.now()));

		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("amount", "50000")
						.param("toAccountNumber", "TEST-9999")
						.param("scheduleMode", "recurring")	// 화면 전환용 라디오 : 서버는 무시
						.param("transferDay", "25")
						.param("recurringTime", "09:00")
						.param("endDate", "2027-10-25"))
				.andExpect(redirectedUrl("/mybank/transfer/scheduled"))
				.andExpect(flash().attribute("successMessage",
						"정기이체를 등록했습니다. 매월 25일 50,000원이 이체되며, 첫 이체는 2026.10.25 09:00입니다."));
		verify(accountTransferService, never()).transfer(anyLong(), anyLong(), any(), any(), any(), anyLong(), any(), any());
	}

	// 등록 실패 : 문구를 보여 주고, 입력했던 정기 시각·종료일은 남겨 다시 정기 모드로 열리게 한다
	@Test
	void recurringTransferFailureKeepsInput() throws Exception {
		given(scheduledTransferService.subscribe(anyLong(), anyLong(), any(), any(), any(), anyLong(), any(), any(),
				any(), any(), any()))
				.willThrow(new IllegalArgumentException("종료일은 첫 이체일(2026-10-25) 이후로 선택하세요."));

		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("amount", "50000")
						.param("toAccountNumber", "TEST-9999")
						.param("transferDay", "25")
						.param("recurringTime", "09:00")
						.param("endDate", "2026-10-01"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("종료일은 첫 이체일(2026-10-25) 이후로 선택하세요."),
						containsString("value=\"2026-10-01\""),
						containsString("value=\"09:00\""))));
	}

	// 매월 날짜가 범위를 벗어나면 입력 검증에서 막는다
	@Test
	void recurringTransferRejectsInvalidDay() throws Exception {
		mockMvc.perform(post("/mybank/transfer").with(user(me)).with(csrf())
						.param("fromAccountId", "1")
						.param("password", "1234")
						.param("amount", "50000")
						.param("toAccountNumber", "TEST-9999")
						.param("transferDay", "32")
						.param("recurringTime", "09:00"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("정기이체 날짜는 1일부터 31일 사이로 선택하세요.")));
		verify(scheduledTransferService, never()).subscribe(anyLong(), anyLong(), any(), any(), any(), anyLong(), any(),
				any(), any(), any(), any());
	}
}
