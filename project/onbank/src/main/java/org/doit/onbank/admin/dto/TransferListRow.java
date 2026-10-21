package org.doit.onbank.admin.dto;

import java.time.LocalDateTime;

// TRX-002 이체내역 한 줄 (TransferSearchRepository.search). account_transfer 1행 = 1줄
// 쿼리의 AS 이름과 getter 이름이 짝 (TransactionListRow 와 같은 방식)
public interface TransferListRow {
    Long getTransferId();                   // 이체번호 (account_transfer_id)
    LocalDateTime getTransferAt();          // 이체일시
    String getTransferType();               // IMMEDIATE / SCHEDULED / RECURRING (팀원2 TransferType)
    String getWithdrawalAccountNumber();    // 출금 계좌번호
    String getWithdrawalMemberName();       // 출금 계좌 회원 이름
    String getWithdrawalMemberLoginId();    // 출금 계좌 회원 아이디
    String getDepositAccountNumber();       // 입금 계좌번호
    String getDepositMemberName();          // 입금 계좌 회원 이름. 타행이면 null (지금은 당행 이체만 있음)
    String getDepositMemberLoginId();       // 입금 계좌 회원 아이디
    Long getAmount();                       // 이체 금액
}
