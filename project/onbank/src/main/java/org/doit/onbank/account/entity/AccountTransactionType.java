package org.doit.onbank.account.entity;

public enum AccountTransactionType {
    DEPOSIT,           // 일반 입금
    WITHDRAWAL,        // 일반 출금
    TRANSFER_IN,       // 이체 입금
    TRANSFER_OUT,      // 이체 출금
    LOAN_DISBURSEMENT, // 대출금 지급
    LOAN_REPAYMENT     // 대출 상환 출금
}
