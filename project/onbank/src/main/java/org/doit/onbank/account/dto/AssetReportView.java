package org.doit.onbank.account.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

// 자산관리 화면에 보여 줄 리포트 (AssetReportService 가 계산)
// 그래프 크기(%)·도넛 조각 위치는 서버에서 계산해 화면은 값만 그린다
public record AssetReportView(
		YearMonth month,					// 기준년월
		List<YearMonth> selectableMonths,	// 월 선택 목록 (최근 12개월, 최신순)
		long totalAssets,					// 총자산 : 입출금 + 예금 + 적금 잔액
		long loanBalance,					// 대출 잔액 : 승인된 대출 원금 - 갚은 원금
		long netAssets,						// 순자산 : 총자산 - 대출 잔액
		Summary summary,					// 기준월 입출금 (지난달 대비)
		List<Slice> composition,			// 자산 구성 (도넛)
		List<MonthBar> trend,				// 최근 6개월 입출금 추이
		long trendMax,						// 추이 그래프 눈금 최댓값
		List<ItemBar> items,				// 기준월 항목별 현황 (저축, 대출 상환, 공과금 ...)
		List<LoanRow> loans,				// 대출 현황
		List<SubscriptionRow> subscriptions,	// 예적금 현황
		List<String> warnings,				// 불러오지 못한 정보 안내 (팀3 테이블 조회 실패 등)
		LocalDateTime generatedAt) {		// 리포트 계산·저장 시각

	// 기준월 입금·출금과 지난달 대비 증감
	public record Summary(long inAmount, long outAmount, long inDiff, long outDiff) {

		public long netFlow() {
			return inAmount - outAmount;
		}
	}

	// 도넛 조각 : SVG 원 둘레를 100으로 두고 dashArray·dashOffset 으로 위치를 잡는다
	public record Slice(String label, String colorClass, long amount, double percent,
			String dashArray, String dashOffset) {
	}

	// 추이 막대 1개월 : 높이는 trendMax 대비 %
	public record MonthBar(YearMonth month, long inAmount, long outAmount, double inHeight, double outHeight,
			boolean selected) {
	}

	// 항목 가로 막대 : 너비는 항목 중 최댓값 대비 %. connected=false 면 아직 데이터 출처가 없는 항목
	public record ItemBar(String label, long amount, double width, String note, boolean connected) {
	}

	public record LoanRow(String productName, long approvedAmount, long remaining, double repaidPercent,
			BigDecimal interestRate, long monthlyInterestEstimate, String maturityDate, String nextDueDate) {
	}

	public record SubscriptionRow(String productName, String typeLabel, long amount, String amountLabel,
			String subscribedAt, String maturityDate, Long expectedInterest, double elapsedPercent) {
	}

	public boolean hasAssets() {
		return totalAssets > 0;
	}

	public boolean hasTrend() {
		return trendMax > 0;
	}
}
