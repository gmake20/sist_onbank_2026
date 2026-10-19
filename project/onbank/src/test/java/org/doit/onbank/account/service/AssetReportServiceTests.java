package org.doit.onbank.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.doit.onbank.account.dto.AssetReportView;
import org.doit.onbank.account.dto.AssetReportView.ItemBar;
import org.doit.onbank.account.dto.AssetReportView.Slice;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.entity.AssetReport;
import org.doit.onbank.account.repository.AssetReportRepository;
import org.doit.onbank.account.repository.LoanSummary;
import org.doit.onbank.account.repository.MonthlyAmount;
import org.doit.onbank.account.repository.MonthlyCashFlow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.test.util.ReflectionTestUtils;

// AssetReportService 단위 테스트 : DB 대신 가짜(Mock) Repository
// 확인하는 것 : 자산 구성·순자산 계산, 월별 저장(새로 만들기/갱신), 지난달 대비, 팀3 조회 실패 시 안내
// 확인하지 못하는 것 : 집계 SQL 자체 → 공용 DB에서 조회로 확인 (handoff 참고)
@ExtendWith(MockitoExtension.class)
class AssetReportServiceTests {

	private static final Long MEMBER_ID = 1L;
	private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

	@Mock
	private AssetReportRepository assetReportRepository;

	@Mock
	private AccountService accountService;

	@InjectMocks
	private AssetReportService service;

	private final YearMonth thisMonth = YearMonth.now();

	@BeforeEach
	void setUp() {
		// saveAll 은 받은 목록을 그대로 돌려준다 (DB가 ID를 넣는 것은 확인하지 않음)
		lenient().when(assetReportRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(assetReportRepository.findMonthlyCashFlows(eq(MEMBER_ID), any(), any())).thenReturn(List.of());
		lenient().when(assetReportRepository.findMonthlyLoanPayments(eq(MEMBER_ID), any(), any())).thenReturn(List.of());
		lenient().when(assetReportRepository.findMonthlyCardUsage(eq(MEMBER_ID), any(), any())).thenReturn(List.of());
		lenient().when(assetReportRepository.findApprovedLoans(MEMBER_ID)).thenReturn(List.of());
		lenient().when(assetReportRepository.findActiveSubscriptions(MEMBER_ID)).thenReturn(List.of());
		lenient().when(assetReportRepository.findByAccountIdAndStandardYearMonthIn(eq(MEMBER_ID), anyCollection()))
				.thenReturn(List.of());
	}

	private Account account(String type, String status, long balance) {
		return Account.builder().memberId(MEMBER_ID).accountType(type).accountStatus(status).balance(balance).build();
	}

	private MonthlyCashFlow flow(YearMonth month, long in, long out, long savings, long utility, long maintenance) {
		return new MonthlyCashFlow() {
			public String getYm() { return month.format(YM); }
			public Long getInAmount() { return in; }
			public Long getOutAmount() { return out; }
			public Long getSavingsAmount() { return savings; }
			public Long getUtilityBillAmount() { return utility; }
			public Long getMaintenanceFeeAmount() { return maintenance; }
		};
	}

	private MonthlyAmount amount(YearMonth month, long value) {
		return new MonthlyAmount() {
			public String getYm() { return month.format(YM); }
			public Long getAmount() { return value; }
		};
	}

	private LoanSummary loan(long approved, long repaid, String rate) {
		return new LoanSummary() {
			public Long getApplicationId() { return 8L; }
			public String getProductName() { return "직장인 신용대출"; }
			public Long getApprovedAmount() { return approved; }
			public BigDecimal getInterestRate() { return new BigDecimal(rate); }
			public String getMaturityDate() { return "2028-10-19"; }
			public Long getRepaidPrincipal() { return repaid; }
			public String getNextDueDate() { return null; }
		};
	}

	// ---------- 자산 구성 · 순자산 ----------

	@Test
	void compositionSumsActiveAndFrozenBalancesByType() {
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(
				account(null, AccountStatus.ACTIVE, 100_000L),				// 유형 없음 → 입출금
				account(AccountType.CHECKING, AccountStatus.FROZEN, 50_000L),	// 동결도 자산
				account(AccountType.SAVINGS, AccountStatus.ACTIVE, 50_000L),
				account(AccountType.CHECKING, AccountStatus.PENDING, 999L),	// 개설대기 제외
				account(AccountType.DEPOSIT, AccountStatus.CLOSED, 999L)));	// 해지 제외

		AssetReportView view = service.getReport(MEMBER_ID, thisMonth);

		assertThat(view.totalAssets()).isEqualTo(200_000L);
		assertThat(view.composition()).extracting(Slice::label).containsExactly("입출금", "예금", "적금");
		assertThat(view.composition()).extracting(Slice::amount).containsExactly(150_000L, 0L, 50_000L);
		assertThat(view.composition().get(0).percent()).isEqualTo(75.0);
		assertThat(view.composition().get(1).dashArray()).isEmpty();		// 0원은 도넛 조각 없음
		assertThat(view.composition().get(2).dashOffset()).isEqualTo("-75.00");	// 입출금 다음부터 시작
	}

	@Test
	void netAssetsSubtractsRemainingLoanPrincipal() {
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(account(null, AccountStatus.ACTIVE, 500_000L)));
		given(assetReportRepository.findApprovedLoans(MEMBER_ID)).willReturn(List.of(loan(10_000_000L, 1_000_000L, "4.0000")));

		AssetReportView view = service.getReport(MEMBER_ID, thisMonth);

		assertThat(view.loanBalance()).isEqualTo(9_000_000L);
		assertThat(view.netAssets()).isEqualTo(-8_500_000L);
		assertThat(view.loans().get(0).repaidPercent()).isEqualTo(10.0);
		assertThat(view.loans().get(0).monthlyInterestEstimate()).isEqualTo(30_000L);	// 900만 × 4% ÷ 12
	}

	// ---------- 월별 저장 ----------

	@Test
	void savesSixMonthsAndComparesWithPreviousMonth() {
		YearMonth last = thisMonth.minusMonths(1);
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of());
		given(assetReportRepository.findMonthlyCashFlows(eq(MEMBER_ID), any(), any())).willReturn(List.of(
				flow(last, 100_000L, 40_000L, 0L, 0L, 0L),
				flow(thisMonth, 200_000L, 30_000L, 50_000L, 12_000L, 80_000L)));
		given(assetReportRepository.findMonthlyLoanPayments(eq(MEMBER_ID), any(), any()))
				.willReturn(List.of(amount(thisMonth, 300_000L)));

		AssetReportView view = service.getReport(MEMBER_ID, thisMonth);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<AssetReport>> saved = ArgumentCaptor.forClass(List.class);
		verify(assetReportRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).hasSize(AssetReportService.TREND_MONTHS);
		AssetReport current = saved.getValue().get(AssetReportService.TREND_MONTHS - 1);
		assertThat(current.getAccountId()).isEqualTo(MEMBER_ID);	// 회원 ID 저장
		assertThat(current.getStandardYearMonth()).isEqualTo(thisMonth.format(YM));
		assertThat(current.getSavingsAmount()).isEqualTo(50_000L);
		assertThat(current.getLoanInterestRepaymentAmount()).isEqualTo(300_000L);
		assertThat(current.getUtilityBillAmount()).isEqualTo(12_000L);
		assertThat(current.getMaintenanceFeeAmount()).isEqualTo(80_000L);
		assertThat(current.getInvestmentAmount()).isNull();	// 출처 없음

		assertThat(view.summary().inDiff()).isEqualTo(100_000L);
		assertThat(view.summary().outDiff()).isEqualTo(-10_000L);
		assertThat(view.trendMax()).isEqualTo(200_000L);
		assertThat(view.trend().get(5).selected()).isTrue();
		assertThat(view.trend().get(5).inHeight()).isEqualTo(100.0);
	}

	@Test
	void updatesExistingRowInsteadOfAddingNew() {
		AssetReport existing = AssetReport.builder().accountId(MEMBER_ID)
				.standardYearMonth(thisMonth.format(YM)).recentDepositAmount(1L).build();
		ReflectionTestUtils.setField(existing, "assetReportId", 77L);
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of());
		given(assetReportRepository.findByAccountIdAndStandardYearMonthIn(eq(MEMBER_ID), anyCollection()))
				.willReturn(new ArrayList<>(List.of(existing)));
		given(assetReportRepository.findMonthlyCashFlows(eq(MEMBER_ID), any(), any()))
				.willReturn(List.of(flow(thisMonth, 5_000L, 0L, 0L, 0L, 0L)));

		service.getReport(MEMBER_ID, thisMonth);

		assertThat(existing.getRecentDepositAmount()).isEqualTo(5_000L);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<AssetReport>> saved = ArgumentCaptor.forClass(List.class);
		verify(assetReportRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).contains(existing);
	}

	// ---------- 기준월 · 팀3 조회 실패 ----------

	@Test
	void outOfRangeMonthFallsBackToThisMonth() {
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of());

		assertThat(service.getReport(MEMBER_ID, thisMonth.plusMonths(1)).month()).isEqualTo(thisMonth);
		assertThat(service.getReport(MEMBER_ID, thisMonth.minusMonths(12)).month()).isEqualTo(thisMonth);
		assertThat(service.getReport(MEMBER_ID, thisMonth.minusMonths(11)).month()).isEqualTo(thisMonth.minusMonths(11));
	}

	@Test
	void team3QueryFailureShowsWarningAndKeepsPage() {
		given(accountService.getMyAccounts(MEMBER_ID)).willReturn(List.of(account(null, AccountStatus.ACTIVE, 10_000L)));
		given(assetReportRepository.findApprovedLoans(MEMBER_ID))
				.willThrow(new InvalidDataAccessResourceUsageException("Unknown column"));
		given(assetReportRepository.findMonthlyLoanPayments(eq(MEMBER_ID), any(), any()))
				.willThrow(new InvalidDataAccessResourceUsageException("Unknown column"));

		AssetReportView view = service.getReport(MEMBER_ID, thisMonth);

		assertThat(view.warnings()).hasSize(2).allMatch(w -> w.contains("불러오지 못했습니다"));
		assertThat(view.totalAssets()).isEqualTo(10_000L);
		ItemBar loanItem = view.items().get(1);
		assertThat(loanItem.connected()).isFalse();
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<AssetReport>> saved = ArgumentCaptor.forClass(List.class);
		verify(assetReportRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).allMatch(r -> r.getLoanInterestRepaymentAmount() == null);	// 모르면 0 대신 NULL
	}
}
