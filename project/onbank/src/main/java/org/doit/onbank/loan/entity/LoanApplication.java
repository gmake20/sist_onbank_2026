
package org.doit.onbank.loan.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.doit.onbank.account.entity.Account;
import org.doit.onbank.employee.entity.Employee;
import org.doit.onbank.employee.entity.EmployeeRole;
import org.doit.onbank.employee.entity.EmployeeStatus;
import org.doit.onbank.member.entity.Member;

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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "loan_application")
@Getter
@NoArgsConstructor
public class LoanApplication {

    // ======================================
    // 1. 기본키
    // ======================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "APPLICATION_ID")
    private Long id;

    // ======================================
    // 2. 대출 상품
    // ======================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "PRODUCT_ID", nullable = false)
    private LoanProduct product;

    // ======================================
    // 3. 신청 고객
    // ======================================

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "MEMBER_ID", nullable = false)
    private Member member;

    @Column(
        name = "APPLICANT_USERNAME",
        nullable = false,
        length = 150
    )
    private String applicantUsername;

    // ======================================
    // 4. 신청 정보
    // ======================================

    @Column(
        name = "REQUESTED_AMOUNT",
        nullable = false,
        precision = 19,
        scale = 0
    )
    private BigDecimal requestedAmount;

    @Column(name = "REQUESTED_MONTHS", nullable = false)
    private Integer requestedMonths;

    @Column(name = "REPAYMENT_METHOD", length = 20)
    private String repaymentMethod;

    @Column(name = "AGREED_TO_TERMS", nullable = false)
    private boolean agreedToTerms;

    // 대출금을 받을 입출금계좌
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_account_id")
    private Account disbursementAccount;
    
    // ======================================
    // 5. 심사 상태
    // ======================================

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private LoanApplicationStatus status;

    // ======================================
    // 6. 심사 담당 은행 직원
    // ======================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "REVIEWED_BY"
    )
    private Employee reviewer;

    // ======================================
    // 7. 대출 승인 정보
    // ======================================

    @Column(
        name = "APPROVED_AMOUNT",
        precision = 19,
        scale = 0
    )
    private BigDecimal approvedAmount;

    @Column(
        name = "APPROVED_INTEREST_RATE",
        precision = 7,
        scale = 4
    )
    private BigDecimal approvedInterestRate;

    @Column(name = "REVIEWED_AT")
    private LocalDateTime reviewedAt;

    @Column(name = "STARTED_AT")
    private LocalDateTime startedAt;

    @Column(name = "MATURITY_DATE")
    private LocalDate maturityDate;

    // ======================================
    // 8. 추가 서류 / 거절 사유
    // ======================================

    @Column(name = "ADDITIONAL_INFO_REASON", length = 500)
    private String additionalInfoReason;

    @Column(name = "REJECTION_REASON", length = 500)
    private String rejectionReason;

    // ======================================
    // 9. 생성 및 수정 날짜
    // ======================================

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "VERSION")
    private Long version;

    // ======================================
    // 10. 대출 신청 생성자
    // ======================================

    public LoanApplication(
            Member member,
            LoanProduct product,
            String applicantUsername,
            BigDecimal requestedAmount,
            Integer requestedMonths,
            String repaymentMethod,
            boolean agreedToTerms,
            Account disbursementAccount) {

        if (member == null || product == null) {
            throw new IllegalArgumentException(
                    "회원과 대출 상품 정보가 필요합니다.");
        }

        if (disbursementAccount == null) {
            throw new IllegalArgumentException(
                    "대출금 수령 계좌가 필요합니다.");
        }

        if (applicantUsername == null
                || applicantUsername.isBlank()) {
            throw new IllegalArgumentException(
                    "신청자 정보가 필요합니다.");
        }

        if (requestedAmount == null
                || requestedAmount.signum() <= 0
                || requestedAmount.scale() > 0) {
            throw new IllegalArgumentException(
                    "대출 신청금액이 올바르지 않습니다.");
        }

        if (requestedMonths == null
                || requestedMonths < 1
                || requestedMonths > 600) {
            throw new IllegalArgumentException(
                    "대출 기간이 올바르지 않습니다.");
        }

        if (!Set.of(
                "EQUAL", "PRINCIPAL", "BULLET"
        ).contains(repaymentMethod)) {
            throw new IllegalArgumentException(
                    "지원하지 않는 상환방식입니다.");
        }

        if (!agreedToTerms) {
            throw new IllegalArgumentException(
                    "필수 약관에 동의해야 합니다.");
        }

        this.member = member;
        this.product = product;
        this.applicantUsername = applicantUsername;
        this.requestedAmount = requestedAmount;
        this.requestedMonths = requestedMonths;
        this.repaymentMethod = repaymentMethod;
        this.agreedToTerms = agreedToTerms;

        // 대출금 수령 계좌 저장
        this.disbursementAccount = disbursementAccount;

        this.status = LoanApplicationStatus.PENDING;

        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    // ======================================
    // 11. 심사 시작
    // ======================================

    public void startReview() {

        if (status != LoanApplicationStatus.PENDING) {
            throw new IllegalStateException(
                "심사대기 상태에서만 심사를 시작할 수 있습니다."
            );
        }

        this.status = LoanApplicationStatus.REVIEWING;
        this.updatedAt = LocalDateTime.now();
    }

    // ======================================
    // 12. 추가 서류 요청
    // ======================================

    public void requestAdditionalInfo(
            String reason,
            Employee reviewer) {

        if (status != LoanApplicationStatus.REVIEWING) {
            throw new IllegalStateException(
                "심사 중인 신청만 서류보완을 요청할 수 있습니다."
            );
        }

        validateReason(reason);
        validateReviewer(reviewer, false);

        this.additionalInfoReason = reason.trim();
        this.reviewer = reviewer;
        this.updatedAt = LocalDateTime.now();

        this.status = LoanApplicationStatus.ADDITIONAL_INFO;
    }

    // ======================================
    // 13. 서류 확인 후 심사 재개
    // ======================================

    public void resumeReview() {

        if (status != LoanApplicationStatus.ADDITIONAL_INFO) {
            throw new IllegalStateException(
                "서류보완 상태에서만 심사를 재개할 수 있습니다."
            );
        }

        this.additionalInfoReason = null;
        this.status = LoanApplicationStatus.REVIEWING;
        this.updatedAt = LocalDateTime.now();
    }

    // ======================================
    // 14. 대출 승인
    // ======================================

    public void approve(
            BigDecimal amount,
            BigDecimal rate,
            Employee reviewer,
            LocalDateTime startDate) {

        if (status != LoanApplicationStatus.REVIEWING) {
            throw new IllegalStateException(
                "심사 중인 신청만 승인할 수 있습니다."
            );
        }

        validateReviewer(reviewer, true);

        if (amount == null
                || amount.signum() <= 0
                || amount.scale() > 0
                || amount.compareTo(requestedAmount) > 0) {
            throw new IllegalArgumentException(
                "승인 금액이 올바르지 않습니다."
            );
        }

        if (rate == null
                || rate.signum() < 0
                || rate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException(
                "승인 금리가 올바르지 않습니다."
            );
        }

        if (startDate == null) {
            throw new IllegalArgumentException(
                "대출 시작일이 필요합니다."
            );
        }

        this.approvedAmount = amount;
        this.approvedInterestRate = rate;
        this.reviewer = reviewer;

        LocalDateTime now = LocalDateTime.now();

        this.reviewedAt = now;
        this.updatedAt = now;
        this.startedAt = startDate;

        this.maturityDate = startDate
                .toLocalDate()
                .plusMonths(requestedMonths);

        this.additionalInfoReason = null;
        this.rejectionReason = null;

        this.status = LoanApplicationStatus.APPROVED;
    }

    // ======================================
    // 15. 대출 거절
    // ======================================

    public void reject(
            String reason,
            Employee reviewer) {

        if (status != LoanApplicationStatus.REVIEWING) {
            throw new IllegalStateException(
                "심사 중인 신청만 거절할 수 있습니다."
            );
        }

        validateReason(reason);
        validateReviewer(reviewer, true);

        this.rejectionReason = reason.trim();
        this.additionalInfoReason = null;
        this.reviewer = reviewer;

        LocalDateTime now = LocalDateTime.now();

        this.reviewedAt = now;
        this.updatedAt = now;

        this.status = LoanApplicationStatus.REJECTED;
    }

    // ======================================
    // 16. 심사 담당자 검증
    // ======================================

    private void validateReviewer(
            Employee reviewer,
            boolean managerRequired) {

        if (reviewer == null) {
            throw new IllegalArgumentException(
                "심사 담당 직원이 없습니다."
            );
        }

        if (reviewer.getStatus() != EmployeeStatus.ACTIVE) {
            throw new IllegalStateException(
                "활성 상태의 직원만 심사할 수 있습니다."
            );
        }

        if (managerRequired
                && reviewer.getRole() != EmployeeRole.MANAGER) {
            throw new IllegalStateException(
                "대출 승인 또는 거절 권한이 없습니다."
            );
        }
    }

    // ======================================
    // 17. 사유 검증
    // ======================================

    private void validateReason(String reason) {

        if (reason == null
                || reason.isBlank()
                || reason.length() > 500) {
            throw new IllegalArgumentException(
                "사유는 1~500자로 입력해야 합니다."
            );
        }
    }
}
