package org.doit.onbank.account.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_loan_transaction",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_LOAN_DISBURSEMENT",
        columnNames = {"LOAN_ACCOUNT_ID", "TRANSACTION_TYPE"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "TRANSACTION_ID")
    private Long transactionId;

    // 입출금 계좌
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ACCOUNT_ID", nullable = false)
    private Account account;

    // 거래 유형
    @Enumerated(EnumType.STRING)
    @Column(name = "TRANSACTION_TYPE",
            nullable = false, length = 30)
    private AccountTransactionType transactionType;

    // 거래 금액
    @Column(name = "AMOUNT",
            nullable = false, precision = 19, scale = 0)
    private BigDecimal amount;

    // 거래 후 잔액
    @Column(name = "BALANCE_AFTER",
            nullable = false, precision = 19, scale = 0)
    private BigDecimal balanceAfter;

    // 관련 대출 계좌 ID
    @Column(name = "LOAN_ACCOUNT_ID")
    private Long loanAccountId;

    // 거래 설명
    @Column(name = "DESCRIPTION", length = 200)
    private String description;

    // 거래 일시
    @Column(name = "TRANSACTED_AT", nullable = false)
    private LocalDateTime transactedAt;

    @Builder
    public AccountTransaction(
            Account account,
            AccountTransactionType transactionType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            Long loanAccountId,
            String description) {

        this.account = account;
        this.transactionType = transactionType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.loanAccountId = loanAccountId;
        this.description = description;
        this.transactedAt = LocalDateTime.now();
    }
}
