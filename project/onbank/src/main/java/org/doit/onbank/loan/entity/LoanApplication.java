
package org.doit.onbank.loan.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loan_application")
@Getter
@Setter
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


	@Column(name = "REPAYMENT_METHOD", length = 20)
	private String repaymentMethod;

	@Column(name = "APPROVED_AMOUNT", precision = 19, scale = 0)
	private BigDecimal approvedAmount;

	@Column(name = "APPROVED_INTEREST_RATE", precision = 7, scale = 4)
	private BigDecimal approvedInterestRate;

	@Column(name = "REVIEWED_BY")
	private Long reviewedBy;

	@Column(name = "REVIEWED_AT")
	private LocalDateTime reviewedAt;

	@Column(name = "STARTED_AT")
	private LocalDateTime startedAt;

	@Column(name = "MATURITY_DATE")
	private java.time.LocalDate maturityDate;

	@Column(name = "UPDATED_AT")
	private LocalDateTime updatedAt;

	public void approve(
			BigDecimal amount,
			BigDecimal rate,
			Long reviewerId,
			LocalDateTime startDate) {

		if (status != LoanApplicationStatus.REVIEWING) {
			throw new IllegalStateException(
					"심사 중인 신청만 승인할 수 있습니다.");
		}

		if (amount == null || amount.signum() <= 0
				|| amount.compareTo(requestedAmount) > 0) {
			throw new IllegalArgumentException(
					"승인 금액이 신청 금액을 초과하거나 올바르지 않습니다.");
		}

		if (rate == null || rate.signum() < 0
				|| rate.compareTo(new BigDecimal("100")) > 0) {
			throw new IllegalArgumentException(
					"승인 금리가 올바르지 않습니다.");
		}

		if (startDate == null) {
			throw new IllegalArgumentException(
					"대출 시작일을 입력하세요.");
		}

		if (!java.util.Set.of(
				"EQUAL", "PRINCIPAL", "BULLET"
				).contains(repaymentMethod)) {
			throw new IllegalStateException(
					"상환 방식이 지정되지 않았습니다.");
		}

		this.approvedAmount = amount;
		this.approvedInterestRate = rate;
		this.reviewedBy = reviewerId;
		this.reviewedAt = LocalDateTime.now();
		this.startedAt = startDate;
		this.maturityDate =
				startDate.toLocalDate().plusMonths(requestedMonths);
		this.updatedAt = LocalDateTime.now();
		this.status = LoanApplicationStatus.APPROVED;
	}


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
