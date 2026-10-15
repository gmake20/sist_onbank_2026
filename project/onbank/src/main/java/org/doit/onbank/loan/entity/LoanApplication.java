
package org.doit.onbank.loan.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "loan_application")
@Getter
@NoArgsConstructor
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "APPLICATION_ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private LoanProduct product;

    @Column(name = "APPLICANT_USERNAME",
            nullable = false, length = 150)
    private String applicantUsername;

    @Column(name = "REQUESTED_AMOUNT",
            nullable = false, precision = 19, scale = 0)
    private BigDecimal requestedAmount;

    @Column(name = "REQUESTED_MONTHS", nullable = false)
    private Integer requestedMonths;

    @Column(name = "AGREED_TO_TERMS", nullable = false)
    private boolean agreedToTerms;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private LoanApplicationStatus status;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    public LoanApplication(
            LoanProduct product,
            String applicantUsername,
            BigDecimal requestedAmount,
            Integer requestedMonths) {

        this.product = product;
        this.applicantUsername = applicantUsername;
        this.requestedAmount = requestedAmount;
        this.requestedMonths = requestedMonths;

        this.agreedToTerms = true;
        this.status = LoanApplicationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    // 관리자의 심사 처리에서만 호출
    public void startReview() {
        if (status != LoanApplicationStatus.PENDING) {
            throw new IllegalStateException(
                "심사대기 상태에서만 심사를 시작할 수 있습니다."
            );
        }
        this.status = LoanApplicationStatus.REVIEWING;
    }
}
