package org.doit.onbank.account.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.doit.onbank.account.dto.AssetReportView;
import org.doit.onbank.account.dto.AssetReportView.ItemBar;
import org.doit.onbank.account.dto.AssetReportView.LoanRow;
import org.doit.onbank.account.dto.AssetReportView.MonthBar;
import org.doit.onbank.account.dto.AssetReportView.Slice;
import org.doit.onbank.account.dto.AssetReportView.SubscriptionRow;
import org.doit.onbank.account.dto.AssetReportView.Summary;
import org.doit.onbank.account.entity.Account;
import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.account.entity.AccountType;
import org.doit.onbank.account.entity.AssetReport;
import org.doit.onbank.account.repository.AssetReportRepository;
import org.doit.onbank.account.repository.LoanSummary;
import org.doit.onbank.account.repository.MonthlyAmount;
import org.doit.onbank.account.repository.MonthlyCashFlow;
import org.doit.onbank.account.repository.SubscriptionSummary;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

// 자산관리 리포트 (기획서 7. 자산관리)
// 화면을 열 때 계산하고, 최근 6개월 값을 asset_report 에 회원·기준년월당 1행으로 저장(있으면 갱신)한다
// 팀3 테이블 조회가 실패해도 화면은 열리고, 그 부분만 "불러오지 못했습니다"로 안내한다
// @Transactional 을 붙이지 않는다 : 팀3 조회가 실패하면 바깥 트랜잭션이 rollback-only 가 되어 저장까지 실패하므로
//   조회·저장은 Repository 메서드 각자의 트랜잭션으로 처리한다
@Service
@RequiredArgsConstructor
public class AssetReportService {

	public static final int TREND_MONTHS = 6;		// 추이 그래프 개월 수
	public static final int SELECTABLE_MONTHS = 12;	// 월 선택 목록 개월 수

	private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
	private static final double DONUT_GAP = 0.8;	// 도넛 조각 사이 간격 (둘레 100 기준)

	private final AssetReportRepository assetReportRepository;
	private final AccountService accountService;

	public AssetReportView getReport(Long memberId, YearMonth requested) {
		YearMonth current = YearMonth.now();
		YearMonth month = isSelectable(requested, current) ? requested : current;

		List<YearMonth> months = new ArrayList<>();
		for (int i = TREND_MONTHS - 1; i >= 0; i--) {
			months.add(month.minusMonths(i));
		}
		LocalDateTime from = months.get(0).atDay(1).atStartOfDay();
		LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();

		// 1. 집계 (팀2 테이블)
		Map<String, MonthlyCashFlow> flows = assetReportRepository.findMonthlyCashFlows(memberId, from, to).stream()
				.collect(Collectors.toMap(MonthlyCashFlow::getYm, Function.identity()));

		// 2. 팀3 테이블 (읽기만, 실패하면 null 또는 빈 목록 + 안내)
		List<String> warnings = new ArrayList<>();
		Map<String, Long> loanPaid = readOrWarn("대출 상환",
				() -> toMap(assetReportRepository.findMonthlyLoanPayments(memberId, from, to)), null, warnings);
		Map<String, Long> cardUsed = readOrWarn("카드 사용",
				() -> toMap(assetReportRepository.findMonthlyCardUsage(memberId, from, to)), null, warnings);
		List<LoanSummary> loans = readOrWarn("대출",
				() -> assetReportRepository.findApprovedLoans(memberId), List.of(), warnings);
		List<SubscriptionSummary> subscriptions = readOrWarn("예적금",
				() -> assetReportRepository.findActiveSubscriptions(memberId), List.of(), warnings);

		// 3. asset_report 저장 (최근 6개월)
		Map<String, AssetReport> reports = saveReports(memberId, months, flows, loanPaid);
		AssetReport selected = reports.get(key(month));
		AssetReport previous = reports.get(key(month.minusMonths(1)));

		// 4. 자산 구성·대출
		List<Slice> composition = composition(accountService.getMyAccounts(memberId));
		long totalAssets = composition.stream().mapToLong(Slice::amount).sum();
		List<LoanRow> loanRows = loans.stream().map(this::loanRow).toList();
		long loanBalance = loanRows.stream().mapToLong(LoanRow::remaining).sum();

		// 5. 추이·항목
		long trendMax = months.stream().map(m -> reports.get(key(m)))
				.mapToLong(r -> Math.max(value(r.getRecentDepositAmount()), value(r.getRecentWithdrawalAmount())))
				.max().orElse(0L);
		List<MonthBar> trend = months.stream().map(m -> {
			AssetReport r = reports.get(key(m));
			long in = value(r.getRecentDepositAmount());
			long out = value(r.getRecentWithdrawalAmount());
			return new MonthBar(m, in, out, percent(in, trendMax), percent(out, trendMax), m.equals(month));
		}).toList();

		Summary summary = new Summary(value(selected.getRecentDepositAmount()), value(selected.getRecentWithdrawalAmount()),
				value(selected.getRecentDepositAmount()) - value(previous.getRecentDepositAmount()),
				value(selected.getRecentWithdrawalAmount()) - value(previous.getRecentWithdrawalAmount()));

		return new AssetReportView(month, selectableMonths(current), totalAssets, loanBalance, totalAssets - loanBalance,
				summary, composition, trend, trendMax,
				items(selected, cardUsed == null ? null : cardUsed.getOrDefault(key(month), 0L)),
				loanRows, subscriptions.stream().map(this::subscriptionRow).toList(), warnings, LocalDateTime.now());
	}

	// 최근 12개월 안의 달만 고를 수 있다 (미래 달, 너무 오래된 달은 이번 달로)
	private boolean isSelectable(YearMonth requested, YearMonth current) {
		return requested != null && !requested.isAfter(current)
				&& !requested.isBefore(current.minusMonths(SELECTABLE_MONTHS - 1L));
	}

	private List<YearMonth> selectableMonths(YearMonth current) {
		List<YearMonth> list = new ArrayList<>();
		for (int i = 0; i < SELECTABLE_MONTHS; i++) {
			list.add(current.minusMonths(i));
		}
		return list;
	}

	// 회원·기준년월당 1행 : 있으면 값만 바꾸고 없으면 새로 만든다
	private Map<String, AssetReport> saveReports(Long memberId, List<YearMonth> months,
			Map<String, MonthlyCashFlow> flows, Map<String, Long> loanPaid) {
		List<String> keys = months.stream().map(AssetReportService::key).toList();
		Map<String, AssetReport> saved = new LinkedHashMap<>();
		assetReportRepository.findByAccountIdAndStandardYearMonthIn(memberId, keys)
				.forEach(report -> saved.putIfAbsent(report.getStandardYearMonth(), report));

		List<AssetReport> toSave = new ArrayList<>();
		for (String ym : keys) {
			MonthlyCashFlow flow = flows.get(ym);
			long in = flow == null ? 0L : value(flow.getInAmount());
			long out = flow == null ? 0L : value(flow.getOutAmount());
			long savings = flow == null ? 0L : value(flow.getSavingsAmount());
			long utility = flow == null ? 0L : value(flow.getUtilityBillAmount());
			long maintenance = flow == null ? 0L : value(flow.getMaintenanceFeeAmount());
			Long loan = loanPaid == null ? null : loanPaid.getOrDefault(ym, 0L);	// 조회 실패면 모름(NULL)

			AssetReport report = saved.get(ym);
			if (report == null) {
				report = AssetReport.builder()
						.accountId(memberId)
						.standardYearMonth(ym)
						.recentDepositAmount(in)
						.recentWithdrawalAmount(out)
						.savingsAmount(savings)
						.loanInterestRepaymentAmount(loan)
						.investmentAmount(null)		// 투자 데이터 출처 없음 (연결 예정)
						.utilityBillAmount(utility)
						.maintenanceFeeAmount(maintenance)
						.build();
			} else {
				report.update(in, out, savings, loan, null, utility, maintenance);
			}
			toSave.add(report);
		}

		Map<String, AssetReport> result = new LinkedHashMap<>();
		assetReportRepository.saveAll(toSave).forEach(report -> result.put(report.getStandardYearMonth(), report));
		return result;
	}

	// 자산 구성 : 정상·동결 계좌 잔액 (개설대기·해지 제외). 유형이 비어 있는 계좌는 입출금으로 본다
	private List<Slice> composition(List<Account> accounts) {
		Map<String, Long> sums = new LinkedHashMap<>();
		sums.put(AccountType.CHECKING, 0L);
		sums.put(AccountType.DEPOSIT, 0L);
		sums.put(AccountType.SAVINGS, 0L);
		for (Account account : accounts) {
			if (!AccountStatus.ACTIVE.equals(account.getAccountStatus())
					&& !AccountStatus.FROZEN.equals(account.getAccountStatus())) {
				continue;
			}
			String type = account.getAccountType() == null ? AccountType.CHECKING : account.getAccountType();
			if (sums.containsKey(type)) {
				sums.merge(type, value(account.getBalance()), Long::sum);
			}
		}

		long total = sums.values().stream().mapToLong(Long::longValue).sum();
		long nonZero = sums.values().stream().filter(v -> v > 0).count();
		List<Slice> slices = new ArrayList<>();
		double start = 0;
		int slot = 1;
		for (Map.Entry<String, Long> entry : sums.entrySet()) {
			double pct = percent(entry.getValue(), total);
			String dashArray = "";
			String dashOffset = "";
			if (pct > 0) {
				double length = nonZero > 1 ? Math.max(pct - DONUT_GAP, 0.3) : pct;
				dashArray = format(length) + " " + format(100 - length);
				dashOffset = format(-start);
			}
			slices.add(new Slice(typeLabel(entry.getKey()), "series-" + slot++, entry.getValue(), pct, dashArray, dashOffset));
			start += pct;
		}
		return slices;
	}

	// 기준월 항목별 현황 : 너비는 항목 중 가장 큰 값 대비
	private List<ItemBar> items(AssetReport report, Long cardUsage) {
		Long[] values = { report.getSavingsAmount(), report.getLoanInterestRepaymentAmount(), cardUsage,
				report.getUtilityBillAmount(), report.getMaintenanceFeeAmount() };
		long max = 0L;
		for (Long v : values) {
			max = Math.max(max, value(v));
		}
		List<ItemBar> items = new ArrayList<>();
		items.add(item("저축액", values[0], max, "예금·적금 계좌로 들어온 금액"));
		items.add(item("대출 이자·상환", values[1], max, "대출 상환 스케줄에서 납부한 금액"));
		items.add(item("카드 사용액", values[2], max, "ONBANK 카드 사용 금액"));
		items.add(item("공과금", values[3], max, "메모에 공과금·전기·가스·수도·통신·세금이 들어간 출금"));
		items.add(item("관리비", values[4], max, "메모에 관리비가 들어간 출금"));
		items.add(new ItemBar("투자 금액", 0L, 0, "투자 상품 연결 예정", false));
		return items;
	}

	private ItemBar item(String label, Long amount, long max, String note) {
		if (amount == null) {	// 팀3 조회 실패
			return new ItemBar(label, 0L, 0, "정보를 불러오지 못했습니다", false);
		}
		return new ItemBar(label, amount, percent(amount, max), note, true);
	}

	private LoanRow loanRow(LoanSummary loan) {
		long approved = value(loan.getApprovedAmount());
		long repaid = Math.min(value(loan.getRepaidPrincipal()), approved);
		long remaining = approved - repaid;
		BigDecimal rate = loan.getInterestRate();
		// 월 예상 이자 : 남은 원금 × 연 금리 ÷ 12 (단순 계산, 실제 상환 방식과 다를 수 있음)
		long monthlyInterest = rate == null ? 0L
				: BigDecimal.valueOf(remaining).multiply(rate)
						.divide(BigDecimal.valueOf(1200), 0, RoundingMode.HALF_UP).longValue();
		return new LoanRow(loan.getProductName() == null ? "대출" : loan.getProductName(), approved, remaining,
				percent(repaid, approved), rate, monthlyInterest, loan.getMaturityDate(), loan.getNextDueDate());
	}

	private SubscriptionRow subscriptionRow(SubscriptionSummary subscription) {
		boolean hasPrincipal = subscription.getPrincipalAmount() != null && subscription.getPrincipalAmount() > 0;
		long amount = hasPrincipal ? subscription.getPrincipalAmount() : value(subscription.getMonthlyPayment());
		return new SubscriptionRow(
				subscription.getProductName() == null ? "예적금" : subscription.getProductName(),
				productTypeLabel(subscription.getProductType()),
				amount, hasPrincipal ? "원금" : "월 납입",
				subscription.getSubscribedAt(), subscription.getMaturityDate(), subscription.getExpectedInterest(),
				elapsedPercent(subscription.getSubscribedAt(), subscription.getMaturityDate()));
	}

	// 가입일 ~ 만기일 중 오늘까지 지난 비율
	private double elapsedPercent(String start, String end) {
		try {
			LocalDate from = LocalDate.parse(start);
			LocalDate until = LocalDate.parse(end);
			long total = ChronoUnit.DAYS.between(from, until);
			long passed = ChronoUnit.DAYS.between(from, LocalDate.now());
			return total <= 0 ? 100 : Math.max(0, Math.min(100, passed * 100.0 / total));
		} catch (DateTimeParseException | NullPointerException e) {
			return 0;
		}
	}

	private <T> T readOrWarn(String name, Supplier<T> query, T fallback, List<String> warnings) {
		try {
			return query.get();
		} catch (DataAccessException e) {
			warnings.add(name + " 정보를 불러오지 못했습니다. (대출·예적금 테이블 변경 확인 필요)");
			return fallback;
		}
	}

	private static Map<String, Long> toMap(List<MonthlyAmount> rows) {
		return rows.stream().collect(Collectors.toMap(MonthlyAmount::getYm, row -> value(row.getAmount())));
	}

	private static String typeLabel(String accountType) {
		return switch (accountType) {
			case AccountType.DEPOSIT -> "예금";
			case AccountType.SAVINGS -> "적금";
			default -> "입출금";
		};
	}

	private static String productTypeLabel(String productType) {
		if (productType == null) {
			return "예적금";
		}
		return switch (productType.toUpperCase(Locale.ROOT)) {
			case "DEPOSIT" -> "예금";
			case "SAVINGS" -> "적금";
			default -> productType;
		};
	}

	private static String key(YearMonth month) {
		return month.format(YM);
	}

	private static double percent(long part, long whole) {
		return whole <= 0 ? 0 : part * 100.0 / whole;
	}

	private static String format(double number) {
		return String.format(Locale.ROOT, "%.2f", number);
	}

	private static long value(Long number) {
		return number == null ? 0L : number;
	}
}
