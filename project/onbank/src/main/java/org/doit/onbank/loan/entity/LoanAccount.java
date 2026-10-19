package org.doit.onbank.loan.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "loan_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOAN_ACCOUNT_ID")
    private Long loanAccountId;

    // 대출 계좌번호
    @Column(name = "LOAN_ACCOUNT_NUMBER",
            nullable = false, unique = true, length = 30)
    private String loanAccountNumber;

    // 대출 신청 정보 (1:1)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "APPLICATION_ID",
                nullable = false, unique = true)
    private LoanApplication loanApplication;
    
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "LOAN_ACCOUNT_ID", nullable = false)
//    private LoanAccount loanAccount;

    // 실제 대출 원금
    @Column(name = "PRINCIPAL_AMOUNT",
            nullable = false, precision = 19, scale = 0)
    private BigDecimal principalAmount;

    // 남은 대출 원금
    @Column(name = "REMAINING_BALANCE",
            nullable = false, precision = 19, scale = 0)
    private BigDecimal remainingBalance;

    // 적용 연이율 (%)
    @Column(name = "INTEREST_RATE",
            nullable = false, precision = 10, scale = 6)
    private BigDecimal interestRate;

    // 상환 방식
    @Column(name = "REPAYMENT_METHOD",
            nullable = false, length = 30)
    private String repaymentMethod;

    // 대출 실행일
    @Column(name = "EXECUTED_AT", nullable = false)
    private LocalDateTime executedAt;

    // 만기일
    @Column(name = "MATURITY_DATE", nullable = false)
    private LocalDate maturityDate;

    // 계좌 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private LoanAccountStatus status;

    @Version
    private Long version;

    @Builder
    private LoanAccount(
            String loanAccountNumber,
            LoanApplication loanApplication,
            BigDecimal principalAmount,
            BigDecimal interestRate,
            String repaymentMethod,
            LocalDateTime executedAt,
            LocalDate maturityDate) {

        this.loanAccountNumber = loanAccountNumber;
        this.loanApplication = loanApplication;
        this.principalAmount = principalAmount;
        this.remainingBalance = principalAmount;
        this.interestRate = interestRate;
        this.repaymentMethod = repaymentMethod;
        this.executedAt = executedAt;
        this.maturityDate = maturityDate;
        this.status = LoanAccountStatus.ACTIVE;
    }

    // 원금 상환
    public void repayPrincipal(BigDecimal amount) {
        if (status != LoanAccountStatus.ACTIVE) {
            throw new IllegalStateException(
                "상환할 수 없는 대출 계좌입니다.");
        }

        if (amount == null || amount.signum() <= 0
                || amount.scale() > 0
                || amount.compareTo(remainingBalance) > 0) {
            throw new IllegalArgumentException(
                "상환 금액이 올바르지 않습니다.");
        }

        this.remainingBalance =
                this.remainingBalance.subtract(amount);

        if (remainingBalance.signum() == 0) {
            this.status = LoanAccountStatus.CLOSED;
        }
    }
}
