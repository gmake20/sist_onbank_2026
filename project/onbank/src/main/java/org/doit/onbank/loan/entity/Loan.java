package org.doit.onbank.loan.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loan")
@Getter
@Setter
@NoArgsConstructor
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LOAN_ID")
    private Long loanId;

    @Column(name = "MEMBER_ID")
    private Long memberId;

    @Column(name = "REPAYMENT_ACCOUNT_ID")
    private Long repaymentAccountId;

    @Column(name = "LOAN_TYPE", length = 30)
    private String loanType;

    @Column(name = "REQUESTED_AMOUNT")
    private Long requestedAmount;

    @Column(name = "APPROVED_AMOUNT")
    private Long approvedAmount;

    @Column(name = "ANNUAL_INTEREST_RATE", precision = 7, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(name = "TERM_MONTHS")
    private Short termMonths;

    @Column(name = "REPAYMENT_METHOD", length = 30)
    private String repaymentMethod;

    @Column(name = "EMPLOYMENT_STATUS", length = 30)
    private String employmentStatus;

    @Column(name = "ANNUAL_INCOME")
    private Long annualIncome;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "REVIEWED_BY")
    private Long reviewedBy;

    @Column(name = "APPLIED_AT")
    private LocalDateTime appliedAt;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    @Column(name = "STARTED_AT")
    private LocalDate startedAt;

    @Column(name = "MATURITY_DATE")
    private LocalDate maturityDate;

    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

}
