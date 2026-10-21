package org.doit.onbank.admin.repository;

import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.admin.dto.TransactionListRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

// TRX-001 전체 거래 (S-09 전체 거래 탭). 조회만 (Repository 상속 → save·delete 없음)
// 일반 SQL (팀원2 AssetReportRepository, DashboardRepository 와 같은 방식)
// 이체 1건의 출금·입금 행과 account_transfer 는 같은 transaction_sqno 를 쓴다 (팀원2 AccountTransferService 41줄) → 상대계좌는 LEFT JOIN
public interface TransactionSearchRepository extends Repository<TransactionHistory, Long> {

    // type : 팀원2 TransactionType 값 하나. null 이면 전체
    // excludeIn : 대시보드 "오늘 거래" 카드와 같은 기준 (이체 1건을 보낸 쪽 1줄로만 센다)
    // 기간은 from 이상 toExclusive 미만 — 서비스에서 종료일 + 1일 00:00 을 넘긴다
    // 계좌번호는 하이픈을 뺀 값끼리 비교 (AccountSearchRepository 와 같음)
    String FROM_WHERE = """
             FROM transaction_history th
             JOIN account a ON a.account_id = th.account_id
             JOIN members m ON m.member_id = a.member_id
             LEFT JOIN account_transfer tr ON tr.transaction_sqno = th.transaction_sqno
             WHERE th.transaction_at >= :from AND th.transaction_at < :toExclusive
             AND (:type IS NULL OR th.transaction_type = :type)
             AND (:excludeIn = FALSE OR th.transaction_type <> 'TRANSFER_IN')
             AND (:accountNumber IS NULL OR REPLACE(a.account_number, '-', '') LIKE CONCAT('%', :accountNumber, '%'))
             AND (:minAmount IS NULL OR th.transaction_amount >= :minAmount)
             AND (:maxAmount IS NULL OR th.transaction_amount <= :maxAmount)
             """;

    // 이체 보냄·받음 두 행은 거래일시가 같아서 거래내역 번호로 한 번 더 정렬
    @Query(value = """
             SELECT th.transaction_history_id AS transactionHistoryId,
                    th.transaction_at AS transactionAt,
                    a.account_number AS accountNumber,
                    m.login_id AS memberLoginId,
                    m.name AS memberName,
                    th.transaction_type AS transactionType,
                    th.transaction_amount AS amount,
                    th.balance_after_transaction AS balanceAfter,
                    CASE th.transaction_type
                        WHEN 'TRANSFER_OUT' THEN tr.deposit_account_number
                        WHEN 'TRANSFER_IN' THEN tr.withdrawal_account_number
                    END AS counterpartAccountNumber,
                    th.transaction_content AS content
             """ + FROM_WHERE + " ORDER BY th.transaction_at DESC, th.transaction_history_id DESC",
            countQuery = "SELECT COUNT(*) " + FROM_WHERE,
            nativeQuery = true)
    Page<TransactionListRow> search(@Param("from") LocalDateTime from,
                                    @Param("toExclusive") LocalDateTime toExclusive,
                                    @Param("type") String type,
                                    @Param("excludeIn") boolean excludeIn,
                                    @Param("accountNumber") String accountNumber,
                                    @Param("minAmount") Long minAmount,
                                    @Param("maxAmount") Long maxAmount,
                                    Pageable pageable);

    // 하단 합계. 입금·출금 부호 없이 거래 금액을 그대로 더한다 (명세서 2.7 "금액 합계")
    // MySQL SUM 은 DECIMAL 이라 SIGNED 로 바꿔 long 으로 받는다 (DashboardRepository 와 같음)
    @Query(value = "SELECT CAST(COALESCE(SUM(th.transaction_amount), 0) AS SIGNED) " + FROM_WHERE,
            nativeQuery = true)
    long sumAmount(@Param("from") LocalDateTime from,
                   @Param("toExclusive") LocalDateTime toExclusive,
                   @Param("type") String type,
                   @Param("excludeIn") boolean excludeIn,
                   @Param("accountNumber") String accountNumber,
                   @Param("minAmount") Long minAmount,
                   @Param("maxAmount") Long maxAmount);
}
