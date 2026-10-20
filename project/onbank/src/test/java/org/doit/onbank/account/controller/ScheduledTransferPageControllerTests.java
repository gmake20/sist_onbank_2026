package org.doit.onbank.account.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
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
import java.util.List;
import java.util.NoSuchElementException;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.ScheduledTransfer;
import org.doit.onbank.account.service.AccountService;
import org.doit.onbank.account.service.ScheduledTransferRunner;
import org.doit.onbank.account.service.ScheduledTransferService;
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

// 예약이체 조회·취소 화면 테스트 : Controller + Thymeleaf 화면 + SecurityConfig 만 띄운다 (DB, JPA 없음)
@WebMvcTest(ScheduledTransferPageController.class)
@Import(SecurityConfig.class)
class ScheduledTransferPageControllerTests {

	private static final Long MEMBER_ID = 100L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ScheduledTransferService scheduledTransferService;

	@MockitoBean
	private ScheduledTransferRunner scheduledTransferRunner;

	@MockitoBean
	private AccountService accountService;

	// 아래는 TransferPageControllerTests 와 같은 이유로 넣는 가짜 Bean (JPA 설정, 로그인 설정)
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

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

		Account account = Account.builder().memberId(MEMBER_ID).accountNumber("1002-111-111111")
				.accountStatus(AccountStatus.ACTIVE).balance(0L).build();
		ReflectionTestUtils.setField(account, "accountId", 1L);
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(account));
	}

	private ScheduledTransfer reserved(Long id, LocalDateTime at) {
		ScheduledTransfer transfer = ScheduledTransfer.reserve(MEMBER_ID, 1L, "1002-222-222222", 50_000L,
				null, null, at, LocalDateTime.now());
		ReflectionTestUtils.setField(transfer, "scheduledTransferId", id);
		return transfer;
	}

	// 목록 : 출금계좌는 계좌번호로, 대기 건에만 [취소]
	@Test
	void listShowsTransfersAndCancelOnlyForWaiting() throws Exception {
		ScheduledTransfer waiting = reserved(7L, LocalDateTime.of(2026, 12, 1, 9, 30));
		ScheduledTransfer failed = reserved(8L, LocalDateTime.of(2026, 10, 1, 9, 0));
		failed.markFailed("잔액이 부족합니다.", LocalDateTime.of(2026, 10, 1, 9, 0));
		given(scheduledTransferService.getMine(MEMBER_ID)).willReturn(List.of(waiting, failed));

		mockMvc.perform(get("/mybank/transfer/scheduled").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("2026.12.01 09:30"),
						containsString("1002-111-111111"),			// 출금계좌 ID → 계좌번호
						containsString("50,000원"),
						containsString("잔액이 부족합니다."),
						containsString("/mybank/transfer/scheduled/7/cancel"),
						not(containsString("/mybank/transfer/scheduled/8/cancel")))));	// 실패 건은 취소 버튼 없음
	}

	@Test
	void listEmpty() throws Exception {
		given(scheduledTransferService.getMine(MEMBER_ID)).willReturn(List.of());

		mockMvc.perform(get("/mybank/transfer/scheduled").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("등록한 예약이체가 없습니다.")));
	}

	@Test
	void cancelRedirectsWithMessage() throws Exception {
		mockMvc.perform(post("/mybank/transfer/scheduled/7/cancel").with(user(me)).with(csrf()))
				.andExpect(redirectedUrl("/mybank/transfer/scheduled"))
				.andExpect(flash().attribute("successMessage", "예약이체를 취소했습니다."));
	}

	// 남의 예약이거나 없는 번호 : 오류 문구만 보여 준다
	@Test
	void cancelOthersShowsError() throws Exception {
		willThrow(new NoSuchElementException("예약이체를 찾을 수 없습니다."))
				.given(scheduledTransferService).cancel(MEMBER_ID, 99L);

		mockMvc.perform(post("/mybank/transfer/scheduled/99/cancel").with(user(me)).with(csrf()))
				.andExpect(redirectedUrl("/mybank/transfer/scheduled"))
				.andExpect(flash().attribute("errorMessage", "예약이체를 찾을 수 없습니다."));
	}

	// ---------- [지금 실행] ----------

	@Test
	void runNowSuccessShowsAmount() throws Exception {
		ScheduledTransfer done = reserved(7L, LocalDateTime.of(2026, 12, 1, 9, 30));
		done.markSucceeded(LocalDateTime.now());
		given(scheduledTransferRunner.runNow(MEMBER_ID, 7L)).willReturn(done);

		mockMvc.perform(post("/mybank/transfer/scheduled/7/run").with(user(me)).with(csrf()))
				.andExpect(redirectedUrl("/mybank/transfer/scheduled"))
				.andExpect(flash().attribute("successMessage", "예약이체를 실행했습니다. 50,000원을 이체했습니다."));
	}

	// 실행했지만 이체 실패 (잔액 부족 등) : 실패 사유를 보여 준다
	@Test
	void runNowFailureShowsReason() throws Exception {
		ScheduledTransfer failed = reserved(7L, LocalDateTime.of(2026, 12, 1, 9, 30));
		failed.markFailed("잔액이 부족합니다.", LocalDateTime.now());
		given(scheduledTransferRunner.runNow(MEMBER_ID, 7L)).willReturn(failed);

		mockMvc.perform(post("/mybank/transfer/scheduled/7/run").with(user(me)).with(csrf()))
				.andExpect(redirectedUrl("/mybank/transfer/scheduled"))
				.andExpect(flash().attribute("errorMessage", "예약이체를 실행하지 못했습니다. 잔액이 부족합니다."));
	}

	@Test
	void listShowsRunButtonForWaiting() throws Exception {
		given(scheduledTransferService.getMine(MEMBER_ID))
				.willReturn(List.of(reserved(7L, LocalDateTime.of(2026, 12, 1, 9, 30))));

		mockMvc.perform(get("/mybank/transfer/scheduled").with(user(me)))
				.andExpect(content().string(containsString("/mybank/transfer/scheduled/7/run")));
	}
}
