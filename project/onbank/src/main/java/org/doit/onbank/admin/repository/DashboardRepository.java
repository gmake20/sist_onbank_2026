package org.doit.onbank.admin.repository;

import org.doit.onbank.account.entity.AccountStatus;
import org.doit.onbank.admin.dto.DailyTransactionStat;
import org.doit.onbank.loan.entity.LoanAccountStatus;
import org.doit.onbank.member.entity.Member;
import org.doit.onbank.member.entity.MemberStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// DSH-001 요약 지표, DSH-002 거래량 추이 (S-04). 여러 테이블을 세기만 하므로 조회 전용 하나로 모은다
// 합계(sum)는 대상이 0건이면 null 이 나온다 → 서비스에서 0 으로 바꾼다
public interface DashboardRepository extends Repository<Member, Long> {

    @Query("SELECT count(m) FROM Member m WHERE m.status <> :excluded")
    long countMembersExcept(@Param("excluded") MemberStatus excluded);

    @Query("SELECT count(m) FROM Member m WHERE m.status = :status")
    long countMembers(@Param("status") MemberStatus status);

    // 이체 1건은 TRANSFER_OUT + TRANSFER_IN 2줄이라 types 로 한쪽만 넘긴다
    @Query("""
             SELECT count(t) FROM TransactionHistory t
             WHERE t.transactionType IN :types
             AND t.transactionAt >= :from AND t.transactionAt < :to
             """)
    long countTransactions(@Param("types") List<String> types,
                           @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
             SELECT sum(t.transactionAmount) FROM TransactionHistory t
             WHERE t.transactionType IN :types
             AND t.transactionAt >= :from AND t.transactionAt < :to
             """)
    Long sumTransactionAmount(@Param("types") List<String> types,
                              @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // DSH-002 : 날짜별 건수·금액. types 는 요약 카드와 같은 목록 (카드의 오늘 숫자 = 차트의 오늘 막대)
    // 일반 SQL (팀원2 AssetReportRepository 와 같은 방식). MySQL SUM 은 DECIMAL 이라 SIGNED 로 바꿔 Long 으로 받는다
    @Query(value = """
             SELECT DATE(transaction_at) AS day,
                    COUNT(*) AS count,
                    CAST(SUM(transaction_amount) AS SIGNED) AS amount
             FROM transaction_history
             WHERE transaction_type IN (:types)
             AND transaction_at >= :from AND transaction_at < :to
             GROUP BY DATE(transaction_at)
             """, nativeQuery = true)
    List<DailyTransactionStat> dailyTransactions(@Param("types") List<String> types,
                                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT count(a) FROM Account a WHERE a.accountStatus = :status")
    long countAccounts(@Param("status") AccountStatus status);

    // 완납(CLOSED)만 빼고 남은 잔액 합 — 정상·연체·부실 포함
    @Query("SELECT sum(l.remainingBalance) FROM LoanAccount l WHERE l.status <> :excluded")
    BigDecimal sumLoanBalanceExcept(@Param("excluded") LoanAccountStatus excluded);
}
