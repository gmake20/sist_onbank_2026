package org.doit.onbank.account.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.doit.onbank.account.dto.AssetReportView;
import org.doit.onbank.account.dto.AssetReportView.ItemBar;
import org.doit.onbank.account.dto.AssetReportView.LoanRow;
import org.doit.onbank.account.dto.AssetReportView.MonthBar;
import org.doit.onbank.account.dto.AssetReportView.Slice;
import org.doit.onbank.account.dto.AssetReportView.Summary;
import org.doit.onbank.account.service.AssetReportService;
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

// 자산관리 화면 테스트 : Controller + Thymeleaf 화면 + SecurityConfig 만 띄운다 (DB, JPA 없음)
@WebMvcTest(AssetReportPageController.class)
@Import(SecurityConfig.class)
class AssetReportPageControllerTests {

	private static final Long MEMBER_ID = 100L;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AssetReportService assetReportService;

	// OnbankApplication 의 @EnableJpaAuditing 이 JPA 설정을 찾으므로 가짜로 채운다
	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	// SecurityConfig 로그인 설정 3개 + 로그인 실패 잠금 핸들러가 요구하는 Bean
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

	private final YearMonth month = YearMonth.of(2026, 10);

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

	private AssetReportView report(List<String> warnings) {
		return new AssetReportView(month, List.of(month, month.minusMonths(1)),
				200_000L, 9_000_000L, -8_800_000L,
				new Summary(200_000L, 60_000L, 200_000L, 60_000L),
				List.of(new Slice("입출금", "series-1", 200_000L, 100.0, "100.00 0.00", "-0.00"),
						new Slice("예금", "series-2", 0L, 0, "", ""),
						new Slice("적금", "series-3", 0L, 0, "", "")),
				List.of(new MonthBar(month.minusMonths(1), 0L, 0L, 0, 0, false),
						new MonthBar(month, 200_000L, 60_000L, 100.0, 30.0, true)),
				200_000L,
				List.of(new ItemBar("저축액", 0L, 0, "예금·적금 계좌로 들어온 금액", true),
						new ItemBar("투자 금액", 0L, 0, "투자 상품 연결 예정", false)),
				List.of(new LoanRow("직장인 신용대출", 10_000_000L, 9_000_000L, 10.0, new BigDecimal("4.0000"),
						30_000L, "2028-10-19", null)),
				List.of(), warnings, LocalDateTime.of(2026, 10, 19, 15, 20));
	}

	@Test
	void headerLinkRedirectsToReport() throws Exception {
		mockMvc.perform(get("/asset"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/mybank/assets"));
	}

	@Test
	void reportRequiresLogin() throws Exception {
		mockMvc.perform(get("/mybank/assets"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	void reportShowsSummaryChartsAndLoans() throws Exception {
		given(assetReportService.getReport(eq(MEMBER_ID), isNull())).willReturn(report(List.of()));

		mockMvc.perform(get("/mybank/assets").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("2026년 10월 자산 리포트"),
						containsString("-8,800,000원"),					// 순자산
						containsString("▲ 지난달보다 200,000원 늘었어요"),	// 지난달 대비 (기호 + 문장)
						containsString("stroke-dasharray=\"100.00 0.00\""),	// 도넛 조각
						containsString("height: 30.0%"),					// 추이 막대
						containsString("표로 보기"),
						containsString("준비 중"),							// 투자 금액
						containsString("직장인 신용대출"),
						containsString("30,000원"),
						containsString("가입한 예적금이 없습니다."))));
	}

	@Test
	void reportPassesSelectedMonth() throws Exception {
		given(assetReportService.getReport(MEMBER_ID, YearMonth.of(2026, 9))).willReturn(report(List.of()));

		mockMvc.perform(get("/mybank/assets").param("month", "2026-09").with(user(me)))
				.andExpect(status().isOk());
	}

	@Test
	void wrongMonthFormatIsIgnored() throws Exception {
		given(assetReportService.getReport(eq(MEMBER_ID), isNull())).willReturn(report(List.of()));

		mockMvc.perform(get("/mybank/assets").param("month", "abc").with(user(me)))
				.andExpect(status().isOk());
	}

	@Test
	void reportShowsWarnings() throws Exception {
		given(assetReportService.getReport(any(), any()))
				.willReturn(report(List.of("대출 정보를 불러오지 못했습니다. (대출·예적금 테이블 변경 확인 필요)")));

		mockMvc.perform(get("/mybank/assets").with(user(me)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("대출 정보를 불러오지 못했습니다.")));
	}
}
