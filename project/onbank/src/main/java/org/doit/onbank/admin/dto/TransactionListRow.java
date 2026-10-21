package org.doit.onbank.admin.dto;

import java.time.LocalDateTime;

// TRX-001 전체 거래 한 줄 (TransactionSearchRepository.search)
// 쿼리의 AS 이름과 getter 이름이 짝 (transactionAt → getTransactionAt). DailyTransactionStat 과 같은 방식
public interface TransactionListRow {
    Long getTransactionHistoryId();         // 거래내역 번호
    LocalDateTime getTransactionAt();       // 거래일시
    String getAccountNumber();              // 계좌번호
    String getMemberLoginId();              // 회원 아이디
    String getMemberName();                 // 회원 이름
    String getTransactionType();            // DEPOSIT / WITHDRAWAL / TRANSFER_OUT / TRANSFER_IN (팀원2 TransactionType)
    Long getAmount();                       // 거래 금액
    Long getBalanceAfter();                 // 거래 후 잔액
    String getCounterpartAccountNumber();   // 상대계좌. 이체 행만 값이 있음 (보냄 → 받은 계좌, 받음 → 보낸 계좌)
    String getContent();                    // 적요. WITHDRAWAL 안에서 ATM 출금·대출 상환·적금 납입을 구분하는 유일한 칸
}
