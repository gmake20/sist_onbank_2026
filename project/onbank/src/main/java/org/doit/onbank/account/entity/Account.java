package org.doit.onbank.account.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private AccountType accountType;	// 계좌 유형 (입출금, 예적금, 대출). 테스트 계좌는 NULL 일 수 있음

	private Long balance;			// 현재 잔액

	@Enumerated(EnumType.STRING)
	@Column(length = 20)
	private AccountStatus accountStatus;	// 계좌 상태

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
	private Account(Long memberId, String accountNumber, AccountType accountType, Long balance,
			AccountStatus accountStatus, String passwordHash, LocalDateTime lastTransactionAt, LocalDate signupDate,
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

	// 계좌 개설 : 잔액·당일 이체액 0, 비밀번호 오류 0, 정상(ACTIVE) 상태로 만든다
	// balance, today_transfer_amount 가 NULL 이면 입금·이체 계산에서 오류가 나므로 반드시 0으로 넣는다
	public static Account open(Long memberId, String accountNumber, AccountType accountType, String passwordHash,
			LocalDate signupDate) {
		return Account.builder()
				.memberId(memberId)
				.accountNumber(accountNumber)
				.accountType(accountType)
				.balance(0L)
				.accountStatus(AccountStatus.ACTIVE)
				.passwordHash(passwordHash)
				.signupDate(signupDate)
				.todayTransferAmount(0L)
				.build();
	}

	// ---------- 상태 변경 (관리자 쪽에서 Service 를 통해 호출) ----------

	// 개설 승인 : 개설대기(PENDING) → 정상(ACTIVE)
	public void activate() {
		requireStatus(AccountStatus.PENDING, "개설대기 계좌만 승인할 수 있습니다.");
		this.accountStatus = AccountStatus.ACTIVE;
	}

	// 개설 반려 : 개설대기(PENDING) → 해지(CLOSED). 반려는 따로 상태를 두지 않고 해지로 처리
	public void reject() {
		requireStatus(AccountStatus.PENDING, "개설대기 계좌만 반려할 수 있습니다.");
		this.accountStatus = AccountStatus.CLOSED;
	}

	// 동결 : 정상(ACTIVE) → 동결(FROZEN). 입금은 받고 출금·이체는 막는다
	public void freeze() {
		requireStatus(AccountStatus.ACTIVE, "정상 계좌만 거래정지할 수 있습니다.");
		this.accountStatus = AccountStatus.FROZEN;
	}

	// 동결 해제 : 동결(FROZEN) → 정상(ACTIVE)
	public void unfreeze() {
		requireStatus(AccountStatus.FROZEN, "거래정지 계좌만 해제할 수 있습니다.");
		this.accountStatus = AccountStatus.ACTIVE;
	}

	// 해지 : 정상·동결 계좌, 잔액이 0일 때만. 되돌리지 않는다
	public void close() {
		if (accountStatus != AccountStatus.ACTIVE && accountStatus != AccountStatus.FROZEN) {
			throw new IllegalStateException("해지할 수 없는 계좌 상태입니다: " + accountStatus);
		}
		if (balance != null && balance != 0L) {
			throw new IllegalStateException("잔액이 남아 있는 계좌는 해지할 수 없습니다.");
		}
		this.accountStatus = AccountStatus.CLOSED;
	}

	private void requireStatus(AccountStatus expected, String message) {
		if (accountStatus != expected) {
			throw new IllegalStateException(message + " (현재 상태: " + accountStatus + ")");
		}
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

	// 이체 출금: 출금(withdraw)과 같은 검사를 한 뒤 당일 누적 이체 금액에 더한다
	// 1일 한도 검사는 회원 한도가 필요하므로 Service 에서 이 메서드를 부르기 전에 한다
	public void transferOut(long amount, LocalDateTime now) {
		withdraw(amount, now);	// 안에서 touchTransaction(now) → 날짜가 바뀌었으면 누적 금액이 0으로 초기화됨
		this.todayTransferAmount = (todayTransferAmount == null ? 0L : todayTransferAmount) + amount;
	}

	// 이체 입금: 입금(deposit)과 같다. 받는 쪽은 한도와 무관
	public void transferIn(long amount, LocalDateTime now) {
		deposit(amount, now);
	}

	// 해당 날짜에 이 계좌에서 이체한 금액 (값을 바꾸지 않고 조회만)
	// 최종 거래일이 그 날짜가 아니면 저장된 누적 금액은 지난 날짜 것이므로 0
	public long getTodayTransferAmountOn(LocalDate date) {
		if (lastTransactionAt == null || !lastTransactionAt.toLocalDate().equals(date)
				|| todayTransferAmount == null) {
			return 0L;
		}
		return todayTransferAmount;
	}

	public boolean canDeposit() {
		return accountStatus != null && accountStatus.isDepositable();
	}

	public boolean canWithdraw() {
		return accountStatus != null && accountStatus.isWithdrawable();
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
