package org.doit.onbank.loan.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "loan_auto_repayment")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LoanAutoRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUTO_REPAYMENT_ID")
    private Long id;

    @Column(name = "LOAN_ACCOUNT_ID", nullable = false, unique = true)
    private Long loanAccountId;

    @Column(name = "MEMBER_ID", nullable = false)
    private Long memberId;

    @Column(name = "ENABLED", nullable = false)
    private boolean enabled;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public LoanAutoRepayment(Long loanAccountId, Long memberId) {
        this.loanAccountId = loanAccountId;
        this.memberId = memberId;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }
}
