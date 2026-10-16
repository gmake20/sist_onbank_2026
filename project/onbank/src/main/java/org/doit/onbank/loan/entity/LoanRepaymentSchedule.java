package org.doit.onbank.loan.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loan_repayment_schedule")
@Getter
@Setter
@NoArgsConstructor
public class LoanRepaymentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SCHEDULE_ID")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "APPLICATION_ID", nullable = false)
    private LoanApplication loanApplication;
    
    @Column(name = "INSTALLMENT_NO")
    private Short installmentNo;

    @Column(name = "DUE_DATE")
    private LocalDate dueDate;

    @Column(name = "PRINCIPAL_AMOUNT")
    private Long principalAmount;

    @Column(name = "INTEREST_AMOUNT")
    private Long interestAmount;

    @Column(name = "TOTAL_AMOUNT")
    private Long totalAmount;

    @Column(name = "STATUS", length = 20)
    private String status;

    @Column(name = "PAID_AT")
    private LocalDateTime paidAt;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

}
