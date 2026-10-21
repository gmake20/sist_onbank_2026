package org.doit.onbank.account.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.doit.onbank.account.entity.AssetReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 자산 리포트 저장 + 리포트에 쓰는 집계 조회
// asset_report.account_id 에는 회원 ID(member_id)를 넣는다 (데이터 사전 "기준 계좌 (또는 회원)", 리포트는 회원 단위)
// 팀3 테이블(loan_account, loan_application, loan_product, loan_repayment_schedule, account_loan_transaction,
// product_subscription, product, productcard, card_usage_history)은
// 팀3 엔티티를 고치지 않고 SQL로 읽기만 한다. 팀3 테이블 구조가 바뀌면 이 SQL도 맞춰야 한다
public interface AssetReportRepository extends JpaRepository<AssetReport, Long> {

	List<AssetReport> findByAccountIdAndStandardYearMonthIn(Long memberId, Collection<String> yearMonths);

	// 월별 입출금 : [from, to) 기간, 본인 계좌끼리 이체(같은 거래 번호의 상대 행도 내 계좌)는 입금·출금에서 뺀다
	@Query(value = "SELECT x.ym AS ym,"
			+ " CAST(COALESCE(SUM(CASE WHEN x.internal = 0 AND x.type IN ('DEPOSIT', 'TRANSFER_IN') THEN x.amt END), 0) AS SIGNED) AS inAmount,"
			+ " CAST(COALESCE(SUM(CASE WHEN x.internal = 0 AND x.type IN ('WITHDRAWAL', 'TRANSFER_OUT') THEN x.amt END), 0) AS SIGNED) AS outAmount,"
			+ " CAST(COALESCE(SUM(CASE WHEN x.type IN ('DEPOSIT', 'TRANSFER_IN') AND x.atype IN ('DEPOSIT', 'SAVINGS') THEN x.amt END), 0) AS SIGNED) AS savingsAmount,"
			+ " CAST(COALESCE(SUM(CASE WHEN x.internal = 0 AND x.type IN ('WITHDRAWAL', 'TRANSFER_OUT') AND x.content NOT LIKE '%관리비%'"
			+ "   AND (x.content LIKE '%공과금%' OR x.content LIKE '%전기%' OR x.content LIKE '%가스%' OR x.content LIKE '%수도%'"
			+ "     OR x.content LIKE '%통신%' OR x.content LIKE '%세금%') THEN x.amt END), 0) AS SIGNED) AS utilityBillAmount,"
			+ " CAST(COALESCE(SUM(CASE WHEN x.internal = 0 AND x.type IN ('WITHDRAWAL', 'TRANSFER_OUT') AND x.content LIKE '%관리비%'"
			+ "   THEN x.amt END), 0) AS SIGNED) AS maintenanceFeeAmount"
			+ " FROM (SELECT DATE_FORMAT(th.transaction_at, '%Y%m') AS ym, th.transaction_type AS type,"
			+ "         th.transaction_amount AS amt, th.transaction_content AS content, a.account_type AS atype,"
			+ "         EXISTS (SELECT 1 FROM transaction_history t2 JOIN account a2 ON a2.account_id = t2.account_id"
			+ "                  WHERE t2.transaction_sqno = th.transaction_sqno"
			+ "                    AND t2.transaction_history_id <> th.transaction_history_id"
			+ "                    AND a2.member_id = :memberId) AS internal"
			+ "       FROM transaction_history th JOIN account a ON a.account_id = th.account_id"
			+ "       WHERE a.member_id = :memberId AND th.transaction_at >= :from AND th.transaction_at < :to) x"
			+ " GROUP BY x.ym", nativeQuery = true)
	List<MonthlyCashFlow> findMonthlyCashFlows(@Param("memberId") Long memberId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	// 월별 대출 상환액 (팀3) : 대출 상환으로 출금된 금액 (원금 + 이자)
	// 상환 스케줄의 PAID_AT 은 채워지지 않아 상환 거래 기록(account_loan_transaction)으로 센다
	@Query(value = "SELECT DATE_FORMAT(t.TRANSACTED_AT, '%Y%m') AS ym, CAST(COALESCE(SUM(t.AMOUNT), 0) AS SIGNED) AS amount"
			+ " FROM account_loan_transaction t"
			+ " JOIN loan_account la ON la.LOAN_ACCOUNT_ID = t.LOAN_ACCOUNT_ID"
			+ " JOIN loan_application ap ON ap.APPLICATION_ID = la.APPLICATION_ID"
			+ " WHERE ap.MEMBER_ID = :memberId AND t.TRANSACTION_TYPE = 'LOAN_REPAYMENT'"
			+ " AND t.TRANSACTED_AT >= :from AND t.TRANSACTED_AT < :to"
			+ " GROUP BY DATE_FORMAT(t.TRANSACTED_AT, '%Y%m')", nativeQuery = true)
	List<MonthlyAmount> findMonthlyLoanPayments(@Param("memberId") Long memberId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	// 월별 카드 사용액 (팀3)
	@Query(value = "SELECT DATE_FORMAT(cu.USED_AT, '%Y%m') AS ym, CAST(COALESCE(SUM(cu.AMOUNT), 0) AS SIGNED) AS amount"
			+ " FROM card_usage_history cu JOIN productcard pc ON pc.CARD_ID = cu.CARD_ID"
			+ " WHERE pc.MEMBER_ID = :memberId AND cu.USED_AT >= :from AND cu.USED_AT < :to"
			+ " GROUP BY DATE_FORMAT(cu.USED_AT, '%Y%m')", nativeQuery = true)
	List<MonthlyAmount> findMonthlyCardUsage(@Param("memberId") Long memberId,
			@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	// 실행된 대출 (팀3) : 대출 계좌(loan_account) 기준, 완납(CLOSED)은 뺀다
	// 승인만 된 신청은 아직 돈이 나가지 않았으므로 넣지 않는다
	// (10-21) 전계좌조회 대출 영역도 이 조회를 쓴다 (대출 계좌번호 추가)
	@Query(value = "SELECT la.LOAN_ACCOUNT_ID AS loanAccountId, la.LOAN_ACCOUNT_NUMBER AS loanAccountNumber,"
			+ " lp.PRODUCT_NAME AS productName,"
			+ " CAST(la.PRINCIPAL_AMOUNT AS SIGNED) AS principalAmount, CAST(la.REMAINING_BALANCE AS SIGNED) AS remainingBalance,"
			+ " la.INTEREST_RATE AS interestRate, DATE_FORMAT(la.MATURITY_DATE, '%Y-%m-%d') AS maturityDate,"
			+ " (SELECT DATE_FORMAT(MIN(s.DUE_DATE), '%Y-%m-%d') FROM loan_repayment_schedule s"
			+ "   WHERE s.LOAN_ACCOUNT_ID = la.LOAN_ACCOUNT_ID AND (s.STATUS IS NULL OR s.STATUS <> 'PAID')) AS nextDueDate"
			+ " FROM loan_account la"
			+ " JOIN loan_application ap ON ap.APPLICATION_ID = la.APPLICATION_ID"
			+ " LEFT JOIN loan_product lp ON lp.LOAN_PRODUCT_ID = ap.PRODUCT_ID"
			+ " WHERE ap.MEMBER_ID = :memberId AND la.STATUS <> 'CLOSED'"
			+ " ORDER BY la.LOAN_ACCOUNT_ID", nativeQuery = true)
	List<LoanSummary> findActiveLoans(@Param("memberId") Long memberId);

	// 가입 중인 예적금 (팀3) : 진행 중(ACTIVE)만, 만기·중도 해지는 뺀다
	// (10-21) 팀3가 예적금을 product_subscription 대신 savings_account + savings_product 에 저장하므로 바꿈
	//   principal : 예금은 맡긴 원금, 적금은 지금까지 넣은 금액. 예상 이자는 저장된 값이 없어 NULL
	@Query(value = "SELECT p.name AS productName, p.type AS productType,"
			+ " s.principal AS principalAmount, s.monthly_amount AS monthlyPayment,"
			+ " DATE_FORMAT(s.opened_on, '%Y-%m-%d') AS subscribedAt,"
			+ " DATE_FORMAT(s.maturity_on, '%Y-%m-%d') AS maturityDate, NULL AS expectedInterest"
			+ " FROM savings_account s JOIN savings_product p ON p.product_id = s.product_id"
			+ " WHERE s.member_id = :memberId AND s.status = 'ACTIVE'"
			+ " ORDER BY s.maturity_on", nativeQuery = true)
	List<SubscriptionSummary> findActiveSubscriptions(@Param("memberId") Long memberId);

	// (10-21) 전계좌조회 예적금 최종거래일 (팀3 savings_payment) : 예적금별 가장 늦은 납입일
	@Query(value = "SELECT p.savings_account_id AS savingsAccountId, DATE_FORMAT(MAX(p.paid_on), '%Y.%m.%d') AS lastPaidOn"
			+ " FROM savings_payment p JOIN savings_account s ON s.savings_account_id = p.savings_account_id"
			+ " WHERE s.member_id = :memberId"
			+ " GROUP BY p.savings_account_id", nativeQuery = true)
	List<SavingsLastPaid> findSavingsLastPaidOn(@Param("memberId") Long memberId);
}
