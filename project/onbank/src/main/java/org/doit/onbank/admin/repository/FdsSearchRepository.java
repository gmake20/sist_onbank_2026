package org.doit.onbank.admin.repository;

import org.doit.onbank.account.entity.TransactionHistory;
import org.doit.onbank.otp.entity.OtpStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

// FDS 판정용 사실 조회 (FdsFacts). 조회만 (Repository 상속 → save·delete 없음)
// 기간은 from 이상 to 이하 — to 는 이번 거래 시각 (이번 거래는 이미 저장된 뒤)
// 회원 기준: 1일 합계·평소 최대·연속 건수·처음 계좌 / 계좌 기준: 큰 입금 직후·장기 미사용
public interface FdsSearchRepository extends Repository<TransactionHistory, Long> {

    // 대출·예적금 거래는 transaction_type 이 일반 입출금과 같아서 적요로만 구분된다 (팀원3 b0b78cd4)
    // 팀원3이 type 을 따로 쓰게 되면 이 상수를 지운다. 문구가 바뀌면 조용히 다시 섞이므로 주의
    String EXCLUDED_CONTENTS = """
             'ONBANK 대출금 지급', 'ONBANK 대출금 상환', 'ONBANK 적금 납입',
             'ONBANK 예적금 만기 지급', 'ONBANK 예적금 중도해지 지급'
             """;

    // 세는 출금 = 즉시이체 + ATM 출금. 예약·정기 이체, 본인 계좌로 보낸 이체, 대출·예적금은 뺀다
    // ATM 출금은 account_transfer 행이 없어서 LEFT JOIN 결과가 NULL
    String OUTFLOW_FROM_WHERE = """
             FROM transaction_history th
             JOIN account a ON a.account_id = th.account_id
             LEFT JOIN account_transfer tr ON tr.transaction_sqno = th.transaction_sqno
             LEFT JOIN account ra ON ra.account_number = tr.deposit_account_number
             WHERE a.member_id = :memberId
             AND th.transaction_type IN ('TRANSFER_OUT', 'WITHDRAWAL')
             AND (tr.transfer_type IS NULL OR tr.transfer_type = 'IMMEDIATE')
             AND (ra.member_id IS NULL OR ra.member_id <> :memberId)
             AND (th.transaction_content IS NULL OR th.transaction_content NOT IN (""" + EXCLUDED_CONTENTS + """
             ))
             AND th.transaction_at >= :from AND th.transaction_at <= :to
             """;

    // OTP_REISSUED : 분실(LOST) 기기가 있는 회원의 지금 사용중 기기 등록 시각. 아니면 null (첫 등록 제외)
    @Query("""
             SELECT d.activatedAt FROM OtpDevice d
             WHERE d.memberId = :memberId AND d.status = :active
             AND EXISTS (SELECT l FROM OtpDevice l WHERE l.memberId = :memberId AND l.status = :lost)
             """)
    LocalDateTime findOtpReactivatedAt(@Param("memberId") Long memberId,
                                       @Param("active") OtpStatus active,
                                       @Param("lost") OtpStatus lost);

    // ELDERLY
    @Query("SELECT m.birthDate FROM Member m WHERE m.memberId = :memberId")
    LocalDate findBirthDate(@Param("memberId") Long memberId);

    // 본인 계좌끼리 이체인지 볼 때 사용. 받는 계좌 주인
    @Query("SELECT a.memberId FROM Account a WHERE a.accountNumber = :accountNumber")
    Long findMemberIdByAccountNumber(@Param("accountNumber") String accountNumber);

    // 이체는 반환값에 거래내역 ID 가 없어서 거래 번호로 보낸 쪽 행을 찾는다
    @Query("""
             SELECT t.transactionHistoryId FROM TransactionHistory t
             WHERE t.accountId = :accountId AND t.transactionSqno = :sqno AND t.transactionType = 'TRANSFER_OUT'
             """)
    Long findTransferOutId(@Param("accountId") Long accountId, @Param("sqno") String sqno);

    // HIGH_DAILY : 합계 (이번 거래 포함). MySQL SUM 은 DECIMAL 이라 SIGNED 로 바꿔 long 으로 받는다
    @Query(value = "SELECT CAST(COALESCE(SUM(th.transaction_amount), 0) AS SIGNED) " + OUTFLOW_FROM_WHERE,
            nativeQuery = true)
    long sumOutflow(@Param("memberId") Long memberId,
                    @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // ABOVE_USUAL : 1회 최대 금액 (이번 거래 제외). 기록 없으면 null
    @Query(value = "SELECT MAX(th.transaction_amount) " + OUTFLOW_FROM_WHERE
            + " AND th.transaction_history_id <> :currentId",
            nativeQuery = true)
    Long maxOutflow(@Param("memberId") Long memberId,
                    @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                    @Param("currentId") Long currentId);

    // RAPID_COUNT : 건수 (이번 거래 포함)
    @Query(value = "SELECT COUNT(*) " + OUTFLOW_FROM_WHERE, nativeQuery = true)
    long countOutflow(@Param("memberId") Long memberId,
                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // NEW_RECIPIENT : 회원의 모든 계좌에서 이 계좌번호로 보낸 적 있는 건수 (이번 거래 제외)
    // 예약·정기 이체로 보낸 것도 "보낸 적 있음"으로 센다
    @Query(value = """
             SELECT COUNT(*) FROM account_transfer tr
             JOIN account a ON a.account_id = tr.account_id
             WHERE a.member_id = :memberId
             AND tr.deposit_account_number = :toAccountNumber
             AND tr.transaction_sqno <> :currentSqno
             """, nativeQuery = true)
    long countPastTransfersTo(@Param("memberId") Long memberId,
                              @Param("toAccountNumber") String toAccountNumber,
                              @Param("currentSqno") String currentSqno);

    // IN_THEN_OUT : 이 계좌가 다른 회원에게서 이체로 받은 최대 금액. 없으면 null
    // DEPOSIT(ATM 입금·대출금·예적금 지급)과 본인 계좌에서 옮긴 돈은 세지 않는다
    @Query(value = """
             SELECT MAX(th.transaction_amount) FROM transaction_history th
             JOIN account_transfer tr ON tr.transaction_sqno = th.transaction_sqno
             JOIN account sa ON sa.account_id = tr.account_id
             WHERE th.account_id = :accountId
             AND th.transaction_type = 'TRANSFER_IN'
             AND sa.member_id <> :memberId
             AND th.transaction_at >= :from AND th.transaction_at <= :to
             """, nativeQuery = true)
    Long maxIncomingFromOthers(@Param("accountId") Long accountId, @Param("memberId") Long memberId,
                               @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    // DORMANT_WAKE : 이 계좌의 바로 전 거래 시각 (Account.lastTransactionAt 은 이번 거래로 이미 바뀜)
    @Query("""
             SELECT max(t.transactionAt) FROM TransactionHistory t
             WHERE t.accountId = :accountId AND t.transactionHistoryId <> :currentId
             """)
    LocalDateTime findPreviousTransactionAt(@Param("accountId") Long accountId,
                                            @Param("currentId") Long currentId);
}
