package org.doit.onbank.account.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

// 계좌 (입출금, 예적금, 대출 등의 계좌 정보를 통합 관리)
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)	// JPA용 기본 생성자
@ToString(exclude = "passwordHash")
public class Account {

	public static final int MAX_PASSWORD_FAIL_COUNT = 5;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)	// AUTO_INCREMENT
	@Column(nullable = false)
	private Long accountId;				// 계좌 번호 (내부 식별자)

	@Column(nullable = false)
	private Long memberId;				// 계좌를 소유한 회원 번호 (members 참조)

	@Column(length = 30)
	private String accountNumber;		// 실제 계좌번호

	@Column(length = 30)
	private String accountType;			// 계좌 유형 (입출금, 예적금, 대출 등)

	private Long balance;			// 현재 잔액

	@Column(length = 20)
	private String accountStatus;		// 계좌 상태 (AccountStatus 상수)

	@Column(length = 255)
	private String passwordHash;		// 계좌 비밀번호 (해시)

	@Column(nullable = false)
	private int passwordFailCount = 0;	// 연속 비밀번호 오류 횟수 (성공 시 0)

	private LocalDateTime lastTransactionAt;	// 최종 거래 일시

	private LocalDate signupDate;		// 가입일 (예적금/대출용)

	private LocalDate expiredDate;		// 만기일 (예적금/대출용)

	@Column(precision = 7, scale = 4)
	private BigDecimal interestRate;	// 적용 이자율

	private Integer signupPeriod;		// 가입 기간(개월)

	private Long monthlyPayment;	// 월 납입 금액

	private Long loanAmount;		// 대출 원금

	private Long loanBalance;		// 대출 잔액

	private Integer paymentDay;			// 매월 납입/결제일

	@Column(length = 30)
	private String repaymentMethod;		// 대출 상환 방식

	private Integer currentPaymentRound;	// 현재 납입 회차

	private Integer totalPaymentRounds;		// 총 납입 회차

	private Long todayTransferAmount;	// 당일 누적 이체 금액 (한도 체크용)

	@Builder
	private Account(Long memberId, String accountNumber, String accountType, Long balance,
			String accountStatus, String passwordHash, LocalDateTime lastTransactionAt, LocalDate signupDate,
			LocalDate expiredDate, BigDecimal interestRate, Integer signupPeriod, Long monthlyPayment,
			Long loanAmount, Long loanBalance, Integer paymentDay, String repaymentMethod,
			Integer currentPaymentRound, Integer totalPaymentRounds, Long todayTransferAmount) {
		this.memberId = memberId;
		this.accountNumber = accountNumber;
		this.accountType = accountType;
		this.balance = balance;
		this.accountStatus = accountStatus;
		this.passwordHash = passwordHash;
		this.lastTransactionAt = lastTransactionAt;
		this.signupDate = signupDate;
		this.expiredDate = expiredDate;
		this.interestRate = interestRate;
		this.signupPeriod = signupPeriod;
		this.monthlyPayment = monthlyPayment;
		this.loanAmount = loanAmount;
		this.loanBalance = loanBalance;
		this.paymentDay = paymentDay;
		this.repaymentMethod = repaymentMethod;
		this.currentPaymentRound = currentPaymentRound;
		this.totalPaymentRounds = totalPaymentRounds;
		this.todayTransferAmount = todayTransferAmount;
	}

	// 거래가 일어날 때마다 호출 (입금·출금·이체 공통)
	// 최종 거래일과 오늘 날짜가 다르면 당일 누적 이체 금액을 0으로 초기화한 뒤 최종 거래 일시를 갱신한다
	// → 날짜가 같으면 todayTransferAmount 는 항상 오늘 이체분만 담고 있다
	public void touchTransaction(LocalDateTime now) {
		if (lastTransactionAt == null || !lastTransactionAt.toLocalDate().equals(now.toLocalDate())) {
			this.todayTransferAmount = 0L;
		}
		this.lastTransactionAt = now;
	}

	// 입금: 정상(ACTIVE)·동결(FROZEN) 계좌만 가능
	public void deposit(long amount, LocalDateTime now) {
		validateAmount(amount);
		if (!canDeposit()) {
			throw new IllegalStateException("입금할 수 없는 계좌 상태입니다: " + accountStatus);
		}
		touchTransaction(now);
		this.balance += amount;
	}

	// 출금: 정상(ACTIVE) 계좌만 가능, 비밀번호 오류 5회면 불가, 잔액 부족이면 불가
	// 현금 출금은 이체 한도와 별개이므로 todayTransferAmount 에 누적하지 않는다
	public void withdraw(long amount, LocalDateTime now) {
		validateAmount(amount);
		if (!canWithdraw()) {
			throw new IllegalStateException("출금할 수 없는 계좌 상태입니다: " + accountStatus);
		}
		if (isPasswordFailLimitReached()) {
			throw new IllegalStateException("계좌 비밀번호 오류 " + MAX_PASSWORD_FAIL_COUNT + "회로 출금할 수 없습니다.");
		}
		if (balance < amount) {
			throw new IllegalStateException("잔액이 부족합니다.");
		}
		touchTransaction(now);
		this.balance -= amount;
	}

	public boolean canDeposit() {
		return AccountStatus.ACTIVE.equals(accountStatus) || AccountStatus.FROZEN.equals(accountStatus);
	}

	public boolean canWithdraw() {
		return AccountStatus.ACTIVE.equals(accountStatus);
	}

	private void validateAmount(long amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("금액은 0보다 커야 합니다.");
		}
	}

	// 비밀번호 오류 1회 증가
	public void increasePasswordFailCount() {
		if (passwordFailCount < MAX_PASSWORD_FAIL_COUNT) {
			this.passwordFailCount++;
		}
	}

	// 비밀번호 성공 시 오류 횟수 초기화
	public void resetPasswordFailCount() {
		this.passwordFailCount = 0;
	}

	// 오류 횟수가 최대치(5회)에 도달했는지
	public boolean isPasswordFailLimitReached() {
		return passwordFailCount >= MAX_PASSWORD_FAIL_COUNT;
	}
}
