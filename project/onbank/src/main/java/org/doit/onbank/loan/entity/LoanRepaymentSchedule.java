
package org.doit.onbank.loan.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

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
import lombok.Setter;

@Entity
@Table(name = "loan_repayment_schedule",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_LOAN_SCHEDULE_ROUND",
        columnNames = {"LOAN_ACCOUNT_ID", "INSTALLMENT_NO"}
    )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanRepaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SCHEDULE_ID")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LOAN_ACCOUNT_ID", nullable = false)
    private LoanAccount loanAccount;

    @Column(name = "INSTALLMENT_NO", nullable = false)
    private Integer installmentNo;

    @Column(name = "DUE_DATE", nullable = false)
    private LocalDate dueDate;

    @Column(name = "PRINCIPAL_AMOUNT",
            nullable = false, precision = 19, scale = 0)
    private BigDecimal principalAmount;

    @Column(name = "INTEREST_AMOUNT",
            nullable = false, precision = 19, scale = 0)
    private BigDecimal interestAmount;

    @Column(name = "TOTAL_AMOUNT",
            nullable = false, precision = 19, scale = 0)
    private BigDecimal totalAmount;

    @Column(name = "REMAINING_BALANCE",
            nullable = false, precision = 19, scale = 0)
    private BigDecimal remainingBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private LoanRepaymentStatus status;

    @Builder
    public LoanRepaymentSchedule(
            LoanAccount loanAccount,
            Integer installmentNo,
            LocalDate dueDate,
            BigDecimal principalAmount,
            BigDecimal interestAmount,
            BigDecimal remainingBalance) {

        this.loanAccount = loanAccount;
        this.installmentNo = installmentNo;
        this.dueDate = dueDate;
        this.principalAmount = principalAmount;
        this.interestAmount = interestAmount;
        this.totalAmount = principalAmount.add(interestAmount);
        this.remainingBalance = remainingBalance;
        this.status = LoanRepaymentStatus.PENDING;
    }
    
    public void markPaid() {
        if (this.status == LoanRepaymentStatus.PAID) {
            throw new IllegalStateException(
                    "이미 납부한 상환 회차입니다.");
        }

        if (this.status != LoanRepaymentStatus.PENDING
                && this.status != LoanRepaymentStatus.OVERDUE) {
            throw new IllegalStateException(
                    "납부할 수 없는 상환 상태입니다.");
        }

        this.status = LoanRepaymentStatus.PAID;
    }
}
